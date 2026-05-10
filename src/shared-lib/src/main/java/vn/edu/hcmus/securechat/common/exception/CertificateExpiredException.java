package vn.edu.hcmus.securechat.common.exception;

public class CertificateExpiredException extends PkiException {
    private static final long serialVersionUID = 1L;

    public CertificateExpiredException(String message) {
        super(message);
    }
    public CertificateExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
