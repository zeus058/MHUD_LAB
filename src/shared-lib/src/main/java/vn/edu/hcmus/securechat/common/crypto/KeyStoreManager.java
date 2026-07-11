package vn.edu.hcmus.securechat.common.crypto;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.util.PathUtil;

/**
 * Quản lý KeyStore — Sử dụng PKCS12 (.pfx/.p12) thuần túy làm định dạng lưu trữ khóa,
 * hoàn toàn độc lập với nền tảng hệ điều hành (không phụ thuộc Windows SunMSCAPI).
 */
public final class KeyStoreManager {

    private static final Logger log = LoggerFactory.getLogger(KeyStoreManager.class);

    private KeyStoreManager() {}

    /**
     * DTO đại diện cho cặp khóa và chuỗi chứng chỉ được tải từ KeyStore.
     */
    public record KeyPairEntry(PrivateKey privateKey, X509Certificate certificate, X509Certificate[] certificateChain) {}

    /**
     * Tải cặp khóa và chuỗi chứng chỉ từ file PKCS12 của một định danh (alias) tương ứng.
     */
    public static KeyPairEntry loadKeyPair(String alias) throws KeyStoreException {
        Path pfxPath = findPfxFile(alias);
        if (pfxPath == null) {
            throw new KeyStoreException("No PKCS12 (.pfx/.p12) keystore file found for alias '" + alias +
                    "' under data/keys/ or data/ca/");
        }

        log.info("Loading key pair and certificate from PKCS12 file: {}", pfxPath.toAbsolutePath());
        try {
            KeyStore p12 = KeyStore.getInstance("PKCS12");
            char[] pwd = "changeit".toCharArray(); // Mật khẩu mặc định
            try (FileInputStream fis = new FileInputStream(pfxPath.toFile())) {
                p12.load(fis, pwd);
            }

            String foundAlias = p12.aliases().hasMoreElements() ? p12.aliases().nextElement() : null;
            if (foundAlias == null) {
                throw new KeyStoreException("No key entry found inside PFX: " + pfxPath);
            }

            PrivateKey key = (PrivateKey) p12.getKey(foundAlias, pwd);
            java.security.cert.Certificate cert = p12.getCertificate(foundAlias);
            java.security.cert.Certificate[] chain = p12.getCertificateChain(foundAlias);

            if (key == null || cert == null) {
                throw new KeyStoreException("Failed to extract key/cert from PFX: " + pfxPath);
            }

            X509Certificate[] x509Chain;
            if (chain != null) {
                x509Chain = new X509Certificate[chain.length];
                for (int i = 0; i < chain.length; i++) {
                    x509Chain[i] = (X509Certificate) chain[i];
                }
            } else {
                x509Chain = new X509Certificate[]{(X509Certificate) cert};
            }

            return new KeyPairEntry(key, (X509Certificate) cert, x509Chain);
        } catch (Exception e) {
            throw new KeyStoreException("Failed to load PKCS12 keystore for alias '" + alias + "': " + e.getMessage(), e);
        }
    }

    private static Path findPfxFile(String alias) {
        Path[] candidates = {
                PathUtil.resolve("data/keys/" + alias + ".pfx"),
                PathUtil.resolve("data/keys/" + alias + ".p12"),
                PathUtil.resolve("data/ca/" + alias + ".pfx"),
                PathUtil.resolve("data/ca/" + alias + ".p12")
        };

        for (Path p : candidates) {
            if (Files.exists(p) && Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }
}
