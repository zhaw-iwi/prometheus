package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.awaitility.Awaitility.await;
import java.time.*;
import java.util.concurrent.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.task.*;
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
    @Test void optedInTaskResponseClaimsOnceAndPersistsSemanticAssociationWithoutExecutingAnotherAction() {
        var storage = new Storage(); storage.put(TaskMemory.PHASE,new JsonPrimitive("RUNNING"));
        storage.put(TaskMemory.ACTIONS,new JsonPrimitive(1));
        Agent agent = agents.saveAndFlush(new Agent("semantic association", "", new TaskState("task",new TaskPolicy(storage,""),false),storage));
        var owner = new ExternalSpeech(UUID.randomUUID(),agent.executionEpoch());
        TaskResponse.expect(storage,owner,UUID.randomUUID(),"Tell a robot joke",Instant.now().minusSeconds(1)); agents.saveAndFlush(agent);
        ownership.acquire(agent.getId(),owner);
        when(gateway.infer(any())).thenAnswer(call -> {
            var request = (ch.zhaw.prometheus.spi.InferenceRequest)call.getArgument(0);
            var data = JsonParser.parseString(request.messages().getLast().getContent()).getAsJsonObject();
            var result = new JsonObject();
            for (String key : List.of("responseId","revision","segmentId")) result.add(key,data.get(key));
            result.addProperty("verdict","MATCH"); return result.toString();
        });
        recording.record(agent.getId(),owner,"ack","Okay",List.of(),SpeechProvenance.Association.NONE,true,Instant.now());
        verifyNoInteractions(gateway);
        for (int i=0;i<2;i++) recording.record(agent.getId(),owner,"joke","My circuits run on coffee today!",List.of(),SpeechProvenance.Association.NONE,true,Instant.now());
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            var loaded=agents.findById(agent.getId()).orElseThrow();var stored=((TaskPolicy)loaded.getCurrentState().ownPolicy()).storage();
            assertEquals("CAPTURED",TaskResponse.status(stored)); assertTrue(TaskResponse.captured(stored,owner));
            assertEquals(1,stored.get(TaskMemory.ACTIONS).getAsInt());
            assertEquals(2,loaded.getEventHistory().toList().size());
        });
        verify(gateway,times(1)).infer(any()); ownership.release(agent.getId(),owner.sessionId());
    }

    @Test void responseCheckDoesNotHoldATransactionOrAgentLockAndOldSuccessCannotReleaseANewResponse() throws Exception {
        var storage=new Storage(); storage.put(TaskMemory.PHASE,new JsonPrimitive("RUNNING"));
        Agent agent=agents.saveAndFlush(new Agent("delayed semantic check","",new TaskState("task",new TaskPolicy(storage,""),false),storage));
        var owner=new ExternalSpeech(UUID.randomUUID(),agent.executionEpoch());
        TaskResponse.expect(storage,owner,UUID.randomUUID(),"Tell a robot joke",Instant.now().minusSeconds(1)); agents.saveAndFlush(agent);
        ownership.acquire(agent.getId(),owner);
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        when(gateway.infer(any())).thenAnswer(call -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            var request=(ch.zhaw.prometheus.spi.InferenceRequest)call.getArgument(0);
            var data=JsonParser.parseString(request.messages().getLast().getContent()).getAsJsonObject();
            var answer=new JsonObject();for(String key:List.of("responseId","revision","segmentId"))answer.add(key,data.get(key));
            answer.addProperty("verdict","MATCH");entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return answer.toString();
        });
        try {
            recording.record(agent.getId(),owner,"old","My circuits run on coffee today!",List.of(),SpeechProvenance.Association.NONE,true,Instant.now());
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            UUID replacement=UUID.randomUUID();
            application.serialized(agent.getId(),() -> new TransactionTemplate(transactions).execute(status -> {
                var loaded=agents.findById(agent.getId()).orElseThrow();var current=((TaskPolicy)loaded.getCurrentState().ownPolicy()).storage();
                TaskResponse.expect(current,owner,replacement,"A new task response",Instant.now());agents.saveAndFlush(loaded);return null;
            }));
            release.countDown();
            // Queue a fence on the same existing worker lane, so assertions run after completion.
            var drained=new CountDownLatch(1);
            var fence=background.reserve(new ch.zhaw.prometheus.model.PreparedAction(UUID.randomUUID(),null,java.util.Map.of(),g->java.util.Map.of()),gateway,
                    (a,v,versions)->{drained.countDown();return "fence";});fence.commit(agent.getId());
            assertTrue(drained.await(5,TimeUnit.SECONDS));
            var loaded=agents.findById(agent.getId()).orElseThrow();var current=((TaskPolicy)loaded.getCurrentState().ownPolicy()).storage();
            assertEquals("PENDING",TaskResponse.status(current));assertFalse(TaskResponse.captured(current,owner));
            verify(gateway,times(1)).infer(any());
        } finally { release.countDown();ownership.release(agent.getId(),owner.sessionId()); }
    }
    @Autowired BackgroundActionExecutor background;
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
