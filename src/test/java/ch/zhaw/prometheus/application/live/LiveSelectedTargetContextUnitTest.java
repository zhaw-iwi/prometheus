package ch.zhaw.prometheus.application.live;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ch.zhaw.prometheus.agentdefs.core.CoordinationAgent;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.hand.*;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import fixtures.behaviour.ObservedTargetsFixture;

class LiveSelectedTargetContextUnitTest {
    final Instant now = Instant.parse("2026-11-24T12:00:00Z");
    final UUID scope = UUID.randomUUID();
    final ch.zhaw.prometheus.model.Agent agent = new CoordinationAgent().createAgent();
    final ch.zhaw.prometheus.model.Storage storage = ((ch.zhaw.prometheus.agentdefs.core.CoordinationPolicy) agent.getCurrentState().ownPolicy()).storage();
    final JsonObject scene = HandScene.configure(storage, scope, ObservedTargetsFixture.virtualRequest(0), agent.getEventHistory(), now);
    Event capture(Instant at, boolean person) {
        var raw = person ? ObservedTargetsFixture.snapshot(at, ObservedTargetsFixture.target("visitor", "person", .5, .5, .4, .8, at))
                : ObservedTargetsFixture.snapshot(at);
        var event = Event.observation(Event.TYPE_SCENE_TARGETS, "camera", raw.toString());
        assertTrue(ObservedTargets.admit(storage, scope, event, at));return event;
    }
    Event append(Event event, Instant at) {
        var stored = agent.getEventHistory().appendEvent(event);
        ReflectionTestUtils.setField(stored, "id", UUID.randomUUID());ReflectionTestUtils.setField(stored, "createdDate", at);return stored;
    }
    void bind(String label, boolean mapped, Instant at) {
        var target = ObservedTargets.fresh(storage, at).getFirst();
        var request = ObservedTargetsFixture.binding(target, mapped ? scene.getAsJsonArray("targets").get(0).getAsJsonObject() : null);
        request.addProperty("label", label);ObservedTargets.bind(storage, request, at);
    }
    LiveContextSnapshot project(Instant at) {
        return new LiveContextProjection(new PromptMessageAssembler(), new LiveVoicePolicyAdapter(), Clock.fixed(at, ZoneOffset.UTC)).project(agent);
    }
    LiveContextSnapshot.Item item(Instant at) {
        return project(at).items().stream().filter(i -> i.type().equals(Event.TYPE_SCENE_TARGETS)).findFirst().orElseThrow();
    }
    @Test void selectedAliasUsesTheExistingFreshSceneSourceAndOnlyQuietDelivery() {
        var event = append(capture(now, true), now);var before = project(now);
        assertFalse(item(now).text().contains("selectedTarget"));bind("Visitor", true, now);
        var current = project(now);var item = item(now);
        assertTrue(item.text().contains("\"label\":\"Visitor\""));assertTrue(item.text().contains("\"outputMapped\":true"));
        assertTrue(item.text().contains("not personal identity"));
        assertEquals(List.of(event.getId()), item.sourceIds());assertEquals(now, item.observedAt());assertEquals(now.plusSeconds(3), item.expiresAt());
        var delivery = new LiveContextDelivery(before);delivery.ready();var changes = delivery.plan(current, List.of()).commands();
        assertTrue(changes.stream().anyMatch(c -> c.content().contains("Visitor")));
        assertTrue(changes.stream().allMatch(c -> c.type().equals("session.thinking.append") && event.getId().equals(c.sourceId())));
        bind("Guest", true, now);assertFalse(item(now).text().contains("Visitor"));assertTrue(item(now).text().contains("Guest"));
    }
    @Test void aliasDoesNotPromiseOutputWithoutACurrentMappedDestination() {
        append(capture(now, true), now);bind("Visitor", false, now);
        assertTrue(item(now).text().contains("\"outputMapped\":false"));
        bind("Visitor", true, now);assertTrue(item(now).text().contains("\"outputMapped\":true"));
        HandScene.configure(storage, scope, ObservedTargetsFixture.virtualRequest(1), agent.getEventHistory(), now);
        assertTrue(item(now).text().contains("\"outputMapped\":false"));
    }
    @Test void expiryDisappearanceAndNewGenerationCannotBorrowAnEarlierSelectionOrScene() {
        append(capture(now, true), now);bind("Visitor", true, now);
        assertFalse(item(now.plusSeconds(3)).text().contains("Visitor"));
        capture(now.plusMillis(500), false);assertFalse(item(now.plusMillis(500)).text().contains("Visitor"));
        var later = now.plusSeconds(1);var reappeared = capture(later, true);
        assertFalse(item(later).text().contains("Visitor"));bind("New visitor", true, later);
        assertFalse(item(later).text().contains("New visitor"), "The old scene must not borrow a new generation's label");
        append(reappeared, later);assertTrue(item(later).text().contains("New visitor"));
    }
}
