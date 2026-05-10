package vn.edu.hcmus.securechat.common.exception;

public class DecryptionException extends CryptoException {
    private static final long serialVersionUID = 1L;

    public DecryptionException(String message) {
        super(message);
    }
    public DecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
