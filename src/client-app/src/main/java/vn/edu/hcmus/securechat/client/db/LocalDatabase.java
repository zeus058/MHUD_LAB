package vn.edu.hcmus.securechat.client.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;
import vn.edu.hcmus.securechat.common.crypto.Pbkdf2KeyDerivation;

/**
 * Quản lý CSDL nội bộ SQLite.
 * Theo báo cáo 3.4 & 5.3: Nội dung tin nhắn phải được mã hóa tĩnh bằng khóa dẫn xuất PBKDF2.
 */
public class LocalDatabase {
    private static final Logger log = LoggerFactory.getLogger(LocalDatabase.class);
    private static final String DB_URL = "jdbc:sqlite:chat_history.db";

    private byte[] dbKey; // Khóa AES dẫn xuất từ password người dùng

    public LocalDatabase() {
        initDb();
    }

    /**
     * Khởi tạo CSDL, gọi hàm này khi người dùng đăng nhập thành công.
     * Tạo khóa dẫn xuất PBKDF2 từ password đăng nhập để mã hóa db.
     */
    public void unlockDatabase(String password) {
        log.info("Dẫn xuất khóa PBKDF2 từ password để mở khóa CSDL nội bộ...");
        try {
            // Trong thực tế salt này phải được load từ config. Giả lập salt cố định.
            byte[] salt = "SecureChatDbSalt".getBytes();
            this.dbKey = Pbkdf2KeyDerivation.deriveDbKey(password.toCharArray(), salt);
            log.info("Mở khóa CSDL thành công.");
        } catch (Exception e) {
            log.error("Lỗi khi mở khóa DB", e);
        }
    }

    private void initDb() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            
            String sql = "CREATE TABLE IF NOT EXISTS messages (" +
                         "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                         "sender TEXT NOT NULL," +
                         "encrypted_content TEXT NOT NULL," + // Nội dung bị mã hóa
                         "timestamp INTEGER NOT NULL" +
                         ");";
            stmt.execute(sql);
            log.info("Khởi tạo bảng messages trong SQLite thành công.");
            
        } catch (Exception e) {
            log.error("Lỗi khi khởi tạo SQLite", e);
        }
    }

    public void saveMessage(String sender, String content) {
        if (dbKey == null) {
            log.error("CSDL chưa được mở khóa (Missing dbKey)");
            return;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO messages(sender, encrypted_content, timestamp) VALUES(?, ?, ?)")) {
            
            // Mã hóa content bằng AES-GCM
            byte[] cipherText = AesGcmCipher.encrypt(dbKey, content.getBytes());
            String encryptedBase64 = Base64.getEncoder().encodeToString(cipherText);
            
            pstmt.setString(1, sender);
            pstmt.setString(2, encryptedBase64);
            pstmt.setLong(3, System.currentTimeMillis());
            pstmt.executeUpdate();
            
            log.info("Lưu tin nhắn mã hóa thành công vào CSDL.");
        } catch (Exception e) {
            log.error("Lỗi lưu tin nhắn", e);
        }
    }

    public void mockReadMessages() {
        if (dbKey == null) return;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT sender, encrypted_content FROM messages LIMIT 1")) {
            
            while (rs.next()) {
                String sender = rs.getString("sender");
                String encryptedBase64 = rs.getString("encrypted_content");
                byte[] cipherText = Base64.getDecoder().decode(encryptedBase64);
                byte[] plainText = AesGcmCipher.decrypt(dbKey, cipherText);
                log.info("Đọc lịch sử: {} -> {}", sender, new String(plainText));
            }
        } catch (Exception e) {
            log.error("Lỗi đọc tin nhắn", e);
        }
    }
}
