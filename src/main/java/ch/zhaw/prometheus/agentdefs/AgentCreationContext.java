package ch.zhaw.prometheus.agentdefs;

import ch.zhaw.prometheus.model.AgentEmbodiment;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

public record AgentCreationContext(
        PromptMessageAssembler promptMessageAssembler,
        LanguageModelGateway languageModelGateway,
        AgentEmbodiment embodiment) {

    public AgentCreationContext(PromptMessageAssembler promptMessageAssembler,
            LanguageModelGateway languageModelGateway) {
        this(promptMessageAssembler, languageModelGateway, AgentEmbodiment.COCKPIT);
    }

    public AgentCreationContext {
        embodiment = embodiment == null ? AgentEmbodiment.COCKPIT : embodiment;
    }

    public PolicyRuntime runtime() {
        return new PolicyRuntime(
                this.promptMessageAssembler.forEmbodiment(this.embodiment),
                this.languageModelGateway);
    }
}
