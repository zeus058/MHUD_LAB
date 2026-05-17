package vn.edu.hcmus.securechat.client.kerberos;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;
import vn.edu.hcmus.securechat.common.crypto.Pbkdf2KeyDerivation;

/**
 * Cache lưu trữ vé TGT và ST cục bộ (Ticket Caching).
 * Báo cáo mục 3.6: Khôi phục phiên, lưu xuống đĩa cứng bằng khóa PBKDF2.
 */
public class TicketCache {
    private static final Logger log = LoggerFactory.getLogger(TicketCache.class);

    private static File cacheFile(String ticketName) {
        return new File("tickets_" + ticketName.replace("/", "_") + ".cache");
    }

    public static void saveTicket(String ticketName, byte[] ticketData, String password) {
        log.info("Đang mã hóa và lưu trữ vé {} xuống đĩa cứng...", ticketName);
        try {
            // 1. Dẫn xuất khóa từ password
            byte[] salt = new byte[32];
            new SecureRandom().nextBytes(salt);
            byte[] key = Pbkdf2KeyDerivation.deriveDbKey(password.toCharArray(), salt);

            // 2. Mã hóa vé
            byte[] encrypted = AesGcmCipher.encrypt(key, ticketData);

            // 3. Lưu file (Mock structure: salt + encrypted)
            File file = cacheFile(ticketName);
            Files.write(file.toPath(), salt, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.write(file.toPath(), encrypted, StandardOpenOption.APPEND);
            
            log.info("Lưu {} thành công vào {}", ticketName, file.getName());
        } catch (Exception e) {
            log.error("Lỗi khi lưu ticket", e);
        }
    }

    public static byte[] getTicket(String ticketName, String password) {
        log.info("Đang đọc vé {} từ đĩa cứng...", ticketName);
        try {
            File file = cacheFile(ticketName);
            if (!file.exists()) {
                return null;
            }
            byte[] fileData = Files.readAllBytes(file.toPath());
            if (fileData.length <= 32) return null;

            byte[] salt = new byte[32];
            System.arraycopy(fileData, 0, salt, 0, 32);

            byte[] encrypted = new byte[fileData.length - 32];
            System.arraycopy(fileData, 32, encrypted, 0, encrypted.length);

            byte[] key = Pbkdf2KeyDerivation.deriveDbKey(password.toCharArray(), salt);
            return AesGcmCipher.decrypt(key, encrypted);
        } catch (Exception e) {
            log.error("Lỗi khi đọc ticket", e);
        }
        return null;
    }

    public static void clearCache() {
        File dir = new File(".");
        File[] cacheFiles = dir.listFiles((d, name) -> name.startsWith("tickets_") && name.endsWith(".cache"));
        if (cacheFiles != null) {
            for (File f : cacheFiles) {
                f.delete();
            }
        }
        log.info("Đã xóa toàn bộ ticket cache cũ.");
    }
}
