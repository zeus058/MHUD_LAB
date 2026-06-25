package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO cho một chunk nhị phân của file (FILE_CHUNK).
 *
 * <p>Chunk data được mã hóa AES-256-GCM bằng FileKey trước khi đặt vào
 * {@code encryptedData} (Base64). Recipient giải mã bằng FileKey đã
 * lấy từ {@link FileMetadataDto#getEncryptedFileKey()}, sau đó ráp lại
 * theo thứ tự {@code chunkIndex} và verify SHA-256 toàn bộ file.</p>
 */
public class FileChunkDto {

    /** ID phiên truyền file (tương ứng với {@link FileMetadataDto#getTransferId()}). */
    @JsonProperty("transferId")
    private String transferId;

    /** Người gửi. */
    @JsonProperty("senderId")
    private String senderId;

    /** Người nhận. */
    @JsonProperty("recipientId")
    private String recipientId;

    /** Chỉ số thứ tự chunk, bắt đầu từ 0. */
    @JsonProperty("chunkIndex")
    private int chunkIndex;

    /** Tổng số chunk (phục vụ validation ở receiver). */
    @JsonProperty("totalChunks")
    private int totalChunks;

    /** Dữ liệu chunk đã mã hóa AES-256-GCM (Base64). */
    @JsonProperty("encryptedData")
    private String encryptedData;

    /** GCM IV sử dụng cho chunk này (Base64, 12 bytes). */
    @JsonProperty("iv")
    private String iv;

    /** Cờ đánh dấu chunk cuối cùng. */
    @JsonProperty("lastChunk")
    private boolean lastChunk;

    public FileChunkDto() {}

    public FileChunkDto(String transferId, String senderId, String recipientId,
                        int chunkIndex, int totalChunks, String encryptedData,
                        String iv, boolean lastChunk) {
        this.transferId = transferId;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.encryptedData = encryptedData;
        this.iv = iv;
        this.lastChunk = lastChunk;
    }

    public String getTransferId() { return transferId; }
    public void setTransferId(String transferId) { this.transferId = transferId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }

    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }

    public String getEncryptedData() { return encryptedData; }
    public void setEncryptedData(String encryptedData) { this.encryptedData = encryptedData; }

    public String getIv() { return iv; }
    public void setIv(String iv) { this.iv = iv; }

    public boolean isLastChunk() { return lastChunk; }
    public void setLastChunk(boolean lastChunk) { this.lastChunk = lastChunk; }
}
