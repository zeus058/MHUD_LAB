package vn.edu.hcmus.securechat.common.protocol.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PreKeyBundle {
    @JsonProperty("ownerId")
    private String ownerId;

    @JsonProperty("identityCertEcdsa")
    private String identityCertEcdsa;

    @JsonProperty("signedPreKeyId")
    private int signedPreKeyId;

    @JsonProperty("signedPreKeyEcdh")
    private String signedPreKeyEcdh;

    @JsonProperty("signedPreKeySignatureEcdsa")
    private String signedPreKeySignatureEcdsa;

    @JsonProperty("oneTimePreKeys")
    private List<OneTimePreKey> oneTimePreKeys = new ArrayList<>();

    @JsonProperty("bundleTimestamp")
    private long bundleTimestamp;

    @JsonProperty("bundleSignatureEcdsa")
    private String bundleSignatureEcdsa;

    @JsonProperty("lastResort")
    private boolean lastResort;

    public PreKeyBundle() {}

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getIdentityCertEcdsa() { return identityCertEcdsa; }
    public void setIdentityCertEcdsa(String identityCertEcdsa) { this.identityCertEcdsa = identityCertEcdsa; }

    public int getSignedPreKeyId() { return signedPreKeyId; }
    public void setSignedPreKeyId(int signedPreKeyId) { this.signedPreKeyId = signedPreKeyId; }

    public String getSignedPreKeyEcdh() { return signedPreKeyEcdh; }
    public void setSignedPreKeyEcdh(String signedPreKeyEcdh) { this.signedPreKeyEcdh = signedPreKeyEcdh; }

    public String getSignedPreKeySignatureEcdsa() { return signedPreKeySignatureEcdsa; }
    public void setSignedPreKeySignatureEcdsa(String signedPreKeySignatureEcdsa) { this.signedPreKeySignatureEcdsa = signedPreKeySignatureEcdsa; }

    public List<OneTimePreKey> getOneTimePreKeys() { return oneTimePreKeys; }
    public void setOneTimePreKeys(List<OneTimePreKey> oneTimePreKeys) { this.oneTimePreKeys = oneTimePreKeys; }

    public long getBundleTimestamp() { return bundleTimestamp; }
    public void setBundleTimestamp(long bundleTimestamp) { this.bundleTimestamp = bundleTimestamp; }

    public String getBundleSignatureEcdsa() { return bundleSignatureEcdsa; }
    public void setBundleSignatureEcdsa(String bundleSignatureEcdsa) { this.bundleSignatureEcdsa = bundleSignatureEcdsa; }

    public boolean isLastResort() { return lastResort; }
    public void setLastResort(boolean lastResort) { this.lastResort = lastResort; }
}
