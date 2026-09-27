package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.application.live.*;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;

class LiveContextDeliveryUnitTest {
    static final UUID AGENT = UUID.randomUUID(), EPOCH = UUID.randomUUID();
    static LiveContextSnapshot snapshot(String revision, String instructions, LiveContextSnapshot.Item... items) {
        return new LiveContextSnapshot(AGENT, EPOCH, revision, Instant.EPOCH, List.of("state"), instructions, List.of(items), 0);
    }
    @Test void readinessSeparatesInstructionsFactsAndAnnouncementsWithoutDialogueEcho() {
        var initial = snapshot("r1", "Guide one"); var delivery = new LiveContextDelivery(initial);
        var fact = new LiveContextSnapshot.Item("weather", "obs.weather.current", "developer", List.of(), Instant.EPOCH, Instant.EPOCH, null, "fresh", "It is raining");
        var next = snapshot("r2", "Guide two", fact);
        UUID source = UUID.randomUUID(); var narration = new LiveContextDelivery.Narration(source, "A confirmed result");
        assertTrue(delivery.plan(next, List.of(narration)).commands().isEmpty()); delivery.ready();
        var batch = delivery.plan(next, List.of(narration));
        assertTrue(batch.commands().stream().anyMatch(value -> value.type().equals("session.instructions.append") && value.content().contains("Guide two")));
        assertTrue(batch.commands().stream().anyMatch(value -> value.type().equals("session.thinking.append") && value.content().contains("It is raining")));
        assertEquals(1, batch.commands().stream().filter(value -> value.type().equals("session.commentary.append")).count());
        delivery.acknowledged(batch); assertTrue(delivery.plan(next, List.of(narration)).commands().isEmpty());
        var removed = delivery.plan(snapshot("r3", "Guide two"), List.of());
        assertTrue(removed.commands().stream().anyMatch(value -> value.content().contains("removed")));
        assertTrue(removed.commands().stream().noneMatch(value -> value.type().equals("session.instructions.append")));
    }
    @Test void multilingualUpdatesAreBoundedAndAnOldEpochCannotBeApplied() {
        var delivery = new LiveContextDelivery(snapshot("r1", "Old")); delivery.ready();
        var batch = delivery.plan(snapshot("r2", "こんにちは ".repeat(200)), List.of(new LiveContextDelivery.Narration(UUID.randomUUID(), "مرحبا ".repeat(80))));
        assertTrue(batch.commands().size() > 1);
        assertTrue(batch.commands().stream().allMatch(value -> value.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 480));
        assertEquals(1, batch.commands().stream().filter(value -> value.type().equals("session.commentary.append")).count());
        var wrong = new LiveContextSnapshot(AGENT, UUID.randomUUID(), "r3", Instant.EPOCH, List.of("other"), "Other", List.of(), 0);
        assertThrows(IllegalStateException.class, () -> delivery.plan(wrong, List.of()));
    }
    @Test void workerCoalescesRefreshesAndLostAcknowledgementIsNeverRetried() throws Exception {
        var contexts = mock(LiveAgentContextService.class); var initial = snapshot("r1", "Guide");
        var owner = new ExternalSpeech(UUID.randomUUID(), EPOCH);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), failed = new CountDownLatch(1);
        AtomicReference<LiveContextSnapshot> latest = new AtomicReference<>(initial);
        when(contexts.refresh(AGENT, owner)).thenAnswer(call -> {
            entered.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS));
            return Optional.of(new LiveAgentContextService.Update(latest.get(), List.of(new LiveContextDelivery.Narration(UUID.randomUUID(), "Result"))));
        });
        var bridge = new LiveContextBridgeService(contexts, Clock.systemUTC()); var attempts = new AtomicInteger();
        var session = bridge.open(initial, owner, command -> { attempts.incrementAndGet(); throw new IllegalStateException("ACK lost"); }, context -> {}, code -> failed.countDown());
        try {
            session.ready(); assertTrue(entered.await(3, TimeUnit.SECONDS));
            for (int index = 2; index < 20; index++) { latest.set(snapshot("r" + index, "Latest")); session.request(); }
            release.countDown(); assertTrue(failed.await(3, TimeUnit.SECONDS)); session.request();
            assertEquals(1, attempts.get()); verify(contexts, times(1)).refresh(AGENT, owner);
            assertEquals("context_delivery_unconfirmed", session.status().state());
            assertEquals("r19", session.status().recent().stream().filter(trace -> trace.phase().equals("send")).findFirst().orElseThrow().revision());
            assertFalse(session.status().toString().contains("Result"));
        } finally { release.countDown(); bridge.shutdown(); }
    }
    @Test void resetDuringFreshReadDiscardsDelayedTaskContext() throws Exception {
        var contexts = mock(LiveAgentContextService.class); var initial = snapshot("r1", "Guide"); var owner = new ExternalSpeech(UUID.randomUUID(), EPOCH);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
        when(contexts.refresh(AGENT, owner)).thenAnswer(call -> {
            entered.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); finished.countDown();
            return Optional.of(new LiveAgentContextService.Update(snapshot("r2", "Obsolete task"), List.of()));
        });
        var attempts = new AtomicInteger(); var bridge = new LiveContextBridgeService(contexts, Clock.systemUTC());
        var session = bridge.open(initial, owner, command -> attempts.incrementAndGet(), value -> fail("Obsolete context applied"), value -> {});
        try {
            session.ready(); assertTrue(entered.await(3, TimeUnit.SECONDS));
            bridge.changed(new AgentCommitted(AGENT, UUID.randomUUID())); release.countDown(); assertTrue(finished.await(3, TimeUnit.SECONDS));
            assertEquals(0, attempts.get()); assertEquals("agent_epoch_changed", session.status().state());
        } finally { release.countDown(); bridge.shutdown(); }
    }
}
