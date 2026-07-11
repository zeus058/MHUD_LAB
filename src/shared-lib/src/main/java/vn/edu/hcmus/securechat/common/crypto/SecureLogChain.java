package vn.edu.hcmus.securechat.common.crypto;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.util.PathUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * SecureLogChain — Hệ thống ghi nhật ký kiểm toán bền vững và chống giả mạo (Tamper-Evident).
 * Mỗi dòng log chứa hash của dòng trước đó (Hash Chain) và được ký/xác thực bằng HMAC-SHA256
 * với khóa bí mật riêng của log.
 */
public class SecureLogChain {
    private static final Logger log = LoggerFactory.getLogger(SecureLogChain.class);
    private static final String LOG_FILE_PATH = PathUtil.resolve("data/secure_audit.log").toString();
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    
    // Khóa HMAC riêng để ký log (bảo mật)
    private static final byte[] LOG_SIGNING_KEY = "SecureChatLogSecretKey2026!@#".getBytes(StandardCharsets.UTF_8);

    private static String lastEntryHash = "0000000000000000000000000000000000000000000000000000000000000000"; // Genesis Hash
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        try {
            File logFile = new File(LOG_FILE_PATH);
            File parentDir = logFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            if (logFile.exists()) {
                // Đọc dòng cuối cùng để lấy lastEntryHash khôi phục trạng thái chain
                List<String> lines = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8);
                if (!lines.isEmpty()) {
                    String lastLine = lines.get(lines.size() - 1);
                    try {
                        ObjectNode node = (ObjectNode) mapper.readTree(lastLine);
                        if (node.has("thisHash")) {
                            lastEntryHash = node.get("thisHash").asText();
                            log.info("SecureLogChain restored lastEntryHash: {}", lastEntryHash);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse last log line for chain state, using genesis hash", e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to initialize SecureLogChain file or state", e);
        }
    }

    /**
     * Ghi nhận sự kiện kiểm toán bảo mật với đầy đủ thông số và cập nhật log chain.
     */
    public static synchronized void logEvent(
            String actor,
            String certSerial,
            String ticketId,
            String serviceId,
            String action,
            String result,
            String reason) {

        try {
            long timestamp = Instant.now().getEpochSecond();
            ObjectNode entry = mapper.createObjectNode();
            
            entry.put("timestamp", timestamp);
            entry.put("actor", actor != null ? actor : "UNKNOWN");
            entry.put("certSerial", certSerial != null ? certSerial : "N/A");
            entry.put("ticketId", ticketId != null ? ticketId : "N/A");
            entry.put("serviceId", serviceId != null ? serviceId : "N/A");
            entry.put("action", action);
            entry.put("result", result);
            entry.put("reason", reason != null ? reason : "N/A");
            entry.put("prevHash", lastEntryHash);

            // 1. Tính toán HMAC trên toàn bộ các trường để đảm bảo tính toàn vẹn và chống giả mạo
            String rawData = String.format("%d|%s|%s|%s|%s|%s|%s|%s|%s",
                    timestamp,
                    entry.get("actor").asText(),
                    entry.get("certSerial").asText(),
                    entry.get("ticketId").asText(),
                    entry.get("serviceId").asText(),
                    entry.get("action").asText(),
                    entry.get("result").asText(),
                    entry.get("reason").asText(),
                    lastEntryHash);

            String hmac = computeHmac(rawData);
            entry.put("hmac", hmac);

            // 2. Tính hash của chính entry này để làm prevHash cho entry tiếp theo
            byte[] entryBytes = mapper.writeValueAsBytes(entry);
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(entryBytes);
            String thisHash = bytesToHex(hashBytes);
            entry.put("thisHash", thisHash);

            // 3. Cập nhật hash cuối cùng của chain
            lastEntryHash = thisHash;

            // 4. Ghi bền vững vào file dưới dạng single JSON line
            String serializedEntry = mapper.writeValueAsString(entry);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE_PATH, true))) {
                writer.write(serializedEntry);
                writer.newLine();
            }

            log.debug("SecureLogChain logged: {} result={} thisHash={}", action, result, thisHash);

        } catch (Exception e) {
            log.error("Failed to write to SecureLogChain", e);
        }
    }

    private static String computeHmac(String data) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256HMAC = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec secretKey = new SecretKeySpec(LOG_SIGNING_KEY, HMAC_ALGORITHM);
        sha256HMAC.init(secretKey);
        byte[] hmacBytes = sha256HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
