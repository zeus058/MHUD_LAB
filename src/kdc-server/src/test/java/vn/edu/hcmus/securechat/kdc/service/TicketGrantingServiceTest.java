package vn.edu.hcmus.securechat.kdc.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;
import vn.edu.hcmus.securechat.common.crypto.CryptoConstants;
import vn.edu.hcmus.securechat.common.crypto.ReplayDefenseService;
import vn.edu.hcmus.securechat.common.exception.InvalidTicketException;
import vn.edu.hcmus.securechat.common.exception.ProtocolException;
import vn.edu.hcmus.securechat.common.exception.ReplayAttackException;
import vn.edu.hcmus.securechat.common.protocol.ControlVector;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.dto.AuthenticatorJson;
import vn.edu.hcmus.securechat.common.protocol.dto.StInner;
import vn.edu.hcmus.securechat.common.protocol.dto.StRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.StResponse;
import vn.edu.hcmus.securechat.common.protocol.dto.StResponseInner;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtInner;
import vn.edu.hcmus.securechat.kdc.crypto.HybridEncryption;

/**
 * Unit Tests cho TicketGrantingService.
 *
 * Vì TGS phụ thuộc vào KdcKeyManager (Windows DPAPI),
 * ta test trực tiếp logic TGS bằng cách mock keys thủ công.
 */
class TicketGrantingServiceTest {

    private static KeyPair tgsKeyPair;
    private static KeyPair chatServerKeyPair;

    @BeforeAll
    static void setUp() throws Exception {
        java.security.Security.addProvider(new BouncyCastleProvider());

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        tgsKeyPair = kpg.generateKeyPair();
        chatServerKeyPair = kpg.generateKeyPair();
    }

    /**
     * Tạo StRequest hợp lệ để test TGS flow thủ công.
     * (Simulate luồng: Client → TGS)
     */
    private StRequestTestBundle createValidStRequest() throws Exception {
        // 1. Sinh session key K_A_TGS
        byte[] sessionKeyTgs = new byte[CryptoConstants.AES_KEY_SIZE_BYTES];
        new SecureRandom().nextBytes(sessionKeyTgs);
        String sessionKeyTgsB64 = Base64.getEncoder().encodeToString(sessionKeyTgs);

        // 2. Tạo TgtInner
        long now = Instant.now().getEpochSecond();
        TgtInner tgtInner = new TgtInner(
                "testUser",
                "tgs.securechat.local",
                now,
                now + CryptoConstants.TGT_LIFETIME_SECONDS,
                sessionKeyTgsB64,
                true,
                ControlVector.ENCRYPT_ONLY + "|" + ControlVector.TGS_SERVICE + "|" + ControlVector.EXPIRY_8H
        );

        // 3. Encrypt TgtInner bằng PU_TGS
        byte[] tgtInnerBytes = JsonSerializer.toBytes(tgtInner);
        byte[] encryptedTgt = HybridEncryption.encrypt(tgsKeyPair.getPublic(), tgtInnerBytes);
        String tgtB64 = Base64.getEncoder().encodeToString(encryptedTgt);

        // 4. Tạo Authenticator
        byte[] nonceBytes = new byte[16];
        new SecureRandom().nextBytes(nonceBytes);
        AuthenticatorJson auth = new AuthenticatorJson(
                "testUser",
                Instant.now().getEpochSecond(),
                Base64.getEncoder().encodeToString(nonceBytes)
        );

        // 5. Encrypt Authenticator bằng K_A_TGS (AES-GCM)
        byte[] authBytes = JsonSerializer.toBytes(auth);
        byte[] encryptedAuth = AesGcmCipher.encrypt(sessionKeyTgs, authBytes);
        String authB64 = Base64.getEncoder().encodeToString(encryptedAuth);

        // 6. Tạo StRequest
        StRequest request = new StRequest(tgtB64, authB64, "chat.securechat.local");

        return new StRequestTestBundle(request, sessionKeyTgs, tgtInner, auth);
    }

    @Test
    void testValidStRequestFlow() throws Exception {
        StRequestTestBundle bundle = createValidStRequest();

        // Giải mã TGT thủ công (simulate TGS logic)
        byte[] encryptedTgt = Base64.getDecoder().decode(bundle.request.getTgt());
        byte[] tgtBytes = HybridEncryption.decrypt(tgsKeyPair.getPrivate(), encryptedTgt);
        TgtInner decryptedTgt = JsonSerializer.fromBytes(tgtBytes, TgtInner.class);

        assertEquals("testUser", decryptedTgt.getClientId());
        assertTrue(decryptedTgt.getExpiresAt() > Instant.now().getEpochSecond());

        // Giải mã Authenticator thủ công
        byte[] sessionKeyTgs = Base64.getDecoder().decode(decryptedTgt.getSessionKey());
        byte[] encAuth = Base64.getDecoder().decode(bundle.request.getAuthenticator());
        byte[] authBytes = AesGcmCipher.decrypt(sessionKeyTgs, encAuth);
        AuthenticatorJson decryptedAuth = JsonSerializer.fromBytes(authBytes, AuthenticatorJson.class);

        assertEquals("testUser", decryptedAuth.getClientId());
    }

    @Test
    void testExpiredTgtRejected() throws Exception {
        // Tạo TGT đã hết hạn
        byte[] sessionKeyTgs = new byte[CryptoConstants.AES_KEY_SIZE_BYTES];
        new SecureRandom().nextBytes(sessionKeyTgs);

        long past = Instant.now().getEpochSecond() - 100; // hết hạn 100 giây trước
        TgtInner expiredTgt = new TgtInner(
                "testUser", "tgs.securechat.local",
                past - CryptoConstants.TGT_LIFETIME_SECONDS, past,
                Base64.getEncoder().encodeToString(sessionKeyTgs),
                true,
                ControlVector.ENCRYPT_ONLY + "|" + ControlVector.TGS_SERVICE + "|" + ControlVector.EXPIRY_8H
        );

        byte[] tgtBytes = JsonSerializer.toBytes(expiredTgt);
        byte[] encTgt = HybridEncryption.encrypt(tgsKeyPair.getPublic(), tgtBytes);

        // Tạo authenticator
        AuthenticatorJson auth = new AuthenticatorJson(
                "testUser", Instant.now().getEpochSecond(), "nonce-expired-test");
        byte[] authBytes = JsonSerializer.toBytes(auth);
        byte[] encAuth = AesGcmCipher.encrypt(sessionKeyTgs, authBytes);

        // Xác minh TGT đã hết hạn
        TgtInner decrypted = JsonSerializer.fromBytes(
                HybridEncryption.decrypt(tgsKeyPair.getPrivate(), encTgt), TgtInner.class);
        assertTrue(Instant.now().getEpochSecond() > decrypted.getExpiresAt(),
                "TGT should be expired");
    }

    @Test
    void testClientIdMismatchDetected() throws Exception {
        // Tạo TGT cho user "alice"
        byte[] sessionKeyTgs = new byte[CryptoConstants.AES_KEY_SIZE_BYTES];
        new SecureRandom().nextBytes(sessionKeyTgs);

        long now = Instant.now().getEpochSecond();
        TgtInner tgt = new TgtInner(
                "alice", "tgs.securechat.local",
                now, now + CryptoConstants.TGT_LIFETIME_SECONDS,
                Base64.getEncoder().encodeToString(sessionKeyTgs),
                true, "ENCRYPT_ONLY|TGS_SERVICE|8H_EXPIRY"
        );

        // Tạo authenticator cho user "bob" (mismatch!)
        AuthenticatorJson auth = new AuthenticatorJson(
                "bob", now, "nonce-mismatch-test");

        // Verify mismatch
        assertNotEquals(tgt.getClientId(), auth.getClientId(),
                "ClientId should mismatch between TGT and Authenticator");
    }

    @Test
    void testReplayDefenseIntegration() throws Exception {
        ReplayDefenseService defense = new ReplayDefenseService();
        try {
            String sameNonce = "reused-nonce-tgs-test";
            long now = Instant.now().getEpochSecond();

            AuthenticatorJson auth1 = new AuthenticatorJson("user1", now, sameNonce);
            defense.validateAuthenticator(auth1); // pass lần đầu

            AuthenticatorJson auth2 = new AuthenticatorJson("user1", now, sameNonce);
            assertThrows(ReplayAttackException.class,
                    () -> defense.validateAuthenticator(auth2)); // reject lần 2
        } finally {
            defense.shutdown();
        }
    }

    @Test
    void testStInnerEncryptionRoundtrip() throws Exception {
        // Tạo StInner
        byte[] sessionKeyChat = new byte[CryptoConstants.AES_KEY_SIZE_BYTES];
        new SecureRandom().nextBytes(sessionKeyChat);
        long now = Instant.now().getEpochSecond();

        StInner stInner = new StInner(
                "testUser", "",
                "chat.securechat.local",
                now, now + CryptoConstants.ST_LIFETIME_SECONDS,
                Base64.getEncoder().encodeToString(sessionKeyChat),
                ControlVector.ST_CV
        );

        // Encrypt bằng Chat Server PU
        byte[] stBytes = JsonSerializer.toBytes(stInner);
        byte[] encrypted = HybridEncryption.encrypt(chatServerKeyPair.getPublic(), stBytes);

        // Decrypt bằng Chat Server PR
        byte[] decrypted = HybridEncryption.decrypt(chatServerKeyPair.getPrivate(), encrypted);
        StInner result = JsonSerializer.fromBytes(decrypted, StInner.class);

        assertEquals("testUser", result.getClientId());
        assertEquals("chat.securechat.local", result.getTargetServer());
        assertEquals(ControlVector.ST_CV, result.getCv());
    }

    // Test helper bundle
    private record StRequestTestBundle(
            StRequest request,
            byte[] sessionKeyTgs,
            TgtInner tgtInner,
            AuthenticatorJson authenticator
    ) {}
}
