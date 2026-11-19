package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.zhaw.prometheus.application.live.*;
import ch.zhaw.prometheus.model.policy.*;

class LiveTaskNarrationUnitTest {
    final UUID agent = UUID.randomUUID(), epoch = UUID.randomUUID();
    final ExternalSpeech owner = new ExternalSpeech(UUID.randomUUID(), epoch);
    LiveContextSnapshot snapshot(String revision, String phase, UUID source, boolean notice) {
        var continuation = source == null ? null : new LiveActivityContext.Continuation(source,
                LiveActivityContext.Occasion.USER_REQUEST, "Current " + phase + " response", Instant.MAX);
        if (notice) continuation = continuation.whileCurrent();
        return new LiveContextSnapshot(agent, epoch, revision, Instant.EPOCH, List.of(phase), "Guide", List.of(), 0,
                Map.of("state", phase), new LiveActivityContext("Confirmed phase: " + phase, continuation));
    }
    @ParameterizedTest
    @ValueSource(strings = {"START", "PAUSE", "STOP", "REPLACE", "RESTART", "RETURN_TO_CONVERSATION", "IN_FLIGHT"})
    void committedControlWithdrawsOldOccasionAcrossAcknowledgementWait(String operation) throws Exception {
        UUID old = UUID.randomUUID(), next = UUID.randomUUID();
        var contexts = mock(LiveAgentContextService.class);
        var latest = new AtomicReference<>(snapshot("proposal", "READY", old, true));
        when(contexts.refresh(agent, owner)).thenAnswer(c -> Optional.of(new LiveAgentContextService.Update(latest.get(), List.of())));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var sent = new CopyOnWriteArrayList<LiveContextDelivery.Command>(); var applied = new AtomicInteger();
        var bridge = new LiveContextBridgeService(contexts, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        boolean inFlight = operation.equals("IN_FLIGHT");
        var session = bridge.open(snapshot("initial", "CONVERSATION", null, false), owner, command -> {
            sent.add(command);
            if (old.equals(command.sourceId()) && command.currentOnly()
                    && (command.type().equals("session.commentary.append") == inFlight) && entered.getCount() > 0) {
                entered.countDown();
                try { assertTrue(release.await(3, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new IllegalStateException(e); }
            }
        }, ignored -> applied.incrementAndGet(), reason -> fail(reason));
        try {
            session.ready(); assertTrue(entered.await(3, TimeUnit.SECONDS));
            latest.set(snapshot("accepted", operation, next, true));
            bridge.changed(new AgentCommitted(agent, epoch, next));
            release.countDown();
            await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() >= 2);
            assertEquals(inFlight ? 1 : 0, sent.stream().filter(c -> old.equals(c.sourceId()) && c.type().equals("session.commentary.append")).count());
            assertEquals(1, sent.stream().filter(c -> next.equals(c.sourceId()) && c.type().equals("session.commentary.append")).count());
            assertEquals(1, sent.stream().filter(c -> c.content().contains("is withdrawn")).count());
            assertEquals(1, session.status().recent().stream().filter(t -> t.phase().equals("task_narration_withdrawn")).count());
            assertTrue(sent.stream().allMatch(c -> c.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= LiveContextDelivery.MAX_APPEND_BYTES));
            assertEquals(next, session.speechRequestId());
            assertFalse(session.status().toString().contains("Confirmed phase"));
        } finally { release.countDown(); session.close(); bridge.shutdown(); }
    }
    @Test void unrelatedContextRevisionDoesNotWithdrawOrReplayCurrentInvitation() {
        UUID source = UUID.randomUUID(); var contexts = mock(LiveAgentContextService.class);
        var latest = new AtomicReference<>(snapshot("r1", "READY", source, true));
        when(contexts.refresh(agent, owner)).thenAnswer(c -> Optional.of(new LiveAgentContextService.Update(latest.get(), List.of())));
        var sent = new CopyOnWriteArrayList<LiveContextDelivery.Command>(); var applied = new AtomicInteger();
        var bridge = new LiveContextBridgeService(contexts);
        var session = bridge.open(snapshot("r0", "CONVERSATION", null, false), owner, sent::add, c -> applied.incrementAndGet(), reason -> fail(reason));
        try {
            session.ready(); await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() == 1);
            latest.set(snapshot("sensory-update", "READY", source, true)); bridge.changed(new AgentCommitted(agent, epoch, source));
            await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() == 2);
            assertEquals(1, sent.stream().filter(c -> c.type().equals("session.commentary.append")).count());
            assertTrue(sent.stream().noneMatch(c -> c.content().contains("withdrawn")));
        } finally { bridge.shutdown(); }
    }
    @Test void committedContributionSurvivesFinishingAndNeverBecomesAWithdrawal() {
        UUID action = UUID.randomUUID(); var contexts = mock(LiveAgentContextService.class);
        var latest = new AtomicReference<>(snapshot("r1", "EXECUTION", action, false));
        when(contexts.refresh(agent, owner)).thenAnswer(c -> Optional.of(new LiveAgentContextService.Update(latest.get(), List.of())));
        var sent = new CopyOnWriteArrayList<LiveContextDelivery.Command>(); var applied = new AtomicInteger();
        var bridge = new LiveContextBridgeService(contexts);
        var session = bridge.open(snapshot("r0", "READY", null, false), owner, command -> {
            sent.add(command);
            if (action.equals(command.sourceId()) && command.type().equals("session.thinking.append")) {
                latest.set(snapshot("r2", "FINISHING", null, false));
                bridge.changed(new AgentCommitted(agent, epoch, null));
            }
        }, c -> applied.incrementAndGet(), reason -> fail(reason));
        try {
            session.ready(); await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() >= 1);
            assertEquals(1, sent.stream().filter(c -> c.type().equals("session.commentary.append")).count());
            assertTrue(sent.stream().noneMatch(c -> c.content().contains("withdrawn")));
        } finally { bridge.shutdown(); }
    }
}
