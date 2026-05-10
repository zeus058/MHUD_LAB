package vn.edu.hcmus.securechat.common.crypto;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchProviderException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            throw new KeyStoreException(
                    "SunMSCAPI provider not available — "
                    + "this application requires Windows 10/11. "
                    + "Please run on a Windows machine.", e);
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
            throw new KeyStoreException(
                    "SunMSCAPI provider not available — Windows required.", e);
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
}
