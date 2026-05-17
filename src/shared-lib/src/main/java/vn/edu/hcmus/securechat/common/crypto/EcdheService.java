package vn.edu.hcmus.securechat.common.crypto;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.KeyAgreement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.exception.CryptoException;

/**
 * Dịch vụ ECDHE (Elliptic Curve Diffie-Hellman Ephemeral).
 * Theo Contrains.md mục 3.4 — handshake giữa Chat Server và Client.
 *
 * Curve: secp256r1 (NIST P-256)
 * Output: 32 bytes shared secret (SS_ECDHE)
 *
 * Luồng:
 *   1. Mỗi bên sinh KeyPair ephemeral (generateKeyPair)
 *   2. Trao đổi public key qua channel (getEncodedPublicKey / decodePublicKey)
 *   3. Compute shared secret (computeSharedSecret)
 *   4. Xóa private key ngay sau khi tính xong (clearPrivateKey)
 */
public final class EcdheService {

    private static final Logger log = LoggerFactory.getLogger(EcdheService.class);

    private static final String CURVE_NAME = "secp256r1";
    private static final String KEY_ALGORITHM = "EC";
    private static final String KA_ALGORITHM = "ECDH";

    private EcdheService() {}

    /**
     * Sinh ephemeral ECDH KeyPair.
     * Gọi mỗi lần bắt đầu handshake mới.
     */
    public static KeyPair generateKeyPair() throws CryptoException {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            kpg.initialize(new ECGenParameterSpec(CURVE_NAME));
            KeyPair pair = kpg.generateKeyPair();
            log.debug("ECDHE ephemeral KeyPair generated (curve={})", CURVE_NAME);
            return pair;
        } catch (Exception e) {
            throw new CryptoException("ECDHE key pair generation failed", e);
        }
    }

    /**
     * Encode public key thành Base64 string để truyền qua JSON.
     * Format: X.509 SubjectPublicKeyInfo DER → Base64
     */
    public static String encodePublicKey(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * Decode public key từ Base64 (X.509 SubjectPublicKeyInfo DER).
     */
    public static PublicKey decodePublicKey(String base64PublicKey) throws CryptoException {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
            KeyFactory kf = KeyFactory.getInstance(KEY_ALGORITHM);
            return kf.generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new CryptoException("Failed to decode ECDHE public key", e);
        }
    }

    /**
     * Decode public key từ raw bytes (X.509 SubjectPublicKeyInfo DER).
     */
    public static PublicKey decodePublicKey(byte[] publicKeyBytes) throws CryptoException {
        try {
            KeyFactory kf = KeyFactory.getInstance(KEY_ALGORITHM);
            return kf.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        } catch (Exception e) {
            throw new CryptoException("Failed to decode ECDHE public key bytes", e);
        }
    }

    /**
     * Tính shared secret từ local private key + remote public key.
     *
     * @param localPrivateKey  Private key của mình (ephemeral)
     * @param remotePublicKey  Public key từ đối phương
     * @return 32 bytes shared secret (SS_ECDHE)
     */
    public static byte[] computeSharedSecret(PrivateKey localPrivateKey, PublicKey remotePublicKey)
            throws CryptoException {
        byte[] fullSecret = null;
        try {
            KeyAgreement ka = KeyAgreement.getInstance(KA_ALGORITHM);
            ka.init(localPrivateKey);
            ka.doPhase(remotePublicKey, true);
            fullSecret = ka.generateSecret();

            // Truncate hoặc hash để đảm bảo đúng 32 bytes
            if (fullSecret.length >= CryptoConstants.AES_KEY_SIZE_BYTES) {
                byte[] result = new byte[CryptoConstants.AES_KEY_SIZE_BYTES];
                System.arraycopy(fullSecret, 0, result, 0, result.length);
                return result;
            } else {
                // Trường hợp hiếm: pad nếu thiếu
                byte[] result = new byte[CryptoConstants.AES_KEY_SIZE_BYTES];
                System.arraycopy(fullSecret, 0, result, 0, fullSecret.length);
                return result;
            }
        } catch (Exception e) {
            throw new CryptoException("ECDHE shared secret computation failed", e);
        } finally {
            if (fullSecret != null) Arrays.fill(fullSecret, (byte) 0);
        }
    }
}
