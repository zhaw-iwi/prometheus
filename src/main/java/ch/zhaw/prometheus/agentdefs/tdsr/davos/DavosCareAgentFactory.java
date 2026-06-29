package ch.zhaw.prometheus.agentdefs.tdsr.davos;

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

final class DavosCareAgentFactory {
    private static final String TAG_GIGI_TDSR = "demo.gigi.tdsr";
    private static final String TAG_GIGI_DAVOS = "demo.gigi.davos";
    private static final String TAG_GIGI_CARE_CENTER = "demo.gigi.care_center";

    private DavosCareAgentFactory() {
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
        Storage storage = new Storage();
        if (storageInitializer != null) {
            storageInitializer.accept(storage);
        }
        State sessionFinal = new Final(finalStateName, prompts.finalPrompt(), DavosCarePrompts.FINAL_STARTER);
        sessionFinal.setEventSelectorSpec(EventSelectorSpec.any());

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
        interactionPolicy.setNonVerbalPlanPrompt(DavosCarePrompts.NONVERBAL_PLAN);
        interactionPolicy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);

        State interactionState = new State(stateName, interactionPolicy, List.of());

        Transition innerToFinal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(prompts.toFinal())),
                List.of(
                        new StaticExtractionAction(
                                prompts.outcomeExtraction(),
                                storage,
                                "outcome")),
                sessionFinal);
        Transition reactToSocialContextChange = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_SOCIAL_SITUATION_CHANGE),
                        new StaticDecision(DavosCarePrompts.SOCIAL_INTERJECTION_OPPORTUNITY)),
                List.of(),
                interactionState);
        interactionState.addTransition(innerToFinal);
        interactionState.addTransition(reactToSocialContextChange);

        Transition outerToFinal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(DavosCarePrompts.OUTER_STATE_TO_FINAL)),
                List.of(
                        new StaticExtractionAction(
                                prompts.outcomeExtraction(),
                                storage,
                                "outcome")),
                sessionFinal);

        State outerState = new OuterState(
                DavosCarePrompts.OUTER_STATE,
                "GIGI Davos care context",
                List.of(outerToFinal),
                interactionState);

        Agent agent = new Agent(agentName, agentDescription, outerState, storage);
        agent.setInteractionProfile(davosCareProfile());
        return agent;
    }

    private static AgentInteractionProfile davosCareProfile() {
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
                List.of(TAG_GIGI_TDSR, TAG_GIGI_DAVOS, TAG_GIGI_CARE_CENTER));
    }
}
