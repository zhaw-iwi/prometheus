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
        return new LiveContextSnapshot(AGENT, EPOCH, revision, Instant.EPOCH, List.of("state"), instructions, List.of(items), 0, java.util.Map.of("state", instructions));
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
                    List.of("state"), "Guide", List.of(item), 0, java.util.Map.of("state", "Guide"));
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
            verify(contexts, times(3)).refresh(AGENT, owner);
            now.set(50000); bridge.refresh();
            await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() == 4);
        } finally { bridge.shutdown(); }
    }
    @Test void confirmedAnnouncementBypassesCommitWindowAndPrecedesPackedSensoryUpdates() {
        var contexts = mock(LiveAgentContextService.class); var now = new AtomicLong();
        Clock clock = mock(Clock.class); when(clock.millis()).thenAnswer(call -> now.get());
        var owner = new ExternalSpeech(UUID.randomUUID(), EPOCH); var initial = snapshot("r0", "Guide");
        var source = UUID.randomUUID(); var current = new AtomicReference<>(new LiveAgentContextService.Update(initial, List.of()));
        when(contexts.refresh(AGENT, owner)).thenAnswer(call -> Optional.of(current.get()));
        var sent = new CopyOnWriteArrayList<LiveContextDelivery.Command>(); var applied = new AtomicInteger();
        var bridge = new LiveContextBridgeService(contexts, clock);
        var session = bridge.open(initial, owner, sent::add, value -> applied.incrementAndGet(), value -> fail(value));
        try {
            session.ready(); await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() == 1);
            now.set(1000);
            var face = evidence("obs.emotion.face", "Happy", 15000);
            var presence = evidence("obs.human.presence", "One person", 15000);
            current.set(new LiveAgentContextService.Update(snapshot("r1", "Completed", face, presence),
                    List.of(new LiveContextDelivery.Narration(source, "Done"))));
            bridge.changed(new AgentCommitted(AGENT, EPOCH));
            verify(contexts, times(1)).refresh(AGENT, owner);
            bridge.changed(new AgentCommitted(AGENT, EPOCH, source));
            await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() == 2);
            assertEquals(List.of("session.instructions.append", "session.commentary.append", "session.thinking.append"),
                    sent.stream().map(LiveContextDelivery.Command::type).toList());
            assertTrue(sent.getLast().content().contains("Happy")); assertTrue(sent.getLast().content().contains("One person"));
            var trace = session.status().recent().stream().filter(t -> t.phase().equals("send") && t.type().equals("session.thinking.append")).findFirst().orElseThrow();
            assertEquals(2, trace.sourceIds().size()); assertTrue(trace.contentBytes() <= 480);
            for (int i = 0; i < 100; i++) bridge.changed(new AgentCommitted(AGENT, EPOCH, source));
            verify(contexts, times(2)).refresh(AGENT, owner);
            now.set(6000); bridge.changed(new AgentCommitted(AGENT, EPOCH));
            await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() == 3);
            assertEquals(1, sent.stream().filter(c -> c.type().equals("session.commentary.append")).count());
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
            now.set(1000);
            session.receive(com.google.gson.JsonParser.parseString("{\"type\":\"session.delegation.created\",\"delegation\":{\"id\":\"pending\",\"target\":\"client\"}}").getAsJsonObject());
            now.set(5000); bridge.refresh();
            await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() == 2);
            assertTrue(sent.isEmpty());
            now.set(6000); bridge.refresh(); // Deadline must bypass the next commit window at 10s.
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
        var wrong = new LiveContextSnapshot(AGENT, UUID.randomUUID(), "r3", Instant.EPOCH, List.of("other"), "Other", List.of(), 0, java.util.Map.of("state", "Other"));
        assertThrows(IllegalStateException.class, () -> delivery.plan(wrong, List.of()));
    }
    static LiveContextSnapshot.Item evidence(String type, String text, long expires) {
        UUID id = UUID.randomUUID();
        return new LiveContextSnapshot.Item(id.toString(), type, "developer", List.of(id), Instant.EPOCH,
                Instant.EPOCH, Instant.ofEpochMilli(expires), "fresh", text);
    }
    @Test void replacementsDeliverSocialFactsFirstWithoutWithdrawingUsableEvidence() {
        var prior = evidence("obs.social.context", "Social context: 1 people visible", 15000);
        var delivery = new LiveContextDelivery(snapshot("r1", "Guide", prior)); delivery.ready();
        var face = evidence("obs.face.emotion", "Observed facial emotion: neutral (confidence 0.99)", 15000);
        var social = evidence("obs.social.context", "Social context: 2 people visible; 1 group, largest 2, singletons 0", 15000);
        var presence = evidence("obs.human.presence", "{\"humanCount\":2,\"ts\":\"1970-01-01T00:00:00Z\"}", 15000);
        var grouping = evidence("obs.social.grouping", "{\"humanCount\":2,\"groupCount\":1,\"largestGroupSize\":2}", 15000);
        var next = snapshot("r2", "Guide", face, presence, grouping, social);
        var batch = delivery.plan(next, List.of());
        assertEquals(4, batch.commands().size(), "One append per small fact, no removal or revision-only append");
        var first = batch.commands().getFirst();
        assertTrue(first.content().contains("2 people visible"));
        assertTrue(first.content().contains("obs.social.context replaces prior value"));
        assertFalse(batch.commands().stream().anyMatch(command -> command.content().contains("removed")));
        assertEquals(social.sourceIds().getFirst(), first.sourceId());
        assertTrue(batch.commands().stream().allMatch(command -> command.content().contains("expires=1970-01-01T00:00:15Z")));
        assertTrue(batch.commands().stream().allMatch(command -> command.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 360));
        assertEquals("r1", delivery.revision(), "Planning does not acknowledge delivery");
        delivery.acknowledged(batch);
        var narrowed = delivery.plan(snapshot("r3", "Guide", social), List.of());
        assertEquals(3, narrowed.commands().size());
        assertTrue(narrowed.commands().stream().allMatch(command -> command.content().contains("removed from selected context; current value unknown")));
        assertFalse(narrowed.commands().stream().anyMatch(command -> command.content().contains("obs.social.context")));
    }
    @Test void nativeHistoryChurnNeedsNoAppendButStateChangesStillDo() {
        var delivery = new LiveContextDelivery(snapshot("r1", "Guide")); delivery.ready();
        var speech = new LiveContextSnapshot.Item("native", "obs.user_utterance", "user", List.of(), null, null, null, "history", "Already heard");
        var batch = delivery.plan(snapshot("r2", "Guide", speech), List.of());
        assertTrue(batch.commands().isEmpty()); delivery.acknowledged(batch);
        var transitioned = new LiveContextSnapshot(AGENT, EPOCH, "r3", Instant.EPOCH, List.of("new state"), "Guide", List.of(), 0, java.util.Map.of("state", "Guide"));
        assertTrue(delivery.plan(transitioned, List.of()).commands().getFirst().content().contains("new state"));
    }
    @Test void fastAcknowledgementsAndCommitStormsStillReadAtMostOncePerFiveSeconds() {
        var contexts = mock(LiveAgentContextService.class); var now = new AtomicLong();
        Clock clock = mock(Clock.class); when(clock.millis()).thenAnswer(call -> now.get());
        var initial = snapshot("r0", "Guide"); var owner = new ExternalSpeech(UUID.randomUUID(), EPOCH);
        when(contexts.refresh(AGENT, owner)).thenAnswer(call -> Optional.of(new LiveAgentContextService.Update(
                snapshot("r" + now.get(), "Guide", evidence("obs.social.context", "Count at " + now.get(), 120000)), List.of())));
        var applied = new AtomicInteger(); var sent = new CopyOnWriteArrayList<LiveContextDelivery.Command>();
        var bridge = new LiveContextBridgeService(contexts, clock);
        var session = bridge.open(initial, owner, sent::add, value -> applied.incrementAndGet(), value -> fail(value));
        try {
            session.ready(); await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() == 1);
            for (int second = 1; second <= 60; second++) {
                now.set(second * 1000L);
                for (int commit = 0; commit < 100; commit++) bridge.changed(new AgentCommitted(AGENT, EPOCH));
                bridge.refresh();
                int expected = 1 + second / 5;
                await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() == expected);
                verify(contexts, times(expected)).refresh(AGENT, owner);
            }
            assertEquals(13, sent.size());
            assertTrue(sent.getLast().content().contains("Count at 60000"));
        } finally { bridge.shutdown(); }
    }
    @Test void delayedAcknowledgementsExpireUnsentChunksWithoutExtendingTheirLifetime() throws Exception {
        var contexts = mock(LiveAgentContextService.class); var now = new AtomicLong();
        Clock clock = mock(Clock.class); when(clock.millis()).thenAnswer(call -> now.get());
        var initial = snapshot("r0", "Guide"); var owner = new ExternalSpeech(UUID.randomUUID(), EPOCH);
        var social = evidence("obs.social.context", "Social context: 2 people visible", 15000);
        var face = evidence("summary.face", "Long face sample ".repeat(70), 2000);
        when(contexts.refresh(AGENT, owner)).thenAnswer(call -> {
            var items = now.get() < 2000 ? List.of(social, face) : List.of(social);
            return Optional.of(new LiveAgentContextService.Update(new LiveContextSnapshot(AGENT, EPOCH, "r" + now.get(),
                    Instant.ofEpochMilli(now.get()), List.of("state"), "Guide", items, 0, java.util.Map.of("state", "Guide")), List.of()));
        });
        var sent = new CopyOnWriteArrayList<LiveContextDelivery.Command>(); var applied = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        var bridge = new LiveContextBridgeService(contexts, clock);
        var session = bridge.open(initial, owner, command -> {
            sent.add(command);
            if (sent.size() == 1) {
                entered.countDown();
                try { assertTrue(release.await(3, TimeUnit.SECONDS)); }
                catch (InterruptedException interrupted) { throw new IllegalStateException(interrupted); }
            }
        }, value -> applied.incrementAndGet(), value -> fail(value));
        try {
            session.ready(); assertTrue(entered.await(3, TimeUnit.SECONDS));
            for (int commit = 0; commit < 100; commit++) session.request();
            verify(contexts, times(1)).refresh(AGENT, owner);
            now.set(3000); release.countDown();
            await().atMost(Duration.ofSeconds(3)).until(() -> applied.get() == 2);
            assertTrue(sent.getFirst().content().contains("2 people visible"));
            assertEquals(1, sent.stream().filter(command -> command.content().contains("unknown (expired at")).count());
            assertFalse(sent.stream().anyMatch(command -> command.content().contains("Long face sample")));
            assertEquals(1, session.status().recent().stream().filter(trace -> trace.phase().equals("expired_before_send")).count());
            assertFalse(session.status().toString().contains("people visible"));
            verify(contexts, times(2)).refresh(AGENT, owner); // Expiry bypasses the 5s commit window.
        } finally { release.countDown(); bridge.shutdown(); }
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
