package ch.zhaw.prometheus.definition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class LegacyRuntimeRemovalContractTest {
    private static final Path MAIN = Path.of("src/main/java/ch/zhaw/prometheus");

    @Test
    void productionTreeContainsNoLegacyDefinitionOrStaticGraphPersistencePath() throws Exception {
        if (Files.exists(MAIN.resolve("agentdefs"))) {
            assertFalse(Files.walk(MAIN.resolve("agentdefs")).anyMatch(Files::isRegularFile));
        }
        assertFalse(Files.exists(MAIN.resolve("repositories/AgentRepository.java")));
        assertFalse(Files.exists(MAIN.resolve("model/State.java")));
        assertFalse(Files.exists(MAIN.resolve("model/Transition.java")));
        assertFalse(Files.exists(MAIN.resolve("model/policy/Policy.java")));
        assertFalse(Files.exists(MAIN.resolve("controllers/dto/SingleStateAgentCreateDTO.java")));

        String production = Files.walk(MAIN).filter(path -> path.toString().endsWith(".java"))
                .map(LegacyRuntimeRemovalContractTest::read).collect(java.util.stream.Collectors.joining("\n"));
        assertFalse(production.contains("AgentDefinitionRegistry"));
        assertFalse(production.contains("createSingleStateAgent"));
        assertFalse(production.contains("agent/singlestate"));
        assertFalse(production.contains("repositories.AgentRepository"));

        String agent = Files.readString(MAIN.resolve("model/Agent.java"));
        String event = Files.readString(MAIN.resolve("model/event/Event.java"));
        assertFalse(agent.contains("jakarta.persistence"));
        assertFalse(event.contains("jakarta.persistence"));
        assertTrue(agent.contains("definitionRevisionId"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
