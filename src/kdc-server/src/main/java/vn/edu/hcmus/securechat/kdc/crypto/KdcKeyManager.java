package vn.edu.hcmus.securechat.kdc.crypto;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

        // Load AS key pair (try Windows-MY first, then fallback to PFX file)
        if (personalStore.containsAlias(AS_ALIAS)) {
            asPrivateKey = loadPrivateKey(personalStore, AS_ALIAS);
            asCertificate = loadCertificate(personalStore, AS_ALIAS);
        } else {
            loadFromPfxFallback(AS_ALIAS, (priv, cert) -> {
                asPrivateKey = priv;
                asCertificate = cert;
            });
        }

        // Load TGS key pair
        if (personalStore.containsAlias(TGS_ALIAS)) {
            tgsPrivateKey = loadPrivateKey(personalStore, TGS_ALIAS);
            tgsCertificate = loadCertificate(personalStore, TGS_ALIAS);
        } else {
            loadFromPfxFallback(TGS_ALIAS, (priv, cert) -> {
                tgsPrivateKey = priv;
                tgsCertificate = cert;
            });
        }

        // Load Chat Server certificate (chỉ cần public key)
        X509Certificate chatCert;
        if (personalStore.containsAlias(CHAT_ALIAS)) {
            chatCert = loadCertificate(personalStore, CHAT_ALIAS);
        } else {
            final X509Certificate[] tmp = new X509Certificate[1];
            loadFromPfxFallback(CHAT_ALIAS, (priv, cert) -> tmp[0] = cert);
            chatCert = tmp[0];
        }
        chatServerPublicKey = chatCert.getPublicKey();

        // Load CA Root certificate
        if (personalStore.containsAlias(CA_ALIAS)) {
            caCertificate = loadCertificate(personalStore, CA_ALIAS);
        } else {
            final X509Certificate[] tmp = new X509Certificate[1];
            loadFromPfxFallback(CA_ALIAS, (priv, cert) -> tmp[0] = cert);
            caCertificate = tmp[0];
        }
        trustAnchors = Collections.singleton(new TrustAnchor(caCertificate, null));

        log.info("KDC Key Manager initialized: AS={}, TGS={}, Chat={}, CA={}",
                asCertificate.getSubjectX500Principal().getName(),
                tgsCertificate.getSubjectX500Principal().getName(),
                chatCert.getSubjectX500Principal().getName(),
                caCertificate.getSubjectX500Principal().getName());
    }

    /**
     * Attempt to find and load a PKCS#12 (PFX/.p12) file for the given alias and
     * supply the private key and certificate to the handler. The method will
     * search common locations and use env var `KDC_PFX_PASSWORD` as password when
     * loading.
     */
    private void loadFromPfxFallback(String alias, PfxResultHandler handler) throws KeyStoreException {
        try {
            Path pfxPath = findPfxFile(alias);
            if (pfxPath == null) {
                throw new KeyStoreException(
                        "Required alias '" + alias + "' not found in Windows-MY KeyStore and no PFX file located. Please provide "
                                + alias + ".pfx in kdc-server/config or set up the certificate in Windows-MY.");
            }

            String pass = System.getenv("KDC_PFX_PASSWORD");
            char[] pwd = pass != null ? pass.toCharArray() : null;

            KeyStore p12 = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(pfxPath.toFile())) {
                p12.load(fis, pwd);
            }

            // find first key entry
            String foundAlias = null;
            for (java.util.Enumeration<String> e = p12.aliases(); e.hasMoreElements();) {
                String a = e.nextElement();
                if (p12.isKeyEntry(a)) {
                    foundAlias = a;
                    break;
                }
            }

            if (foundAlias == null) {
                throw new KeyStoreException("No key entry found inside PFX: " + pfxPath);
            }

            PrivateKey key = (PrivateKey) p12.getKey(foundAlias, pwd);
            java.security.cert.Certificate cert = p12.getCertificate(foundAlias);
            if (key == null || cert == null) {
                throw new KeyStoreException("Failed to extract key/cert from PFX: " + pfxPath);
            }

            handler.handle(key, (X509Certificate) cert);

        } catch (Exception e) {
            throw new KeyStoreException("Failed to load PFX fallback for alias '" + alias + "': " + e.getMessage(), e);
        }
    }

    private Path findPfxFile(String alias) {
        // Search locations: env KDC_PFX_DIR, src/kdc-server/config, kdc-server/config, cwd
        String dirEnv = System.getenv("KDC_PFX_DIR");
        String[] candidates = {
                dirEnv != null ? Paths.get(dirEnv, alias + ".pfx").toString() : null,
                dirEnv != null ? Paths.get(dirEnv, alias + ".p12").toString() : null,
                Paths.get("src", "kdc-server", "config", alias + ".pfx").toString(),
                Paths.get("src", "kdc-server", "config", alias + ".p12").toString(),
                Paths.get("kdc-server", "config", alias + ".pfx").toString(),
                Paths.get("kdc-server", "config", alias + ".p12").toString(),
                Paths.get(alias + ".pfx").toString(),
                Paths.get(alias + ".p12").toString()
        };

        for (String p : candidates) {
            if (p == null) continue;
            Path path = Paths.get(p);
            if (Files.exists(path) && Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    private interface PfxResultHandler {
        void handle(PrivateKey priv, X509Certificate cert) throws Exception;
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
