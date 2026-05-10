package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Certificate Response — CA → Client
 * JSON schema theo Contrains.md mục 2.3
 *
 * {
 *   "certificate": "base64(X.509 v3 DER-encoded certificate)",
 *   "caChain":     "base64(CA certificate chain — PEM/DER concatenated)",
 *   "serial":      "string — Certificate serial number (hex)",
 *   "notBefore":   "long — Unix timestamp (milliseconds)",
 *   "notAfter":    "long — Unix timestamp (milliseconds)",
 *   "ocspUrl":     "string — OCSP responder URL (FQDN:port)"
 * }
 */
public class CertificateResponse {

    @JsonProperty("certificate")
    private String certificate;

    @JsonProperty("caChain")
    private String caChain;

    @JsonProperty("serial")
    private String serial;

    @JsonProperty("notBefore")
    private long notBefore;

    @JsonProperty("notAfter")
    private long notAfter;

    @JsonProperty("ocspUrl")
    private String ocspUrl;

    public CertificateResponse() {}

    public CertificateResponse(String certificate, String caChain, String serial,
                               long notBefore, long notAfter, String ocspUrl) {
        this.certificate = certificate;
        this.caChain = caChain;
        this.serial = serial;
        this.notBefore = notBefore;
        this.notAfter = notAfter;
        this.ocspUrl = ocspUrl;
    }

    public String getCertificate() { return certificate; }
    public void setCertificate(String certificate) { this.certificate = certificate; }

    public String getCaChain() { return caChain; }
    public void setCaChain(String caChain) { this.caChain = caChain; }

    public String getSerial() { return serial; }
    public void setSerial(String serial) { this.serial = serial; }

    public long getNotBefore() { return notBefore; }
    public void setNotBefore(long notBefore) { this.notBefore = notBefore; }

    public long getNotAfter() { return notAfter; }
    public void setNotAfter(long notAfter) { this.notAfter = notAfter; }

    public String getOcspUrl() { return ocspUrl; }
    public void setOcspUrl(String ocspUrl) { this.ocspUrl = ocspUrl; }
}
