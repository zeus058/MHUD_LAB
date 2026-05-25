package vn.edu.hcmus.securechat.common.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class Argon2idKeyDerivationTest {

    @Test
    void deriveDbKeyIsDeterministicForSameSalt() throws Exception {
        char[] password = "Correct Horse Battery Staple".toCharArray();
        byte[] salt = new byte[CryptoConstants.ARGON2ID_SALT_SIZE];
        Arrays.fill(salt, (byte) 7);

        byte[] first = Argon2idKeyDerivation.deriveDbKey(password, salt);
        byte[] second = Argon2idKeyDerivation.deriveDbKey(password, salt);

        assertNotNull(first);
        assertEquals(CryptoConstants.AES_KEY_SIZE_BYTES, first.length);
        assertArrayEquals(first, second);
    }

    @Test
    void rejectsWrongSaltLength() {
        assertThrows(Exception.class, () ->
                Argon2idKeyDerivation.deriveDbKey("pw".toCharArray(), new byte[8]));
    }
}
