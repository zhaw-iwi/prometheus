package ch.zhaw.prometheus.agents.tdsr.shhd.de;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.PromptPolicy;

class TdsrShhdGermanPromptContractTest {
    private static final int MAX_PERSISTED_PROMPT_LENGTH = 8000;

    private static final List<DefinitionCase> DEFINITIONS = List.of(
            new DefinitionCase(
                    new ch.zhaw.prometheus.agentdefs.tdsr.shhd.de.EPFLActive(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.de.EPFLActive.class,
                    "tdsr.shhd.de.epfl_active",
                    "EPFL Active",
                    List.of("Qolo", "soziale Navigation", "Menschen nicht als Hindernisse")),
            new DefinitionCase(
                    new ch.zhaw.prometheus.agentdefs.tdsr.shhd.de.Furka(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.de.Furka.class,
                    "tdsr.shhd.de.furka",
                    "Furka",
                    List.of("Furka-Pass", "Belvedere", "Goldfinger")),
            new DefinitionCase(
                    new ch.zhaw.prometheus.agentdefs.tdsr.shhd.de.InterviewingPeople(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.de.InterviewingPeople.class,
                    "tdsr.shhd.de.interviewing_people",
                    "Interviewing People",
                    List.of("Haltung der Person", "Vertrauen", "gesellschaftliche Akzeptanz")),
            new DefinitionCase(
                    new ch.zhaw.prometheus.agentdefs.tdsr.shhd.de.SUPSIActive(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.de.SUPSIActive.class,
                    "tdsr.shhd.de.supsi_active",
                    "SUPSI Active",
                    List.of("Teleoperation", "Batteriepack", "Sicherheit")),
            new DefinitionCase(
                    new ch.zhaw.prometheus.agentdefs.tdsr.shhd.de.UnisStudent(),
                    ch.zhaw.prometheus.agentdefs.tdsr.shhd.de.UnisStudent.class,
                    "tdsr.shhd.de.unis_student",
                    "Unis Student",
                    List.of("Studentin oder einem Studenten", "persönliche Motivation", "keine Prüfungsfragen")));

    @Test
    void definitionsUseGermanShhdKeysPackagePathsAndLanguageCodes() {
        for (DefinitionCase definitionCase : DEFINITIONS) {
            AgentDefinition definition = definitionCase.definition();
            Agent agent = definition.createAgent();

            assertEquals(definitionCase.key(), definition.key());
            assertEquals(List.of("tdsr", "shhd", "de"), definition.packagePath());
            assertEquals(AgentDefinition.LANGUAGE_GERMAN, definition.languageCode());
            assertEquals(AgentDefinition.LANGUAGE_GERMAN, agent.getLanguageCode());
            assertTrue(agent.getName().contains("GIGI TDSR"));
            assertFalse(agent.getName().contains("SHHD"));
            assertFalse(agent.getDescription().contains("SHHD"));
            assertFalse(agent.getCurrentState().getName().contains("SHHD"));
            assertTrue(agent.getName().contains(definitionCase.displayToken()));
            assertTrue(agent.getDescription().contains("Deutschsprachiger"));
            assertTrue(agent.getDescription().contains("sozialer Kontextwahrnehmung")
                    || agent.getDescription().contains("sozialer Kontext"));
        }
    }

    @Test
    void definitionsKeepSocialTourInteractionProfile() {
        for (DefinitionCase definitionCase : DEFINITIONS) {
            AgentInteractionProfile profile = definitionCase.definition().createAgent().getInteractionProfile();

            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_USER_UTTERANCE));
            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_WEATHER_CURRENT));
            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_WEATHER_FORECAST));
            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_HUMAN_PRESENCE));
            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_GROUPING));
            assertTrue(profile.supportsObservation(AgentInteractionProfile.OBS_SOCIAL_SITUATION_CHANGE));
            assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_SPEECH));
            assertTrue(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE));
            assertTrue(profile.supportsBehaviourModality(
                    AgentInteractionProfile.MODALITY_NONVERBAL_FACIAL_EXPRESSION));
            assertFalse(profile.supportsBehaviourModality(AgentInteractionProfile.MODALITY_NONVERBAL_MOTION));
        }
    }

    @Test
    void statePromptsKeepCommonPersonaSignalsAndAgentSpecificIntent() throws Exception {
        for (DefinitionCase definitionCase : DEFINITIONS) {
            String prompt = prompt(definitionCase.definitionClass(), "PROMPT_STATE");

            assertTrue(prompt.contains("Du bist GIGI"));
            assertTrue(prompt.contains("Antworte immer auf Deutsch"));
            assertTrue(prompt.contains("mit Frank gemeinsam per Auto"));
            assertTrue(prompt.contains("Nutze diesen TDSR-Kontext nur"));
            assertTrue(prompt.contains("bleibe sonst bei der aktuellen Unterhaltung"));
            assertTrue(prompt.contains("Route kompakt"));
            assertTrue(prompt.contains("EPFL Lausanne"));
            assertTrue(prompt.contains("SUPSI Lugano"));
            assertTrue(prompt.contains("ZHAW Winterthur"));
            assertTrue(prompt.contains("Gesprächsfokus"));
            assertGermanBriefMicroHumorContract(prompt);
            assertFalse(prompt.contains("meist ein oder zwei kurze Sätze"));
            assertFalse(prompt.contains("selten drei"));
            assertTrue(prompt.contains("Kontextsignale, untergeordnet zum Gesprächsfokus"));
            assertTrue(prompt.contains("obs.weather.current"));
            assertTrue(prompt.contains("obs.weather.forecast"));
            assertTrue(prompt.contains("bereitgestellter aktueller Standort"));
            assertTrue(prompt.contains("obs.human.presence"));
            assertTrue(prompt.contains("obs.social.grouping"));
            assertTrue(prompt.contains("obs.social.situation_change"));
            assertTrue(prompt.contains("nicht mechanisch und nicht jedes Mal"));
            assertTrue(prompt.contains("Die Interaktion endet nur"));
            for (String anchor : definitionCase.intentAnchors()) {
                assertTrue(prompt.contains(anchor), definitionCase.key() + " missing " + anchor);
            }
        }
    }

    @Test
    void socialContextTransitionUsesPromptGateAndFinalTransitionUsesUserUtterances() throws Exception {
        for (DefinitionCase definitionCase : DEFINITIONS) {
            Agent agent = definitionCase.definition().createAgent();
            List<Transition> transitions = transitions(agent.getCurrentState());

            assertTrue(transitions.stream()
                    .flatMap(transition -> transition.getDecisions().stream())
                    .anyMatch(decision -> decision.toString().contains(Event.TYPE_USER_UTTERANCE)));
            assertTrue(transitions.stream()
                    .flatMap(transition -> transition.getDecisions().stream())
                    .anyMatch(decision -> decision.toString().contains(Event.TYPE_SOCIAL_SITUATION_CHANGE)));

            String toFinal = prompt(definitionCase.definitionClass(), "PROMPT_TO_FINAL");
            assertTrue(toFinal.contains("hoher Sicherheit"));
            assertTrue(toFinal.contains("das gesamte Gespräch jetzt zu beenden"));

            String socialGate = prompt(definitionCase.definitionClass(), "PROMPT_SOCIAL_INTERJECTION_OPPORTUNITY");
            assertTrue(socialGate.contains("kurze, dezente soziale Randbemerkung"));
            assertTrue(socialGate.contains("now_alone"));
            assertTrue(socialGate.contains("crowd_detected"));
            assertTrue(socialGate.contains("Schweigen natürlicher"));
        }
    }

    @Test
    void outcomeExtractionPromptsUseSharedCompactJsonContract() throws Exception {
        for (DefinitionCase definitionCase : DEFINITIONS) {
            String prompt = prompt(definitionCase.definitionClass(), "PROMPT_OUTCOME_EXTRACTION");

            assertTrue(prompt.startsWith("Extract the ended TDSR interaction"), definitionCase.key());
            assertTrue(prompt.contains("\"flow_type\":\"single_state\""), definitionCase.key());
            assertTrue(prompt.contains("\"social_context_used\":true|false"), definitionCase.key());
            assertTrue(prompt.contains("Rules: exactly one outcome"), definitionCase.key());
            assertTrue(prompt.length() < 700, definitionCase.key());
            assertFalse(prompt.contains("Extrahiere das Ergebnis"), definitionCase.key());
        }
    }

    @Test
    void policiesKeepStructuredNonverbalPromptAndPromptFieldsFitPersistence() throws Exception {
        for (DefinitionCase definitionCase : DEFINITIONS) {
            PromptPolicy policy = interactionPolicy(definitionCase.definition().createAgent().getCurrentState());
            assertNotNull(policy.getNonVerbalPlanPrompt());
            assertTrue(policy.getNonVerbalPlanPrompt().contains("Produce STRICT JSON only"));
            assertTrue(policy.getNonVerbalPlanPrompt().contains("OPEN_QUESTION"));
            assertTrue(policy.getNonVerbalPlanPrompt().contains("Prefer NONE for many routine turns"));
            assertTrue(policy.getNonVerbalPlanPrompt().contains("Do not output robot-server command IDs"));
            assertTrue(policy.getNonVerbalGesturePrompt().contains("Allowed labels only"));

            for (Map.Entry<String, String> promptField : promptFields(definitionCase.definitionClass()).entrySet()) {
                assertTrue(promptField.getValue().length() <= MAX_PERSISTED_PROMPT_LENGTH,
                        definitionCase.definitionClass().getSimpleName() + "." + promptField.getKey()
                                + " must fit the persisted prompt column");
            }
        }
    }

    @Test
    void promptFieldsDoNotExposeShhdAcronym() throws Exception {
        for (DefinitionCase definitionCase : DEFINITIONS) {
            for (Map.Entry<String, String> promptField : promptFields(definitionCase.definitionClass()).entrySet()) {
                assertFalse(promptField.getValue().toLowerCase(java.util.Locale.ROOT).contains("shhd"),
                        definitionCase.definitionClass().getName() + "." + promptField.getKey());
            }
        }
    }

    @Test
    void finalPromptsTieBackToSceneWithoutOpeningNewTopic() throws Exception {
        for (DefinitionCase definitionCase : DEFINITIONS) {
            String prompt = prompt(definitionCase.definitionClass(), "PROMPT_FINAL");

            assertTrue(prompt.contains("Antworte ausnahmslos auf Deutsch"));
            assertTrue(prompt.contains("Diese Unterhaltung ist beendet"));
            assertTrue(prompt.contains("Verabschiede dich in einem kurzen Satz"));
            assertTrue(prompt.contains("beginne kein neues Thema"));
        }
    }

    private static PromptPolicy interactionPolicy(State state) throws Exception {
        Field policyField = State.class.getDeclaredField("policy");
        policyField.setAccessible(true);
        Policy policy = (Policy) policyField.get(state);
        return assertInstanceOf(PromptPolicy.class, policy);
    }

    @SuppressWarnings("unchecked")
    private static List<Transition> transitions(State state) throws Exception {
        Field transitionsField = State.class.getDeclaredField("transitions");
        transitionsField.setAccessible(true);
        return (List<Transition>) transitionsField.get(state);
    }

    private static String prompt(Class<?> definitionClass, String fieldName) throws Exception {
        Field field = definitionClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }

    private static void assertGermanBriefMicroHumorContract(String prompt) {
        assertTrue(prompt.contains("warmen Mikrohumor häufiger"));
        assertTrue(prompt.contains("kurzen Bezug auf etwas Früheres"));
        assertTrue(prompt.contains("gutwillig, situationsbezogen"));
        assertTrue(prompt.contains("Mach keine Witze auf Kosten"));
        assertTrue(prompt.contains("Antworte sehr knapp: meist ein Satz, oft nur 3 bis 10 Wörter"));
        assertTrue(prompt.contains("direkte Erklärung es wirklich braucht"));
        assertTrue(prompt.contains("langen Ein-Satz-Monolog"));
        assertTrue(prompt.contains("fast fragmentartig kurz"));
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

    private record DefinitionCase(AgentDefinition definition, Class<?> definitionClass, String key,
            String displayToken, List<String> intentAnchors) {
    }
}
