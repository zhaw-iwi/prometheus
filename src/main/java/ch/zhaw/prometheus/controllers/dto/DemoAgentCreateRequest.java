package ch.zhaw.prometheus.controllers.dto;

public class DemoAgentCreateRequest {
    private String agentDefinitionKey;

    public String getAgentDefinitionKey() {
        return this.agentDefinitionKey;
    }

    public void setAgentDefinitionKey(String agentDefinitionKey) {
        this.agentDefinitionKey = agentDefinitionKey;
    }
}
