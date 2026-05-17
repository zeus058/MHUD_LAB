package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ST Response inner JSON (trước khi mã hóa bằng K_A_TGS) — theo Contrains.md.
 *
 * Đây là phần response mà TGS mã hóa bằng session key K_A_TGS
 * để Client giải mã và lấy session key K_A_Chat.
 *
 * {
 *   "sessionKey":   "string — base64(K_A_Chat, 32 bytes)",
 *   "nonce":        "string — base64(nonce từ request)",
 *   "targetServer": "string — FQDN của Chat Server"
 * }
 */
public class StResponseInner {

    @JsonProperty("sessionKey")
    private String sessionKey;

    @JsonProperty("nonce")
    private String nonce;

    @JsonProperty("targetServer")
    private String targetServer;

    public StResponseInner() {}

    public StResponseInner(String sessionKey, String nonce, String targetServer) {
        this.sessionKey = sessionKey;
        this.nonce = nonce;
        this.targetServer = targetServer;
    }

    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public String getTargetServer() { return targetServer; }
    public void setTargetServer(String targetServer) { this.targetServer = targetServer; }
}
