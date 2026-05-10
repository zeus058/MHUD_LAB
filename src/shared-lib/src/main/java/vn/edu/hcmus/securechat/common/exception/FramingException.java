package vn.edu.hcmus.securechat.common.exception;

public class FramingException extends ProtocolException {
    private static final long serialVersionUID = 1L;

    public FramingException(String message) {
        super(message);
    }
    public FramingException(String message, Throwable cause) {
        super(message, cause);
    }
}
