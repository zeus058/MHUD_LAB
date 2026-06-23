package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Chat Handshake Request (Client → Chat Server) — theo Contrains.md §5.4.
 *
 * {
 *   "st":           "string — base64(Hybrid_Encrypt(PU_ChatServer, st_inner_json))",
 *   "authenticator": "string — base64(AES-GCM(K_A_Chat, authenticator_json))",
 *   "ecdhePubKey":  "string — base64(X.509 SubjectPublicKeyInfo DER) — ECDHE ephemeral PK",
 *   "sessionNonce": "string — base64(16 random bytes cho HKDF salt)"
 * }
 */
public class ChatHandshakeRequest {

    @JsonProperty("st")
    private String st;

    @JsonProperty("authenticator")
    private String authenticator;

    @JsonProperty("ecdhePubKey")
    private String ecdhePubKey;

    @JsonProperty("sessionNonce")
    private String sessionNonce;

    public ChatHandshakeRequest() {}

    public ChatHandshakeRequest(String st, String authenticator,
                                 String ecdhePubKey, String sessionNonce) {
        this.st = st;
        this.authenticator = authenticator;
        this.ecdhePubKey = ecdhePubKey;
        this.sessionNonce = sessionNonce;
    }

    public String getSt() { return st; }
    public void setSt(String st) { this.st = st; }

    public String getAuthenticator() { return authenticator; }
    public void setAuthenticator(String authenticator) { this.authenticator = authenticator; }

    public String getEcdhePubKey() { return ecdhePubKey; }
    public void setEcdhePubKey(String ecdhePubKey) { this.ecdhePubKey = ecdhePubKey; }

    public String getSessionNonce() { return sessionNonce; }
    public void setSessionNonce(String sessionNonce) { this.sessionNonce = sessionNonce; }
}
