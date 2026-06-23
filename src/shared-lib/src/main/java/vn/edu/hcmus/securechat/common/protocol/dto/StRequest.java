package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ST Request (Client → TGS) — JSON schema theo Contrains.md mục 2.3.
 *
 * {
 *   "tgt":            "string — base64(Hybrid_Encrypt(PU_TGS, tgt_json_bytes))",
 *   "authenticator":  "string — base64(AES-GCM encrypt bằng K_A_TGS, authenticator_json_bytes)",
 *   "targetServer":   "string — FQDN của Chat Server (ví dụ: chat.securechat.local)"
 * }
 */
public class StRequest {

    @JsonProperty("tgt")
    private String tgt;

    @JsonProperty("authenticator")
    private String authenticator;

    @JsonProperty("targetServer")
    private String targetServer;

    @JsonProperty("signature")
    private String signature; // Base64 signature of (tgt + "|" + authenticator + "|" + targetServer)

    @JsonProperty("channelBinding")
    private String channelBinding;

    public StRequest() {}

    public StRequest(String tgt, String authenticator, String targetServer) {
        this.tgt = tgt;
        this.authenticator = authenticator;
        this.targetServer = targetServer;
    }

    public StRequest(String tgt, String authenticator, String targetServer, String signature) {
        this.tgt = tgt;
        this.authenticator = authenticator;
        this.targetServer = targetServer;
        this.signature = signature;
    }

    public String getTgt() { return tgt; }
    public void setTgt(String tgt) { this.tgt = tgt; }

    public String getAuthenticator() { return authenticator; }
    public void setAuthenticator(String authenticator) { this.authenticator = authenticator; }

    public String getTargetServer() { return targetServer; }
    public void setTargetServer(String targetServer) { this.targetServer = targetServer; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public String getChannelBinding() { return channelBinding; }
    public void setChannelBinding(String channelBinding) { this.channelBinding = channelBinding; }
}
