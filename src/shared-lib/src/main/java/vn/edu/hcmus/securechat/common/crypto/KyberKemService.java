package vn.edu.hcmus.securechat.common.crypto;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.KEM;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.exception.CryptoException;

/**
 * Dịch vụ ML-KEM-768 (Kyber) Key Encapsulation — Kháng lượng tử.
 * Theo Contrains.md mục 3.4 — NIST FIPS 203.
 *
 * ML-KEM-768 cung cấp:
 * - Key generation: sinh Kyber KeyPair (public + private)
 * - Encapsulation (Client side): tạo ciphertext + shared secret từ server public key
 * - Decapsulation (Server side): giải ciphertext bằng private key → shared secret
 *
 * Output: 32 bytes shared secret (SS_KYBER)
 *
 * Sử dụng Java 25 KEM API + BouncyCastle provider.
 */
public final class KyberKemService {

    private static final Logger log = LoggerFactory.getLogger(KyberKemService.class);

    private static final String ALGORITHM = "ML-KEM";
    private static final String PROVIDER = "BC";

    private KyberKemService() {}

    /**
     * Sinh ML-KEM-768 KeyPair (server side, thực hiện một lần khi khởi động).
     */
    public static KeyPair generateKeyPair() throws CryptoException {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(ALGORITHM, PROVIDER);
            // ML-KEM-768 parameter spec
            kpg.initialize(new java.security.spec.NamedParameterSpec(CryptoConstants.KYBER_PARAM));
            KeyPair pair = kpg.generateKeyPair();
            log.debug("ML-KEM-768 KeyPair generated");
            return pair;
        } catch (Exception e) {
            throw new CryptoException("ML-KEM-768 key pair generation failed", e);
        }
    }

    /**
     * Encode public key thành Base64 để truyền qua JSON.
     */
    public static String encodePublicKey(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * Decode public key từ Base64.
     */
    public static PublicKey decodePublicKey(String base64PublicKey) throws CryptoException {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
            KeyFactory kf = KeyFactory.getInstance(ALGORITHM, PROVIDER);
            return kf.generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new CryptoException("Failed to decode ML-KEM public key", e);
        }
    }

    /**
     * Decode public key từ raw bytes.
     */
    public static PublicKey decodePublicKey(byte[] publicKeyBytes) throws CryptoException {
        try {
            KeyFactory kf = KeyFactory.getInstance(ALGORITHM, PROVIDER);
            return kf.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        } catch (Exception e) {
            throw new CryptoException("Failed to decode ML-KEM public key bytes", e);
        }
    }

    /**
     * Encapsulation (Client side) — tạo ciphertext + shared secret.
     *
     * @param serverPublicKey ML-KEM public key của server
     * @return EncapsulationResult chứa ciphertext (gửi cho server) và sharedSecret (32 bytes)
     */
    public static EncapsulationResult encapsulate(PublicKey serverPublicKey) throws CryptoException {
        try {
            KEM kem = KEM.getInstance(ALGORITHM, PROVIDER);
            KEM.Encapsulator encapsulator = kem.newEncapsulator(serverPublicKey);
            KEM.Encapsulated encapsulated = encapsulator.encapsulate();

            byte[] ciphertext = encapsulated.encapsulation();
            byte[] sharedSecret = encapsulated.key().getEncoded();

            // Đảm bảo 32 bytes
            byte[] ss = normalizeSecret(sharedSecret);

            log.debug("ML-KEM encapsulation done, ciphertext={} bytes, secret={} bytes",
                    ciphertext.length, ss.length);

            return new EncapsulationResult(ciphertext, ss);
        } catch (Exception e) {
            throw new CryptoException("ML-KEM encapsulation failed", e);
        }
    }

    /**
     * Decapsulation (Server side) — giải ciphertext bằng private key.
     *
     * @param serverPrivateKey ML-KEM private key của server
     * @param ciphertext       Ciphertext từ client encapsulation
     * @return 32 bytes shared secret (SS_KYBER)
     */
    public static byte[] decapsulate(PrivateKey serverPrivateKey, byte[] ciphertext)
            throws CryptoException {
        try {
            KEM kem = KEM.getInstance(ALGORITHM, PROVIDER);
            KEM.Decapsulator decapsulator = kem.newDecapsulator(serverPrivateKey);
            javax.crypto.SecretKey secretKey = decapsulator.decapsulate(ciphertext);
            byte[] sharedSecret = secretKey.getEncoded();

            byte[] ss = normalizeSecret(sharedSecret);
            log.debug("ML-KEM decapsulation done, secret={} bytes", ss.length);

            return ss;
        } catch (Exception e) {
            throw new CryptoException("ML-KEM decapsulation failed", e);
        }
    }

    /**
     * Normalize shared secret to exactly 32 bytes.
     */
    private static byte[] normalizeSecret(byte[] raw) {
        if (raw.length == CryptoConstants.AES_KEY_SIZE_BYTES) {
            return Arrays.copyOf(raw, raw.length);
        }
        byte[] result = new byte[CryptoConstants.AES_KEY_SIZE_BYTES];
        System.arraycopy(raw, 0, result, 0, Math.min(raw.length, result.length));
        return result;
    }

    /**
     * Kết quả Encapsulation — chứa ciphertext + shared secret.
     */
    public record EncapsulationResult(
            byte[] ciphertext,
            byte[] sharedSecret
    ) {
        public String ciphertextBase64() {
            return Base64.getEncoder().encodeToString(ciphertext);
        }

        public String sharedSecretBase64() {
            return Base64.getEncoder().encodeToString(sharedSecret);
        }
    }
}
