package vn.edu.hcmus.securechat.common.crypto;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import vn.edu.hcmus.securechat.common.exception.KeyDerivationException;

/**
 * Argon2id password-to-key derivation for local-at-rest secrets.
 */
public final class Argon2idKeyDerivation {

    public static final int SALT_SIZE = CryptoConstants.ARGON2ID_SALT_SIZE;
    public static final int MEMORY_KIB = CryptoConstants.ARGON2ID_MEMORY_KIB;
    public static final int ITERATIONS = CryptoConstants.ARGON2ID_ITERATIONS;
    public static final int PARALLELISM = CryptoConstants.ARGON2ID_PARALLELISM;

    private Argon2idKeyDerivation() {}

    public static byte[] deriveDbKey(char[] password, byte[] salt)
            throws KeyDerivationException {
        return deriveKey(password, salt, CryptoConstants.AES_KEY_SIZE_BYTES);
    }

    public static byte[] deriveKey(char[] password, byte[] salt, int outputLength)
            throws KeyDerivationException {
        if (password == null || password.length == 0) {
            throw new KeyDerivationException("Password must not be empty");
        }
        if (salt == null || salt.length != SALT_SIZE) {
            throw new KeyDerivationException("Argon2id salt must be " + SALT_SIZE + " bytes");
        }
        if (outputLength <= 0) {
            throw new KeyDerivationException("Output length must be positive");
        }

        byte[] passwordBytes = null;
        try {
            passwordBytes = toUtf8(password);

            Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withSalt(Arrays.copyOf(salt, salt.length))
                    .withMemoryAsKB(MEMORY_KIB)
                    .withIterations(ITERATIONS)
                    .withParallelism(PARALLELISM)
                    .build();

            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(parameters);
            byte[] key = new byte[outputLength];
            generator.generateBytes(passwordBytes, key);
            return key;
        } catch (KeyDerivationException e) {
            throw e;
        } catch (Exception e) {
            throw new KeyDerivationException("Argon2id derivation failed", e);
        } finally {
            if (passwordBytes != null) {
                Arrays.fill(passwordBytes, (byte) 0);
            }
        }
    }

    private static byte[] toUtf8(char[] password) throws KeyDerivationException {
        try {
            ByteBuffer buffer = StandardCharsets.UTF_8
                    .newEncoder()
                    .encode(CharBuffer.wrap(password));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            if (buffer.hasArray()) {
                Arrays.fill(buffer.array(), (byte) 0);
            }
            return bytes;
        } catch (CharacterCodingException e) {
            throw new KeyDerivationException("Password UTF-8 encoding failed", e);
        }
    }
}
