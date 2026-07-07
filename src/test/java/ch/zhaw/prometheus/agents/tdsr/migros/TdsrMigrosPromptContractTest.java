package ch.zhaw.prometheus.agents.tdsr.migros;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private static final int MAX_GENERATION_STATE_PROMPT_LENGTH = 2600;

    private static final DefinitionCase APPENZELL_GENERAL = new DefinitionCase(
            new ch.zhaw.prometheus.agentdefs.tdsr.migros.AppenzellGeneral(),
            ch.zhaw.prometheus.agentdefs.tdsr.migros.AppenzellGeneral.class,
            "tdsr.migros.appenzell_general");
    private static final DefinitionCase APPENZELL_SCENE_2_MENU_PLANNER = new DefinitionCase(
            new ch.zhaw.prometheus.agentdefs.tdsr.migros.AppenzellScene2MenuPlanner(),
            ch.zhaw.prometheus.agentdefs.tdsr.migros.AppenzellScene2MenuPlanner.class,
            "tdsr.migros.appenzell_scene_2_menu_planner");
    private static final DefinitionCase APPENZELL_SCENE_3_CHECKOUT_REFLECTION = new DefinitionCase(
            new ch.zhaw.prometheus.agentdefs.tdsr.migros.AppenzellScene3CheckoutReflection(),
            ch.zhaw.prometheus.agentdefs.tdsr.migros.AppenzellScene3CheckoutReflection.class,
            "tdsr.migros.appenzell_scene_3_checkout_reflection");
    private static final List<DefinitionCase> SCENE_DEFINITIONS = List.of(
            APPENZELL_SCENE_2_MENU_PLANNER,
            APPENZELL_SCENE_3_CHECKOUT_REFLECTION);

    @Test
    void generalDefinitionUsesMigrosKeyPackagePathAndGermanRealtimeLanguage() {
        AgentDefinition definition = APPENZELL_GENERAL.definition();
        Agent agent = definition.createAgent();

        assertEquals("tdsr.migros.appenzell_general", definition.key());
        assertEquals(List.of("tdsr", "migros"), definition.packagePath());
        assertEquals(AgentDefinition.LANGUAGE_GERMAN, definition.languageCode());
        assertEquals(AgentDefinition.LANGUAGE_GERMAN, agent.getLanguageCode());
        assertEquals("GIGI Migros - Appenzell General", agent.getName());
        assertContains(agent.getDescription(), "German");
        assertContains(agent.getDescription(), "general station conversation");
        assertContains(agent.getDescription(), "customers and employees");
    }

    @Test
    void sceneDefinitionsUseMigrosKeysPackagePathAndGermanRealtimeLanguage() {
        for (DefinitionCase definitionCase : SCENE_DEFINITIONS) {
            AgentDefinition definition = definitionCase.definition();
            Agent agent = definition.createAgent();

            assertEquals(definitionCase.key(), definition.key());
            assertEquals(List.of("tdsr", "migros"), definition.packagePath());
            assertEquals(AgentDefinition.LANGUAGE_GERMAN, definition.languageCode());
            assertEquals(AgentDefinition.LANGUAGE_GERMAN, agent.getLanguageCode());
            assertContains(agent.getName(), "GIGI Migros - Appenzell Scene");
            assertContains(agent.getDescription(), "German");
            assertContains(agent.getDescription(), "scripted scene");
        }
    }

    @Test
    void generalProfileExposesSocialWeatherInputAndGestureOnlyOutput() {
        AgentInteractionProfile profile = APPENZELL_GENERAL.definition().createAgent().getInteractionProfile();

        assertMigrosSocialWeatherGestureOnlyProfile(profile);

        assertTrue(profile.getProfileTags().contains("demo.gigi.tdsr"));
        assertTrue(profile.getProfileTags().contains("demo.gigi.migros"));
        assertTrue(profile.getProfileTags().contains("demo.gigi.appenzell"));
        assertTrue(profile.getProfileTags().contains("demo.gigi.general_conversation"));
        assertTrue(profile.getProfileTags().contains("demo.gigi.triadic_service"));
        assertFalse(profile.getProfileTags().contains("demo.gigi.scripted_scene"));
    }

    @Test
    void sceneProfilesExposeSocialWeatherInputAndGestureOnlyOutput() {
        for (DefinitionCase definitionCase : SCENE_DEFINITIONS) {
            AgentInteractionProfile profile = definitionCase.definition().createAgent().getInteractionProfile();

            assertMigrosSocialWeatherGestureOnlyProfile(profile);

            assertTrue(profile.getProfileTags().contains("demo.gigi.tdsr"));
            assertTrue(profile.getProfileTags().contains("demo.gigi.migros"));
            assertTrue(profile.getProfileTags().contains("demo.gigi.appenzell"));
            assertTrue(profile.getProfileTags().contains("demo.gigi.scripted_scene"));
            assertTrue(profile.getProfileTags().contains("demo.gigi.triadic_service"));
        }
    }

    @Test
    void sharedStationPromptKeepsMigrosPersonaWithCompactSensingAndSafetyContext() {
        String stationOuterState = ch.zhaw.prometheus.agentdefs.tdsr.migros.TdsrMigrosPrompts.STATION_OUTER_STATE;

        assertContains(stationOuterState, "Tour de Suisse Robotique");
        assertContains(stationOuterState, "Migros-Filiale in Appenzell");
        assertContains(stationOuterState, "Du ersetzt keine Mitarbeitenden");
        assertContains(stationOuterState, "menschlichen Kontakt staerkt");
        assertContains(stationOuterState, "Antworte nur auf Deutsch");
        assertContains(stationOuterState, "Meistens ein Satz");
        assertContains(stationOuterState, "Nicht jede Antwort mit einer Frage beenden");
        assertContains(stationOuterState, "obs.social.context");
        assertContains(stationOuterState, "obs.social.situation_change");
        assertContains(stationOuterState, "obs.weather.current");
        assertContains(stationOuterState, "Kommentiere Wetter nicht proaktiv");
        assertContains(stationOuterState, "Behaupte nicht, dass du Wetter selbst spuerst");
        assertContains(stationOuterState, "Erfinde keine Live-Verfuegbarkeit");
        assertContains(stationOuterState,
                "Ich bin GIGI, ein sozial intelligenter Roboter auf der Tour de Suisse Robotique.");
        assertFalse(stationOuterState.contains("obs.emotion.face"));
        assertFalse(stationOuterState.contains("SIRA Lab"));
        assertFalse(stationOuterState.contains("Hotel Grischa"));
        assertCompactGenerationPrompt("STATION_OUTER_STATE", stationOuterState);
    }

    @Test
    void generalPromptGroundsOpenStationConversationWithoutSceneAnchors() throws Exception {
        Map<String, String> prompts = stringFields(APPENZELL_GENERAL.definitionClass());
        String statePrompt = prompts.get("PROMPT_STATE");

        assertContains(statePrompt, "nicht an eine Filmszene gebunden");
        assertContains(statePrompt, "Kundinnen, Kunden, Mitarbeitenden");
        assertContains(statePrompt, "Migros-Filiale");
        assertContains(statePrompt, "Appenzell");
        assertContains(statePrompt, "Alltagshilfe mehr ist als Produktsuche");
        assertContains(statePrompt, "Migros-Mitarbeitende");
        assertContains(statePrompt, "Keine bestimmte Filmszene");
        assertContains(statePrompt, "Keine festen Produktketten");
        assertContains(statePrompt, "Produktetiketten");
        assertContains(statePrompt, "Keine medizinische");
        assertFalse(statePrompt.contains("Protein-Drink"));
        assertFalse(statePrompt.contains("Linsensalat"));
        assertFalse(statePrompt.contains("Tomaten"));
        assertFalse(statePrompt.contains("Mostbroeckli"));
        assertFalse(statePrompt.contains("Pantli"));
        assertCompactGenerationPrompt("AppenzellGeneral.PROMPT_STATE", statePrompt);
        assertContains(prompts.get("PROMPT_OUTCOME_EXTRACTION"),
                "\"interaction_type\": \"migros_appenzell_general\"");
        assertContains(prompts.get("PROMPT_FINAL"), "Antworte nur auf Deutsch");

        for (Map.Entry<String, String> prompt : prompts.entrySet()) {
            assertTrue(prompt.getValue().length() <= MAX_PERSISTED_PROMPT_LENGTH,
                    APPENZELL_GENERAL.definitionClass().getSimpleName() + "." + prompt.getKey());
        }
    }

    @Test
    void scriptedScenePromptsGroundSceneBeatsAndStatelessVariationRules() throws Exception {
        Map<String, String> scene2Prompts = stringFields(APPENZELL_SCENE_2_MENU_PLANNER.definitionClass());
        String scene2State = scene2Prompts.get("PROMPT_STATE");
        assertContains(scene2State, "Szene 2");
        assertContains(scene2State, "In-Store Beratung / Menuplanner");
        assertContains(scene2State, "Starte sehr klein");
        assertContains(scene2State, "kommt vom Sport");
        assertContains(scene2State, "du kommst vom Sport und brauchst etwas Proteinreiches");
        assertContains(scene2State, "Protein-Drink");
        assertContains(scene2State, "Linsensalat");
        assertContains(scene2State, "Tomaten");
        assertContains(scene2State, "Mostbroeckli");
        assertContains(scene2State, "Pantli");
        assertContains(scene2State, "GIGI macht die Entscheidung leichter");
        assertContains(scene2State, "erste Starter-Antwort ist nur die Begruessung");
        assertContains(scene2State, "Ich mache einen Vorschlag");
        assertFalse(scene2State.toLowerCase(java.util.Locale.ROOT).contains("sortier"));
        assertContains(scene2State, "Kopiere die Beispieldialoge nie wortwoertlich");
        assertContains(scene2State, "Waehle still eine andere Satzform");
        assertContains(scene2Prompts.get("PROMPT_STATE_STARTER"), "Hoi, ich bin GIGI.");
        assertFalse(scene2Prompts.get("PROMPT_STATE_STARTER").contains("Protein-Drink"));
        assertFalse(scene2Prompts.get("PROMPT_STATE_STARTER").contains("Linsensalat"));
        assertFalse(scene2Prompts.get("PROMPT_STATE_STARTER").contains("ausgewogenes Essen"));
        assertCompactGenerationPrompt("AppenzellScene2MenuPlanner.PROMPT_STATE", scene2State);
        assertContains(scene2Prompts.get("PROMPT_OUTCOME_EXTRACTION"),
                "\"interaction_type\": \"migros_appenzell_scene_2_menu_planner\"");

        Map<String, String> scene3Prompts = stringFields(APPENZELL_SCENE_3_CHECKOUT_REFLECTION.definitionClass());
        String scene3State = scene3Prompts.get("PROMPT_STATE");
        assertContains(scene3State, "Szene 3");
        assertContains(scene3State, "Kasse / Reflexion");
        assertContains(scene3State, "Peterli glatt");
        assertContains(scene3State, "Vitamine wie A, C und K");
        assertContains(scene3State, "60-Liter-Kehrichtsaecke");
        assertContains(scene3State, "Gewohnheit");
        assertContains(scene3State, "Menschen suchen nicht nur Produkte");
        assertContains(scene3State, "Kopiere die Beispieldialoge nie wortwoertlich");
        assertContains(scene3State, "Waehle still eine andere Satzform");
        assertCompactGenerationPrompt("AppenzellScene3CheckoutReflection.PROMPT_STATE", scene3State);
        assertContains(scene3Prompts.get("PROMPT_OUTCOME_EXTRACTION"),
                "\"interaction_type\": \"migros_appenzell_scene_3_checkout_reflection\"");

        for (DefinitionCase definitionCase : SCENE_DEFINITIONS) {
            for (Map.Entry<String, String> prompt : stringFields(definitionCase.definitionClass()).entrySet()) {
                assertTrue(prompt.getValue().length() <= MAX_PERSISTED_PROMPT_LENGTH,
                        definitionCase.definitionClass().getSimpleName() + "." + prompt.getKey());
            }
        }
    }

    @Test
    void generalSensingEventsHaveSelfTransitionsAndUseGestureOnlyPrompt() throws Exception {
        Agent agent = APPENZELL_GENERAL.definition().createAgent();
        OuterState outerState = assertInstanceOf(OuterState.class, agent.getCurrentState());
        State interactionState = outerState.getInnerCurrent();

        assertEquals(List.of(
                "GIGI Migros Appenzell context",
                "GIGI Migros Appenzell general conversation"), outerState.getActiveStatePath());
        assertNoTransitionDecision(interactionState, Event.TYPE_FACE_EMOTION);
        assertTransitionDecision(interactionState, Event.TYPE_SOCIAL_CONTEXT);
        assertTransitionDecision(interactionState, Event.TYPE_SOCIAL_SITUATION_CHANGE);

        PromptPolicy policy = assertInstanceOf(PromptPolicy.class, policy(interactionState));
        assertNull(policy.getNonVerbalPlanPrompt());
        assertEquals(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT, policy.getNonVerbalGesturePrompt());
    }

    @Test
    void sceneSensingEventsHaveSelfTransitionsAndUseGestureOnlyPrompt() throws Exception {
        for (DefinitionCase definitionCase : SCENE_DEFINITIONS) {
            Agent agent = definitionCase.definition().createAgent();
            OuterState outerState = assertInstanceOf(OuterState.class, agent.getCurrentState());
            State interactionState = outerState.getInnerCurrent();

            assertEquals("GIGI Migros Appenzell scene context", outerState.getActiveStatePath().get(0));
            assertTransitionDecision(interactionState, Event.TYPE_SOCIAL_CONTEXT);
            assertTransitionDecision(interactionState, Event.TYPE_SOCIAL_SITUATION_CHANGE);

            PromptPolicy policy = assertInstanceOf(PromptPolicy.class, policy(interactionState));
            assertNull(policy.getNonVerbalPlanPrompt());
            assertEquals(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT, policy.getNonVerbalGesturePrompt());
        }
    }

    private static void assertTransitionDecision(State state, String eventType) throws Exception {
        assertTrue(transitions(state).stream()
                .flatMap(transition -> transition.getDecisions().stream())
                .anyMatch(decision -> decision.toString().contains(eventType)),
                "missing transition decision for " + eventType);
    }

    private static void assertNoTransitionDecision(State state, String eventType) throws Exception {
        assertFalse(transitions(state).stream()
                .flatMap(transition -> transition.getDecisions().stream())
                .anyMatch(decision -> decision.toString().contains(eventType)),
                "unexpected transition decision for " + eventType);
    }

    private static void assertMigrosSocialWeatherGestureOnlyProfile(AgentInteractionProfile profile) {
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_USER_UTTERANCE));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_HUMAN_PRESENCE));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_GROUPING));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_CONTEXT));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_WEATHER_CURRENT));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_WEATHER_FORECAST));
        assertFalse(profile.supportsObservation(AgentInteractionProfile.OBS_FACE_EMOTION));
        assertEquals(7, profile.getSupportedObservations().size());

        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_SPEECH));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE));
        assertFalse(profile.supportsBehaviourModality(
                AgentInteractionProfile.MODALITY_NONVERBAL_FACIAL_EXPRESSION));
        assertFalse(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_GAZE));
        assertFalse(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_MOTION));
        assertFalse(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN));
        assertFalse(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_DISPLAY));
        assertEquals(2, profile.getSupportedBehaviourModalities().size());
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

    private static void assertCompactGenerationPrompt(String name, String prompt) {
        assertTrue(prompt.length() <= MAX_GENERATION_STATE_PROMPT_LENGTH,
                name + " length was " + prompt.length());
        assertFalse(prompt.contains("End:"), name + " should not carry redundant final-label instructions");
    }

    private record DefinitionCase(AgentDefinition definition, Class<?> definitionClass, String key) {
    }
}
