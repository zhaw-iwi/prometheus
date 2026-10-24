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
    @Test void dynamicGuidanceIsAddedReplacedAndWithdrawnWithoutReopeningConversation() {
        var initial = snapshot("reception", new LiveActivityContext("No active round", null));
        var delivery = new LiveContextDelivery(initial); delivery.ready();
        var guidance = new LinkedHashMap<>(initial.guidance());
        guidance.put("opening", "Greet once");
        guidance.put("taskVoice", "Only accepted tasks run");
        guidance.put("policy", "Keep selected identities secret during introductions");
        var current = new LiveContextSnapshot(AGENT, EPOCH, "r2", Instant.EPOCH, List.of("task"), "Guide",
                List.of(), 0, guidance, new LiveActivityContext("Selected identity: Ada", null));
        var batch = delivery.plan(current, List.of());
        String transmitted = batch.commands().toString();
        assertFalse(transmitted.contains("Ada")); // Commands' diagnostics omit content.
        var content = batch.commands().stream().map(LiveContextDelivery.Command::content).toList();
        assertTrue(content.stream().anyMatch(c -> c.contains("Only accepted tasks run")));
        assertTrue(content.stream().noneMatch(c -> c.contains("Greet once")));
        assertTrue(content.getFirst().contains("Keep selected identities secret"));
        assertTrue(content.getLast().contains("Selected identity: Ada"));
        delivery.acknowledged(batch);
        assertTrue(delivery.plan(current, List.of()).commands().isEmpty());
        var withdrawn = delivery.plan(initial, List.of());
        assertTrue(withdrawn.commands().stream().anyMatch(c -> c.content().contains("CURRENT TASKVOICE")
                && c.content().contains("no longer applies")));
        assertTrue(withdrawn.commands().stream().noneMatch(c -> c.content().contains("Greet once")));
    }
    @Test void expiredOccasionsCannotStartSpeechAndExpiryNeverBecomesAnAnnouncement() {
        var initial = snapshot("reception", new LiveActivityContext("No active round", null));
        var delivery = new LiveContextDelivery(initial); delivery.ready();
        var expired = new Continuation(UUID.randomUUID(), Occasion.PROACTIVE, "Welcome", Instant.EPOCH);
        var current = snapshot("reception", new LiveActivityContext("A visitor is present", expired));
        assertTrue(delivery.plan(current, List.of()).commands().stream()
                .noneMatch(c -> c.type().equals("session.commentary.append")));
        var valid = new Continuation(UUID.randomUUID(), Occasion.USER_REQUEST, "Confirmed result", Instant.EPOCH.plusSeconds(10));
        var batch = delivery.plan(snapshot("reception", new LiveActivityContext("Result ready", valid)), List.of());
        var speech = batch.commands().getLast();
        assertFalse(speech.expiredAt(Instant.EPOCH.plusSeconds(9)));
        assertTrue(speech.expiredAt(Instant.EPOCH.plusSeconds(10)));
        assertEquals("session.thinking.append", speech.expired().type());
        assertFalse(speech.expired().content().contains("Confirmed result"));
        assertTrue(delivery.plan(snapshot("reception", new LiveActivityContext("Result ready", valid)), List.of())
                .commands().stream().noneMatch(c -> c.type().equals("session.commentary.append")));
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
    @Test void everyNewRoundDeliversItsRequiredWordsOnceInsteadOfAPromiseOrGenericParaphrase() {
        var initial = snapshot("reception", new LiveActivityContext("No current result", null));
        var delivery = new LiveContextDelivery(initial); delivery.ready();
        var identities = new HashSet<UUID>();
        for (int round = 0; round < 3; round++) {
            var cue = new Continuation(UUID.randomUUID(), Occasion.USER_REQUEST, "New round confirmed; capture closed.",
                    Instant.EPOCH.plusSeconds(30), false, "Schere, Stein, Papier.");
            identities.add(cue.id());
            var current = snapshot("countdown", new LiveActivityContext("Countdown for new round; no current result", cue));
            var batch = delivery.plan(current, List.of());
            var commands = batch.commands().stream().filter(c -> c.type().equals("session.commentary.append")).toList();
            assertEquals(1, commands.size());
            var command = commands.getFirst();
            assertTrue(command.content().contains("say exactly NOW: Schere, Stein, Papier."));
            assertTrue(command.content().contains("even if spoken in an earlier round"));
            assertFalse(command.content().contains("in your own words"));
            assertEquals(cue.id(), command.sourceId()); assertTrue(command.currentOnly());
            delivery.acknowledged(batch);
            assertTrue(delivery.plan(current, List.of()).commands().isEmpty());
        }
        assertEquals(3, identities.size());
        assertThrows(IllegalArgumentException.class, () -> new Continuation(UUID.randomUUID(), Occasion.USER_REQUEST,
                "New round", null, false, "x".repeat(161)));
    }
}
