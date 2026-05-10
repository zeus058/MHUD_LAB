package vn.edu.hcmus.securechat.common.crypto;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Utility chạy một lần để precompute expected values cho test-vectors.json.
 * Chạy: mvn exec:java -pl shared-lib -Dexec.mainClass="vn.edu.hcmus.securechat.common.crypto.TestVectorPrecompute"
 */
public class TestVectorPrecompute {

    public static void main(String[] args) throws Exception {
        org.bouncycastle.jce.provider.BouncyCastleProvider bc =
                new org.bouncycastle.jce.provider.BouncyCastleProvider();
        java.security.Security.addProvider(bc);

        HexFormat hex = HexFormat.of();
        System.out.println("=== Test Vector Precomputation ===\n");

        // --- AES-GCM ---
        byte[] aesKey = hex.parseHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        byte[] aesNonce = hex.parseHex("000000000000000000000000");
        byte[] plaintext = "SecureChat-TestVector-v1".getBytes(StandardCharsets.UTF_8);

        // Dùng BouncyCastle trực tiếp với nonce cố định
        org.bouncycastle.crypto.BlockCipher engine = org.bouncycastle.crypto.engines.AESEngine.newInstance();
        org.bouncycastle.crypto.modes.GCMModeCipher gcm = org.bouncycastle.crypto.modes.GCMBlockCipher.newInstance(engine);
        gcm.init(true, new org.bouncycastle.crypto.params.AEADParameters(
                new org.bouncycastle.crypto.params.KeyParameter(aesKey), 128, aesNonce, null));
        byte[] cipherAndTag = new byte[gcm.getOutputSize(plaintext.length)];
        int len = gcm.processBytes(plaintext, 0, plaintext.length, cipherAndTag, 0);
        gcm.doFinal(cipherAndTag, len);

        System.out.println("[AES-GCM]");
        System.out.println("  ciphertext_and_tag = " + hex.formatHex(cipherAndTag));

        // --- HKDF ---
        byte[] ssEcdhe = hex.parseHex("aabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccddaabbccdd");
        byte[] ssKyber = hex.parseHex("eeff0011eeff0011eeff0011eeff0011eeff0011eeff0011eeff0011eeff0011");
        byte[] sessionNonce = hex.parseHex("deadbeefdeadbeefdeadbeefdeadbeef");

        byte[] masterKey = HkdfKeyDerivation.deriveSessionKey(ssEcdhe, ssKyber, sessionNonce);
        System.out.println("\n[HKDF]");
        System.out.println("  expected_master_key = " + hex.formatHex(masterKey));

        // --- PBKDF2 ---
        char[] password = "TestPassword123".toCharArray();
        byte[] salt = hex.parseHex("0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20");

        byte[] dbKey = Pbkdf2KeyDerivation.deriveDbKey(password, salt);
        System.out.println("\n[PBKDF2]");
        System.out.println("  expected_key = " + hex.formatHex(dbKey));

        System.out.println("\n=== Done. Copy these values into test-vectors.json ===");
    }
}
