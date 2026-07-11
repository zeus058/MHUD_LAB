package vn.edu.hcmus.securechat.chat.crypto;

import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
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
        // Load Chat Server key pair
        KeyStoreManager.KeyPairEntry chatKeys = KeyStoreManager.loadKeyPair(CHAT_ALIAS);
        chatPrivateKey = chatKeys.privateKey();
        chatCertificate = chatKeys.certificate();
        log.info("Loaded Chat Server cert from PKCS12 file");

        // Load CA Root certificate
        KeyStoreManager.KeyPairEntry caKeys = KeyStoreManager.loadKeyPair(CA_ALIAS);
        caCertificate = caKeys.certificate();
        log.info("Loaded CA Root from PKCS12 file");

        log.info("Chat KeyManager initialized: chat={}, ca={}",
                chatCertificate.getSubjectX500Principal().getName(),
                caCertificate.getSubjectX500Principal().getName());
    }

    public PrivateKey getChatPrivateKey()       { return chatPrivateKey; }
    public X509Certificate getChatCertificate() { return chatCertificate; }
    public PublicKey getChatPublicKey()          { return chatCertificate.getPublicKey(); }
    public X509Certificate getCaCertificate()   { return caCertificate; }
}
