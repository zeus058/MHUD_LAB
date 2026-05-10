package vn.edu.hcmus.securechat.common.exception;

public class ControlVectorException extends ProtocolException {
    private static final long serialVersionUID = 1L;

    public ControlVectorException(String message) {
        super(message);
    }
    public ControlVectorException(String message, Throwable cause) {
        super(message, cause);
    }
}
