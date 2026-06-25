package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO metadata cho một lần truyền file (FILE_INIT).
 *
 * <p>Sender gửi {@code FileMetadataDto} lên Chat Server trước khi gửi bất kỳ chunk nào.
 * {@code encryptedFileKey} là AES-256-GCM key đã được E2EE-encrypt cho recipient
 * thông qua kênh Double Ratchet hiện có — đảm bảo File Key không bao giờ lộ ra
 * plaintext trên đường truyền hay ở Server.</p>
 *
 * <p>Integrity: Receiver giải mã tất cả chunk rồi tính SHA-256 và so sánh với
 * {@code sha256Hex}. Nếu không khớp → từ chối file (tampered).</p>
 */
public class FileMetadataDto {

    /** ID phiên truyền file duy nhất (UUID). */
    @JsonProperty("transferId")
    private String transferId;

    /** Người gửi. */
    @JsonProperty("senderId")
    private String senderId;

    /** Người nhận. */
    @JsonProperty("recipientId")
    private String recipientId;

    /** Tên file gốc (không bao gồm đường dẫn). */
    @JsonProperty("fileName")
    private String fileName;

    /** Kích thước file tính bằng byte. */
    @JsonProperty("fileSize")
    private long fileSize;

    /** MIME type (ví dụ: "application/pdf", "image/png"). */
    @JsonProperty("mimeType")
    private String mimeType;

    /** Tổng số chunk sẽ được gửi. */
    @JsonProperty("totalChunks")
    private int totalChunks;

    /**
     * AES-256-GCM key dùng để mã hóa các chunk, đã được E2EE-encrypt cho recipient
     * (Base64 — serialized EncryptedChatEnvelope JSON chứa FileKey).
     */
    @JsonProperty("encryptedFileKey")
    private String encryptedFileKey;

    /** SHA-256 hex của file gốc (plaintext) — dùng để verify toàn vẹn sau khi ráp. */
    @JsonProperty("sha256Hex")
    private String sha256Hex;

    /** Timestamp khởi tạo (epoch seconds). */
    @JsonProperty("initiatedAt")
    private long initiatedAt;

    public FileMetadataDto() {}

    public FileMetadataDto(String transferId, String senderId, String recipientId,
                           String fileName, long fileSize, String mimeType,
                           int totalChunks, String encryptedFileKey,
                           String sha256Hex, long initiatedAt) {
        this.transferId = transferId;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.totalChunks = totalChunks;
        this.encryptedFileKey = encryptedFileKey;
        this.sha256Hex = sha256Hex;
        this.initiatedAt = initiatedAt;
    }

    public String getTransferId() { return transferId; }
    public void setTransferId(String transferId) { this.transferId = transferId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }

    public String getEncryptedFileKey() { return encryptedFileKey; }
    public void setEncryptedFileKey(String encryptedFileKey) { this.encryptedFileKey = encryptedFileKey; }

    public String getSha256Hex() { return sha256Hex; }
    public void setSha256Hex(String sha256Hex) { this.sha256Hex = sha256Hex; }

    public long getInitiatedAt() { return initiatedAt; }
    public void setInitiatedAt(long initiatedAt) { this.initiatedAt = initiatedAt; }
}
