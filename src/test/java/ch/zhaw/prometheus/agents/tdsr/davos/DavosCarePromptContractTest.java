package ch.zhaw.prometheus.agents.tdsr.davos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.OuterState;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class DavosCarePromptContractTest {
    private static final int MAX_PERSISTED_PROMPT_LENGTH = 8000;

    private static final List<DefinitionCase> CARE_DEFINITIONS = List.of(
            new DefinitionCase(
                    new ch.zhaw.prometheus.agentdefs.tdsr.davos.SingleStateTherapyAppointmentReminder(),
                    ch.zhaw.prometheus.agentdefs.tdsr.davos.SingleStateTherapyAppointmentReminder.class,
                    "tdsr.davos.therapy_appointment_reminder",
                    "therapy appointment",
                    List.of("foot-in-the-door", "appointment", "care staff")),
            new DefinitionCase(
                    new ch.zhaw.prometheus.agentdefs.tdsr.davos.TwoStateTherapyAppointmentReminder(),
                    ch.zhaw.prometheus.agentdefs.tdsr.davos.TwoStateTherapyAppointmentReminder.class,
                    "tdsr.davos.therapy_appointment_reminder_intro",
                    "therapy appointment",
                    List.of("foot-in-the-door", "appointment", "care staff")),
            new DefinitionCase(
                    new ch.zhaw.prometheus.agentdefs.tdsr.davos.SingleStateGuessingGame(),
                    ch.zhaw.prometheus.agentdefs.tdsr.davos.SingleStateGuessingGame.class,
                    "tdsr.davos.guessing_game",
                    "guessing game",
                    List.of("final guess", "yes/no", "not a quiz")),
            new DefinitionCase(
                    new ch.zhaw.prometheus.agentdefs.tdsr.davos.SingleStateGuessingGameUserGuess(),
                    ch.zhaw.prometheus.agentdefs.tdsr.davos.SingleStateGuessingGameUserGuess.class,
                    "tdsr.davos.guessing_game_user_guess",
                    "user guessing game",
                    List.of("secret item", "yes/no questions", "wrong guess")),
            new DefinitionCase(
                    new ch.zhaw.prometheus.agentdefs.tdsr.davos.SingleStateSmartGoalCoaching(),
                    ch.zhaw.prometheus.agentdefs.tdsr.davos.SingleStateSmartGoalCoaching.class,
                    "tdsr.davos.smart_goal_coaching",
                    "SMART goal coaching",
                    List.of("SMART goal", "first step", "wellbeing wishes")));
    private static final DefinitionCase SUMMIT_HOTEL_DEFINITION = new DefinitionCase(
            new ch.zhaw.prometheus.agentdefs.tdsr.davos.SingleStateSummitHotelConversation(),
            ch.zhaw.prometheus.agentdefs.tdsr.davos.SingleStateSummitHotelConversation.class,
            "tdsr.davos.summit_hotel_conversation",
            "freie deutschsprachige Demo-Begegnung",
            List.of("Davos Tech Summit", "Hotel Grischa", "Mountainbike-Socken"));
    private static final List<DefinitionCase> ALL_DEFINITIONS = Stream
            .concat(CARE_DEFINITIONS.stream(), Stream.of(SUMMIT_HOTEL_DEFINITION))
            .toList();

    @Test
    void definitionsUseDavosKeysPackagePathAndEnglishRealtimeLanguage() {
        for (DefinitionCase definitionCase : CARE_DEFINITIONS) {
            AgentDefinition definition = definitionCase.definition();
            Agent agent = definition.createAgent();

            assertEquals(definitionCase.key(), definition.key());
            assertEquals(List.of("tdsr", "davos"), definition.packagePath());
            assertEquals(AgentDefinition.LANGUAGE_ENGLISH, definition.languageCode());
            assertEquals(AgentDefinition.LANGUAGE_ENGLISH, agent.getLanguageCode());
            assertTrue(agent.getName().contains("Davos"));
            assertTrue(agent.getDescription().contains("English"));
            assertFalse(agent.getName().contains("Pflegezentrum"));
            assertFalse(agent.getDescription().contains("Pflegezentrum"));
        }
    }

    @Test
    void davosProfilesExposeWeatherSocialContextAndPhysicalBehaviourOutput() {
        for (DefinitionCase definitionCase : ALL_DEFINITIONS) {
            AgentInteractionProfile profile = definitionCase.definition().createAgent().getInteractionProfile();

            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_USER_UTTERANCE));
            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_WEATHER_CURRENT));
            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_WEATHER_FORECAST));
            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_HUMAN_PRESENCE));
            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_GROUPING));
            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE));
            assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_SPEECH));
            assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE));
            assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_FACIAL_EXPRESSION));
            assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_GAZE));
            assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_MOTION));
            assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN));
            assertFalse(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_DISPLAY));
            assertEquals(6, profile.getSupportedBehaviourModalities().size());
        }
    }

    @Test
    void guessingGameDisplayNamesMakeTheGuesserExplicit() {
        Agent agentGuesses = new ch.zhaw.prometheus.agentdefs.tdsr.davos.SingleStateGuessingGame().createAgent();
        Agent userGuesses = new ch.zhaw.prometheus.agentdefs.tdsr.davos.SingleStateGuessingGameUserGuess()
                .createAgent();

        assertEquals("GIGI Davos - Agent Is Guessing", agentGuesses.getName());
        assertContains(agentGuesses.getDescription(), "GIGI guesses the older adult's item");

        assertEquals("GIGI Davos - User Is Guessing", userGuesses.getName());
        assertContains(userGuesses.getDescription(), "older adult guesses GIGI's item");
    }

    @Test
    void summitHotelConversationUsesGermanRealtimeLanguageAndPublicDavosContext() {
        AgentDefinition definition = SUMMIT_HOTEL_DEFINITION.definition();
        Agent agent = definition.createAgent();

        assertEquals("tdsr.davos.summit_hotel_conversation", definition.key());
        assertEquals(List.of("tdsr", "davos"), definition.packagePath());
        assertEquals(AgentDefinition.LANGUAGE_GERMAN, definition.languageCode());
        assertEquals(AgentDefinition.LANGUAGE_GERMAN, agent.getLanguageCode());
        assertEquals("GIGI Davos - Summit Hotel Conversation", agent.getName());
        assertContains(agent.getDescription(), "German");
        assertContains(agent.getDescription(), "Hotel Grischa");
        assertFalse(agent.getName().contains("Care"));
        assertFalse(agent.getDescription().contains("care-center"));
    }

    @Test
    void promptsKeepOriginalCareTasksButUseEnglishDavosContext() throws Exception {
        Map<String, String> sharedPrompts = stringFields(ch.zhaw.prometheus.agentdefs.tdsr.davos.DavosCarePrompts.class);
        String outerState = sharedPrompts.get("OUTER_STATE");

        assertContains(outerState, "care center in Davos");
        assertContains(outerState, "Answer only in English");
        assertContains(outerState, "Answer very briefly: usually one sentence, often only 3-10 words");
        assertContains(outerState, "Use warm micro-humor regularly in ordinary moments");
        assertContains(outerState, "normally include one small");
        assertContains(outerState, "Do not accept a first \"no\", \"maybe\", \"not in the mood\"");
        assertContains(outerState, "first understand the reason");
        assertContains(outerState, "choose one reason-sensitive");
        assertContains(outerState, "Small steps are task-specific bridges");
        assertContains(outerState, "not automatic final successes");
        assertContains(outerState, "Your name GIGI is roughly pronounced");
        assertContains(outerState, "obs.weather.current");
        assertContains(outerState, "obs.weather.forecast");
        assertContains(outerState, "obs.human.presence");
        assertContains(outerState, "obs.social.grouping");
        assertContains(outerState, "obs.social.situation_change");

        for (DefinitionCase definitionCase : CARE_DEFINITIONS) {
            Map<String, String> prompts = stringFields(definitionCase.definitionClass());
            String allPrompts = String.join("\n", prompts.values()) + "\n" + String.join("\n", sharedPrompts.values());

            assertContains(prompts.get("PROMPT_STATE"), definitionCase.taskAnchor());
            assertContains(prompts.get("PROMPT_STATE"), "shared resistance protocol");
            for (String anchor : definitionCase.intentAnchors()) {
                assertContains(prompts.get("PROMPT_STATE"), anchor);
            }
            assertContains(prompts.get("PROMPT_FINAL"), "Answer only in English");
            assertContains(prompts.get("PROMPT_OUTCOME_EXTRACTION"), "\"flow_type\": \"single_state\"");
            assertTrue(prompts.get("PROMPT_OUTCOME_EXTRACTION").contains("\"interaction_type\": \"davos_"));

            assertFalse(allPrompts.contains("Antworte"));
            assertFalse(allPrompts.contains("Sprich ausnahmslos Deutsch"));
            assertFalse(allPrompts.contains("Pflegezentrum"));
            assertFalse(allPrompts.contains("Nutzer"));

            for (Map.Entry<String, String> prompt : prompts.entrySet()) {
                assertTrue(prompt.getValue().length() <= MAX_PERSISTED_PROMPT_LENGTH,
                        definitionCase.definitionClass().getSimpleName() + "." + prompt.getKey());
            }
        }
    }

    @Test
    void summitHotelConversationPromptUsesGermanSummitHotelAndMerchContext() throws Exception {
        Map<String, String> sharedPrompts = stringFields(ch.zhaw.prometheus.agentdefs.tdsr.davos.DavosGeneralPrompts.class);
        Map<String, String> prompts = stringFields(SUMMIT_HOTEL_DEFINITION.definitionClass());
        String outerState = sharedPrompts.get("OUTER_STATE");
        String statePrompt = prompts.get("PROMPT_STATE");
        String allPrompts = String.join("\n", prompts.values()) + "\n" + String.join("\n", sharedPrompts.values());

        assertContains(outerState, "Antworte nur auf Deutsch");
        assertContains(outerState, "Hotel Grischa in Davos");
        assertContains(outerState, "wie Roboter Menschen sinnvoll unterstützen können");
        assertContains(outerState, "obs.weather.current");
        assertContains(outerState, "obs.social.situation_change");

        assertContains(statePrompt, "freie deutschsprachige Demo-Begegnung");
        assertContains(statePrompt, "keinen festen Use Case");
        assertContains(statePrompt, "Davos Tech Summit");
        assertContains(statePrompt, "1. bis 4. Juli 2026");
        assertContains(statePrompt, "Physical AI");
        assertContains(statePrompt, "Hotel Grischa");
        assertContains(statePrompt, "Talstrasse 3");
        assertContains(statePrompt, "Mountainbike-Socken");
        assertContains(statePrompt, "Trinkflaschen");
        assertContains(statePrompt, "Kerzen");
        assertContains(statePrompt, "Tücher");
        assertContains(statePrompt, "Rezeption");
        assertContains(statePrompt, "Vertrauen schaffen würde");
        assertContains(statePrompt, "Zusammenarbeit");
        assertContains(prompts.get("PROMPT_STATE_STARTER"), "wie Roboter Menschen nützlich sein können");
        assertContains(prompts.get("PROMPT_OUTCOME_EXTRACTION"),
                "\"interaction_type\": \"davos_summit_hotel_conversation\"");
        assertContains(prompts.get("PROMPT_FINAL"), "Antworte nur auf Deutsch");

        assertFalse(allPrompts.contains("care center"));
        assertFalse(allPrompts.contains("older adult"));
        assertFalse(allPrompts.contains("care-center"));
        assertFalse(allPrompts.contains("Pflegezentrum"));
        assertFalse(allPrompts.contains("Answer only in English"));

        for (Map.Entry<String, String> prompt : prompts.entrySet()) {
            assertTrue(prompt.getValue().length() <= MAX_PERSISTED_PROMPT_LENGTH,
                    SUMMIT_HOTEL_DEFINITION.definitionClass().getSimpleName() + "." + prompt.getKey());
        }
    }

    @Test
    void therapyReminderKeepsElderlyCarePersuasionStrategyAndUsesSmallStepsAsMomentum() throws Exception {
        Map<String, String> prompts = stringFields(
                ch.zhaw.prometheus.agentdefs.tdsr.davos.SingleStateTherapyAppointmentReminder.class);
        String statePrompt = prompts.get("PROMPT_STATE");
        String toFinalPrompt = prompts.get("PROMPT_TO_FINAL");
        String outcomePrompt = prompts.get("PROMPT_OUTCOME_EXTRACTION");

        assertContains(statePrompt, "Gently persuade");
        assertContains(statePrompt, "Appointment therapy context:");
        assertContains(statePrompt, "${therapyAppointmentContext}");
        assertContains(statePrompt, "preselected for this interaction");
        assertContains(statePrompt, "Do not change to another therapy type");
        assertContains(statePrompt, "Do not claim to access live medical records");
        assertContains(statePrompt, "Therapy-specific small-step rule");
        assertContains(statePrompt, "do not immediately offer a smaller deal");
        assertContains(statePrompt, "Apply the shared resistance protocol");
        assertContains(statePrompt, "first understand the reason");
        assertContains(statePrompt, "then choose one reason-sensitive");
        assertContains(statePrompt, "then offer a smaller foot-in-the-door step only if");
        assertContains(statePrompt, "Do not use reduced participation as the first strategy");
        assertContains(statePrompt, "Do not repeat the same strategy");
        assertContains(statePrompt, "After a mini-step yes, do not close");
        assertContains(statePrompt, "Do not end the interaction just to stay brief");

        assertContains(toFinalPrompt, "attendance-equivalent plan such as going there now with support");
        assertContains(toFinalPrompt, "at least three");
        assertContains(toFinalPrompt, "intermediate mini-steps such as talking about it");
        assertContains(toFinalPrompt, "assistant has already used that step to invite attendance");

        assertContains(outcomePrompt, "completed is true only when the person agreed to attend");
        assertContains(outcomePrompt, "completed is false for a standalone mini-step");

        Agent agent = new ch.zhaw.prometheus.agentdefs.tdsr.davos.SingleStateTherapyAppointmentReminder()
                .createAgent();
        Map<String, com.google.gson.JsonElement> storage = agent.getStorage();
        assertTrue(storage.containsKey("therapyAppointmentContext"));
        String selectedType = storage.get("therapyAppointmentContext").getAsJsonObject().get("type").getAsString();
        assertTrue(List.of(
                "physiotherapy",
                "occupational_therapy",
                "activation").contains(selectedType), selectedType);
    }

    @Test
    void therapyReminderWithIntroStartsUseCaseWithStateScopedHistory() {
        Agent agent = new ch.zhaw.prometheus.agentdefs.tdsr.davos.TwoStateTherapyAppointmentReminder()
                .createAgent();
        RecordingGateway gateway = new RecordingGateway();
        PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway);

        OuterState outerState = assertInstanceOf(OuterState.class, agent.getCurrentState());
        assertEquals(List.of(
                "GIGI Davos care context",
                "GIGI Davos therapy reminder introduction"), outerState.getActiveStatePath());
        assertEquals("GIGI Davos - Therapy Reminder (w. Intro)", agent.getName());
        assertTrue(agent.listStates().contains("GIGI Davos therapy reminder use case"));

        agent.start(runtime);
        agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "move on"), runtime);

        assertEquals(List.of(
                "GIGI Davos care context",
                "GIGI Davos therapy reminder use case"), outerState.getActiveStatePath());
        assertEquals(2, agent.getEventsForState("GIGI Davos therapy reminder introduction").size());
        assertEquals(1, agent.getEventsForState("GIGI Davos therapy reminder use case").size());
        assertTrue(agent.getEventsForState("GIGI Davos therapy reminder use case").get(0).getPayload()
                .contains("Hello, I am GIGI"));

        assertTrue(gateway.therapyStartPrompt().contains("Task: Gently persuade"));
        assertFalse(gateway.therapyStartPrompt().contains("Introduce GIGI before an optional"));
        assertFalse(gateway.therapyStartPrompt().contains("move on"));
    }

    @Test
    void therapyReminderWithIntroPromptSeparatesIntroductionFromUseCase() throws Exception {
        Map<String, String> prompts = stringFields(
                ch.zhaw.prometheus.agentdefs.tdsr.davos.TwoStateTherapyAppointmentReminder.class);

        assertContains(prompts.get("PROMPT_INTRO_STATE"), "Introduce GIGI before an optional");
        assertContains(prompts.get("PROMPT_INTRO_STATE"), "ask for a clear confirmation");
        assertContains(prompts.get("PROMPT_INTRO_STATE"), "Do not announce that the state is changing");
        assertContains(prompts.get("PROMPT_INTRO_STATE_STARTER"), "I am not here to replace care");
        assertContains(prompts.get("PROMPT_INTRO_TO_THERAPY_REMINDER"), "Return true only if the person clearly wants");
        assertContains(prompts.get("PROMPT_INTRO_TO_THERAPY_REMINDER"), "Return false for a bare \"yes\"");
        assertContains(prompts.get("PROMPT_STATE_STARTER"), "Hello, I am GIGI");
    }

    @Test
    void socialSituationChangesHavePromptGatedSelfTransition() throws Exception {
        for (DefinitionCase definitionCase : ALL_DEFINITIONS) {
            Agent agent = definitionCase.definition().createAgent();
            OuterState outerState = assertInstanceOf(OuterState.class, agent.getCurrentState());
            State interactionState = therapyInteractionState(definitionCase, outerState.getInnerCurrent());

            assertTrue(transitions(interactionState).stream()
                    .flatMap(transition -> transition.getDecisions().stream())
                    .anyMatch(decision -> decision.toString().contains(Event.TYPE_SOCIAL_SITUATION_CHANGE)),
                    definitionCase.key());
        }
    }

    @Test
    void davosAgentsUseSupportedGigiPhysicalBehaviourContract() throws Exception {
        Map<String, String> sharedPrompts = stringFields(ch.zhaw.prometheus.agentdefs.tdsr.davos.DavosCarePrompts.class);
        String prompt = sharedPrompts.get("NONVERBAL_PLAN");
        Map<String, String> generalPrompts = stringFields(
                ch.zhaw.prometheus.agentdefs.tdsr.davos.DavosGeneralPrompts.class);
        String generalPrompt = generalPrompts.get("NONVERBAL_PLAN");

        assertContains(prompt, "\"nonVerbal\"");
        assertContains(prompt, "\"gesture\"");
        assertContains(prompt, "\"facialExpression\"");
        assertContains(prompt, "\"gaze\"");
        assertContains(prompt, "\"motion\"");
        assertContains(prompt, "\"handSign\"");
        assertContains(prompt, "stillness");
        assertContains(prompt, "energy");
        for (String safeGesture : List.of("OPEN_QUESTION", "EXPLAIN", "UNCERTAIN", "ACKNOWLEDGE", "POLITE", "NONE")) {
            assertContains(prompt, safeGesture);
        }
        for (String handSign : List.of("rock", "scissor", "paper")) {
            assertContains(prompt, handSign);
        }
        for (String robotServerId : List.of(
                "open_question_gesture",
                "explanatory_sweep_gesture",
                "uncertainty_shrug_gesture",
                "acknowledgement_close_hands_gesture",
                "polite_apology_gesture")) {
            assertContains(prompt, robotServerId);
        }
        assertContains(prompt, "Do not output robot-server command IDs");
        assertContains(prompt, "Do not output locomotion fields");
        assertContains(prompt, "motion.handSign");
        assertContains(prompt, "motion.move");
        assertContains(prompt, "motion.turn");
        assertContains(prompt, "Do not output display fields");

        assertContains(generalPrompt, "\"nonVerbal\"");
        assertContains(generalPrompt, "\"gesture\"");
        assertContains(generalPrompt, "\"facialExpression\"");
        assertContains(generalPrompt, "\"gaze\"");
        assertContains(generalPrompt, "\"motion\"");
        assertContains(generalPrompt, "\"handSign\"");
        assertContains(generalPrompt, "public hotel or summit demonstration");
        assertContains(generalPrompt, "Do not output robot-server command IDs");
        assertFalse(generalPrompt.contains("older adult"));
        assertFalse(generalPrompt.contains("care-center"));

        for (DefinitionCase definitionCase : CARE_DEFINITIONS) {
            Agent agent = definitionCase.definition().createAgent();
            OuterState outerState = assertInstanceOf(OuterState.class, agent.getCurrentState());
            PromptPolicy interactionPolicy = assertInstanceOf(PromptPolicy.class, policy(outerState.getInnerCurrent()));

            assertEquals(prompt, interactionPolicy.getNonVerbalPlanPrompt(), definitionCase.key());
            assertEquals(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT,
                    interactionPolicy.getNonVerbalGesturePrompt(), definitionCase.key());
        }

        Agent summitAgent = SUMMIT_HOTEL_DEFINITION.definition().createAgent();
        OuterState summitOuterState = assertInstanceOf(OuterState.class, summitAgent.getCurrentState());
        PromptPolicy summitInteractionPolicy = assertInstanceOf(PromptPolicy.class,
                policy(summitOuterState.getInnerCurrent()));
        assertEquals(generalPrompt, summitInteractionPolicy.getNonVerbalPlanPrompt());
        assertEquals(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT,
                summitInteractionPolicy.getNonVerbalGesturePrompt());
    }

    private static void assertContains(String value, String expected) {
        assertTrue(value.contains(expected), "missing: " + expected);
    }

    private static Map<String, String> stringFields(Class<?> definitionClass) throws Exception {
        java.util.LinkedHashMap<String, String> prompts = new java.util.LinkedHashMap<>();
        for (Field field : definitionClass.getDeclaredFields()) {
            if (!String.class.equals(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            prompts.put(field.getName(), (String) field.get(null));
        }
        return prompts;
    }

    private static Object policy(State state) throws Exception {
        Field policyField = State.class.getDeclaredField("policy");
        policyField.setAccessible(true);
        return policyField.get(state);
    }

    private static State therapyInteractionState(DefinitionCase definitionCase, State initialInnerState)
            throws Exception {
        if (!ch.zhaw.prometheus.agentdefs.tdsr.davos.TwoStateTherapyAppointmentReminder.KEY
                .equals(definitionCase.key())) {
            return initialInnerState;
        }
        return transitions(initialInnerState).stream()
                .map(Transition::getSubsequentState)
                .filter(state -> "GIGI Davos therapy reminder use case".equals(state.getName()))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Transition> transitions(State state) throws Exception {
        Field transitionsField = State.class.getDeclaredField("transitions");
        transitionsField.setAccessible(true);
        return (List<Transition>) transitionsField.get(state);
    }

    private record DefinitionCase(AgentDefinition definition, Class<?> definitionClass, String key,
            String taskAnchor, List<String> intentAnchors) {
    }

    private static final class RecordingGateway implements LanguageModelGateway {
        private String therapyStartPrompt = "";

        @Override
        public String complete(List<PromptMessage> messages) {
            String prompt = join(messages);
            if (prompt.contains("Produce STRICT JSON only for GIGI's nonverbal behaviour")) {
                return """
                        {
                          "nonVerbal": {
                            "gesture": "NONE",
                            "facialExpression": {"type": "warmNeutral", "intensity": 0.1},
                            "gaze": {"direction": "toward_user", "focus": "older_adult"},
                            "motion": {"stillness": 1.0, "energy": 0.0}
                          },
                          "motion": null
                        }
                        """;
            }
            if (prompt.contains("Task: Gently persuade")) {
                this.therapyStartPrompt = prompt;
                return "Hello, I am GIGI. I wanted to gently remind you about your upcoming appointment. How does that feel right now?";
            }
            return "Hello, I am GIGI. I am not here to replace care; I am here to make your next step feel a little less lonely.";
        }

        @Override
        public boolean decide(List<PromptMessage> messages) {
            return join(messages).contains("leave the GIGI introduction state");
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

        String therapyStartPrompt() {
            return this.therapyStartPrompt;
        }

        private static String join(List<PromptMessage> messages) {
            return messages.stream()
                    .map(PromptMessage::getContent)
                    .reduce("", (left, right) -> left + "\n" + right);
        }
    }
}
