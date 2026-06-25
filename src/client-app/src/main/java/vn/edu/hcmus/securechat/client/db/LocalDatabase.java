package vn.edu.hcmus.securechat.client.db;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.client.storage.ClientStoragePaths;
import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;
import vn.edu.hcmus.securechat.common.crypto.Argon2idKeyDerivation;
import vn.edu.hcmus.securechat.common.crypto.CryptoConstants;
import vn.edu.hcmus.securechat.common.exception.KeyDerivationException;

/**
 * CSDL SQLite cục bộ — tin nhắn mã hóa tĩnh bằng khóa Argon2id (BaoCao_NangCap.md v2.0).
 */
public class LocalDatabase {
    private static final Logger log = LoggerFactory.getLogger(LocalDatabase.class);

    private final String username;
    private final String jdbcUrl;
    private final Path saltDbFile;
    private byte[] dbKey;

    public LocalDatabase(String username) {
        this.username = username;
        Path sqliteFile = ClientStoragePaths.messagesSqliteFile(username);
        this.saltDbFile = ClientStoragePaths.chatDbSaltFile(username);
        this.jdbcUrl = "jdbc:sqlite:" + sqliteFile.toString().replace('\\', '/');
        initDb();
    }

    /**
     * Dẫn xuất khóa Argon2id từ password; salt lưu riêng theo user.
     */
    public void unlockDatabase(char[] password) throws KeyDerivationException {
        log.info("Dẫn xuất khóa Argon2id cho user={}", username);
        try {
            ClientStoragePaths.ensureUserDir(username);
            byte[] salt = loadOrCreateSalt();
            this.dbKey = Argon2idKeyDerivation.deriveDbKey(password, salt);
            log.info("Mở khóa CSDL thành công: {}", ClientStoragePaths.messagesSqliteFile(username));
        } catch (KeyDerivationException e) {
            throw e;
        } catch (Exception e) {
            throw new KeyDerivationException("Không mở khóa được CSDL cục bộ", e);
        }
    }

    public boolean isUnlocked() {
        return dbKey != null;
    }

    private void initDb() {
        try {
            ClientStoragePaths.ensureUserDir(username);
            migrateLegacyStorageIfNeeded();
            try (Connection conn = DriverManager.getConnection(jdbcUrl);
                 Statement stmt = conn.createStatement()) {
                String sql = "CREATE TABLE IF NOT EXISTS messages ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "owner TEXT,"
                        + "peer TEXT,"
                        + "sender TEXT NOT NULL,"
                        + "encrypted_content TEXT NOT NULL,"
                        + "timestamp INTEGER NOT NULL"
                        + ");";
                stmt.execute(sql);
                
                String sqlGroup = "CREATE TABLE IF NOT EXISTS group_metadata ("
                        + "group_id TEXT PRIMARY KEY,"
                        + "group_name TEXT NOT NULL,"
                        + "members TEXT NOT NULL"
                        + ");";
                stmt.execute(sqlGroup);

                try {
                    stmt.execute("ALTER TABLE messages ADD COLUMN owner TEXT");
                } catch (Exception ignored) {
                    // column exists
                }
                try {
                    stmt.execute("ALTER TABLE messages ADD COLUMN peer TEXT");
                } catch (Exception ignored) {
                    // column exists
                }
                log.info("Khởi tạo SQLite messages cho user={}", username);
            }
        } catch (Exception e) {
            log.error("Lỗi khi khởi tạo SQLite", e);
        }
    }

    public record GroupInfoRecord(String groupId, String groupName, String members) {}

    public void saveGroup(String groupId, String groupName, String members) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT OR REPLACE INTO group_metadata(group_id, group_name, members) VALUES(?, ?, ?)")) {
            pstmt.setString(1, groupId);
            pstmt.setString(2, groupName);
            pstmt.setString(3, members);
            pstmt.executeUpdate();
            log.info("Saved group metadata: groupId={}, name={}", groupId, groupName);
        } catch (Exception e) {
            log.error("Lỗi lưu metadata nhóm", e);
        }
    }

    public void deleteGroup(String groupId) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            // Delete group metadata
            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM group_metadata WHERE group_id = ?")) {
                pstmt.setString(1, groupId);
                pstmt.executeUpdate();
            }
            // Delete group messages
            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM messages WHERE owner = ? AND peer = ?")) {
                pstmt.setString(1, username);
                pstmt.setString(2, groupId);
                pstmt.executeUpdate();
            }
            log.info("Deleted group from local DB: groupId={}", groupId);
        } catch (Exception e) {
            log.error("Lỗi xóa nhóm khỏi DB", e);
        }
    }

    public java.util.List<GroupInfoRecord> loadGroups() {
        java.util.List<GroupInfoRecord> list = new java.util.ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT group_id, group_name, members FROM group_metadata")) {
            while (rs.next()) {
                list.add(new GroupInfoRecord(
                        rs.getString("group_id"),
                        rs.getString("group_name"),
                        rs.getString("members")
                ));
            }
        } catch (Exception e) {
            log.error("Lỗi tải danh sách nhóm từ DB", e);
        }
        return list;
    }

    public void saveMessage(String owner, String peer, String sender, String content, long timestamp) {
        if (dbKey == null) {
            log.warn("Bỏ qua lưu tin nhắn — CSDL chưa được mở khóa");
            return;
        }

        byte[] plainText = content.getBytes(StandardCharsets.UTF_8);
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO messages(owner, peer, sender, encrypted_content, timestamp) "
                             + "VALUES(?, ?, ?, ?, ?)")) {
            byte[] cipherText = AesGcmCipher.encrypt(dbKey, plainText);
            String encryptedBase64 = Base64.getEncoder().encodeToString(cipherText);
            pstmt.setString(1, owner);
            pstmt.setString(2, peer);
            pstmt.setString(3, sender);
            pstmt.setString(4, encryptedBase64);
            pstmt.setLong(5, timestamp);
            pstmt.executeUpdate();
        } catch (Exception e) {
            log.error("Lỗi lưu tin nhắn", e);
        } finally {
            Arrays.fill(plainText, (byte) 0);
        }
    }

    public java.util.List<vn.edu.hcmus.securechat.common.protocol.dto.ChatMessage> loadMessages(
            String owner, String peer) {
        java.util.List<vn.edu.hcmus.securechat.common.protocol.dto.ChatMessage> list = new java.util.ArrayList<>();
        if (dbKey == null) {
            return list;
        }
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT sender, encrypted_content, timestamp FROM messages "
                             + "WHERE owner = ? AND peer = ? ORDER BY timestamp ASC")) {
            pstmt.setString(1, owner);
            pstmt.setString(2, peer);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String sender = rs.getString("sender");
                    String encryptedBase64 = rs.getString("encrypted_content");
                    long timestamp = rs.getLong("timestamp");
                    byte[] cipherText = Base64.getDecoder().decode(encryptedBase64);
                    byte[] plainText = AesGcmCipher.decrypt(dbKey, cipherText);
                    try {
                        String content = new String(plainText, StandardCharsets.UTF_8);
                        list.add(new vn.edu.hcmus.securechat.common.protocol.dto.ChatMessage(
                                sender, content, timestamp));
                    } finally {
                        Arrays.fill(plainText, (byte) 0);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Lỗi đọc tin nhắn", e);
        }
        return list;
    }

    /**
     * Salt Argon2id: 32 bytes riêng cho từng user.
     */
    private byte[] loadOrCreateSalt() throws IOException {
        if (Files.exists(saltDbFile) && Files.size(saltDbFile) >= CryptoConstants.ARGON2ID_SALT_SIZE) {
            byte[] fileBytes = Files.readAllBytes(saltDbFile);
            if (fileBytes.length >= CryptoConstants.ARGON2ID_SALT_SIZE) {
                return Arrays.copyOf(fileBytes, CryptoConstants.ARGON2ID_SALT_SIZE);
            }
        }

        byte[] legacy = tryLoadLegacySalt();
        if (legacy != null) {
            Files.write(saltDbFile, legacy);
            return legacy;
        }

        byte[] salt = new byte[CryptoConstants.ARGON2ID_SALT_SIZE];
        new SecureRandom().nextBytes(salt);
        Files.write(saltDbFile, salt);
        return salt;
    }

    private byte[] tryLoadLegacySalt() {
        Path legacySalt = Path.of("data", "client", "chat_history.salt");
        try {
            if (Files.exists(legacySalt)) {
                byte[] salt = Files.readAllBytes(legacySalt);
                if (salt.length == CryptoConstants.ARGON2ID_SALT_SIZE) {
                    log.info("Di chuyển salt cũ sang {}", saltDbFile);
                    return salt;
                }
            }
        } catch (IOException e) {
            log.warn("Không đọc được salt cũ", e);
        }
        return null;
    }

    private void migrateLegacyStorageIfNeeded() throws IOException {
        Path target = ClientStoragePaths.messagesSqliteFile(username);
        if (Files.exists(target)) {
            return;
        }
        Path legacyInCwd = Path.of("chat_history.db");
        Path legacyInData = Path.of("data", "client", "chat_history.db");
        Path source = Files.exists(legacyInCwd) ? legacyInCwd
                : (Files.exists(legacyInData) ? legacyInData : null);
        if (source != null) {
            Files.copy(source, target);
            log.info("Sao chép CSDL cũ {} → {}", source, target);
        }
    }
}
