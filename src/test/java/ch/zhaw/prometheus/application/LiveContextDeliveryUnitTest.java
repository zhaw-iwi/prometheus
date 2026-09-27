package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
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
    @Test void idleTicksReadOnlyAtSensoryExpiryCommitAndFallback() {
        var contexts = mock(LiveAgentContextService.class); var now = new AtomicLong();
        Clock clock = mock(Clock.class); when(clock.millis()).thenAnswer(call -> now.get());
        var owner = new ExternalSpeech(UUID.randomUUID(), EPOCH);
        java.util.function.Supplier<LiveContextSnapshot> current = () -> {
            boolean expired = now.get() >= 15000;
            var item = new LiveContextSnapshot.Item("presence", "obs.human.presence", "developer", List.of(),
                    Instant.EPOCH, Instant.EPOCH, Instant.ofEpochMilli(15000), expired ? "expired" : "fresh",
                    expired ? "Current value unknown" : "One person visible");
            return new LiveContextSnapshot(AGENT, EPOCH, expired ? "expired" : "fresh", Instant.ofEpochMilli(now.get()),
                    List.of("state"), "Guide", List.of(item), 0);
        };
        when(contexts.refresh(AGENT, owner)).thenAnswer(call -> Optional.of(new LiveAgentContextService.Update(current.get(), List.of())));
        var applied = new AtomicInteger(); var sent = new CopyOnWriteArrayList<LiveContextDelivery.Command>();
        var bridge = new LiveContextBridgeService(contexts, clock);
        var session = bridge.open(current.get(), owner, sent::add, value -> applied.incrementAndGet(), value -> fail(value));
        try {
            session.ready(); await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() == 1);
            for (int second = 1; second < 15; second++) { now.set(second * 1000L); bridge.refresh(); }
            verify(contexts, times(1)).refresh(AGENT, owner);
            now.set(15000); bridge.refresh(); await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() == 2);
            assertTrue(sent.stream().anyMatch(command -> command.content().contains("Current value unknown")));
            for (int second = 16; second < 45; second++) { now.set(second * 1000L); bridge.refresh(); }
            verify(contexts, times(2)).refresh(AGENT, owner);
            now.set(45000); bridge.refresh(); await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() == 3);
            bridge.changed(new AgentCommitted(AGENT, EPOCH));
            await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() == 4);
        } finally { bridge.shutdown(); }
    }
    @Test void pendingDelegationStillWakesAtItsFiveSecondDeadline() {
        var contexts = mock(LiveAgentContextService.class); var now = new AtomicLong();
        Clock clock = mock(Clock.class); when(clock.millis()).thenAnswer(call -> now.get());
        var initial = snapshot("r1", "Guide"); var owner = new ExternalSpeech(UUID.randomUUID(), EPOCH);
        when(contexts.refresh(AGENT, owner)).thenReturn(Optional.of(new LiveAgentContextService.Update(initial, List.of())));
        var sent = new CopyOnWriteArrayList<LiveContextDelivery.Command>(); var applied = new AtomicInteger();
        var bridge = new LiveContextBridgeService(contexts, clock);
        var session = bridge.open(initial, owner, sent::add, value -> applied.incrementAndGet(), value -> fail(value));
        try {
            session.ready(); await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() == 1);
            session.receive(com.google.gson.JsonParser.parseString("{\"type\":\"session.delegation.created\",\"delegation\":{\"id\":\"pending\",\"target\":\"client\"}}").getAsJsonObject());
            await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() == 2);
            assertTrue(sent.isEmpty());
            now.set(5000); bridge.refresh();
            await().atMost(Duration.ofSeconds(3)).until(() -> sent.stream().anyMatch(command -> "pending".equals(command.delegationId())));
        } finally { bridge.shutdown(); }
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
