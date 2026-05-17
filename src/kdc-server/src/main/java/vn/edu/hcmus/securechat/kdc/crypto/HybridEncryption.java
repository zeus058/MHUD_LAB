package vn.edu.hcmus.securechat.kdc.crypto;

/**
 * @deprecated Đã chuyển sang shared-lib: {@link vn.edu.hcmus.securechat.common.crypto.HybridEncryption}.
 * File này giữ lại để backward compatibility — delegate tất cả sang shared-lib.
 */
public final class HybridEncryption {

    private HybridEncryption() {}

    /**
     * @see vn.edu.hcmus.securechat.common.crypto.HybridEncryption#encrypt
     */
    public static byte[] encrypt(java.security.PublicKey recipientPublicKey, byte[] plaintext)
            throws vn.edu.hcmus.securechat.common.exception.CryptoException {
        return vn.edu.hcmus.securechat.common.crypto.HybridEncryption.encrypt(recipientPublicKey, plaintext);
    }

    /**
     * @see vn.edu.hcmus.securechat.common.crypto.HybridEncryption#decrypt
     */
    public static byte[] decrypt(java.security.PrivateKey recipientPrivateKey, byte[] cipherData)
            throws vn.edu.hcmus.securechat.common.exception.CryptoException {
        return vn.edu.hcmus.securechat.common.crypto.HybridEncryption.decrypt(recipientPrivateKey, cipherData);
    }
}
