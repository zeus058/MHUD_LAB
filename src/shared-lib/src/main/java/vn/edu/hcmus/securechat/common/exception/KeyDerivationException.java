package vn.edu.hcmus.securechat.common.exception;

public class KeyDerivationException extends CryptoException {
    public KeyDerivationException(String message) {
        super(message);
    }
    public KeyDerivationException(String message, Throwable cause) {
        super(message, cause);
    }
}
