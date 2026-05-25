package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Authenticator JSON (trước khi mã hóa bằng session key) — Contrains.md mục 2.3.
 *
 * {
 *   "clientId":  "string",
 *   "timestamp": 1715000000,
 *   "nonce":     "string — base64(16 random bytes)"
 * }
 */
public class AuthenticatorJson {

    @JsonProperty("clientId")
    private String clientId;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("nonce")
    private String nonce;

    @JsonProperty("ticketId")
    private String ticketId;

    @JsonProperty("targetService")
    private String targetService;

    @JsonProperty("seqNum")
    private long seqNum;

    @JsonProperty("channelBinding")
    private String channelBinding;

    public AuthenticatorJson() {}

    public AuthenticatorJson(String clientId, long timestamp, String nonce) {
        this.clientId = clientId;
        this.timestamp = timestamp;
        this.nonce = nonce;
    }

    public AuthenticatorJson(String clientId, long timestamp, String nonce,
                             String ticketId, String targetService, long seqNum,
                             String channelBinding) {
        this.clientId = clientId;
        this.timestamp = timestamp;
        this.nonce = nonce;
        this.ticketId = ticketId;
        this.targetService = targetService;
        this.seqNum = seqNum;
        this.channelBinding = channelBinding;
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public String getTicketId() { return ticketId; }
    public void setTicketId(String ticketId) { this.ticketId = ticketId; }

    public String getTargetService() { return targetService; }
    public void setTargetService(String targetService) { this.targetService = targetService; }

    public long getSeqNum() { return seqNum; }
    public void setSeqNum(long seqNum) { this.seqNum = seqNum; }

    public String getChannelBinding() { return channelBinding; }
    public void setChannelBinding(String channelBinding) { this.channelBinding = channelBinding; }
}
