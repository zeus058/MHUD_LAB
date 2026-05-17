package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StResponseInner {
    @JsonProperty("sessionKey")
    private String sessionKey;

    @JsonProperty("nonce")
    private String nonce;

    public StResponseInner() {}

    public StResponseInner(String sessionKey, String nonce) {
        this.sessionKey = sessionKey;
        this.nonce = nonce;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }
}
