package vn.edu.hcmus.securechat.common.exception;

public class CertificateRevokedException extends PkiException {
    public CertificateRevokedException(String message) {
        super(message);
    }
    public CertificateRevokedException(String message, Throwable cause) {
        super(message, cause);
    }
}
