package vn.edu.hcmus.securechat.common.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.edu.hcmus.securechat.common.exception.MacVerificationException;

class AesGcmCipherTest {

    private static JsonNode testVectors;
    private static final HexFormat hex = HexFormat.of();

    @BeforeAll
    static void setUp() throws Exception {
        org.bouncycastle.jce.provider.BouncyCastleProvider bc = new org.bouncycastle.jce.provider.BouncyCastleProvider();
        java.security.Security.addProvider(bc);
        
        try (InputStream in = AesGcmCipherTest.class.getResourceAsStream("/test-vectors.json")) {
            ObjectMapper mapper = new ObjectMapper();
            testVectors = mapper.readTree(in).get("aes_gcm");
        }
    }

    @Test
    void testDecrypt_matchesTestVector() throws Exception {
        byte[] key = hex.parseHex(testVectors.get("key").asText());
        byte[] expectedPlaintext = testVectors.get("plaintext").asText().getBytes(StandardCharsets.UTF_8);
        byte[] cipherAndTag = hex.parseHex(testVectors.get("ciphertext_and_tag").asText());
        byte[] nonce = hex.parseHex(testVectors.get("nonce").asText());

        byte[] cipherData = new byte[nonce.length + cipherAndTag.length];
        System.arraycopy(nonce, 0, cipherData, 0, nonce.length);
        System.arraycopy(cipherAndTag, 0, cipherData, nonce.length, cipherAndTag.length);

        // We decrypt the static test vector
        byte[] actualPlaintext = AesGcmCipher.decrypt(key, cipherData);

        assertNotNull(actualPlaintext);
        assertArrayEquals(expectedPlaintext, actualPlaintext, "Decrypted plaintext should match expected.");
    }

    @Test
    void testEncryptDecrypt_roundTrip() throws Exception {
        byte[] key = hex.parseHex(testVectors.get("key").asText());
        byte[] plaintext = "Hello World! This is a test message.".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = AesGcmCipher.encrypt(key, plaintext);
        assertNotNull(encrypted);

        byte[] decrypted = AesGcmCipher.decrypt(key, encrypted);
        assertArrayEquals(plaintext, decrypted, "Round-trip encryption/decryption failed.");
    }

    @Test
    void testDecrypt_tamperedTag_throwsMacVerificationException() throws Exception {
        byte[] key = hex.parseHex(testVectors.get("key").asText());
        byte[] plaintext = "Secret Message".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = AesGcmCipher.encrypt(key, plaintext);
        
        // Tamper with the MAC tag (last byte)
        encrypted[encrypted.length - 1] ^= (byte) 0xFF;

        assertThrows(MacVerificationException.class, () -> {
            AesGcmCipher.decrypt(key, encrypted);
        }, "Tampering with the tag must throw MacVerificationException.");
    }
}
