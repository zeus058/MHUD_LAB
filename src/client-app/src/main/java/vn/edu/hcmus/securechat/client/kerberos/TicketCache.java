package vn.edu.hcmus.securechat.client.kerberos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.client.storage.ClientStoragePaths;
import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;
import vn.edu.hcmus.securechat.common.crypto.CryptoConstants;
import vn.edu.hcmus.securechat.common.crypto.Pbkdf2KeyDerivation;

/**
 * Cache vé TGT/ST — định dạng file: [salt 32 bytes][ciphertext AES-GCM] (Contrains.md §3.3).
 */
public class TicketCache {
    private static final Logger log = LoggerFactory.getLogger(TicketCache.class);

    private TicketCache() {
    }

    public static void saveTicket(String username, String ticketName, byte[] ticketData, char[] password) {
        log.info("Mã hóa và lưu vé {} cho user={}", ticketName, username);
        byte[] key = null;
        try {
            ClientStoragePaths.ensureUserDir(username);
            byte[] salt = new byte[CryptoConstants.PBKDF2_SALT_SIZE];
            new SecureRandom().nextBytes(salt);
            key = Pbkdf2KeyDerivation.deriveDbKey(password, salt);
            byte[] encrypted = AesGcmCipher.encrypt(key, ticketData);

            Path file = ClientStoragePaths.ticketCacheFile(username, ticketName);
            Files.write(file, salt, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.write(file, encrypted, StandardOpenOption.APPEND);
            log.info("Đã lưu {} → {}", ticketName, file);
        } catch (Exception e) {
            log.error("Lỗi khi lưu ticket", e);
        } finally {
            if (key != null) {
                Arrays.fill(key, (byte) 0);
            }
        }
    }

    public static byte[] getTicket(String username, String ticketName, char[] password) {
        log.info("Đọc vé {} cho user={}", ticketName, username);
        byte[] key = null;
        try {
            Path file = ClientStoragePaths.ticketCacheFile(username, ticketName);
            if (!Files.isRegularFile(file)) {
                byte[] legacy = tryLoadLegacyTicket(ticketName, password);
                if (legacy != null) {
                    saveTicket(username, ticketName, legacy, password);
                }
                return legacy;
            }
            byte[] fileData = Files.readAllBytes(file);
            if (fileData.length <= CryptoConstants.PBKDF2_SALT_SIZE) {
                return null;
            }

            byte[] salt = Arrays.copyOfRange(fileData, 0, CryptoConstants.PBKDF2_SALT_SIZE);
            byte[] encrypted = Arrays.copyOfRange(fileData, CryptoConstants.PBKDF2_SALT_SIZE, fileData.length);

            key = Pbkdf2KeyDerivation.deriveDbKey(password, salt);
            return AesGcmCipher.decrypt(key, encrypted);
        } catch (Exception e) {
            log.error("Lỗi khi đọc ticket", e);
        } finally {
            if (key != null) {
                Arrays.fill(key, (byte) 0);
            }
        }
        return null;
    }

    public static void clearCache(String username) {
        try {
            Path userDir = ClientStoragePaths.userDir(username);
            if (!Files.isDirectory(userDir)) {
                return;
            }
            try (Stream<Path> files = Files.list(userDir)) {
                files.filter(p -> p.getFileName().toString().startsWith("tickets_")
                        && p.getFileName().toString().endsWith(".cache"))
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                log.warn("Không xóa được {}", p, e);
                            }
                        });
            }
            log.info("Đã xóa ticket cache của user={}", username);
        } catch (IOException e) {
            log.error("Lỗi xóa ticket cache", e);
        }
    }

    private static byte[] tryLoadLegacyTicket(String ticketName, char[] password) {
        Path legacy = Path.of("tickets_" + ticketName.replace("/", "_") + ".cache");
        if (!Files.isRegularFile(legacy)) {
            return null;
        }
        byte[] key = null;
        try {
            byte[] fileData = Files.readAllBytes(legacy);
            if (fileData.length <= CryptoConstants.PBKDF2_SALT_SIZE) {
                return null;
            }
            byte[] salt = Arrays.copyOfRange(fileData, 0, CryptoConstants.PBKDF2_SALT_SIZE);
            byte[] encrypted = Arrays.copyOfRange(fileData, CryptoConstants.PBKDF2_SALT_SIZE, fileData.length);
            key = Pbkdf2KeyDerivation.deriveDbKey(password, salt);
            log.info("Đọc vé legacy từ {}", legacy);
            return AesGcmCipher.decrypt(key, encrypted);
        } catch (Exception e) {
            log.warn("Không đọc được vé legacy {}", legacy, e);
            return null;
        } finally {
            if (key != null) {
                Arrays.fill(key, (byte) 0);
            }
        }
    }
}
