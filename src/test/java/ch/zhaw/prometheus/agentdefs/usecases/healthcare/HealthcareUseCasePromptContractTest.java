package ch.zhaw.prometheus.agentdefs.usecases.healthcare;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.agentdefs.AgentDefinition;

class HealthcareUseCasePromptContractTest {
    private static final List<AgentDefinition> DEFINITIONS = List.of(
            new SingleStateGuessingGame(),
            new SingleStateGuessingGameUserGuess(),
            new SingleStateHealthcareConversation(),
            new SingleStateSmartGoalCoaching(),
            new SingleStateTherapyAppointmentReminder(),
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
