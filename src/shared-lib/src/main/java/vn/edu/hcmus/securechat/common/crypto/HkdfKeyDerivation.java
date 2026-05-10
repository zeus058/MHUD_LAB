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

        byte[] info = CryptoConstants.HKDF_INFO.getBytes(StandardCharsets.UTF_8); // cố định

        try {
            HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
            hkdf.init(new HKDFParameters(ikm, sessionNonce, info));
            byte[] masterKey = new byte[CryptoConstants.AES_KEY_SIZE_BYTES]; // 256-bit cho AES-256
            hkdf.generateBytes(masterKey, 0, masterKey.length);
            return masterKey;
        } catch (Exception e) {
            throw new KeyDerivationException("HKDF derivation failed", e);
        } finally {
            Arrays.fill(ikm, (byte) 0); // xóa IKM trung gian ngay sau dùng
        }
    }
}
