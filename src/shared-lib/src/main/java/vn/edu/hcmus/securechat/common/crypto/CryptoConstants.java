package vn.edu.hcmus.securechat.common.crypto;

public final class CryptoConstants {
    // AES-GCM
    public static final int AES_KEY_SIZE_BYTES  = 32;
    public static final int GCM_NONCE_SIZE      = 12;
    public static final int GCM_TAG_BITS        = 128;

    // PBKDF2
    public static final int PBKDF2_ITERATIONS   = 100_000;
    public static final int PBKDF2_SALT_SIZE    = 32;

    // HKDF
    public static final String HKDF_INFO        = "SecureChat-v1";

    // Kerberos
    public static final int MAX_TIME_SKEW_SECONDS   = 300;  // 5 phút
    public static final int NONCE_CACHE_TTL_SECONDS = 600;  // 10 phút
    public static final int NONCE_SIZE_BYTES        = 16;
    public static final int TGT_LIFETIME_SECONDS    = 28_800; // 8 giờ
    public static final int ST_LIFETIME_SECONDS     = 28_800; // 8 giờ

    // Kyber
    public static final String KYBER_PARAM = "ml_kem_768"; // NIST FIPS 203

    private CryptoConstants() {} // không khởi tạo
}
