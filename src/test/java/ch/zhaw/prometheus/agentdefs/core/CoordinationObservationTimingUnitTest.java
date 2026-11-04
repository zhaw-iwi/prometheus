package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import com.google.gson.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.task.*;

class CoordinationObservationTimingUnitTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void calibratedScissorsTriggersExactlyOneContributionAfterAcceptance(boolean live) throws Exception {
        JsonObject data;
        try (var input = getClass().getResourceAsStream("/coordination/observation-timing.json")) {
            assertNotNull(input);
            data = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        Instant received = Instant.parse(data.get("serverReceiptTime").getAsString());
        Instant accepted = received.minusSeconds(4);
        var now = new AtomicReference<>(accepted.minusSeconds(1));
        try (var clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenAnswer(call -> now.get());
            var f = new CoordinationAgentUnitTest();
            var runtime = live ? f.live : f.text;
            var agreement = CoordinationAgentUnitTest.sensor(false);
            agreement.addProperty("goal", "Tell one short dry joke on scissors");
            agreement.addProperty("agentRole", "Tell one short dry joke only after a fresh scissors cue");
            var task = agreement.getAsJsonObject("reactions");
            task.addProperty("goal", agreement.get("goal").getAsString());
            task.addProperty("maxActions", 1);
            var rules = new JsonArray(); rules.add(data.get("rule").deepCopy()); task.add("rules", rules);
            f.propose(agreement, runtime);
            now.set(accepted); f.start(runtime);
            assertEquals("EXECUTION", CoordinationMemory.stage(f.storage()));
            assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt());
            clearInvocations(f.gateway);
            if (live) {
                // Even a correctly dated cue cannot bypass the arming response association.
                now.set(received);
                assertNull(f.sign("scissor", .899, received.minusMillis(26), runtime));
                verifyNoInteractions(f.gateway);
                capture(f, now, "The task is armed and waiting for your scissors sign.");
            }
            now.set(received.plusMillis(10));
            // Delivered after acceptance, but captured before it: never restamp at receipt.
            assertNull(f.sign("scissor", .899, accepted.minusMillis(1), runtime));
            assertNull(f.sign("scissor", .899, Instant.parse(data.get("browserSourceTime").getAsString()), runtime));
            verifyNoInteractions(f.gateway);
            assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt());

            String joke = "My calendar is full of dates, but none of them call back.";
            when(f.gateway.infer(any())).thenReturn(BehaviourPlan.speechOnly(joke).toJsonObject().toString());
            // The source capture instant is retained. A new Live frame must also follow capture confirmation.
            Instant source = live ? now.get().minusMillis(1) : Instant.parse(data.get("serverCaptureTime").getAsString());
            var result = f.sign("scissor", .899, source, runtime);
            assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt());
            verify(f.gateway, times(1)).infer(any());
            assertEquals("LIMIT_REACHED", f.storage().get(CoordinationMemory.OUTCOME).getAsString());
            if (live) {
                assertEquals("FINISHING", CoordinationMemory.stage(f.storage()));
                assertNull(f.sign("scissor", .899, source, runtime));
                verify(f.gateway, times(1)).infer(any());
                var cue = f.policy().liveActivityContext().playbackCue();
                assertNotNull(cue); assertNull(cue.segmentId());
                played(f, cue.id());
                assertEquals("FINISHING", CoordinationMemory.stage(f.storage()));
                capture(f, now, joke);
                assertNotNull(f.policy().liveActivityContext().playbackCue().segmentId());
                assertEquals("FINISHING", CoordinationMemory.stage(f.storage()));
                played(f, UUID.randomUUID());
                assertEquals("FINISHING", CoordinationMemory.stage(f.storage()));
                played(f, cue.id());
                assertEquals("PLAYBACK_CONFIRMED", f.storage().get(CoordinationDelivery.KEY).getAsJsonObject().get("status").getAsString());
                played(f, cue.id());
            } else {
                assertNotNull(result);
                assertEquals(joke, BehaviourPlan.fromJson(result.getPayload()).getSpeech());
                assertNull(f.policy().liveActivityContext().playbackCue());
            }
            assertEquals("REFLECTION", CoordinationMemory.stage(f.storage()));
            clearInvocations(f.gateway);
            assertNull(f.sign("scissor", .899, source, runtime));
            now.set(now.get().plusSeconds(4));
            assertNull(f.sign("scissor", .899, now.get(), runtime));
            verifyNoInteractions(f.gateway);
            assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        }
    }

    private static void capture(CoordinationAgentUnitTest f, AtomicReference<Instant> now, String words) {
        Instant started = now.get().plusMillis(1); now.set(started.plusMillis(1));
        var speech = f.hear(words, true, started);
        var check = TaskResponse.claim(f.storage(), f.owner, speech, started, now.get()).orElseThrow();
        assertTrue(TaskResponse.finish(f.storage(), f.owner, check, true, now.get()));
        assertEquals("CAPTURED", TaskResponse.status(f.storage()));
    }
    private static void played(CoordinationAgentUnitTest f, UUID id) {
        f.agent.acknowledge(new Event(CoordinationDelivery.PLAYED, "system", Event.KIND_SYSTEM, id.toString()), f.live);
    }
}
