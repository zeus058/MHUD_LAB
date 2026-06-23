package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class E2eeInitMessage {
    @JsonProperty("conversationId")
    private String conversationId;

    @JsonProperty("senderId")
    private String senderId;

    @JsonProperty("recipientId")
    private String recipientId;

    @JsonProperty("ephemeralEcdhPubKey")
    private String ephemeralEcdhPubKey;

    @JsonProperty("nonce")
    private String nonce;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("usedOneTimePreKeyId")
    private Integer usedOneTimePreKeyId;

    @JsonProperty("senderCertEcdsa")
    private String senderCertEcdsa;

    @JsonProperty("transcriptHash")
    private String transcriptHash;

    @JsonProperty("signatureEcdsa")
    private String signatureEcdsa;

    public E2eeInitMessage() {}

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public String getEphemeralEcdhPubKey() { return ephemeralEcdhPubKey; }
    public void setEphemeralEcdhPubKey(String ephemeralEcdhPubKey) { this.ephemeralEcdhPubKey = ephemeralEcdhPubKey; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public Integer getUsedOneTimePreKeyId() { return usedOneTimePreKeyId; }
    public void setUsedOneTimePreKeyId(Integer usedOneTimePreKeyId) { this.usedOneTimePreKeyId = usedOneTimePreKeyId; }

    public String getSenderCertEcdsa() { return senderCertEcdsa; }
    public void setSenderCertEcdsa(String senderCertEcdsa) { this.senderCertEcdsa = senderCertEcdsa; }

    public String getTranscriptHash() { return transcriptHash; }
    public void setTranscriptHash(String transcriptHash) { this.transcriptHash = transcriptHash; }

    public String getSignatureEcdsa() { return signatureEcdsa; }
    public void setSignatureEcdsa(String signatureEcdsa) { this.signatureEcdsa = signatureEcdsa; }
}
