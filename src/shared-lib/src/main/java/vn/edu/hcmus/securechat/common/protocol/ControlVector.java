package vn.edu.hcmus.securechat.common.protocol;

import vn.edu.hcmus.securechat.common.exception.ControlVectorException;

public final class ControlVector {
    public static final String AUTH_ONLY = "AUTH_ONLY";
    public static final String ENCRYPT_ONLY = "ENCRYPT_ONLY";
    public static final String CHAT_SERVICE = "CHAT_SERVICE";
    public static final String TGS_SERVICE  = "TGS_SERVICE";
    public static final String EXPIRY_8H    = "8H_EXPIRY";
    public static final String EXPIRY_24H   = "24H_EXPIRY";
    public static final String NO_CONTENT_DECRYPT = "NO_CONTENT_DECRYPT";
    public static final String DERIVATION_PROHIBITED = "DERIVATION_PROHIBITED";
    public static final String RENEWABLE = "RENEWABLE";
    public static final String PROXIABLE = "PROXIABLE";

    public static final String TGT_CV = AUTH_ONLY + "|" + TGS_SERVICE + "|" + EXPIRY_8H
            + "|" + RENEWABLE + "|" + PROXIABLE;

    // CV chuẩn cho ST theo BaoCao_NangCap.md: khóa chỉ xác thực truy cập, không giải mã nội dung.
    public static final String ST_CV = AUTH_ONLY + "|" + CHAT_SERVICE + "|" + EXPIRY_8H
            + "|" + NO_CONTENT_DECRYPT + "|" + DERIVATION_PROHIBITED;

    // Chat Server PHẢI gọi hàm này trước khi dùng key từ ST
    public static void validateForChatService(String cv) throws ControlVectorException {
        if (cv == null || !cv.contains(CHAT_SERVICE)) {
            throw new ControlVectorException(
                "CV does not contain CHAT_SERVICE flag: " + cv);
        }
        if (!cv.contains(AUTH_ONLY)) {
            throw new ControlVectorException(
                "CV does not contain AUTH_ONLY flag: " + cv);
        }
        if (!cv.contains(NO_CONTENT_DECRYPT)) {
            throw new ControlVectorException(
                "CV does not contain NO_CONTENT_DECRYPT flag: " + cv);
        }
        if (!cv.contains(DERIVATION_PROHIBITED)) {
            throw new ControlVectorException(
                "CV does not contain DERIVATION_PROHIBITED flag: " + cv);
        }
    }
    
    private ControlVector() {}
}
