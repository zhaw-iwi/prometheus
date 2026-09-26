package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest(properties = "prometheus.runtime.tick.enabled=false")
class LiveSpeechPersistenceIntegrationTest {
    @Autowired AgentRepository agents;
    @Autowired ExternalSpeechRecordingService recording;
    @Autowired ExternalSpeechOwnership ownership;
    @Autowired AgentApplicationService application;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean LanguageModelGateway gateway;
    @Test void nativeIntentAndRealizationSurviveReloadWithoutDuplicateConversationOrStateExecution() {
        when(gateway.infer(any())).thenReturn("{\"speech\":\"Planned result\"}");
        Agent agent = agents.saveAndFlush(new Agent("speech persistence", "", new State("talk", new PromptPolicy("Speak", null, null), List.of())));
        var owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch()); agents.saveAndFlush(agent);
        ownership.acquire(agent.getId(), owner);
        application.start(agent.getId());
        Agent started = agents.findById(agent.getId()).orElseThrow();
        Event intent = started.getEventHistory().toList().getFirst();
        assertTrue(ConversationProjection.isIntent(intent));
        Event nativeOne = recording.record(agent.getId(), owner, "native-1", "Realized result", List.of(intent.getId()),
                SpeechProvenance.Association.CONFIRMED, true).orElseThrow();
        Event replay = recording.record(agent.getId(), owner, "native-1", "Realized result", List.of(intent.getId()),
                SpeechProvenance.Association.CONFIRMED, true).orElseThrow();
        assertEquals(nativeOne.getId(), replay.getId());
        Event ambiguous = recording.record(agent.getId(), owner, "native-2", "Possibly related words", List.of(intent.getId()),
                SpeechProvenance.Association.AMBIGUOUS, false).orElseThrow();
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            Agent reloaded = agents.findById(agent.getId()).orElseThrow();
            var events = reloaded.getEventHistory().toList(); assertEquals(3, events.size());
            assertEquals(List.of(intent.getId(), nativeOne.getId(), ambiguous.getId()), events.stream().map(Event::getId).toList());
            assertEquals("Planned result", BehaviourPlan.fromJson(events.getFirst().getPayload()).getSpeech());
            assertNull(BehaviourPlan.fromJson(ConversationProjection.view(events.getFirst()).payload()).getSpeech());
            assertEquals(SpeechProvenance.Association.CONFIRMED, events.get(1).speechProvenance().association());
            assertEquals(List.of(intent.getId()), events.get(1).speechProvenance().intentIds());
            assertEquals(SpeechProvenance.Association.AMBIGUOUS, events.getLast().speechProvenance().association());
            assertFalse(events.getLast().speechProvenance().complete());
            var messages = new PromptMessageAssembler().compose(reloaded.getCurrentState().getEventHistory(), "context");
            assertEquals(3, messages.size()); assertEquals("Realized result", messages.get(1).getContent());
            assertEquals("talk", reloaded.getCurrentState().getName());
        });
        verify(gateway, times(1)).infer(any());
        ownership.revoke(agent.getId());
        assertTrue(recording.record(agent.getId(), owner, "late", "obsolete", List.of(), SpeechProvenance.Association.NONE, true).isEmpty());
        assertThrows(BehaviourSpeechUnavailableException.class, () -> ScopedBehaviourSpeechService.canonicalSpeech(intent));
    }
}
