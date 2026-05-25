package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ST inner JSON (trước khi mã hóa bằng PU_ChatServer) — Contrains.md mục 2.3.
 *
 * {
 *   "clientId":     "string",
 *   "clientPubKey": "string — base64(X.509 SubjectPublicKeyInfo DER)",
 *   "targetServer": "string — FQDN của Chat Server",
 *   "issuedAt":     1715000000,
 *   "expiresAt":    1715028800,
 *   "sessionKey":   "string — base64(K_A_Chat, 32 bytes)",
 *   "cv":           "string — AUTH_ONLY|CHAT_SERVICE|8H_EXPIRY|NO_CONTENT_DECRYPT|DERIVATION_PROHIBITED"
 * }
 */
public class StInner {

    @JsonProperty("stId")
    private String stId;

    @JsonProperty("clientId")
    private String clientId;

    @JsonProperty("clientPubKey")
    private String clientPubKey;

    @JsonProperty("targetServer")
    private String targetServer;

    @JsonProperty("issuedAt")
    private long issuedAt;

    @JsonProperty("expiresAt")
    private long expiresAt;

    @JsonProperty("sessionKey")
    private String sessionKey;

    @JsonProperty("cv")
    private String cv;

    @JsonProperty("clientCertSerial")
    private String clientCertSerial;

    public StInner() {}

    public StInner(String clientId, String clientPubKey, String targetServer,
                   long issuedAt, long expiresAt, String sessionKey, String cv) {
        this.clientId = clientId;
        this.clientPubKey = clientPubKey;
        this.targetServer = targetServer;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.sessionKey = sessionKey;
        this.cv = cv;
    }

    public StInner(String stId, String clientId, String clientPubKey, String targetServer,
                   long issuedAt, long expiresAt, String sessionKey, String cv,
                   String clientCertSerial) {
        this.stId = stId;
        this.clientId = clientId;
        this.clientPubKey = clientPubKey;
        this.targetServer = targetServer;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.sessionKey = sessionKey;
        this.cv = cv;
        this.clientCertSerial = clientCertSerial;
    }

    public String getStId() { return stId; }
    public void setStId(String stId) { this.stId = stId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientPubKey() { return clientPubKey; }
    public void setClientPubKey(String clientPubKey) { this.clientPubKey = clientPubKey; }

    public String getTargetServer() { return targetServer; }
    public void setTargetServer(String targetServer) { this.targetServer = targetServer; }

    public long getIssuedAt() { return issuedAt; }
    public void setIssuedAt(long issuedAt) { this.issuedAt = issuedAt; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }

    public String getCv() { return cv; }
    public void setCv(String cv) { this.cv = cv; }

    public String getClientCertSerial() { return clientCertSerial; }
    public void setClientCertSerial(String clientCertSerial) { this.clientCertSerial = clientCertSerial; }
}
