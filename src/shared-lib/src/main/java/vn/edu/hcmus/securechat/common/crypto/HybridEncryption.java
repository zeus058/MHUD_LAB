package vn.edu.hcmus.securechat.common.crypto;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

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

    private static final String RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String RSA_PKCS1_ALGORITHM = "RSA/ECB/PKCS1Padding";
    private static final byte[] MAGIC = new byte[] {'S', 'C', 'H', '2'};
    private static final byte MODE_RSA_OAEP_SHA256 = 0x01;
    private static final byte MODE_RSA_PKCS1_COMPAT = 0x02;
    private static final byte[] DEFAULT_AAD =
            "SecureChat-HybridEncryption-v2".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final OAEPParameterSpec OAEP_SHA256 = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT);

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
        return encrypt(recipientPublicKey, plaintext, DEFAULT_AAD);
    }

    public static byte[] encrypt(PublicKey recipientPublicKey, byte[] plaintext, byte[] aad)
            throws CryptoException {
        byte[] dek = null;
        try {
            // 1. Sinh DEK (AES-256 key ngẫu nhiên)
            dek = new byte[CryptoConstants.AES_KEY_SIZE_BYTES];
            new SecureRandom().nextBytes(dek);

            // 2. Mã hóa DEK bằng RSA-OAEP
            Cipher rsaCipher = initRsaCipher(Cipher.ENCRYPT_MODE, recipientPublicKey, MODE_RSA_OAEP_SHA256);
            byte[] encryptedDek = rsaCipher.doFinal(dek);

            // 3. Mã hóa plaintext bằng AES-GCM với DEK
            byte[] aesGcmCiphertext = AesGcmCipher.encrypt(dek, plaintext, aad);

            return pack(MODE_RSA_OAEP_SHA256, encryptedDek, aesGcmCiphertext);

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
        return decrypt(recipientPrivateKey, cipherData, DEFAULT_AAD);
    }

    /**
     * Hybrid encryption variant for recipients whose private key lives in
     * Windows-MY/SunMSCAPI. Current SunMSCAPI exposes RSA/PKCS#1 decrypt only,
     * so this wraps only the random CEK with PKCS#1 while keeping payload
     * confidentiality and integrity in AES-256-GCM.
     */
    public static byte[] encryptForWindowsKeyStoreRecipient(PublicKey recipientPublicKey, byte[] plaintext)
            throws CryptoException {
        return encryptForWindowsKeyStoreRecipient(recipientPublicKey, plaintext, DEFAULT_AAD);
    }

    public static byte[] encryptForWindowsKeyStoreRecipient(PublicKey recipientPublicKey, byte[] plaintext, byte[] aad)
            throws CryptoException {
        byte[] dek = null;
        try {
            dek = new byte[CryptoConstants.AES_KEY_SIZE_BYTES];
            new SecureRandom().nextBytes(dek);

            Cipher rsaCipher = initRsaCipher(Cipher.ENCRYPT_MODE, recipientPublicKey, MODE_RSA_PKCS1_COMPAT);
            byte[] encryptedDek = rsaCipher.doFinal(dek);
            byte[] aesGcmCiphertext = AesGcmCipher.encrypt(dek, plaintext, aad);
            return pack(MODE_RSA_PKCS1_COMPAT, encryptedDek, aesGcmCiphertext);
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Hybrid encryption failed", e);
        } finally {
            if (dek != null) Arrays.fill(dek, (byte) 0);
        }
    }

    public static byte[] decrypt(PrivateKey recipientPrivateKey, byte[] cipherData, byte[] aad)
            throws CryptoException {
        byte[] dek = null;
        try {
            if (cipherData.length < 4) {
                throw new CryptoException("Hybrid ciphertext too short");
            }

            ParsedCiphertext parsed = parse(cipherData);

            // 1. Đọc length của encrypted DEK
            int dekLen = parsed.encryptedDek().length;
            if (dekLen <= 0 || dekLen > cipherData.length - 2) {
                throw new CryptoException("Invalid DEK length: " + dekLen);
            }

            // 3. Giải mã DEK bằng RSA-OAEP
            Cipher rsaCipher = initRsaCipher(Cipher.DECRYPT_MODE, recipientPrivateKey, parsed.mode());
            dek = rsaCipher.doFinal(parsed.encryptedDek());

            // 4. Giải mã plaintext bằng AES-GCM
            return AesGcmCipher.decrypt(dek, parsed.aesGcmCiphertext(), aad);

        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Hybrid decryption failed", e);
        } finally {
            if (dek != null) Arrays.fill(dek, (byte) 0);
        }
    }

    private static byte[] pack(byte mode, byte[] encryptedDek, byte[] aesGcmCiphertext) {
        int headerSize = MAGIC.length + 1 + 2;
        int dekLen = encryptedDek.length;
        byte[] result = new byte[headerSize + dekLen + aesGcmCiphertext.length];
        System.arraycopy(MAGIC, 0, result, 0, MAGIC.length);
        result[MAGIC.length] = mode;
        result[MAGIC.length + 1] = (byte) ((dekLen >> 8) & 0xFF);
        result[MAGIC.length + 2] = (byte) (dekLen & 0xFF);
        System.arraycopy(encryptedDek, 0, result, headerSize, dekLen);
        System.arraycopy(aesGcmCiphertext, 0, result, headerSize + dekLen, aesGcmCiphertext.length);
        return result;
    }

    private static ParsedCiphertext parse(byte[] cipherData) throws CryptoException {
        int offset = 0;
        byte mode = MODE_RSA_OAEP_SHA256;
        if (hasMagic(cipherData)) {
            if (cipherData.length < MAGIC.length + 3) {
                throw new CryptoException("Hybrid ciphertext header too short");
            }
            mode = cipherData[MAGIC.length];
            offset = MAGIC.length + 1;
        }

        int dekLen = ((cipherData[offset] & 0xFF) << 8) | (cipherData[offset + 1] & 0xFF);
        int payloadOffset = offset + 2;
        if (dekLen <= 0 || dekLen > cipherData.length - payloadOffset) {
            throw new CryptoException("Invalid DEK length: " + dekLen);
        }

        byte[] encryptedDek = Arrays.copyOfRange(cipherData, payloadOffset, payloadOffset + dekLen);
        byte[] aesGcmCiphertext = Arrays.copyOfRange(cipherData, payloadOffset + dekLen, cipherData.length);
        return new ParsedCiphertext(mode, encryptedDek, aesGcmCiphertext);
    }

    private static boolean hasMagic(byte[] cipherData) {
        if (cipherData.length < MAGIC.length + 3) {
            return false;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (cipherData[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static Cipher initRsaCipher(int mode, Key key, byte wrappingMode) throws GeneralSecurityException {
        List<String> providers = providerPreference(key);
        List<String> transformations = wrappingMode == MODE_RSA_PKCS1_COMPAT
                ? List.of(RSA_PKCS1_ALGORITHM, "RSA")
                : List.of(RSA_ALGORITHM, "RSA/ECB/OAEPPadding");
        GeneralSecurityException last = null;

        for (String provider : providers) {
            for (String transformation : transformations) {
                try {
                    Cipher cipher = provider == null
                            ? Cipher.getInstance(transformation)
                            : Cipher.getInstance(transformation, provider);
                    if (wrappingMode == MODE_RSA_PKCS1_COMPAT) {
                        cipher.init(mode, key);
                    } else {
                        cipher.init(mode, key, OAEP_SHA256);
                    }
                    return cipher;
                } catch (GeneralSecurityException e) {
                    last = e;
                }
            }
        }

        throw last != null ? last : new GeneralSecurityException("No RSA-OAEP provider available");
    }

    private static List<String> providerPreference(Key key) {
        List<String> providers = new ArrayList<>();
        if (key != null && key.getClass().getName().startsWith("sun.security.mscapi.")) {
            providers.add("SunMSCAPI");
        }
        providers.add(null);
        return providers;
    }

    private record ParsedCiphertext(byte mode, byte[] encryptedDek, byte[] aesGcmCiphertext) {}
}
