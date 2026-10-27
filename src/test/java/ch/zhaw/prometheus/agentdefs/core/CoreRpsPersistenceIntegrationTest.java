package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.rps.RpsStorageKeys;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.*;

@SpringBootTest(properties = "prometheus.runtime.tick.enabled=false")
class CoreRpsPersistenceIntegrationTest {
    @Autowired AgentRepository agents;
    @MockitoBean LanguageModelGateway language;
    Agent reload(Agent agent) { return agents.findById(agents.saveAndFlush(agent).getId()).orElseThrow(); }
    @Test void twoRoundsReloadAtEveryBoundaryAndIgnoreOldCaptureAndPlayback() {
        when(language.decide(any())).thenAnswer(call -> {
            List<PromptMessage> messages = call.getArgument(0);
            String prompt = messages.getFirst().getContent();
            return prompt.contains("clearly ready to start a round") || prompt.contains("wants to play another round");
        });
        var agent = reload(new RockScissorPaper().createAgent());
        var owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
        var runtime = new PolicyRuntime(new PromptMessageAssembler(), language).withExternalSpeech(owner);
        String oldId = null;
        for (int number = 1; number <= 2; number++) {
            agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", number == 1 ? "Ready" : "Again"), runtime);
            agent = reload(agent); var storage = CoreRpsRoundUnitTest.storage(agent);
            assertEquals("INTRO", CoreRpsRound.phase(storage));
            String id = CoreRpsRound.text(CoreRpsRound.round(storage), "id"); assertNotEquals(oldId, id);
            var policy = (CoreRpsActivityPolicy) agent.reachableStates().stream().filter(s -> s.getName().contains("Reveal Sign")).findFirst().orElseThrow().ownPolicy();
            Instant now = Instant.now();
            var speech = agent.recordExternalSpeech("Show one hand. One, two, three.", new SpeechProvenance(SpeechProvenance.Origin.NATIVE,
                    owner.sessionId(), owner.epoch(), "count-" + number, SpeechProvenance.Association.NONE, List.of(), true));
            assertTrue(policy.liveSpeechCaptured(speech, owner, now, Instant.now(), List.of())); agent = reload(agent);
            if (oldId != null) assertNull(agent.acknowledge(new Event(CoreRpsRound.PLAYED, "system", Event.KIND_SYSTEM, oldId), runtime));
            assertNotNull(agent.acknowledge(new Event(CoreRpsRound.PLAYED, "system", Event.KIND_SYSTEM, id), runtime));
            agent = reload(agent); storage = CoreRpsRoundUnitTest.storage(agent); assertEquals("WAITING", CoreRpsRound.phase(storage));
            if (oldId != null) assertNull(agent.acknowledge(CoreRpsRoundUnitTest.sign(oldId, Instant.now(), 1), runtime));
            agent.acknowledge(CoreRpsRoundUnitTest.sign(id, Instant.now(), 1), runtime);
            agent = reload(agent); storage = CoreRpsRoundUnitTest.storage(agent);
            assertEquals("RESULT", CoreRpsRound.phase(storage)); assertEquals(number, storage.get(CoreRpsRound.COUNT).getAsInt());
            assertEquals(number, storage.get(RpsStorageKeys.ROUNDS).getAsJsonArray().size());
            assertNull(agent.acknowledge(CoreRpsRoundUnitTest.sign(id, Instant.now(), 1), runtime)); oldId = id;
        }
        agent.reset(); agent = reload(agent); assertFalse(CoreRpsRoundUnitTest.storage(agent).containsKey(CoreRpsRound.KEY));
        verify(language, never()).infer(any());
    }
}
