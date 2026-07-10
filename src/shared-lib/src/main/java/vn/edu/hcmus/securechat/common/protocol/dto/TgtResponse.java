package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TGT Response (AS → Client) — JSON schema cố định theo Contrains.md mục 2.3.
 *
 * {
 *   "tgt":            "string — base64(Hybrid_Encrypt(PU_TGS, tgt_json_bytes))",
 *   "response":       "string — base64(Hybrid_Encrypt(PU_client, response_inner_json_bytes))",
 *   "serverSignature":"string — base64(SHA256withRSA_sign(AS_PrivKey, clientId|nonce|response))",
 *   "asCertificate":  "string — base64(DER of AS X.509 cert, for trust bootstrap)"
 * }
 */
public class TgtResponse {

    @JsonProperty("tgt")
    private String tgt;

    @JsonProperty("response")
    private String response;

    /** Server proof: AS ký (clientId|nonce|response) bằng AS private key. */
    @JsonProperty("serverSignature")
    private String serverSignature;

    /** AS certificate (DER, Base64) — trust bootstrap cho client verify serverSignature. */
    @JsonProperty("asCertificate")
    private String asCertificate;

    public TgtResponse() {}

    public TgtResponse(String tgt, String response, String serverSignature, String asCertificate) {
        this.tgt = tgt;
        this.response = response;
        this.serverSignature = serverSignature;
        this.asCertificate = asCertificate;
    }

    public String getTgt() { return tgt; }
    public void setTgt(String tgt) { this.tgt = tgt; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public String getServerSignature() { return serverSignature; }
    public void setServerSignature(String serverSignature) { this.serverSignature = serverSignature; }

    public String getAsCertificate() { return asCertificate; }
    public void setAsCertificate(String asCertificate) { this.asCertificate = asCertificate; }
}
