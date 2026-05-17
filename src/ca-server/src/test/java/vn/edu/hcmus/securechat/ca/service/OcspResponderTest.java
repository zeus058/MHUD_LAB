package vn.edu.hcmus.securechat.ca.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vn.edu.hcmus.securechat.ca.storage.CertificateStorage;
import vn.edu.hcmus.securechat.ca.storage.CertificateStorage.CertStatus;
import vn.edu.hcmus.securechat.common.protocol.dto.OcspRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.OcspResponse;

class OcspResponderTest {

    private OcspResponder responder;
    private CertificateStorage mockStorage;
    private KeyPair mockCaKey;
    private X509Certificate mockCaCert;

    @BeforeAll
    static void setupProvider() {
        org.bouncycastle.jce.provider.BouncyCastleProvider bc = new org.bouncycastle.jce.provider.BouncyCastleProvider();
        java.security.Security.addProvider(bc);
    }

    @BeforeEach
    void setUp() throws Exception {
        mockStorage = mock(CertificateStorage.class);

        // Generate a real key pair for signing mock responses
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        mockCaKey = kpg.generateKeyPair();
        
        // We can just use a mock for the cert, or create one. OcspResponder only uses it for embedding (not signing).
        // Wait, OcspResponder doesn't actually embed the cert in our simple JSON response, but it signs with the private key.
        mockCaCert = mock(X509Certificate.class);

        responder = new OcspResponder(mockStorage, mockCaKey.getPrivate(), mockCaCert);
    }

    @Test
    void testRespondToOcspRequest_goodStatus() throws Exception {
        when(mockStorage.getCertificateStatus(anyString())).thenReturn(CertStatus.GOOD);

        OcspRequest request = new OcspRequest("1234abcd", "CN=SecureChat CA", "nonce123");
        OcspResponse response = responder.respondToOcspRequest(request);

        assertNotNull(response);
        assertEquals("GOOD", response.getCertStatus(), "Status should be GOOD");
        assertNotNull(response.getSignature(), "Response must be signed");
    }

    @Test
    void testRespondToOcspRequest_revokedStatus() throws Exception {
        when(mockStorage.getCertificateStatus(anyString())).thenReturn(CertStatus.REVOKED);
        
        // Mock the info retrieval
        CertificateStorage.CertificateInfo info = new CertificateStorage.CertificateInfo();
        info.revocationTime = System.currentTimeMillis() - 1000;
        info.revocationReason = "unspecified";
        when(mockStorage.getCertificateInfo(anyString())).thenReturn(java.util.Optional.of(info));

        OcspRequest request = new OcspRequest("1234abcd", "CN=SecureChat CA", "nonce123");
        OcspResponse response = responder.respondToOcspRequest(request);

        assertNotNull(response);
        assertEquals("REVOKED", response.getCertStatus(), "Status should be REVOKED");
        assertEquals("unspecified", response.getRevocationReason());
        assertNotNull(response.getSignature(), "Response must be signed");
    }
}
