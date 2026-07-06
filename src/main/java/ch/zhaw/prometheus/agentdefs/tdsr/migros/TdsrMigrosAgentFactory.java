package ch.zhaw.prometheus.agentdefs.tdsr.migros;

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

final class TdsrMigrosAgentFactory {
    private static final String TAG_GIGI_TDSR = "demo.gigi.tdsr";
    private static final String TAG_GIGI_MIGROS = "demo.gigi.migros";
    private static final String TAG_GIGI_APPENZELL = "demo.gigi.appenzell";
    private static final String TAG_GENERAL_CONVERSATION = "demo.gigi.general_conversation";
    private static final String TAG_SCRIPTED_SCENE = "demo.gigi.scripted_scene";
    private static final String TAG_TRIADIC_SERVICE = "demo.gigi.triadic_service";

    private TdsrMigrosAgentFactory() {
    }

    record TaskPrompts(String state, String starter, String toFinal, String outcomeExtraction, String finalPrompt) {
    }

    static Agent singleStateGeneralAgent(TaskPrompts prompts, String agentName, String agentDescription,
            String stateName, String finalStateName, List<String> reactionEventTypes) {
        Storage storage = new Storage();
        State sessionFinal = new Final(finalStateName, prompts.finalPrompt(), TdsrMigrosPrompts.FINAL_STARTER);
        sessionFinal.setEventSelectorSpec(EventSelectorSpec.any());

        State interactionState = new State(
                stateName,
                migrosGestureOnlyPromptPolicy(prompts.state(), prompts.starter()),
                List.of());

        interactionState.addTransition(finalTransition(prompts.toFinal(), prompts.outcomeExtraction(), storage,
                sessionFinal));
        for (String eventType : reactionEventTypes == null ? List.<String>of() : reactionEventTypes) {
            interactionState.addTransition(new Transition(new LatestEventTypeDecision(eventType), interactionState));
        }

        Transition outerToFinal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(TdsrMigrosPrompts.OUTER_STATE_TO_FINAL)),
                List.of(
                        new StaticExtractionAction(prompts.outcomeExtraction(), storage, "outcome")),
                sessionFinal);

        State outerState = new OuterState(
                TdsrMigrosPrompts.STATION_OUTER_STATE,
                "GIGI Migros Appenzell context",
                List.of(outerToFinal),
                interactionState);

        Agent agent = new Agent(agentName, agentDescription, outerState, storage);
        agent.setInteractionProfile(migrosGeneralProfile());
        return agent;
    }

    static Agent singleStateSceneAgent(TaskPrompts prompts, String agentName, String agentDescription,
            String stateName, String finalStateName, List<String> sceneTags, List<String> reactionEventTypes) {
        Storage storage = new Storage();
        State sessionFinal = new Final(finalStateName, prompts.finalPrompt(), TdsrMigrosPrompts.FINAL_STARTER);
        sessionFinal.setEventSelectorSpec(EventSelectorSpec.any());

        State interactionState = new State(
                stateName,
                migrosGestureOnlyPromptPolicy(prompts.state(), prompts.starter()),
                List.of());

        interactionState.addTransition(finalTransition(prompts.toFinal(), prompts.outcomeExtraction(), storage,
                sessionFinal));
        for (String eventType : reactionEventTypes == null ? List.<String>of() : reactionEventTypes) {
            interactionState.addTransition(new Transition(new LatestEventTypeDecision(eventType), interactionState));
        }

        Transition outerToFinal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(TdsrMigrosPrompts.OUTER_STATE_TO_FINAL)),
                List.of(
                        new StaticExtractionAction(prompts.outcomeExtraction(), storage, "outcome")),
                sessionFinal);

        State outerState = new OuterState(
                TdsrMigrosPrompts.STATION_OUTER_STATE,
                "GIGI Migros Appenzell scene context",
                List.of(outerToFinal),
                interactionState);

        Agent agent = new Agent(agentName, agentDescription, outerState, storage);
        agent.setInteractionProfile(migrosSceneProfile(sceneTags));
        return agent;
    }

    static PromptPolicy migrosGestureOnlyPromptPolicy(String prompt, String starter) {
        PromptPolicy policy = new PromptPolicy(prompt, starter, PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        policy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);
        return policy;
    }

    static AgentInteractionProfile migrosSceneProfile(List<String> sceneTags) {
        java.util.ArrayList<String> tags = new java.util.ArrayList<>(List.of(
                TAG_GIGI_TDSR,
                TAG_GIGI_MIGROS,
                TAG_GIGI_APPENZELL,
                TAG_SCRIPTED_SCENE,
                TAG_TRIADIC_SERVICE));
        if (sceneTags != null) {
            tags.addAll(sceneTags);
        }
        return AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE,
                        AgentInteractionProfile.OBS_HUMAN_PRESENCE,
                        AgentInteractionProfile.OBS_SOCIAL_GROUPING,
                        AgentInteractionProfile.OBS_SOCIAL_CONTEXT,
                        AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE,
                        AgentInteractionProfile.OBS_WEATHER_CURRENT,
                        AgentInteractionProfile.OBS_WEATHER_FORECAST),
                gestureOnlyModalities(),
                tags);
    }

    static AgentInteractionProfile migrosGeneralProfile() {
        return AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE,
                        AgentInteractionProfile.OBS_HUMAN_PRESENCE,
                        AgentInteractionProfile.OBS_SOCIAL_GROUPING,
                        AgentInteractionProfile.OBS_SOCIAL_CONTEXT,
                        AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE,
                        AgentInteractionProfile.OBS_WEATHER_CURRENT,
                        AgentInteractionProfile.OBS_WEATHER_FORECAST),
                gestureOnlyModalities(),
                List.of(
                        TAG_GIGI_TDSR,
                        TAG_GIGI_MIGROS,
                        TAG_GIGI_APPENZELL,
                        TAG_GENERAL_CONVERSATION,
                        TAG_TRIADIC_SERVICE));
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

    private static List<String> gestureOnlyModalities() {
        return List.of(
                AgentInteractionProfile.MODALITY_SPEECH,
                AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE);
    }
}
