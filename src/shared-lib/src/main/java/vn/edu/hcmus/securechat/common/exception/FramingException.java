package vn.edu.hcmus.securechat.common.exception;

public class FramingException extends ProtocolException {
    public FramingException(String message) {
        super(message);
    }
    public FramingException(String message, Throwable cause) {
        super(message, cause);
    }
}
