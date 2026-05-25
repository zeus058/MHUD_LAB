package vn.edu.hcmus.securechat.client.kerberos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.client.storage.ClientStoragePaths;
import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;
import vn.edu.hcmus.securechat.common.crypto.Argon2idKeyDerivation;
import vn.edu.hcmus.securechat.common.crypto.CryptoConstants;
import vn.edu.hcmus.securechat.common.crypto.Pbkdf2KeyDerivation;

/**
 * Cache vé TGT/ST — v2 format: ["SC2A"][Argon2id salt 32 bytes][AES-GCM ciphertext].
 * Legacy PBKDF2 cache vẫn được đọc để di trú dữ liệu cũ.
 */
public class TicketCache {
    private static final Logger log = LoggerFactory.getLogger(TicketCache.class);
    private static final byte[] MAGIC_ARGON2ID = new byte[] {'S', 'C', '2', 'A'};

    private TicketCache() {
    }

    public static void saveTicket(String username, String ticketName, byte[] ticketData, char[] password)
            throws IOException, vn.edu.hcmus.securechat.common.exception.CryptoException {
        log.info("Mã hóa và lưu vé {} cho user={}", ticketName, username);
        byte[] key = null;
        byte[] salt = null;
        byte[] encrypted = null;
        byte[] output = null;
        try {
            ClientStoragePaths.ensureUserDir(username);
            salt = new byte[CryptoConstants.ARGON2ID_SALT_SIZE];
            new SecureRandom().nextBytes(salt);
            key = Argon2idKeyDerivation.deriveDbKey(password, salt);
            encrypted = AesGcmCipher.encrypt(key, ticketData);

            Path file = ClientStoragePaths.ticketCacheFile(username, ticketName);
            output = new byte[MAGIC_ARGON2ID.length + salt.length + encrypted.length];
            System.arraycopy(MAGIC_ARGON2ID, 0, output, 0, MAGIC_ARGON2ID.length);
            System.arraycopy(salt, 0, output, MAGIC_ARGON2ID.length, salt.length);
            System.arraycopy(encrypted, 0, output, MAGIC_ARGON2ID.length + salt.length, encrypted.length);
            Files.write(file, output);
            log.info("Đã lưu {} → {}", ticketName, file);
        } finally {
            if (key != null) {
                Arrays.fill(key, (byte) 0);
            }
            if (salt != null) {
                Arrays.fill(salt, (byte) 0);
            }
            if (encrypted != null) {
                Arrays.fill(encrypted, (byte) 0);
            }
            if (output != null) {
                Arrays.fill(output, (byte) 0);
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

            int offset;
            byte[] salt;
            byte[] encrypted;
            if (hasMagic(fileData)) {
                offset = MAGIC_ARGON2ID.length;
                salt = Arrays.copyOfRange(fileData, offset, offset + CryptoConstants.ARGON2ID_SALT_SIZE);
                encrypted = Arrays.copyOfRange(fileData, offset + CryptoConstants.ARGON2ID_SALT_SIZE, fileData.length);
                key = Argon2idKeyDerivation.deriveDbKey(password, salt);
            } else {
                salt = Arrays.copyOfRange(fileData, 0, CryptoConstants.PBKDF2_SALT_SIZE);
                encrypted = Arrays.copyOfRange(fileData, CryptoConstants.PBKDF2_SALT_SIZE, fileData.length);
                key = Pbkdf2KeyDerivation.deriveDbKey(password, salt);
            }
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

    private static boolean hasMagic(byte[] fileData) {
        if (fileData.length < MAGIC_ARGON2ID.length + CryptoConstants.ARGON2ID_SALT_SIZE) {
            return false;
        }
        for (int i = 0; i < MAGIC_ARGON2ID.length; i++) {
            if (fileData[i] != MAGIC_ARGON2ID[i]) {
                return false;
            }
        }
        return true;
    }
}
