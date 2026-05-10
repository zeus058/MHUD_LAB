package vn.edu.hcmus.securechat.common.exception;

public class CertificateExpiredException extends PkiException {
    public CertificateExpiredException(String message) {
        super(message);
    }
    public CertificateExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
