package vn.edu.hcmus.securechat.client.network;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.client.crypto.E2eeCryptoService;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.EncryptedChatEnvelope;
import vn.edu.hcmus.securechat.common.protocol.dto.GroupMessageDto;

/**
 * Client-side group chat manager.
 *
 * <p>Text messages are still fan-out E2EE payloads. Membership changes are sent
 * as small GROUP_MESSAGE control frames so every member can keep local metadata
 * in sync without adding a new server-visible group registry.</p>
 */
public class GroupManager {

    public static final int MIN_GROUP_MEMBERS = 3;
    public static final String CONTROL_GROUP_CREATED = "GROUP_CREATED";
    public static final String CONTROL_MEMBERS_UPDATED = "MEMBERS_UPDATED";
    public static final String CONTROL_MEMBER_REMOVED = "MEMBER_REMOVED";

    private static final Logger log = LoggerFactory.getLogger(GroupManager.class);

    public record GroupInfo(String groupId, String groupName, List<String> memberIds, String leaderId) {
        public boolean isLeader(String userId) {
            return leaderId != null && leaderId.equals(userId);
        }
    }

    private final String localUserId;
    private final E2eeCryptoService e2ee;
    private final vn.edu.hcmus.securechat.client.db.LocalDatabase localDb;
    private final Map<String, GroupInfo> groups = new ConcurrentHashMap<>();

    public GroupManager(String localUserId, E2eeCryptoService e2ee,
                        vn.edu.hcmus.securechat.client.db.LocalDatabase localDb) {
        this.localUserId = localUserId;
        this.e2ee = e2ee;
        this.localDb = localDb;
        loadGroupsFromDb();
    }

    private void loadGroupsFromDb() {
        if (localDb == null) {
            return;
        }
        try {
            List<vn.edu.hcmus.securechat.client.db.LocalDatabase.GroupInfoRecord> dbGroups = localDb.loadGroups();
            for (vn.edu.hcmus.securechat.client.db.LocalDatabase.GroupInfoRecord r : dbGroups) {
                List<String> members = normalizeMembers(splitMembers(r.members()), true);
                String leaderId = chooseLeader(r.leaderId(), members);
                groups.put(r.groupId(), new GroupInfo(
                        r.groupId(), r.groupName(), immutableMembers(members), leaderId));
            }
            log.info("Loaded {} groups from local database", dbGroups.size());
        } catch (Exception e) {
            log.error("Error loading groups from database", e);
        }
    }

    public GroupInfo createGroup(String groupName, List<String> memberIds) {
        List<String> allMembers = normalizeMembers(memberIds, true);
        if (allMembers.size() < MIN_GROUP_MEMBERS) {
            throw new IllegalArgumentException("A group must have at least " + MIN_GROUP_MEMBERS + " members");
        }

        String groupId = "group-" + UUID.randomUUID();
        GroupInfo info = new GroupInfo(groupId, groupName, immutableMembers(allMembers), localUserId);
        groups.put(groupId, info);
        persist(info);

        log.info("Group created: groupId={} name={} members={} leader={}",
                groupId, groupName, allMembers, localUserId);
        return info;
    }

    public GroupInfo registerGroup(String groupId, String groupName, String senderId) {
        return registerGroup(groupId, groupName, List.of(senderId), senderId);
    }

    public GroupInfo registerGroup(String groupId, String groupName, List<String> members) {
        return registerGroup(groupId, groupName, members, null);
    }

    public GroupInfo registerGroup(String groupId, String groupName, List<String> members, String leaderId) {
        GroupInfo existing = groups.get(groupId);
        List<String> uniqueMembers = normalizeMembers(members, true);
        String resolvedLeader = chooseLeader(
                leaderId != null && !leaderId.isBlank()
                        ? leaderId
                        : (existing == null ? null : existing.leaderId()),
                uniqueMembers);
        GroupInfo updated = new GroupInfo(groupId, groupName, immutableMembers(uniqueMembers), resolvedLeader);
        groups.put(groupId, updated);
        persist(updated);

        log.info("Registered/updated group: groupId={} name={} members={} leader={}",
                groupId, groupName, uniqueMembers, resolvedLeader);
        return updated;
    }

    public void sendGroupMessage(String groupId, String text) throws IOException {
        GroupInfo info = requireGroup(groupId);
        ensureSocketConnected();

        List<String> recipientIds = new ArrayList<>();
        List<String> encryptedPayloads = new ArrayList<>();
        long sentAt = Instant.now().getEpochSecond();

        for (String memberId : info.memberIds()) {
            if (memberId.equals(localUserId)) {
                continue;
            }
            try {
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

        GroupMessageDto dto = new GroupMessageDto(
                groupId, info.groupName(), localUserId,
                recipientIds, encryptedPayloads, sentAt, newNonce());
        dto.setMemberIds(new ArrayList<>(info.memberIds()));
        dto.setLeaderId(info.leaderId());

        sendGroupDto(dto);
        log.info("GROUP_MESSAGE sent groupId={} recipients={}", groupId, recipientIds.size());
    }

    public List<GroupInfo> listGroups() {
        return new ArrayList<>(groups.values());
    }

    public GroupInfo getGroup(String groupId) {
        return groups.get(groupId);
    }

    public GroupInfo addMember(String groupId, String newMemberId) {
        return addMembers(groupId, List.of(newMemberId));
    }

    public GroupInfo addMembers(String groupId, List<String> newMemberIds) {
        GroupInfo old = requireGroup(groupId);
        List<String> updatedMembers = new ArrayList<>(old.memberIds());
        boolean changed = false;
        for (String memberId : normalizeMembers(newMemberIds, false)) {
            if (!updatedMembers.contains(memberId)) {
                updatedMembers.add(memberId);
                changed = true;
            }
        }
        if (!changed) {
            return old;
        }
        GroupInfo updated = new GroupInfo(
                groupId, old.groupName(), immutableMembers(updatedMembers), old.leaderId());
        groups.put(groupId, updated);
        persist(updated);
        log.info("Members added to group {}: {}", groupId, newMemberIds);
        return updated;
    }

    public GroupInfo removeMember(String groupId, String memberId, String actorId) {
        GroupInfo old = requireGroup(groupId);
        if (!old.isLeader(actorId)) {
            throw new SecurityException("Only the group leader can remove members");
        }
        if (memberId == null || memberId.isBlank() || !old.memberIds().contains(memberId)) {
            return old;
        }
        if (memberId.equals(old.leaderId())) {
            throw new IllegalArgumentException("The group leader cannot remove themself");
        }
        if (old.memberIds().size() - 1 < MIN_GROUP_MEMBERS) {
            throw new IllegalStateException("A group must keep at least " + MIN_GROUP_MEMBERS + " members");
        }

        List<String> updatedMembers = new ArrayList<>(old.memberIds());
        updatedMembers.remove(memberId);
        GroupInfo updated = new GroupInfo(
                groupId, old.groupName(), immutableMembers(updatedMembers), old.leaderId());
        groups.put(groupId, updated);
        persist(updated);
        log.info("Member {} removed from group {}", memberId, groupId);
        return updated;
    }

    public void broadcastGroupCreated(String groupId) throws IOException {
        GroupInfo info = requireGroup(groupId);
        sendGroupControl(info, CONTROL_GROUP_CREATED, recipientsExceptLocal(info.memberIds()), null);
    }

    public void broadcastMembersUpdated(String groupId) throws IOException {
        GroupInfo info = requireGroup(groupId);
        sendGroupControl(info, CONTROL_MEMBERS_UPDATED, recipientsExceptLocal(info.memberIds()), null);
    }

    public void broadcastMemberRemoved(String groupId, String removedMemberId) throws IOException {
        GroupInfo info = requireGroup(groupId);
        List<String> recipients = recipientsExceptLocal(info.memberIds());
        if (removedMemberId != null && !removedMemberId.equals(localUserId) && !recipients.contains(removedMemberId)) {
            recipients.add(removedMemberId);
        }
        sendGroupControl(info, CONTROL_MEMBER_REMOVED, recipients, removedMemberId);
    }

    public void removeGroup(String groupId) {
        groups.remove(groupId);
        if (localDb != null) {
            localDb.deleteGroup(groupId);
        }
    }

    private void sendGroupControl(GroupInfo info, String controlType,
                                  Collection<String> recipients, String removedMemberId) throws IOException {
        ensureSocketConnected();
        List<String> recipientIds = normalizeMembers(new ArrayList<>(recipients), false);
        if (recipientIds.isEmpty()) {
            return;
        }
        GroupMessageDto dto = new GroupMessageDto(
                info.groupId(), info.groupName(), localUserId,
                recipientIds, Collections.nCopies(recipientIds.size(), ""),
                Instant.now().getEpochSecond(), newNonce());
        dto.setControlType(controlType);
        dto.setMemberIds(new ArrayList<>(info.memberIds()));
        dto.setLeaderId(info.leaderId());
        dto.setRemovedMemberId(removedMemberId);
        sendGroupDto(dto);
        log.info("GROUP_CONTROL sent groupId={} type={} recipients={}",
                info.groupId(), controlType, recipientIds.size());
    }

    private void sendGroupDto(GroupMessageDto dto) throws IOException {
        try {
            e2ee.sendFrame(PacketFrame.TYPE_GROUP_MESSAGE, JsonSerializer.toBytes(dto));
        } catch (vn.edu.hcmus.securechat.common.exception.ProtocolException pe) {
            throw new IOException("Failed to serialize GroupMessageDto", pe);
        }
    }

    private void ensureSocketConnected() {
        if (e2ee.getChatSocket() == null || e2ee.getChatSocket().isClosed()) {
            throw new IllegalStateException("Chat socket not connected");
        }
    }

    private GroupInfo requireGroup(String groupId) {
        GroupInfo info = groups.get(groupId);
        if (info == null) {
            throw new IllegalStateException("Group not found: " + groupId);
        }
        return info;
    }

    private void persist(GroupInfo info) {
        if (localDb != null) {
            localDb.saveGroup(info.groupId(), info.groupName(),
                    String.join(",", info.memberIds()), info.leaderId());
        }
    }

    private List<String> recipientsExceptLocal(List<String> members) {
        List<String> recipients = new ArrayList<>();
        for (String member : members) {
            if (!localUserId.equals(member)) {
                recipients.add(member);
            }
        }
        return recipients;
    }

    private List<String> normalizeMembers(List<String> members, boolean includeLocal) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (includeLocal) {
            unique.add(localUserId);
        }
        if (members != null) {
            for (String member : members) {
                if (member == null) {
                    continue;
                }
                String trimmed = member.trim();
                if (!trimmed.isEmpty()) {
                    unique.add(trimmed);
                }
            }
        }
        return new ArrayList<>(unique);
    }

    private List<String> splitMembers(String members) {
        if (members == null || members.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String member : members.split(",")) {
            result.add(member);
        }
        return result;
    }

    private List<String> immutableMembers(List<String> members) {
        return Collections.unmodifiableList(new ArrayList<>(members));
    }

    private String chooseLeader(String preferredLeader, List<String> members) {
        if (preferredLeader != null && !preferredLeader.isBlank() && members.contains(preferredLeader)) {
            return preferredLeader;
        }
        return members.isEmpty() ? localUserId : members.get(0);
    }

    private String newNonce() {
        byte[] nonceBytes = new byte[16];
        new SecureRandom().nextBytes(nonceBytes);
        return Base64.getEncoder().encodeToString(nonceBytes);
    }
}
