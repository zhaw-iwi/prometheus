package ch.zhaw.prometheus.controllers.dto;

public class DemoAgentCreateRequest {
    private String agentDefinitionKey;
    private String embodiment;

    public String getAgentDefinitionKey() {
        return this.agentDefinitionKey;
    }

    public void setAgentDefinitionKey(String agentDefinitionKey) {
        this.agentDefinitionKey = agentDefinitionKey;
    }

    public String getEmbodiment() {
        return this.embodiment;
    }

    public void setEmbodiment(String embodiment) {
        this.embodiment = embodiment;
    }
}
