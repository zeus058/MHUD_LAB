package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OneTimePreKey {
    @JsonProperty("id")
    private int id;

    @JsonProperty("ecdhPubKey")
    private String ecdhPubKey;

    @JsonProperty("kyberPubKey")
    private String kyberPubKey;

    public OneTimePreKey() {}

    public OneTimePreKey(int id, String ecdhPubKey, String kyberPubKey) {
        this.id = id;
        this.ecdhPubKey = ecdhPubKey;
        this.kyberPubKey = kyberPubKey;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getEcdhPubKey() { return ecdhPubKey; }
    public void setEcdhPubKey(String ecdhPubKey) { this.ecdhPubKey = ecdhPubKey; }

    public String getKyberPubKey() { return kyberPubKey; }
    public void setKyberPubKey(String kyberPubKey) { this.kyberPubKey = kyberPubKey; }
}
