package vn.edu.hcmus.securechat.client.crypto;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
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

import vn.edu.hcmus.securechat.common.protocol.dto.CertificateSigningRequest;

/**
 * Quản lý sinh khóa RSA, tạo CSR và lưu trữ KeyStore cục bộ.
 */
public class PkiManager {
    private static final Logger log = LoggerFactory.getLogger(PkiManager.class);
    
    // Lưu trữ tạm thời KeyPair để dùng trong phiên (đợi nhận cert)
    private static KeyPair currentKeyPair;
    private static X509Certificate currentCertificate;

    /**
     * Sinh cặp khóa RSA 2048.
     */
    public static void generateKeyPair(String username) throws Exception {
        log.info("Generating RSA-2048 KeyPair for user: {}", username);
        
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, new SecureRandom());
        currentKeyPair = keyGen.generateKeyPair();
    }

    /**
     * Tạo DTO CertificateSigningRequest với định dạng chuẩn của ca-server.
     */
    public static CertificateSigningRequest createCsrPayload(String subjectDn) throws Exception {
        if (currentKeyPair == null) {
            throw new IllegalStateException("KeyPair has not been generated yet.");
        }

        // 1. PublicKey dạng Base64 (Raw DER)
        String pubKeyBase64 = Base64.getEncoder().encodeToString(currentKeyPair.getPublic().getEncoded());

        // 2. Nonce 16 bytes random (Base64)
        byte[] nonceBytes = new byte[16];
        new SecureRandom().nextBytes(nonceBytes);
        String nonceBase64 = Base64.getEncoder().encodeToString(nonceBytes);

        // 3. Signature trên subjectDn | publicKey | nonce
        String dataToSign = subjectDn + "|" + pubKeyBase64 + "|" + nonceBase64;
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(currentKeyPair.getPrivate());
        sig.update(dataToSign.getBytes(StandardCharsets.UTF_8));
        String signatureBase64 = Base64.getEncoder().encodeToString(sig.sign());

        return new CertificateSigningRequest(subjectDn, pubKeyBase64, nonceBase64, signatureBase64);
    }

    /**
     * Lưu Private Key và Certificate Chain vào file PKCS12, bảo vệ bằng password.
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

        // Tạo KeyStore PKCS12
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null); // Initialize empty keystore

        // Set KeyEntry
        keyStore.setKeyEntry(username, currentKeyPair.getPrivate(), password, chain);

        // Đảm bảo thư mục tồn tại
        File dir = new File("data/client");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Lưu ra file
        File ksFile = new File(dir, "keystore_" + username + ".p12");
        try (FileOutputStream fos = new FileOutputStream(ksFile)) {
            keyStore.store(fos, password);
        }

        log.info("KeyStore saved successfully at: {}", ksFile.getAbsolutePath());
    }

    /**
     * Tải KeyStore từ file PKCS12 bằng password của người dùng.
     */
    public static void loadKeyStore(String username, char[] password) throws Exception {
        File ksFile = new File("data/client", "keystore_" + username + ".p12");
        if (!ksFile.exists()) {
            throw new Exception("KeyStore file not found for user: " + username);
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (java.io.FileInputStream fis = new java.io.FileInputStream(ksFile)) {
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
}
