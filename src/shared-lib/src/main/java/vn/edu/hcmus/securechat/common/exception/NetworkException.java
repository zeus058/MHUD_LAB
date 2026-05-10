package vn.edu.hcmus.securechat.common.exception;

public class NetworkException extends SecureChatException {
    public NetworkException(String message) {
        super(message);
    }
    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
