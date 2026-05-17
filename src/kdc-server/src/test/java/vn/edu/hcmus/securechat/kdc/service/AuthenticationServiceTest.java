package vn.edu.hcmus.securechat.kdc.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vn.edu.hcmus.securechat.common.exception.CertificateRevokedException;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtResponse;
import vn.edu.hcmus.securechat.kdc.crypto.KdcKeyManager;
import vn.edu.hcmus.securechat.kdc.storage.KdcStorage;

class AuthenticationServiceTest {

    private AuthenticationService asService;
    private OcspClient mockOcspClient;
    private KdcStorage mockKdcStorage;
    private KdcKeyManager mockKeyManager;

    @BeforeEach
    void setUp() throws Exception {
        mockOcspClient = mock(OcspClient.class);
        mockKdcStorage = mock(KdcStorage.class);
        mockKeyManager = mock(KdcKeyManager.class);
        
        asService = new AuthenticationService(mockKeyManager, mockOcspClient, new vn.edu.hcmus.securechat.common.crypto.ReplayDefenseService(), mockKdcStorage);
    }

    // IT-04: Revoked cert phải fail khi xin vé
    @Test
    void revokedCert_shouldBeRejectedByAs() throws Exception {
        // Mock X509Certificate
        X509Certificate mockCert = mock(X509Certificate.class);
        javax.security.auth.x500.X500Principal principal = new javax.security.auth.x500.X500Principal("CN=Test");
        org.mockito.Mockito.when(mockCert.getSubjectX500Principal()).thenReturn(principal);
        org.mockito.Mockito.when(mockCert.getIssuerX500Principal()).thenReturn(principal);
        org.mockito.Mockito.when(mockCert.getSerialNumber()).thenReturn(java.math.BigInteger.ONE);

        org.mockito.Mockito.when(mockKeyManager.decodeCertificate(org.mockito.ArgumentMatchers.any())).thenReturn(mockCert);

        // Mock OCSP Client to throw CertificateRevokedException
        doThrow(new CertificateRevokedException("Revoked by admin"))
            .when(mockOcspClient).verifyCertificateStatus(anyString(), anyString());

        // Create a fake TgtRequest
        TgtRequest request = new TgtRequest();
        request.setClientId("user1");
        request.setTargetTgs("tgs.securechat.local");
        request.setNonce("nonce123");
        
        // Dummy certificate base64
        request.setCert(Base64.getEncoder().encodeToString("dummy".getBytes()));

        // Act & Assert
        assertThrows(CertificateRevokedException.class, () -> asService.issueTgt(request, "127.0.0.1"));
    }
}
