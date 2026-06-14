package ch.zhaw.prometheus.controllers.dto;

public class AccessCodeCreateRequest {
    private String code;
    private Boolean enabled;

    public String getCode() {
        return this.code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Boolean getEnabled() {
        return this.enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
