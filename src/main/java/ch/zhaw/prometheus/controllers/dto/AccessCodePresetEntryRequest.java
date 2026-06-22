package ch.zhaw.prometheus.controllers.dto;

import java.util.List;

public class AccessCodePresetEntryRequest {
    private String code;
    private List<String> agentTypeKeys;

    public String getCode() {
        return this.code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public List<String> getAgentTypeKeys() {
        return this.agentTypeKeys;
    }

    public void setAgentTypeKeys(List<String> agentTypeKeys) {
        this.agentTypeKeys = agentTypeKeys;
    }
}
