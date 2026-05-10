package vn.edu.hcmus.securechat.common.protocol;

import vn.edu.hcmus.securechat.common.exception.ControlVectorException;

public final class ControlVector {
    public static final String ENCRYPT_ONLY = "ENCRYPT_ONLY";
    public static final String CHAT_SERVICE = "CHAT_SERVICE";
    public static final String TGS_SERVICE  = "TGS_SERVICE";
    public static final String EXPIRY_8H    = "8H_EXPIRY";
    public static final String EXPIRY_24H   = "24H_EXPIRY";

    // CV chuẩn cho ST
    public static final String ST_CV = ENCRYPT_ONLY + "|" + CHAT_SERVICE + "|" + EXPIRY_8H;

    // Chat Server PHẢI gọi hàm này trước khi dùng key từ ST
    public static void validateForChatService(String cv) throws ControlVectorException {
        if (cv == null || !cv.contains(CHAT_SERVICE)) {
            throw new ControlVectorException(
                "CV does not contain CHAT_SERVICE flag: " + cv);
        }
        if (!cv.contains(ENCRYPT_ONLY)) {
            throw new ControlVectorException(
                "CV does not contain ENCRYPT_ONLY flag: " + cv);
        }
    }
    
    private ControlVector() {}
}
