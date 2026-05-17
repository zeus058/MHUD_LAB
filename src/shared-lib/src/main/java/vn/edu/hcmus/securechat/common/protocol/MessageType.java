package vn.edu.hcmus.securechat.common.protocol;

/**
 * Enum mapping tên gợi nhớ cho các TYPE constants trong PacketFrame.
 * Giúp team member dùng switch-case thay vì nhớ hex codes.
 *
 * Sử dụng:
 *   MessageType type = MessageType.fromByte(frame.getType());
 *   switch (type) {
 *       case CSR_REQUEST -> handleCsrRequest(frame);
 *       ...
 *   }
 */
public enum MessageType {
    CSR_REQUEST(PacketFrame.TYPE_CSR_REQUEST, "Client gửi CSR lên CA"),
    CERT_RESPONSE(PacketFrame.TYPE_CERT_RESPONSE, "CA trả Certificate"),
    TGT_REQUEST(PacketFrame.TYPE_TGT_REQUEST, "Client xin TGT từ AS"),
    TGT_RESPONSE(PacketFrame.TYPE_TGT_RESPONSE, "AS trả TGT + session key"),
    ST_REQUEST(PacketFrame.TYPE_ST_REQUEST, "Client xin ST từ TGS"),
    ST_RESPONSE(PacketFrame.TYPE_ST_RESPONSE, "TGS trả ST"),
    CHAT_HANDSHAKE(PacketFrame.TYPE_CHAT_HANDSHAKE, "Handshake ECDHE + Kyber"),
    CHAT_MESSAGE(PacketFrame.TYPE_CHAT_MESSAGE, "Tin nhắn đã mã hóa"),
    OCSP_REQUEST(PacketFrame.TYPE_OCSP_REQUEST, "Client xin OCSP status"),
    OCSP_RESPONSE(PacketFrame.TYPE_OCSP_RESPONSE, "Server trả OCSP response"),
    USER_LIST(PacketFrame.TYPE_USER_LIST, "Server push danh sách user online"),
    ERROR(PacketFrame.TYPE_ERROR, "Generic error response");

    private final byte code;
    private final String description;

    MessageType(byte code, String description) {
        this.code = code;
        this.description = description;
    }

    public byte getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Chuyển byte type từ PacketFrame header thành enum.
     *
     * @throws IllegalArgumentException nếu type không hợp lệ
     */
    public static MessageType fromByte(byte code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException(
                String.format("Unknown message type: 0x%02X", code));
    }
}
