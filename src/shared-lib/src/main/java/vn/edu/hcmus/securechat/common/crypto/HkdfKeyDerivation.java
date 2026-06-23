package vn.edu.hcmus.securechat.common.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;

import vn.edu.hcmus.securechat.common.exception.KeyDerivationException;

public class HkdfKeyDerivation {

    public static byte[] deriveSessionKey(
            byte[] ssEcdhe,   // 32 bytes shared secret từ ECDHE
            byte[] sessionNonce // 16 bytes, trao đổi trong handshake
    ) throws KeyDerivationException {

        byte[] ikm = Arrays.copyOf(ssEcdhe, ssEcdhe.length);

        try {
            return hkdf(ikm, sessionNonce, CryptoConstants.HKDF_INFO, CryptoConstants.AES_KEY_SIZE_BYTES);
        } catch (Exception e) {
            throw new KeyDerivationException("HKDF derivation failed", e);
        } finally {
            Arrays.fill(ikm, (byte) 0); // xóa IKM trung gian ngay sau dùng
        }
    }

    public static byte[] deriveConversationKey(byte[] ssEcdhe, byte[] salt)
            throws KeyDerivationException {
        byte[] master = null;
        try {
            master = deriveSessionKey(ssEcdhe, salt);
            return expand(master, "SecureChat-E2EE-conversation-v2",
                    CryptoConstants.E2EE_CONVERSATION_KEY_SIZE_BYTES);
        } finally {
            if (master != null) {
                Arrays.fill(master, (byte) 0);
            }
        }
    }

    public static byte[] expand(byte[] keyMaterial, String info, int length)
            throws KeyDerivationException {
        return hkdf(keyMaterial, null, info, length);
    }

    public static RatchetStep deriveRatchetStep(byte[] chainKey, long messageNumber)
            throws KeyDerivationException {
        byte[] msgKey = expand(chainKey, "SecureChat-msg-key-v2:" + messageNumber,
                CryptoConstants.AES_KEY_SIZE_BYTES);
        byte[] nextChainKey = expand(chainKey, "SecureChat-chain-step-v2:" + messageNumber,
                CryptoConstants.AES_KEY_SIZE_BYTES);
        byte[] nonce = expand(msgKey, "SecureChat-msg-iv-v2:" + messageNumber,
                CryptoConstants.GCM_NONCE_SIZE);
        return new RatchetStep(msgKey, nextChainKey, nonce);
    }

    private static byte[] hkdf(byte[] ikm, byte[] salt, String info, int length)
            throws KeyDerivationException {
        try {
            HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
            hkdf.init(new HKDFParameters(
                    ikm,
                    salt,
                    info.getBytes(StandardCharsets.UTF_8)));
            byte[] output = new byte[length];
            hkdf.generateBytes(output, 0, output.length);
            return output;
        } catch (Exception e) {
            throw new KeyDerivationException("HKDF failed for info=" + info, e);
        }
    }

    public record RatchetStep(byte[] messageKey, byte[] nextChainKey, byte[] nonce) {}
}
