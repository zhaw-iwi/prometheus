package ch.zhaw.prometheus.agents.tdsr.migros;

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
import ch.zhaw.prometheus.model.policy.PromptPolicy;

class TdsrMigrosPromptContractTest {
    private static final int MAX_PERSISTED_PROMPT_LENGTH = 8000;

    private static final DefinitionCase APPENZELL_MENU_PLANNER = new DefinitionCase(
            new ch.zhaw.prometheus.agentdefs.tdsr.migros.AppenzellMenuPlanner(),
            ch.zhaw.prometheus.agentdefs.tdsr.migros.AppenzellMenuPlanner.class,
            "tdsr.migros.appenzell_menu_planner");

    @Test
    void definitionUsesMigrosKeyPackagePathAndGermanRealtimeLanguage() {
        AgentDefinition definition = APPENZELL_MENU_PLANNER.definition();
        Agent agent = definition.createAgent();

        assertEquals("tdsr.migros.appenzell_menu_planner", definition.key());
        assertEquals(List.of("tdsr", "migros"), definition.packagePath());
        assertEquals(AgentDefinition.LANGUAGE_GERMAN, definition.languageCode());
        assertEquals(AgentDefinition.LANGUAGE_GERMAN, agent.getLanguageCode());
        assertEquals("GIGI Migros - Appenzell Menu Planner", agent.getName());
        assertContains(agent.getDescription(), "German");
        assertContains(agent.getDescription(), "triadic customer-employee interaction");
    }

    @Test
    void profileExposesSocialFacialWeatherInputAndFullPhysicalOutput() {
        AgentInteractionProfile profile = APPENZELL_MENU_PLANNER.definition().createAgent().getInteractionProfile();

        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_USER_UTTERANCE));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_FACE_EMOTION));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_HUMAN_PRESENCE));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_GROUPING));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_CONTEXT));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_WEATHER_CURRENT));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_WEATHER_FORECAST));
        assertEquals(8, profile.getSupportedObservations().size());

        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_SPEECH));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_FACIAL_EXPRESSION));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_GAZE));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_MOTION));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN));
        assertFalse(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_DISPLAY));
        assertEquals(6, profile.getSupportedBehaviourModalities().size());

        assertTrue(profile.getProfileTags().contains("demo.gigi.tdsr"));
        assertTrue(profile.getProfileTags().contains("demo.gigi.migros"));
        assertTrue(profile.getProfileTags().contains("demo.gigi.appenzell"));
        assertTrue(profile.getProfileTags().contains("demo.gigi.triadic_service"));
    }

    @Test
    void sharedMigrosPromptsCarryTourPersonaStyleAndSensingGuardrails() throws Exception {
        Map<String, String> sharedPrompts = stringFields(
                ch.zhaw.prometheus.agentdefs.tdsr.migros.TdsrMigrosPrompts.class);
        String outerState = sharedPrompts.get("OUTER_STATE");
        String nonverbal = sharedPrompts.get("NONVERBAL_PLAN");

        assertContains(outerState, "Tour de Suisse Robotique");
        assertContains(outerState, "Migros-Filiale in Appenzell");
        assertContains(outerState, "Du ersetzt keine Mitarbeitenden");
        assertContains(outerState, "menschlichen Kontakt staerkt");
        assertContains(outerState, "Antworte nur auf Deutsch");
        assertContains(outerState, "Meistens ein Satz");
        assertContains(outerState, "Nicht jede Antwort mit einer Frage beenden");
        assertContains(outerState, "obs.emotion.face");
        assertContains(outerState, "obs.social.context");
        assertContains(outerState, "obs.weather.current");
        assertContains(outerState, "Kommentiere Wetter nicht proaktiv");
        assertContains(outerState, "Behaupte nicht, dass du Wetter selbst spuerst");
        assertContains(outerState, "Erfinde keine Live-Verfuegbarkeit");
        assertFalse(outerState.contains("SIRA Lab"));
        assertFalse(outerState.contains("Hotel Grischa"));

        assertContains(nonverbal, "\"nonVerbal\"");
        assertContains(nonverbal, "\"facialExpression\"");
        assertContains(nonverbal, "\"gaze\"");
        assertContains(nonverbal, "\"motion\"");
        assertContains(nonverbal, "\"handSign\"");
        assertContains(nonverbal, "Migros employee adds trust");
        assertContains(nonverbal, "Do not output locomotion fields");
        assertContains(nonverbal, "Do not output display fields");
    }

    @Test
    void menuPlannerPromptGroundsTriadicMigrosAppenzellSceneWithoutScriptingIt() throws Exception {
        Map<String, String> prompts = stringFields(APPENZELL_MENU_PLANNER.definitionClass());
        String statePrompt = prompts.get("PROMPT_STATE");

        assertContains(statePrompt, "In-Store-Beratung");
        assertContains(statePrompt, "Menuplanner");
        assertContains(statePrompt, "Migros-Filiale");
        assertContains(statePrompt, "Appenzell");
        assertContains(statePrompt, "aeltere Kundin");
        assertContains(statePrompt, "kommt vom Sport");
        assertContains(statePrompt, "Protein-Drink");
        assertContains(statePrompt, "Bio-Linsensalat");
        assertContains(statePrompt, "Tomaten");
        assertContains(statePrompt, "Mostbroeckli");
        assertContains(statePrompt, "Pantli");
        assertContains(statePrompt, "Triadische Interaktion");
        assertContains(statePrompt, "Migros-Mitarbeitende");
        assertContains(statePrompt, "menschliches Vertrauen");
        assertContains(statePrompt, "nicht als Skript");
        assertContains(statePrompt, "Tu nie so, als haette eine Mitarbeitende etwas bestaetigt");
        assertContains(statePrompt, "Keine Allergene");
        assertContains(statePrompt, "Keine medizinische");
        assertContains(prompts.get("PROMPT_OUTCOME_EXTRACTION"),
                "\"interaction_type\": \"migros_appenzell_menu_planner\"");
        assertContains(prompts.get("PROMPT_FINAL"), "Antworte nur auf Deutsch");

        for (Map.Entry<String, String> prompt : prompts.entrySet()) {
            assertTrue(prompt.getValue().length() <= MAX_PERSISTED_PROMPT_LENGTH,
                    APPENZELL_MENU_PLANNER.definitionClass().getSimpleName() + "." + prompt.getKey());
        }
    }

    @Test
    void sensingEventsHaveSelfTransitionsAndUseSharedNonverbalPrompt() throws Exception {
        Agent agent = APPENZELL_MENU_PLANNER.definition().createAgent();
        OuterState outerState = assertInstanceOf(OuterState.class, agent.getCurrentState());
        State interactionState = outerState.getInnerCurrent();

        assertEquals(List.of(
                "GIGI Migros Appenzell context",
                "GIGI Migros Appenzell menu planner"), outerState.getActiveStatePath());
        assertTransitionDecision(interactionState, Event.TYPE_FACE_EMOTION);
        assertTransitionDecision(interactionState, Event.TYPE_SOCIAL_CONTEXT);
        assertTransitionDecision(interactionState, Event.TYPE_SOCIAL_SITUATION_CHANGE);

        PromptPolicy policy = assertInstanceOf(PromptPolicy.class, policy(interactionState));
        assertEquals(ch.zhaw.prometheus.agentdefs.tdsr.migros.TdsrMigrosPrompts.NONVERBAL_PLAN,
                policy.getNonVerbalPlanPrompt());
        assertEquals(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT, policy.getNonVerbalGesturePrompt());
    }

    private static void assertTransitionDecision(State state, String eventType) throws Exception {
        assertTrue(transitions(state).stream()
                .flatMap(transition -> transition.getDecisions().stream())
                .anyMatch(decision -> decision.toString().contains(eventType)),
                "missing transition decision for " + eventType);
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
            if (!String.class.equals(field.getType())) {
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
}
