package vn.edu.hcmus.securechat.common.exception;

public class ConnectionTimeoutException extends NetworkException {
    private static final long serialVersionUID = 1L;

    public ConnectionTimeoutException(String message) {
        super(message);
    }
    public ConnectionTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
