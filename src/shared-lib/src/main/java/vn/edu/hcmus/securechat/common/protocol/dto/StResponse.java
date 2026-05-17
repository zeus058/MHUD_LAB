package vn.edu.hcmus.securechat.common.protocol.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

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

    public String getSt() {
        return st;
    }

    public void setSt(String st) {
        this.st = st;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }
}
