package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TGT Response (AS → Client) — JSON schema cố định theo Contrains.md mục 2.3.
 *
 * {
 *   "tgt":      "string — base64(Hybrid_Encrypt(PU_TGS, tgt_json_bytes))",
 *   "response": "string — base64(Hybrid_Encrypt(PU_client, response_inner_json_bytes))"
 * }
 */
public class TgtResponse {

    @JsonProperty("tgt")
    private String tgt;

    @JsonProperty("response")
    private String response;

    public TgtResponse() {}

    public TgtResponse(String tgt, String response) {
        this.tgt = tgt;
        this.response = response;
    }

    public String getTgt() { return tgt; }
    public void setTgt(String tgt) { this.tgt = tgt; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
}
