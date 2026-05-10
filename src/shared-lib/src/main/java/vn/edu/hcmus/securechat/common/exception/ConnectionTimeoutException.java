package vn.edu.hcmus.securechat.common.exception;

public class ConnectionTimeoutException extends NetworkException {
    public ConnectionTimeoutException(String message) {
        super(message);
    }
    public ConnectionTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
