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

    public TgtResponseInner() {}

    public TgtResponseInner(String sessionKey, String nonce, String targetTgs) {
        this.sessionKey = sessionKey;
        this.nonce = nonce;
        this.targetTgs = targetTgs;
    }

    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public String getTargetTgs() { return targetTgs; }
    public void setTargetTgs(String targetTgs) { this.targetTgs = targetTgs; }
}
