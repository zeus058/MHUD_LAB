package vn.edu.hcmus.securechat.kdc.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;
import vn.edu.hcmus.securechat.common.crypto.CryptoConstants;
import vn.edu.hcmus.securechat.common.exception.CryptoException;
import vn.edu.hcmus.securechat.common.exception.InvalidTicketException;
import vn.edu.hcmus.securechat.common.exception.ProtocolException;
import vn.edu.hcmus.securechat.common.exception.ReplayAttackException;
import vn.edu.hcmus.securechat.common.crypto.RateLimitService;
import vn.edu.hcmus.securechat.common.crypto.ReplayDefenseService;
import vn.edu.hcmus.securechat.common.protocol.ControlVector;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.Role;
import vn.edu.hcmus.securechat.common.protocol.dto.AuthenticatorJson;
import vn.edu.hcmus.securechat.common.protocol.dto.StInner;
import vn.edu.hcmus.securechat.common.protocol.dto.StRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.StResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.StResponseInner;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtInner;
import vn.edu.hcmus.securechat.common.crypto.HybridEncryption;
import vn.edu.hcmus.securechat.kdc.crypto.KdcKeyManager;

/**
 * Ticket Granting Service (TGS) — Xử lý ST Request.
 *
 * Luồng theo BaoCao_SecureChat.md Giai đoạn 3:
 * 1. Client gửi StRequest (tgt, authenticator, targetServer)
 * 2. TGS giải mã TGT bằng PR_TGS → lấy K_A_TGS
 * 3. TGS dùng K_A_TGS giải mã Authenticator → validate timestamp + nonce
 * 4. TGS kiểm tra TGT chưa hết hạn
 * 5. TGS sinh session key K_A_Chat
 * 6. TGS tạo StInner, encrypt bằng PU_ChatServer (Hybrid Encrypt)
 * 7. TGS tạo StResponseInner, encrypt bằng K_A_TGS (AES-GCM)
 * 8. TGS gửi StResponse (st + response)
 */
public class TicketGrantingService {

    private static final Logger log = LoggerFactory.getLogger(TicketGrantingService.class);
    private static final Logger auditLog = LoggerFactory.getLogger("securechat.audit");

    private final KdcKeyManager keyManager;
    private final ReplayDefenseService replayDefense;
    private final RateLimitService rateLimiter = new RateLimitService();
    private final vn.edu.hcmus.securechat.kdc.storage.KdcStorage storage;

    public TicketGrantingService(KdcKeyManager keyManager, ReplayDefenseService replayDefense, vn.edu.hcmus.securechat.kdc.storage.KdcStorage storage) {
        this.keyManager = keyManager;
        this.replayDefense = replayDefense;
        this.storage = storage;
    }

    /**
     * Xử lý ST Request và trả về ST Response.
     *
     * @param request  StRequest từ Client
     * @param clientIp IP của Client (cho audit log)
     * @return StResponse chứa encrypted ST + encrypted response
     */
    public StResponse issueServiceTicket(StRequest request, String clientIp)
            throws ProtocolException, CryptoException, InvalidTicketException,
                   ReplayAttackException {

        byte[] sessionKeyTgs = null;
        byte[] sessionKeyChat = null;
        String rateClientId = "UNKNOWN";

        try {
            // 1. Validate request fields
            validateStRequest(request);
            rateLimiter.check("TGS_IP", clientIp, 20, Duration.ofMinutes(1));

            // 2. Giải mã TGT bằng TGS Private Key (Hybrid Decrypt)
            byte[] encryptedTgt = Base64.getDecoder().decode(request.getTgt());
            byte[] tgtBytes = HybridEncryption.decrypt(
                    keyManager.getTgsPrivateKey(), encryptedTgt);

            // 3. Deserialize TgtInner
            TgtInner tgtInner = JsonSerializer.fromBytes(tgtBytes, TgtInner.class);
            rateClientId = tgtInner.getClientId();
            rateLimiter.checkFailureCooldown("TGS_CLIENT_FAILURE", rateClientId);
            String effectiveTgtId = !isBlank(tgtInner.getTgtId())
                    ? tgtInner.getTgtId()
                    : ticketFingerprint(request.getTgt());
            log.info("TGT decrypted: clientId={}, expires={}",
                    tgtInner.getClientId(),
                    Instant.ofEpochSecond(tgtInner.getExpiresAt()));

            // 4. Kiểm tra TGT chưa hết hạn
            long now = Instant.now().getEpochSecond();
            if (now > tgtInner.getExpiresAt()) {
                auditLog.warn("ST_REJECTED clientId={} ip={} reason=TGT_EXPIRED",
                        tgtInner.getClientId(), clientIp);
                throw new InvalidTicketException(
                        "TGT expired at " + Instant.ofEpochSecond(tgtInner.getExpiresAt()));
            }

            // 5. Lấy session key K_A_TGS từ TGT
            sessionKeyTgs = Base64.getDecoder().decode(tgtInner.getSessionKey());

            // 6. Giải mã Authenticator bằng K_A_TGS (AES-GCM)
            byte[] encryptedAuth = Base64.getDecoder().decode(request.getAuthenticator());
            byte[] authBytes = AesGcmCipher.decrypt(sessionKeyTgs, encryptedAuth);

            // 7. Deserialize Authenticator
            AuthenticatorJson authenticator = JsonSerializer.fromBytes(
                    authBytes, AuthenticatorJson.class);

            // 8. Validate Authenticator (timestamp skew + nonce uniqueness)
            replayDefense.validateAuthenticator(authenticator);
            log.info("Authenticator validated: clientId={}", authenticator.getClientId());

            // 9. Xác minh clientId trong Authenticator khớp với TGT
            if (!tgtInner.getClientId().equals(authenticator.getClientId())) {
                auditLog.error("ST_REJECTED reason=CLIENT_ID_MISMATCH tgt={} auth={}",
                        tgtInner.getClientId(), authenticator.getClientId());
                throw new InvalidTicketException(
                        "ClientId mismatch: TGT=" + tgtInner.getClientId()
                        + " Authenticator=" + authenticator.getClientId());
            }

            // 9.5. Xác thực Proof-of-Possession (PoP) cho ST Request
            if (request.getSignature() == null || request.getSignature().isBlank()) {
                throw new ProtocolException("ST request missing Proof-of-Possession signature");
            }
            if (tgtInner.getClientCert() == null || tgtInner.getClientCert().isBlank()) {
                throw new InvalidTicketException("TGT missing client certificate information");
            }
            byte[] clientCertDer = Base64.getDecoder().decode(tgtInner.getClientCert());
            java.security.cert.X509Certificate clientCert = keyManager.decodeCertificate(clientCertDer);
            
            String dataToVerifySt = request.getTgt() + "|" + request.getAuthenticator() + "|" + request.getTargetServer();
            java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
            sig.initVerify(clientCert.getPublicKey());
            sig.update(dataToVerifySt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] sigBytes = Base64.getDecoder().decode(request.getSignature());
            if (!sig.verify(sigBytes)) {
                throw new ProtocolException("ST request Proof-of-Possession signature verification failed");
            }
            log.info("Proof-of-Possession signature verified successfully in TGS for client: {}", tgtInner.getClientId());

            // 10. Sinh session key K_A_Chat (32 bytes, SecureRandom)
            sessionKeyChat = new byte[CryptoConstants.AES_KEY_SIZE_BYTES];
            new SecureRandom().nextBytes(sessionKeyChat);
            String sessionKeyChatB64 = Base64.getEncoder().encodeToString(sessionKeyChat);

            // 11. Tạo StInner
            long expiresAt = now + CryptoConstants.ST_LIFETIME_SECONDS;
            String stId = UUID.randomUUID().toString();
            String clientPubKeyB64 = Base64.getEncoder().encodeToString(clientCert.getPublicKey().getEncoded());

            StInner stInner = new StInner(
                    stId,
                    tgtInner.getClientId(),
                    clientPubKeyB64,
                    request.getTargetServer(),
                    now,
                    expiresAt,
                    sessionKeyChatB64,
                    ControlVector.ST_CV,
                    clientCert.getSerialNumber().toString(16),
                    tgtInner.getRole()
            );

            // 12. Serialize StInner → JSON bytes
            byte[] stInnerBytes = JsonSerializer.toBytes(stInner);

            // 13. Hybrid Encrypt StInner bằng PU_TargetServer
            java.security.PublicKey targetPublicKey;
            if (vn.edu.hcmus.securechat.common.config.ServerConfig.NOTIFICATION_SERVICE_ID.equals(request.getTargetServer())) {
                targetPublicKey = keyManager.getNotificationServerPublicKey();
            } else {
                targetPublicKey = keyManager.getChatServerPublicKey();
            }

            byte[] encryptedSt = HybridEncryption.encryptForWindowsKeyStoreRecipient(
                    targetPublicKey, stInnerBytes);
            String stB64 = Base64.getEncoder().encodeToString(encryptedSt);

            // 14. Tạo StResponseInner (chứa K_A_Chat cho Client)
            StResponseInner responseInner = new StResponseInner(
                    sessionKeyChatB64,
                    authenticator.getNonce(),
                    request.getTargetServer(),
                    stId,
                    now,
                    expiresAt
            );

            // 15. Serialize ResponseInner → JSON bytes
            byte[] responseInnerBytes = JsonSerializer.toBytes(responseInner);

            // 16. Encrypt ResponseInner bằng K_A_TGS (AES-GCM)
            byte[] encryptedResponse = AesGcmCipher.encrypt(sessionKeyTgs, responseInnerBytes);
            String responseB64 = Base64.getEncoder().encodeToString(encryptedResponse);

            // 17. Audit log v\u00e0 DB record
            auditLog.info("ST_ISSUED clientId={} targetServer={} ip={} issued={} expires={}",
                    tgtInner.getClientId(), request.getTargetServer(),
                    clientIp, now, expiresAt);

            try {
                storage.recordStIssued(tgtInner.getClientId(), request.getTargetServer(), now, expiresAt, clientIp, ControlVector.ST_CV);
                storage.logAuditEvent("ST_ISSUED", tgtInner.getClientId(), clientIp, "ST issued to " + request.getTargetServer(), true);

                vn.edu.hcmus.securechat.common.crypto.SecureLogChain.logEvent(
                        tgtInner.getClientId(),
                        clientCert.getSerialNumber().toString(16),
                        stId,
                        request.getTargetServer(),
                        "ST_REQUEST",
                        "SUCCESS",
                        "ST successfully issued from TGT " + effectiveTgtId
                );
            } catch (Exception e) {
                log.warn("Failed to record ST issuance in storage or secure log chain", e);
            }

            // 18. Server Proof (Mutual Authentication) — TGS k\u00fd response b\u1eb1ng TGS private key
            String dataToSignTgs = tgtInner.getClientId() + "|" + authenticator.getNonce() + "|" + responseB64;
            java.security.Signature serverSig = java.security.Signature.getInstance("SHA256withRSA");
            serverSig.initSign(keyManager.getTgsPrivateKey());
            serverSig.update(dataToSignTgs.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String serverSignature = Base64.getEncoder().encodeToString(serverSig.sign());
            String tgsCertB64 = Base64.getEncoder().encodeToString(keyManager.getTgsCertificate().getEncoded());

            log.info("ST issued for client={}, target={}, expires={} (mutual auth proof attached)",
                    tgtInner.getClientId(), request.getTargetServer(),
                    Instant.ofEpochSecond(expiresAt));
            rateLimiter.recordSuccess("TGS_CLIENT_FAILURE", tgtInner.getClientId());

            return new StResponse(stB64, responseB64, serverSignature, tgsCertB64);


        } catch (ProtocolException | CryptoException e) {
            rateLimiter.recordFailure("TGS_CLIENT_FAILURE", rateClientId, 5, Duration.ofMinutes(15));
            try {
                vn.edu.hcmus.securechat.common.crypto.SecureLogChain.logEvent(
                        "UNKNOWN",
                        "N/A",
                        "N/A",
                        request != null ? request.getTargetServer() : "N/A",
                        "ST_REQUEST",
                        "FAILED",
                        e.getMessage()
                );
            } catch (Exception ex) {
                log.warn("Failed to write failure to secure log chain", ex);
            }
            throw e;
        } catch (Exception e) {
            rateLimiter.recordFailure("TGS_CLIENT_FAILURE", rateClientId, 5, Duration.ofMinutes(15));
            try {
                vn.edu.hcmus.securechat.common.crypto.SecureLogChain.logEvent(
                        "UNKNOWN",
                        "N/A",
                        "N/A",
                        request != null ? request.getTargetServer() : "N/A",
                        "ST_REQUEST",
                        "FAILED",
                        e.getMessage()
                );
            } catch (Exception ex) {
                log.warn("Failed to write failure to secure log chain", ex);
            }
            throw new RuntimeException(e);
        } finally {
            // Xóa session keys khỏi bộ nhớ
            if (sessionKeyTgs != null) Arrays.fill(sessionKeyTgs, (byte) 0);
            if (sessionKeyChat != null) Arrays.fill(sessionKeyChat, (byte) 0);
        }
    }

    /**
     * Validate ST request fields.
     */
    private void validateStRequest(StRequest request) throws ProtocolException {
        if (request.getTgt() == null || request.getTgt().isBlank()) {
            throw new ProtocolException("ST request missing TGT");
        }
        if (request.getAuthenticator() == null || request.getAuthenticator().isBlank()) {
            throw new ProtocolException("ST request missing Authenticator");
        }
        if (request.getTargetServer() == null || request.getTargetServer().isBlank()) {
            throw new ProtocolException("ST request missing targetServer");
        }
    }

    private static String ticketFingerprint(String ticketBase64) throws CryptoException {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Base64.getDecoder().decode(ticketBase64));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new CryptoException("Failed to fingerprint ticket", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
