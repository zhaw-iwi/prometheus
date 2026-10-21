package ch.zhaw.prometheus.application.live;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.policy.LiveActivityContext;
import ch.zhaw.prometheus.model.policy.LiveActivityContext.*;

class LiveActivityContextUnitTest {
    private static final UUID AGENT = UUID.randomUUID(), EPOCH = UUID.randomUUID();
    private static LiveContextSnapshot snapshot(String state, LiveActivityContext activity) {
        return new LiveContextSnapshot(AGENT, EPOCH, state, Instant.EPOCH, List.of(state), "Guide",
                List.of(), 0, Map.of("state", state, "policy", "Stable rules"), activity);
    }
    @Test void quietFactsPrecedeOneContinuationAndNeitherRefreshNorReconnectReplaysIt() {
        var initial = snapshot("reception", new LiveActivityContext("No active round", null));
        var delivery = new LiveContextDelivery(initial); delivery.ready();
        var continuation = new Continuation(UUID.randomUUID(), Occasion.USER_REQUEST, "Guessing round started; invite the first question.");
        var current = snapshot("guessing", new LiveActivityContext("Target: Albert Einstein. Died in 1955.", continuation));
        var batch = delivery.plan(current, List.of());
        assertTrue(batch.commands().stream().noneMatch(c -> c.type().equals("session.instructions.append")));
        assertTrue(batch.commands().get(1).content().contains("Albert Einstein"));
        assertEquals("session.commentary.append", batch.commands().getLast().type());
        assertTrue(batch.commands().getLast().content().contains("pending request once"));
        assertEquals(continuation.id(), batch.commands().getLast().sourceId());
        delivery.acknowledged(batch, Set.of("obs.face"));
        assertTrue(delivery.plan(current, List.of()).commands().isEmpty());
        var reconnected = new LiveContextDelivery(current); reconnected.ready();
        assertTrue(reconnected.plan(current, List.of()).commands().isEmpty());
        var ended = snapshot("reception", new LiveActivityContext("No active round", null));
        var removal = delivery.plan(ended, List.of());
        assertTrue(removal.commands().stream().allMatch(c -> c.type().equals("session.thinking.append")));
        assertTrue(removal.commands().stream().anyMatch(c -> c.content().contains("replaces prior activity facts")));
    }
    @Test void startupCarriesFactsWithoutSpeakingAuthorizationAndBoundsSensitiveContext() {
        var continuation = new Continuation(UUID.randomUUID(), Occasion.PROACTIVE, "One authorized welcome.");
        var context = new LiveActivityContext("Target: Albert Einstein", continuation);
        var startup = snapshot("guessing", context);
        assertTrue(startup.startupInput().toString().contains("Albert Einstein"));
        assertFalse(startup.instructions().contains("Einstein"));
        assertFalse(startup.startupInput().toString().contains("authorized welcome"));
        assertFalse(startup.toString().contains("Einstein"));
        assertFalse(context.toString().contains("Einstein"));
        assertThrows(IllegalArgumentException.class, () -> new LiveActivityContext("ü".repeat(2001), null));
        assertThrows(IllegalArgumentException.class, () -> new Continuation(UUID.randomUUID(), Occasion.USER_REQUEST, "x".repeat(1001)));
    }
    @Test void legacyPoliciesStillUseInstructionChangesAndScriptedAnnouncements() {
        var delivery = new LiveContextDelivery(snapshot("before", null)); delivery.ready();
        var batch = delivery.plan(snapshot("after", null), List.of(new LiveContextDelivery.Narration(UUID.randomUUID(), "Done")));
        assertEquals(List.of("session.instructions.append", "session.commentary.append"), batch.commands().stream().map(LiveContextDelivery.Command::type).toList());
        assertTrue(batch.commands().getLast().content().contains("Confirmed backend announcement"));
    }
}
