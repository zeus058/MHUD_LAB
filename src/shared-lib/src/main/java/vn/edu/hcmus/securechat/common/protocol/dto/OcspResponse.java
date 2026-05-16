package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OCSP Response — OCSP Responder → Client/Server
 * JSON schema theo Contrains.md mục 2.3
 *
 * {
 *   "certStatus":     "string — GOOD | REVOKED | UNKNOWN",
 *   "thisUpdate":     "long — Unix timestamp (milliseconds) khi status được tạo",
 *   "nextUpdate":     "long — Unix timestamp (milliseconds) khi cần refresh",
 *   "revokedAt":      "long — Unix timestamp khi cert bị revoked (null nếu GOOD)",
 *   "revocationReason": "string — unspecified | keyCompromise | caCompromise | ..."
 *                        "(null nếu GOOD)",
 *   "producedAt":     "long — Unix timestamp khi response được ký",
 *   "signature":      "base64(RSA-PSS signature của toàn bộ response body)"
 * }
 */
public class OcspResponse {

    public enum CertStatus {
        GOOD,
        REVOKED,
        UNKNOWN
    }

    @JsonProperty("certStatus")
    private String certStatus;

    @JsonProperty("thisUpdate")
    private long thisUpdate;

    @JsonProperty("nextUpdate")
    private long nextUpdate;

    @JsonProperty("revokedAt")
    private Long revokedAt;

    @JsonProperty("revocationReason")
    private String revocationReason;

    @JsonProperty("producedAt")
    private long producedAt;

    @JsonProperty("signature")
    private String signature;

    public OcspResponse() {}

    public OcspResponse(String certStatus, long thisUpdate, long nextUpdate,
                       Long revokedAt, String revocationReason,
                       long producedAt, String signature) {
        this.certStatus = certStatus;
        this.thisUpdate = thisUpdate;
        this.nextUpdate = nextUpdate;
        this.revokedAt = revokedAt;
        this.revocationReason = revocationReason;
        this.producedAt = producedAt;
        this.signature = signature;
    }

    public String getCertStatus() { return certStatus; }
    public void setCertStatus(String certStatus) { this.certStatus = certStatus; }

    public long getThisUpdate() { return thisUpdate; }
    public void setThisUpdate(long thisUpdate) { this.thisUpdate = thisUpdate; }

    public long getNextUpdate() { return nextUpdate; }
    public void setNextUpdate(long nextUpdate) { this.nextUpdate = nextUpdate; }

    public Long getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Long revokedAt) { this.revokedAt = revokedAt; }

    public String getRevocationReason() { return revocationReason; }
    public void setRevocationReason(String revocationReason) { this.revocationReason = revocationReason; }

    public long getProducedAt() { return producedAt; }
    public void setProducedAt(long producedAt) { this.producedAt = producedAt; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
}
