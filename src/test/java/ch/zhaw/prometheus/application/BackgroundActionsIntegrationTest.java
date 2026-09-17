package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.google.gson.*;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.logging.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.repositories.*;
import ch.zhaw.prometheus.spi.*;

@SpringBootTest(properties = "prometheus.runtime.tick.enabled=false")
class BackgroundActionsIntegrationTest {
    @Autowired AgentApplicationService service;
    @Autowired AgentRepository agents;
    @Autowired StorageRepository storages;
    @Autowired BackgroundActionExecutor executor;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean LanguageModelGateway gateway;
    @MockitoBean AgentMonitorBroadcaster monitor;
    @MockitoBean AgentBehaviourBroadcaster behaviour;
    private final List<UUID> created = new ArrayList<>();
    private final List<CountDownLatch> releases = new ArrayList<>();

    @BeforeEach void inference() {
        when(gateway.decide(any())).thenReturn(true);
        when(gateway.infer(any())).thenReturn("{\"speech\":\"Goodbye.\"}");
        when(gateway.extract(any())).thenReturn(JsonParser.parseString("{\"completed\":true}"));
    }
    @AfterEach void cleanup() {
        releases.forEach(CountDownLatch::countDown);
        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(0, executor.retainedAgents()));
        created.forEach(agents::deleteById);
    }
    private record Fixture(UUID agent, UUID storage) {}
    private Fixture create(boolean blocking, int actionCount, boolean legacy) throws Exception {
        Storage storage = new Storage(); var actions = new ArrayList<Action>();
        for (int i = 0; i < actionCount; i++) {
            var action = new StaticExtractionAction("summary-" + i, storage, "outcome");
            if (blocking) action.blocking();
            if (legacy) { var mode = Action.class.getDeclaredField("executionMode"); mode.setAccessible(true); mode.set(action, null); }
            actions.add(action);
        }
        var initial = new State("initial", new PromptPolicy("conversation", "hello", null),
                List.of(new Transition(List.of(new StaticDecision("close")), actions, new Final("final"))));
        Agent agent = agents.saveAndFlush(new Agent("fixture", "background action test", initial, storage));
        created.add(agent.getId());
        // Find the managed storage identity without passing that entity to a worker.
        return new Fixture(agent.getId(), storage.getID());
    }
    private CountDownLatch[] holdExtraction(AtomicInteger calls) {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); releases.add(release);
        doAnswer(invocation -> {
            InferenceRequest request = invocation.getArgument(0);
            if (request.purpose() != InferencePurpose.EXTRACTION) return "{\"speech\":\"Goodbye.\"}";
            int call = calls.incrementAndGet(); entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return "{\"completed\":true,\"sequence\":" + call + "}";
        }).when(gateway).infer(any());
        return new CountDownLatch[] { entered, release };
    }
    private void close(Fixture fixture) { assertTrue(service.acknowledge(fixture.agent,
            new EventRequest(Event.TYPE_USER_UTTERANCE, "user", "observation", "finish")).isPresent()); }
    private void idle() { await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(0, executor.retainedAgents())); }

    @Test void legacySummaryFinishesAfterFarewellAndPersistsThroughFreshReload() throws Exception {
        var calls = new AtomicInteger(); var gates = holdExtraction(calls); var fixture = create(false, 1, true);
        close(fixture); assertTrue(gates[0].await(5, TimeUnit.SECONDS));
        Agent closed = agents.findById(fixture.agent).orElseThrow();
        assertFalse(closed.isActive()); assertFalse(closed.getStorage().containsKey("outcome"));
        assertEquals("{\"speech\":\"Goodbye.\"}", closed.getEventHistory().toList().getLast().getPayload());
        verify(behaviour, times(1)).publish(eq(fixture.agent), any());
        gates[1].countDown(); idle();
        assertTrue(agents.findById(fixture.agent).orElseThrow().getStorage().get("outcome").getAsJsonObject().get("completed").getAsBoolean());
        assertEquals(1, calls.get());
        verify(behaviour, times(1)).publish(eq(fixture.agent), any());
    }

    @Test void resetAndDeletionDiscardLateResults() throws Exception {
        for (boolean delete : List.of(false, true)) {
            var gates = holdExtraction(new AtomicInteger()); var fixture = create(false, 1, false);
            close(fixture); assertTrue(gates[0].await(5, TimeUnit.SECONDS));
            if (delete) service.serialized(fixture.agent, () -> { agents.deleteById(fixture.agent); return null; });
            else service.reset(fixture.agent);
            gates[1].countDown(); idle();
            if (delete) { assertTrue(agents.findById(fixture.agent).isEmpty()); created.remove(fixture.agent); }
            else { Agent reset = agents.findById(fixture.agent).orElseThrow(); assertTrue(reset.isActive()); assertFalse(reset.getStorage().containsKey("outcome")); }
        }
    }

    @Test void queuedWritesToSameDestinationRemainOrdered() throws Exception {
        var calls = new AtomicInteger(); var gates = holdExtraction(calls); var fixture = create(false, 2, false);
        close(fixture); assertTrue(gates[0].await(5, TimeUnit.SECONDS)); gates[1].countDown(); idle();
        assertEquals(2, calls.get());
        assertEquals(2, agents.findById(fixture.agent).orElseThrow().getStorage().get("outcome").getAsJsonObject().get("sequence").getAsInt());
    }

    @Test void newerForegroundWriteIsNeverOverwrittenByBackgroundResult() throws Exception {
        var gates = holdExtraction(new AtomicInteger()); var fixture = create(false, 1, false);
        close(fixture); assertTrue(gates[0].await(5, TimeUnit.SECONDS));
        service.serialized(fixture.agent, () -> new TransactionTemplate(transactions).execute(tx -> {
            Storage storage = storages.findById(fixture.storage).orElseThrow();
            storage.put("outcome", new JsonPrimitive("newer")); storages.saveAndFlush(storage); return null;
        }));
        gates[1].countDown(); idle();
        assertEquals("newer", agents.findById(fixture.agent).orElseThrow().getStorage().get("outcome").getAsString());
    }

    @Test void explicitBlockingActionAndRollbackKeepTheirContracts() throws Exception {
        var blocking = create(true, 1, false); close(blocking);
        assertTrue(agents.findById(blocking.agent).orElseThrow().getStorage().containsKey("outcome"));
        verify(gateway, times(1)).extract(any());
        var rolledBack = create(false, 1, false);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> { close(rolledBack); tx.setRollbackOnly(); });
        assertTrue(agents.findById(rolledBack.agent).orElseThrow().isActive());
        assertEquals(64, executor.availableCapacity());
        verify(gateway, never()).infer(argThat(request -> request.purpose() == InferencePurpose.EXTRACTION));
    }

    @Test void failedBackgroundInferenceLeavesFarewellAndStorageIntact() throws Exception {
        when(gateway.infer(any())).thenAnswer(invocation -> {
            InferenceRequest request = invocation.getArgument(0);
            if (request.purpose() == InferencePurpose.EXTRACTION) throw new IllegalStateException("fixture failure");
            return "{\"speech\":\"Goodbye.\"}";
        });
        var fixture = create(false, 1, false); close(fixture); idle();
        Agent closed = agents.findById(fixture.agent).orElseThrow();
        assertFalse(closed.isActive()); assertFalse(closed.getStorage().containsKey("outcome"));
        assertEquals(2, closed.getEventHistory().toList().size());
    }
}
