package ch.zhaw.prometheus.model.policy;

import ch.zhaw.prometheus.spi.LanguageModelGateway;
import ch.zhaw.prometheus.model.GuardEvaluation;

public record PolicyRuntime(
        PromptMessageAssembler promptMessageAssembler,
        LanguageModelGateway languageModelGateway,
        OutputProfile outputProfile,
        GuardEvaluation guardEvaluation,
        ch.zhaw.prometheus.model.ActionExecution actionExecution,
        ch.zhaw.prometheus.model.BehaviourSpeculation behaviourSpeculation) {

    public PolicyRuntime(PromptMessageAssembler assembler, LanguageModelGateway gateway, OutputProfile profile,
            GuardEvaluation evaluation, ch.zhaw.prometheus.model.ActionExecution actions) {
        this(assembler, gateway, profile, evaluation, actions, null);
    }

    public PolicyRuntime withBehaviourSpeculation(ch.zhaw.prometheus.model.BehaviourSpeculation speculation) {
        return new PolicyRuntime(promptMessageAssembler, languageModelGateway, outputProfile, guardEvaluation, actionExecution, speculation);
    }

    public PolicyRuntime withGateway(LanguageModelGateway gateway) {
        return new PolicyRuntime(promptMessageAssembler, gateway, outputProfile, guardEvaluation, actionExecution, behaviourSpeculation);
    }

    public PolicyRuntime(PromptMessageAssembler assembler, LanguageModelGateway gateway, OutputProfile profile,
            GuardEvaluation evaluation) { this(assembler, gateway, profile, evaluation, null); }

    public PolicyRuntime withActionExecution(ch.zhaw.prometheus.model.ActionExecution execution) {
        return new PolicyRuntime(promptMessageAssembler, languageModelGateway, outputProfile, guardEvaluation, execution, behaviourSpeculation);
    }

    public PolicyRuntime(PromptMessageAssembler assembler, LanguageModelGateway gateway, OutputProfile profile) {
        this(assembler, gateway, profile, null);
    }

    public PolicyRuntime withGuardEvaluation(GuardEvaluation evaluation) {
        return new PolicyRuntime(promptMessageAssembler, languageModelGateway, outputProfile, evaluation, actionExecution, behaviourSpeculation);
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
