package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TGT Response Inner JSON (trước khi mã hóa bằng PU_client) — Contrains.md mục 2.3.
 *
 * {
 *   "sessionKey": "string — base64(K_A_TGS, 32 bytes AES-256 key)",
 *   "nonce":      "string — client nonce to prevent replay",
 *   "targetTgs":  "string"
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
