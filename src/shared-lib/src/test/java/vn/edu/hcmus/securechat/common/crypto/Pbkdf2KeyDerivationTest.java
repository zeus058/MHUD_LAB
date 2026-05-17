package vn.edu.hcmus.securechat.common.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class Pbkdf2KeyDerivationTest {

    private static JsonNode testVectors;
    private static final HexFormat hex = HexFormat.of();

    @BeforeAll
    static void setUp() throws Exception {
        org.bouncycastle.jce.provider.BouncyCastleProvider bc = new org.bouncycastle.jce.provider.BouncyCastleProvider();
        java.security.Security.addProvider(bc);
        
        try (InputStream in = Pbkdf2KeyDerivationTest.class.getResourceAsStream("/test-vectors.json")) {
            ObjectMapper mapper = new ObjectMapper();
            testVectors = mapper.readTree(in).get("pbkdf2");
        }
    }

    @Test
    void testDeriveDbKey_matchesTestVector() throws Exception {
        char[] password = testVectors.get("password").asText().toCharArray();
        byte[] salt = hex.parseHex(testVectors.get("salt").asText());
        byte[] expectedKey = hex.parseHex(testVectors.get("expected_key").asText());

        byte[] actualKey = Pbkdf2KeyDerivation.deriveDbKey(password, salt);

        assertNotNull(actualKey);
        assertArrayEquals(expectedKey, actualKey, "Derived PBKDF2 key should match the precomputed test vector.");
    }
}
