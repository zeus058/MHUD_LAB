package vn.edu.hcmus.securechat.common.exception;

public class CryptoException extends SecureChatException {
    private static final long serialVersionUID = 1L;

    public CryptoException(String message) {
        super(message);
    }
    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
