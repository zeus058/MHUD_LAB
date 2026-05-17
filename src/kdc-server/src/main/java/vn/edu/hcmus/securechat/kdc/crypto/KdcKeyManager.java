package vn.edu.hcmus.securechat.kdc.crypto;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.crypto.KeyStoreManager;
import vn.edu.hcmus.securechat.common.exception.ChainValidationException;
import vn.edu.hcmus.securechat.common.exception.PkiException;

/**
 * Quản lý khóa cho KDC Server (AS + TGS).
 *
 * Lưu trữ và cung cấp:
 * - AS Server private key + certificate
 * - TGS Server private key + certificate
 * - Chat Server public key (để mã hóa ST)
 * - CA Root certificate (Trust Anchor)
 *
 * Tất cả khóa được bảo vệ bởi Windows DPAPI (SunMSCAPI).
 */
public final class KdcKeyManager {

    private static final Logger log = LoggerFactory.getLogger(KdcKeyManager.class);

    // Aliases trong Windows KeyStore — admin phải tạo sẵn
    private static final String AS_ALIAS   = "securechat-as";
    private static final String TGS_ALIAS  = "securechat-tgs";
    private static final String CHAT_ALIAS = "securechat-chat";
    private static final String CA_ALIAS   = "securechat-ca";

    private final PrivateKey asPrivateKey;
    private final X509Certificate asCertificate;
    private final PrivateKey tgsPrivateKey;
    private final X509Certificate tgsCertificate;
    private final PublicKey chatServerPublicKey;
    private final X509Certificate caCertificate;
    private final Set<TrustAnchor> trustAnchors;

    public KdcKeyManager() throws KeyStoreException, NoSuchAlgorithmException {
        KeyStore personalStore = KeyStoreManager.loadPersonalStore();

        // Load AS key pair
        validateAlias(personalStore, AS_ALIAS, "AS Server");
        asPrivateKey = loadPrivateKey(personalStore, AS_ALIAS);
        asCertificate = loadCertificate(personalStore, AS_ALIAS);

        // Load TGS key pair
        validateAlias(personalStore, TGS_ALIAS, "TGS Server");
        tgsPrivateKey = loadPrivateKey(personalStore, TGS_ALIAS);
        tgsCertificate = loadCertificate(personalStore, TGS_ALIAS);

        // Load Chat Server certificate (chỉ cần public key)
        validateAlias(personalStore, CHAT_ALIAS, "Chat Server");
        X509Certificate chatCert = loadCertificate(personalStore, CHAT_ALIAS);
        chatServerPublicKey = chatCert.getPublicKey();

        // Load CA Root certificate
        validateAlias(personalStore, CA_ALIAS, "CA Root");
        caCertificate = loadCertificate(personalStore, CA_ALIAS);
        trustAnchors = Collections.singleton(new TrustAnchor(caCertificate, null));

        log.info("KDC Key Manager initialized: AS={}, TGS={}, Chat={}, CA={}",
                asCertificate.getSubjectX500Principal().getName(),
                tgsCertificate.getSubjectX500Principal().getName(),
                chatCert.getSubjectX500Principal().getName(),
                caCertificate.getSubjectX500Principal().getName());
    }

    private void validateAlias(KeyStore ks, String alias, String name)
            throws KeyStoreException {
        if (!ks.containsAlias(alias)) {
            throw new KeyStoreException(
                    name + " key alias '" + alias + "' not found in Windows-MY KeyStore. "
                    + "Please import using: certutil -importpfx " + alias + ".pfx");
        }
    }

    private PrivateKey loadPrivateKey(KeyStore ks, String alias) throws KeyStoreException {
        try {
            PrivateKey key = (PrivateKey) ks.getKey(alias, null);
            if (key == null) {
                throw new KeyStoreException("No private key found for alias: " + alias);
            }
            return key;
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new KeyStoreException("Failed to load private key for: " + alias, e);
        }
    }

    private X509Certificate loadCertificate(KeyStore ks, String alias) throws KeyStoreException {
        java.security.cert.Certificate cert = ks.getCertificate(alias);
        if (cert == null) {
            throw new KeyStoreException("No certificate found for alias: " + alias);
        }
        return (X509Certificate) cert;
    }

    /**
     * Validate certificate chain từ leaf → Root CA.
     * Theo Contrains.md mục 4.3.
     */
    public void validateCertificateChain(X509Certificate clientCert)
            throws ChainValidationException {
        try {
            java.security.cert.CertPath certPath =
                    CertificateFactory.getInstance("X.509")
                            .generateCertPath(Collections.singletonList(clientCert));

            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            PKIXParameters params = new PKIXParameters(trustAnchors);
            params.setRevocationEnabled(false); // OCSP stapling xử lý riêng
            validator.validate(certPath, params);

        } catch (CertPathValidatorException e) {
            throw new ChainValidationException(
                    "Certificate chain validation failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ChainValidationException(
                    "Chain validation error: " + e.getMessage(), e);
        }
    }

    /**
     * Decode X.509 certificate từ DER bytes.
     */
    public X509Certificate decodeCertificate(byte[] derBytes) throws PkiException {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(derBytes));
        } catch (Exception e) {
            throw new PkiException("Failed to decode X.509 certificate", e);
        }
    }

    // Getters
    public PrivateKey getAsPrivateKey()         { return asPrivateKey; }
    public X509Certificate getAsCertificate()   { return asCertificate; }
    public PrivateKey getTgsPrivateKey()         { return tgsPrivateKey; }
    public X509Certificate getTgsCertificate()   { return tgsCertificate; }
    public PublicKey getTgsPublicKey()           { return tgsCertificate.getPublicKey(); }
    public PublicKey getChatServerPublicKey()    { return chatServerPublicKey; }
    public X509Certificate getCaCertificate()   { return caCertificate; }
    public Set<TrustAnchor> getTrustAnchors()   { return trustAnchors; }
}
