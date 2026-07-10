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
import vn.edu.hcmus.securechat.common.protocol.Role;
import vn.edu.hcmus.securechat.common.protocol.dto.RenewTgtRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.AuthenticatorJson;
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

            // 5.5. Determine Role
            Role role = determineRole(request.getClientId());

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
                    role
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

            // 12. Server Proof (Mutual Authentication) — AS k\u00fd response b\u1eb1ng AS private key
            // Client d\u00f9ng asCertificate \u0111\u1ec3 verify signature m\u00e0 kh\u00f4ng c\u1ea7n bi\u1ebft tr\u01b0\u1edbc
            String dataToSign = request.getClientId() + "|" + request.getNonce() + "|" + responseB64;
            java.security.Signature serverSig = java.security.Signature.getInstance("SHA256withRSA");
            serverSig.initSign(keyManager.getAsPrivateKey());
            serverSig.update(dataToSign.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String serverSignature = Base64.getEncoder().encodeToString(serverSig.sign());
            String asCertB64 = Base64.getEncoder().encodeToString(keyManager.getAsCertificate().getEncoded());

            // 13. Audit log v\u00e0 DB record
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

            log.info("TGT issued for client={}, expires={} (mutual auth proof attached)",
                    request.getClientId(), Instant.ofEpochSecond(expiresAt));
            rateLimiter.recordSuccess("AS_CLIENT_FAILURE", request.getClientId());

            return new TgtResponse(tgtB64, responseB64, serverSignature, asCertB64);


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
            // X\u00f3a session key kh\u1ecfi b\u1ed9 nh\u1edb
            if (sessionKey != null) Arrays.fill(sessionKey, (byte) 0);
        }
    }

    public TgtResponse renewTgt(RenewTgtRequest request, String clientIp) throws Exception {
        byte[] sessionKeyTgs = null;
        byte[] newSessionKey = null;
        
        try {
            rateLimiter.check("AS_RENEW_IP", clientIp, 20, Duration.ofMinutes(1));
            
            // 1. Gi\u1ea3i m\u00e3 oldTgt
            byte[] encryptedOldTgt = Base64.getDecoder().decode(request.getOldTgt());
            byte[] tgtBytes = HybridEncryption.decrypt(keyManager.getTgsPrivateKey(), encryptedOldTgt);
            TgtInner oldTgt = JsonSerializer.fromBytes(tgtBytes, TgtInner.class);
            
            rateLimiter.checkFailureCooldown("AS_RENEW_FAILURE", oldTgt.getClientId());

            if (!oldTgt.isRenewable()) {
                throw new ProtocolException("TGT is not renewable");
            }

            long now = Instant.now().getEpochSecond();
            if (now > oldTgt.getRenewTill()) {
                throw new vn.edu.hcmus.securechat.common.exception.InvalidTicketException("TGT renew period has expired (renewTill = " + Instant.ofEpochSecond(oldTgt.getRenewTill()) + ")");
            }

            sessionKeyTgs = Base64.getDecoder().decode(oldTgt.getSessionKey());

            // 2. Gi\u1ea3i m\u00e3 authenticator b\u1eb1ng K_A_TGS
            byte[] encryptedAuth = Base64.getDecoder().decode(request.getAuthenticator());
            byte[] authBytes = vn.edu.hcmus.securechat.common.crypto.AesGcmCipher.decrypt(sessionKeyTgs, encryptedAuth);
            AuthenticatorJson auth = JsonSerializer.fromBytes(authBytes, AuthenticatorJson.class);

            replayDefense.validateAuthenticator(auth);

            if (!oldTgt.getClientId().equals(auth.getClientId()) || !oldTgt.getClientId().equals(request.getClientId())) {
                throw new vn.edu.hcmus.securechat.common.exception.InvalidTicketException("ClientId mismatch in renew request");
            }

            // 3. PoP \u0111\u1ec3 ch\u1ee9ng minh ng\u01b0\u1eddi g\u1eedi th\u1ef1c s\u1ef1 c\u00f3 private key (trust on first use ho\u1eb7c \u0111\u1ecdc l\u1ea1i t\u1eeb DB/CA)
            // L\u1ea5y cert t\u1eeb TGT c\u0169
            byte[] clientCertDer = Base64.getDecoder().decode(oldTgt.getClientCert());
            X509Certificate clientCert = keyManager.decodeCertificate(clientCertDer);
            
            // OCSP Check cert
            String certSerial = clientCert.getSerialNumber().toString(16);
            String issuerDn = clientCert.getIssuerX500Principal().getName();
            try {
                ocspClient.verifyCertificateStatus(certSerial, issuerDn);
            } catch (CertificateRevokedException e) {
                log.error("Certificate is revoked during renew: {}", e.getMessage());
                throw e;
            } catch (PkiException e) {
                log.warn("OCSP check failed during renew (CA may be unavailable), continuing: {}", e.getMessage());
            }

            String dataToVerify = request.getOldTgt() + "|" + request.getAuthenticator() + "|" + request.getClientId();
            java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
            sig.initVerify(clientCert.getPublicKey());
            sig.update(dataToVerify.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] sigBytes = Base64.getDecoder().decode(request.getSignature());
            if (!sig.verify(sigBytes)) {
                throw new ProtocolException("RenewTgt request Proof-of-Possession signature verification failed");
            }

            // 4. Sinh new session key
            newSessionKey = new byte[CryptoConstants.AES_KEY_SIZE_BYTES];
            new SecureRandom().nextBytes(newSessionKey);
            String sessionKeyB64 = Base64.getEncoder().encodeToString(newSessionKey);

            // 5. T\u1ea1o new TGT
            long expiresAt = now + CryptoConstants.TGT_LIFETIME_SECONDS;
            String newTgtId = UUID.randomUUID().toString();

            TgtInner newTgt = new TgtInner(
                    newTgtId,
                    oldTgt.getClientId(),
                    oldTgt.getTargetTgs(),
                    now,
                    expiresAt,
                    oldTgt.getRenewTill(), // gi\u1eef nguy\u00ean renewTill
                    sessionKeyB64,
                    true,
                    TGT_CV,
                    oldTgt.getClientCert(),
                    oldTgt.getRole()
            );

            byte[] tgtInnerBytes = JsonSerializer.toBytes(newTgt);
            byte[] encryptedTgt = HybridEncryption.encryptForWindowsKeyStoreRecipient(keyManager.getTgsPublicKey(), tgtInnerBytes);
            String tgtB64 = Base64.getEncoder().encodeToString(encryptedTgt);

            TgtResponseInner responseInner = new TgtResponseInner(
                    sessionKeyB64,
                    auth.getNonce(),
                    oldTgt.getTargetTgs(),
                    newTgtId,
                    now,
                    expiresAt,
                    oldTgt.getRenewTill()
            );

            byte[] responseInnerBytes = JsonSerializer.toBytes(responseInner);
            byte[] encryptedResponse = HybridEncryption.encrypt(clientCert.getPublicKey(), responseInnerBytes);
            String responseB64 = Base64.getEncoder().encodeToString(encryptedResponse);

            // Mutual auth
            String dataToSign = request.getClientId() + "|" + auth.getNonce() + "|" + responseB64;
            java.security.Signature serverSig = java.security.Signature.getInstance("SHA256withRSA");
            serverSig.initSign(keyManager.getAsPrivateKey());
            serverSig.update(dataToSign.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String serverSignature = Base64.getEncoder().encodeToString(serverSig.sign());
            String asCertB64 = Base64.getEncoder().encodeToString(keyManager.getAsCertificate().getEncoded());

            auditLog.info("TGT_RENEWED clientId={} ip={} issued={} expires={}", request.getClientId(), clientIp, now, expiresAt);
            storage.recordTgtIssued(request.getClientId(), oldTgt.getTargetTgs(), now, expiresAt, clientIp, TGT_CV);
            rateLimiter.recordSuccess("AS_RENEW_FAILURE", request.getClientId());

            return new TgtResponse(tgtB64, responseB64, serverSignature, asCertB64);
            
        } catch (Exception e) {
            rateLimiter.recordFailure("AS_RENEW_FAILURE", request != null ? request.getClientId() : "UNKNOWN", 5, Duration.ofMinutes(15));
            throw e;
        } finally {
            if (sessionKeyTgs != null) Arrays.fill(sessionKeyTgs, (byte) 0);
            if (newSessionKey != null) Arrays.fill(newSessionKey, (byte) 0);
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

    private Role determineRole(String clientId) {
        if (clientId != null && (clientId.toLowerCase().contains("admin") || clientId.equalsIgnoreCase("admin@example.com"))) {
            return Role.ADMIN;
        }
        return Role.USER;
    }
}
