package vn.edu.hcmus.securechat.kdc.crypto;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final String NOTIFICATION_ALIAS = "securechat-notification";
    private static final String CA_ALIAS   = "securechat-ca";

    private final PrivateKey asPrivateKey;
    private final X509Certificate asCertificate;
    private final PrivateKey tgsPrivateKey;
    private final X509Certificate tgsCertificate;
    private final PublicKey chatServerPublicKey;
    private final PublicKey notificationServerPublicKey;
    private final X509Certificate caCertificate;
    private final Set<TrustAnchor> trustAnchors;

    public KdcKeyManager() throws KeyStoreException, NoSuchAlgorithmException {
        KeyStore personalStore = KeyStoreManager.loadPersonalStore();

        // Load AS key pair (try Windows-MY first, then fallback to PFX file)
        if (personalStore.containsAlias(AS_ALIAS)) {
            asPrivateKey = loadPrivateKey(personalStore, AS_ALIAS);
            asCertificate = loadCertificate(personalStore, AS_ALIAS);
            log.info("Loaded AS cert from Windows-MY");
        } else {
            final PrivateKey[] k = new PrivateKey[1];
            final X509Certificate[] c = new X509Certificate[1];
            KeyStoreManager.loadFromPfxFallback(AS_ALIAS, (priv, cert) -> {
                k[0] = priv;
                c[0] = cert;
            });
            asPrivateKey = k[0];
            asCertificate = c[0];
            log.info("Loaded AS cert from fallback file");
        }

        // Load TGS key pair
        if (personalStore.containsAlias(TGS_ALIAS)) {
            tgsPrivateKey = loadPrivateKey(personalStore, TGS_ALIAS);
            tgsCertificate = loadCertificate(personalStore, TGS_ALIAS);
            log.info("Loaded TGS cert from Windows-MY");
        } else {
            final PrivateKey[] k = new PrivateKey[1];
            final X509Certificate[] c = new X509Certificate[1];
            KeyStoreManager.loadFromPfxFallback(TGS_ALIAS, (priv, cert) -> {
                k[0] = priv;
                c[0] = cert;
            });
            tgsPrivateKey = k[0];
            tgsCertificate = c[0];
            log.info("Loaded TGS cert from fallback file");
        }

        // Load Chat Server certificate (chỉ cần public key)
        X509Certificate chatCert;
        if (personalStore.containsAlias(CHAT_ALIAS)) {
            chatCert = loadCertificate(personalStore, CHAT_ALIAS);
            log.info("Loaded Chat cert from Windows-MY");
        } else {
            final X509Certificate[] tmp = new X509Certificate[1];
            KeyStoreManager.loadFromPfxFallback(CHAT_ALIAS, (priv, cert) -> tmp[0] = cert);
            chatCert = tmp[0];
            log.info("Loaded Chat cert from fallback file");
        }
        chatServerPublicKey = chatCert.getPublicKey();

        // Load Notification Server certificate
        X509Certificate notifCert;
        if (personalStore.containsAlias(NOTIFICATION_ALIAS)) {
            notifCert = loadCertificate(personalStore, NOTIFICATION_ALIAS);
            log.info("Loaded Notification cert from Windows-MY");
        } else {
            final X509Certificate[] tmpNotif = new X509Certificate[1];
            try {
                KeyStoreManager.loadFromPfxFallback(NOTIFICATION_ALIAS, (priv, cert) -> tmpNotif[0] = cert);
            } catch (Exception e) {
                log.warn("Could not load notification cert, falling back to chat cert: {}", e.getMessage());
                tmpNotif[0] = chatCert; // Fallback so KDC doesn't crash if cert isn't generated yet
            }
            notifCert = tmpNotif[0];
            log.info("Loaded Notification cert from fallback file (or fallback)");
        }
        notificationServerPublicKey = notifCert.getPublicKey();

        // Load CA Root certificate
        if (personalStore.containsAlias(CA_ALIAS)) {
            caCertificate = loadCertificate(personalStore, CA_ALIAS);
            log.info("Loaded CA cert from Windows-MY");
        } else {
            final X509Certificate[] tmp = new X509Certificate[1];
            KeyStoreManager.loadFromPfxFallback(CA_ALIAS, (priv, cert) -> tmp[0] = cert);
            caCertificate = tmp[0];
            log.info("Loaded CA cert from fallback file");
        }
        trustAnchors = Collections.singleton(new TrustAnchor(caCertificate, null));

        log.info("KDC Key Manager initialized: AS={}, TGS={}, Chat={}, Notif={}, CA={}",
                asCertificate.getSubjectX500Principal().getName(),
                tgsCertificate.getSubjectX500Principal().getName(),
                chatCert.getSubjectX500Principal().getName(),
                notifCert.getSubjectX500Principal().getName(),
                caCertificate.getSubjectX500Principal().getName());
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
    public PublicKey getNotificationServerPublicKey() { return notificationServerPublicKey; }
    public X509Certificate getCaCertificate()   { return caCertificate; }
    public Set<TrustAnchor> getTrustAnchors()   { return trustAnchors; }
}
