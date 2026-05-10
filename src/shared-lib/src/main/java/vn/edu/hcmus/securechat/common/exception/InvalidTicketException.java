package vn.edu.hcmus.securechat.common.exception;

public class InvalidTicketException extends ProtocolException {
    public InvalidTicketException(String message) {
        super(message);
    }
    public InvalidTicketException(String message, Throwable cause) {
        super(message, cause);
    }
}
