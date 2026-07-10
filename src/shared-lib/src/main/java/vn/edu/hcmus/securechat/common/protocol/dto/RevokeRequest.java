package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RevokeRequest {
    @JsonProperty("clientId")
    private String clientId;

    @JsonProperty("certSerial")
    private String certSerial;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("signature")
    private String signature; // Base64 signature of (clientId + certSerial + reason)

    public RevokeRequest() {}

    public RevokeRequest(String clientId, String certSerial, String reason, String signature) {
        this.clientId = clientId;
        this.certSerial = certSerial;
        this.reason = reason;
        this.signature = signature;
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getCertSerial() { return certSerial; }
    public void setCertSerial(String certSerial) { this.certSerial = certSerial; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
}
