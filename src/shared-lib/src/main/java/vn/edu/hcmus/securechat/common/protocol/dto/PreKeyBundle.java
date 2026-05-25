package vn.edu.hcmus.securechat.common.protocol.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PreKeyBundle {
    @JsonProperty("ownerId")
    private String ownerId;

    @JsonProperty("identityCertEcdsa")
    private String identityCertEcdsa;

    @JsonProperty("identityCertDilithium")
    private String identityCertDilithium;

    @JsonProperty("signedPreKeyId")
    private int signedPreKeyId;

    @JsonProperty("signedPreKeyEcdh")
    private String signedPreKeyEcdh;

    @JsonProperty("signedPreKeyKyber")
    private String signedPreKeyKyber;

    @JsonProperty("signedPreKeySignatureEcdsa")
    private String signedPreKeySignatureEcdsa;

    @JsonProperty("signedPreKeySignatureDilithium")
    private String signedPreKeySignatureDilithium;

    @JsonProperty("oneTimePreKeys")
    private List<OneTimePreKey> oneTimePreKeys = new ArrayList<>();

    @JsonProperty("bundleTimestamp")
    private long bundleTimestamp;

    @JsonProperty("bundleSignatureEcdsa")
    private String bundleSignatureEcdsa;

    @JsonProperty("bundleSignatureDilithium")
    private String bundleSignatureDilithium;

    @JsonProperty("lastResort")
    private boolean lastResort;

    public PreKeyBundle() {}

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getIdentityCertEcdsa() { return identityCertEcdsa; }
    public void setIdentityCertEcdsa(String identityCertEcdsa) { this.identityCertEcdsa = identityCertEcdsa; }

    public String getIdentityCertDilithium() { return identityCertDilithium; }
    public void setIdentityCertDilithium(String identityCertDilithium) { this.identityCertDilithium = identityCertDilithium; }

    public int getSignedPreKeyId() { return signedPreKeyId; }
    public void setSignedPreKeyId(int signedPreKeyId) { this.signedPreKeyId = signedPreKeyId; }

    public String getSignedPreKeyEcdh() { return signedPreKeyEcdh; }
    public void setSignedPreKeyEcdh(String signedPreKeyEcdh) { this.signedPreKeyEcdh = signedPreKeyEcdh; }

    public String getSignedPreKeyKyber() { return signedPreKeyKyber; }
    public void setSignedPreKeyKyber(String signedPreKeyKyber) { this.signedPreKeyKyber = signedPreKeyKyber; }

    public String getSignedPreKeySignatureEcdsa() { return signedPreKeySignatureEcdsa; }
    public void setSignedPreKeySignatureEcdsa(String signedPreKeySignatureEcdsa) { this.signedPreKeySignatureEcdsa = signedPreKeySignatureEcdsa; }

    public String getSignedPreKeySignatureDilithium() { return signedPreKeySignatureDilithium; }
    public void setSignedPreKeySignatureDilithium(String signedPreKeySignatureDilithium) { this.signedPreKeySignatureDilithium = signedPreKeySignatureDilithium; }

    public List<OneTimePreKey> getOneTimePreKeys() { return oneTimePreKeys; }
    public void setOneTimePreKeys(List<OneTimePreKey> oneTimePreKeys) { this.oneTimePreKeys = oneTimePreKeys; }

    public long getBundleTimestamp() { return bundleTimestamp; }
    public void setBundleTimestamp(long bundleTimestamp) { this.bundleTimestamp = bundleTimestamp; }

    public String getBundleSignatureEcdsa() { return bundleSignatureEcdsa; }
    public void setBundleSignatureEcdsa(String bundleSignatureEcdsa) { this.bundleSignatureEcdsa = bundleSignatureEcdsa; }

    public String getBundleSignatureDilithium() { return bundleSignatureDilithium; }
    public void setBundleSignatureDilithium(String bundleSignatureDilithium) { this.bundleSignatureDilithium = bundleSignatureDilithium; }

    public boolean isLastResort() { return lastResort; }
    public void setLastResort(boolean lastResort) { this.lastResort = lastResort; }
}
