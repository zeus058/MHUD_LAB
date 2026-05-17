package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TGT inner JSON (trước khi mã hóa bằng PU_TGS) — Contrains.md mục 2.3.
 *
 * {
 *   "clientId":   "string",
 *   "targetTgs":  "string",
 *   "issuedAt":   1715000000,
 *   "expiresAt":  1715028800,
 *   "sessionKey": "string — base64(K_A_TGS, 32 bytes AES-256 key)",
 *   "renewable":  true,
 *   "cv":         "string — ví dụ: ENCRYPT_ONLY|TGS_SERVICE|8H_EXPIRY"
 * }
 */
public class TgtInner {

    @JsonProperty("clientId")
    private String clientId;

    @JsonProperty("targetTgs")
    private String targetTgs;

    @JsonProperty("issuedAt")
    private long issuedAt;

    @JsonProperty("expiresAt")
    private long expiresAt;

    @JsonProperty("sessionKey")
    private String sessionKey;

    @JsonProperty("renewable")
    private boolean renewable;

    @JsonProperty("cv")
    private String cv;

    @JsonProperty("clientCert")
    private String clientCert; // Base64 of DER-encoded client certificate

    public TgtInner() {}

    public TgtInner(String clientId, String targetTgs, long issuedAt, long expiresAt,
                    String sessionKey, boolean renewable, String cv) {
        this.clientId = clientId;
        this.targetTgs = targetTgs;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.sessionKey = sessionKey;
        this.renewable = renewable;
        this.cv = cv;
    }

    public TgtInner(String clientId, String targetTgs, long issuedAt, long expiresAt,
                    String sessionKey, boolean renewable, String cv, String clientCert) {
        this.clientId = clientId;
        this.targetTgs = targetTgs;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.sessionKey = sessionKey;
        this.renewable = renewable;
        this.cv = cv;
        this.clientCert = clientCert;
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getTargetTgs() { return targetTgs; }
    public void setTargetTgs(String targetTgs) { this.targetTgs = targetTgs; }

    public long getIssuedAt() { return issuedAt; }
    public void setIssuedAt(long issuedAt) { this.issuedAt = issuedAt; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }

    public boolean isRenewable() { return renewable; }
    public void setRenewable(boolean renewable) { this.renewable = renewable; }

    public String getCv() { return cv; }
    public void setCv(String cv) { this.cv = cv; }

    public String getClientCert() { return clientCert; }
    public void setClientCert(String clientCert) { this.clientCert = clientCert; }
}
