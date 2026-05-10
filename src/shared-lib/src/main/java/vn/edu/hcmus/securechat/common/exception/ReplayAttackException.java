package vn.edu.hcmus.securechat.common.exception;

public class ReplayAttackException extends ProtocolException {
    public ReplayAttackException(String message) {
        super(message);
    }
    public ReplayAttackException(String message, Throwable cause) {
        super(message, cause);
    }
}
