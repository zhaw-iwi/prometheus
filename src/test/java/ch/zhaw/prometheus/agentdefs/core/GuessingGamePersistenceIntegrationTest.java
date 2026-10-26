package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ch.zhaw.prometheus.agentdefs.usecases.healthcare.SingleStateGuessingGameUserGuess;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.guessing.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest(properties = "prometheus.runtime.tick.enabled=false")
class GuessingGamePersistenceIntegrationTest {
    @Autowired AgentRepository agents;
    @MockitoBean LanguageModelGateway language;
    Agent reload(Agent agent) { return agents.findById(agents.saveAndFlush(agent).getId()).orElseThrow(); }
    @Test void bothDefinitionsReloadAnUnchangedSecretRevealAndFreshRound() {
        for (var candidate : List.of(new RoleClarificationGuessingGame().createAgent(), new SingleStateGuessingGameUserGuess().createAgent())) {
            GuessingGameUnitTest.controls(language, true); when(language.infer(any())).thenReturn("{\"operation\":\"START\"}");
            var agent = reload(candidate); var live = new PolicyRuntime(new PromptMessageAssembler(), language)
                .withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch()));
            agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "You choose, I guess. Start now."), live); agent = reload(agent);
            var original = GuessingMemory.record(GuessingGameUnitTest.storage(agent)); assertEquals("ACTIVE", original.get("phase").getAsString());
            when(language.infer(any())).thenReturn("{\"operation\":\"KEEP\"}");
            agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Is it alive?"), live); agent = reload(agent);
            assertEquals(original.get("secret"), GuessingMemory.record(GuessingGameUnitTest.storage(agent)).get("secret"));
            agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "I give up"), live); agent = reload(agent);
            var revealed = GuessingMemory.record(GuessingGameUnitTest.storage(agent)); assertEquals("REVEALED", revealed.get("phase").getAsString());
            assertEquals(original.get("secret"), revealed.get("secret"));
            when(language.infer(any())).thenReturn("{\"operation\":\"RESTART\"}");
            agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Another round please"), live); agent = reload(agent);
            var next = GuessingMemory.record(GuessingGameUnitTest.storage(agent)); assertNotEquals(original.get("id"), next.get("id")); assertNotEquals(original.get("secret"), next.get("secret"));
            agent.reset(); agent = reload(agent); assertFalse(GuessingMemory.record(GuessingGameUnitTest.storage(agent)).has("secret"));
        }
    }
}
