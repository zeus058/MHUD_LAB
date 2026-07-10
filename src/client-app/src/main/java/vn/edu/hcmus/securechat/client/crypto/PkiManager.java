package vn.edu.hcmus.securechat.client.crypto;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.client.storage.ClientStoragePaths;
import vn.edu.hcmus.securechat.common.protocol.dto.CertificateSigningRequest;
import vn.edu.hcmus.securechat.common.protocol.dto.RevokeRequest;
import vn.edu.hcmus.securechat.common.crypto.CryptoConstants;
import vn.edu.hcmus.securechat.common.util.PathUtil;

/**
 * Manages RSA key generation, CSR creation, and local KeyStore storage.
 */
public class PkiManager {
    private static final Logger log = LoggerFactory.getLogger(PkiManager.class);
    
    // Temporary KeyPair for the current registration session.
    private static KeyPair currentKeyPair;
    private static X509Certificate currentCertificate;

    /**
     * Generates an RSA-2048 key pair.
     */
    public static void generateKeyPair(String username) throws Exception {
        log.info("Generating RSA-2048 KeyPair for user: {}", username);
        
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, new SecureRandom());
        currentKeyPair = keyGen.generateKeyPair();
    }

    /**
     * Creates the CertificateSigningRequest DTO expected by ca-server.
     */
    public static CertificateSigningRequest createCsrPayload(String subjectDn) throws Exception {
        if (currentKeyPair == null) {
            throw new IllegalStateException("KeyPair has not been generated yet.");
        }

        // 1. PublicKey as Base64 (raw DER)
        String pubKeyBase64 = Base64.getEncoder().encodeToString(currentKeyPair.getPublic().getEncoded());

        // 2. Nonce 16 bytes random (Base64)
        byte[] nonceBytes = new byte[CryptoConstants.NONCE_SIZE_BYTES];
        new SecureRandom().nextBytes(nonceBytes);
        String nonceBase64 = Base64.getEncoder().encodeToString(nonceBytes);

        // 3. Signature over subjectDn | publicKey | nonce
        String dataToSign = subjectDn + "|" + pubKeyBase64 + "|" + nonceBase64;
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(currentKeyPair.getPrivate());
        sig.update(dataToSign.getBytes(StandardCharsets.UTF_8));
        String signatureBase64 = Base64.getEncoder().encodeToString(sig.sign());

        return new CertificateSigningRequest(subjectDn, pubKeyBase64, nonceBase64, signatureBase64);
    }

    /**
     * Creates the RevokeRequest DTO.
     */
    public static RevokeRequest createRevokePayload(String username, String certSerial, String reason) throws Exception {
        if (currentKeyPair == null) {
            throw new IllegalStateException("KeyPair has not been loaded.");
        }

        String dataToSign = username + "|" + certSerial + "|" + reason;
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(currentKeyPair.getPrivate());
        sig.update(dataToSign.getBytes(StandardCharsets.UTF_8));
        String signatureBase64 = Base64.getEncoder().encodeToString(sig.sign());

        return new RevokeRequest(username, certSerial, reason, signatureBase64);
    }

    /**
     * Saves Private Key and Certificate Chain into a password-protected PKCS12 file.
     */
    public static void saveKeyStore(String username, char[] password, String certBase64, String caChainBase64) throws Exception {
        if (currentKeyPair == null) {
            throw new IllegalStateException("KeyPair has not been generated yet.");
        }

        log.info("Saving KeyStore for user: {}", username);

        // Parse Certificates
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        
        byte[] certBytes = Base64.getDecoder().decode(certBase64);
        X509Certificate clientCert = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(certBytes));

        byte[] caChainBytes = Base64.getDecoder().decode(caChainBase64);
        Collection<? extends Certificate> caCerts = certFactory.generateCertificates(new ByteArrayInputStream(caChainBytes));
        
        List<Certificate> chainList = new ArrayList<>();
        chainList.add(clientCert);
        chainList.addAll(caCerts);
        
        Certificate[] chain = chainList.toArray(new Certificate[0]);

        // Create PKCS12 KeyStore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null); // Initialize empty keystore

        // Set KeyEntry
        keyStore.setKeyEntry(username, currentKeyPair.getPrivate(), password, chain);

        ClientStoragePaths.ensureUserDir(username);
        Path ksPath = ClientStoragePaths.keystoreFile(username);
        try (FileOutputStream fos = new FileOutputStream(ksPath.toFile())) {
            keyStore.store(fos, password);
        }

        log.info("KeyStore saved successfully at: {}", ksPath.toAbsolutePath());
    }

    /**
     * Loads the PKCS12 KeyStore with the user's password.
     */
    public static void loadKeyStore(String username, char[] password) throws Exception {
        Path ksPath = resolveKeystorePath(username);
        if (ksPath == null) {
            Path expectedPath = ClientStoragePaths.keystoreFile(username);
            throw new Exception("KeyStore file not found for user: " + username + 
                ". Expected at: " + expectedPath.toAbsolutePath());
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(ksPath.toFile())) {
            keyStore.load(fis, password);
        }

        PrivateKey privKey = (PrivateKey) keyStore.getKey(username, password);
        Certificate cert = keyStore.getCertificate(username);
        if (privKey == null || cert == null) {
            throw new Exception("Cannot find key or certificate for alias: " + username);
        }

        currentKeyPair = new KeyPair(cert.getPublicKey(), privKey);
        currentCertificate = (X509Certificate) cert;
        log.info("KeyStore loaded successfully for user: {}", username);
    }

    public static PublicKey getPublicKey() {
        return currentKeyPair != null ? currentKeyPair.getPublic() : null;
    }

    public static PrivateKey getPrivateKey() {
        return currentKeyPair != null ? currentKeyPair.getPrivate() : null;
    }

    public static X509Certificate getCertificate() {
        return currentCertificate;
    }

    private static Path resolveKeystorePath(String username) throws Exception {
        Path current = ClientStoragePaths.keystoreFile(username);
        log.info("Checking keystore at: {}", current.toAbsolutePath());
        if (Files.isRegularFile(current)) {
            return current;
        }
        Path legacy = PathUtil.resolve("data/client/keystore_" + username + ".p12");
        if (Files.isRegularFile(legacy)) {
            ClientStoragePaths.ensureUserDir(username);
            Files.copy(legacy, current, StandardCopyOption.REPLACE_EXISTING);
            log.info("Migrating legacy keystore {} -> {}", legacy, current);
            return current;
        }
        return null;
    }
}
