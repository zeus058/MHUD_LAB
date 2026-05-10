package vn.edu.hcmus.securechat.common.exception;

public class MacVerificationException extends CryptoException {
    private static final long serialVersionUID = 1L;

    public MacVerificationException(String message) {
        super(message);
    }
    public MacVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
