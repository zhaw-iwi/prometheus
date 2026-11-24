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
        var result = LiveCommandResult.current(agent, now, source.getId()::equals);
        var facts = result.facts();
        assertTrue(facts.contains(source.getId().toString())); assertTrue(facts.contains("HAND_SIGN rock"));
        assertTrue(facts.contains("Direct sign authorized by this request."));
        assertTrue(facts.contains("unconfirmed"));
        assertNull(LiveCommandResult.current(agent, now, id -> false));
        assertNull(LiveCommandResult.current(agent, now.plusSeconds(30), id -> true));
        assertNull(LiveCommandResult.current(agent, now.minusSeconds(1), id -> true));
        agent.getEventHistory().appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE,"user","Stop that"));
        assertNull(LiveCommandResult.current(agent, now,id -> true));
    }
    @Test void silentConflictAndUnsupportedFactsCannotClaimSuccess() {
        for (var status : List.of("CONFLICT","UNSUPPORTED","CLARIFY")) {
            command(status,true); var facts = LiveCommandResult.current(agent,now,id -> true).facts();
            assertTrue(facts.contains("Not performed")); assertTrue(facts.contains("Explicit silence: do not speak"));
            assertTrue(facts.contains("No new speaking occasion"));
        }
        agent.reset(); assertNull(LiveCommandResult.current(agent,now,id -> true));
    }
    @Test void unresolvedTargetsReceiveCurrentTurnClarificationWithoutALegacySignSubstitution() {
        var source=command("CLARIFY",false);
        var receipt=storage.get(DirectCommands.LAST).getAsJsonObject();
        receipt.add("kind",JsonNull.INSTANCE);receipt.add("value",JsonNull.INSTANCE);storage.put(DirectCommands.LAST,receipt);
        var saved=receipt.deepCopy();var facts=LiveCommandResult.current(agent,now,source.getId()::equals).facts();
        assertTrue(facts.contains("Not performed"));assertTrue(facts.contains("action or target"));
        assertFalse(facts.contains("sign/gesture"));assertTrue(facts.contains("No new speaking occasion"));
        assertTrue(facts.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=380);
        assertEquals(saved,storage.get(DirectCommands.LAST));
    }
    @Test void failedPointCarriesItsOwnReasonAndLeavesTheSavedTaskSeparate() {
        var source = command("CLARIFY", false);
        var receipt = storage.get(DirectCommands.LAST).getAsJsonObject();
        for (String reason : List.of("selected_target_absent", "target_unavailable")) {
            receipt.addProperty("kind", "POINT"); receipt.addProperty("clarification", reason);
            storage.put(DirectCommands.LAST, receipt);
            var facts = LiveCommandResult.current(agent, now, source.getId()::equals).facts();
            assertTrue(facts.contains("No point sent."));
            assertEquals(reason.equals("selected_target_absent"), facts.contains("absent from the current scene"));
            assertTrue(facts.contains("The saved task is separate."));
            assertTrue(facts.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 380);
        }
    }
    @Test void factsAreQuietDeduplicatedWithdrawnAndNeverAStartupAnnouncement() {
        command("ACCEPTED",false);
        var projection = new LiveContextProjection(new PromptMessageAssembler(),new LiveVoicePolicyAdapter(),Clock.fixed(now,ZoneOffset.UTC));
        var initial = projection.project(agent);
        assertFalse(initial.instructions().contains("COMMAND RESULT for"));
        var delivery = new LiveContextDelivery(initial); delivery.ready();
        var current = projection.project(agent,LiveCommandResult.current(agent,now,id -> true));
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
            var result = LiveCommandResult.current(agent, now, source.getId()::equals);
            var facts = result.facts();
            assertTrue(facts.contains("Accepted output plan: " + kind + "."));
            if ("POINT".equals(kind)) assertTrue(facts.contains("A repeat needs its own result."));
            assertTrue(facts.contains("Rendering/physical execution unconfirmed"));
            assertTrue(facts.contains("No new speaking occasion"));
            assertTrue(facts.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 380);
            var current = projection.project(agent, result);var batch = delivery.plan(current,List.of());
            assertEquals(source.getId(), batch.commands().getFirst().sourceId());
            assertEquals("ACCEPTED", batch.commands().getFirst().commandStatus());
            assertTrue(batch.commands().stream().allMatch(c -> c.type().equals("session.thinking.append")));
            delivery.acknowledged(batch, Set.of());assertTrue(delivery.plan(current,List.of()).commands().isEmpty());
            assertEquals(saved, storage.get(DirectCommands.LAST));
        }
    }

    @Test void onlyTheCurrentPublishedPoseAndVoiceOwnerCanConfirmDigitalRendering() {
        var source = command("ACCEPTED", false);
        var receipt = storage.get(DirectCommands.LAST).getAsJsonObject();
        var hand = ch.zhaw.prometheus.model.hand.HandActions.pose(fixtures.behaviour.ComposedHandFixture.output());
        receipt.addProperty("kind", "HAND_POSE"); receipt.add("handAction", hand);
        var history = agent.getEventHistory();
        var plan = history.appendEvent(Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                "{\"motion\":{\"handAction\":" + hand + "}}"));
        var id = UUID.randomUUID();
        ReflectionTestUtils.setField(plan, "id", id);
        var owner = new ch.zhaw.prometheus.model.policy.ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
        DirectCommands.publishedPose(storage, plan, owner);
        assertFalse(DirectCommands.renderedPose(storage, history, source.getId(), owner, now));
        assertFalse(DirectCommands.renderedPose(storage, history, id, null, now));
        assertFalse(DirectCommands.renderedPose(storage, history, id,
                new ch.zhaw.prometheus.model.policy.ExternalSpeech(UUID.randomUUID(), owner.epoch()), now));
        assertFalse(DirectCommands.renderedPose(storage, history, id, owner, now.plusSeconds(30)));
        assertEquals("ACCEPTED", LiveCommandResult.current(agent, now, source.getId()::equals).status());
        assertTrue(DirectCommands.renderedPose(storage, history, id, owner, now.plusSeconds(4)));
        assertFalse(DirectCommands.renderedPose(storage, history, id, owner, now));
        var confirmed = LiveCommandResult.current(agent, now, source.getId()::equals);
        assertEquals("RENDERED", confirmed.status()); assertEquals(source.getId(), confirmed.source());
        assertTrue(confirmed.facts().contains("Digital renderer confirmed this pose."));
        history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Do something else"));
        assertNull(LiveCommandResult.current(agent, now.plusSeconds(5), source.getId()::equals));
    }
}
