package vn.edu.hcmus.securechat.common.crypto;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.exception.CryptoException;

/**
 * Mã hóa lai (Hybrid Encryption) — RSA bọc khóa AES, AES-GCM mã hóa dữ liệu.
 *
 * Dùng chung cho cả KDC, Chat Server và Client.
 *
 * Cấu trúc output (byte[]):
 *   [2 bytes: length of RSA-encrypted DEK (big-endian)]
 *   [N bytes: RSA-encrypted DEK]
 *   [M bytes: AES-GCM encrypted data (nonce + ciphertext + tag)]
 */
public final class HybridEncryption {

    private static final String RSA_ALGORITHM = "RSA/ECB/PKCS1Padding";

    private HybridEncryption() {}

    /**
     * Hybrid Encrypt: RSA bọc DEK + AES-GCM mã hóa data.
     *
     * @param recipientPublicKey RSA Public Key của người nhận
     * @param plaintext          Dữ liệu cần mã hóa
     * @return byte[] chứa [len(encDek) | encDek | aesGcmCiphertext]
     */
    public static byte[] encrypt(PublicKey recipientPublicKey, byte[] plaintext)
            throws CryptoException {
        byte[] dek = null;
        try {
            // 1. Sinh DEK (AES-256 key ngẫu nhiên)
            dek = new byte[CryptoConstants.AES_KEY_SIZE_BYTES];
            new SecureRandom().nextBytes(dek);

            // 2. Mã hóa DEK bằng RSA-OAEP
            Cipher rsaCipher = Cipher.getInstance(RSA_ALGORITHM);
            rsaCipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey);
            byte[] encryptedDek = rsaCipher.doFinal(dek);

            // 3. Mã hóa plaintext bằng AES-GCM với DEK
            byte[] aesGcmCiphertext = AesGcmCipher.encrypt(dek, plaintext);

            // 4. Ghép: [2 bytes len] + [encryptedDek] + [aesGcmCiphertext]
            int dekLen = encryptedDek.length;
            byte[] result = new byte[2 + dekLen + aesGcmCiphertext.length];
            result[0] = (byte) ((dekLen >> 8) & 0xFF);
            result[1] = (byte) (dekLen & 0xFF);
            System.arraycopy(encryptedDek, 0, result, 2, dekLen);
            System.arraycopy(aesGcmCiphertext, 0, result, 2 + dekLen, aesGcmCiphertext.length);

            return result;

        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Hybrid encryption failed", e);
        } finally {
            if (dek != null) Arrays.fill(dek, (byte) 0);
        }
    }

    /**
     * Hybrid Decrypt: RSA mở DEK + AES-GCM giải mã data.
     *
     * @param recipientPrivateKey RSA Private Key của người nhận
     * @param cipherData          Dữ liệu đã mã hóa
     * @return byte[] plaintext gốc
     */
    public static byte[] decrypt(PrivateKey recipientPrivateKey, byte[] cipherData)
            throws CryptoException {
        byte[] dek = null;
        try {
            if (cipherData.length < 4) {
                throw new CryptoException("Hybrid ciphertext too short");
            }

            // 1. Đọc length của encrypted DEK
            int dekLen = ((cipherData[0] & 0xFF) << 8) | (cipherData[1] & 0xFF);
            if (dekLen <= 0 || dekLen > cipherData.length - 2) {
                throw new CryptoException("Invalid DEK length: " + dekLen);
            }

            // 2. Tách encrypted DEK và AES-GCM ciphertext
            byte[] encryptedDek = new byte[dekLen];
            System.arraycopy(cipherData, 2, encryptedDek, 0, dekLen);

            byte[] aesGcmCiphertext = new byte[cipherData.length - 2 - dekLen];
            System.arraycopy(cipherData, 2 + dekLen, aesGcmCiphertext, 0, aesGcmCiphertext.length);

            // 3. Giải mã DEK bằng RSA-OAEP
            Cipher rsaCipher = Cipher.getInstance(RSA_ALGORITHM);
            rsaCipher.init(Cipher.DECRYPT_MODE, recipientPrivateKey);
            dek = rsaCipher.doFinal(encryptedDek);

            // 4. Giải mã plaintext bằng AES-GCM
            return AesGcmCipher.decrypt(dek, aesGcmCiphertext);

        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Hybrid decryption failed", e);
        } finally {
            if (dek != null) Arrays.fill(dek, (byte) 0);
        }
    }
}
