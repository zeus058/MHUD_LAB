package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TGT Request (Client → AS) — JSON schema cố định theo Contrains.md mục 2.3.
 *
 * {
 *   "clientId":  "string",
 *   "targetTgs": "string — FQDN của TGS",
 *   "nonce":     "string — base64(16 random bytes)",
 *   "cert":      "string — base64(DER-encoded X.509 certificate)"
 * }
 */
public class TgtRequest {

    @JsonProperty("clientId")
    private String clientId;

    @JsonProperty("targetTgs")
    private String targetTgs;

    @JsonProperty("nonce")
    private String nonce;

    @JsonProperty("cert")
    private String cert;

    public TgtRequest() {}

    public TgtRequest(String clientId, String targetTgs, String nonce, String cert) {
        this.clientId = clientId;
        this.targetTgs = targetTgs;
        this.nonce = nonce;
        this.cert = cert;
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getTargetTgs() { return targetTgs; }
    public void setTargetTgs(String targetTgs) { this.targetTgs = targetTgs; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public String getCert() { return cert; }
    public void setCert(String cert) { this.cert = cert; }
}
