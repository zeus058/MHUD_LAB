package vn.edu.hcmus.securechat.common.crypto;

public final class CryptoConstants {
    // AES-GCM
    public static final int AES_KEY_SIZE_BYTES  = 32;
    public static final int GCM_NONCE_SIZE      = 12;
    public static final int GCM_TAG_BITS        = 128;

    // PBKDF2
    public static final int PBKDF2_ITERATIONS   = 100_000;
    public static final int PBKDF2_SALT_SIZE    = 32;

    // Argon2id password-to-key derivation (OWASP 2024 baseline)
    public static final int ARGON2ID_SALT_SIZE       = 32;
    public static final int ARGON2ID_MEMORY_KIB      = 65_536; // 64 MiB
    public static final int ARGON2ID_ITERATIONS      = 3;
    public static final int ARGON2ID_PARALLELISM     = 4;

    // HKDF
    public static final String HKDF_INFO        = "SecureChat-E2EE-v2";
    public static final String HKDF_X3DH_SALT_INFO = "X3DH-SecureChat-v2";
    public static final int E2EE_CONVERSATION_KEY_SIZE_BYTES = 64;
    public static final int E2EE_SKIPPED_KEY_MAX_COUNT = 100;
    public static final long E2EE_SKIPPED_KEY_TTL_MILLIS = 24L * 60L * 60L * 1000L;

    // Kerberos
    public static final int MAX_TIME_SKEW_SECONDS   = 300;  // 5 phút
    public static final int NONCE_CACHE_TTL_SECONDS = 600;  // 10 phút
    public static final int NONCE_SIZE_BYTES        = 16;
    public static final int TGT_LIFETIME_SECONDS    = 28_800; // 8 giờ
    public static final int ST_LIFETIME_SECONDS     = 28_800; // 8 giờ
    public static final int TICKET_RENEW_TILL_SECONDS = 604_800; // 7 ngày

    // Kyber
    public static final String KYBER_PARAM = "ML-KEM-768"; // NIST FIPS 203

    private CryptoConstants() {} // không khởi tạo
}
