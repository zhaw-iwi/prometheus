package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.agentdefs.AgentDefinition;

class ValerianCorePromptContractTest {
    private static final List<AgentDefinition> DEFINITIONS = List.of(
            new FacialExpressionSensitivity(),
            new MultimodalBehaviour(),
            new RockScissorPaper(),
            new RockScissorPaperMatch(),
            new RoleClarificationGuessingGame(),
            new SocialContextSensitivity());

    private static final List<String> STALE_PERSONA_TERMS = List.of(
            "GIGI",
            "gigi",
            "TDSR",
            "tdsr",
            "Tour de Suisse",
            "Davos",
            "Hotel Grischa",
            "Jee-jee",
            "Chee-chee",
            "robot",
            "Roboter");

    @Test
    void coreOuterPersonaIsValerianDigitalAgentPersona() {
        String persona = ValerianCorePrompts.OUTER_STATE;

        assertTrue(persona.contains("You are Valerian"));
        assertTrue(persona.contains("manifestation of a digital agent"));
        assertTrue(persona.contains("SIRA Lab"));
        assertTrue(persona.contains("PROMETHEUS"));
        assertTrue(persona.contains("rapid prototyping"));
        assertTrue(persona.contains("human-AI collaboration"));
        assertTrue(persona.contains("Answer only in English"));
        assertTrue(persona.contains("mystery fog"));
    }

    @Test
    void coreDefinitionsUseCoreNamespaceEnglishAndDisplayName() {
        for (AgentDefinition definition : DEFINITIONS) {
            assertTrue(definition.key().startsWith("core."), definition.key());
            assertTrue(definition.displayName().startsWith("Valerian Core"), definition.key());
            assertTrue(definition.description().toLowerCase().startsWith("english core"), definition.key());
            assertTrue(AgentDefinition.LANGUAGE_ENGLISH.equals(definition.languageCode()), definition.key());
        }
    }

    @Test
    void germanMatchUsesGermanLanguageMetadataAndPrompts() {
        AgentDefinition definition = new GermanRockScissorPaperMatch();

        assertTrue(definition.key().startsWith("core."));
        assertTrue(definition.displayName().contains("Deutsch"));
        assertTrue(definition.description().startsWith("Deutschsprachiger Core"));
        assertTrue(AgentDefinition.LANGUAGE_GERMAN.equals(definition.languageCode()));
        assertTrue(GermanRockScissorPaperMatch.PROMPT_SETUP.contains("Antworte ausnahmslos auf Deutsch"));
        assertTrue(GermanRockScissorPaperMatch.PROMPT_STARTER.contains("Schere, Stein, Papier"));
        assertTrue(GermanRockScissorPaperMatch.PROMPT_OUTER_STATE.contains("Antworte ausnahmslos auf Deutsch"));
        assertFalse(GermanRockScissorPaperMatch.PROMPT_OUTER_STATE.contains("Answer only in English"));
    }

    @Test
    void coreSourcesDoNotMentionRetiredRobotOrTdsrPersona() throws IOException {
        for (Path source : Files.walk(Path.of("src/main/java/ch/zhaw/prometheus/agentdefs/core"))
                .filter(path -> path.toString().endsWith(".java"))
                .toList()) {
            String text = Files.readString(source);
            for (String staleTerm : STALE_PERSONA_TERMS) {
                assertFalse(text.contains(staleTerm), () -> "stale term " + staleTerm + " in " + source);
            }
        }
    }
}
