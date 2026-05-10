package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OCSP Request — Client/Server → OCSP Responder
 * JSON schema theo Contrains.md mục 2.3
 *
 * {
 *   "certSerial":   "string — Certificate serial number to check (hex)",
 *   "issuerDn":     "string — X.500 DN của CA cấp chứng chỉ",
 *   "nonce":        "base64(random bytes) — chống replay"
 * }
 */
public class OcspRequest {

    @JsonProperty("certSerial")
    private String certSerial;

    @JsonProperty("issuerDn")
    private String issuerDn;

    @JsonProperty("nonce")
    private String nonce;

    public OcspRequest() {}

    public OcspRequest(String certSerial, String issuerDn, String nonce) {
        this.certSerial = certSerial;
        this.issuerDn = issuerDn;
        this.nonce = nonce;
    }

    public String getCertSerial() { return certSerial; }
    public void setCertSerial(String certSerial) { this.certSerial = certSerial; }

    public String getIssuerDn() { return issuerDn; }
    public void setIssuerDn(String issuerDn) { this.issuerDn = issuerDn; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }
}
