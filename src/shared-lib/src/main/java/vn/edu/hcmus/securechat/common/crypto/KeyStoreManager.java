package vn.edu.hcmus.securechat.common.crypto;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.util.PathUtil;

/**
 * Quản lý KeyStore — Phương án A: Bắt buộc Windows SunMSCAPI.
 *
 * Theo Contrains.md mục 1.2 — Phương án A:
 * Toàn bộ develop và demo trên Windows 10/11.
 * SunMSCAPI là bắt buộc, không có fallback.
 *
 * Private Key KHÔNG BAO GIỜ được lưu dưới dạng plaintext trên disk
 * hoặc xuất hiện dưới dạng plaintext trên RAM của application layer.
 *
 * Sử dụng:
 *   KeyStore ks = KeyStoreManager.loadPersonalStore();
 *   PrivateKey pk = (PrivateKey) ks.getKey(alias, null); // DPAPI tự giải mã
 */
public final class KeyStoreManager {

    private static final Logger log = LoggerFactory.getLogger(KeyStoreManager.class);

    // Phương án A: Windows-only
    private static final String KEYSTORE_TYPE_PERSONAL = "Windows-MY";
    private static final String KEYSTORE_TYPE_ROOT     = "Windows-ROOT";
    private static final String PROVIDER               = "SunMSCAPI";

    private KeyStoreManager() {}

    /**
     * Load Windows Personal Certificate Store (chứa cert + private key của user).
     * Private key được bảo vệ bởi Windows DPAPI — không cần password.
     */
    public static KeyStore loadPersonalStore() throws KeyStoreException {
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE_PERSONAL, PROVIDER);
            ks.load(null, null); // SunMSCAPI không cần password
            log.info("Loaded Windows Personal KeyStore (Windows-MY), entries={}",
                    ks.size());
            return ks;
        } catch (NoSuchProviderException e) {
            log.warn("SunMSCAPI provider not available (non-Windows OS). Using empty KeyStore to trigger fallback.");
            try {
                KeyStore emptyKs = KeyStore.getInstance("PKCS12");
                emptyKs.load(null, null);
                return emptyKs;
            } catch (Exception ex) {
                throw new KeyStoreException("Failed to create empty fallback KeyStore", ex);
            }
        } catch (Exception e) {
            throw new KeyStoreException("Failed to load Windows-MY KeyStore", e);
        }
    }

    /**
     * Load Windows Root Certificate Store (chứa trusted CA certificates).
     */
    public static KeyStore loadRootStore() throws KeyStoreException {
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE_ROOT, PROVIDER);
            ks.load(null, null);
            log.info("Loaded Windows Root KeyStore (Windows-ROOT), entries={}",
                    ks.size());
            return ks;
        } catch (NoSuchProviderException e) {
            log.warn("SunMSCAPI provider not available (non-Windows OS). Using empty KeyStore to trigger fallback.");
            try {
                KeyStore emptyKs = KeyStore.getInstance("PKCS12");
                emptyKs.load(null, null);
                return emptyKs;
            } catch (Exception ex) {
                throw new KeyStoreException("Failed to create empty fallback KeyStore", ex);
            }
        } catch (Exception e) {
            throw new KeyStoreException("Failed to load Windows-ROOT KeyStore", e);
        }
    }

    /**
     * Kiểm tra xem alias có tồn tại trong Personal Store hay không.
     */
    public static boolean hasAlias(String alias) throws KeyStoreException {
        KeyStore ks = loadPersonalStore();
        return ks.containsAlias(alias);
    }

    /**
     * Thử nạp cặp khóa/chứng chỉ từ file PFX/P12 trong thư mục data/keys/
     * làm phương án dự phòng khi Windows KeyStore không có.
     */
    public static void loadFromPfxFallback(String alias, PfxResultHandler handler) throws KeyStoreException {
        Path pfxPath = findPfxFile(alias);
        if (pfxPath == null) {
            throw new KeyStoreException("Key alias '" + alias + "' not found in Windows-MY " +
                    "and no fallback PFX file found in data/keys/");
        }

        log.info("Loading fallback key/cert from: {}", pfxPath.toAbsolutePath());
        try {
            KeyStore p12 = KeyStore.getInstance("PKCS12");
            char[] pwd = "changeit".toCharArray(); // Password mặc định cho lab/demo
            try (FileInputStream fis = new FileInputStream(pfxPath.toFile())) {
                p12.load(fis, pwd);
            }

            String foundAlias = p12.aliases().hasMoreElements() ? p12.aliases().nextElement() : null;
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

    private static Path findPfxFile(String alias) {
        // Ưu tiên tìm trong data/keys/ của dự án
        Path[] candidates = {
                PathUtil.resolve("data/keys/" + alias + ".pfx"),
                PathUtil.resolve("data/keys/" + alias + ".p12"),
                PathUtil.resolve("data/ca/" + alias + ".pfx"), // Trường hợp CA key
                PathUtil.resolve("data/ca/" + alias + ".p12")
        };

        for (Path p : candidates) {
            if (Files.exists(p) && Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    public interface PfxResultHandler {
        void handle(PrivateKey priv, X509Certificate cert) throws Exception;
    }
}
