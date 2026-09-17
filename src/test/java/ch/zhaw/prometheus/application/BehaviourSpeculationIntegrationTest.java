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
import com.google.gson.JsonPrimitive;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.logging.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.*;

@SpringBootTest(properties = "prometheus.runtime.tick.enabled=false")
class BehaviourSpeculationIntegrationTest {
    @Autowired AgentApplicationService service;
    @Autowired AgentRepository agents;
    @Autowired BehaviourSpeculationService speculation;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean LanguageModelGateway gateway;
    @MockitoBean AgentMonitorBroadcaster monitor;
    @MockitoBean AgentBehaviourBroadcaster behaviour;
    final List<UUID> created = new ArrayList<>();
    final List<CountDownLatch> releases = new ArrayList<>();

    @BeforeEach void setup() { when(gateway.supportsBehaviourSpeculation()).thenReturn(true); }
    @AfterEach void cleanup() {
        releases.forEach(CountDownLatch::countDown);
        created.forEach(id -> speculation.discard(id, "test_cleanup"));
        await().untilAsserted(() -> assertEquals(2, speculation.availableWorkers()));
        created.forEach(agents::deleteById);
    }

    private UUID create(String transitionKind, boolean blockingDependency, boolean regulated) {
        var storage = new Storage();
        State current = new State("current", new PromptPolicy("current instructions", "hello", null), List.of());
        State next = blockingDependency ? new State("next", new PromptPolicy("Use ${choice}", "respond", null,
                storage, List.of("choice"), PromptValueShape.PRIMITIVE), List.of()) : new Final("done");
        List<Action> actions = blockingDependency ? List.of(new StaticExtractionAction("extract choice", storage, "choice").blocking()) : List.of();
        Transition transition = new Transition(List.of(new StaticDecision("transition?")), actions,
                transitionKind.equals("self") ? current : next);
        State root = current;
        if (transitionKind.equals("outer")) root = new OuterState("outer instructions", "outer", List.of(transition), current);
        else {
            current.addTransition(transition);
            if (transitionKind.equals("inner")) root = new OuterState("outer instructions", "outer", List.of(), current);
        }
        Agent agent = new Agent("fixture", "speculation integration", root, storage);
        if (regulated) agent.setRegulationSystem(new ch.zhaw.prometheus.model.commons.regulation.ZurichRegulationSystem(
                0, 0, 0, 0, .6, .2, .5));
        UUID id = agents.saveAndFlush(agent).getId(); created.add(id); return id;
    }
    private static EventRequest input(String text) { return new EventRequest(Event.TYPE_USER_UTTERANCE, "user", "observation", text); }
    private CountDownLatch[] slowFirstBehaviour(AtomicInteger calls) {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); releases.add(release);
        doAnswer(invocation -> {
            InferenceRequest request = invocation.getArgument(0);
            assertEquals(InferencePurpose.BEHAVIOUR, request.purpose());
            int call = calls.incrementAndGet();
            if (call == 1) {
                entered.countDown();
                boolean interrupted = false;
                while (true) { try { assertTrue(release.await(10, TimeUnit.SECONDS)); break; } catch (InterruptedException ignored) { interrupted = true; } }
                if (interrupted) Thread.currentThread().interrupt();
                return "{\"speech\":\"candidate\"}";
            }
            if (request.messages().getFirst().getContent().startsWith("Use")) assertTrue(request.messages().toString().contains("chosen"));
            return "{\"speech\":\"fresh\"}";
        }).when(gateway).infer(any());
        return new CountDownLatch[]{ entered, release };
    }

    @Test void decisionAndBehaviourOverlapThenGenerationReusesAcrossPersistedReload() throws Exception {
        var calls = new AtomicInteger(); var gates = slowFirstBehaviour(calls); UUID id = create("inner", false, false);
        when(gateway.decide(any())).thenAnswer(invocation -> { assertTrue(gates[0].await(5, TimeUnit.SECONDS)); return false; });
        assertNull(service.acknowledge(id, input("hello")).orElseThrow().getResponseEvent());
        assertEquals(1, speculation.retainedCandidates());
        assertEquals(1, agents.findById(id).orElseThrow().getEventHistory().toList().size());
        verify(behaviour, never()).publish(any(), any());
        gates[1].countDown();
        assertEquals(BehaviourGenerationOutcome.GENERATED, service.generate(id, List.of()));
        assertEquals(1, calls.get());
        assertEquals("{\"speech\":\"candidate\"}", agents.findById(id).orElseThrow().getEventHistory().toList().getLast().getPayload());
        verify(behaviour, times(1)).publish(eq(id), any());
        assertEquals(0, speculation.retainedCandidates());
    }

    @Test void outerInnerSelfAndFinalTransitionsNeverWaitForObsoleteInference() throws Exception {
        for (String kind : List.of("outer", "inner", "self", "final")) {
            var calls = new AtomicInteger(); var gates = slowFirstBehaviour(calls); UUID id = create(kind, false, false);
            doAnswer(invocation -> { assertTrue(gates[0].await(5, TimeUnit.SECONDS)); return true; }).when(gateway).decide(any());
            try (var caller = Executors.newSingleThreadExecutor()) {
                var result = caller.submit(() -> service.acknowledge(id, input("transition"))).get(5, TimeUnit.SECONDS);
                assertEquals("{\"speech\":\"fresh\"}", result.orElseThrow().getResponseEvent().getPayload());
                assertEquals(1, gates[1].getCount()); assertEquals(2, calls.get());
                assertEquals(0, speculation.retainedCandidates());
            } finally { gates[1].countDown(); }
            await().untilAsserted(() -> assertEquals(2, speculation.availableWorkers()));
            assertEquals(2, agents.findById(id).orElseThrow().getEventHistory().toList().size());
        }
    }

    @Test void blockingActionCompletesBeforeNewStatePromptIsAssembled() throws Exception {
        var calls = new AtomicInteger(); var gates = slowFirstBehaviour(calls); UUID id = create("final", true, false);
        when(gateway.decide(any())).thenAnswer(invocation -> { assertTrue(gates[0].await(5, TimeUnit.SECONDS)); return true; });
        when(gateway.extract(any())).thenReturn(new JsonPrimitive("chosen"));
        assertEquals("{\"speech\":\"fresh\"}", service.acknowledge(id, input("transition")).orElseThrow().getResponseEvent().getPayload());
        assertEquals("chosen", agents.findById(id).orElseThrow().getStorage().get("choice").getAsString());
        gates[1].countDown();
    }

    @Test void resetSensoryAndRegulationPathsPreserveExistingPublicationRules() throws Exception {
        var calls = new AtomicInteger(); var gates = slowFirstBehaviour(calls); UUID id = create("final", false, false);
        when(gateway.decide(any())).thenReturn(false);
        service.acknowledge(id, input("hello")); assertTrue(gates[0].await(5, TimeUnit.SECONDS));
        service.reset(id); gates[1].countDown();
        assertEquals(0, speculation.retainedCandidates());
        assertEquals(1, agents.findById(id).orElseThrow().getEventHistory().toList().size());
        service.acknowledge(id, new EventRequest(Event.TYPE_FACE_EMOTION, "user", "observation", "{}"));
        assertEquals(2, calls.get()); assertEquals(0, speculation.retainedCandidates());
        UUID regulated = create("final", false, true);
        service.acknowledge(regulated, input("hello"));
        assertEquals(2, calls.get()); assertEquals(0, speculation.retainedCandidates());
    }

    @Test void newerInputAndRollbackInvalidateEarlierCandidates() throws Exception {
        var calls = new AtomicInteger(); var gates = slowFirstBehaviour(calls); UUID id = create("final", false, false);
        when(gateway.decide(any())).thenReturn(false);
        service.acknowledge(id, input("first")); assertTrue(gates[0].await(5, TimeUnit.SECONDS));
        service.acknowledge(id, input("second"));
        assertEquals(BehaviourGenerationOutcome.GENERATED, service.generate(id, List.of()));
        assertEquals("{\"speech\":\"fresh\"}", agents.findById(id).orElseThrow().getEventHistory().toList().getLast().getPayload());
        assertEquals(2, calls.get()); gates[1].countDown();
        await().untilAsserted(() -> assertEquals(2, speculation.availableWorkers()));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.acknowledge(id, input("rolled back")); tx.setRollbackOnly();
        });
        assertEquals(0, speculation.retainedCandidates());
        assertEquals(3, agents.findById(id).orElseThrow().getEventHistory().toList().size());
    }

    @Test void malformedRequiredCandidateFailsWithoutPublicationOrRepairRequest() {
        var calls = new AtomicInteger();
        when(gateway.infer(any())).thenAnswer(invocation -> { calls.incrementAndGet(); return "invalid JSON"; });
        when(gateway.decide(any())).thenReturn(false); UUID id = create("final", false, false);
        service.acknowledge(id, input("hello"));
        assertThrows(IllegalStateException.class, () -> service.generate(id, List.of()));
        assertEquals(1, calls.get()); verify(behaviour, never()).publish(any(), any());
        assertEquals(1, agents.findById(id).orElseThrow().getEventHistory().toList().size());
    }
}
