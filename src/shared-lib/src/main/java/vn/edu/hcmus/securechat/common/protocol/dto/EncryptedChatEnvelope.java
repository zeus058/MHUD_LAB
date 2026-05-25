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

    @JsonProperty("conversationId")
    private String conversationId;

    @JsonProperty("msgId")
    private long msgId;

    @JsonProperty("senderId")
    private String senderId;

    @JsonProperty("recipientId")
    private String recipientId;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("aadHash")
    private String aadHash;

    @JsonProperty("payload")
    private String payload;

    public EncryptedChatEnvelope() {}

    public EncryptedChatEnvelope(String recipientId, String payload) {
        this.recipientId = recipientId;
        this.payload = payload;
    }

    public EncryptedChatEnvelope(String conversationId, long msgId, String senderId,
                                 String recipientId, long timestamp, String aadHash,
                                 String payload) {
        this.conversationId = conversationId;
        this.msgId = msgId;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.timestamp = timestamp;
        this.aadHash = aadHash;
        this.payload = payload;
    }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public long getMsgId() { return msgId; }
    public void setMsgId(long msgId) { this.msgId = msgId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getAadHash() { return aadHash; }
    public void setAadHash(String aadHash) { this.aadHash = aadHash; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}
