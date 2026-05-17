package vn.edu.hcmus.securechat.kdc.crypto;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Arrays;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import vn.edu.hcmus.securechat.common.exception.CryptoException;

/**
 * Unit Tests cho HybridEncryption.
 *
 * Kiểm tra:
 * - Encrypt/Decrypt roundtrip với RSA-2048
 * - Plaintext lớn (> RSA block size)
 * - Tampered ciphertext → exception
 * - Sai private key → exception
 * - Empty plaintext
 */
@SuppressWarnings("deprecation")
class HybridEncryptionTest {

    private static KeyPair rsaKeyPair;
    private static KeyPair otherKeyPair;

    @BeforeAll
    static void setUp() throws Exception {
        java.security.Security.addProvider(new BouncyCastleProvider());

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        rsaKeyPair = kpg.generateKeyPair();
        otherKeyPair = kpg.generateKeyPair();
    }

    @Test
    void testEncryptDecryptRoundtrip() throws CryptoException {
        byte[] plaintext = "Hello SecureChat KDC!".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = HybridEncryption.encrypt(rsaKeyPair.getPublic(), plaintext);
        assertNotNull(ciphertext);
        assertTrue(ciphertext.length > plaintext.length);

        byte[] decrypted = HybridEncryption.decrypt(rsaKeyPair.getPrivate(), ciphertext);
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void testLargePayload() throws CryptoException {
        // TGT/ST JSON có thể lớn hơn RSA block size (245 bytes cho RSA-2048 OAEP)
        byte[] largePlaintext = new byte[4096];
        new SecureRandom().nextBytes(largePlaintext);

        byte[] ciphertext = HybridEncryption.encrypt(rsaKeyPair.getPublic(), largePlaintext);
        byte[] decrypted = HybridEncryption.decrypt(rsaKeyPair.getPrivate(), ciphertext);

        assertArrayEquals(largePlaintext, decrypted);
    }

    @Test
    void testTamperedCiphertext() throws CryptoException {
        byte[] plaintext = "Sensitive ticket data".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = HybridEncryption.encrypt(rsaKeyPair.getPublic(), plaintext);

        // Tamper with the AES-GCM part (after the RSA-encrypted DEK)
        int dekLen = ((ciphertext[0] & 0xFF) << 8) | (ciphertext[1] & 0xFF);
        int tamperIdx = 2 + dekLen + 5; // somewhere in the AES-GCM payload
        if (tamperIdx < ciphertext.length) {
            ciphertext[tamperIdx] ^= (byte) 0xFF;
        }

        assertThrows(CryptoException.class, () ->
            HybridEncryption.decrypt(rsaKeyPair.getPrivate(), ciphertext)
        );
    }

    @Test
    void testWrongPrivateKey() throws CryptoException {
        byte[] plaintext = "Only for the right recipient".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = HybridEncryption.encrypt(rsaKeyPair.getPublic(), plaintext);

        // Giải mã bằng private key khác → fail
        assertThrows(CryptoException.class, () ->
            HybridEncryption.decrypt(otherKeyPair.getPrivate(), ciphertext)
        );
    }

    @Test
    void testEmptyPlaintext() throws CryptoException {
        byte[] empty = new byte[0];
        byte[] ciphertext = HybridEncryption.encrypt(rsaKeyPair.getPublic(), empty);
        byte[] decrypted = HybridEncryption.decrypt(rsaKeyPair.getPrivate(), ciphertext);
        assertArrayEquals(empty, decrypted);
    }

    @Test
    void testShortCiphertext() {
        // Ciphertext quá ngắn → exception
        assertThrows(CryptoException.class, () ->
            HybridEncryption.decrypt(rsaKeyPair.getPrivate(), new byte[]{0x01, 0x02})
        );
    }

    @Test
    void testDifferentEncryptionsDifferentCiphertext() throws CryptoException {
        byte[] plaintext = "Same plaintext, different ciphertext".getBytes(StandardCharsets.UTF_8);

        byte[] ct1 = HybridEncryption.encrypt(rsaKeyPair.getPublic(), plaintext);
        byte[] ct2 = HybridEncryption.encrypt(rsaKeyPair.getPublic(), plaintext);

        // DEK khác nhau mỗi lần → ciphertext khác nhau
        assertFalse(Arrays.equals(ct1, ct2),
                "Two encryptions of the same plaintext should produce different ciphertexts");

        // Nhưng cả hai giải mã ra cùng plaintext
        assertArrayEquals(plaintext, HybridEncryption.decrypt(rsaKeyPair.getPrivate(), ct1));
        assertArrayEquals(plaintext, HybridEncryption.decrypt(rsaKeyPair.getPrivate(), ct2));
    }
}
