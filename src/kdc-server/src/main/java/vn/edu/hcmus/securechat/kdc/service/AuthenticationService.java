package vn.edu.hcmus.securechat.kdc.service;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.crypto.CryptoConstants;
import vn.edu.hcmus.securechat.common.exception.CertificateRevokedException;
import vn.edu.hcmus.securechat.common.exception.ChainValidationException;
import vn.edu.hcmus.securechat.common.exception.CryptoException;
import vn.edu.hcmus.securechat.common.exception.PkiException;
import vn.edu.hcmus.securechat.common.exception.ProtocolException;
import vn.edu.hcmus.securechat.common.protocol.ControlVector;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtInner;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtResponseInner;
import vn.edu.hcmus.securechat.common.crypto.HybridEncryption;
import vn.edu.hcmus.securechat.common.crypto.RateLimitService;
import vn.edu.hcmus.securechat.common.crypto.ReplayDefenseService;
import vn.edu.hcmus.securechat.kdc.crypto.KdcKeyManager;

/**
 * Authentication Server (AS) — Xử lý TGT Request.
 *
 * Luồng theo BaoCao_SecureChat.md Giai đoạn 2:
 * 1. Client gửi TgtRequest (clientId, targetTgs, nonce, cert)
 * 2. AS xác minh chứng chỉ (chain validation + OCSP check)
 * 3. AS sinh session key K_A_TGS (32 bytes)
 * 4. AS tạo TgtInner, encrypt bằng PU_TGS (Hybrid Encrypt)
 * 5. AS tạo TgtResponseInner, encrypt bằng PU_client
 * 6. AS gửi TgtResponse (tgt + response)
 */
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    private static final Logger auditLog = LoggerFactory.getLogger("securechat.audit");

    private static final String TGT_CV = ControlVector.TGT_CV;

    private final KdcKeyManager keyManager;
    private final OcspClient ocspClient;
    private final ReplayDefenseService replayDefense;
    private final RateLimitService rateLimiter = new RateLimitService();
    private final vn.edu.hcmus.securechat.kdc.storage.KdcStorage storage;

    public AuthenticationService(KdcKeyManager keyManager, OcspClient ocspClient, ReplayDefenseService replayDefense, vn.edu.hcmus.securechat.kdc.storage.KdcStorage storage) {
        this.keyManager = keyManager;
        this.ocspClient = ocspClient;
        this.replayDefense = replayDefense;
        this.storage = storage;
    }

    /**
     * Xử lý TGT Request và trả về TGT Response.
     *
     * @param request TgtRequest từ Client
     * @param clientIp IP của Client (cho audit log)
     * @return TgtResponse chứa encrypted TGT + encrypted response
     */
    public TgtResponse issueTgt(TgtRequest request, String clientIp)
            throws ProtocolException, PkiException, CryptoException,
                   ChainValidationException, CertificateRevokedException {

        byte[] sessionKey = null;
        try {
            // 1. Validate request fields
            validateTgtRequest(request);
            rateLimiter.check("AS_IP", clientIp, 10, Duration.ofMinutes(1));
            rateLimiter.checkFailureCooldown("AS_CLIENT_FAILURE", request.getClientId());

            // 2. Decode và verify client certificate
            byte[] certDer = Base64.getDecoder().decode(request.getCert());
            X509Certificate clientCert = keyManager.decodeCertificate(certDer);

            // 3. Chain validation (leaf → Root CA)
            keyManager.validateCertificateChain(clientCert);
            log.info("Certificate chain validated for client: {}",
                    clientCert.getSubjectX500Principal().getName());

            // 4. OCSP check — kiểm tra cert còn hợp lệ không
            String certSerial = clientCert.getSerialNumber().toString(16);
            String issuerDn = clientCert.getIssuerX500Principal().getName();
            try {
                ocspClient.verifyCertificateStatus(certSerial, issuerDn);
            } catch (vn.edu.hcmus.securechat.common.exception.CertificateRevokedException e) {
                log.error("Certificate is revoked: {}", e.getMessage());
                throw e;
            } catch (vn.edu.hcmus.securechat.common.exception.PkiException e) {
                // Nếu CA không available, log warning và tiếp tục (graceful degradation)
                log.warn("OCSP check failed (CA may be unavailable), continuing: {}", e.getMessage());
                auditLog.warn("OCSP_UNAVAILABLE clientId={} ip={} reason={}",
                        request.getClientId(), clientIp, e.getMessage());
            }

            // 4.5. Xác thực Proof-of-Possession (PoP)
            if (request.getSignature() == null || request.getSignature().isBlank()) {
                throw new ProtocolException("TGT request missing Proof-of-Possession signature");
            }
            String dataToVerify = request.getClientId() + "|" + request.getTargetTgs() + "|" + request.getNonce() + "|" + request.getTimestamp();
            java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
            sig.initVerify(clientCert.getPublicKey());
            sig.update(dataToVerify.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] sigBytes = Base64.getDecoder().decode(request.getSignature());
            if (!sig.verify(sigBytes)) {
                throw new ProtocolException("TGT request Proof-of-Possession signature verification failed");
            }
            log.info("Proof-of-Possession signature verified successfully for client: {}", request.getClientId());

            // 4.6. Kiểm tra Replay Attack bằng ReplayDefenseService
            replayDefense.validate(request.getClientId(), request.getTimestamp(), request.getNonce());
            log.info("Replay attack check passed for client: {}", request.getClientId());

            // 5. Sinh session key K_A_TGS (32 bytes, SecureRandom)
            sessionKey = new byte[CryptoConstants.AES_KEY_SIZE_BYTES];
            new SecureRandom().nextBytes(sessionKey);
            String sessionKeyB64 = Base64.getEncoder().encodeToString(sessionKey);

            // 6. Tạo TgtInner
            long now = Instant.now().getEpochSecond();
            long expiresAt = now + CryptoConstants.TGT_LIFETIME_SECONDS;
            long renewTill = now + CryptoConstants.TICKET_RENEW_TILL_SECONDS;
            String tgtId = UUID.randomUUID().toString();

            TgtInner tgtInner = new TgtInner(
                    tgtId,
                    request.getClientId(),
                    request.getTargetTgs(),
                    now,
                    expiresAt,
                    renewTill,
                    sessionKeyB64,
                    true,   // renewable
                    TGT_CV,
                    request.getCert(),
                    request.getDilithiumCert()
            );

            // 7. Serialize TgtInner → JSON bytes
            byte[] tgtInnerBytes = JsonSerializer.toBytes(tgtInner);

            // 8. Hybrid Encrypt TgtInner bằng PU_TGS
            byte[] encryptedTgt = HybridEncryption.encryptForWindowsKeyStoreRecipient(
                    keyManager.getTgsPublicKey(), tgtInnerBytes);
            String tgtB64 = Base64.getEncoder().encodeToString(encryptedTgt);

            // 9. Tạo TgtResponseInner (chứa K_A_TGS cho Client)
            TgtResponseInner responseInner = new TgtResponseInner(
                    sessionKeyB64,
                    request.getNonce(),
                    request.getTargetTgs(),
                    tgtId,
                    now,
                    expiresAt,
                    renewTill
            );

            // 10. Serialize ResponseInner → JSON bytes
            byte[] responseInnerBytes = JsonSerializer.toBytes(responseInner);

            // 11. Hybrid Encrypt ResponseInner bằng PU_client
            byte[] encryptedResponse = HybridEncryption.encrypt(
                    clientCert.getPublicKey(), responseInnerBytes);
            String responseB64 = Base64.getEncoder().encodeToString(encryptedResponse);

            // 12. Audit log và DB record
            auditLog.info("TGT_ISSUED clientId={} ip={} issued={} expires={}",
                    request.getClientId(), clientIp, now, expiresAt);
            
            try {
                storage.recordTgtIssued(request.getClientId(), request.getTargetTgs(), now, expiresAt, clientIp, TGT_CV);
                storage.logAuditEvent("TGT_ISSUED", request.getClientId(), clientIp, "TGT issued", true);
                
                vn.edu.hcmus.securechat.common.crypto.SecureLogChain.logEvent(
                        request.getClientId(),
                        certSerial,
                        tgtId,
                        request.getTargetTgs(),
                        "TGT_REQUEST",
                        "SUCCESS",
                        "TGT successfully issued"
                );
            } catch (Exception e) {
                log.warn("Failed to record TGT issuance in storage or secure log chain", e);
            }

            log.info("TGT issued for client={}, expires={}",
                    request.getClientId(), Instant.ofEpochSecond(expiresAt));
            rateLimiter.recordSuccess("AS_CLIENT_FAILURE", request.getClientId());

            return new TgtResponse(tgtB64, responseB64);

        } catch (ProtocolException | vn.edu.hcmus.securechat.common.exception.PkiException | CryptoException e) {
            if (request != null) {
                rateLimiter.recordFailure("AS_CLIENT_FAILURE", request.getClientId(), 5, Duration.ofMinutes(15));
            }
            try {
                vn.edu.hcmus.securechat.common.crypto.SecureLogChain.logEvent(
                        request != null ? request.getClientId() : "UNKNOWN",
                        "N/A",
                        "N/A",
                        request != null ? request.getTargetTgs() : "N/A",
                        "TGT_REQUEST",
                        "FAILED",
                        e.getMessage()
                );
            } catch (Exception ex) {
                log.warn("Failed to write failure to secure log chain", ex);
            }
            throw e;
        } catch (Exception e) {
            if (request != null) {
                rateLimiter.recordFailure("AS_CLIENT_FAILURE", request.getClientId(), 5, Duration.ofMinutes(15));
            }
            try {
                vn.edu.hcmus.securechat.common.crypto.SecureLogChain.logEvent(
                        request != null ? request.getClientId() : "UNKNOWN",
                        "N/A",
                        "N/A",
                        request != null ? request.getTargetTgs() : "N/A",
                        "TGT_REQUEST",
                        "FAILED",
                        e.getMessage()
                );
            } catch (Exception ex) {
                log.warn("Failed to write failure to secure log chain", ex);
            }
            throw new RuntimeException(e);
        } finally {
            // Xóa session key khỏi bộ nhớ
            if (sessionKey != null) Arrays.fill(sessionKey, (byte) 0);
        }
    }

    /**
     * Validate TGT request fields.
     */
    private void validateTgtRequest(TgtRequest request) throws ProtocolException {
        if (request.getClientId() == null || request.getClientId().isBlank()) {
            throw new ProtocolException("TGT request missing clientId");
        }
        if (request.getTargetTgs() == null || request.getTargetTgs().isBlank()) {
            throw new ProtocolException("TGT request missing targetTgs");
        }
        if (request.getNonce() == null || request.getNonce().isBlank()) {
            throw new ProtocolException("TGT request missing nonce");
        }
        if (request.getCert() == null || request.getCert().isBlank()) {
            throw new ProtocolException("TGT request missing client certificate");
        }
    }
}
