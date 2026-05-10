package vn.edu.hcmus.securechat.common.exception;

public class ChainValidationException extends PkiException {
    public ChainValidationException(String message) {
        super(message);
    }
    public ChainValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
