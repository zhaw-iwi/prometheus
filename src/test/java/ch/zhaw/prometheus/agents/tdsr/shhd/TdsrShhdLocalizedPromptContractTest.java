package ch.zhaw.prometheus.agents.tdsr.shhd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

class TdsrShhdLocalizedPromptContractTest {
    private static final int MAX_PERSISTED_PROMPT_LENGTH = 8000;

    private static final List<LocalizedDefinition> LOCALIZED_DEFINITIONS = List.of(
            localized(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.en.EPFLActive(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.en.EPFLActive.class,
                    AgentDefinition.LANGUAGE_ENGLISH, "Answer", "English", "epfl_active"),
            localized(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.en.Furka(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.en.Furka.class,
                    AgentDefinition.LANGUAGE_ENGLISH, "Answer", "English", "furka"),
            localized(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.en.InterviewingPeople(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.en.InterviewingPeople.class,
                    AgentDefinition.LANGUAGE_ENGLISH, "Answer", "English", "interviewing_people"),
            localized(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.en.SUPSIActive(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.en.SUPSIActive.class,
                    AgentDefinition.LANGUAGE_ENGLISH, "Answer", "English", "supsi_active"),
            localized(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.en.UnisStudent(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.en.UnisStudent.class,
                    AgentDefinition.LANGUAGE_ENGLISH, "Answer", "English", "unis_student"),
            localized(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.it.EPFLActive(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.it.EPFLActive.class,
                    AgentDefinition.LANGUAGE_ITALIAN, "Rispondi", "italiano", "epfl_active"),
            localized(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.it.Furka(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.it.Furka.class,
                    AgentDefinition.LANGUAGE_ITALIAN, "Rispondi", "italiano", "furka"),
            localized(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.it.InterviewingPeople(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.it.InterviewingPeople.class,
                    AgentDefinition.LANGUAGE_ITALIAN, "Rispondi", "italiano", "interviewing_people"),
            localized(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.it.SUPSIActive(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.it.SUPSIActive.class,
                    AgentDefinition.LANGUAGE_ITALIAN, "Rispondi", "italiano", "supsi_active"),
            localized(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.it.UnisStudent(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.it.UnisStudent.class,
                    AgentDefinition.LANGUAGE_ITALIAN, "Rispondi", "italiano", "unis_student"),
            localized(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.fr.EPFLActive(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.fr.EPFLActive.class,
                    AgentDefinition.LANGUAGE_FRENCH, "Réponds", "français", "epfl_active"),
            localized(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.fr.Furka(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.fr.Furka.class,
                    AgentDefinition.LANGUAGE_FRENCH, "Réponds", "français", "furka"),
            localized(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.fr.InterviewingPeople(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.fr.InterviewingPeople.class,
                    AgentDefinition.LANGUAGE_FRENCH, "Réponds", "français", "interviewing_people"),
            localized(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.fr.SUPSIActive(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.fr.SUPSIActive.class,
                    AgentDefinition.LANGUAGE_FRENCH, "Réponds", "français", "supsi_active"),
            localized(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.fr.UnisStudent(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.fr.UnisStudent.class,
                    AgentDefinition.LANGUAGE_FRENCH, "Réponds", "français", "unis_student"));

    private static final List<BabylonDefinition> BABYLON_DEFINITIONS = List.of(
            babylon(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.babylon.EPFLActive(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.babylon.EPFLActive.class,
                    "epfl_active"),
            babylon(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.babylon.Furka(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.babylon.Furka.class,
                    "furka"),
            babylon(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.babylon.InterviewingPeople(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.babylon.InterviewingPeople.class,
                    "interviewing_people"),
            babylon(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.babylon.SUPSIActive(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.babylon.SUPSIActive.class,
                    "supsi_active"),
            babylon(new ch.zhaw.prometheus.agentdefs.tdsr.shhd.babylon.UnisStudent(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.babylon.UnisStudent.class,
                    "unis_student"));

    @Test
    void localizedDefinitionsUseExpectedKeysPackagePathsAndRealtimeLanguageCodes() {
        for (LocalizedDefinition localized : LOCALIZED_DEFINITIONS) {
            AgentDefinition definition = localized.definition();
            Agent agent = definition.createAgent();

            assertEquals("tdsr.shhd." + localized.languageCode() + "." + localized.agentName(), definition.key());
            assertEquals(List.of("tdsr", "shhd", localized.languageCode()), definition.packagePath());
            assertEquals(localized.languageCode(), definition.languageCode(), definition.key());
            assertEquals(localized.languageCode(), agent.getLanguageCode(), definition.key());
            assertSocialTourProfile(agent.getInteractionProfile());
        }
    }

    @Test
    void babylonDefinitionsUseExpectedKeysPackagePathsAndNoRealtimeLanguageCode() {
        for (BabylonDefinition babylon : BABYLON_DEFINITIONS) {
            AgentDefinition definition = babylon.definition();
            Agent agent = definition.createAgent();

            assertEquals("tdsr.shhd.babylon." + babylon.agentName(), definition.key());
            assertEquals(List.of("tdsr", "shhd", "babylon"), definition.packagePath());
            assertNull(definition.languageCode(), definition.key());
            assertNull(agent.getLanguageCode(), definition.key());
            assertSocialTourProfile(agent.getInteractionProfile());
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
    void babylonPromptsKeepLanguageOpenAndInterpretMultilingualStopIntent() throws Exception {
        for (BabylonDefinition babylon : BABYLON_DEFINITIONS) {
            String allPrompts = String.join("\n", promptFields(babylon.definitionClass()).values());

            assertTrue(allPrompts.contains(
                    "Du kannst Deutsch, Französisch, Italienisch und Englisch. Antworte in der Sprache, in der Du angesprochen wirst."));
            assertTrue(allPrompts.contains("If no user language is known yet, start in English."));
            assertTrue(allPrompts.contains("Interpret stop intent in German, French, Italian, and English."));
            assertFalse(allPrompts.contains("Answer only in English."));
            assertFalse(allPrompts.contains("languageCode"));
        }
    }

    @Test
    void promptsKeepProtocolTokensLanguageNeutral() throws Exception {
        for (Class<?> definitionClass : allDefinitionClasses()) {
            String allPrompts = String.join("\n", promptFields(definitionClass).values());

            assertTrue(allPrompts.contains("obs.weather.current"), definitionClass.getName());
            assertTrue(allPrompts.contains("obs.weather.forecast"), definitionClass.getName());
            assertTrue(allPrompts.contains("obs.human.presence"), definitionClass.getName());
            assertTrue(allPrompts.contains("obs.social.grouping"), definitionClass.getName());
            assertTrue(allPrompts.contains("obs.social.situation_change"), definitionClass.getName());
            assertTrue(allPrompts.contains("now_alone"), definitionClass.getName());
            assertTrue(allPrompts.contains("crowd_detected"), definitionClass.getName());
            assertTrue(allPrompts.contains("\"flow_type\""), definitionClass.getName());
            assertTrue(allPrompts.contains("\"outcomes\""), definitionClass.getName());
            assertTrue(allPrompts.contains("\"social_context_used\""), definitionClass.getName());
        }
    }

    @Test
    void promptsUseBriefMicroHumorAccentAcrossLanguages() throws Exception {
        for (Class<?> definitionClass : allDefinitionClasses()) {
            Map<String, String> prompts = promptFields(definitionClass);
            String statePrompt = prompts.get("PROMPT_STATE");
            String finalPrompt = prompts.get("PROMPT_FINAL");

            assertBriefMicroHumorContract(statePrompt, finalPrompt, promptLanguage(definitionClass), definitionClass);
            assertFalse(statePrompt.contains("usually one or two short sentences"), definitionClass.getName());
            assertFalse(statePrompt.contains("souvent une ou deux phrases"), definitionClass.getName());
            assertFalse(statePrompt.contains("di solito una o due frasi"), definitionClass.getName());
            assertFalse(statePrompt.contains("rarely three"), definitionClass.getName());
            assertFalse(statePrompt.contains("rarement trois"), definitionClass.getName());
            assertFalse(statePrompt.contains("raramente tre"), definitionClass.getName());
        }
    }


    @Test
    void outcomeExtractionPromptsUseSharedCompactJsonContract() throws Exception {
        for (Class<?> definitionClass : allDefinitionClasses()) {
            String prompt = promptFields(definitionClass).get("PROMPT_OUTCOME_EXTRACTION");

            assertTrue(prompt.startsWith("Extract the ended TDSR SHHD interaction"), definitionClass.getName());
            assertTrue(prompt.contains("\"flow_type\":\"single_state\""), definitionClass.getName());
            assertTrue(prompt.contains("\"social_context_used\":true|false"), definitionClass.getName());
            assertTrue(prompt.contains("Rules: exactly one outcome"), definitionClass.getName());
            assertTrue(prompt.length() < 700, definitionClass.getName());
            assertFalse(prompt.contains("Extrais le résultat"), definitionClass.getName());
            assertFalse(prompt.contains("Estrai il risultato"), definitionClass.getName());
            assertFalse(prompt.contains("Extrahiere das Ergebnis"), definitionClass.getName());
        }
    }

    @Test
    void promptsKeepSourcePromptRefinementsAcrossLanguages() throws Exception {
        for (Class<?> definitionClass : allDefinitionClasses()) {
            String statePrompt = promptFields(definitionClass).get("PROMPT_STATE");
            String language = promptLanguage(definitionClass);

            assertContains(statePrompt, socialAloneAnchor(language), definitionClass);
            assertContains(statePrompt, socialGroupAnchor(language), definitionClass);

            switch (definitionClass.getSimpleName()) {
                case "EPFLActive" -> assertContains(statePrompt, epflObjectAnchor(language), definitionClass);
                case "Furka" -> {
                    assertContains(statePrompt, "Appenzell", definitionClass);
                    assertContains(statePrompt, furkaTourBridgeAnchor(language), definitionClass);
                }
                case "InterviewingPeople" ->
                    assertContains(statePrompt, interviewingFollowUpAnchor(language), definitionClass);
                case "SUPSIActive" -> {
                    assertContains(statePrompt, supsiTeleoperationPhraseAnchor(language), definitionClass);
                    assertContains(statePrompt, "Snap-Fits", definitionClass);
                }
                case "UnisStudent" -> assertContains(statePrompt, unisStudentStrategyAnchor(language), definitionClass);
                default -> throw new IllegalStateException("Unexpected SHHD agent class: " + definitionClass);
            }
        }
    }

    private static void assertSocialTourProfile(AgentInteractionProfile profile) {
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_USER_UTTERANCE));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_WEATHER_CURRENT));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_WEATHER_FORECAST));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_HUMAN_PRESENCE));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_GROUPING));
        assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_SPEECH));
        assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE));
        assertFalse(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_MOTION));
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

    private static List<Class<?>> allDefinitionClasses() {
        return java.util.stream.Stream.concat(
                LOCALIZED_DEFINITIONS.stream().map(LocalizedDefinition::definitionClass),
                BABYLON_DEFINITIONS.stream().map(BabylonDefinition::definitionClass))
                .toList();
    }

    private static void assertContains(String prompt, String expected, Class<?> definitionClass) {
        assertTrue(prompt.contains(expected), definitionClass.getName() + " missing: " + expected);
    }

    private static void assertBriefMicroHumorContract(
            String statePrompt,
            String finalPrompt,
            String language,
            Class<?> definitionClass) {
        switch (language) {
            case "fr" -> {
                assertContains(statePrompt, "micro-humour chaleureux", definitionClass);
                assertContains(statePrompt, "Réponds très brièvement: souvent une phrase", definitionClass);
                assertContains(statePrompt, "Ne compense pas par une longue phrase unique", definitionClass);
                assertContains(finalPrompt, "Dis au revoir en une phrase courte", definitionClass);
            }
            case "it" -> {
                assertContains(statePrompt, "micro-umorismo caldo", definitionClass);
                assertContains(statePrompt, "Rispondi in modo molto breve: di solito una frase", definitionClass);
                assertContains(statePrompt, "Non compensare con una frase unica ma lunga", definitionClass);
                assertContains(finalPrompt, "Congedati in una frase breve", definitionClass);
            }
            default -> {
                assertContains(statePrompt, "Use warm micro-humor more often", definitionClass);
                assertContains(statePrompt, "Answer very briefly: usually one sentence, often only 3-10 words",
                        definitionClass);
                assertContains(statePrompt, "Do not compensate with one long sentence", definitionClass);
                assertContains(finalPrompt, "Say goodbye in one short sentence", definitionClass);
            }
        }
    }

    private static String promptLanguage(Class<?> definitionClass) {
        String className = definitionClass.getName();
        if (className.contains(".fr.")) {
            return "fr";
        }
        if (className.contains(".it.")) {
            return "it";
        }
        return "en";
    }

    private static String socialAloneAnchor(String language) {
        return switch (language) {
            case "fr" -> "soudain plus personne n'est visible";
            case "it" -> "improvvisamente non è più visibile nessuno";
            default -> "suddenly no one is visible";
        };
    }

    private static String socialGroupAnchor(String language) {
        return switch (language) {
            case "fr" -> "une personne devient plusieurs";
            case "it" -> "da una persona si passa a più persone";
            default -> "one person becomes several";
        };
    }

    private static String epflObjectAnchor(String language) {
        return switch (language) {
            case "fr" -> "humains des objets";
            case "it" -> "umani dagli oggetti";
            default -> "humans from objects";
        };
    }

    private static String furkaTourBridgeAnchor(String language) {
        return switch (language) {
            case "fr" -> "fromage ou le chocolat";
            case "it" -> "formaggio o cioccolato";
            default -> "cheese, or chocolate";
        };
    }

    private static String interviewingFollowUpAnchor(String language) {
        return switch (language) {
            case "fr" -> "exemple concret";
            case "it" -> "esempio concreto";
            default -> "concrete example";
        };
    }

    private static String supsiTeleoperationPhraseAnchor(String language) {
        return switch (language) {
            case "fr" -> "On m'explique";
            case "it" -> "Mi viene spiegato";
            default -> "I am being told";
        };
    }

    private static String unisStudentStrategyAnchor(String language) {
        return switch (language) {
            case "fr" -> "parle avec idéalisme";
            case "it" -> "parla con idealismo";
            default -> "speaks idealistically";
        };
    }

    private static LocalizedDefinition localized(AgentDefinition definition, Class<?> definitionClass,
            String languageCode, String guardVerb, String languageName, String agentName) {
        return new LocalizedDefinition(definition, definitionClass, languageCode, guardVerb, languageName, agentName);
    }

    private static BabylonDefinition babylon(AgentDefinition definition, Class<?> definitionClass, String agentName) {
        return new BabylonDefinition(definition, definitionClass, agentName);
    }

    private record LocalizedDefinition(AgentDefinition definition, Class<?> definitionClass, String languageCode,
            String guardVerb, String languageName, String agentName) {
    }

    private record BabylonDefinition(AgentDefinition definition, Class<?> definitionClass, String agentName) {
    }
}
