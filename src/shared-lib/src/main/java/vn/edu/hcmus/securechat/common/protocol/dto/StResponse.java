package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ST Response (TGS → Client) — JSON schema theo Contrains.md mục 2.3.
 *
 * {
 *   "st":             "string — base64(Hybrid_Encrypt(PU_ChatServer, st_inner_json_bytes))",
 *   "response":       "string — base64(AES-GCM encrypt bằng K_A_TGS, response_inner_json_bytes))",
 *   "serverSignature":"string — base64(SHA256withRSA_sign(TGS_PrivKey, clientId|authNonce|response))",
 *   "tgsCertificate": "string — base64(DER of TGS X.509 cert, for trust bootstrap)"
 * }
 *
 * response_inner chứa: { sessionKey (K_A_Chat), nonce, targetServer }
 */
public class StResponse {

    @JsonProperty("st")
    private String st;

    @JsonProperty("response")
    private String response;

    /** Server proof: TGS ký (clientId|authNonce|response) bằng TGS private key. */
    @JsonProperty("serverSignature")
    private String serverSignature;

    /** TGS certificate (DER, Base64) — trust bootstrap cho client verify serverSignature. */
    @JsonProperty("tgsCertificate")
    private String tgsCertificate;

    public StResponse() {}

    public StResponse(String st, String response, String serverSignature, String tgsCertificate) {
        this.st = st;
        this.response = response;
        this.serverSignature = serverSignature;
        this.tgsCertificate = tgsCertificate;
    }

    public String getSt() { return st; }
    public void setSt(String st) { this.st = st; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public String getServerSignature() { return serverSignature; }
    public void setServerSignature(String serverSignature) { this.serverSignature = serverSignature; }

    public String getTgsCertificate() { return tgsCertificate; }
    public void setTgsCertificate(String tgsCertificate) { this.tgsCertificate = tgsCertificate; }
}
