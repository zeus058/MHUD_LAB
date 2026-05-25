package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Một dòng trong danh sách hội thoại do Chat Server đẩy xuống client.
 */
public class UserListEntry {

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("online")
    private boolean online;

    @JsonProperty("preKeyAvailable")
    private boolean preKeyAvailable;

    @JsonProperty("lastSeenAt")
    private long lastSeenAt;

    public UserListEntry() {
    }

    public UserListEntry(String userId, boolean online, boolean preKeyAvailable, long lastSeenAt) {
        this.userId = userId;
        this.online = online;
        this.preKeyAvailable = preKeyAvailable;
        this.lastSeenAt = lastSeenAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public boolean isPreKeyAvailable() {
        return preKeyAvailable;
    }

    public void setPreKeyAvailable(boolean preKeyAvailable) {
        this.preKeyAvailable = preKeyAvailable;
    }

    public long getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(long lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
