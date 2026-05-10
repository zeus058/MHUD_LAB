package vn.edu.hcmus.securechat.common.exception;

public class ProtocolException extends SecureChatException {
    public ProtocolException(String message) {
        super(message);
    }
    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
