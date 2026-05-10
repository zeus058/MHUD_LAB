package vn.edu.hcmus.securechat.common.exception;

public class NetworkException extends SecureChatException {
    private static final long serialVersionUID = 1L;

    public NetworkException(String message) {
        super(message);
    }
    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
