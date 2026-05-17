package vn.edu.hcmus.securechat.common.crypto;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;

import vn.edu.hcmus.securechat.common.exception.CryptoException;

/**
 * HybridCrypto — Thực hiện mã hoá Hybrid (RSA + AES-GCM).
 * Dùng để mã hoá TGT và Kerberos AS-REP theo yêu cầu dự án.
 */
public class HybridCrypto {

    // 2048-bit RSA produces 256 byte cipher text
    private static final int RSA_KEY_SIZE_BYTES = 256;
    private static final int AES_KEY_SIZE = 32; // AES-256

    /**
     * Mã hoá payload bằng Hybrid Encryption.
     * 1. Sinh ngẫu nhiên AES-256 session key (K).
     * 2. Mã hoá K bằng RSA public key -> E(K).
     * 3. Mã hoá payload bằng K (AES-GCM) -> E(payload).
     * 4. Gộp E(K) + E(payload) trả về.
     */
    public static byte[] encrypt(PublicKey rsaPublicKey, byte[] payload) throws CryptoException {
        try {
            // 1. Sinh session key K (AES-256)
            byte[] aesKey = new byte[AES_KEY_SIZE];
            new SecureRandom().nextBytes(aesKey);

            // 2. Mã hoá K bằng RSA
            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            rsaCipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
            byte[] encryptedKey = rsaCipher.doFinal(aesKey);

            // Đảm bảo RSA output size = 256 bytes (RSA-2048)
            if (encryptedKey.length != RSA_KEY_SIZE_BYTES) {
                throw new CryptoException("RSA key size is not 2048 bit. Encrypted key size: " + encryptedKey.length);
            }

            // 3. Mã hoá payload bằng AES-GCM với K
            byte[] encryptedPayload = AesGcmCipher.encrypt(aesKey, payload);

            // 4. Concat E(K) và E(payload)
            byte[] result = new byte[RSA_KEY_SIZE_BYTES + encryptedPayload.length];
            System.arraycopy(encryptedKey, 0, result, 0, RSA_KEY_SIZE_BYTES);
            System.arraycopy(encryptedPayload, 0, result, RSA_KEY_SIZE_BYTES, encryptedPayload.length);

            // Xoá K khỏi bộ nhớ
            Arrays.fill(aesKey, (byte) 0);

            return result;
        } catch (Exception e) {
            throw new CryptoException("Hybrid encryption failed", e);
        }
    }

    /**
     * Giải mã payload bằng Hybrid Decryption.
     * 1. Tách E(K) (256 bytes đầu) và E(payload) (phần còn lại).
     * 2. Giải mã E(K) bằng RSA private key -> K.
     * 3. Giải mã E(payload) bằng K (AES-GCM) -> payload.
     */
    public static byte[] decrypt(PrivateKey rsaPrivateKey, byte[] cipherData) throws CryptoException {
        if (cipherData == null || cipherData.length < RSA_KEY_SIZE_BYTES) {
            throw new CryptoException("Cipher data is too short for Hybrid decryption");
        }
        
        try {
            // 1. Tách phần RSA và phần AES
            byte[] encryptedKey = Arrays.copyOfRange(cipherData, 0, RSA_KEY_SIZE_BYTES);
            byte[] encryptedPayload = Arrays.copyOfRange(cipherData, RSA_KEY_SIZE_BYTES, cipherData.length);

            // 2. Giải mã K bằng RSA
            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            rsaCipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
            byte[] aesKey = rsaCipher.doFinal(encryptedKey);

            // 3. Giải mã payload bằng AES-GCM với K
            byte[] payload = AesGcmCipher.decrypt(aesKey, encryptedPayload);

            // Xoá K khỏi bộ nhớ
            Arrays.fill(aesKey, (byte) 0);

            return payload;
        } catch (Exception e) {
            throw new CryptoException("Hybrid decryption failed", e);
        }
    }
}
