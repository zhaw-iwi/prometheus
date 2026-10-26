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
import ch.zhaw.prometheus.model.guessing.*;

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
        return singleStateSignalDemo(prompts, agentName, agentDescription, stateName, finalStateName, interactionProfile, reactionEventTypes, false);
    }
    static Agent singleStateSignalDemo(SignalDemoPrompts prompts, String agentName, String agentDescription,
            String stateName, String finalStateName, AgentInteractionProfile interactionProfile,
            List<String> reactionEventTypes, boolean meaningfulSensing) {
        Storage storage = new Storage();
        State sessionFinal = new Final(finalStateName, prompts.finalPrompt(), ValerianCorePrompts.FINAL_STARTER);
        sessionFinal.setEventSelectorSpec(EventSelectorSpec.any());

        PromptPolicy interactionPolicy = meaningfulSensing ? new SensoryPolicy(prompts.state(), prompts.starter(), storage)
                : corePromptPolicy(prompts.state(), prompts.starter());
        State interactionState = new State(stateName, interactionPolicy, List.of());

        Transition innerToFinal = finalTransition(prompts.toFinal(), prompts.outcomeExtraction(), storage,
                sessionFinal);
        interactionState.addTransition(innerToFinal);
        for (String eventType : reactionEventTypes == null ? List.<String>of() : reactionEventTypes) {
            // Derived summaries refresh context but cannot duplicate the richer source reaction.
            if (meaningfulSensing) {
                if (Event.TYPE_SOCIAL_SITUATION_CHANGE.equals(eventType)) continue;
                interactionState.addTransition(new Transition(new LatestEventTypeDecision(eventType),
                        new SensoryReactionAction(storage, prompts.state(), 1000, 2000, 8000), interactionState));
            } else interactionState.addTransition(new Transition(new LatestEventTypeDecision(eventType), interactionState));
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
        return rockScissorPaper(prompts, agentName, agentDescription, true);
    }
    static Agent rockScissorPaper(RpsPrompts prompts, String agentName, String agentDescription, boolean liveCountdown) {
        Storage storage = new Storage();
        storage.put(CoreRpsRound.COUNTDOWN, new com.google.gson.JsonPrimitive(liveCountdown));

        State finalState = new Final("Valerian Core RPS complete", prompts.finalPrompt(),
                ValerianCorePrompts.FINAL_STARTER);
        finalState.setPolicy(new CoreRpsActivityPolicy(storage));
        finalState.setEventSelectorSpec(EventSelectorSpec.any());
        State resultState = new State(
                "Valerian Core RPS Round Result",
                new CoreRpsActivityPolicy(storage),
                List.of());
        State revealState = new State(
                "Valerian Core RPS Reveal Sign",
                new CoreRpsActivityPolicy(storage),
                List.of());

        State startState = new State(
                "Valerian Core RPS Start",
                new CoreRpsActivityPolicy(storage),
                List.of());

        Transition startToFinal = rpsFinalTransition(prompts.toFinal(), finalState, storage);
        Transition startToReveal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(prompts.ready())),
                List.of(new CoreRpsRoundAction(storage, CoreRpsRoundAction.Operation.SELECT)),
                revealState);

        Transition revealToFinal = rpsFinalTransition(prompts.toFinal(), finalState, storage);
        Transition revealToResult = new Transition(
                List.of(new CoreRpsRoundDecision(storage, CoreRpsRoundDecision.Condition.SIGN)),
                List.of(new CoreRpsRoundAction(storage, CoreRpsRoundAction.Operation.RESULT)),
                resultState);

        Transition resultToFinal = rpsFinalTransition(prompts.toFinal(), finalState, storage);
        Transition resultToReveal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(prompts.playAgain())),
                List.of(new CoreRpsRoundAction(storage, CoreRpsRoundAction.Operation.SELECT)),
                revealState);

        startState.addTransition(startToFinal);
        startState.addTransition(startToReveal);
        revealState.addTransition(revealToFinal);
        revealState.addTransition(new Transition(new CoreRpsRoundDecision(storage, CoreRpsRoundDecision.Condition.OPEN),
                new CoreRpsRoundAction(storage, CoreRpsRoundAction.Operation.OPEN), revealState));
        for (var condition : List.of(CoreRpsRoundDecision.Condition.EXPIRED, CoreRpsRoundDecision.Condition.ENDED))
            revealState.addTransition(new Transition(new CoreRpsRoundDecision(storage, condition),
                    new CoreRpsRoundAction(storage, CoreRpsRoundAction.Operation.ABORT), resultState));
        revealState.addTransition(revealToResult);
        resultState.addTransition(resultToFinal);
        resultState.addTransition(resultToReveal);

        State outerState = coreOuterState(finalState, prompts.toFinal(), null, storage, startState,
                List.of(new CoreRpsRoundAction(storage, CoreRpsRoundAction.Operation.STOP)));
        Agent agent = new Agent(agentName, agentDescription, outerState, storage);
        agent.setInteractionProfile(rockScissorPaperProfile());
        return agent;
    }

    static Agent roleClarificationGuessingGame(RoleClarificationPrompts prompts, String agentName,
            String agentDescription) {
        Storage storage = new Storage();
        GuessingMemory.initialize(storage, GuessingMemory.Role.UNDECIDED);
        State finalState = new Final("Valerian Core role clarification guessing game complete",
                prompts.finalPrompt(),
                ValerianCorePrompts.FINAL_STARTER);
        finalState.setEventSelectorSpec(EventSelectorSpec.any());
        finalState.setPolicy(guessingPolicy(storage, prompts.finalPrompt(), ValerianCorePrompts.FINAL_STARTER));
        State valerianGuessesState = new State(
                "Valerian Core guessing game - Valerian guesses",
                guessingPolicy(storage, prompts.valerianGuessesState(), prompts.valerianGuessesStarter()),
                List.of());
        State userGuessesState = new State(
                "Valerian Core guessing game - user guesses",
                guessingPolicy(storage, prompts.userGuessesState(), prompts.userGuessesStarter()),
                List.of());
        State roleClarificationState = new State(
                "Valerian Core guessing game role clarification",
                corePromptPolicy(prompts.roleClarificationState(), prompts.roleClarificationStarter()),
                List.of());

        valerianGuessesState.addTransition(
                finalTransition(prompts.toFinal(), prompts.outcomeExtraction(), storage, finalState));
        userGuessesState.addTransition(
                finalTransition(prompts.toFinal(), prompts.outcomeExtraction(), storage, finalState));
        for (var game : List.of(valerianGuessesState, userGuessesState))
            game.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                    new GuessingAction(storage, GuessingAction.Mode.UPDATE), game));
        roleClarificationState.addTransition(
                finalTransition(prompts.toFinal(), prompts.outcomeExtraction(), storage, finalState));
        roleClarificationState.addTransition(new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(prompts.roleToValerianGuesses())),
                List.of(new GuessingAction(storage, GuessingAction.Mode.AGENT_ROLE)),
                valerianGuessesState));
        roleClarificationState.addTransition(new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(prompts.roleToUserGuesses())),
                List.of(new GuessingAction(storage, GuessingAction.Mode.USER_ROLE)),
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
        var actions = new java.util.ArrayList<ch.zhaw.prometheus.model.Action>();
        if (storage.containsKey(GuessingMemory.CONFIG)) actions.add(new GuessingAction(storage, GuessingAction.Mode.CLOSE));
        actions.add(new StaticExtractionAction(outcomeExtractionPrompt, storage, "outcome"));
        return new Transition(
                exitDecisions(storage, toFinalPrompt),
                actions,
                finalState);
    }

    private static Transition rpsFinalTransition(String toFinalPrompt, State finalState, Storage storage) {
        return new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(toFinalPrompt)),
                List.of(new CoreRpsRoundAction(storage, CoreRpsRoundAction.Operation.STOP)),
                finalState);
    }

    private static State coreOuterState(State finalState, String toFinalPrompt, String outcomeExtractionPrompt,
            Storage storage, State initialState) {
        return coreOuterState(finalState, toFinalPrompt, outcomeExtractionPrompt, storage, initialState, List.of());
    }
    private static State coreOuterState(State finalState, String toFinalPrompt, String outcomeExtractionPrompt,
            Storage storage, State initialState, List<ch.zhaw.prometheus.model.Action> finalActions) {
        List<ch.zhaw.prometheus.model.Action> actions = new java.util.ArrayList<>(finalActions);
        if (storage.containsKey(GuessingMemory.CONFIG)) actions.add(new GuessingAction(storage, GuessingAction.Mode.CLOSE));
        if (outcomeExtractionPrompt != null) actions.add(new StaticExtractionAction(outcomeExtractionPrompt, storage, "outcome"));
        var decisions = new java.util.ArrayList<>(exitDecisions(storage, toFinalPrompt));
        decisions.add(decisions.size() - 1, new StaticDecision(ValerianCorePrompts.OUTER_STATE_TO_FINAL));
        Transition outerToFinal = new Transition(decisions,
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
    private static List<ch.zhaw.prometheus.model.Decision> exitDecisions(Storage storage, String prompt) {
        var decisions = new java.util.ArrayList<ch.zhaw.prometheus.model.Decision>();
        decisions.add(new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE));
        if (storage.containsKey(GuessingMemory.CONFIG)) {
            decisions.add(new GuessingExitDecision(storage));
            prompt += "\nGiving up and asking for the answer ends the round, not the conversation: return false for that.";
        }
        decisions.add(new StaticDecision(prompt)); return decisions;
    }
    private static GuessingPolicy guessingPolicy(Storage storage, String prompt, String starter) {
        return new GuessingPolicy(storage, ValerianCorePrompts.OUTER_STATE + "\n" + prompt, starter, ValerianCorePrompts.NONVERBAL_PLAN);
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

