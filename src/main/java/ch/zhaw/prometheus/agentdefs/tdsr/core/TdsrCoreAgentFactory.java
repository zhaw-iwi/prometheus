package ch.zhaw.prometheus.agentdefs.tdsr.core;

import java.util.List;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Final;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfiles;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.rps.RpsEvaluateRoundAction;
import ch.zhaw.prometheus.model.rps.RpsResultPolicy;
import ch.zhaw.prometheus.model.rps.RpsRevealPolicy;
import ch.zhaw.prometheus.model.rps.RpsSelectAgentSignAction;

public final class TdsrCoreAgentFactory {
    public static final String GUESSING_GAME_NONVERBAL_PLAN = """
            Produce STRICT JSON only for GIGI's nonverbal behaviour.
            Return exactly one JSON object. No markdown, no code fences, no explanations.

            Required key:
            - "gesture": one of OPEN_QUESTION, EXPLAIN, UNCERTAIN, ACKNOWLEDGE, POLITE, NONE

            Optional keys:
            - "facialExpression": {"type":"string","intensity":0.0-1.0}
            - "gaze": {"direction":"string","focus":"string"}
            - "posture": {"type":"string","lean":"string","openness":0.0-1.0}
            - "prosody": {"rate":"string","pitch":"string","volume":"string"}
            - "proxemics": {"distance":"string"}

            Do not output robot-server command IDs such as open_question_gesture,
            explanatory_sweep_gesture, uncertainty_shrug_gesture,
            acknowledgement_close_hands_gesture, polite_apology_gesture,
            right_hand_up, face_wave, left_kiss, hands_up, release_arm, or idle_pose.
            Do not output top-level motion, motion.move, motion.turn, or locomotion fields.

            Gesture mapping:
            - greeting or invitation to start the game -> POLITE
            - start invitation, play-again invitation, or an important clarifying question -> OPEN_QUESTION
            - a clue summary or final guess -> EXPLAIN
            - uncertainty, thinking aloud, or playful robot self-correction -> UNCERTAIN
            - confirmation, success acknowledgement, round wrap-up, or goodbye -> ACKNOWLEDGE
            - routine yes/no game question or quiet neutral continuation -> NONE

            Use gestures sparsely and vary them across the recent chat history.
            Prefer NONE for many ordinary turns, especially routine yes/no game questions.
            Do not use OPEN_QUESTION just because the speech contains a question.
            Avoid OPEN_QUESTION if it was used recently; choose NONE or ACKNOWLEDGE when fitting.
            Keep gestures small and suitable for a humanoid social robot.
            Prefer warm facial expression, gaze toward the user, open posture, and calm prosody.
            Do not use the same expressive gesture mechanically on every turn.
            """;

    public static final String TOUR_NONVERBAL_PLAN = """
            Produce STRICT JSON only for GIGI's nonverbal behaviour.
            Return exactly one JSON object. No markdown, no code fences, no explanations.

            Required key:
            - "gesture": one of POLITE, EXPLAIN, OPEN_QUESTION, UNCERTAIN, ACKNOWLEDGE, NONE

            Optional keys:
            - "facialExpression": {"type":"string","intensity":0.0-1.0}
            - "gaze": {"direction":"string","focus":"string"}
            - "posture": {"type":"string","lean":"string","openness":0.0-1.0}
            - "prosody": {"rate":"string","pitch":"string","volume":"string"}

            Do not output robot-server command IDs such as open_question_gesture,
            explanatory_sweep_gesture, uncertainty_shrug_gesture,
            acknowledgement_close_hands_gesture, polite_apology_gesture,
            right_hand_up, face_wave, left_kiss, hands_up, release_arm, or idle_pose.
            Do not output top-level motion, motion.move, motion.turn, or locomotion fields.

            Gesture mapping:
            - greeting or warm invitation -> POLITE
            - explaining GIGI, TDSR, robotics, or a station -> EXPLAIN
            - one short follow-up question when it is the main social action -> OPEN_QUESTION
            - uncertainty or missing details -> UNCERTAIN
            - acknowledgement, thanks, or goodbye -> ACKNOWLEDGE
            - ordinary back-and-forth where gesture would distract -> NONE

            Keep gestures occasional, small, and suitable for a humanoid public demo robot.
            Prefer NONE for many routine turns. Do not gesture mechanically on every response.
            Do not use OPEN_QUESTION just because the speech contains a question.
            Avoid OPEN_QUESTION if it was used recently; choose NONE, EXPLAIN, or ACKNOWLEDGE when fitting.
            Vary gestures across the recent chat history.
            """;

    private TdsrCoreAgentFactory() {
    }

    public static String tourConversationOutcomeExtraction() {
        return """
                Extract ended TDSR core tour interaction. Return valid JSON only:
                {"flow_type":"single_state","outcomes":[{"interaction_type":"tdsr_tour_conversation","completed":true,"discussed_topics":["string"],"visitor_questions":["string"],"conversation_summary":"string","result_summary":"string"}],"overall_summary":"string"}
                Rules: exactly one outcome; arrays may be empty; summaries are short and based only on the conversation.
                """;
    }

    public static String tourConversationSocialContextOutcomeExtraction() {
        return """
                Extract ended TDSR core social-tour interaction. Return valid JSON only:
                {"flow_type":"single_state","outcomes":[{"interaction_type":"tdsr_tour_conversation_social_context","completed":true,"discussed_topics":["string"],"visitor_questions":["string"],"social_context_used":true|false,"observed_change_types":["string"],"conversation_summary":"string","result_summary":"string"}],"overall_summary":"string"}
                Rules: exactly one outcome; arrays may be empty; social_context_used is true only if GIGI used social context changes; summaries are short and based only on the conversation/events.
                """;
    }

    public static String socialContextSensitivityOutcomeExtraction() {
        return """
                Extract ended TDSR core social-context demo. Return valid JSON only:
                {"flow_type":"single_state","outcomes":[{"interaction_type":"social_context_sensitivity","completed":true,"reacted_to_social_events":true|false,"observed_change_types":["arrival"],"conversation_summary":"string","result_summary":"string"}],"overall_summary":"string"}
                Rules: exactly one outcome; observed_change_types contains only change types that occurred; summaries are short and based only on the conversation/events.
                """;
    }

    public static String guessingGameOutcomeExtraction() {
        return """
                Extract ended TDSR core guessing game. Return valid JSON only:
                {"flow_type":"single_state","outcomes":[{"interaction_type":"guessing_game_with_gestures","completed":true|false,"final_guess":"string|null","gesture_demo":true,"result_summary":"string","user_confirmation":"string|null"}],"overall_summary":"string"}
                Rules: exactly one outcome; completed is true only if GIGI's final guess was confirmed; gesture_demo is always true; summaries are short and based only on the conversation.
                """;
    }

    public record SingleStatePrompts(String state, String starter, String toFinal, String outcomeExtraction,
            String finalPrompt) {
    }

    public record RpsPrompts(String start, String starter, String ready, String playAgain, String toFinal,
            String finalPrompt) {
    }

    public record SocialTourPrompts(String state, String starter, String toFinal, String outcomeExtraction,
            String socialInterjectionOpportunity, String finalPrompt) {
    }

    public static Agent guessingGameWithGestures(SingleStatePrompts prompts, String agentName,
            String agentDescription) {
        Storage storage = new Storage();
        State sessionFinal = new Final("GIGI TDSR Guessing Game with Gestures Final", prompts.finalPrompt());

        Transition toFinal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(prompts.toFinal())),
                List.of(
                        new StaticExtractionAction(prompts.outcomeExtraction(), storage, "outcome")),
                sessionFinal);

        PromptPolicy interactionPolicy = new PromptPolicy(
                prompts.state(),
                prompts.starter(),
                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        interactionPolicy.setNonVerbalPlanPrompt(GUESSING_GAME_NONVERBAL_PLAN);
        interactionPolicy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);

        State interactionState = new State(
                "GIGI TDSR Guessing Game with Gestures",
                interactionPolicy,
                List.of(toFinal));

        Agent agent = new Agent(agentName, agentDescription, interactionState, storage);
        agent.setInteractionProfile(AgentInteractionProfiles.gigiTdsrGuessingGameWithGestures());
        return agent;
    }

    public static Agent socialContextSensitivity(SingleStatePrompts prompts, String agentName,
            String agentDescription) {
        Storage storage = new Storage();
        State sessionFinal = new Final("GIGI TDSR Social Context Final", prompts.finalPrompt());

        PromptPolicy interactionPolicy = new PromptPolicy(
                prompts.state(),
                prompts.starter(),
                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        State interactionState = new State(
                "GIGI TDSR Social Context",
                interactionPolicy,
                List.of());

        Transition toFinal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(prompts.toFinal())),
                List.of(
                        new StaticExtractionAction(prompts.outcomeExtraction(), storage, "outcome")),
                sessionFinal);
        Transition reactToSocialChange = new Transition(
                new LatestEventTypeDecision(Event.TYPE_SOCIAL_SITUATION_CHANGE),
                interactionState);

        interactionState.addTransition(toFinal);
        interactionState.addTransition(reactToSocialChange);

        Agent agent = new Agent(agentName, agentDescription, interactionState, storage);
        agent.setInteractionProfile(AgentInteractionProfiles.gigiTdsrSocialContextSensitivity());
        return agent;
    }

    public static Agent rockScissorPaper(RpsPrompts prompts, String agentName, String agentDescription) {
        Storage storage = new Storage();

        State finalState = new Final("GIGI TDSR RPS Final", prompts.finalPrompt());
        State resultState = new State(
                "GIGI TDSR RPS Round Result",
                new RpsResultPolicy(storage),
                List.of());
        State revealState = new State(
                "GIGI TDSR RPS Reveal Sign",
                new RpsRevealPolicy(storage),
                List.of());

        PromptPolicy startPolicy = new PromptPolicy(
                prompts.start(),
                prompts.starter(),
                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        State startState = new State(
                "GIGI TDSR RPS Start",
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

        Agent agent = new Agent(agentName, agentDescription, startState, storage);
        agent.setInteractionProfile(AgentInteractionProfiles.gigiTdsrRockScissorPaper());
        return agent;
    }

    public static Agent tourConversation(SingleStatePrompts prompts, String agentName, String agentDescription) {
        Storage storage = new Storage();
        State sessionFinal = new Final("GIGI TDSR Tour Conversation Final", prompts.finalPrompt());

        PromptPolicy interactionPolicy = new PromptPolicy(
                prompts.state(),
                prompts.starter(),
                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        interactionPolicy.setNonVerbalPlanPrompt(TOUR_NONVERBAL_PLAN);
        interactionPolicy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);

        State interactionState = new State(
                "GIGI TDSR Tour Conversation",
                interactionPolicy,
                List.of());

        Transition toFinal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(prompts.toFinal())),
                List.of(
                        new StaticExtractionAction(prompts.outcomeExtraction(), storage, "outcome")),
                sessionFinal);
        interactionState.addTransition(toFinal);

        Agent agent = new Agent(agentName, agentDescription, interactionState, storage);
        agent.setInteractionProfile(AgentInteractionProfiles.gigiTdsrTourConversation());
        return agent;
    }

    public static Agent tourConversationSocialContext(SocialTourPrompts prompts, String agentName,
            String agentDescription) {
        Storage storage = new Storage();
        State sessionFinal = new Final("GIGI TDSR Tour Conversation Social Context Final",
                prompts.finalPrompt());

        PromptPolicy interactionPolicy = new PromptPolicy(
                prompts.state(),
                prompts.starter(),
                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        interactionPolicy.setNonVerbalPlanPrompt(TOUR_NONVERBAL_PLAN);
        interactionPolicy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);

        State interactionState = new State(
                "GIGI TDSR Tour Conversation Social Context",
                interactionPolicy,
                List.of());

        Transition toFinal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(prompts.toFinal())),
                List.of(
                        new StaticExtractionAction(prompts.outcomeExtraction(), storage, "outcome")),
                sessionFinal);
        Transition reactToSocialContextChange = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_SOCIAL_SITUATION_CHANGE),
                        new StaticDecision(prompts.socialInterjectionOpportunity())),
                List.of(),
                interactionState);
        interactionState.addTransition(toFinal);
        interactionState.addTransition(reactToSocialContextChange);

        Agent agent = new Agent(agentName, agentDescription, interactionState, storage);
        agent.setInteractionProfile(AgentInteractionProfiles.gigiTdsrTourConversationSocialContextSensitivity());
        return agent;
    }

    private static Transition rpsFinalTransition(String toFinalPrompt, State finalState) {
        return new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(toFinalPrompt)),
                List.of(),
                finalState);
    }
}
