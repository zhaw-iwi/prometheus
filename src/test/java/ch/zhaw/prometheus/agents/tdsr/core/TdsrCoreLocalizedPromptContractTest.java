package ch.zhaw.prometheus.agents.tdsr.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;

class TdsrCoreLocalizedPromptContractTest {
    private static final int MAX_PERSISTED_PROMPT_LENGTH = 8000;

    private static final List<LocalizedDefinition> LOCALIZED_DEFINITIONS = List.of(
            new LocalizedDefinition(new ch.zhaw.prometheus.agentdefs.tdsr.core.fr.GuessingGameWithGestures(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.fr.GuessingGameWithGestures.class,
                    AgentDefinition.LANGUAGE_FRENCH, "Réponds", "français"),
            new LocalizedDefinition(new ch.zhaw.prometheus.agentdefs.tdsr.core.fr.SocialContextSensitivity(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.fr.SocialContextSensitivity.class,
                    AgentDefinition.LANGUAGE_FRENCH, "Réponds", "français"),
            new LocalizedDefinition(new ch.zhaw.prometheus.agentdefs.tdsr.core.fr.RockScissorPaper(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.fr.RockScissorPaper.class,
                    AgentDefinition.LANGUAGE_FRENCH, "Réponds", "français"),
            new LocalizedDefinition(new ch.zhaw.prometheus.agentdefs.tdsr.core.fr.TourConversation(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.fr.TourConversation.class,
                    AgentDefinition.LANGUAGE_FRENCH, "Réponds", "français"),
            new LocalizedDefinition(
                    new ch.zhaw.prometheus.agentdefs.tdsr.core.fr.TourConversationSocialContextSensitivity(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.fr.TourConversationSocialContextSensitivity.class,
                    AgentDefinition.LANGUAGE_FRENCH, "Réponds", "français"),
            new LocalizedDefinition(new ch.zhaw.prometheus.agentdefs.tdsr.core.it.GuessingGameWithGestures(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.it.GuessingGameWithGestures.class,
                    AgentDefinition.LANGUAGE_ITALIAN, "Rispondi", "italiano"),
            new LocalizedDefinition(new ch.zhaw.prometheus.agentdefs.tdsr.core.it.SocialContextSensitivity(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.it.SocialContextSensitivity.class,
                    AgentDefinition.LANGUAGE_ITALIAN, "Rispondi", "italiano"),
            new LocalizedDefinition(new ch.zhaw.prometheus.agentdefs.tdsr.core.it.RockScissorPaper(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.it.RockScissorPaper.class,
                    AgentDefinition.LANGUAGE_ITALIAN, "Rispondi", "italiano"),
            new LocalizedDefinition(new ch.zhaw.prometheus.agentdefs.tdsr.core.it.TourConversation(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.it.TourConversation.class,
                    AgentDefinition.LANGUAGE_ITALIAN, "Rispondi", "italiano"),
            new LocalizedDefinition(
                    new ch.zhaw.prometheus.agentdefs.tdsr.core.it.TourConversationSocialContextSensitivity(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.it.TourConversationSocialContextSensitivity.class,
                    AgentDefinition.LANGUAGE_ITALIAN, "Rispondi", "italiano"),
            new LocalizedDefinition(new ch.zhaw.prometheus.agentdefs.tdsr.core.en.GuessingGameWithGestures(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.en.GuessingGameWithGestures.class,
                    AgentDefinition.LANGUAGE_ENGLISH, "Answer", "English"),
            new LocalizedDefinition(new ch.zhaw.prometheus.agentdefs.tdsr.core.en.SocialContextSensitivity(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.en.SocialContextSensitivity.class,
                    AgentDefinition.LANGUAGE_ENGLISH, "Answer", "English"),
            new LocalizedDefinition(new ch.zhaw.prometheus.agentdefs.tdsr.core.en.RockScissorPaper(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.en.RockScissorPaper.class,
                    AgentDefinition.LANGUAGE_ENGLISH, "Answer", "English"),
            new LocalizedDefinition(new ch.zhaw.prometheus.agentdefs.tdsr.core.en.TourConversation(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.en.TourConversation.class,
                    AgentDefinition.LANGUAGE_ENGLISH, "Answer", "English"),
            new LocalizedDefinition(
                    new ch.zhaw.prometheus.agentdefs.tdsr.core.en.TourConversationSocialContextSensitivity(),
                    ch.zhaw.prometheus.agentdefs.tdsr.core.en.TourConversationSocialContextSensitivity.class,
                    AgentDefinition.LANGUAGE_ENGLISH, "Answer", "English"));

    @Test
    void localizedDefinitionsUseExpectedKeysAndRealtimeLanguageCodes() {
        for (LocalizedDefinition localized : LOCALIZED_DEFINITIONS) {
            AgentDefinition definition = localized.definition();
            Agent agent = definition.createAgent();

            assertTrue(definition.key().startsWith("tdsr.core." + localized.languageCode() + "."),
                    definition.key());
            assertEquals(localized.languageCode(), definition.languageCode(), definition.key());
            assertEquals(localized.languageCode(), agent.getLanguageCode(), definition.key());
            assertFalse(agent.getInteractionProfile().getSupportedObservations().isEmpty(), definition.key());
            assertFalse(agent.getInteractionProfile().getSupportedBehaviourModalities().isEmpty(), definition.key());
        }
    }

    @Test
    void localizedPromptsCarryTheirLanguageGuardsAndNoGermanOnlyGuard() throws Exception {
        for (LocalizedDefinition localized : LOCALIZED_DEFINITIONS) {
            for (Map.Entry<String, String> promptField : promptFields(localized.definitionClass()).entrySet()) {
                String fieldName = promptField.getKey();
                String prompt = promptField.getValue();

                assertTrue(prompt.length() <= MAX_PERSISTED_PROMPT_LENGTH,
                        localized.definitionClass().getSimpleName() + "." + fieldName
                                + " must fit the persisted prompt column");

                if (fieldName.equals("PROMPT_STATE") || fieldName.equals("PROMPT_FINAL")) {
                    String lowerPrompt = prompt.toLowerCase(java.util.Locale.ROOT);
                    assertTrue(lowerPrompt.contains(localized.guardVerb().toLowerCase(java.util.Locale.ROOT)),
                            localized.definitionClass() + "." + fieldName);
                    assertTrue(lowerPrompt.contains(localized.languageName().toLowerCase(java.util.Locale.ROOT)),
                            localized.definitionClass() + "." + fieldName);
                }

                assertFalse(prompt.contains("Antworte immer auf Deutsch"), localized.definitionClass() + "." + fieldName);
                assertFalse(prompt.contains("Antworte ausnahmslos auf Deutsch"),
                        localized.definitionClass() + "." + fieldName);
            }
        }
    }

    @Test
    void localizedPromptsKeepProtocolTokensLanguageNeutral() throws Exception {
        for (LocalizedDefinition localized : LOCALIZED_DEFINITIONS) {
            String allPrompts = String.join("\n", promptFields(localized.definitionClass()).values());

            assertTrue(allPrompts.contains("obs.weather.current"), localized.definitionClass().getName());
            assertTrue(allPrompts.contains("obs.weather.forecast"), localized.definitionClass().getName());
            if (!localized.definitionClass().getSimpleName().equals("RockScissorPaper")) {
                assertTrue(allPrompts.contains("\"flow_type\""), localized.definitionClass().getName());
                assertTrue(allPrompts.contains("\"outcomes\""), localized.definitionClass().getName());
            }

            if (localized.definitionClass().getSimpleName().contains("SocialContext")) {
                assertTrue(allPrompts.contains("obs.social.situation_change"),
                        localized.definitionClass().getName());
                assertTrue(allPrompts.contains("now_alone"), localized.definitionClass().getName());
                if (localized.definitionClass().getSimpleName().equals("SocialContextSensitivity")) {
                    assertTrue(allPrompts.contains("arrival"), localized.definitionClass().getName());
                } else {
                    assertTrue(allPrompts.contains("crowd_detected"), localized.definitionClass().getName());
                }
            }
        }
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

    private record LocalizedDefinition(AgentDefinition definition, Class<?> definitionClass, String languageCode,
            String guardVerb, String languageName) {
    }
}
