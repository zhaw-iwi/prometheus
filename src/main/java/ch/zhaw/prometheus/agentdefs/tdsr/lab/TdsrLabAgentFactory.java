package ch.zhaw.prometheus.agentdefs.tdsr.lab;

import java.util.List;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Final;
import ch.zhaw.prometheus.model.OuterState;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventSelectorSpec;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.model.policy.PromptPolicy;

final class TdsrLabAgentFactory {
    private static final String TAG_GIGI_TDSR = "demo.gigi.tdsr";
    private static final String TAG_GIGI_LAB = "demo.gigi.lab";
    private static final String TAG_GIGI_SIRA = "demo.gigi.sira_lab";
    private static final String TAG_SOCIAL_CONTEXT = "demo.gigi.social_context";
    private static final String TAG_FACIAL_EXPRESSION = "demo.gigi.facial_expression";

    private TdsrLabAgentFactory() {
    }

    record SignalDemoPrompts(String state, String starter, String toFinal, String outcomeExtraction,
            String finalPrompt) {
    }

    static Agent singleStateSignalDemo(SignalDemoPrompts prompts, String agentName, String agentDescription,
            String stateName, String finalStateName, AgentInteractionProfile interactionProfile,
            List<String> reactionEventTypes) {
        Storage storage = new Storage();
        State sessionFinal = new Final(finalStateName, prompts.finalPrompt(), TdsrLabPrompts.FINAL_STARTER);
        sessionFinal.setEventSelectorSpec(EventSelectorSpec.any());

        PromptPolicy interactionPolicy = labPromptPolicy(prompts.state(), prompts.starter());
        State interactionState = new State(stateName, interactionPolicy, List.of());

        Transition innerToFinal = finalTransition(prompts.toFinal(), prompts.outcomeExtraction(), storage,
                sessionFinal);
        interactionState.addTransition(innerToFinal);
        for (String eventType : reactionEventTypes == null ? List.<String>of() : reactionEventTypes) {
            interactionState.addTransition(new Transition(new LatestEventTypeDecision(eventType), interactionState));
        }

        Transition outerToFinal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(TdsrLabPrompts.OUTER_STATE_TO_FINAL)),
                List.of(
                        new StaticExtractionAction(prompts.outcomeExtraction(), storage, "outcome")),
                sessionFinal);

        State outerState = new OuterState(
                TdsrLabPrompts.OUTER_STATE,
                "GIGI SIRA Lab context",
                List.of(outerToFinal),
                interactionState);

        Agent agent = new Agent(agentName, agentDescription, outerState, storage);
        agent.setInteractionProfile(interactionProfile);
        return agent;
    }

    static AgentInteractionProfile socialContextProfile() {
        return AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE,
                        AgentInteractionProfile.OBS_HUMAN_PRESENCE,
                        AgentInteractionProfile.OBS_SOCIAL_GROUPING,
                        AgentInteractionProfile.OBS_SOCIAL_CONTEXT,
                        AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE),
                physicalBehaviourModalities(),
                List.of(
                        TAG_GIGI_TDSR,
                        TAG_GIGI_LAB,
                        TAG_GIGI_SIRA,
                        TAG_SOCIAL_CONTEXT));
    }

    static AgentInteractionProfile facialExpressionProfile() {
        return AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE,
                        AgentInteractionProfile.OBS_FACE_EMOTION),
                physicalBehaviourModalities(),
                List.of(
                        TAG_GIGI_TDSR,
                        TAG_GIGI_LAB,
                        TAG_GIGI_SIRA,
                        TAG_FACIAL_EXPRESSION));
    }

    static PromptPolicy labPromptPolicy(String prompt, String starter) {
        PromptPolicy policy = new PromptPolicy(prompt, starter, PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        policy.setNonVerbalPlanPrompt(TdsrLabPrompts.NONVERBAL_PLAN);
        policy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);
        return policy;
    }

    private static Transition finalTransition(String toFinalPrompt, String outcomeExtractionPrompt, Storage storage,
            State finalState) {
        return new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(toFinalPrompt)),
                List.of(
                        new StaticExtractionAction(outcomeExtractionPrompt, storage, "outcome")),
                finalState);
    }

    private static List<String> physicalBehaviourModalities() {
        return List.of(
                AgentInteractionProfile.MODALITY_SPEECH,
                AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE,
                AgentInteractionProfile.MODALITY_NONVERBAL_FACIAL_EXPRESSION,
                AgentInteractionProfile.MODALITY_NONVERBAL_GAZE,
                AgentInteractionProfile.MODALITY_NONVERBAL_MOTION,
                AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN);
    }
}
