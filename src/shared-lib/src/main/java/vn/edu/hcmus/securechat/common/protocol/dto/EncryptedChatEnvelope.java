package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Encrypted Chat Envelope — vỏ bọc tin nhắn mã hóa khi routing qua Chat Server.
 *
 * Chat Server KHÔNG giải mã content — chỉ xác minh sender rồi forward.
 *
 * {
 *   "recipientId": "string — clientId của người nhận",
 *   "payload":     "string — base64(AES-GCM encrypted ChatMessage JSON)"
 * }
 */
public class EncryptedChatEnvelope {

    @JsonProperty("recipientId")
    private String recipientId;

    @JsonProperty("payload")
    private String payload;

    public EncryptedChatEnvelope() {}

    public EncryptedChatEnvelope(String recipientId, String payload) {
        this.recipientId = recipientId;
        this.payload = payload;
    }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}
