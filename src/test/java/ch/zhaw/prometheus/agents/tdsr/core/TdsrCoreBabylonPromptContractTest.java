package ch.zhaw.prometheus.agents.tdsr.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;

class TdsrCoreBabylonPromptContractTest {
    private static final int MAX_PERSISTED_PROMPT_LENGTH = 8000;
    private static final String MULTILINGUAL_RULE =
            "Du kannst Deutsch, Französisch, Italienisch und Englisch. Antworte in der Sprache, in der Du angesprochen wirst.";

    private static final List<BabylonDefinition> BABYLON_DEFINITIONS = List.of(
            new BabylonDefinition(new ch.zhaw.prometheus.agentdefs.tdsr.core.babylon.GuessingGameWithGestures(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.babylon.GuessingGameWithGestures.class),
            new BabylonDefinition(new ch.zhaw.prometheus.agentdefs.tdsr.core.babylon.SocialContextSensitivity(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.babylon.SocialContextSensitivity.class),
            new BabylonDefinition(new ch.zhaw.prometheus.agentdefs.tdsr.core.babylon.RockScissorPaper(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.babylon.RockScissorPaper.class),
            new BabylonDefinition(new ch.zhaw.prometheus.agentdefs.tdsr.core.babylon.TourConversation(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.babylon.TourConversation.class),
            new BabylonDefinition(new ch.zhaw.prometheus.agentdefs.tdsr.core.babylon.TourConversationSocialContextSensitivity(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.babylon.TourConversationSocialContextSensitivity.class));

    @Test
    void babylonDefinitionsUseExpectedKeysAndNoRealtimeLanguageCode() {
        for (BabylonDefinition babylon : BABYLON_DEFINITIONS) {
            AgentDefinition definition = babylon.definition();
            Agent agent = definition.createAgent();

            assertTrue(definition.key().startsWith("tdsr.core.babylon."), definition.key());
            assertNull(definition.languageCode(), definition.key());
            assertNull(agent.getLanguageCode(), definition.key());
            assertFalse(agent.getInteractionProfile().getSupportedObservations().isEmpty(), definition.key());
            assertFalse(agent.getInteractionProfile().getSupportedBehaviourModalities().isEmpty(), definition.key());
        }
    }

    @Test
    void babylonStateAndFinalPromptsKeepLanguageOpen() throws Exception {
        for (BabylonDefinition babylon : BABYLON_DEFINITIONS) {
            Map<String, String> prompts = promptFields(babylon.definitionClass());
            String statePrompt = statePrompt(prompts);
            String finalPrompt = prompts.get("PROMPT_FINAL");
            String allPrompts = String.join("\n", prompts.values());

            assertTrue(statePrompt.contains(MULTILINGUAL_RULE), babylon.definitionClass().getName());
            assertTrue(finalPrompt.contains(MULTILINGUAL_RULE), babylon.definitionClass().getName());
            assertTrue(allPrompts.contains("German, French, Italian, or English"),
                    babylon.definitionClass().getName());
            assertFalse(allPrompts.contains("Always answer in English"), babylon.definitionClass().getName());
            assertFalse(allPrompts.contains("Answer only in English"), babylon.definitionClass().getName());
            assertFalse(allPrompts.contains("play rock-scissor-paper in English"),
                    babylon.definitionClass().getName());
        }
    }

    @Test
    void babylonPromptsStillStartInEnglishAndKeepProtocolTokens() throws Exception {
        for (BabylonDefinition babylon : BABYLON_DEFINITIONS) {
            Map<String, String> prompts = promptFields(babylon.definitionClass());
            String starterPrompt = starterPrompt(prompts);
            String allPrompts = String.join("\n", prompts.values());

            for (Map.Entry<String, String> promptField : prompts.entrySet()) {
                assertTrue(promptField.getValue().length() <= MAX_PERSISTED_PROMPT_LENGTH,
                        babylon.definitionClass().getSimpleName() + "." + promptField.getKey()
                                + " must fit the persisted prompt column");
            }

            assertTrue(starterPrompt.contains("English"), babylon.definitionClass().getName());
            assertTrue(allPrompts.contains("obs.weather.current"), babylon.definitionClass().getName());
            assertTrue(allPrompts.contains("obs.weather.forecast"), babylon.definitionClass().getName());
            if (!babylon.definitionClass().getSimpleName().equals("RockScissorPaper")) {
                assertTrue(allPrompts.contains("\"flow_type\""), babylon.definitionClass().getName());
                assertTrue(allPrompts.contains("\"outcomes\""), babylon.definitionClass().getName());
            }

            if (babylon.definitionClass().getSimpleName().contains("SocialContext")) {
                assertTrue(allPrompts.contains("obs.social.situation_change"),
                        babylon.definitionClass().getName());
                assertTrue(allPrompts.contains("now_alone"), babylon.definitionClass().getName());
            }
        }
    }

    @Test
    void babylonOutcomeExtractionPromptsUseSharedCompactCoreContracts() throws Exception {
        for (BabylonDefinition babylon : BABYLON_DEFINITIONS) {
            Map<String, String> prompts = promptFields(babylon.definitionClass());
            if (babylon.definitionClass().getSimpleName().equals("RockScissorPaper")) {
                assertFalse(prompts.containsKey("PROMPT_OUTCOME_EXTRACTION"), babylon.definitionClass().getName());
                continue;
            }

            assertSharedCoreOutcomePrompt(
                    prompts.get("PROMPT_OUTCOME_EXTRACTION"),
                    babylon.definitionClass());
        }
    }

    @Test
    void babylonTourPromptsUseBriefMicroHumorContract() throws Exception {
        for (BabylonDefinition babylon : BABYLON_DEFINITIONS) {
            if (!isTourConversationClass(babylon.definitionClass())) {
                continue;
            }

            Map<String, String> prompts = promptFields(babylon.definitionClass());
            String statePrompt = statePrompt(prompts);
            String finalPrompt = prompts.get("PROMPT_FINAL");

            assertTrue(statePrompt.contains("Use warm micro-humor more often"),
                    babylon.definitionClass().getName());
            assertTrue(statePrompt.contains("Answer very briefly: usually one sentence, often only 3-10 words"),
                    babylon.definitionClass().getName());
            assertTrue(statePrompt.contains("Do not compensate with one long sentence"),
                    babylon.definitionClass().getName());
            assertTrue(finalPrompt.contains("Say goodbye in one short sentence"),
                    babylon.definitionClass().getName());
            assertFalse(statePrompt.contains("usually one or two short sentences"),
                    babylon.definitionClass().getName());
            assertFalse(statePrompt.contains("rarely three"), babylon.definitionClass().getName());
        }
    }

    private static boolean isTourConversationClass(Class<?> definitionClass) {
        String simpleName = definitionClass.getSimpleName();
        return simpleName.equals("TourConversation")
                || simpleName.equals("TourConversationSocialContextSensitivity");
    }

    private static String statePrompt(Map<String, String> prompts) {
        if (prompts.containsKey("PROMPT_STATE")) {
            return prompts.get("PROMPT_STATE");
        }
        return prompts.get("PROMPT_START");
    }

    private static String starterPrompt(Map<String, String> prompts) {
        if (prompts.containsKey("PROMPT_STATE_STARTER")) {
            return prompts.get("PROMPT_STATE_STARTER");
        }
        return prompts.get("PROMPT_STARTER");
    }

    private static Map<String, String> promptFields(Class<?> definitionClass) throws Exception {
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

    private static void assertSharedCoreOutcomePrompt(String prompt, Class<?> definitionClass) {
        assertTrue(prompt.contains("\"flow_type\":\"single_state\""), definitionClass.getName());
        assertTrue(prompt.contains("\"outcomes\""), definitionClass.getName());
        assertTrue(prompt.contains("Rules: exactly one outcome"), definitionClass.getName());
        assertTrue(prompt.length() < 700, definitionClass.getName());
        assertFalse(prompt.contains("Extrahiere das Ergebnis"), definitionClass.getName());
        assertFalse(prompt.contains("Extrais le résultat"), definitionClass.getName());
        assertFalse(prompt.contains("Estrai il risultato"), definitionClass.getName());

        switch (definitionClass.getSimpleName()) {
            case "GuessingGameWithGestures" -> {
                assertTrue(prompt.startsWith("Extract ended TDSR core guessing game"), definitionClass.getName());
                assertTrue(prompt.contains("\"interaction_type\":\"guessing_game_with_gestures\""),
                        definitionClass.getName());
                assertTrue(prompt.contains("\"final_guess\":\"string|null\""), definitionClass.getName());
                assertTrue(prompt.contains("\"gesture_demo\":true"), definitionClass.getName());
            }
            case "SocialContextSensitivity" -> {
                assertTrue(prompt.startsWith("Extract ended TDSR core social-context demo"),
                        definitionClass.getName());
                assertTrue(prompt.contains("\"interaction_type\":\"social_context_sensitivity\""),
                        definitionClass.getName());
                assertTrue(prompt.contains("\"reacted_to_social_events\":true|false"),
                        definitionClass.getName());
            }
            case "TourConversation" -> {
                assertTrue(prompt.startsWith("Extract ended TDSR core tour interaction"),
                        definitionClass.getName());
                assertTrue(prompt.contains("\"interaction_type\":\"tdsr_tour_conversation\""),
                        definitionClass.getName());
                assertTrue(prompt.contains("\"visitor_questions\""), definitionClass.getName());
            }
            case "TourConversationSocialContextSensitivity" -> {
                assertTrue(prompt.startsWith("Extract ended TDSR core social-tour interaction"),
                        definitionClass.getName());
                assertTrue(prompt.contains("\"interaction_type\":\"tdsr_tour_conversation_social_context\""),
                        definitionClass.getName());
                assertTrue(prompt.contains("\"social_context_used\":true|false"), definitionClass.getName());
                assertTrue(prompt.contains("\"observed_change_types\""), definitionClass.getName());
            }
            default -> throw new IllegalStateException("Unexpected TDSR core class: " + definitionClass);
        }
    }

    private record BabylonDefinition(AgentDefinition definition, Class<?> definitionClass) {
    }
}
