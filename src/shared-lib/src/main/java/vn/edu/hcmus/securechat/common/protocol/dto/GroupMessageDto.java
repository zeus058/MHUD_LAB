package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO cho tin nhắn nhóm (Group Chat — Client-side Fan-out).
 *
 * <p>Client gửi một gói {@code GroupMessageDto} lên Chat Server.
 * Server đọc {@code recipientIds}, tìm các Socket session tương ứng và
 * forward {@code encryptedPayloads[i]} tới {@code recipientIds[i]}.
 * Server không bao giờ đọc được nội dung vì mỗi payload đã được
 * E2EE-encrypt riêng cho từng thành viên bằng Double Ratchet hiện có.</p>
 *
 * <p>Flow: Client → Server (GROUP_MESSAGE) → Server fan-out → N clients nhận CHAT_MESSAGE bình thường.</p>
 */
public class GroupMessageDto {

    /** ID định danh nhóm (UUID do người tạo sinh ra, lưu cục bộ). */
    @JsonProperty("groupId")
    private String groupId;

    /** Tên hiển thị của nhóm. */
    @JsonProperty("groupName")
    private String groupName;

    /** Người gửi tin nhắn. */
    @JsonProperty("senderId")
    private String senderId;

    /**
     * Danh sách người nhận (không bao gồm người gửi).
     * {@code recipientIds[i]} tương ứng với {@code encryptedPayloads[i]}.
     */
    @JsonProperty("recipientIds")
    private List<String> recipientIds;

    /**
     * Danh sách EncryptedChatEnvelope (JSON Base64) đã được E2EE cho từng người nhận.
     * Mỗi phần tử là JSON serialized của {@link EncryptedChatEnvelope}.
     */
    @JsonProperty("encryptedPayloads")
    private List<String> encryptedPayloads;

    /** Timestamp gửi (epoch seconds). */
    @JsonProperty("sentAt")
    private long sentAt;

    /** Nonce chống replay (Base64). */
    @JsonProperty("nonce")
    private String nonce;

    public GroupMessageDto() {}

    public GroupMessageDto(String groupId, String groupName, String senderId,
                           List<String> recipientIds, List<String> encryptedPayloads,
                           long sentAt, String nonce) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.senderId = senderId;
        this.recipientIds = recipientIds;
        this.encryptedPayloads = encryptedPayloads;
        this.sentAt = sentAt;
        this.nonce = nonce;
    }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public List<String> getRecipientIds() { return recipientIds; }
    public void setRecipientIds(List<String> recipientIds) { this.recipientIds = recipientIds; }

    public List<String> getEncryptedPayloads() { return encryptedPayloads; }
    public void setEncryptedPayloads(List<String> encryptedPayloads) { this.encryptedPayloads = encryptedPayloads; }

    public long getSentAt() { return sentAt; }
    public void setSentAt(long sentAt) { this.sentAt = sentAt; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }
}
