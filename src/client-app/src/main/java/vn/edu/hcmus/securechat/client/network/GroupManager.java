package vn.edu.hcmus.securechat.client.network;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.client.crypto.E2eeCryptoService;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.GroupMessageDto;
import vn.edu.hcmus.securechat.common.protocol.dto.EncryptedChatEnvelope;
import vn.edu.hcmus.securechat.common.protocol.dto.ChatMessage;

/**
 * Quản lý tính năng Group Chat — Client-side Fan-out.
 *
 * <p>Kiến trúc: Client tự sinh N {@link EncryptedChatEnvelope} (một cho mỗi thành viên nhóm),
 * đóng gói vào {@link GroupMessageDto} rồi gửi lên Chat Server dưới dạng
 * {@code GROUP_MESSAGE}. Chat Server chỉ fan-out nguyên gói mà không biết nội dung.</p>
 *
 * <p>Bảo mật: Mỗi payload được E2EE bằng {@link E2eeCryptoService#encryptForPeer(String, String)},
 * tái dụng hoàn toàn Double Ratchet session hiện có. Không có khóa nhóm tập trung.</p>
 *
 * <p>Persistence: Nhóm lưu trong bộ nhớ phiên + SQLite cục bộ thông qua
 * {@code LocalDatabase} (bảng {@code groups}).</p>
 */
public class GroupManager {

    private static final Logger log = LoggerFactory.getLogger(GroupManager.class);

    /** Thông tin định nghĩa một nhóm. */
    public record GroupInfo(String groupId, String groupName, List<String> memberIds) {}

    private final String localUserId;
    private final E2eeCryptoService e2ee;
    private final vn.edu.hcmus.securechat.client.db.LocalDatabase localDb;

    /** Bản đồ groupId → GroupInfo, lưu trong bộ nhớ phiên. */
    private final Map<String, GroupInfo> groups = new ConcurrentHashMap<>();

    public GroupManager(String localUserId, E2eeCryptoService e2ee,
                        vn.edu.hcmus.securechat.client.db.LocalDatabase localDb) {
        this.localUserId = localUserId;
        this.e2ee = e2ee;
        this.localDb = localDb;
        loadGroupsFromDb();
    }

    private void loadGroupsFromDb() {
        if (localDb == null) return;
        try {
            java.util.List<vn.edu.hcmus.securechat.client.db.LocalDatabase.GroupInfoRecord> dbGroups = localDb.loadGroups();
            for (vn.edu.hcmus.securechat.client.db.LocalDatabase.GroupInfoRecord r : dbGroups) {
                List<String> members = new ArrayList<>();
                for (String m : r.members().split(",")) {
                    if (!m.trim().isEmpty()) {
                        members.add(m.trim());
                    }
                }
                groups.put(r.groupId(), new GroupInfo(r.groupId(), r.groupName(), Collections.unmodifiableList(members)));
            }
            log.info("Loaded {} groups from local database", dbGroups.size());
        } catch (Exception e) {
            log.error("Error loading groups from database", e);
        }
    }

    /**
     * Tạo nhóm mới với danh sách thành viên cho trước.
     *
     * @param groupName  Tên nhóm hiển thị
     * @param memberIds  Danh sách userId của thành viên (KHÔNG bao gồm localUser — tự được thêm)
     * @return {@link GroupInfo} vừa tạo
     */
    public GroupInfo createGroup(String groupName, List<String> memberIds) {
        String groupId = "group-" + UUID.randomUUID().toString();
        List<String> allMembers = new ArrayList<>(memberIds);
        if (!allMembers.contains(localUserId)) {
            allMembers.add(localUserId);
        }
        GroupInfo info = new GroupInfo(groupId, groupName, Collections.unmodifiableList(allMembers));
        groups.put(groupId, info);

        if (localDb != null) {
            localDb.saveGroup(groupId, groupName, String.join(",", allMembers));
        }

        log.info("Group created: groupId={} name={} members={}", groupId, groupName, allMembers);
        return info;
    }

    public GroupInfo registerGroup(String groupId, String groupName, String senderId) {
        return registerGroup(groupId, groupName, List.of(senderId));
    }

    public GroupInfo registerGroup(String groupId, String groupName, List<String> members) {
        GroupInfo info = groups.get(groupId);
        if (info == null) {
            List<String> uniqueMembers = new ArrayList<>();
            for (String m : members) {
                String trimmed = m.trim();
                if (!trimmed.isEmpty() && !uniqueMembers.contains(trimmed)) {
                    uniqueMembers.add(trimmed);
                }
            }
            if (!uniqueMembers.contains(localUserId)) {
                uniqueMembers.add(localUserId);
            }
            info = new GroupInfo(groupId, groupName, Collections.unmodifiableList(uniqueMembers));
            groups.put(groupId, info);

            if (localDb != null) {
                localDb.saveGroup(groupId, groupName, String.join(",", uniqueMembers));
            }
            log.info("Registered group dynamically: groupId={} name={} members={}", groupId, groupName, uniqueMembers);
        }
        return info;
    }

    /**
     * Gửi một tin nhắn văn bản tới toàn bộ thành viên nhóm.
     *
     * <p>Phương thức này chạy trong SwingWorker thread — KHÔNG gọi từ EDT.</p>
     *
     * @param groupId ID nhóm đã tạo
     * @param text    Nội dung tin nhắn plaintext
     * @throws IOException          Lỗi mạng
     * @throws IllegalStateException Không tìm thấy nhóm hoặc chưa kết nối
     */
    public void sendGroupMessage(String groupId, String text) throws IOException {
        GroupInfo info = groups.get(groupId);
        if (info == null) {
            throw new IllegalStateException("Group not found: " + groupId);
        }
        if (e2ee.getChatSocket() == null || e2ee.getChatSocket().isClosed()) {
            throw new IllegalStateException("Chat socket not connected");
        }

        List<String> recipientIds = new ArrayList<>();
        List<String> encryptedPayloads = new ArrayList<>();
        long sentAt = Instant.now().getEpochSecond();

        // Fan-out: mã hóa riêng cho từng thành viên (trừ localUser)
        for (String memberId : info.memberIds()) {
            if (memberId.equals(localUserId)) {
                continue;
            }
            try {
                // Tái dụng E2EE kênh hiện có (Double Ratchet)
                EncryptedChatEnvelope envelope = e2ee.encryptForPeer(memberId, text);
                recipientIds.add(memberId);
                encryptedPayloads.add(JsonSerializer.toJsonString(envelope));
            } catch (Exception ex) {
                log.warn("Failed to encrypt group message for member={}: {}", memberId, ex.getMessage());
            }
        }

        if (recipientIds.isEmpty()) {
            log.warn("No recipients reachable for group message groupId={}", groupId);
            return;
        }

        // Sinh nonce chống replay
        byte[] nonceBytes = new byte[16];
        new SecureRandom().nextBytes(nonceBytes);
        String nonce = Base64.getEncoder().encodeToString(nonceBytes);

        GroupMessageDto dto = new GroupMessageDto(
                groupId, info.groupName(), localUserId,
                recipientIds, encryptedPayloads, sentAt, nonce);

        try {
            e2ee.sendFrame(PacketFrame.TYPE_GROUP_MESSAGE, JsonSerializer.toBytes(dto));
        } catch (vn.edu.hcmus.securechat.common.exception.ProtocolException pe) {
            throw new IOException("Failed to serialize GroupMessageDto", pe);
        }
        log.info("GROUP_MESSAGE sent groupId={} recipients={}", groupId, recipientIds.size());
    }

    /**
     * Trả về danh sách tất cả nhóm hiện có trong phiên.
     */
    public List<GroupInfo> listGroups() {
        return new ArrayList<>(groups.values());
    }

    /**
     * Lấy thông tin nhóm theo ID. Trả về {@code null} nếu không tồn tại.
     */
    public GroupInfo getGroup(String groupId) {
        return groups.get(groupId);
    }

    /**
     * Thêm thành viên vào nhóm đã có.
     */
    public boolean addMember(String groupId, String newMemberId) {
        GroupInfo old = groups.get(groupId);
        if (old == null) return false;
        if (old.memberIds().contains(newMemberId)) return true;
        List<String> updated = new ArrayList<>(old.memberIds());
        updated.add(newMemberId);
        groups.put(groupId, new GroupInfo(groupId, old.groupName(), Collections.unmodifiableList(updated)));
        log.info("Member {} added to group {}", newMemberId, groupId);
        return true;
    }

    /**
     * Xóa nhóm khỏi bộ nhớ phiên và CSDL cục bộ.
     */
    public void removeGroup(String groupId) {
        groups.remove(groupId);
        if (localDb != null) {
            localDb.deleteGroup(groupId);
        }
    }
}
