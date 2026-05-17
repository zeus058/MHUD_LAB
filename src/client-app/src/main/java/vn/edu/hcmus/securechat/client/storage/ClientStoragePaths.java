package vn.edu.hcmus.securechat.client.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Đường dẫn lưu trữ cục bộ trên client — thống nhất dưới {@code data/client/}.
 * Mỗi người dùng có thư mục riêng để tránh ghi đè khi test nhiều tài khoản trên một máy.
 */
public final class ClientStoragePaths {

    public static final String CLIENT_DATA_ROOT = "data/client";

    private ClientStoragePaths() {
    }

    public static Path clientRoot() {
        return Path.of(CLIENT_DATA_ROOT);
    }

    public static Path userDir(String username) {
        return clientRoot().resolve(sanitizeUsername(username));
    }

    /**
     * File {@code chat.db}: bytes 0–31 chứa salt PBKDF2 (Contrains.md §3.3).
     */
    public static Path chatDbSaltFile(String username) {
        return userDir(username).resolve("chat.db");
    }

    /** SQLite lưu tin nhắn (nội dung cột {@code encrypted_content} đã mã hóa AES-GCM). */
    public static Path messagesSqliteFile(String username) {
        return userDir(username).resolve("messages.sqlite");
    }

    public static Path keystoreFile(String username) {
        return userDir(username).resolve("keystore.p12");
    }

    /**
     * Vé Kerberos mã hóa: [salt 32 bytes][ciphertext] (Contrains.md §3.3).
     */
    public static Path ticketCacheFile(String username, String ticketName) {
        String safe = ticketName.replace("/", "_").replace("\\", "_");
        return userDir(username).resolve("tickets_" + safe + ".cache");
    }

    public static void ensureUserDir(String username) throws IOException {
        Files.createDirectories(userDir(username));
    }

    public static boolean keystoreExists(String username) {
        return Files.isRegularFile(keystoreFile(username));
    }

    private static String sanitizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        return username.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
