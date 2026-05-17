package vn.edu.hcmus.securechat.kdc.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;
import vn.edu.hcmus.securechat.common.crypto.CryptoConstants;
import vn.edu.hcmus.securechat.common.exception.CryptoException;
import vn.edu.hcmus.securechat.common.exception.InvalidTicketException;
import vn.edu.hcmus.securechat.common.exception.ProtocolException;
import vn.edu.hcmus.securechat.common.exception.ReplayAttackException;
import vn.edu.hcmus.securechat.common.crypto.ReplayDefenseService;
import vn.edu.hcmus.securechat.common.protocol.ControlVector;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
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

        try {
            // 1. Validate request fields
            validateStRequest(request);

            // 2. Giải mã TGT bằng TGS Private Key (Hybrid Decrypt)
            byte[] encryptedTgt = Base64.getDecoder().decode(request.getTgt());
            byte[] tgtBytes = HybridEncryption.decrypt(
                    keyManager.getTgsPrivateKey(), encryptedTgt);

            // 3. Deserialize TgtInner
            TgtInner tgtInner = JsonSerializer.fromBytes(tgtBytes, TgtInner.class);
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

            // 10. Sinh session key K_A_Chat (32 bytes, SecureRandom)
            sessionKeyChat = new byte[CryptoConstants.AES_KEY_SIZE_BYTES];
            new SecureRandom().nextBytes(sessionKeyChat);
            String sessionKeyChatB64 = Base64.getEncoder().encodeToString(sessionKeyChat);

            // 11. Tạo StInner
            long expiresAt = now + CryptoConstants.ST_LIFETIME_SECONDS;
            String clientPubKeyB64 = ""; // Client public key sẽ được trích từ TGT nếu cần

            StInner stInner = new StInner(
                    tgtInner.getClientId(),
                    clientPubKeyB64,
                    request.getTargetServer(),
                    now,
                    expiresAt,
                    sessionKeyChatB64,
                    ControlVector.ST_CV
            );

            // 12. Serialize StInner → JSON bytes
            byte[] stInnerBytes = JsonSerializer.toBytes(stInner);

            // 13. Hybrid Encrypt StInner bằng PU_ChatServer
            byte[] encryptedSt = HybridEncryption.encrypt(
                    keyManager.getChatServerPublicKey(), stInnerBytes);
            String stB64 = Base64.getEncoder().encodeToString(encryptedSt);

            // 14. Tạo StResponseInner (chứa K_A_Chat cho Client)
            StResponseInner responseInner = new StResponseInner(
                    sessionKeyChatB64,
                    authenticator.getNonce(),
                    request.getTargetServer()
            );

            // 15. Serialize ResponseInner → JSON bytes
            byte[] responseInnerBytes = JsonSerializer.toBytes(responseInner);

            // 16. Encrypt ResponseInner bằng K_A_TGS (AES-GCM)
            byte[] encryptedResponse = AesGcmCipher.encrypt(sessionKeyTgs, responseInnerBytes);
            String responseB64 = Base64.getEncoder().encodeToString(encryptedResponse);

            // 17. Audit log và DB record
            auditLog.info("ST_ISSUED clientId={} targetServer={} ip={} issued={} expires={}",
                    tgtInner.getClientId(), request.getTargetServer(),
                    clientIp, now, expiresAt);

            try {
                storage.recordStIssued(tgtInner.getClientId(), request.getTargetServer(), now, expiresAt, clientIp, ControlVector.ST_CV);
                storage.logAuditEvent("ST_ISSUED", tgtInner.getClientId(), clientIp, "ST issued to " + request.getTargetServer(), true);
            } catch (Exception e) {
                log.warn("Failed to record ST issuance in storage", e);
            }

            log.info("ST issued for client={}, target={}, expires={}",
                    tgtInner.getClientId(), request.getTargetServer(),
                    Instant.ofEpochSecond(expiresAt));

            return new StResponse(stB64, responseB64);

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
}
