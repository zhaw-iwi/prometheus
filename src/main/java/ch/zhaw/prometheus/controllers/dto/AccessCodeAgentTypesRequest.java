package ch.zhaw.prometheus.controllers.dto;

import java.util.List;

public class AccessCodeAgentTypesRequest {
    private List<String> agentTypeKeys;

    public List<String> getAgentTypeKeys() {
        return this.agentTypeKeys;
    }

    public void setAgentTypeKeys(List<String> agentTypeKeys) {
        this.agentTypeKeys = agentTypeKeys;
    }
}
