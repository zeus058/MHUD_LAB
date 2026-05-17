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

                // 5. Lưu vé (gồm chuỗi TGT mã hoá và K_A_TGS) vào đĩa
                String cacheData = tgtResp.getTgt() + "|||" + inner.getSessionKey();
                byte[] cacheBytes = cacheData.getBytes(StandardCharsets.UTF_8);
                try {
                    TicketCache.saveTicket(username, "TGT", cacheBytes, password);
                } finally {
                    Arrays.fill(cacheBytes, (byte) 0);
                }

                log.info("Xác thực Kerberos AS thành công! Đã lưu TGT và SessionKey K_A_TGS.");
                return;
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
        try {
            // 1. Lấy TGT và K_A_TGS từ cache
            byte[] cachedData = TicketCache.getTicket(username, "TGT", password);
            if (cachedData == null) {
                TicketCache.clearCache(username);
                throw new Exception("Không đọc được TGT từ cache. Vui lòng đăng nhập lại.");
            }
            String cacheString = new String(cachedData, StandardCharsets.UTF_8);
            String[] parts = cacheString.split("\\|\\|\\|");
            if (parts.length != 2) {
                TicketCache.clearCache(username);
                throw new Exception("Dữ liệu TGT cache bị hỏng. Vui lòng đăng nhập lại.");
            }
            String tgtBase64 = parts[0];
            String kaTgsBase64 = parts[1];
            byte[] kaTgsBytes = Base64.getDecoder().decode(kaTgsBase64);

            // 2. Tạo Authenticator (timestamp tính bằng SECONDS, khớp với
            // ReplayDefenseService)
            long timestamp = NtpTimeClient.getCurrentNetworkTime() / 1000L;
            String authNonce = randomNonceBase64();
            AuthenticatorJson auth = new AuthenticatorJson(username, timestamp, authNonce);
            byte[] authBytes = JsonSerializer.toBytes(auth);
            byte[] encryptedAuth = AesGcmCipher.encrypt(kaTgsBytes, authBytes);
            String authBase64 = Base64.getEncoder().encodeToString(encryptedAuth);

            // 3. Tạo ST_REQUEST
            StRequest req = new StRequest(tgtBase64, authBase64, targetService);
            byte[] reqBytes = JsonSerializer.toBytes(req);
            PacketFrame frame = new PacketFrame(PacketFrame.TYPE_ST_REQUEST, (byte) 1, (short) 0, reqBytes);

            log.info("Gửi ST_REQUEST lên TGS...");
            PacketFrame response = SocketClient.sendRequest(ServerConfig.TGS_HOST, ServerConfig.TGS_PORT, frame);

            if (response.getType() == PacketFrame.TYPE_ST_RESPONSE) {
                log.info("Nhận được ST_RESPONSE. Đang giải mã...");
                StResponse stResp = JsonSerializer.fromBytes(response.getPayload(), StResponse.class);

                // Giải mã response inner
                byte[] cipherBytes = Base64.getDecoder().decode(stResp.getResponse());
                byte[] innerBytes = AesGcmCipher.decrypt(kaTgsBytes, cipherBytes);
                StResponseInner inner = JsonSerializer.fromBytes(innerBytes, StResponseInner.class);

                if (!authNonce.equals(inner.getNonce())) {
                    throw new Exception("Nonce trong ST_RESPONSE không khớp.");
                }

                // Lưu ST và K_A_Chat
                String stCacheData = stResp.getSt() + "|||" + inner.getSessionKey();
                byte[] stCacheBytes = stCacheData.getBytes(StandardCharsets.UTF_8);
                try {
                    TicketCache.saveTicket(username, "ST_" + targetService, stCacheBytes, password);
                } finally {
                    Arrays.fill(stCacheBytes, (byte) 0);
                }
                log.info("Xác thực Kerberos TGS thành công! Đã lưu ST và SessionKey K_A_Chat.");
                return;
            } else {
                throw new Exception("Server trả về gói tin không hợp lệ thay vì ST_RESPONSE.");
            }
        } catch (Exception e) {
            log.error("Lỗi khi xin ST", e);
            throw new Exception("Đăng nhập thất bại khi xin ST: " + e.getMessage(), e);
        }
    }

    private static String randomNonceBase64() {
        byte[] nonce = new byte[CryptoConstants.NONCE_SIZE_BYTES];
        new SecureRandom().nextBytes(nonce);
        return Base64.getEncoder().encodeToString(nonce);
    }
}
