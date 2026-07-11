package vn.edu.hcmus.securechat.kdc.crypto;

import java.io.ByteArrayInputStream;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
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
        // Load AS key pair
        KeyStoreManager.KeyPairEntry asKeys = KeyStoreManager.loadKeyPair(AS_ALIAS);
        asPrivateKey = asKeys.privateKey();
        asCertificate = asKeys.certificate();
        log.info("Loaded AS cert from PKCS12 file");

        // Load TGS key pair
        KeyStoreManager.KeyPairEntry tgsKeys = KeyStoreManager.loadKeyPair(TGS_ALIAS);
        tgsPrivateKey = tgsKeys.privateKey();
        tgsCertificate = tgsKeys.certificate();
        log.info("Loaded TGS cert from PKCS12 file");

        // Load Chat Server certificate (only public key is needed)
        KeyStoreManager.KeyPairEntry chatKeys = KeyStoreManager.loadKeyPair(CHAT_ALIAS);
        chatServerPublicKey = chatKeys.certificate().getPublicKey();
        log.info("Loaded Chat cert from PKCS12 file");

        // Load Notification Server certificate
        X509Certificate notifCert;
        try {
            KeyStoreManager.KeyPairEntry notifKeys = KeyStoreManager.loadKeyPair(NOTIFICATION_ALIAS);
            notifCert = notifKeys.certificate();
            log.info("Loaded Notification cert from PKCS12 file");
        } catch (Exception e) {
            log.warn("Could not load notification cert, falling back to chat cert: {}", e.getMessage());
            notifCert = chatKeys.certificate(); // Fallback so KDC doesn't crash if cert isn't generated yet
        }
        notificationServerPublicKey = notifCert.getPublicKey();

        // Load CA Root certificate
        KeyStoreManager.KeyPairEntry caKeys = KeyStoreManager.loadKeyPair(CA_ALIAS);
        caCertificate = caKeys.certificate();
        log.info("Loaded CA cert from PKCS12 file");
        
        trustAnchors = Collections.singleton(new java.security.cert.TrustAnchor(caCertificate, null));

        log.info("KDC Key Manager initialized: AS={}, TGS={}, Chat={}, Notif={}, CA={}",
                asCertificate.getSubjectX500Principal().getName(),
                tgsCertificate.getSubjectX500Principal().getName(),
                chatKeys.certificate().getSubjectX500Principal().getName(),
                notifCert.getSubjectX500Principal().getName(),
                caCertificate.getSubjectX500Principal().getName());
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
