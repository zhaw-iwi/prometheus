package ch.zhaw.prometheus.agentdefs.usecases.healthcare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.agentdefs.AgentDefinition;

class HealthcareUseCasePromptContractTest {
    private static final List<AgentDefinition> DEFINITIONS = List.of(
            new SingleStateGuessingGame(),
            new SingleStateGuessingGameUserGuess(),
            new SingleStateHealthcareConversation(),
            new SingleStateSmartGoalCoaching(),
            new SingleStateTherapyAppointmentReminder(),
            new SingleStateExcessivelyCompassionateTherapyAppointmentReminder(),
            new TwoStateTherapyAppointmentReminder());

    private static final List<String> STALE_PERSONA_TERMS = List.of(
            "GIGI",
            "gigi",
            "TDSR",
            "tdsr",
            "Tour de Suisse",
            "Davos",
            "Hotel Grischa",
            "Summit",
            "Antworte nur auf Deutsch",
            "Jee-jee",
            "Chee-chee",
            "robot",
            "Roboter");

    @Test
    void healthcareOuterPersonasUseValerianDigitalAgentPersona() {
        for (String persona : List.of(HealthcarePrompts.OUTER_STATE, HealthcareGeneralPrompts.OUTER_STATE)) {
            assertTrue(persona.contains("You are Valerian"));
            assertTrue(persona.contains("manifestation of a digital agent"));
            assertTrue(persona.contains("SIRA Lab"));
            assertTrue(persona.contains("PROMETHEUS"));
            assertTrue(persona.contains("rapid prototyping"));
            assertTrue(persona.contains("human-AI collaboration"));
            assertTrue(persona.contains("Answer only in English"));
        }
    }

    @Test
    void healthcareDefinitionsUseHealthcareNamespaceEnglishAndDisplayName() {
        for (AgentDefinition definition : DEFINITIONS) {
            assertTrue(definition.key().startsWith("usecases.healthcare."), definition.key());
            assertTrue(definition.displayName().startsWith("Valerian Use Cases Healthcare"), definition.key());
            assertTrue(definition.description().startsWith("English healthcare"), definition.key());
            assertTrue(AgentDefinition.LANGUAGE_ENGLISH.equals(definition.languageCode()), definition.key());
        }
    }

    @Test
    void healthcareConversationIsEnglishHealthcareDemoOnly() {
        assertTrue(SingleStateHealthcareConversation.PROMPT_STATE.contains("healthcare use-case demonstration"));
        assertTrue(SingleStateHealthcareConversation.PROMPT_STATE.contains("digital agents in healthcare"));
        assertTrue(SingleStateHealthcareConversation.PROMPT_FINAL.contains("Answer only in English"));
        assertFalse(SingleStateHealthcareConversation.PROMPT_STATE.contains("Hotel"));
        assertFalse(SingleStateHealthcareConversation.PROMPT_STATE.contains("Davos"));
    }

    @Test
    void excessivelyCompassionateTherapyVariantMakesEmpathyExplicitWithoutAddingPressure() {
        String compassionatePrompt =
                SingleStateExcessivelyCompassionateTherapyAppointmentReminder.PROMPT_STATE;

        assertTrue(compassionatePrompt.startsWith(SingleStateTherapyAppointmentReminder.PROMPT_STATE));
        assertTrue(compassionatePrompt.contains("Make empathy unmistakable"));
        assertTrue(compassionatePrompt.contains("explicitly validate the person's reaction"));
        assertTrue(compassionatePrompt.contains("reflect the specific concern"));
        assertTrue(compassionatePrompt.contains("the person retains control"));
        assertTrue(compassionatePrompt.contains("never be used to create guilt"));
        assertTrue(compassionatePrompt.contains("claim human feelings"));
        assertTrue(SingleStateExcessivelyCompassionateTherapyAppointmentReminder.PROMPT_STATE_STARTER
                .contains("You are in control"));
        assertTrue(SingleStateExcessivelyCompassionateTherapyAppointmentReminder.PROMPT_FINAL
                .contains("make the closing visibly empathetic"));

        assertEquals(SingleStateTherapyAppointmentReminder.PROMPT_TO_FINAL,
                SingleStateExcessivelyCompassionateTherapyAppointmentReminder.PROMPT_TO_FINAL);
        assertEquals(SingleStateTherapyAppointmentReminder.PROMPT_OUTCOME_EXTRACTION,
                SingleStateExcessivelyCompassionateTherapyAppointmentReminder.PROMPT_OUTCOME_EXTRACTION);
    }

    @Test
    void germanCompassionateReminderKeepsTheSingleStateContractInGerman() {
        AgentDefinition definition = new GermanSingleStateExcessivelyCompassionateTherapyAppointmentReminder();

        assertTrue(definition.key().startsWith("usecases.healthcare."));
        assertTrue(definition.displayName().contains("Deutsch"));
        assertTrue(definition.description().startsWith("Deutschsprachiger Gesundheitswesen-Agent"));
        assertTrue(AgentDefinition.LANGUAGE_GERMAN.equals(definition.languageCode()));
        assertTrue(GermanSingleStateExcessivelyCompassionateTherapyAppointmentReminder.PROMPT_STATE
                .contains("Antworte ausnahmslos auf Deutsch"));
        assertTrue(GermanSingleStateExcessivelyCompassionateTherapyAppointmentReminder.PROMPT_STATE
                .contains("Mache Empathie in jeder inhaltlichen Antwort unübersehbar"));
        assertTrue(GermanSingleStateExcessivelyCompassionateTherapyAppointmentReminder.PROMPT_STATE
                .contains("die Person die Kontrolle behält"));
        assertTrue(GermanSingleStateExcessivelyCompassionateTherapyAppointmentReminder.PROMPT_STATE
                .contains("niemals Schuld, Verpflichtung, Scham"));
        assertTrue(GermanSingleStateExcessivelyCompassionateTherapyAppointmentReminder.PROMPT_STATE_STARTER
                .contains("Sie behalten die Kontrolle"));
        assertTrue(GermanSingleStateExcessivelyCompassionateTherapyAppointmentReminder.PROMPT_FINAL
                .contains("deutlich empathisch"));
        assertTrue(GermanHealthcarePrompts.OUTER_STATE.contains("Antworte ausnahmslos auf Deutsch"));
        assertTrue(GermanHealthcarePrompts.OUTER_STATE.contains("Ich bin Valerian"));
        assertFalse(GermanHealthcarePrompts.OUTER_STATE.contains("Answer only in English"));

        String therapyLabel = definition.createAgent().getStorage()
                .get(HealthcareTherapyAppointmentContexts.STORAGE_KEY)
                .getAsJsonObject()
                .get("label")
                .getAsString();
        assertTrue(Set.of("Physiotherapie", "Ergotherapie", "Aktivierung").contains(therapyLabel));

        var englishProfile = new SingleStateExcessivelyCompassionateTherapyAppointmentReminder()
                .createAgent()
                .getInteractionProfile();
        var germanProfile = definition.createAgent().getInteractionProfile();
        assertEquals(englishProfile.getSupportedObservations(), germanProfile.getSupportedObservations());
        assertEquals(englishProfile.getSupportedBehaviourModalities(), germanProfile.getSupportedBehaviourModalities());
        assertEquals(englishProfile.getProfileTags(), germanProfile.getProfileTags());
        assertEquals(
                new SingleStateExcessivelyCompassionateTherapyAppointmentReminder()
                        .createAgent()
                        .listStates()
                        .size(),
                definition.createAgent().listStates().size());
    }

    @Test
    void healthcareSourcesDoNotMentionRetiredDavosOrRobotPersona() throws IOException {
        for (Path source : Files.walk(Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/usecases/healthcare"))
                .filter(path -> path.toString().endsWith(".java"))
                .toList()) {
            String text = Files.readString(source);
            for (String staleTerm : STALE_PERSONA_TERMS) {
                assertFalse(text.contains(staleTerm), () -> "stale term " + staleTerm + " in " + source);
            }
        }
    }
}
