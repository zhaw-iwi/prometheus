package ch.zhaw.prometheus.model.policy;

import ch.zhaw.prometheus.spi.LanguageModelGateway;

public record PolicyRuntime(
        PromptMessageAssembler promptMessageAssembler,
        LanguageModelGateway languageModelGateway,
        OutputProfile outputProfile) {

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
