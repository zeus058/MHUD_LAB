package vn.edu.hcmus.securechat.ca.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CertificateAuthorityTest {

    private CertificateAuthority ca;

    @BeforeAll
    static void setupProvider() {
        org.bouncycastle.jce.provider.BouncyCastleProvider bc = new org.bouncycastle.jce.provider.BouncyCastleProvider();
        java.security.Security.addProvider(bc);
    }

    @BeforeEach
    void setUp() throws Exception {
        // Generate mock CA key pair and cert
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair pair = kpg.generateKeyPair();
        
        org.bouncycastle.operator.ContentSigner sigGen = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate());
        org.bouncycastle.cert.X509v3CertificateBuilder certGen = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
            new org.bouncycastle.asn1.x500.X500Name("CN=TestCA"),
            java.math.BigInteger.valueOf(1),
            new java.util.Date(System.currentTimeMillis() - 100000),
            new java.util.Date(System.currentTimeMillis() + 100000),
            new org.bouncycastle.asn1.x500.X500Name("CN=TestCA"),
            pair.getPublic()
        );
        
        byte[] certBytes = certGen.build(sigGen).getEncoded();
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509", "BC");
        X509Certificate caCert = (X509Certificate) certFactory.generateCertificate(new java.io.ByteArrayInputStream(certBytes));

        ca = new CertificateAuthority(pair.getPrivate(), caCert);
    }

    @Test
    void testCaInitialization_createsKeysAndCert() throws Exception {
        assertNotNull(ca.getCaPrivateKey(), "CA Private Key must be generated");
        assertNotNull(ca.getCaCertificate(), "CA Certificate must be generated");
        assertNotNull(ca.getCertificateChain(), "CA Certificate Chain must be created");
        assertTrue(ca.getCertificateChain().length > 0, "Chain should not be empty");
    }

    @Test
    void testIssueCertificate_createsValidClientCertificate() throws Exception {
        // Generate a mock client key pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair clientPair = kpg.generateKeyPair();
        PublicKey clientPubKey = clientPair.getPublic();

        // Issue a certificate for the client
        String subjectDn = "CN=testUser";
        byte[] certDer = ca.issueCertificate(subjectDn, clientPubKey);
        assertNotNull(certDer, "Issued certificate DER bytes must not be null");

        // Validate the generated certificate
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509", "BC");
        X509Certificate cert = (X509Certificate) certFactory.generateCertificate(new java.io.ByteArrayInputStream(certDer));

        assertNotNull(cert);
        assertTrue(cert.getSubjectX500Principal().getName().contains("CN=testUser"));
        
        // Verify signature with CA's public key
        assertDoesNotThrow(() -> cert.verify(ca.getCaCertificate().getPublicKey()), "Certificate must be signed by the CA");
    }
}
