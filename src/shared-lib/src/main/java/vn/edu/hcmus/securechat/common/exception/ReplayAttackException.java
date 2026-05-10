package vn.edu.hcmus.securechat.common.exception;

public class ReplayAttackException extends ProtocolException {
    private static final long serialVersionUID = 1L;

    public ReplayAttackException(String message) {
        super(message);
    }
    public ReplayAttackException(String message, Throwable cause) {
        super(message, cause);
    }
}
