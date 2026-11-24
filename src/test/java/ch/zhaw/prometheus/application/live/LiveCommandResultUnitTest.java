package ch.zhaw.prometheus.application.live;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ch.zhaw.prometheus.agentdefs.core.*;
import ch.zhaw.prometheus.model.behaviour.DirectCommands;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;

class LiveCommandResultUnitTest {
    final Instant now = Instant.parse("2026-11-19T09:00:00Z");
    final ch.zhaw.prometheus.model.Agent agent = new CoordinationAgent().createAgent();
    final ch.zhaw.prometheus.model.Storage storage = ((CoordinationPolicy)LiveVoicePolicyAdapter.chain(agent.getCurrentState()).getLast().ownPolicy()).storage();
    Event command(String status, boolean silent) {
        var source = agent.getEventHistory().appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Show rock"));
        ReflectionTestUtils.setField(source, "id", UUID.randomUUID()); ReflectionTestUtils.setField(source, "createdDate", now);
        var receipt = new JsonObject(); receipt.addProperty("sourcePosition", source.sourcePosition()); receipt.addProperty("status",status);
        receipt.addProperty("silent",silent); receipt.addProperty("kind","HAND_SIGN"); receipt.addProperty("value","rock");
        storage.put(DirectCommands.LAST, receipt); return source;
    }
    @Test void onlyTheCurrentAdmittedTurnCanSupplyFactsAndReconnectCannotReplayThem() {
        var source = command("ACCEPTED",false);
        var facts = LiveCommandResult.facts(agent, now, source.getId()::equals);
        assertTrue(facts.contains(source.getId().toString())); assertTrue(facts.contains("HAND_SIGN rock"));
        assertTrue(facts.contains("unconfirmed"));
        assertEquals("",LiveCommandResult.facts(agent, now, id -> false));
        assertEquals("",LiveCommandResult.facts(agent, now.plusSeconds(30), id -> true));
        assertEquals("",LiveCommandResult.facts(agent, now.minusSeconds(1), id -> true));
        agent.getEventHistory().appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE,"user","Stop that"));
        assertEquals("",LiveCommandResult.facts(agent, now,id -> true));
    }
    @Test void silentConflictAndUnsupportedFactsCannotClaimSuccess() {
        for (var status : List.of("CONFLICT","UNSUPPORTED","CLARIFY")) {
            command(status,true); var facts = LiveCommandResult.facts(agent,now,id -> true);
            assertTrue(facts.contains("Not performed")); assertTrue(facts.contains("Explicit silence: do not speak"));
            assertTrue(facts.contains("No new speaking occasion"));
        }
        agent.reset(); assertEquals("",LiveCommandResult.facts(agent,now,id -> true));
    }
    @Test void factsAreQuietDeduplicatedWithdrawnAndNeverAStartupAnnouncement() {
        command("ACCEPTED",false);
        var projection = new LiveContextProjection(new PromptMessageAssembler(),new LiveVoicePolicyAdapter(),Clock.fixed(now,ZoneOffset.UTC));
        var initial = projection.project(agent);
        assertFalse(initial.instructions().contains("COMMAND RESULT for"));
        var delivery = new LiveContextDelivery(initial); delivery.ready();
        var current = projection.project(agent,LiveCommandResult.facts(agent,now,id -> true));
        var batch = delivery.plan(current,List.of());
        assertFalse(batch.commands().isEmpty());
        assertTrue(batch.commands().stream().allMatch(c -> c.type().equals("session.thinking.append")));
        delivery.acknowledged(batch,Set.of());
        assertTrue(delivery.plan(current,List.of()).commands().isEmpty());
        var withdrawal = delivery.plan(initial,List.of());
        assertTrue(withdrawal.commands().stream().allMatch(c -> c.type().equals("session.thinking.append")));
        assertTrue(withdrawal.commands().stream().anyMatch(c -> c.content().contains("Earlier completed commands and task agreements are unchanged")));
        assertTrue(withdrawal.commands().stream().noneMatch(c -> c.content().contains("no longer applies")));
    }
    @Test void structuredHandReceiptsWithNullScalarValueRemainBoundedQuietContext() {
        var projection = new LiveContextProjection(new PromptMessageAssembler(),new LiveVoicePolicyAdapter(),Clock.fixed(now,ZoneOffset.UTC));
        for (String kind : List.of("HAND_POSE", "POINT", "HAND_SEQUENCE")) {
            var source = command("ACCEPTED", false);
            var receipt = storage.get(DirectCommands.LAST).getAsJsonObject();
            receipt.addProperty("kind", kind);receipt.add("value", JsonNull.INSTANCE);
            // Full structured payloads must not be copied into the 380-byte delta.
            receipt.add("handAction", ch.zhaw.prometheus.model.hand.HandActions.pose(switch (kind) {
                case "HAND_POSE" -> fixtures.behaviour.ComposedHandFixture.output();
                case "POINT" -> fixtures.behaviour.PointingFixture.action("left");
                default -> fixtures.behaviour.OutputSequenceFixture.counting(1, 10, 500);
            }));
            storage.put(DirectCommands.LAST, receipt);
            var saved = receipt.deepCopy();
            var initial = projection.project(agent);var delivery = new LiveContextDelivery(initial);delivery.ready();
            var facts = LiveCommandResult.facts(agent, now, source.getId()::equals);
            assertTrue(facts.contains("Accepted output plan: " + kind + "."));
            assertTrue(facts.contains("Rendering/physical execution unconfirmed"));
            assertTrue(facts.contains("No new speaking occasion"));
            assertTrue(facts.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 380);
            var current = projection.project(agent, facts);var batch = delivery.plan(current,List.of());
            assertFalse(batch.commands().isEmpty());
            assertTrue(batch.commands().stream().allMatch(c -> c.type().equals("session.thinking.append")));
            delivery.acknowledged(batch, Set.of());assertTrue(delivery.plan(current,List.of()).commands().isEmpty());
            assertEquals(saved, storage.get(DirectCommands.LAST));
        }
    }
}
