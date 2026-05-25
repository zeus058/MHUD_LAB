package vn.edu.hcmus.securechat.common.crypto;

import java.security.SecureRandom;
import java.util.Arrays;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.GCMModeCipher;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.InvalidCipherTextException;

import vn.edu.hcmus.securechat.common.exception.CryptoException;
import vn.edu.hcmus.securechat.common.exception.DecryptionException;
import vn.edu.hcmus.securechat.common.exception.MacVerificationException;

public class AesGcmCipher {

    private static final int NONCE_SIZE = CryptoConstants.GCM_NONCE_SIZE;
    private static final int TAG_BITS   = CryptoConstants.GCM_TAG_BITS;
    private static final SecureRandom RNG = new SecureRandom();

    public static byte[] encrypt(byte[] key, byte[] plaintext) throws CryptoException {
        return encrypt(key, plaintext, null);
    }

    public static byte[] encrypt(byte[] key, byte[] plaintext, byte[] aad) throws CryptoException {
        try {
            byte[] nonce = new byte[NONCE_SIZE];
            RNG.nextBytes(nonce);
            return encryptWithNonce(key, plaintext, aad, nonce);
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("AES-GCM encrypt failed", e);
        }
    }

    public static byte[] encryptWithNonce(byte[] key, byte[] plaintext, byte[] aad, byte[] nonce)
            throws CryptoException {
        try {
            validateNonce(nonce);
            GCMModeCipher cipher = GCMBlockCipher.newInstance(AESEngine.newInstance());
            cipher.init(true, new AEADParameters(new KeyParameter(key), TAG_BITS, nonce, aad));
            byte[] cipherAndTag = new byte[cipher.getOutputSize(plaintext.length)];
            int len = cipher.processBytes(plaintext, 0, plaintext.length, cipherAndTag, 0);
            cipher.doFinal(cipherAndTag, len);

            // [ nonce(12) | ciphertext+tag ]
            byte[] result = new byte[NONCE_SIZE + cipherAndTag.length];
            System.arraycopy(nonce, 0, result, 0, NONCE_SIZE);
            System.arraycopy(cipherAndTag, 0, result, NONCE_SIZE, cipherAndTag.length);
            return result;

        } catch (Exception e) {
            throw new CryptoException("AES-GCM encrypt failed", e);
        }
    }

    public static byte[] decrypt(byte[] key, byte[] cipherData) throws CryptoException {
        return decrypt(key, cipherData, null);
    }

    public static byte[] decrypt(byte[] key, byte[] cipherData, byte[] aad) throws CryptoException {
        try {
            if (cipherData.length < NONCE_SIZE + TAG_BITS / 8) {
                throw new DecryptionException("Cipher text is too short");
            }
            byte[] nonce      = Arrays.copyOfRange(cipherData, 0, NONCE_SIZE);
            byte[] cipherText = Arrays.copyOfRange(cipherData, NONCE_SIZE, cipherData.length);

            GCMModeCipher cipher = GCMBlockCipher.newInstance(AESEngine.newInstance());
            cipher.init(false, new AEADParameters(new KeyParameter(key), TAG_BITS, nonce, aad));

            byte[] plaintext = new byte[cipher.getOutputSize(cipherText.length)];
            int len = cipher.processBytes(cipherText, 0, cipherText.length, plaintext, 0);
            cipher.doFinal(plaintext, len);
            return plaintext;

        } catch (InvalidCipherTextException e) {
            // MAC fail → throw ngay, KHÔNG tiếp tục xử lý
            throw new MacVerificationException("GCM tag verification failed — connection must be closed");
        } catch (Exception e) {
            if (e instanceof MacVerificationException) {
                throw (MacVerificationException) e;
            }
            throw new DecryptionException("AES-GCM decrypt failed", e);
        }
    }

    private static void validateNonce(byte[] nonce) throws CryptoException {
        if (nonce == null || nonce.length != NONCE_SIZE) {
            throw new CryptoException("AES-GCM nonce must be exactly " + NONCE_SIZE + " bytes");
        }
    }
}
