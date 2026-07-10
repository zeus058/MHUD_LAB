package vn.edu.hcmus.securechat.ca.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.ca.storage.CertificateStorage;
import vn.edu.hcmus.securechat.common.crypto.NonceCache;
import vn.edu.hcmus.securechat.common.crypto.RateLimitService;
import vn.edu.hcmus.securechat.common.exception.ProtocolException;
import vn.edu.hcmus.securechat.common.protocol.dto.CertificateSigningRequest;

/**
 * Registration Authority (RA) — Tầng kiểm soát đăng ký trước CA.
 *
 * Trách nhiệm:
 * 1. Rate limit: mỗi IP tối đa 5 registrations/giờ
 * 2. Nonce uniqueness: mỗi nonce CSR chỉ được dùng 1 lần
 * 3. Policy check: subjectDn phải theo format "CN=<username>,O=SecureChat,C=VN"
 * 4. Duplicate check: clientId chưa có cert active thì mới cho đăng ký
 * 5. Forward sang CertificateAuthority.issueCertificate()
 *
 * RA KHÔNG giữ CA private key — chỉ gọi CA thông qua interface.
 */
public class RegistrationAuthority {

    private static final Logger log = LoggerFactory.getLogger(RegistrationAuthority.class);
    private static final Logger auditLog = LoggerFactory.getLogger("securechat.audit");

    private static final String DN_REGEX = "CN=.+,O=SecureChat,C=VN(?:,OU=.+)?";
    private static final int MAX_REGISTRATIONS_PER_HOUR = 5;

    private final CertificateAuthority ca;
    private final CertificateStorage certStorage;
    private final RateLimitService rateLimiter;
    private final NonceCache csrNonceCache;

    public RegistrationAuthority(CertificateAuthority ca, CertificateStorage certStorage) {
        this.ca = ca;
        this.certStorage = certStorage;
        this.rateLimiter = new RateLimitService();
        this.csrNonceCache = new NonceCache();
    }

    /**
     * Xử lý đăng ký: validate (RA policy) → issue cert qua CA.
     *
     * @param csr             CSR từ client (đã được verify signature ở CaServerMain)
     * @param clientIp        IP của client (cho rate limit)
     * @param subjectPublicKey Public key đã decode từ CSR
     * @return DER-encoded X.509 certificate
     * @throws ProtocolException nếu vi phạm policy
     * @throws Exception         nếu CA gặp lỗi khi ký cert
     */
    public byte[] processCsrRequest(CertificateSigningRequest csr, String clientIp,
                                    java.security.PublicKey subjectPublicKey)
            throws Exception {

        // 1. Rate limit theo IP
        rateLimiter.check("RA_IP", clientIp, MAX_REGISTRATIONS_PER_HOUR, Duration.ofHours(1));

        // 2. Nonce uniqueness (chống replay CSR)
        String nonceKey = "CSR_NONCE:" + csr.getNonce();
        if (csrNonceCache.contains(nonceKey)) {
            auditLog.warn("RA_REJECTED ip={} reason=NONCE_REPLAY nonce={}", clientIp, csr.getNonce());
            throw new ProtocolException("RA_REJECTED: CSR nonce already used: " + csr.getNonce());
        }
        csrNonceCache.put(nonceKey);

        // 3. SubjectDn policy check — bắt buộc format CN=<id>,O=SecureChat,C=VN
        if (csr.getSubjectDn() == null || !csr.getSubjectDn().matches(DN_REGEX)) {
            auditLog.warn("RA_REJECTED ip={} reason=INVALID_DN dn={}", clientIp, csr.getSubjectDn());
            throw new ProtocolException(
                "RA_REJECTED: subjectDn must match 'CN=<id>,O=SecureChat,C=VN[,OU=<name>]' but got: " + csr.getSubjectDn());
        }

        // 4. Duplicate check: chưa có cert active cho clientId này
        String clientId = extractCn(csr.getSubjectDn());
        if (certStorage.hasActiveCertificate(clientId)) {
            auditLog.warn("RA_REJECTED ip={} reason=DUPLICATE clientId={}", clientIp, clientId);
            throw new ProtocolException(
                "RA_REJECTED: Client '" + clientId + "' already has an active certificate. Revoke it first.");
        }

        // 5. Forward lên CA để ký
        byte[] certDer = ca.issueCertificate(csr.getSubjectDn(), subjectPublicKey);

        // 6. Audit log RA approval
        auditLog.info("RA_APPROVED clientId={} ip={} subjectDn={}", clientId, clientIp, csr.getSubjectDn());
        log.info("RA approved registration for clientId={}", clientId);

        rateLimiter.recordSuccess("RA_IP", clientIp);
        return certDer;
    }

    /**
     * Extract CN value từ X.500 DN string.
     * Ví dụ: "CN=alice,O=SecureChat,C=VN" → "alice"
     */
    private String extractCn(String subjectDn) {
        for (String part : subjectDn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("CN=")) {
                return trimmed.substring(3);
            }
        }
        return subjectDn;
    }
}
