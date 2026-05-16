package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Certificate Signing Request (CSR) — Client → CA
 * JSON schema theo Contrains.md mục 2.3
 *
 * {
 *   "subjectDn":   "CN=user@example.com,O=Company,C=US — X.500 DN format",
 *   "publicKey":   "base64(RSA Public Key — PKCS#1 DER format)",
 *   "nonce":       "base64(16 random bytes) — chống replay",
 *   "signature":   "base64(RSA-PSS signature trên [subjectDn || publicKey || nonce])"
 * }
 */
public class CertificateSigningRequest {

    @JsonProperty("subjectDn")
    private String subjectDn;

    @JsonProperty("publicKey")
    private String publicKey;

    @JsonProperty("nonce")
    private String nonce;

    @JsonProperty("signature")
    private String signature;

    public CertificateSigningRequest() {}

    public CertificateSigningRequest(String subjectDn, String publicKey,
                                      String nonce, String signature) {
        this.subjectDn = subjectDn;
        this.publicKey = publicKey;
        this.nonce = nonce;
        this.signature = signature;
    }

    public String getSubjectDn() { return subjectDn; }
    public void setSubjectDn(String subjectDn) { this.subjectDn = subjectDn; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
}
