package vn.edu.hcmus.securechat.common.crypto;

import org.bouncycastle.crypto.PBEParametersGenerator;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;

import vn.edu.hcmus.securechat.common.exception.KeyDerivationException;

public class Pbkdf2KeyDerivation {

    public static final int ITERATIONS  = CryptoConstants.PBKDF2_ITERATIONS;
    public static final int SALT_SIZE   = CryptoConstants.PBKDF2_SALT_SIZE;
    public static final int KEY_SIZE    = CryptoConstants.AES_KEY_SIZE_BYTES;

    public static byte[] deriveDbKey(char[] password, byte[] salt)
            throws KeyDerivationException {
        // password phải là char[], KHÔNG phải String
        try {
            PKCS5S2ParametersGenerator gen =
                new PKCS5S2ParametersGenerator(new SHA256Digest());
            gen.init(PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(password),
                     salt, ITERATIONS);
            return ((KeyParameter) gen.generateDerivedParameters(KEY_SIZE * 8)).getKey();
        } catch (Exception e) {
            throw new KeyDerivationException("PBKDF2 failed", e);
        }
    }
}
