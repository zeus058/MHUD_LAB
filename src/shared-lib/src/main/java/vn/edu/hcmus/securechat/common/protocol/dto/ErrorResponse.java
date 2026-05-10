package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Error Response JSON — gửi khi server trả lỗi (TYPE_ERROR = 0xFF).
 * KHÔNG bao giờ leak chi tiết exception nội bộ ra client.
 *
 * {
 *   "errorCode":    "string — mã lỗi (ví dụ: AUTH_FAILED, TICKET_EXPIRED)",
 *   "message":      "string — thông báo generic cho client",
 *   "timestamp":    1715000000
 * }
 */
public class ErrorResponse {

    // Các error code chuẩn
    public static final String ERR_AUTH_FAILED        = "AUTH_FAILED";
    public static final String ERR_TICKET_EXPIRED     = "TICKET_EXPIRED";
    public static final String ERR_CERT_REVOKED       = "CERT_REVOKED";
    public static final String ERR_CERT_INVALID       = "CERT_INVALID";
    public static final String ERR_REPLAY_DETECTED    = "REPLAY_DETECTED";
    public static final String ERR_MAC_FAILED         = "MAC_FAILED";
    public static final String ERR_INTERNAL           = "INTERNAL_ERROR";
    public static final String ERR_BAD_REQUEST        = "BAD_REQUEST";

    @JsonProperty("errorCode")
    private String errorCode;

    @JsonProperty("message")
    private String message;

    @JsonProperty("timestamp")
    private long timestamp;

    public ErrorResponse() {}

    public ErrorResponse(String errorCode, String message, long timestamp) {
        this.errorCode = errorCode;
        this.message = message;
        this.timestamp = timestamp;
    }

    /**
     * Factory method — tạo ErrorResponse với timestamp hiện tại.
     */
    public static ErrorResponse of(String errorCode, String message) {
        return new ErrorResponse(errorCode, message, System.currentTimeMillis() / 1000);
    }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
