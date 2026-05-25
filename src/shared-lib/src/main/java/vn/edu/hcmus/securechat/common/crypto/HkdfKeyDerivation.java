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
            byte[] ssKyber,   // 32 bytes shared secret từ Kyber
            byte[] sessionNonce // 16 bytes, trao đổi trong handshake
    ) throws KeyDerivationException {

        // Thứ tự concatenate: SS_ECDHE TRƯỚC, SS_KYBER SAU — BẤT BIẾN
        byte[] ikm = new byte[ssEcdhe.length + ssKyber.length];
        System.arraycopy(ssEcdhe, 0, ikm, 0, ssEcdhe.length);
        System.arraycopy(ssKyber, 0, ikm, ssEcdhe.length, ssKyber.length);

        try {
            return hkdf(ikm, sessionNonce, CryptoConstants.HKDF_INFO, CryptoConstants.AES_KEY_SIZE_BYTES);
        } catch (Exception e) {
            throw new KeyDerivationException("HKDF derivation failed", e);
        } finally {
            Arrays.fill(ikm, (byte) 0); // xóa IKM trung gian ngay sau dùng
        }
    }

    public static byte[] deriveConversationKey(byte[] ssEcdhe, byte[] ssKyber, byte[] salt)
            throws KeyDerivationException {
        byte[] master = null;
        try {
            master = deriveSessionKey(ssEcdhe, ssKyber, salt);
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
