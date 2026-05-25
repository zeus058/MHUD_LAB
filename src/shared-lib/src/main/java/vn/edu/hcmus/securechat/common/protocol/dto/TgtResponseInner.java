package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TGT Response inner JSON (trước khi mã hóa bằng PU_client) — theo Contrains.md.
 *
 * Đây là phần response mà AS mã hóa bằng Public Key của Client
 * để Client giải mã bằng Private Key và lấy session key K_A_TGS.
 *
 * {
 *   "sessionKey": "string — base64(K_A_TGS, 32 bytes AES-256 key)",
 *   "nonce":      "string — nonce từ request (để chống replay)",
 *   "targetTgs":  "string — FQDN của TGS"
 * }
 */
public class TgtResponseInner {

    @JsonProperty("sessionKey")
    private String sessionKey;

    @JsonProperty("nonce")
    private String nonce;

    @JsonProperty("targetTgs")
    private String targetTgs;

    @JsonProperty("tgtId")
    private String tgtId;

    @JsonProperty("issuedAt")
    private long issuedAt;

    @JsonProperty("expiresAt")
    private long expiresAt;

    @JsonProperty("renewTill")
    private long renewTill;

    public TgtResponseInner() {}

    public TgtResponseInner(String sessionKey, String nonce, String targetTgs) {
        this.sessionKey = sessionKey;
        this.nonce = nonce;
        this.targetTgs = targetTgs;
    }

    public TgtResponseInner(String sessionKey, String nonce, String targetTgs,
                            String tgtId, long issuedAt, long expiresAt, long renewTill) {
        this.sessionKey = sessionKey;
        this.nonce = nonce;
        this.targetTgs = targetTgs;
        this.tgtId = tgtId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.renewTill = renewTill;
    }

    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public String getTargetTgs() { return targetTgs; }
    public void setTargetTgs(String targetTgs) { this.targetTgs = targetTgs; }

    public String getTgtId() { return tgtId; }
    public void setTgtId(String tgtId) { this.tgtId = tgtId; }

    public long getIssuedAt() { return issuedAt; }
    public void setIssuedAt(long issuedAt) { this.issuedAt = issuedAt; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

    public long getRenewTill() { return renewTill; }
    public void setRenewTill(long renewTill) { this.renewTill = renewTill; }
}
