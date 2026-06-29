package ch.zhaw.prometheus.agents.tdsr.davos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.OuterState;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

class DavosCarePromptContractTest {
    private static final int MAX_PERSISTED_PROMPT_LENGTH = 8000;

    private static final List<DefinitionCase> DEFINITIONS = List.of(
            new DefinitionCase(
                    new ch.zhaw.prometheus.agentdefs.tdsr.davos.SingleStateTherapyAppointmentReminder(),
                    ch.zhaw.prometheus.agentdefs.tdsr.davos.SingleStateTherapyAppointmentReminder.class,
                    "tdsr.davos.therapy_appointment_reminder",
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

    @Test
    void definitionsUseDavosKeysPackagePathAndEnglishRealtimeLanguage() {
        for (DefinitionCase definitionCase : DEFINITIONS) {
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
    void davosProfilesExposeWeatherSocialContextAndSpeechOnlyOutput() {
        for (DefinitionCase definitionCase : DEFINITIONS) {
            AgentInteractionProfile profile = definitionCase.definition().createAgent().getInteractionProfile();

            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_USER_UTTERANCE));
            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_WEATHER_CURRENT));
            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_WEATHER_FORECAST));
            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_HUMAN_PRESENCE));
            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_GROUPING));
            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE));
            assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_SPEECH));
            assertFalse(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_MOTION));
            assertEquals(1, profile.getSupportedBehaviourModalities().size());
        }
    }

    @Test
    void promptsKeepOriginalCareTasksButUseEnglishDavosContext() throws Exception {
        Map<String, String> sharedPrompts = stringFields(ch.zhaw.prometheus.agentdefs.tdsr.davos.DavosCarePrompts.class);
        String outerState = sharedPrompts.get("OUTER_STATE");

        assertContains(outerState, "care center in Davos");
        assertContains(outerState, "Answer only in English");
        assertContains(outerState, "Answer very briefly: usually one sentence, often only 3-10 words");
        assertContains(outerState, "Use warm micro-humor where appropriate");
        assertContains(outerState, "Your name GIGI is roughly pronounced");
        assertContains(outerState, "obs.weather.current");
        assertContains(outerState, "obs.weather.forecast");
        assertContains(outerState, "obs.human.presence");
        assertContains(outerState, "obs.social.grouping");
        assertContains(outerState, "obs.social.situation_change");

        for (DefinitionCase definitionCase : DEFINITIONS) {
            Map<String, String> prompts = stringFields(definitionCase.definitionClass());
            String allPrompts = String.join("\n", prompts.values()) + "\n" + String.join("\n", sharedPrompts.values());

            assertContains(prompts.get("PROMPT_STATE"), definitionCase.taskAnchor());
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
    void socialSituationChangesHavePromptGatedSelfTransition() throws Exception {
        for (DefinitionCase definitionCase : DEFINITIONS) {
            Agent agent = definitionCase.definition().createAgent();
            OuterState outerState = assertInstanceOf(OuterState.class, agent.getCurrentState());
            State interactionState = outerState.getInnerCurrent();

            assertTrue(transitions(interactionState).stream()
                    .flatMap(transition -> transition.getDecisions().stream())
                    .anyMatch(decision -> decision.toString().contains(Event.TYPE_SOCIAL_SITUATION_CHANGE)),
                    definitionCase.key());
        }
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

    @SuppressWarnings("unchecked")
    private static List<Transition> transitions(State state) throws Exception {
        Field transitionsField = State.class.getDeclaredField("transitions");
        transitionsField.setAccessible(true);
        return (List<Transition>) transitionsField.get(state);
    }

    private record DefinitionCase(AgentDefinition definition, Class<?> definitionClass, String key,
            String taskAnchor, List<String> intentAnchors) {
    }
}
