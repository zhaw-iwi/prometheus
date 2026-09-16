package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ch.zhaw.prometheus.agentdefs.usecases.healthcare.SingleStateSmartGoalCoaching;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.logging.*;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.*;

@SpringBootTest(properties = "prometheus.runtime.tick.enabled=false")
class ParallelAgentTurnsIntegrationTest {
    @Autowired AgentApplicationService service;
    @Autowired AgentRepository repository;
    @Autowired GuardInferenceExecutor executor;
    @MockitoBean LanguageModelGateway gateway;
    @MockitoBean AgentMonitorBroadcaster monitor;
    @MockitoBean AgentBehaviourBroadcaster behaviour;
    final List<UUID> created = new ArrayList<>();

    @AfterEach void removeOnlyTestAgents() { created.forEach(repository::deleteById); }

    @Test void sameAgentTurnsReloadInOrderWhileAnotherAgentProceedsAndExtractionRunsOnce() throws Exception {
        var started = new CountDownLatch(2); var release = new CountDownLatch(1); var closing = new AtomicBoolean();
        when(gateway.guardInferenceOptions()).thenReturn(new GuardInferenceOptions(GuardInferenceOptions.Strategy.PARALLEL, 16, 65536));
        when(gateway.guardExecutor()).thenReturn(executor);
        when(gateway.infer(any())).thenAnswer(invocation -> {
            InferenceRequest request = invocation.getArgument(0);
            if (request.purpose() != InferencePurpose.DECISION) return "{\"speech\":\"Ready.\",\"nonVerbal\":{}}";
            assertTrue(Thread.currentThread().isVirtual());
            if (request.messages().stream().anyMatch(message -> message.getContent().contains("first-turn"))) {
                started.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS));
            }
            return Boolean.toString(closing.get());
        });
        when(gateway.complete(any())).thenReturn("Finished.");
        when(gateway.extract(any())).thenReturn(com.google.gson.JsonParser.parseString("{\"completed\":true}"));
        UUID first = create(), other = create();
        try (var turns = Executors.newFixedThreadPool(3)) {
            var firstTurn = turns.submit(() -> service.acknowledge(first, input("first-turn")));
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                var secondTurn = turns.submit(() -> service.acknowledge(first, input("second-turn")));
                var otherTurn = turns.submit(() -> service.acknowledge(other, input("other-agent")));
                assertTrue(otherTurn.get(5, TimeUnit.SECONDS).isPresent());
                release.countDown();
                assertTrue(firstTurn.get(5, TimeUnit.SECONDS).isPresent());
                assertTrue(secondTurn.get(5, TimeUnit.SECONDS).isPresent());
            } finally { release.countDown(); }
        }
        assertEquals(List.of("first-turn", "second-turn"), userInputs(first));
        assertEquals(List.of("other-agent"), userInputs(other));
        closing.set(true);
        assertTrue(service.acknowledge(first, input("close-turn")).isPresent());
        Agent reloaded = repository.findById(first).orElseThrow();
        assertFalse(reloaded.isActive()); assertTrue(reloaded.getStorage().get("outcome").getAsJsonObject().get("completed").getAsBoolean());
        verify(gateway, times(1)).extract(any());
        service.reset(first);
        reloaded = repository.findById(first).orElseThrow();
        assertTrue(reloaded.isActive()); assertEquals(1, reloaded.getEventHistory().toList().size());
    }

    private UUID create() {
        Agent agent = new SingleStateSmartGoalCoaching().createAgent(); agent.start(service.runtime());
        UUID id = repository.save(agent).getId(); created.add(id); return id;
    }
    private List<String> userInputs(UUID id) {
        return repository.findById(id).orElseThrow().getEventHistory().toList().stream()
                .filter(event -> Event.TYPE_USER_UTTERANCE.equals(event.getType())).map(Event::getPayload).toList();
    }
    private static EventRequest input(String text) { return new EventRequest(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, Event.KIND_OBSERVATION, text); }
}
