package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PreKeyRequest {
    @JsonProperty("recipientId")
    private String recipientId;

    public PreKeyRequest() {}

    public PreKeyRequest(String recipientId) {
        this.recipientId = recipientId;
    }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }
}
