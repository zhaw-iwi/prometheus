package ch.zhaw.prometheus.model.policy;

import ch.zhaw.prometheus.spi.LanguageModelGateway;
import ch.zhaw.prometheus.model.GuardEvaluation;

public record PolicyRuntime(
        PromptMessageAssembler promptMessageAssembler,
        LanguageModelGateway languageModelGateway,
        OutputProfile outputProfile,
        GuardEvaluation guardEvaluation) {

    public PolicyRuntime(PromptMessageAssembler assembler, LanguageModelGateway gateway, OutputProfile profile) {
        this(assembler, gateway, profile, null);
    }

    public PolicyRuntime withGuardEvaluation(GuardEvaluation evaluation) {
        return new PolicyRuntime(promptMessageAssembler, languageModelGateway, outputProfile, evaluation);
    }

    public PolicyRuntime(PromptMessageAssembler promptMessageAssembler,
            LanguageModelGateway languageModelGateway) {
        this(promptMessageAssembler, languageModelGateway, OutputProfile.FULL_PLAN);
    }

    public PolicyRuntime {
        if (promptMessageAssembler == null) {
            throw new IllegalArgumentException("promptMessageAssembler must not be null");
        }
        if (languageModelGateway == null) {
            throw new IllegalArgumentException("languageModelGateway must not be null");
        }
        if (outputProfile == null) {
            outputProfile = OutputProfile.FULL_PLAN;
        }
    }
}
