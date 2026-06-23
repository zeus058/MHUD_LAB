package vn.edu.hcmus.securechat.common.crypto;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import vn.edu.hcmus.securechat.common.exception.CryptoException;
import vn.edu.hcmus.securechat.common.exception.MacVerificationException;
import vn.edu.hcmus.securechat.common.exception.ReplayAttackException;
import vn.edu.hcmus.securechat.common.protocol.dto.AuthenticatorJson;

class CryptoTest {

    private static final HexFormat HEX = HexFormat.of();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ====================================================================
    // AES-GCM Tests
    // ====================================================================

    @Test
    void testAesGcmEncryptDecrypt() throws CryptoException {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);

        String originalMessage = "Hello SecureChat E2EE!";
        byte[] plaintext = originalMessage.getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = AesGcmCipher.encrypt(key, plaintext);
        assertNotNull(ciphertext);
        assertTrue(ciphertext.length > plaintext.length + 12);

        byte[] decrypted = AesGcmCipher.decrypt(key, ciphertext);
        assertEquals(originalMessage, new String(decrypted, StandardCharsets.UTF_8));
    }

    @Test
    void testAesGcmMacTamper() throws CryptoException {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);

        byte[] plaintext = "Secret Message".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = AesGcmCipher.encrypt(key, plaintext);

        // Tamper with ciphertext
        encrypted[15] ^= (byte) 0xFF;

        assertThrows(MacVerificationException.class, () -> {
            AesGcmCipher.decrypt(key, encrypted);
        });
    }

    @Test
    void testAesGcmAadTamper() throws CryptoException {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);

        byte[] plaintext = "AAD-bound message".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "conversation|1|alice|bob".getBytes(StandardCharsets.UTF_8);
        byte[] wrongAad = "conversation|1|mallory|bob".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = AesGcmCipher.encrypt(key, plaintext, aad);

        assertThrows(MacVerificationException.class, () ->
                AesGcmCipher.decrypt(key, encrypted, wrongAad));
    }

    @Test
    void testAesGcmTestVector() throws Exception {
        JsonNode tv = loadTestVectors().get("aes_gcm");

        byte[] key = HEX.parseHex(tv.get("key").asText());
        byte[] nonce = HEX.parseHex(tv.get("nonce").asText());
        byte[] plaintext = tv.get("plaintext").asText().getBytes(StandardCharsets.UTF_8);
        byte[] expectedCiphertextAndTag = HEX.parseHex(tv.get("ciphertext_and_tag").asText());

        // Encrypt với nonce cố định (chỉ dùng trong test vector verification)
        org.bouncycastle.crypto.modes.GCMModeCipher cipher =
                org.bouncycastle.crypto.modes.GCMBlockCipher.newInstance(
                        org.bouncycastle.crypto.engines.AESEngine.newInstance());
        cipher.init(true, new org.bouncycastle.crypto.params.AEADParameters(
                new org.bouncycastle.crypto.params.KeyParameter(key), 128, nonce, null));
        byte[] cipherAndTag = new byte[cipher.getOutputSize(plaintext.length)];
        int len = cipher.processBytes(plaintext, 0, plaintext.length, cipherAndTag, 0);
        cipher.doFinal(cipherAndTag, len);

        assertArrayEquals(expectedCiphertextAndTag, cipherAndTag,
                "AES-GCM ciphertext+tag does not match test vector");
    }

    // ====================================================================
    // HKDF Tests
    // ====================================================================

    @Test
    void testHkdfKeyDerivation() throws Exception {
        byte[] ssEcdhe = new byte[32];
        byte[] nonce = new byte[16];

        Arrays.fill(ssEcdhe, (byte) 1);
        Arrays.fill(nonce, (byte) 3);

        byte[] masterKey1 = HkdfKeyDerivation.deriveSessionKey(ssEcdhe, nonce);
        byte[] masterKey2 = HkdfKeyDerivation.deriveSessionKey(ssEcdhe, nonce);

        assertNotNull(masterKey1);
        assertEquals(32, masterKey1.length);
        assertArrayEquals(masterKey1, masterKey2); // deterministic
    }

    @Test
    void testHkdfTestVector() throws Exception {
        JsonNode tv = loadTestVectors().get("hkdf");

        byte[] ssEcdhe = HEX.parseHex(tv.get("ss_ecdhe").asText());
        byte[] sessionNonce = HEX.parseHex(tv.get("session_nonce").asText());
        byte[] expectedMasterKey = HEX.parseHex(tv.get("expected_master_key").asText());

        byte[] result = HkdfKeyDerivation.deriveSessionKey(ssEcdhe, sessionNonce);

        assertArrayEquals(expectedMasterKey, result,
                "HKDF master key does not match test vector");
    }

    // ====================================================================
    // PBKDF2 Tests
    // ====================================================================

    @Test
    void testPbkdf2KeyDerivation() throws Exception {
        char[] password = "StrongPassword123".toCharArray();
        byte[] salt = new byte[32];
        Arrays.fill(salt, (byte) 4);

        byte[] dbKey = Pbkdf2KeyDerivation.deriveDbKey(password, salt);
        assertNotNull(dbKey);
        assertEquals(32, dbKey.length);
    }

    @Test
    void testPbkdf2TestVector() throws Exception {
        JsonNode tv = loadTestVectors().get("pbkdf2");

        char[] password = tv.get("password").asText().toCharArray();
        byte[] salt = HEX.parseHex(tv.get("salt").asText());
        byte[] expectedKey = HEX.parseHex(tv.get("expected_key").asText());

        byte[] result = Pbkdf2KeyDerivation.deriveDbKey(password, salt);

        assertArrayEquals(expectedKey, result,
                "PBKDF2 key does not match test vector");
    }

    // ====================================================================
    // Replay Defense Tests
    // ====================================================================

    @Test
    void testReplayDefense_validAuthenticator() throws Exception {
        ReplayDefenseService defense = new ReplayDefenseService();
        try {
            AuthenticatorJson auth = new AuthenticatorJson(
                    "testUser", Instant.now().getEpochSecond(), "unique-nonce-001");
            assertDoesNotThrow(() -> defense.validateAuthenticator(auth));
        } finally {
            defense.shutdown();
        }
    }

    @Test
    void testReplayDefense_timestampSkew() {
        ReplayDefenseService defense = new ReplayDefenseService();
        try {
            // Timestamp 6 phút trước — vượt quá MAX_TIME_SKEW_SECONDS (300s = 5 phút)
            AuthenticatorJson auth = new AuthenticatorJson(
                    "testUser", Instant.now().getEpochSecond() - 400, "nonce-002");

            assertThrows(ReplayAttackException.class,
                    () -> defense.validateAuthenticator(auth));
        } finally {
            defense.shutdown();
        }
    }

    @Test
    void testReplayDefense_nonceReuse() throws Exception {
        ReplayDefenseService defense = new ReplayDefenseService();
        try {
            String sameNonce = "reused-nonce-003";
            long now = Instant.now().getEpochSecond();

            AuthenticatorJson auth1 = new AuthenticatorJson("user1", now, sameNonce);
            defense.validateAuthenticator(auth1); // pass lần đầu

            AuthenticatorJson auth2 = new AuthenticatorJson("user1", now, sameNonce);
            assertThrows(ReplayAttackException.class,
                    () -> defense.validateAuthenticator(auth2)); // bị reject
        } finally {
            defense.shutdown();
        }
    }

    // ====================================================================
    // NonceCache Tests
    // ====================================================================

    @Test
    void testNonceCache_basicOperations() {
        NonceCache cache = new NonceCache(10); // TTL 10 giây cho test nhanh
        try {
            assertFalse(cache.contains("nonce-a"));

            cache.put("nonce-a");
            assertTrue(cache.contains("nonce-a"));
            assertFalse(cache.contains("nonce-b"));
            assertEquals(1, cache.size());
        } finally {
            cache.shutdown();
        }
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    private JsonNode loadTestVectors() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/test-vectors.json")) {
            assertNotNull(is, "test-vectors.json not found in classpath");
            return MAPPER.readTree(is);
        }
    }
}
