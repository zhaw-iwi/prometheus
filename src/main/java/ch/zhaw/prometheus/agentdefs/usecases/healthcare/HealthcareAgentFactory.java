package ch.zhaw.prometheus.agentdefs.usecases.healthcare;

import java.util.List;
import java.util.function.Consumer;

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
import ch.zhaw.prometheus.model.policy.PromptValueShape;
import ch.zhaw.prometheus.model.guessing.*;

final class HealthcareAgentFactory {
    private static final String TAG_VALERIAN_USECASES = "demo.valerian.usecases";
    private static final String TAG_VALERIAN_HEALTHCARE = "demo.valerian.healthcare";
    private static final String TAG_VALERIAN_CARE_CENTER = "demo.valerian.care_center";
    private static final String TAG_VALERIAN_PUBLIC_DEMO = "demo.valerian.public_demo";

    private HealthcareAgentFactory() {
    }

    record TaskPrompts(String state, String starter, String toFinal, String outcomeExtraction, String finalPrompt) {
    }

    static Agent singleStateCareAgent(TaskPrompts prompts, String agentName, String agentDescription,
            String stateName, String finalStateName) {
        return singleStateCareAgent(prompts, agentName, agentDescription, stateName, finalStateName, storage -> {
        });
    }

    static Agent singleStateCareAgent(TaskPrompts prompts, String agentName, String agentDescription,
            String stateName, String finalStateName, Consumer<Storage> storageInitializer) {
        return singleStateCareAgent(prompts, agentName, agentDescription, stateName, finalStateName,
                storageInitializer, List.of());
    }

    static Agent singleStateCareAgent(TaskPrompts prompts, String agentName, String agentDescription,
            String stateName, String finalStateName, Consumer<Storage> storageInitializer,
            List<String> stateStorageKeysFrom) {
        return singleStateAgent(
                prompts,
                agentName,
                agentDescription,
                stateName,
                finalStateName,
                storageInitializer,
                stateStorageKeysFrom,
                HealthcarePrompts.NONVERBAL_PLAN,
                HealthcarePrompts.OUTER_STATE,
                "Valerian Use Cases Healthcare context",
                HealthcarePrompts.OUTER_STATE_TO_FINAL,
                HealthcarePrompts.SOCIAL_INTERJECTION_OPPORTUNITY,
                HealthcarePrompts.FINAL_STARTER,
                healthcareCareProfile());
    }

    static Agent singleStateGeneralAgent(TaskPrompts prompts, String agentName, String agentDescription,
            String stateName, String finalStateName) {
        return singleStateAgent(
                prompts,
                agentName,
                agentDescription,
                stateName,
                finalStateName,
                storage -> {
                },
                List.of(),
                HealthcareGeneralPrompts.NONVERBAL_PLAN,
                HealthcareGeneralPrompts.OUTER_STATE,
                "Valerian Use Cases Healthcare public demo context",
                HealthcareGeneralPrompts.OUTER_STATE_TO_FINAL,
                HealthcareGeneralPrompts.SOCIAL_INTERJECTION_OPPORTUNITY,
                HealthcareGeneralPrompts.FINAL_STARTER,
                healthcareGeneralProfile());
    }

    private static Agent singleStateAgent(TaskPrompts prompts, String agentName, String agentDescription,
            String stateName, String finalStateName, Consumer<Storage> storageInitializer,
            List<String> stateStorageKeysFrom, String nonVerbalPlanPrompt, String outerStatePrompt,
            String outerStateName, String outerToFinalPrompt, String socialInterjectionPrompt,
            String finalStarterPrompt, AgentInteractionProfile interactionProfile) {
        Storage storage = new Storage();
        if (storageInitializer != null) {
            storageInitializer.accept(storage);
        }
        State sessionFinal = new Final(finalStateName, prompts.finalPrompt(), finalStarterPrompt);
        sessionFinal.setEventSelectorSpec(EventSelectorSpec.any());
        boolean guessing = storage.containsKey(GuessingMemory.CONFIG);
        if (guessing) sessionFinal.setPolicy(new GuessingPolicy(storage, outerStatePrompt + "\n" + prompts.finalPrompt(), finalStarterPrompt, nonVerbalPlanPrompt));

        PromptPolicy interactionPolicy = stateStorageKeysFrom == null || stateStorageKeysFrom.isEmpty()
                ? new PromptPolicy(
                        prompts.state(),
                        prompts.starter(),
                        PromptPolicy.DEFAULT_SUMMARISE_PROMPT)
                : new PromptPolicy(
                        prompts.state(),
                        prompts.starter(),
                        PromptPolicy.DEFAULT_SUMMARISE_PROMPT,
                        storage,
                        stateStorageKeysFrom,
                        PromptValueShape.OBJECT);
        interactionPolicy.setNonVerbalPlanPrompt(nonVerbalPlanPrompt);
        interactionPolicy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);
        if (guessing) interactionPolicy = new GuessingPolicy(storage, outerStatePrompt + "\n" + prompts.state(), prompts.starter(), nonVerbalPlanPrompt);

        State interactionState = new State(stateName, interactionPolicy, List.of());

        Transition innerToFinal = new Transition(
                closingDecisions(storage, prompts.toFinal(), guessing),
                closingActions(storage, prompts.outcomeExtraction(), guessing),
                sessionFinal);
        Transition reactToSocialContextChange = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_SOCIAL_SITUATION_CHANGE),
                        new StaticDecision(socialInterjectionPrompt)),
                List.of(),
                interactionState);
        interactionState.addTransition(innerToFinal);
        interactionState.addTransition(reactToSocialContextChange);
        if (guessing) interactionState.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                new GuessingAction(storage, GuessingAction.Mode.UPDATE), interactionState));

        Transition outerToFinal = new Transition(
                closingDecisions(storage, outerToFinalPrompt, guessing),
                closingActions(storage, prompts.outcomeExtraction(), guessing),
                sessionFinal);

        State outerState = new OuterState(
                outerStatePrompt,
                outerStateName,
                List.of(outerToFinal),
                interactionState);

        Agent agent = new Agent(agentName, agentDescription, outerState, storage);
        agent.setInteractionProfile(interactionProfile);
        return agent;
    }

    static AgentInteractionProfile healthcareCareProfile() {
        return AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE,
                        AgentInteractionProfile.OBS_WEATHER_CURRENT,
                        AgentInteractionProfile.OBS_WEATHER_FORECAST,
                        AgentInteractionProfile.OBS_HUMAN_PRESENCE,
                        AgentInteractionProfile.OBS_SOCIAL_GROUPING,
                        AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE),
                List.of(
                        AgentInteractionProfile.MODALITY_SPEECH,
                        AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE,
                        AgentInteractionProfile.MODALITY_NONVERBAL_FACIAL_EXPRESSION,
                        AgentInteractionProfile.MODALITY_NONVERBAL_GAZE,
                        AgentInteractionProfile.MODALITY_NONVERBAL_MOTION,
                        AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN),
                List.of(TAG_VALERIAN_USECASES, TAG_VALERIAN_HEALTHCARE, TAG_VALERIAN_CARE_CENTER));
    }
    private static List<ch.zhaw.prometheus.model.Action> closingActions(Storage storage, String prompt, boolean guessing) {
        var actions = new java.util.ArrayList<ch.zhaw.prometheus.model.Action>();
        if (guessing) actions.add(new GuessingAction(storage, GuessingAction.Mode.CLOSE));
        actions.add(new StaticExtractionAction(prompt, storage, "outcome")); return actions;
    }
    private static List<ch.zhaw.prometheus.model.Decision> closingDecisions(Storage storage, String prompt, boolean guessing) {
        var decisions = new java.util.ArrayList<ch.zhaw.prometheus.model.Decision>();
        decisions.add(new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE));
        if (guessing) { decisions.add(new GuessingExitDecision(storage)); prompt += "\nGiving up and asking for the answer ends the round, not the conversation: return false for that."; }
        decisions.add(new StaticDecision(prompt)); return decisions;
    }

    static AgentInteractionProfile healthcareGeneralProfile() {
        return AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE,
                        AgentInteractionProfile.OBS_WEATHER_CURRENT,
                        AgentInteractionProfile.OBS_WEATHER_FORECAST,
                        AgentInteractionProfile.OBS_HUMAN_PRESENCE,
                        AgentInteractionProfile.OBS_SOCIAL_GROUPING,
                        AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE),
                List.of(
                        AgentInteractionProfile.MODALITY_SPEECH,
                        AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE,
                        AgentInteractionProfile.MODALITY_NONVERBAL_FACIAL_EXPRESSION,
                        AgentInteractionProfile.MODALITY_NONVERBAL_GAZE,
                        AgentInteractionProfile.MODALITY_NONVERBAL_MOTION,
                        AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN),
                List.of(
                        TAG_VALERIAN_USECASES,
                        TAG_VALERIAN_HEALTHCARE,
                        TAG_VALERIAN_PUBLIC_DEMO));
    }
}

