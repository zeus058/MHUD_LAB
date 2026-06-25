package vn.edu.hcmus.securechat.client.network;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.client.crypto.E2eeCryptoService;
import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.FileChunkDto;
import vn.edu.hcmus.securechat.common.protocol.dto.FileMetadataDto;

/**
 * Quản lý tính năng gửi/nhận file an toàn (File Transfer).
 *
 * <h3>Kiến trúc Bảo mật</h3>
 * <ul>
 *   <li><b>FileKey</b>: Sinh ngẫu nhiên 32-byte AES-256-GCM key cho mỗi phiên truyền file.
 *       FileKey được E2EE-encrypt qua Double Ratchet và đính kèm vào
 *       {@link FileMetadataDto#getEncryptedFileKey()}.</li>
 *   <li><b>Chunk Encryption</b>: Mỗi chunk được mã hóa riêng bằng {@code AesGcmCipher.encrypt(fileKey, chunkData)}.
 *       IV/nonce được nhúng vào đầu ciphertext theo chuẩn AesGcmCipher.</li>
 *   <li><b>Integrity</b>: SHA-256 của file gốc được đưa vào FileMetadata;
 *       receiver kiểm tra sau khi ráp toàn bộ chunk.</li>
 *   <li><b>Server Zero-Knowledge</b>: Chat Server chỉ forward nguyên gói theo recipientId
 *       mà không bao giờ nhìn thấy FileKey hay nội dung plaintext.</li>
 * </ul>
 *
 * <h3>Giới hạn chunk</h3>
 * <p>Kích thước mỗi chunk = 512 KB (524 288 bytes) — nhỏ hơn giới hạn 50 MB của PacketFrame
 * và đủ lớn để hiệu quả truyền tải.</p>
 */
public class FileTransferManager {

    private static final Logger log = LoggerFactory.getLogger(FileTransferManager.class);

    /** Kích thước mỗi chunk: 512 KB. */
    public static final int CHUNK_SIZE_BYTES = 512 * 1024;

    /** Thông tin theo dõi tiến trình nhận một file. */
    private static class InboundTransfer {
        final FileMetadataDto meta;
        final byte[] fileKey;
        final byte[][] chunks;
        int receivedCount = 0;
        String groupId;
        String groupName;

        InboundTransfer(FileMetadataDto meta, byte[] fileKey) {
            this.meta = meta;
            this.fileKey = fileKey;
            this.chunks = new byte[meta.getTotalChunks()][];
        }
    }

    private final String localUserId;
    private final E2eeCryptoService e2ee;

    /** Map transferId → inbound transfer state. */
    private final Map<String, InboundTransfer> inboundTransfers = new ConcurrentHashMap<>();

    public interface FileReceivedCallback {
        void onFileReceived(File file, String fileName, String senderId, String groupId);
    }

    /**
     * Callback khi nhận xong file.
     * Chạy trong background thread — gọi SwingUtilities.invokeLater trước khi cập nhật UI.
     */
    private FileReceivedCallback onFileReceived;

    /**
     * Callback tiến trình: (transferId, phần trăm 0–100).
     */
    private BiConsumer<String, Integer> onProgress;

    private GroupManager groupManager;

    public FileTransferManager(String localUserId, E2eeCryptoService e2ee) {
        this.localUserId = localUserId;
        this.e2ee = e2ee;
    }

    public void setGroupManager(GroupManager groupManager) {
        this.groupManager = groupManager;
    }

    public void setOnFileReceived(FileReceivedCallback callback) {
        this.onFileReceived = callback;
    }

    public void setOnProgress(BiConsumer<String, Integer> callback) {
        this.onProgress = callback;
    }

    // =========================================================================
    // === GỬI FILE ============================================================
    // =========================================================================

    /**
     * Gửi file tới một peer.
     *
     * <p>Nên chạy trong SwingWorker thread — NOT trên EDT.</p>
     *
     * @param file       File cần gửi
     * @param recipientId ID người nhận
     * @throws IOException Lỗi đọc file hoặc lỗi mạng
     */
    public void sendFile(File file, String recipientId) throws IOException {
        if (recipientId.startsWith("group-") && groupManager != null) {
            GroupManager.GroupInfo group = groupManager.getGroup(recipientId);
            if (group != null) {
                for (String memberId : group.memberIds()) {
                    if (!memberId.equals(localUserId)) {
                        try {
                            sendFileSingle(file, memberId, recipientId, group.groupName());
                        } catch (Exception ex) {
                            log.error("Failed to send file to group member: " + memberId, ex);
                        }
                    }
                }
                return;
            }
        }
        sendFileSingle(file, recipientId, null, null);
    }

    private void sendFileSingle(File file, String recipientId, String groupId, String groupName) throws IOException {
        if (!file.exists() || !file.isFile()) {
            throw new IOException("File not found or not a regular file: " + file.getAbsolutePath());
        }

        String transferId = UUID.randomUUID().toString();
        long fileSize = file.length();
        int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE_BYTES);
        String mimeType = detectMimeType(file.getName());

        // 1. Sinh FileKey ngẫu nhiên AES-256-GCM
        byte[] fileKey = new byte[32];
        new SecureRandom().nextBytes(fileKey);

        try {
            // 2. Tính SHA-256 của file gốc
            String sha256Hex = computeSha256Hex(file);

            // 3. E2EE-encrypt FileKey cho recipient qua Double Ratchet
            String fileKeyBase64 = Base64.getEncoder().encodeToString(fileKey);
            String payloadText = "FILE_KEY:" + transferId + ":" + fileKeyBase64;
            if (groupId != null && groupName != null) {
                payloadText += ":" + groupId + ":" + groupName;
            }
            vn.edu.hcmus.securechat.common.protocol.dto.EncryptedChatEnvelope keyEnvelope =
                    e2ee.encryptForPeer(recipientId, payloadText);
            String encryptedFileKey = JsonSerializer.toJsonString(keyEnvelope);

            // 4. Gửi FILE_INIT (metadata)
            FileMetadataDto meta = new FileMetadataDto(
                    transferId, localUserId, recipientId,
                    file.getName(), fileSize, mimeType,
                    totalChunks, encryptedFileKey, sha256Hex,
                    Instant.now().getEpochSecond());

            e2ee.sendFrame(PacketFrame.TYPE_FILE_INIT, JsonSerializer.toBytes(meta));
            log.info("FILE_INIT sent transferId={} file={} size={} chunks={}",
                    transferId, file.getName(), fileSize, totalChunks);

            // 5. Đọc và gửi từng chunk
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[CHUNK_SIZE_BYTES];
                int chunkIndex = 0;
                int bytesRead;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    byte[] chunkData = Arrays.copyOf(buffer, bytesRead);

                    // Mã hóa chunk bằng FileKey (AES-256-GCM, IV nhúng vào đầu)
                    byte[] encryptedChunk = AesGcmCipher.encrypt(fileKey, chunkData);
                    // Tách IV (12 bytes đầu) và ciphertext để lưu riêng trong DTO
                    byte[] iv = Arrays.copyOfRange(encryptedChunk, 0, 12);
                    byte[] ciphertext = Arrays.copyOfRange(encryptedChunk, 12, encryptedChunk.length);

                    boolean isLast = (chunkIndex == totalChunks - 1);
                    FileChunkDto chunk = new FileChunkDto(
                            transferId, localUserId, recipientId,
                            chunkIndex, totalChunks,
                            Base64.getEncoder().encodeToString(ciphertext),
                            Base64.getEncoder().encodeToString(iv),
                            isLast);

                    e2ee.sendFrame(PacketFrame.TYPE_FILE_CHUNK, JsonSerializer.toBytes(chunk));

                    // Báo cáo tiến trình
                    int pct = (int) (((chunkIndex + 1) * 100L) / totalChunks);
                    if (onProgress != null) {
                        onProgress.accept(transferId, pct);
                    }

                    chunkIndex++;
                }
            }

            log.info("FILE_TRANSFER_COMPLETE transferId={} totalChunks={}", transferId, totalChunks);

        } catch (Exception ex) {
            throw new IOException("File transfer failed for transferId=" + transferId, ex);
        } finally {
            Arrays.fill(fileKey, (byte) 0);
        }
    }

    // =========================================================================
    // === NHẬN FILE ===========================================================
    // =========================================================================

    /**
     * Xử lý khi nhận được gói FILE_INIT từ server.
     *
     * <p>Giải mã FileKey từ {@code encryptedFileKey} và chuẩn bị buffer nhận chunk.</p>
     *
     * @param meta FileMetadataDto vừa nhận được
     */
    public void handleFileInit(FileMetadataDto meta) {
        try {
            // encryptedFileKey là JSON của EncryptedChatEnvelope → giải mã qua Double Ratchet
            vn.edu.hcmus.securechat.common.protocol.dto.EncryptedChatEnvelope keyEnvelope =
                    JsonSerializer.fromString(meta.getEncryptedFileKey(),
                            vn.edu.hcmus.securechat.common.protocol.dto.EncryptedChatEnvelope.class);
            vn.edu.hcmus.securechat.common.protocol.dto.ChatMessage keyMsg =
                    e2ee.decryptIncoming(keyEnvelope);

            // Payload: "FILE_KEY:<transferId>:<base64FileKey>:[groupId]:[groupName]"
            String[] parts = keyMsg.getContent().split(":", 5);
            if (parts.length < 3 || !"FILE_KEY".equals(parts[0])) {
                log.warn("Invalid FILE_KEY payload format for transferId={}", meta.getTransferId());
                return;
            }
            byte[] fileKey = Base64.getDecoder().decode(parts[2]);

            InboundTransfer transfer = new InboundTransfer(meta, fileKey);
            if (parts.length >= 5) {
                transfer.groupId = parts[3];
                transfer.groupName = parts[4];
            }
            inboundTransfers.put(meta.getTransferId(), transfer);
            log.info("FILE_INIT accepted transferId={} from={} file={} chunks={} group={}",
                    meta.getTransferId(), meta.getSenderId(), meta.getFileName(), meta.getTotalChunks(), transfer.groupId);

        } catch (Exception ex) {
            log.error("Failed to handle FILE_INIT transferId={}", meta.getTransferId(), ex);
        }
    }

    /**
     * Xử lý khi nhận được gói FILE_CHUNK từ server.
     *
     * <p>Giải mã chunk, lưu vào buffer. Khi nhận đủ chunk → ráp file,
     * verify SHA-256, lưu vào Downloads và kích hoạt callback.</p>
     *
     * @param chunk FileChunkDto vừa nhận được
     */
    public void handleFileChunk(FileChunkDto chunk) {
        InboundTransfer transfer = inboundTransfers.get(chunk.getTransferId());
        if (transfer == null) {
            log.warn("No inbound transfer for transferId={}", chunk.getTransferId());
            return;
        }
        if (chunk.getChunkIndex() < 0 || chunk.getChunkIndex() >= transfer.meta.getTotalChunks()) {
            log.warn("Invalid chunkIndex={} for transferId={}", chunk.getChunkIndex(), chunk.getTransferId());
            return;
        }

        try {
            // Ráp IV + ciphertext rồi giải mã bằng FileKey
            byte[] iv = Base64.getDecoder().decode(chunk.getIv());
            byte[] ciphertext = Base64.getDecoder().decode(chunk.getEncryptedData());

            // AesGcmCipher.decrypt mong đợi [nonce(12) | ciphertext+tag]
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            byte[] plainChunk = AesGcmCipher.decrypt(transfer.fileKey, combined);
            transfer.chunks[chunk.getChunkIndex()] = plainChunk;
            transfer.receivedCount++;

            // Báo cáo tiến trình
            int pct = (int) ((transfer.receivedCount * 100L) / transfer.meta.getTotalChunks());
            if (onProgress != null) {
                onProgress.accept(chunk.getTransferId(), pct);
            }

            log.debug("CHUNK received transferId={} index={}/{} progress={}%",
                    chunk.getTransferId(), chunk.getChunkIndex() + 1,
                    transfer.meta.getTotalChunks(), pct);

            // Khi đã nhận đủ tất cả chunk
            if (transfer.receivedCount == transfer.meta.getTotalChunks()) {
                assembleAndVerify(transfer);
            }

        } catch (Exception ex) {
            log.error("Failed to process FILE_CHUNK transferId={} index={}",
                    chunk.getTransferId(), chunk.getChunkIndex(), ex);
        }
    }

    // =========================================================================
    // === PRIVATE HELPERS =====================================================
    // =========================================================================

    private void assembleAndVerify(InboundTransfer transfer) {
        try {
            // 1. Ráp tất cả chunk lại thành file hoàn chỉnh
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (byte[] ch : transfer.chunks) {
                if (ch == null) {
                    log.error("Missing chunk during assembly transferId={}", transfer.meta.getTransferId());
                    return;
                }
                baos.write(ch);
            }
            byte[] fileBytes = baos.toByteArray();

            // 2. Verify SHA-256 toàn vẹn
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] actualHash = sha256.digest(fileBytes);
            String actualHex = bytesToHex(actualHash);

            if (!actualHex.equalsIgnoreCase(transfer.meta.getSha256Hex())) {
                log.error("SHA-256 MISMATCH for transferId={}! Expected={} Got={}",
                        transfer.meta.getTransferId(), transfer.meta.getSha256Hex(), actualHex);
                inboundTransfers.remove(transfer.meta.getTransferId());
                return;
            }

            // 3. Lưu file ra thư mục Downloads
            File downloadsDir = new File(System.getProperty("user.home"), "Downloads" + File.separator + "SecureChat");
            downloadsDir.mkdirs();
            File outFile = new File(downloadsDir, transfer.meta.getFileName());

            // Đặt tên tránh trùng lặp
            if (outFile.exists()) {
                String baseName = getBaseName(transfer.meta.getFileName());
                String ext = getExtension(transfer.meta.getFileName());
                outFile = new File(downloadsDir, baseName + "_" + System.currentTimeMillis() + ext);
            }

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(fileBytes);
            }

            log.info("FILE_ASSEMBLED transferId={} savedTo={} sha256OK",
                    transfer.meta.getTransferId(), outFile.getAbsolutePath());

            // 4. Callback
            if (onFileReceived != null) {
                File finalFile = outFile;
                onFileReceived.onFileReceived(finalFile, transfer.meta.getFileName(), transfer.meta.getSenderId(), transfer.groupId);
            }

        } catch (Exception ex) {
            log.error("Failed to assemble file transferId={}", transfer.meta.getTransferId(), ex);
        } finally {
            // Xóa trắng FileKey khỏi bộ nhớ
            InboundTransfer t = inboundTransfers.remove(transfer.meta.getTransferId());
            if (t != null) {
                Arrays.fill(t.fileKey, (byte) 0);
            }
        }
    }

    private static String computeSha256Hex(File file) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                sha256.update(buffer, 0, read);
            }
        }
        return bytesToHex(sha256.digest());
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String detectMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif"))  return "image/gif";
        if (lower.endsWith(".mp4"))  return "video/mp4";
        if (lower.endsWith(".mp3"))  return "audio/mpeg";
        if (lower.endsWith(".txt"))  return "text/plain";
        if (lower.endsWith(".zip"))  return "application/zip";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return "application/octet-stream";
    }

    private static String getBaseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot) : "";
    }
}
