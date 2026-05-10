package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Authenticator JSON (trước khi mã hóa bằng session key) — Contrains.md mục 2.3.
 *
 * {
 *   "clientId":  "string",
 *   "timestamp": 1715000000,
 *   "nonce":     "string — base64(16 random bytes)"
 * }
 */
public class AuthenticatorJson {

    @JsonProperty("clientId")
    private String clientId;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("nonce")
    private String nonce;

    public AuthenticatorJson() {}

    public AuthenticatorJson(String clientId, long timestamp, String nonce) {
        this.clientId = clientId;
        this.timestamp = timestamp;
        this.nonce = nonce;
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }
}
