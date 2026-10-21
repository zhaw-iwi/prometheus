package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;

import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.google.gson.JsonPrimitive;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReception;
import ch.zhaw.prometheus.application.live.*;
import ch.zhaw.prometheus.model.policy.*;

class GalaDinnerLiveDeliveryUnitTest {
    @Test void activityChangesStayQuietAndDeliverOneContinuationBeforeSensorsWithSlowAcknowledgements() {
        var agent = new GalaDinnerReception().createAgent();
        ReflectionTestUtils.setField(agent, "id", UUID.randomUUID());
        var now = new AtomicLong();
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenAnswer(call -> now.get());
        when(clock.instant()).thenAnswer(call -> Instant.ofEpochMilli(now.get()));
        var projection = new LiveContextProjection(new PromptMessageAssembler(), new LiveVoicePolicyAdapter(), clock);
        var initial = projection.project(agent);
        var owner = new ExternalSpeech(UUID.randomUUID(), initial.epoch());
        var contexts = mock(LiveAgentContextService.class);
        var current = new AtomicReference<>(new LiveAgentContextService.Update(initial, List.of()));
        when(contexts.refresh(agent.getId(), owner)).thenAnswer(call -> Optional.of(current.get()));
        var sent = new CopyOnWriteArrayList<LiveContextDelivery.Command>();
        var applied = new AtomicInteger();
        var failure = new AtomicReference<String>();
        var bridge = new LiveContextBridgeService(contexts, clock);
        var session = bridge.open(initial, owner, command -> {
            sent.add(command);
            now.addAndGet(700); // Each synchronous ACK costs realistic provider time on a controlled clock.
        }, value -> applied.incrementAndGet(), failure::set);
        try {
            session.ready();
            await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() == 1);
            var states = agent.reachableStates();
            var storage = (ch.zhaw.prometheus.model.Storage) ReflectionTestUtils.getField(agent, "storage");
            String[][] journey = {{"GUESS", "Gala guess the person"}, {"RECEPTION", "Gala reception"},
                    {"RPS", "Gala rock-scissor-paper"}, {"RECEPTION", "Gala reception"},
                    {"TASK", "Gala co-created task"}, {"RECEPTION", "Gala reception"}};
            for (var step : journey) {
                ReflectionTestUtils.setField(agent, "currentState", states.stream()
                        .filter(state -> state.getName().equals(step[1])).findFirst().orElseThrow());
                storage.put("gala.mode", new JsonPrimitive(step[0]));
                UUID source = UUID.randomUUID();
                storage.put("gala.continuation_id", new JsonPrimitive(source.toString()));
                storage.put("gala.continuation_facts", new JsonPrimitive("Requested activity change completed."));
                current.set(new LiveAgentContextService.Update(projection.project(agent),
                        List.of()));
                int before = sent.size(), expected = applied.get() + 1;
                long committedAt = now.get();
                bridge.changed(new AgentCommitted(agent.getId(), owner.epoch(), source));
                await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() == expected);
                var batch = sent.subList(before, sent.size());
                assertEquals("session.commentary.append", batch.getLast().type());
                assertTrue(batch.subList(0, batch.size() - 1).stream().allMatch(c -> c.type().equals("session.thinking.append")));
                assertEquals(1, batch.stream().filter(c -> c.type().equals("session.commentary.append")).count());
                assertTrue(batch.getFirst().content().contains(step[1]));
                assertTrue(batch.stream().noneMatch(command -> command.content().contains("GALA REFERENZ")));
                assertTrue(now.get() - committedAt <= 2100, "State/facts/continuation must not wait behind the reference");
                assertEquals(source, batch.getLast().sourceId());
            }
            assertNull(failure.get());
        } finally { bridge.shutdown(); }
    }
}
