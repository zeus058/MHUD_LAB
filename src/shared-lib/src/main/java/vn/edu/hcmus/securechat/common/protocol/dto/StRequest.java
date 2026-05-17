package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StRequest {
    @JsonProperty("tgt")
    private String tgt;

    @JsonProperty("authenticator")
    private String authenticator;

    @JsonProperty("targetService")
    private String targetService;

    @JsonProperty("nonce")
    private String nonce;

    public StRequest() {}

    public StRequest(String tgt, String authenticator, String targetService, String nonce) {
        this.tgt = tgt;
        this.authenticator = authenticator;
        this.targetService = targetService;
        this.nonce = nonce;
    }

    public String getTgt() {
        return tgt;
    }

    public void setTgt(String tgt) {
        this.tgt = tgt;
    }

    public String getAuthenticator() {
        return authenticator;
    }

    public void setAuthenticator(String authenticator) {
        this.authenticator = authenticator;
    }

    public String getTargetService() {
        return targetService;
    }

    public void setTargetService(String targetService) {
        this.targetService = targetService;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }
}
