package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO bọc ngoài tín hiệu WebRTC Signaling (CALL_SDP_OFFER / CALL_SDP_ANSWER / CALL_ICE_CANDIDATE).
 *
 * <p>Chat Server nhận gói này và forward như một {@code CHAT_MESSAGE} E2EE thông thường tới
 * recipient. Bản thân {@code encryptedSignal} là JSON đã được mã hóa E2EE
 * (Double Ratchet) — Server mù hoàn toàn trước nội dung SDP/ICE.</p>
 *
 * <p>Loại tín hiệu được phân biệt bởi {@code signalType}: "SDP_OFFER", "SDP_ANSWER",
 * hoặc "ICE_CANDIDATE".</p>
 */
public class CallSignalDto {

    /** ID cuộc gọi duy nhất (UUID do caller sinh). */
    @JsonProperty("callId")
    private String callId;

    /** Loại tín hiệu: "SDP_OFFER" | "SDP_ANSWER" | "ICE_CANDIDATE". */
    @JsonProperty("signalType")
    private String signalType;

    /** Người khởi tạo cuộc gọi. */
    @JsonProperty("callerId")
    private String callerId;

    /** Người được gọi. */
    @JsonProperty("calleeId")
    private String calleeId;

    /**
     * Tín hiệu WebRTC đã E2EE-encrypt (Base64 — JSON đã mã hóa).
     * Bên trong (sau khi giải mã): SDP string hoặc ICE candidate JSON.
     */
    @JsonProperty("encryptedSignal")
    private String encryptedSignal;

    /** Loại phương tiện: "AUDIO" | "VIDEO". */
    @JsonProperty("mediaType")
    private String mediaType;

    /** Timestamp (epoch seconds) — dùng cho anti-replay. */
    @JsonProperty("timestamp")
    private long timestamp;

    /** Nonce chống phát lại (Base64). */
    @JsonProperty("nonce")
    private String nonce;

    public CallSignalDto() {}

    public CallSignalDto(String callId, String signalType, String callerId, String calleeId,
                         String encryptedSignal, String mediaType, long timestamp, String nonce) {
        this.callId = callId;
        this.signalType = signalType;
        this.callerId = callerId;
        this.calleeId = calleeId;
        this.encryptedSignal = encryptedSignal;
        this.mediaType = mediaType;
        this.timestamp = timestamp;
        this.nonce = nonce;
    }

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }

    public String getSignalType() { return signalType; }
    public void setSignalType(String signalType) { this.signalType = signalType; }

    public String getCallerId() { return callerId; }
    public void setCallerId(String callerId) { this.callerId = callerId; }

    public String getCalleeId() { return calleeId; }
    public void setCalleeId(String calleeId) { this.calleeId = calleeId; }

    public String getEncryptedSignal() { return encryptedSignal; }
    public void setEncryptedSignal(String encryptedSignal) { this.encryptedSignal = encryptedSignal; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }
}
