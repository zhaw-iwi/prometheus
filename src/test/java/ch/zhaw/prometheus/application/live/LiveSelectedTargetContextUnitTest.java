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
    @Test void configuredRegionsReachLiveAsScopedFrameDataAndExpire() {
        var pointing=fixtures.behaviour.PointingFixture.observation("hand-one","right",now);
        var first=append(Event.observation(Event.TYPE_HAND_POINTING,"camera",pointing.toString()),now);
        var initial=project(now);
        var request=fixtures.behaviour.HandSceneFixture.request(1);
        request.getAsJsonArray("targets").get(0).getAsJsonObject().addProperty("expiresAt",now.plusSeconds(2).toString());
        HandScene.configure(storage,scope,request,agent.getEventHistory(),now);
        var projected=project(now);
        var item=projected.items().stream().filter(e->e.type().equals(Event.TYPE_HAND_POINTING)).findFirst().orElseThrow();
        assertTrue(item.text().contains("\"label\":\"Display\""));
        assertEquals(List.of(first.getId()),item.sourceIds());
        assertTrue(project(now.plusSeconds(2)).items().stream().noneMatch(e->e.text().contains("Operator-registered regions")));
        var changed=pointing.deepCopy();changed.getAsJsonObject("frame").addProperty("revision",2);
        assertTrue(agent.handPointingContext(changed.toString(),now).isEmpty());
        var delivery=new LiveContextDelivery(initial);delivery.ready();
        assertTrue(delivery.plan(projected,List.of()).commands().stream().anyMatch(c->
                c.type().equals("session.thinking.append")&&c.content().contains("Display")&&c.sourceId().equals(first.getId())));
    }

    @Test void ambiguousRayCarriesFreshMatchingLabelsWithoutSelectingOrSpeaking() {
        var raw=ObservedTargetsFixture.snapshot(now,
                ObservedTargetsFixture.target("cup-a","cup",.65,.5,.1,.1,now),
                ObservedTargetsFixture.target("cup-b","cup",.85,.5,.1,.1,now));
        var sceneEvent=Event.observation(Event.TYPE_SCENE_TARGETS,"camera",raw.toString());
        assertTrue(ObservedTargets.admit(storage,scope,sceneEvent,now));
        var candidates=new JsonArray();
        ObservedTargets.fresh(storage,now).forEach(target->candidates.add(HandScene.ref(target).json()));
        var sample=fixtures.behaviour.PointingFixture.observation("hand-one","right",now);
        var resolution=sample.getAsJsonObject("resolution");
        resolution.addProperty("status","ambiguous");resolution.add("candidates",candidates);
        var before=project(now);
        var point=append(Event.observation(Event.TYPE_HAND_POINTING,"camera",sample.toString()),now);
        var delivery=new LiveContextDelivery(before);delivery.ready();
        var changes=delivery.plan(project(now),List.of()).commands().stream()
                .filter(c->c.content().contains("multiple detected targets")).toList();
        assertEquals(1,changes.size());assertEquals(point.getId(),changes.getFirst().sourceId());
        assertEquals("session.thinking.append",changes.getFirst().type());
        assertTrue(changes.getFirst().content().contains("\"cup\",\"cup\""));
        assertFalse(storage.containsKey(ObservedTargets.SELECTED));
        var later=now.plusMillis(500);capture(later,false);
        assertFalse(agent.handPointingContext(sample.toString(),later).contains("multiple detected targets"));
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
    @Test void onlyTheFreshRemovalFrameWithdrawsTheSelectionInQuietContext() {
        append(capture(now, true), now);bind("Visitor", true, now);var before=project(now);
        var removalAt=now.plusMillis(500);var removed=append(capture(removalAt, false), removalAt);
        var selected=TargetRef.parse(storage.get(ObservedTargets.SELECTED));
        assertTrue(ObservedTargets.selectedAbsent(storage,selected,removalAt));
        assertFalse(ObservedTargets.selectedAbsent(storage,selected,removalAt.plusSeconds(4)));
        var item=item(removalAt);
        assertTrue(item.text().contains("previous selection is absent"));assertFalse(item.text().contains("Visitor"));
        var delivery=new LiveContextDelivery(before);delivery.ready();
        var updates=delivery.plan(project(removalAt), List.of()).commands().stream()
                .filter(c->c.content().contains("previous selection is absent")).toList();
        assertEquals(1,updates.size());assertEquals("session.thinking.append",updates.getFirst().type());
        assertEquals(removed.getId(),updates.getFirst().sourceId());
        assertFalse(item(removalAt.plusSeconds(3)).text().contains("previous selection is absent"));
        var later=now.plusSeconds(1);capture(later, true);bind("New visitor", true, later);
        assertFalse(item(later).text().contains("previous selection is absent"));
    }
}
