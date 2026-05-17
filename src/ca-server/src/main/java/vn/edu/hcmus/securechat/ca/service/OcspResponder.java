package vn.edu.hcmus.securechat.ca.service;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.ca.storage.CertificateStorage;
import vn.edu.hcmus.securechat.ca.storage.CertificateStorage.CertStatus;
import vn.edu.hcmus.securechat.ca.storage.CertificateStorage.CertificateInfo;
import vn.edu.hcmus.securechat.common.exception.PkiException;
import vn.edu.hcmus.securechat.common.protocol.dto.OcspRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.OcspResponse;

/**
 * OcspResponder — Xử lý Online Certificate Status Protocol (OCSP).
 *
 * Chức năng:
 * - Kiểm tra trạng thái chứng chỉ (GOOD, REVOKED, UNKNOWN)
 * - Tạo OCSP response ký số
 * - Hỗ trợ nextUpdate (khi nên refresh status)
 *
 * Theo RFC 6960 (OCSP v1).
 */
public class OcspResponder {

    private static final Logger log = LoggerFactory.getLogger(OcspResponder.class);
    private static final Logger auditLog = LoggerFactory.getLogger("securechat.audit");

    // OCSP response hợp lệ trong 4 giờ
    private static final long OCSP_RESPONSE_VALIDITY_MS = 4 * 60 * 60 * 1000;

    private final CertificateStorage certStorage;
    private final PrivateKey ocspPrivateKey;
    private final X509Certificate ocspCertificate;

    public OcspResponder(CertificateStorage certStorage,
            PrivateKey ocspPrivateKey,
            X509Certificate ocspCertificate) {
        this.certStorage = certStorage;
        this.ocspPrivateKey = ocspPrivateKey;
        this.ocspCertificate = ocspCertificate;
    }

    /**
     * Xử lý OCSP request và tạo response.
     *
     * @param request OcspRequest từ client
     * @return OcspResponse (signed)
     */
    public OcspResponse respondToOcspRequest(OcspRequest request) throws PkiException {
        try {
            String certSerial = request.getCertSerial();
            log.info("OCSP request for certificate: {}", certSerial);
            auditLog.info("OCSP_REQUEST serial={}", certSerial);

            // Kiểm tra trạng thái chứng chỉ
            CertStatus status = certStorage.getCertificateStatus(certSerial);

            long now = System.currentTimeMillis();
            long nextUpdate = now + OCSP_RESPONSE_VALIDITY_MS;

            OcspResponse response = new OcspResponse();
            response.setThisUpdate(now);
            response.setNextUpdate(nextUpdate);
            response.setProducedAt(now);

            // Lấy thông tin chi tiết
            switch (status) {
                case GOOD -> {
                    response.setCertStatus("GOOD");
                    response.setRevokedAt(null);
                    response.setRevocationReason(null);
                    log.info("OCSP response: serial={}, status=GOOD", certSerial);
                    auditLog.info("OCSP_RESPONSE serial={} status=GOOD", certSerial);
                }

                case REVOKED -> {
                    response.setCertStatus("REVOKED");
                    var certInfo = certStorage.getCertificateInfo(certSerial);
                    if (certInfo.isPresent()) {
                        CertificateInfo info = certInfo.get();
                        response.setRevokedAt(info.revocationTime);
                        response.setRevocationReason(
                                info.revocationReason != null ? info.revocationReason : "unspecified");
                    }
                    log.warn("OCSP response: serial={}, status=REVOKED, reason={}",
                            certSerial, response.getRevocationReason());
                    auditLog.warn("OCSP_RESPONSE serial={} status=REVOKED reason={}",
                            certSerial, response.getRevocationReason());
                }

                case UNKNOWN -> {
                    response.setCertStatus("UNKNOWN");
                    response.setRevokedAt(null);
                    response.setRevocationReason(null);
                    log.warn("OCSP response: serial={}, status=UNKNOWN", certSerial);
                    auditLog.warn("OCSP_RESPONSE serial={} status=UNKNOWN", certSerial);
                }
            }

            // Ký OCSP response (dùng SHA256withRSA)
            byte[] responseBody = createResponseBody(response);
            byte[] signature = signOcspResponse(responseBody);
            response.setSignature(Base64.getEncoder().encodeToString(signature));

            return response;

        } catch (Exception e) {
            log.error("OCSP response generation failed", e);
            auditLog.error("OCSP_FAILED error={}", e.getMessage());
            throw new PkiException("OCSP response generation failed", e);
        }
    }

    /**
     * Tạo response body (chứa các trường cần ký).
     * Trong thực tế, body này là DER-encoded OCSPResponse theo RFC 6960.
     * Tạm thời dùng JSON serialization cho simplified version.
     */
    private byte[] createResponseBody(OcspResponse response) {
        // Tạo string từ các trường quan trọng
        String body = response.getCertStatus() + "|" +
                response.getThisUpdate() + "|" +
                response.getNextUpdate() + "|" +
                (response.getRevokedAt() != null ? response.getRevokedAt() : "0") + "|" +
                (response.getRevocationReason() != null ? response.getRevocationReason() : "");
        return body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Ký OCSP response bằng OCSP Private Key.
     * Sử dụng SHA256withRSA.
     */
    private byte[] signOcspResponse(byte[] responseBody) throws Exception {
        java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
        sig.initSign(ocspPrivateKey);
        sig.update(responseBody);
        return sig.sign();
    }

    /**
     * Verify OCSP response signature (dùng OCSP certificate public key).
     * Có thể dùng để kiểm tra response từ server khác.
     */
    public boolean verifyOcspResponseSignature(OcspResponse response, byte[] responseBody) {
        try {
            java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
            sig.initVerify(ocspCertificate.getPublicKey());
            sig.update(responseBody);

            byte[] signature = Base64.getDecoder().decode(response.getSignature());
            return sig.verify(signature);

        } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException
                | java.security.SignatureException | IllegalArgumentException e) {
            log.error("OCSP response signature verification failed", e);
            return false;
        }
    }

    /**
     * Kiểm tra xem OCSP response còn hợp lệ không (chưa hết hạn).
     */
    public boolean isOcspResponseValid(OcspResponse response) {
        long now = System.currentTimeMillis();
        return response.getNextUpdate() > now;
    }
}
