package vn.edu.hcmus.securechat.common.protocol.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SessionTicket {
    @JsonProperty("version")
    private int version = 2;

    @JsonProperty("clientId")
    private String clientId;

    @JsonProperty("deviceFingerprint")
    private String deviceFingerprint;

    @JsonProperty("stId")
    private String stId;

    @JsonProperty("issuedAt")
    private long issuedAt;

    @JsonProperty("expiresAt")
    private long expiresAt;

    @JsonProperty("permissions")
    private List<String> permissions = new ArrayList<>();

    public SessionTicket() {}

    public SessionTicket(String clientId, String deviceFingerprint, String stId,
                         long issuedAt, long expiresAt, List<String> permissions) {
        this.clientId = clientId;
        this.deviceFingerprint = deviceFingerprint;
        this.stId = stId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.permissions = permissions;
    }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }

    public String getStId() { return stId; }
    public void setStId(String stId) { this.stId = stId; }

    public long getIssuedAt() { return issuedAt; }
    public void setIssuedAt(long issuedAt) { this.issuedAt = issuedAt; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }
}
