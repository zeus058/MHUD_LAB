package vn.edu.hcmus.securechat.common.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.HexFormat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class HkdfKeyDerivationTest {

    private static JsonNode testVectors;
    private static final HexFormat hex = HexFormat.of();

    @BeforeAll
    static void setUp() throws Exception {
        org.bouncycastle.jce.provider.BouncyCastleProvider bc = new org.bouncycastle.jce.provider.BouncyCastleProvider();
        java.security.Security.addProvider(bc);
        
        try (InputStream in = HkdfKeyDerivationTest.class.getResourceAsStream("/test-vectors.json")) {
            ObjectMapper mapper = new ObjectMapper();
            testVectors = mapper.readTree(in).get("hkdf");
        }
    }

    @Test
    void testDeriveSessionKey_matchesTestVector() throws Exception {
        byte[] ssEcdhe = hex.parseHex(testVectors.get("ss_ecdhe").asText());
        byte[] ssKyber = hex.parseHex(testVectors.get("ss_kyber").asText());
        byte[] sessionNonce = hex.parseHex(testVectors.get("session_nonce").asText());
        byte[] expectedMasterKey = hex.parseHex(testVectors.get("expected_master_key").asText());

        byte[] actualMasterKey = HkdfKeyDerivation.deriveSessionKey(ssEcdhe, ssKyber, sessionNonce);

        assertNotNull(actualMasterKey);
        assertArrayEquals(expectedMasterKey, actualMasterKey, "Derived master key should match the precomputed test vector.");
    }
}
