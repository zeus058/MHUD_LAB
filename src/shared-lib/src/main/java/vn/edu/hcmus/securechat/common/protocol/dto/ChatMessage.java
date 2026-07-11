package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AES-GCM Message JSON (chat payload trước khi mã hóa) — Contrains.md mục 2.3.
 *
 * {
 *   "senderId":  "string",
 *   "content":   "string — nội dung tin nhắn plaintext",
 *   "sentAt":    1715000000
 * }
 */
public class ChatMessage {

    @JsonProperty("conversationId")
    private String conversationId;

    @JsonProperty("msgId")
    private long msgId;

    @JsonProperty("senderId")
    private String senderId;

    @JsonProperty("recipientId")
    private String recipientId;

    @JsonProperty("content")
    private String content;

    @JsonProperty("sentAt")
    private long sentAt;

    @JsonProperty("controlType")
    private String controlType;

    @JsonProperty("targetTimestamp")
    private long targetTimestamp;

    public ChatMessage() {}

    public ChatMessage(String senderId, String content, long sentAt) {
        this.senderId = senderId;
        this.content = content;
        this.sentAt = sentAt;
    }

    public ChatMessage(String conversationId, long msgId, String senderId,
                       String recipientId, String content, long sentAt) {
        this.conversationId = conversationId;
        this.msgId = msgId;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.content = content;
        this.sentAt = sentAt;
    }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public long getMsgId() { return msgId; }
    public void setMsgId(long msgId) { this.msgId = msgId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getSentAt() { return sentAt; }
    public void setSentAt(long sentAt) { this.sentAt = sentAt; }

    public String getControlType() { return controlType; }
    public void setControlType(String controlType) { this.controlType = controlType; }

    public long getTargetTimestamp() { return targetTimestamp; }
    public void setTargetTimestamp(long targetTimestamp) { this.targetTimestamp = targetTimestamp; }
}
