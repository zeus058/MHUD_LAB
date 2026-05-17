package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ST Response (TGS → Client) — JSON schema theo Contrains.md mục 2.3.
 *
 * {
 *   "st":       "string — base64(Hybrid_Encrypt(PU_ChatServer, st_inner_json_bytes))",
 *   "response": "string — base64(AES-GCM encrypt bằng K_A_TGS, response_inner_json_bytes))"
 * }
 *
 * response_inner chứa: { sessionKey (K_A_Chat), nonce, targetServer }
 */
public class StResponse {

    @JsonProperty("st")
    private String st;

    @JsonProperty("response")
    private String response;

    public StResponse() {}

    public StResponse(String st, String response) {
        this.st = st;
        this.response = response;
    }

    public String getSt() { return st; }
    public void setSt(String st) { this.st = st; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
}
