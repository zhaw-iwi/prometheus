package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.*;

class BehaviourSpeculationServiceUnitTest {
    private final UUID id = UUID.randomUUID(), epoch = UUID.randomUUID(), eventId = UUID.randomUUID();
    private final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    private final PromptMessageAssembler assembler = new PromptMessageAssembler();
    private record Input(State state, Event event, InferenceRequest request) {}
    private Input input(String prompt) {
        var policy = new PromptPolicy(prompt, null, null);
        var state = new State("state", policy, List.of(new Transition(new StaticDecision("close?"), new Final("final"))));
        var history = new EventHistory(); state.setEventHistory(history);
        var event = Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "hello").withStatePath("state");
        history.appendEvent(event);
        return new Input(state, event, policy.responseRequest(state.getEventHistory(), assembler));
    }
    private BehaviourSpeculationService.Scope prepare(BehaviourSpeculationService service, UUID agent, Input input) {
        when(gateway.supportsBehaviourSpeculation()).thenReturn(true);
        var scope = service.open(agent, epoch, true);
        scope.prepare(input.state, input.event, new PolicyRuntime(assembler, gateway));
        return scope;
    }
    private void retain(BehaviourSpeculationService.Scope scope) { scope.result(eventId, false); scope.commit(); scope.close(); }

    @Test void reusesOneInFlightRequestAcrossTheHttpBoundary() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); Input input = input("ordinary");
        when(gateway.infer(any())).thenAnswer(invocation -> { entered.countDown(); waitFor(release); return "{\"speech\":\"reused\"}"; });
        try (var service = new BehaviourSpeculationService(true, 1, 2, 30000); var consumer = Executors.newSingleThreadExecutor()) {
            retain(prepare(service, id, input)); assertTrue(entered.await(5, TimeUnit.SECONDS));
            var response = consumer.submit(() -> service.forGeneration(id, epoch, eventId, gateway).infer(input.request));
            release.countDown(); assertEquals("{\"speech\":\"reused\"}", response.get(5, TimeUnit.SECONDS));
            verify(gateway, times(1)).infer(any()); assertEquals(0, service.retainedCandidates());
        } finally { release.countDown(); }
    }

    @Test void changedInputsDiscardWithoutWaitingAndSaturationDoesNotDelayRequiredWork() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var old = input("old"); var fresh = input("fresh");
        doAnswer(invocation -> {
            InferenceRequest request = invocation.getArgument(0);
            if (request.messages().getFirst().getContent().equals("old")) {
                entered.countDown(); waitIgnoringInterrupt(release); return "{\"speech\":\"obsolete\"}";
            }
            return "{\"speech\":\"fresh\"}";
        }).when(gateway).infer(any());
        try (var service = new BehaviourSpeculationService(true, 1, 2, 30000); var caller = Executors.newSingleThreadExecutor()) {
            retain(prepare(service, id, old)); assertTrue(entered.await(5, TimeUnit.SECONDS));
            UUID other = UUID.randomUUID(); retain(prepare(service, other, fresh)); // Worker permit is occupied, so skip.
            assertEquals(1, service.retainedCandidates());
            assertEquals("{\"speech\":\"fresh\"}", caller.submit(() ->
                    service.forGeneration(other, epoch, eventId, gateway).infer(fresh.request)).get(2, TimeUnit.SECONDS));
            assertEquals("{\"speech\":\"fresh\"}", caller.submit(() ->
                    service.forGeneration(id, epoch, eventId, gateway).infer(fresh.request)).get(2, TimeUnit.SECONDS));
            assertEquals(0, service.retainedCandidates()); assertEquals(0, service.availableWorkers());
            release.countDown(); await().untilAsserted(() -> assertEquals(1, service.availableWorkers()));
        } finally { release.countDown(); }
    }

    @Test void selfTransitionDiscardsEvenAnIdenticalCompletedRequest() throws Exception {
        when(gateway.infer(any())).thenReturn("{\"speech\":\"fixture\"}");
        var input = input("same");
        try (var service = new BehaviourSpeculationService(true, 1, 2, 30000); var scope = prepare(service, id, input)) {
            await().untilAsserted(() -> verify(gateway, times(1)).infer(any()));
            new Transition(input.state).action(input.state, new PolicyRuntime(assembler, gateway).withBehaviourSpeculation(scope));
            scope.result(eventId, true); scope.commit();
            assertEquals(0, service.retainedCandidates());
            service.forGeneration(id, epoch, eventId, gateway).infer(input.request);
            verify(gateway, times(2)).infer(any());
        }
    }

    @Test void requiredFailureIsReportedOnceButDiscardedFailureDoesNotFailTheTurn() {
        when(gateway.infer(any())).thenThrow(new IllegalStateException("private provider detail"));
        var input = input("same");
        try (var service = new BehaviourSpeculationService(true, 1, 2, 30000)) {
            retain(prepare(service, id, input));
            var error = assertThrows(IllegalStateException.class,
                    () -> service.forGeneration(id, epoch, eventId, gateway).infer(input.request));
            assertFalse(error.toString().contains("private")); verify(gateway, times(1)).infer(any());
            try (var discarded = prepare(service, id, input)) { assertDoesNotThrow(discarded::invalidate); }
        }
    }

    @Test void rollbackExpiryAndChangedInputIdentityPreventReuse() {
        var clock = new AtomicLong(); when(gateway.infer(any())).thenReturn("{\"speech\":\"fixture\"}"); var input = input("same");
        try (var service = new BehaviourSpeculationService(true, 1, 2, 10, clock::get)) {
            TransactionSynchronizationManager.initSynchronization();
            try {
                retain(prepare(service, id, input));
                TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
                assertEquals(0, service.retainedCandidates());
            } finally { TransactionSynchronizationManager.clearSynchronization(); }
            await().untilAsserted(() -> assertEquals(1, service.availableWorkers()));
            retain(prepare(service, id, input));
            clock.set(TimeUnit.MILLISECONDS.toNanos(11)); service.expire(); assertEquals(0, service.retainedCandidates());
            await().untilAsserted(() -> assertEquals(1, service.availableWorkers()));
            retain(prepare(service, id, input));
            service.forGeneration(id, epoch, UUID.randomUUID(), gateway).infer(input.request);
            assertEquals(0, service.retainedCandidates());
        }
    }

    private static void waitFor(CountDownLatch latch) throws InterruptedException { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
    private static void waitIgnoringInterrupt(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) { try { assertTrue(latch.await(5, TimeUnit.SECONDS)); break; } catch (InterruptedException ignored) { interrupted = true; } }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
