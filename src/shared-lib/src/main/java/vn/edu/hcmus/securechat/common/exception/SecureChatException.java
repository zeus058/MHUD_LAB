package vn.edu.hcmus.securechat.common.exception;

public class SecureChatException extends Exception {
    public SecureChatException(String message) {
        super(message);
    }
    public SecureChatException(String message, Throwable cause) {
        super(message, cause);
    }
}
