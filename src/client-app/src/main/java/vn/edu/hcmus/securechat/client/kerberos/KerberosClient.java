package vn.edu.hcmus.securechat.client.kerberos;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.crypto.CryptoConstants;
import vn.edu.hcmus.securechat.client.network.NtpTimeClient;
import vn.edu.hcmus.securechat.client.network.SocketClient;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.AuthenticatorJson;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtResponseInner;
import vn.edu.hcmus.securechat.common.protocol.dto.StRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.StResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.StResponseInner;
import vn.edu.hcmus.securechat.common.protocol.dto.ErrorResponse;
import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;
import vn.edu.hcmus.securechat.common.crypto.HybridEncryption;
import vn.edu.hcmus.securechat.client.crypto.PkiManager;
import vn.edu.hcmus.securechat.common.config.ServerConfig;
import java.nio.charset.StandardCharsets;

/**
 * Client thực hiện luồng Kerberos V5 (Mock theo phương án A).
 */
public class KerberosClient {
    private static final Logger log = LoggerFactory.getLogger(KerberosClient.class);

    private String freshTgtUsername;
    private String freshTgtBase64;
    private String freshTgtId;
    private byte[] freshKaTgs;

    /**
     * Yêu cầu TGT từ Authentication Server bằng PKINIT (X.509 + RSA).
     */
    public void requestTgt(String username, char[] password) throws Exception {
        log.info("Bắt đầu xin vé TGT từ Authentication Server cho user: {}", username);
        try {
            // 1. Tải KeyStore
            PkiManager.loadKeyStore(username, password);
        } catch (Exception e) {
            throw new Exception(
                    "Không tìm thấy chứng chỉ người dùng hoặc mật khẩu sai. Vui lòng đăng ký trước hoặc kiểm tra lại thông tin.",
                    e);
        }

        try {
            // 2. Tạo TgtRequest
            TgtRequest req = new TgtRequest();
            req.setClientId(username);
            req.setTargetTgs(ServerConfig.TGS_HOST);
            String nonce = randomNonceBase64();
            req.setNonce(nonce);

            byte[] certDer = PkiManager.getCertificate().getEncoded();
            req.setCert(Base64.getEncoder().encodeToString(certDer));

            long timestamp = NtpTimeClient.getCurrentNetworkTime() / 1000L;
            req.setTimestamp(timestamp);

            // Ký Proof-of-Possession (PoP) cho TGT
            String dataToSign = username + "|" + ServerConfig.TGS_HOST + "|" + nonce + "|" + timestamp;
            java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
            sig.initSign(PkiManager.getPrivateKey());
            sig.update(dataToSign.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            req.setSignature(Base64.getEncoder().encodeToString(sig.sign()));

            // 3. Gửi qua mạng tới AS Server
            PacketFrame frame = new PacketFrame(PacketFrame.TYPE_TGT_REQUEST, (byte) 1, (short) 0,
                    JsonSerializer.toBytes(req));
            PacketFrame response = SocketClient.sendRequest(ServerConfig.AS_HOST, ServerConfig.AS_PORT, frame);

            if (response.getType() == PacketFrame.TYPE_TGT_RESPONSE) {
                log.info("Nhận được TGT_RESPONSE. Thực hiện giải mã Hybrid...");
                TgtResponse tgtResp = JsonSerializer.fromBytes(response.getPayload(), TgtResponse.class);

                // 4. Giải mã response inner bằng Private Key của client
                byte[] cipherBytes = Base64.getDecoder().decode(tgtResp.getResponse());
                byte[] innerBytes = HybridEncryption.decrypt(PkiManager.getPrivateKey(), cipherBytes);
                TgtResponseInner inner = JsonSerializer.fromBytes(innerBytes, TgtResponseInner.class);

                if (!nonce.equals(inner.getNonce())) {
                    throw new Exception("Nonce trong TGT_RESPONSE không khớp. Vui lòng thử lại.");
                }

                byte[] sessionKeyBytes = Base64.getDecoder().decode(inner.getSessionKey());
                try {
                    rememberFreshTgt(username, tgtResp.getTgt(), sessionKeyBytes, inner.getTgtId());
                } finally {
                    Arrays.fill(sessionKeyBytes, (byte) 0);
                }

                // 5. Lưu vé (gồm chuỗi TGT mã hoá và K_A_TGS) vào đĩa
                String cacheData = tgtResp.getTgt() + "|||" + inner.getSessionKey()
                        + "|||" + nullToEmpty(inner.getTgtId());
                byte[] cacheBytes = cacheData.getBytes(StandardCharsets.UTF_8);
                try {
                    TicketCache.saveTicket(username, "TGT", cacheBytes, password);
                } finally {
                    Arrays.fill(cacheBytes, (byte) 0);
                }

                log.info("Xác thực Kerberos AS thành công! Đã lưu TGT và SessionKey K_A_TGS.");
                return;
            } else if (response.getType() == PacketFrame.TYPE_ERROR) {
                ErrorResponse errorResp = JsonSerializer.fromBytes(response.getPayload(), ErrorResponse.class);
                log.error("Server error: code={}, message={}", errorResp.getErrorCode(), errorResp.getMessage());
                throw new Exception("Đăng nhập thất bại: " + errorResp.getMessage());
            } else {
                throw new Exception("Server trả về gói tin không hợp lệ thay vì TGT_RESPONSE.");
            }
        } catch (Exception e) {
            log.error("Lỗi khi xin TGT", e);
            throw new Exception("Đăng nhập thất bại khi xin TGT: " + e.getMessage(), e);
        }
    }

    /**
     * Yêu cầu Service Ticket (ST) từ Ticket Granting Server.
     */
    public void requestSt(String username, char[] password, String targetService) throws Exception {
        log.info("Bắt đầu xin vé ST cho dịch vụ: {}", targetService);
        byte[] cachedData = null;
        byte[] kaTgsBytes = null;
        byte[] authBytes = null;
        byte[] encryptedAuth = null;
        byte[] reqBytes = null;
        byte[] cipherBytes = null;
        byte[] innerBytes = null;
        try {
            // 1. Lấy TGT và K_A_TGS từ cache
            String tgtBase64;
            String tgtId;
            if (hasFreshTgt(username)) {
                tgtBase64 = freshTgtBase64;
                tgtId = freshTgtId;
                kaTgsBytes = Arrays.copyOf(freshKaTgs, freshKaTgs.length);
                log.info("Dùng TGT vừa nhận trong RAM để xin ST, tránh đọc nhầm cache cũ.");
            } else {
                cachedData = TicketCache.getTicket(username, "TGT", password);
                if (cachedData == null) {
                    TicketCache.clearCache(username);
                    throw new Exception("Không đọc được TGT từ cache. Vui lòng đăng nhập lại.");
                }
                String cacheString = new String(cachedData, StandardCharsets.UTF_8);
                String[] parts = cacheString.split("\\|\\|\\|");
                if (parts.length < 2) {
                    TicketCache.clearCache(username);
                    throw new Exception("Dữ liệu TGT cache bị hỏng. Vui lòng đăng nhập lại.");
                }
                tgtBase64 = parts[0];
                String kaTgsBase64 = parts[1];
                tgtId = parts.length >= 3 ? parts[2] : "";
                kaTgsBytes = Base64.getDecoder().decode(kaTgsBase64);
            }

            // 2. Tạo Authenticator (timestamp tính bằng SECONDS, khớp với
            // ReplayDefenseService)
            long timestamp = NtpTimeClient.getCurrentNetworkTime() / 1000L;
            String authNonce = randomNonceBase64();
            AuthenticatorJson auth = new AuthenticatorJson(
                    username,
                    timestamp,
                    authNonce,
                    tgtId,
                    targetService,
                    1L,
                    "");
            authBytes = JsonSerializer.toBytes(auth);
            encryptedAuth = AesGcmCipher.encrypt(kaTgsBytes, authBytes);
            String authBase64 = Base64.getEncoder().encodeToString(encryptedAuth);

            // 3. Tạo ST_REQUEST
            StRequest req = new StRequest(tgtBase64, authBase64, targetService);

            // Ký Proof-of-Possession (PoP) cho ST
            String dataToSignSt = tgtBase64 + "|" + authBase64 + "|" + targetService;
            java.security.Signature sigSt = java.security.Signature.getInstance("SHA256withRSA");
            sigSt.initSign(PkiManager.getPrivateKey());
            sigSt.update(dataToSignSt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            req.setSignature(Base64.getEncoder().encodeToString(sigSt.sign()));

            reqBytes = JsonSerializer.toBytes(req);
            PacketFrame frame = new PacketFrame(PacketFrame.TYPE_ST_REQUEST, (byte) 1, (short) 0, reqBytes);

            log.info("Gửi ST_REQUEST lên TGS...");
            PacketFrame response = SocketClient.sendRequest(ServerConfig.TGS_HOST, ServerConfig.TGS_PORT, frame);

            if (response.getType() == PacketFrame.TYPE_ST_RESPONSE) {
                log.info("Nhận được ST_RESPONSE. Đang giải mã...");
                StResponse stResp = JsonSerializer.fromBytes(response.getPayload(), StResponse.class);

                // Giải mã response inner
                cipherBytes = Base64.getDecoder().decode(stResp.getResponse());
                innerBytes = AesGcmCipher.decrypt(kaTgsBytes, cipherBytes);
                StResponseInner inner = JsonSerializer.fromBytes(innerBytes, StResponseInner.class);

                if (!authNonce.equals(inner.getNonce())) {
                    throw new Exception("Nonce trong ST_RESPONSE không khớp.");
                }

                // Lưu ST và K_A_Chat
                String stCacheData = stResp.getSt() + "|||" + inner.getSessionKey()
                        + "|||" + nullToEmpty(inner.getStId());
                byte[] stCacheBytes = stCacheData.getBytes(StandardCharsets.UTF_8);
                try {
                    TicketCache.saveTicket(username, "ST_" + targetService, stCacheBytes, password);
                } finally {
                    Arrays.fill(stCacheBytes, (byte) 0);
                }
                log.info("Xác thực Kerberos TGS thành công! Đã lưu ST và SessionKey K_A_Chat.");
                clearFreshTgt();
                return;
            } else if (response.getType() == PacketFrame.TYPE_ERROR) {
                ErrorResponse errorResp = JsonSerializer.fromBytes(response.getPayload(), ErrorResponse.class);
                log.error("Server error: code={}, message={}", errorResp.getErrorCode(), errorResp.getMessage());
                if ("INVALID_TICKET".equals(errorResp.getErrorCode()) || "CRYPTO_ERROR".equals(errorResp.getErrorCode())) {
                    TicketCache.clearCache(username);
                }
                throw new Exception("Đăng nhập thất bại: " + errorResp.getMessage());
            } else {
                throw new Exception("Server trả về gói tin không hợp lệ thay vì ST_RESPONSE.");
            }
        } catch (Exception e) {
            log.error("Lỗi khi xin ST", e);
            throw new Exception("Đăng nhập thất bại khi xin ST: " + e.getMessage(), e);
        } finally {
            if (cachedData != null) Arrays.fill(cachedData, (byte) 0);
            if (kaTgsBytes != null) Arrays.fill(kaTgsBytes, (byte) 0);
            if (authBytes != null) Arrays.fill(authBytes, (byte) 0);
            if (encryptedAuth != null) Arrays.fill(encryptedAuth, (byte) 0);
            if (reqBytes != null) Arrays.fill(reqBytes, (byte) 0);
            if (cipherBytes != null) Arrays.fill(cipherBytes, (byte) 0);
            if (innerBytes != null) Arrays.fill(innerBytes, (byte) 0);
        }
    }

    private static String randomNonceBase64() {
        byte[] nonce = new byte[CryptoConstants.NONCE_SIZE_BYTES];
        new SecureRandom().nextBytes(nonce);
        return Base64.getEncoder().encodeToString(nonce);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void rememberFreshTgt(String username, String tgtBase64, byte[] kaTgsBytes, String tgtId) {
        clearFreshTgt();
        freshTgtUsername = username;
        freshTgtBase64 = tgtBase64;
        freshTgtId = nullToEmpty(tgtId);
        freshKaTgs = Arrays.copyOf(kaTgsBytes, kaTgsBytes.length);
    }

    private boolean hasFreshTgt(String username) {
        return username != null
                && username.equals(freshTgtUsername)
                && freshTgtBase64 != null
                && freshKaTgs != null;
    }

    private void clearFreshTgt() {
        if (freshKaTgs != null) {
            Arrays.fill(freshKaTgs, (byte) 0);
        }
        freshTgtUsername = null;
        freshTgtBase64 = null;
        freshTgtId = null;
        freshKaTgs = null;
    }
}
