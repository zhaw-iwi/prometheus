package ch.zhaw.prometheus.model.policy;

import ch.zhaw.prometheus.spi.LanguageModelGateway;
import ch.zhaw.prometheus.model.GuardEvaluation;

public record PolicyRuntime(
        PromptMessageAssembler promptMessageAssembler,
        LanguageModelGateway languageModelGateway,
        OutputProfile outputProfile,
        GuardEvaluation guardEvaluation,
        ch.zhaw.prometheus.model.ActionExecution actionExecution,
        ch.zhaw.prometheus.model.BehaviourSpeculation behaviourSpeculation,
        ExternalSpeech externalSpeech) {

    public PolicyRuntime forCapabilities(ch.zhaw.prometheus.model.interaction.AgentInteractionProfile profile) {
        return new PolicyRuntime(promptMessageAssembler.forCapabilities(profile), languageModelGateway,
                outputProfile, guardEvaluation, actionExecution, behaviourSpeculation, externalSpeech);
    }

    public PolicyRuntime(PromptMessageAssembler assembler, LanguageModelGateway gateway, OutputProfile profile,
            GuardEvaluation evaluation, ch.zhaw.prometheus.model.ActionExecution actions,
            ch.zhaw.prometheus.model.BehaviourSpeculation speculation) {
        this(assembler, gateway, profile, evaluation, actions, speculation, null);
    }

    public PolicyRuntime withExternalSpeech(ExternalSpeech owner) {
        return new PolicyRuntime(promptMessageAssembler, languageModelGateway, outputProfile, guardEvaluation, actionExecution,
                owner == null ? behaviourSpeculation : null, owner);
    }

    public PolicyRuntime(PromptMessageAssembler assembler, LanguageModelGateway gateway, OutputProfile profile,
            GuardEvaluation evaluation, ch.zhaw.prometheus.model.ActionExecution actions) {
        this(assembler, gateway, profile, evaluation, actions, null);
    }

    public PolicyRuntime withBehaviourSpeculation(ch.zhaw.prometheus.model.BehaviourSpeculation speculation) {
        return new PolicyRuntime(promptMessageAssembler, languageModelGateway, outputProfile, guardEvaluation, actionExecution, externalSpeech == null ? speculation : null, externalSpeech);
    }

    public PolicyRuntime withGateway(LanguageModelGateway gateway) {
        return new PolicyRuntime(promptMessageAssembler, gateway, outputProfile, guardEvaluation, actionExecution, behaviourSpeculation, externalSpeech);
    }

    public PolicyRuntime(PromptMessageAssembler assembler, LanguageModelGateway gateway, OutputProfile profile,
            GuardEvaluation evaluation) { this(assembler, gateway, profile, evaluation, null); }

    public PolicyRuntime withActionExecution(ch.zhaw.prometheus.model.ActionExecution execution) {
        return new PolicyRuntime(promptMessageAssembler, languageModelGateway, outputProfile, guardEvaluation, execution, behaviourSpeculation, externalSpeech);
    }

    public PolicyRuntime(PromptMessageAssembler assembler, LanguageModelGateway gateway, OutputProfile profile) {
        this(assembler, gateway, profile, null);
    }

    public PolicyRuntime withGuardEvaluation(GuardEvaluation evaluation) {
        return new PolicyRuntime(promptMessageAssembler, languageModelGateway, outputProfile, evaluation, actionExecution, behaviourSpeculation, externalSpeech);
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
