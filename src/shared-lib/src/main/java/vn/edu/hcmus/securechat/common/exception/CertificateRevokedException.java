package vn.edu.hcmus.securechat.common.exception;

public class CertificateRevokedException extends PkiException {
    private static final long serialVersionUID = 1L;

    public CertificateRevokedException(String message) {
        super(message);
    }
    public CertificateRevokedException(String message, Throwable cause) {
        super(message, cause);
    }
}
