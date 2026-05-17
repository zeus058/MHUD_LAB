package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Chat Handshake Response (Chat Server → Client) — theo Contrains.md §5.4.
 *
 * {
 *   "status":          "string — OK | ERROR",
 *   "clientId":        "string — confirmed client identity",
 *   "ecdhePubKey":     "string — base64(server ECDHE ephemeral public key)",
 *   "expiresAt":       long — Unix epoch seconds khi session hết hạn,
 *   "masterKeyDerived": boolean — true nếu HKDF master key đã được tạo
 * }
 */
public class ChatHandshakeResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("clientId")
    private String clientId;

    @JsonProperty("ecdhePubKey")
    private String ecdhePubKey;

    @JsonProperty("expiresAt")
    private long expiresAt;

    @JsonProperty("masterKeyDerived")
    private boolean masterKeyDerived;

    public ChatHandshakeResponse() {}

    public ChatHandshakeResponse(String status, String clientId, String ecdhePubKey,
                                  long expiresAt, boolean masterKeyDerived) {
        this.status = status;
        this.clientId = clientId;
        this.ecdhePubKey = ecdhePubKey;
        this.expiresAt = expiresAt;
        this.masterKeyDerived = masterKeyDerived;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getEcdhePubKey() { return ecdhePubKey; }
    public void setEcdhePubKey(String ecdhePubKey) { this.ecdhePubKey = ecdhePubKey; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

    public boolean isMasterKeyDerived() { return masterKeyDerived; }
    public void setMasterKeyDerived(boolean masterKeyDerived) { this.masterKeyDerived = masterKeyDerived; }
}
