package vn.edu.hcmus.securechat.common.exception;

public class KeyDerivationException extends CryptoException {
    private static final long serialVersionUID = 1L;

    public KeyDerivationException(String message) {
        super(message);
    }
    public KeyDerivationException(String message, Throwable cause) {
        super(message, cause);
    }
}
