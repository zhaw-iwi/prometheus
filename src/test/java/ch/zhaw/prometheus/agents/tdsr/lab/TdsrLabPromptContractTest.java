package ch.zhaw.prometheus.agents.tdsr.lab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.OuterState;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class TdsrLabPromptContractTest {
    private static final int MAX_PERSISTED_PROMPT_LENGTH = 8000;

    private static final List<DefinitionCase> DEFINITIONS = List.of(
            new DefinitionCase(
                    new ch.zhaw.prometheus.agentdefs.tdsr.lab.SocialContextSensitivity(),
                    ch.zhaw.prometheus.agentdefs.tdsr.lab.SocialContextSensitivity.class,
                    "tdsr.lab.social_context_sensitivity"),
            new DefinitionCase(
                    new ch.zhaw.prometheus.agentdefs.tdsr.lab.FacialExpressionSensitivity(),
                    ch.zhaw.prometheus.agentdefs.tdsr.lab.FacialExpressionSensitivity.class,
                    "tdsr.lab.facial_expression_sensitivity"),
            new DefinitionCase(
                    new ch.zhaw.prometheus.agentdefs.tdsr.lab.RockScissorPaper(),
                    ch.zhaw.prometheus.agentdefs.tdsr.lab.RockScissorPaper.class,
                    "tdsr.lab.rock_scissor_paper"),
            new DefinitionCase(
                    new ch.zhaw.prometheus.agentdefs.tdsr.lab.RoleClarificationGuessingGame(),
                    ch.zhaw.prometheus.agentdefs.tdsr.lab.RoleClarificationGuessingGame.class,
                    "tdsr.lab.role_clarification_guessing_game"),
            new DefinitionCase(
                    new ch.zhaw.prometheus.agentdefs.tdsr.lab.MultimodalBehaviour(),
                    ch.zhaw.prometheus.agentdefs.tdsr.lab.MultimodalBehaviour.class,
                    "tdsr.lab.multimodal_behaviour"));

    @Test
    void definitionsUseLabKeysPackagePathAndEnglishRealtimeLanguage() {
        for (DefinitionCase definitionCase : DEFINITIONS) {
            AgentDefinition definition = definitionCase.definition();
            Agent agent = definition.createAgent();

            assertEquals(definitionCase.key(), definition.key());
            assertEquals(List.of("tdsr", "lab"), definition.packagePath());
            assertEquals(AgentDefinition.LANGUAGE_ENGLISH, definition.languageCode());
            assertEquals(AgentDefinition.LANGUAGE_ENGLISH, agent.getLanguageCode());
            assertTrue(agent.getName().contains("SIRA Lab"));
            assertTrue(agent.getDescription().contains("English"));
        }
    }

    @Test
    void labProfilesExposeFocusedSensingAndPhysicalBehaviourOutput() {
        AgentInteractionProfile socialProfile = new ch.zhaw.prometheus.agentdefs.tdsr.lab.SocialContextSensitivity()
                .createAgent()
                .getInteractionProfile();
        assertTrue(socialProfile.supportsObservation(AgentInteractionProfile.OBS_USER_UTTERANCE));
        assertTrue(socialProfile.supportsObservation(AgentInteractionProfile.OBS_HUMAN_PRESENCE));
        assertTrue(socialProfile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_GROUPING));
        assertTrue(socialProfile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_CONTEXT));
        assertTrue(socialProfile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE));
        assertFalse(socialProfile.supportsObservation(AgentInteractionProfile.OBS_FACE_EMOTION));
        assertLabPhysicalOutput(socialProfile);

        AgentInteractionProfile facialProfile = new ch.zhaw.prometheus.agentdefs.tdsr.lab.FacialExpressionSensitivity()
                .createAgent()
                .getInteractionProfile();
        assertTrue(facialProfile.supportsObservation(AgentInteractionProfile.OBS_USER_UTTERANCE));
        assertTrue(facialProfile.supportsObservation(AgentInteractionProfile.OBS_FACE_EMOTION));
        assertFalse(facialProfile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_CONTEXT));
        assertLabPhysicalOutput(facialProfile);

        AgentInteractionProfile rpsProfile = new ch.zhaw.prometheus.agentdefs.tdsr.lab.RockScissorPaper()
                .createAgent()
                .getInteractionProfile();
        assertTrue(rpsProfile.supportsObservation(AgentInteractionProfile.OBS_USER_UTTERANCE));
        assertTrue(rpsProfile.supportsObservation(AgentInteractionProfile.OBS_HAND_SIGN));
        assertTrue(rpsProfile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_DISPLAY));
        assertEquals(7, rpsProfile.getSupportedBehaviourModalities().size());

        AgentInteractionProfile roleProfile = new ch.zhaw.prometheus.agentdefs.tdsr.lab.RoleClarificationGuessingGame()
                .createAgent()
                .getInteractionProfile();
        assertTrue(roleProfile.supportsObservation(AgentInteractionProfile.OBS_USER_UTTERANCE));
        assertFalse(roleProfile.supportsObservation(AgentInteractionProfile.OBS_HAND_SIGN));
        assertFalse(roleProfile.supportsObservation(AgentInteractionProfile.OBS_FACE_EMOTION));
        assertLabPhysicalOutput(roleProfile);

        AgentInteractionProfile multimodalProfile = new ch.zhaw.prometheus.agentdefs.tdsr.lab.MultimodalBehaviour()
                .createAgent()
                .getInteractionProfile();
        assertTrue(multimodalProfile.supportsObservation(AgentInteractionProfile.OBS_USER_UTTERANCE));
        assertTrue(multimodalProfile.supportsObservation(AgentInteractionProfile.OBS_FACE_EMOTION));
        assertTrue(multimodalProfile.supportsObservation(AgentInteractionProfile.OBS_HUMAN_PRESENCE));
        assertTrue(multimodalProfile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_GROUPING));
        assertTrue(multimodalProfile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_CONTEXT));
        assertTrue(multimodalProfile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE));
        assertTrue(multimodalProfile.supportsObservation(AgentInteractionProfile.OBS_HAND_SIGN));
        assertTrue(multimodalProfile.supportsObservation(AgentInteractionProfile.OBS_WEATHER_CURRENT));
        assertTrue(multimodalProfile.supportsObservation(AgentInteractionProfile.OBS_WEATHER_FORECAST));
        assertLabPhysicalOutput(multimodalProfile);
    }

    @Test
    void sharedLabPromptsCarrySiraGigiHumourAndSafetyContext() {
        String outerState = ch.zhaw.prometheus.agentdefs.tdsr.lab.TdsrLabPrompts.OUTER_STATE;
        String nonverbal = ch.zhaw.prometheus.agentdefs.tdsr.lab.TdsrLabPrompts.NONVERBAL_PLAN;

        assertContains(outerState, "Answer only in English");
        assertContains(outerState, "ZHAW SIRA Lab");
        assertContains(outerState, "Socially Intelligent and Responsible Agents");
        assertContains(outerState, "PROMETHEUS is one of the SIRA Lab achievements");
        assertContains(outerState, "you travel through Switzerland by car with Frank");
        assertContains(outerState, "learn how to be useful");
        assertContains(outerState, "Answer very briefly: usually one sentence, often only 3-10 words");
        assertContains(outerState, "Use warm micro-humor");
        assertContains(outerState, "Treat sensing events as imperfect lab signals");
        assertFalse(outerState.contains("Hotel Grischa"));
        assertFalse(outerState.contains("care center"));

        assertContains(nonverbal, "\"nonVerbal\"");
        assertContains(nonverbal, "\"facialExpression\"");
        assertContains(nonverbal, "\"gaze\"");
        assertContains(nonverbal, "\"motion\"");
        assertContains(nonverbal, "\"handSign\"");
        assertContains(nonverbal, "Do not output locomotion fields");
        assertContains(nonverbal, "Return exactly one JSON object");
    }

    @Test
    void labAgentPromptsDescribeTheirSensorVocabulary() throws Exception {
        Map<String, String> socialPrompts = stringFields(
                ch.zhaw.prometheus.agentdefs.tdsr.lab.SocialContextSensitivity.class);
        String socialState = socialPrompts.get("PROMPT_STATE");
        assertContains(socialState, "obs.social.context");
        assertContains(socialState, "obs.human.presence");
        assertContains(socialState, "obs.social.grouping");
        assertContains(socialState, "obs.social.situation_change");
        assertContains(socialState, "approaching");
        assertContains(socialState, "receding");
        assertContains(socialState, "attending");
        assertContains(socialState, "not_attending");

        Map<String, String> facialPrompts = stringFields(
                ch.zhaw.prometheus.agentdefs.tdsr.lab.FacialExpressionSensitivity.class);
        String facialState = facialPrompts.get("PROMPT_STATE");
        assertContains(facialState, "obs.emotion.face");
        assertContains(facialState, "dominant emotion");
        assertContains(facialState, "valence");
        assertContains(facialState, "arousal");
        assertContains(facialState, "disgusted");
        assertContains(facialState, "Comment on the signal, not the soul");

        Map<String, String> rpsPrompts = stringFields(
                ch.zhaw.prometheus.agentdefs.tdsr.lab.RockScissorPaper.class);
        String rpsState = rpsPrompts.get("PROMPT_START");
        assertContains(rpsState, "obs.hand.sign");
        assertContains(rpsState, "deterministic");
        assertContains(rpsState, "display output");
        assertContains(rpsState, "Answer only in English");

        Map<String, String> rolePrompts = stringFields(
                ch.zhaw.prometheus.agentdefs.tdsr.lab.RoleClarificationGuessingGame.class);
        String roleState = rolePrompts.get("PROMPT_ROLE_CLARIFICATION_STATE");
        assertContains(roleState, "role clarification");
        assertContains(roleState, "GIGI guesses");
        assertContains(roleState, "User guesses");
        assertContains(roleState, "These roles need to be specified precisely");
        assertContains(rolePrompts.get("PROMPT_ROLE_TO_GIGI_GUESSES"), "GIGI guesses the person's secret item");
        assertContains(rolePrompts.get("PROMPT_ROLE_TO_USER_GUESSES"), "the user guesses GIGI's secret item");

        Map<String, String> multimodalPrompts = stringFields(
                ch.zhaw.prometheus.agentdefs.tdsr.lab.MultimodalBehaviour.class);
        String multimodalState = multimodalPrompts.get("PROMPT_STATE");
        assertContains(multimodalState, "one current BehaviourPlan per generation");
        assertContains(multimodalState, "speech content and rhythm");
        assertContains(multimodalState, "nonverbal gesture");
        assertContains(multimodalState, "facial expression type and intensity");
        assertContains(multimodalState, "gaze direction and focus");
        assertContains(multimodalState, "motion stillness and energy");
        assertContains(multimodalState, "optional hand sign");

        for (DefinitionCase definitionCase : DEFINITIONS) {
            for (Map.Entry<String, String> prompt : stringFields(definitionCase.definitionClass()).entrySet()) {
                assertTrue(prompt.getValue().length() <= MAX_PERSISTED_PROMPT_LENGTH,
                        definitionCase.definitionClass().getSimpleName() + "." + prompt.getKey());
            }
        }
    }

    @Test
    void sensingEventsHaveSelfTransitionsAndUseSharedNonverbalPrompt() throws Exception {
        Agent socialAgent = new ch.zhaw.prometheus.agentdefs.tdsr.lab.SocialContextSensitivity().createAgent();
        State socialState = innerState(socialAgent);
        assertTransitionDecision(socialState, Event.TYPE_SOCIAL_CONTEXT);
        assertTransitionDecision(socialState, Event.TYPE_SOCIAL_SITUATION_CHANGE);
        assertSharedNonverbalPrompt(socialState);

        Agent facialAgent = new ch.zhaw.prometheus.agentdefs.tdsr.lab.FacialExpressionSensitivity().createAgent();
        State facialState = innerState(facialAgent);
        assertTransitionDecision(facialState, Event.TYPE_FACE_EMOTION);
        assertSharedNonverbalPrompt(facialState);

        Agent multimodalAgent = new ch.zhaw.prometheus.agentdefs.tdsr.lab.MultimodalBehaviour().createAgent();
        assertSharedNonverbalPrompt(innerState(multimodalAgent));
    }

    @Test
    void roleClarificationGuessingGameStartsInClarificationAndCanEnterEitherRoleState() throws Exception {
        Agent agent = new ch.zhaw.prometheus.agentdefs.tdsr.lab.RoleClarificationGuessingGame().createAgent();
        OuterState outerState = assertInstanceOf(OuterState.class, agent.getCurrentState());
        State roleState = outerState.getInnerCurrent();

        assertEquals(List.of(
                "GIGI SIRA Lab context",
                "GIGI SIRA Lab guessing game role clarification"), outerState.getActiveStatePath());
        assertTrue(agent.listStates().contains("GIGI SIRA Lab guessing game - GIGI guesses"));
        assertTrue(agent.listStates().contains("GIGI SIRA Lab guessing game - user guesses"));
        assertTransitionToState(roleState, "GIGI SIRA Lab guessing game - GIGI guesses");
        assertTransitionToState(roleState, "GIGI SIRA Lab guessing game - user guesses");
        assertSharedNonverbalPrompt(roleState);
    }

    @Test
    void labRpsUsesEnglishDeterministicRevealAndResultPolicies() {
        Agent agent = new ch.zhaw.prometheus.agentdefs.tdsr.lab.RockScissorPaper().createAgent();
        RecordingGateway gateway = new RecordingGateway();
        PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway);

        agent.start(runtime);
        Event reveal = agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "ready"),
                runtime);
        BehaviourPlan revealPlan = BehaviourPlan.fromJson(reveal.getPayload());

        assertEquals("Rock, scissor, paper", revealPlan.getSpeech());
        assertEquals("rock", revealPlan.getMotion().getAsJsonObject().get("handSign").getAsString());
        assertEquals("Rock, Scissor, Paper",
                revealPlan.getDisplay().getAsJsonObject().get("title").getAsString());

        Event result = agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, Event.ACTOR_USER,
                "{\"sign\":\"scissor\",\"confidence\":1.0}"), runtime);
        BehaviourPlan resultPlan = BehaviourPlan.fromJson(result.getPayload());

        assertContains(resultPlan.getSpeech(), "I win");
        assertContains(resultPlan.getSpeech(), "rock beats scissor");
        assertFalse(resultPlan.getSpeech().contains("Ich"));
        assertEquals("rock beats scissor",
                resultPlan.getDisplay().getAsJsonObject().get("reason").getAsString());
    }

    private static void assertLabPhysicalOutput(AgentInteractionProfile profile) {
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_SPEECH));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE));
        assertTrue(profile.supportsBehaviourModality(
                AgentInteractionProfile.MODALITY_NONVERBAL_FACIAL_EXPRESSION));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_GAZE));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_MOTION));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN));
        assertFalse(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_DISPLAY));
        assertEquals(6, profile.getSupportedBehaviourModalities().size());
    }

    private static void assertSharedNonverbalPrompt(State state) throws Exception {
        PromptPolicy policy = assertInstanceOf(PromptPolicy.class, policy(state));
        assertEquals(ch.zhaw.prometheus.agentdefs.tdsr.lab.TdsrLabPrompts.NONVERBAL_PLAN,
                policy.getNonVerbalPlanPrompt());
        assertEquals(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT, policy.getNonVerbalGesturePrompt());
    }

    private static State innerState(Agent agent) {
        OuterState outerState = assertInstanceOf(OuterState.class, agent.getCurrentState());
        return outerState.getInnerCurrent();
    }

    private static void assertTransitionDecision(State state, String eventType) throws Exception {
        assertTrue(transitions(state).stream()
                .flatMap(transition -> transition.getDecisions().stream())
                .anyMatch(decision -> decision.toString().contains(eventType)),
                "missing transition decision for " + eventType);
    }

    private static void assertTransitionToState(State state, String stateName) throws Exception {
        assertTrue(transitions(state).stream()
                .map(transition -> transition.getSubsequentState().getName())
                .anyMatch(stateName::equals),
                "missing transition to " + stateName);
    }

    private static Object policy(State state) throws Exception {
        Field policyField = State.class.getDeclaredField("policy");
        policyField.setAccessible(true);
        return policyField.get(state);
    }

    @SuppressWarnings("unchecked")
    private static List<Transition> transitions(State state) throws Exception {
        Field transitionsField = State.class.getDeclaredField("transitions");
        transitionsField.setAccessible(true);
        return (List<Transition>) transitionsField.get(state);
    }

    private static Map<String, String> stringFields(Class<?> definitionClass) throws Exception {
        java.util.LinkedHashMap<String, String> prompts = new java.util.LinkedHashMap<>();
        for (Field field : definitionClass.getDeclaredFields()) {
            if (!field.getName().startsWith("PROMPT_")) {
                continue;
            }
            field.setAccessible(true);
            prompts.put(field.getName(), (String) field.get(null));
        }
        return prompts;
    }

    private static void assertContains(String value, String expected) {
        assertTrue(value.contains(expected), "missing: " + expected);
    }

    private record DefinitionCase(AgentDefinition definition, Class<?> definitionClass, String key) {
    }

    private static final class RecordingGateway implements LanguageModelGateway {
        @Override
        public String complete(List<PromptMessage> messages) {
            String prompt = join(messages);
            if (prompt.contains("Produce STRICT JSON only for GIGI's nonverbal behaviour")) {
                return """
                        {
                          "nonVerbal": {
                            "gesture": "NONE",
                            "facialExpression": {"type": "warmNeutral", "intensity": 0.2},
                            "gaze": {"direction": "toward_user", "focus": "person"},
                            "motion": {"stillness": 0.8, "energy": 0.2}
                          },
                          "motion": null
                        }
                        """;
            }
            return "Hello, I am GIGI. The lab game is ready.";
        }

        @Override
        public boolean decide(List<PromptMessage> messages) {
            String prompt = join(messages);
            return prompt.contains("ready to start a round of rock-scissor-paper");
        }

        @Override
        public JsonElement extract(List<PromptMessage> messages) {
            return JsonNull.INSTANCE;
        }

        @Override
        public JsonElement summarise(List<PromptMessage> messages) {
            return JsonNull.INSTANCE;
        }

        @Override
        public String summariseOffline(List<PromptMessage> messages) {
            return "";
        }

        private static String join(List<PromptMessage> messages) {
            return messages.stream()
                    .map(PromptMessage::getContent)
                    .reduce("", (left, right) -> left + "\n" + right);
        }
    }
}
