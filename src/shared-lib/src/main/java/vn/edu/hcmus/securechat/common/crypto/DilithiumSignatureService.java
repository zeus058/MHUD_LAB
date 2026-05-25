package vn.edu.hcmus.securechat.common.crypto;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumKeyGenerationParameters;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumKeyPairGenerator;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumParameters;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumPrivateKeyParameters;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumPublicKeyParameters;
import org.bouncycastle.pqc.crypto.crystals.dilithium.DilithiumSigner;

import vn.edu.hcmus.securechat.common.exception.CryptoException;

/**
 * ML-DSA-65/Dilithium-3 signature helper for hybrid transcript signing.
 */
public final class DilithiumSignatureService {
    private static final DilithiumParameters PARAMS = DilithiumParameters.dilithium3;
    private static final SecureRandom RNG = new SecureRandom();

    private DilithiumSignatureService() {}

    public static DilithiumKeyPair generateKeyPair() throws CryptoException {
        try {
            DilithiumKeyPairGenerator generator = new DilithiumKeyPairGenerator();
            generator.init(new DilithiumKeyGenerationParameters(RNG, PARAMS));
            AsymmetricCipherKeyPair pair = generator.generateKeyPair();
            DilithiumPublicKeyParameters publicKey = (DilithiumPublicKeyParameters) pair.getPublic();
            DilithiumPrivateKeyParameters privateKey = (DilithiumPrivateKeyParameters) pair.getPrivate();
            return new DilithiumKeyPair(publicKey.getEncoded(), privateKey.getEncoded());
        } catch (Exception e) {
            throw new CryptoException("Dilithium-3 key generation failed", e);
        }
    }

    public static byte[] sign(byte[] privateKeyEncoded, byte[] publicKeyEncoded, byte[] message)
            throws CryptoException {
        try {
            DilithiumPublicKeyParameters publicKey =
                    new DilithiumPublicKeyParameters(PARAMS, publicKeyEncoded);
            DilithiumPrivateKeyParameters privateKey =
                    new DilithiumPrivateKeyParameters(PARAMS, privateKeyEncoded, publicKey);
            DilithiumSigner signer = new DilithiumSigner();
            signer.init(true, privateKey);
            return signer.generateSignature(message);
        } catch (Exception e) {
            throw new CryptoException("Dilithium-3 signing failed", e);
        }
    }

    public static boolean verify(byte[] publicKeyEncoded, byte[] message, byte[] signature)
            throws CryptoException {
        try {
            DilithiumPublicKeyParameters publicKey =
                    new DilithiumPublicKeyParameters(PARAMS, publicKeyEncoded);
            DilithiumSigner signer = new DilithiumSigner();
            signer.init(false, publicKey);
            return signer.verifySignature(message, signature);
        } catch (Exception e) {
            throw new CryptoException("Dilithium-3 verification failed", e);
        }
    }

    public record DilithiumKeyPair(byte[] publicKey, byte[] privateKey) {
        public String publicKeyBase64() {
            return Base64.getEncoder().encodeToString(publicKey);
        }

        public String privateKeyBase64() {
            return Base64.getEncoder().encodeToString(privateKey);
        }

        public void destroyPrivateKey() {
            Arrays.fill(privateKey, (byte) 0);
        }
    }
}
