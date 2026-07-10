package vn.edu.hcmus.securechat.chat.crypto;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.crypto.KeyStoreManager;

/**
 * Quản lý khóa cho Chat Server.
 *
 * Load từ Windows DPAPI (SunMSCAPI):
 * - Chat Server private key (để decrypt ST từ TGS)
 * - Chat Server certificate
 * - CA Root certificate
 */
public final class ChatKeyManager {

    private static final Logger log = LoggerFactory.getLogger(ChatKeyManager.class);

    private static final String CHAT_ALIAS = "securechat-chat";
    private static final String CA_ALIAS   = "securechat-ca";

    private final PrivateKey chatPrivateKey;
    private final X509Certificate chatCertificate;
    private final X509Certificate caCertificate;

    public ChatKeyManager() throws KeyStoreException, NoSuchAlgorithmException {
        KeyStore personalStore = KeyStoreManager.loadPersonalStore();

        // Load Chat Server key pair
        if (personalStore.containsAlias(CHAT_ALIAS)) {
            chatPrivateKey = loadPrivateKey(personalStore, CHAT_ALIAS);
            chatCertificate = loadCertificate(personalStore, CHAT_ALIAS);
            log.info("Loaded Chat Server cert from Windows-MY");
        } else {
            final PrivateKey[] k = new PrivateKey[1];
            final X509Certificate[] c = new X509Certificate[1];
            KeyStoreManager.loadFromPfxFallback(CHAT_ALIAS, (priv, cert) -> {
                k[0] = priv;
                c[0] = cert;
            });
            chatPrivateKey = k[0];
            chatCertificate = c[0];
            log.info("Loaded Chat Server cert from fallback file");
        }

        // Load CA Root certificate
        if (personalStore.containsAlias(CA_ALIAS)) {
            caCertificate = loadCertificate(personalStore, CA_ALIAS);
            log.info("Loaded CA Root from Windows-MY");
        } else {
            final X509Certificate[] c = new X509Certificate[1];
            KeyStoreManager.loadFromPfxFallback(CA_ALIAS, (priv, cert) -> {
                c[0] = cert;
            });
            caCertificate = c[0];
            log.info("Loaded CA Root from fallback file");
        }

        log.info("Chat KeyManager initialized: chat={}, ca={}",
                chatCertificate.getSubjectX500Principal().getName(),
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

    public PrivateKey getChatPrivateKey()       { return chatPrivateKey; }
    public X509Certificate getChatCertificate() { return chatCertificate; }
    public PublicKey getChatPublicKey()          { return chatCertificate.getPublicKey(); }
    public X509Certificate getCaCertificate()   { return caCertificate; }
}
