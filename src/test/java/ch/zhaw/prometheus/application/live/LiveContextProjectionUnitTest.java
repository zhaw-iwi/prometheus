package ch.zhaw.prometheus.application.live;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ch.zhaw.prometheus.agentdefs.core.MultimodalBehaviour;
import ch.zhaw.prometheus.agentdefs.core.RockScissorPaper;
import ch.zhaw.prometheus.agentdefs.core.RoleClarificationGuessingGame;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.OuterState;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventSelectorSpec;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;

class LiveContextProjectionUnitTest {
    static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");
    LiveContextProjection projection(Instant now) {
        return new LiveContextProjection(new PromptMessageAssembler(), new LiveVoicePolicyAdapter(), Clock.fixed(now, ZoneOffset.UTC));
    }
    State leaf(Agent agent) { return LiveVoicePolicyAdapter.chain(agent.getCurrentState()).getLast(); }
    static final class EncounterPolicy extends PromptPolicy implements ch.zhaw.prometheus.model.policy.ExternalVoicePolicy {
        final int start;
        boolean observations = true;
        EncounterPolicy(int start) { super("Speak briefly.", null, null); this.start = start; }
        public boolean supportsLiveState(State state) { return true; }
        public String voiceInstructions() { return "Speak briefly."; }
        public int liveHistoryStartIndex() { return start; }
        public boolean liveObservationContextEnabled() { return observations; }
    }
    Event append(Agent agent, String type, String payload, Instant observed, String... path) {
        if (ch.zhaw.prometheus.model.event.ObservationEvidence.supported(type)) {
            var source = com.google.gson.JsonParser.parseString(payload).getAsJsonObject();
            if (!source.has("observed_at") && !source.has("ts")) source.addProperty("ts", observed.toString());
            payload = source.toString();
        }
        Event event = agent.getEventHistory().appendEvent(Event.observation(type, Event.ACTOR_USER, payload).withStatePath(path));
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "createdDate", observed);
        return event;
    }
    @Test void nestedSelectionPreservesOriginalSourcesAndComposesOnlyConversationalPolicies() {
        var policy = new PromptPolicy("Inner conversational guidance", null, null);
        policy.setNonVerbalPlanPrompt("STRICT_JSON_SENTINEL");
        var leaf = new State("inner", policy, List.of());
        var outer = new OuterState("Outer conversational guidance", "outer", List.of(), leaf);
        var agent = new Agent("pilot", "", outer);
        agent.setInteractionProfile(new MultimodalBehaviour().createAgent().getInteractionProfile());
        Event selected = append(agent, Event.TYPE_USER_UTTERANCE, "selected dialogue", NOW.minusSeconds(2), "outer", "inner");
        append(agent, Event.TYPE_USER_UTTERANCE, "excluded sentinel", NOW, "outer", "different");
        var result = projection(NOW).project(agent);
        assertEquals(List.of("outer", "inner"), result.statePath()); assertEquals(1, result.items().size());
        assertEquals(List.of(selected.getId()), result.items().getFirst().sourceIds());
        assertEquals(NOW.minusSeconds(2), result.items().getFirst().receivedAt());
        assertTrue(result.instructions().contains("Outer conversational guidance"));
        assertTrue(result.instructions().contains("Inner conversational guidance"));
        assertFalse(result.instructions().contains("STRICT_JSON_SENTINEL"));
        assertFalse(result.startupInput().toString().contains("excluded sentinel"));
        assertFalse(result.toString().contains("selected dialogue"));
        assertThrows(UnsupportedOperationException.class, () -> result.items().clear());
    }
    @Test void sensoryAdaptersAndTemporalSummaryRetainOnlySelectedFreshEvidence() {
        Agent agent = new MultimodalBehaviour().createAgent(); String name = leaf(agent).getName();
        append(agent, Event.TYPE_FACE_EMOTION, "{\"emotion\":\"happy\",\"confidence\":0.9,\"valence\":0.8}", NOW.minusSeconds(3), name);
        append(agent, Event.TYPE_FACE_EMOTION, "{\"emotion\":\"angry\",\"confidence\":0.99}", NOW, "excluded");
        append(agent, Event.TYPE_SOCIAL_CONTEXT, "{\"humanCount\":2,\"groupCount\":1,\"largestGroupSize\":2}", NOW, name);
        append(agent, Event.TYPE_WEATHER_CURRENT, "{\"condition\":\"rain\",\"temperature_c\":12,\"observed_at\":\"2026-09-27T11:59:00Z\"}", NOW, name);
        var result = projection(NOW).project(agent);
        String input = result.startupInput().toString();
        assertTrue(input.contains("facial emotion: happy (confidence 0.90)"));
        assertTrue(input.contains("2 people visible")); assertTrue(input.contains("rain, 12 C"));
        assertTrue(input.contains("Nonverbal summary")); assertFalse(input.contains("angry"));
        var weather = result.items().stream().filter(item -> item.type().equals(Event.TYPE_WEATHER_CURRENT)).findFirst().orElseThrow();
        assertEquals(NOW.minusSeconds(60), weather.observedAt()); assertEquals(NOW, weather.receivedAt());
        assertTrue(result.items().stream().allMatch(item -> item.role().equals("developer")));
    }
    @Test void deferredObservationContextRetainsBackendEvidenceAndRestoresOnlyItsOriginalFreshness() {
        var policy = new EncounterPolicy(0); var state = new State("waiting", policy, List.of());
        var agent = new Agent("quiet task", "", state);
        agent.setInteractionProfile(new MultimodalBehaviour().createAgent().getInteractionProfile());
        var face = append(agent, Event.TYPE_FACE_EMOTION, "{\"emotion\":\"happy\",\"confidence\":0.9,\"facePresent\":true}", NOW, "waiting");
        append(agent, Event.TYPE_USER_UTTERANCE, "Wait for a cue", NOW, "waiting");
        var initial = projection(NOW).project(agent);
        assertTrue(initial.items().stream().anyMatch(i -> i.type().equals(Event.TYPE_FACE_EMOTION)));
        policy.observations = false;
        var waiting = projection(NOW.plusSeconds(1)).project(agent);
        assertEquals(1, waiting.items().size()); assertEquals(Event.TYPE_USER_UTTERANCE, waiting.items().getFirst().type());
        assertEquals(2, agent.getEventHistory().toList().size());
        var delivery = new LiveContextDelivery(initial); delivery.ready();
        assertTrue(delivery.plan(waiting, List.of()).commands().stream().anyMatch(c -> c.content().contains("obs.emotion.face removed")));
        policy.observations = true;
        var restored = projection(NOW.plusSeconds(2)).project(agent).items().stream().filter(i -> i.type().equals(Event.TYPE_FACE_EMOTION)).findFirst().orElseThrow();
        assertEquals(List.of(face.getId()), restored.sourceIds()); assertEquals(NOW, restored.observedAt()); assertEquals("fresh", restored.freshness());
        var expired = projection(NOW.plusSeconds(16)).project(agent).items().stream().filter(i -> i.type().equals(Event.TYPE_FACE_EMOTION)).findFirst().orElseThrow();
        assertEquals("expired", expired.freshness()); assertFalse(expired.text().contains("happy"));
    }
    @Test void expiryUnknownAgeAndSelectorRemovalChangeRevisionWithoutRefreshingOldFacts() {
        Agent agent = new MultimodalBehaviour().createAgent(); String name = leaf(agent).getName();
        Event face = append(agent, Event.TYPE_FACE_EMOTION, "{\"emotion\":\"sad\"}", NOW.minusSeconds(10), name);
        append(agent, Event.TYPE_WEATHER_CURRENT, "{\"condition\":\"sunny\",\"observed_at\":\"unknown\"}", NOW, name);
        var first = projection(NOW).project(agent);
        assertEquals("unknown", first.items().stream().filter(i -> i.type().equals(Event.TYPE_WEATHER_CURRENT)).findFirst().orElseThrow().freshness());
        var repeated = projection(NOW.plusSeconds(1)).project(agent); assertEquals(first.revision(), repeated.revision());
        var expired = projection(NOW.plusSeconds(6)).project(agent); assertNotEquals(first.revision(), expired.revision());
        var item = expired.items().stream().filter(i -> i.sourceIds().contains(face.getId())).findFirst().orElseThrow();
        assertEquals("expired", item.freshness()); assertFalse(item.text().contains("sad"));
        assertFalse(expired.startupInput().toString().contains("Nonverbal summary"));
        leaf(agent).setEventSelectorSpec(EventSelectorSpec.type(Event.TYPE_USER_UTTERANCE));
        var narrowed = projection(NOW.plusSeconds(6)).project(agent);
        assertTrue(narrowed.items().isEmpty());
        assertNotEquals(expired.revision(), narrowed.revision());
    }
    @Test void encounterStartsWithFreshSelectedWeatherButNeverPriorVisitorEvidence() {
        Agent agent = new MultimodalBehaviour().createAgent(); String name = leaf(agent).getName();
        Event weather = append(agent, Event.TYPE_WEATHER_CURRENT,
                "{\"location_label\":\"Zurich\",\"condition\":\"clear\",\"temperature_c\":9,\"observed_at\":\""
                + NOW.minusSeconds(559) + "\",\"ts\":\"" + NOW + "\"}", NOW, name);
        Event forecast = append(agent, Event.TYPE_WEATHER_FORECAST,
                "{\"days\":[{\"date\":\"2026-09-28\",\"condition\":\"rain\"}]}", NOW.minusSeconds(60), name);
        append(agent, Event.TYPE_USER_UTTERANCE, "previous visitor secret", NOW, name);
        append(agent, Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, "{\"speech\":\"previous private reply\"}", NOW, name);
        append(agent, Event.TYPE_FACE_EMOTION, "{\"emotion\":\"happy\",\"confidence\":0.9}", NOW, name);
        append(agent, Event.TYPE_HUMAN_PRESENCE, "{\"humanCount\":2}", NOW, name);
        append(agent, Event.TYPE_HAND_SIGN, "{\"sign\":\"rock\"}", NOW, name);
        append(agent, Event.TYPE_WEATHER_CURRENT, "{\"condition\":\"snow\"}", NOW, "excluded");
        leaf(agent).setPolicy(new EncounterPolicy(agent.getEventHistory().toList().size()));
        var result = projection(NOW).project(agent);
        assertEquals(List.of(Event.TYPE_WEATHER_CURRENT, Event.TYPE_WEATHER_FORECAST), result.items().stream().map(i -> i.type()).toList());
        assertEquals(List.of(weather.getId()), result.items().getFirst().sourceIds());
        assertTrue(result.items().getFirst().text().contains("Current weather for Zurich: clear"));
        assertTrue(result.items().getFirst().text().contains("9 C"));
        assertEquals(NOW.minusSeconds(559), result.items().getFirst().observedAt());
        assertEquals(List.of(forecast.getId()), result.items().getLast().sourceIds());
        var expired = projection(NOW.plusSeconds(41)).project(agent);
        assertEquals(List.of(Event.TYPE_WEATHER_FORECAST), expired.items().stream().map(i -> i.type()).toList());
        assertTrue(projection(NOW.plusSeconds(21540)).project(agent).items().isEmpty());
        leaf(agent).setEventSelectorSpec(EventSelectorSpec.type(Event.TYPE_USER_UTTERANCE));
        assertTrue(projection(NOW).project(agent).items().isEmpty());
    }
    @Test void encounterDoesNotReviveExpiredUndatedOrFutureWeatherWithReceiptTime() {
        for (String observed : List.of(NOW.minusSeconds(600).toString(), NOW.plusSeconds(1).toString(), "2026-09-27T12:00", "invalid")) {
            Agent agent = new MultimodalBehaviour().createAgent(); String name = leaf(agent).getName();
            append(agent, Event.TYPE_WEATHER_CURRENT, "{\"condition\":\"clear\",\"observed_at\":\"" + observed
                    + "\",\"ts\":\"" + NOW + "\"}", NOW, name);
            leaf(agent).setPolicy(new EncounterPolicy(agent.getEventHistory().toList().size()));
            assertTrue(projection(NOW).project(agent).items().isEmpty(), observed);
        }
    }
    @Test void absentOrPoorlyDetectedFaceWithdrawsItsOlderExpressionAndSummary() {
        for (String invalid : List.of("\"facePresent\":false", "\"faceDetectionConfidence\":0.2", "\"faceDetectionConfidence\":1.2")) {
            Agent agent = new MultimodalBehaviour().createAgent(); String name = leaf(agent).getName();
            append(agent, Event.TYPE_FACE_EMOTION, "{\"emotion\":\"happy\",\"confidence\":0.9}", NOW.minusSeconds(2), name);
            append(agent, Event.TYPE_FACE_EMOTION, "{\"emotion\":\"angry\",\"confidence\":0.99," + invalid + "}", NOW.minusSeconds(1), name);
            var result = projection(NOW).project(agent);
            assertFalse(result.startupInput().toString().contains("angry"));
            assertFalse(result.startupInput().toString().contains("happy"));
            assertFalse(result.startupInput().toString().contains("Nonverbal summary"));
            assertEquals("unknown", result.items().getFirst().freshness());
        }
    }
    @Test void receiptTimeCannotMakeAnUndatedSensorValueCurrent() {
        Agent agent = new MultimodalBehaviour().createAgent();
        var event = agent.getEventHistory().appendEvent(Event.observation(Event.TYPE_FACE_EMOTION, "sensor",
                "{\"emotion\":\"happy\",\"confidence\":0.99}").withStatePath(leaf(agent).getName()));
        ReflectionTestUtils.setField(event, "createdDate", NOW);
        var result = projection(NOW).project(agent);
        assertEquals("unknown", result.items().getFirst().freshness());
        assertFalse(result.startupInput().toString().contains("happy"));
        assertFalse(result.startupInput().toString().contains("Nonverbal summary"));
    }
    @Test void delayedAndFutureFramesCannotReplaceMoreRecentValidEvidence() {
        Agent agent = new MultimodalBehaviour().createAgent(); String name = leaf(agent).getName();
        append(agent, Event.TYPE_FACE_EMOTION, "{\"emotion\":\"happy\",\"confidence\":0.9}", NOW.minusSeconds(1), name);
        append(agent, Event.TYPE_FACE_EMOTION, "{\"emotion\":\"sad\",\"confidence\":0.99}", NOW.minusSeconds(8), name);
        append(agent, Event.TYPE_FACE_EMOTION, "{\"emotion\":\"angry\",\"confidence\":0.99}", NOW.plusSeconds(20), name);
        var result = projection(NOW).project(agent);
        assertTrue(result.startupInput().toString().contains("happy"));
        assertFalse(result.startupInput().toString().contains("sad"));
        assertFalse(result.startupInput().toString().contains("angry"));
        assertFalse(projection(NOW.plusSeconds(15)).project(agent).startupInput().toString().contains("Nonverbal summary"));
    }
    @Test void cameraHandSignsReachGenericLiveContextAndExpireWhenNoLongerObserved() {
        Agent agent = new ch.zhaw.prometheus.agentdefs.core.GenericMultimodalBehaviour().createAgent();
        var delivery = new LiveContextDelivery(projection(NOW).project(agent)); delivery.ready();
        Event hand = append(agent, Event.TYPE_HAND_SIGN,
                "{\"source\":\"valerian.hand.camera\",\"sign\":\"paper\",\"confidence\":0.9,"
                + "\"detectionMode\":\"client_camera\",\"stabilityFrames\":3,\"ts\":\"" + NOW + "\"}", NOW);
        var current = projection(NOW.plusSeconds(1)).project(agent);
        var item = current.items().stream().filter(i -> Event.TYPE_HAND_SIGN.equals(i.type())).findFirst().orElseThrow();
        assertEquals("fresh", item.freshness()); assertEquals(List.of(hand.getId()), item.sourceIds());
        var batch = delivery.plan(current, List.of());
        String content = batch.commands().stream().map(LiveContextDelivery.Command::content).reduce("", String::concat);
        assertTrue(content.contains("obs.hand.sign")); assertTrue(content.contains("\"sign\":\"paper\""));
        assertTrue(content.contains("\"confidence\":0.9"));
        delivery.acknowledged(batch);
        String expired = delivery.plan(projection(NOW.plusSeconds(15)).project(agent), List.of()).commands().stream()
                .map(LiveContextDelivery.Command::content).reduce("", String::concat);
        assertTrue(expired.contains("expired")); assertFalse(expired.contains("\"sign\":\"paper\""));
    }
    @Test void supportsOnlyDeclaredCapabilityAndKnownPolicies() {
        var adapter = new LiveVoicePolicyAdapter();
        for (Agent agent : List.of(new MultimodalBehaviour().createAgent(), new RockScissorPaper().createAgent(),
                new RoleClarificationGuessingGame().createAgent(), new ch.zhaw.prometheus.agentdefs.core.LiveMultimodal().createAgent())) {
            assertTrue(adapter.supports(agent)); assertFalse(adapter.instructions(agent).isBlank());
        }
        Agent agent = new MultimodalBehaviour().createAgent();
        leaf(agent).setPolicy(org.mockito.Mockito.mock(Policy.class));
        assertFalse(adapter.supports(agent)); assertThrows(IllegalArgumentException.class, () -> projection(NOW).project(agent));
        Agent unknown = new Agent("unknown", "", new State("state", new PromptPolicy("Hello", null, null), List.of()));
        assertFalse(adapter.supports(unknown));
    }
    @Test void repeatedStableObservationsKeepCurrentEvidenceFreshButExpireWhenSensingStops() {
        Agent agent = new ch.zhaw.prometheus.agentdefs.core.LiveMultimodal().createAgent();
        String[] types = {Event.TYPE_FACE_EMOTION, Event.TYPE_HUMAN_PRESENCE, Event.TYPE_SOCIAL_GROUPING, Event.TYPE_SOCIAL_CONTEXT};
        String[] values = {"\"emotion\":\"neutral\",\"confidence\":0.99", "\"humanCount\":1",
                "\"humanCount\":1,\"groupCount\":0", "\"humanCount\":1,\"groupCount\":0"};
        for (int seconds = 0; seconds <= 30; seconds += 5) {
            Instant observed = NOW.plusSeconds(seconds);
            for (int i = 0; i < types.length; i++) append(agent, types[i],
                    "{" + values[i] + ",\"ts\":\"" + observed + "\"}", observed);
            var items = projection(observed.plusSeconds(4)).project(agent).items().stream()
                    .filter(item -> item.type().startsWith("obs.")).toList();
            assertEquals(4, items.size()); assertTrue(items.stream().allMatch(item -> item.freshness().equals("fresh")));
        }
        var expired = projection(NOW.plusSeconds(46)).project(agent).items();
        assertEquals(4, expired.size()); assertTrue(expired.stream().allMatch(item -> item.freshness().equals("expired")));
    }

    @Test void sharedTagsDoNotGrantLiveAndUnsupportedFuturePolicyRejectsBeforeTransition() {
        Agent agent = new RockScissorPaper().createAgent();
        var adapter = new LiveVoicePolicyAdapter();
        agent.setInteractionProfile(agent.getInteractionProfile().withExternalRealtimeSpeech(false));
        assertFalse(adapter.supports(agent));
        assertThrows(IllegalArgumentException.class, () -> adapter.instructions(agent));
        agent.setInteractionProfile(agent.getInteractionProfile().withExternalRealtimeSpeech(true));
        assertTrue(adapter.supports(agent));
        var unsupported = new State("unsupported later result", new ch.zhaw.prometheus.model.policy.NoOpPolicy(), List.of());
        leaf(agent).addTransition(new ch.zhaw.prometheus.model.Transition(
                new ch.zhaw.prometheus.model.commons.decisions.StaticDecision("Never execute during inspection"), unsupported));
        assertFalse(adapter.supports(agent));
        assertThrows(IllegalArgumentException.class, () -> adapter.instructions(agent));
    }
    @Test void boundedUnicodeContextKeepsRecentHistoryAndUsesCorrectProviderTextParts() {
        Agent agent = new MultimodalBehaviour().createAgent(); String name = leaf(agent).getName();
        for (int i = 0; i < 100; i++) append(agent, Event.TYPE_USER_UTTERANCE, "😀".repeat(1500) + i, NOW, name);
        Event assistant = agent.getEventHistory().appendEvent(Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN,
                Event.ACTOR_ASSISTANT, "{\"speech\":\"Most recent answer\"}").withStatePath(name));
        ReflectionTestUtils.setField(assistant, "id", UUID.randomUUID());
        var result = projection(NOW).project(agent);
        assertTrue(result.omitted() > 0); assertTrue(result.items().size() <= 40);
        String json = result.startupInput().toString(); assertTrue(json.getBytes(StandardCharsets.UTF_8).length <= 7000);
        assertEquals("assistant", result.items().getLast().role()); assertTrue(json.contains("output_text"));
        assertTrue(json.contains("Most recent answer"));
        for (var item : result.items()) assertFalse(item.text().contains("\uFFFD"));
    }
    @Test void oversizedPolicyFailsInsteadOfTruncatingTaskRules() {
        Agent agent = new MultimodalBehaviour().createAgent();
        leaf(agent).setPolicy(new PromptPolicy("x".repeat(LiveVoicePolicyAdapter.MAX_INSTRUCTION_BYTES), null, null));
        assertThrows(IllegalArgumentException.class, () -> projection(NOW).project(agent));
    }
}
