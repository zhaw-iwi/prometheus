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

    private record BabylonDefinition(AgentDefinition definition, Class<?> definitionClass) {
    }
}
