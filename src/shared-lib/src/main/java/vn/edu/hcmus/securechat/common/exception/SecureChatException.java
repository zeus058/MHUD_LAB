package vn.edu.hcmus.securechat.common.exception;

public class SecureChatException extends Exception {
    private static final long serialVersionUID = 1L;

    public SecureChatException(String message) {
        super(message);
    }
    public SecureChatException(String message, Throwable cause) {
        super(message, cause);
    }
}
