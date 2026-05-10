package vn.edu.hcmus.securechat.common.exception;

public class ChainValidationException extends PkiException {
    private static final long serialVersionUID = 1L;

    public ChainValidationException(String message) {
        super(message);
    }
    public ChainValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
