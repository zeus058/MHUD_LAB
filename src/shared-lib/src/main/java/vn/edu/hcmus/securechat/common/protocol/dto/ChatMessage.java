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

    @JsonProperty("senderId")
    private String senderId;

    @JsonProperty("content")
    private String content;

    @JsonProperty("sentAt")
    private long sentAt;

    public ChatMessage() {}

    public ChatMessage(String senderId, String content, long sentAt) {
        this.senderId = senderId;
        this.content = content;
        this.sentAt = sentAt;
    }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getSentAt() { return sentAt; }
    public void setSentAt(long sentAt) { this.sentAt = sentAt; }
}
