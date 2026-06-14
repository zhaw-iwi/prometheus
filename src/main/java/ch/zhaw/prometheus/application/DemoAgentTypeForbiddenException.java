package ch.zhaw.prometheus.application;

public class DemoAgentTypeForbiddenException extends RuntimeException {
    public DemoAgentTypeForbiddenException(String agentDefinitionKey) {
        super("agent type is not available for this access code: " + agentDefinitionKey);
    }
}
