package ch.zhaw.prometheus.agentdefs;

import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

public record AgentCreationContext(
        PromptMessageAssembler promptMessageAssembler,
        LanguageModelGateway languageModelGateway) {

    public PolicyRuntime runtime() {
        return new PolicyRuntime(this.promptMessageAssembler, this.languageModelGateway);
    }
}
