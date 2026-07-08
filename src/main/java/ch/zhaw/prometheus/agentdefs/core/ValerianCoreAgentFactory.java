package ch.zhaw.prometheus.agentdefs.core;

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
import ch.zhaw.prometheus.model.rps.RpsEvaluateRoundAction;
import ch.zhaw.prometheus.model.rps.RpsSelectAgentSignAction;

final class ValerianCoreAgentFactory {
    private static final String TAG_VALERIAN_CORE = "demo.valerian.core";
    private static final String TAG_VALERIAN_SIRA = "demo.valerian.sira_lab";
    private static final String TAG_SOCIAL_CONTEXT = "demo.valerian.social_context";
    private static final String TAG_FACIAL_EXPRESSION = "demo.valerian.facial_expression";
    private static final String TAG_RPS = "demo.valerian.rps";
    private static final String TAG_GUESSING_GAME = "demo.valerian.guessing_game";
    private static final String TAG_ROLE_CLARIFICATION = "demo.valerian.role_clarification";
    private static final String TAG_MULTIMODAL_BEHAVIOUR = "demo.valerian.multimodal_behaviour";

    private ValerianCoreAgentFactory() {
    }

    record SignalDemoPrompts(String state, String starter, String toFinal, String outcomeExtraction,
            String finalPrompt) {
    }

    record RpsPrompts(String start, String starter, String ready, String playAgain, String toFinal,
            String finalPrompt) {
    }

    record RoleClarificationPrompts(String roleClarificationState, String roleClarificationStarter,
            String roleToValerianGuesses, String roleToUserGuesses, String valerianGuessesState, String valerianGuessesStarter,
            String userGuessesState, String userGuessesStarter, String toFinal, String outcomeExtraction,
            String finalPrompt) {
    }

    static Agent singleStateSignalDemo(SignalDemoPrompts prompts, String agentName, String agentDescription,
            String stateName, String finalStateName, AgentInteractionProfile interactionProfile,
            List<String> reactionEventTypes) {
        Storage storage = new Storage();
        State sessionFinal = new Final(finalStateName, prompts.finalPrompt(), ValerianCorePrompts.FINAL_STARTER);
        sessionFinal.setEventSelectorSpec(EventSelectorSpec.any());

        PromptPolicy interactionPolicy = corePromptPolicy(prompts.state(), prompts.starter());
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
                        new StaticDecision(ValerianCorePrompts.OUTER_STATE_TO_FINAL)),
                List.of(
                        new StaticExtractionAction(prompts.outcomeExtraction(), storage, "outcome")),
                sessionFinal);

        State outerState = new OuterState(
                ValerianCorePrompts.OUTER_STATE,
                "Valerian Core context",
                List.of(outerToFinal),
                interactionState);

        Agent agent = new Agent(agentName, agentDescription, outerState, storage);
        agent.setInteractionProfile(interactionProfile);
        return agent;
    }

    static Agent rockScissorPaper(RpsPrompts prompts, String agentName, String agentDescription) {
        Storage storage = new Storage();

        State finalState = new Final("Valerian Core RPS complete", prompts.finalPrompt(),
                ValerianCorePrompts.FINAL_STARTER);
        finalState.setEventSelectorSpec(EventSelectorSpec.any());
        State resultState = new State(
                "Valerian Core RPS Round Result",
                new CoreRpsResultPolicy(storage),
                List.of());
        State revealState = new State(
                "Valerian Core RPS Reveal Sign",
                new CoreRpsRevealPolicy(storage),
                List.of());

        PromptPolicy startPolicy = corePromptPolicy(prompts.start(), prompts.starter());
        State startState = new State(
                "Valerian Core RPS Start",
                startPolicy,
                List.of());

        Transition startToFinal = rpsFinalTransition(prompts.toFinal(), finalState);
        Transition startToReveal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(prompts.ready())),
                List.of(new RpsSelectAgentSignAction(storage)),
                revealState);

        Transition revealToFinal = rpsFinalTransition(prompts.toFinal(), finalState);
        Transition revealToResult = new Transition(
                List.of(new LatestEventTypeDecision(Event.TYPE_HAND_SIGN)),
                List.of(new RpsEvaluateRoundAction(storage)),
                resultState);

        Transition resultToFinal = rpsFinalTransition(prompts.toFinal(), finalState);
        Transition resultToReveal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(prompts.playAgain())),
                List.of(new RpsSelectAgentSignAction(storage)),
                revealState);

        startState.addTransition(startToFinal);
        startState.addTransition(startToReveal);
        revealState.addTransition(revealToFinal);
        revealState.addTransition(revealToResult);
        resultState.addTransition(resultToFinal);
        resultState.addTransition(resultToReveal);

        State outerState = coreOuterState(finalState, prompts.toFinal(), null, storage, startState);
        Agent agent = new Agent(agentName, agentDescription, outerState, storage);
        agent.setInteractionProfile(rockScissorPaperProfile());
        return agent;
    }

    static Agent roleClarificationGuessingGame(RoleClarificationPrompts prompts, String agentName,
            String agentDescription) {
        Storage storage = new Storage();
        State finalState = new Final("Valerian Core role clarification guessing game complete",
                prompts.finalPrompt(),
                ValerianCorePrompts.FINAL_STARTER);
        finalState.setEventSelectorSpec(EventSelectorSpec.any());

        State valerianGuessesState = new State(
                "Valerian Core guessing game - Valerian guesses",
                corePromptPolicy(prompts.valerianGuessesState(), prompts.valerianGuessesStarter()),
                List.of());
        State userGuessesState = new State(
                "Valerian Core guessing game - user guesses",
                corePromptPolicy(prompts.userGuessesState(), prompts.userGuessesStarter()),
                List.of());
        State roleClarificationState = new State(
                "Valerian Core guessing game role clarification",
                corePromptPolicy(prompts.roleClarificationState(), prompts.roleClarificationStarter()),
                List.of());

        valerianGuessesState.addTransition(
                finalTransition(prompts.toFinal(), prompts.outcomeExtraction(), storage, finalState));
        userGuessesState.addTransition(
                finalTransition(prompts.toFinal(), prompts.outcomeExtraction(), storage, finalState));
        roleClarificationState.addTransition(
                finalTransition(prompts.toFinal(), prompts.outcomeExtraction(), storage, finalState));
        roleClarificationState.addTransition(new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(prompts.roleToValerianGuesses())),
                List.of(),
                valerianGuessesState));
        roleClarificationState.addTransition(new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(prompts.roleToUserGuesses())),
                List.of(),
                userGuessesState));

        State outerState = coreOuterState(finalState, prompts.toFinal(), prompts.outcomeExtraction(), storage,
                roleClarificationState);
        Agent agent = new Agent(agentName, agentDescription, outerState, storage);
        agent.setInteractionProfile(roleClarificationGuessingGameProfile());
        return agent;
    }

    static AgentInteractionProfile socialContextProfile() {
        return AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE,
                        AgentInteractionProfile.OBS_HUMAN_PRESENCE,
                        AgentInteractionProfile.OBS_SOCIAL_GROUPING,
                        AgentInteractionProfile.OBS_SOCIAL_CONTEXT,
                        AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE,
                        AgentInteractionProfile.OBS_WEATHER_CURRENT,
                        AgentInteractionProfile.OBS_WEATHER_FORECAST),
                physicalBehaviourModalities(),
                List.of(
                        TAG_VALERIAN_CORE,
                        TAG_VALERIAN_SIRA,
                        TAG_SOCIAL_CONTEXT));
    }

    static AgentInteractionProfile facialExpressionProfile() {
        return AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE,
                        AgentInteractionProfile.OBS_FACE_EMOTION,
                        AgentInteractionProfile.OBS_WEATHER_CURRENT,
                        AgentInteractionProfile.OBS_WEATHER_FORECAST),
                physicalBehaviourModalities(),
                List.of(
                        TAG_VALERIAN_CORE,
                        TAG_VALERIAN_SIRA,
                        TAG_FACIAL_EXPRESSION));
    }

    static AgentInteractionProfile rockScissorPaperProfile() {
        return AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE,
                        AgentInteractionProfile.OBS_HAND_SIGN,
                        AgentInteractionProfile.OBS_WEATHER_CURRENT,
                        AgentInteractionProfile.OBS_WEATHER_FORECAST),
                physicalBehaviourModalitiesWithDisplay(),
                List.of(
                        TAG_VALERIAN_CORE,
                        TAG_VALERIAN_SIRA,
                        TAG_RPS));
    }

    static AgentInteractionProfile roleClarificationGuessingGameProfile() {
        return AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE,
                        AgentInteractionProfile.OBS_WEATHER_CURRENT,
                        AgentInteractionProfile.OBS_WEATHER_FORECAST),
                physicalBehaviourModalities(),
                List.of(
                        TAG_VALERIAN_CORE,
                        TAG_VALERIAN_SIRA,
                        TAG_GUESSING_GAME,
                        TAG_ROLE_CLARIFICATION));
    }

    static AgentInteractionProfile multimodalBehaviourProfile() {
        return AgentInteractionProfile.of(
                List.of(
                        AgentInteractionProfile.OBS_USER_UTTERANCE,
                        AgentInteractionProfile.OBS_FACE_EMOTION,
                        AgentInteractionProfile.OBS_HUMAN_PRESENCE,
                        AgentInteractionProfile.OBS_SOCIAL_GROUPING,
                        AgentInteractionProfile.OBS_SOCIAL_CONTEXT,
                        AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE,
                        AgentInteractionProfile.OBS_HAND_SIGN,
                        AgentInteractionProfile.OBS_WEATHER_CURRENT,
                        AgentInteractionProfile.OBS_WEATHER_FORECAST),
                physicalBehaviourModalities(),
                List.of(
                        TAG_VALERIAN_CORE,
                        TAG_VALERIAN_SIRA,
                        TAG_MULTIMODAL_BEHAVIOUR));
    }

    static PromptPolicy corePromptPolicy(String prompt, String starter) {
        PromptPolicy policy = new PromptPolicy(prompt, starter, PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        policy.setNonVerbalPlanPrompt(ValerianCorePrompts.NONVERBAL_PLAN);
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

    private static Transition rpsFinalTransition(String toFinalPrompt, State finalState) {
        return new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(toFinalPrompt)),
                List.of(),
                finalState);
    }

    private static State coreOuterState(State finalState, String toFinalPrompt, String outcomeExtractionPrompt,
            Storage storage, State initialState) {
        List<ch.zhaw.prometheus.model.Action> actions = outcomeExtractionPrompt == null
                ? List.of()
                : List.of(new StaticExtractionAction(outcomeExtractionPrompt, storage, "outcome"));
        Transition outerToFinal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(ValerianCorePrompts.OUTER_STATE_TO_FINAL),
                        new StaticDecision(toFinalPrompt)),
                actions,
                finalState);
        return new OuterState(
                ValerianCorePrompts.OUTER_STATE,
                "Valerian Core context",
                List.of(outerToFinal),
                initialState);
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

    private static List<String> physicalBehaviourModalitiesWithDisplay() {
        return List.of(
                AgentInteractionProfile.MODALITY_SPEECH,
                AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE,
                AgentInteractionProfile.MODALITY_NONVERBAL_FACIAL_EXPRESSION,
                AgentInteractionProfile.MODALITY_NONVERBAL_GAZE,
                AgentInteractionProfile.MODALITY_NONVERBAL_MOTION,
                AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN,
                AgentInteractionProfile.MODALITY_DISPLAY);
    }
}

