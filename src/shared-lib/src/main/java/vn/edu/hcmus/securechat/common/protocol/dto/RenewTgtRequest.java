package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RenewTgtRequest {
    @JsonProperty("oldTgt")
    private String oldTgt;        // Base64 encrypted old TGT
    
    @JsonProperty("authenticator")
    private String authenticator; // AES-GCM(K_A_TGS, AuthenticatorJson) \u2014 d\u00f9ng session key c\u0169
    
    @JsonProperty("clientId")
    private String clientId;
    
    @JsonProperty("signature")
    private String signature;     // PoP signature

    public RenewTgtRequest() {}

    public RenewTgtRequest(String oldTgt, String authenticator, String clientId, String signature) {
        this.oldTgt = oldTgt;
        this.authenticator = authenticator;
        this.clientId = clientId;
        this.signature = signature;
    }

    public String getOldTgt() { return oldTgt; }
    public void setOldTgt(String oldTgt) { this.oldTgt = oldTgt; }

    public String getAuthenticator() { return authenticator; }
    public void setAuthenticator(String authenticator) { this.authenticator = authenticator; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
}
