package ch.zhaw.prometheus.definition.runtime;

import com.fasterxml.jackson.databind.JsonNode;

public interface RuntimeModelGateway {
    RuntimeBehaviour generate(RuntimePromptBundle prompts, RuntimeInvocation invocation);

    boolean decide(String prompt, RuntimeInvocation invocation);

    JsonNode extract(String prompt, JsonNode outputSchema, RuntimeInvocation invocation);
}
