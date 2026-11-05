package ch.zhaw.prometheus.model.task;

import static fixtures.behaviour.HandPoseFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class HandPoseTaskUnitTest {
    final Instant now = Instant.parse("2026-11-04T12:00:00Z");
    TaskSpec.Rule rule() { return TaskSpec.parse(task()).executable().rules().getFirst(); }
    boolean matches(JsonObject... hands) { return TaskDecision.matchesObservation(rule(), pose(now, hands), now); }
    JsonObject ruleJson(JsonObject task) { return task.getAsJsonArray("rules").get(0).getAsJsonObject(); }

    @Test void poseRulesRoundTripWithoutChangingScalarPersistedShapes() {
        var json = task(); var spec = TaskSpec.parse(json).executable();
        assertEquals(json, spec.json()); assertEquals(spec, TaskSpec.parse(spec.json()));
        assertNull(spec.rules().getFirst().field());
        assertTrue(spec.rules().getFirst().conditionDescription().contains("index gt 0.5 AND ring gt 0.5"));
        var scalar = TaskSpec.parse(JsonParser.parseString(GenericMultimodalTaskUnitTest.SPEC));
        assertEquals(scalar, TaskSpec.parse(scalar.json()));
        assertFalse(scalar.json().getAsJsonArray("rules").get(0).getAsJsonObject().has("handPose"));
    }

    @Test void classifiedRulesRoundTripAndRejectUnsupportedLabelsOrNumericOperatorsOnWords() {
        var json = stateTask(); var spec = TaskSpec.parse(json).executable();
        assertEquals(json, spec.json()); assertEquals(spec, TaskSpec.parse(spec.json()));
        assertTrue(spec.rules().getFirst().conditionDescription().contains("middle eq straight"));
        for (String value : List.of("unknown", "closed", "STRAIGHT", "0.5", "INSTRUCTION_SENTINEL")) {
            var invalid = stateTask(); ruleJson(invalid).getAsJsonObject("handPose").getAsJsonArray("all")
                    .get(0).getAsJsonObject().addProperty("value", value);
            assertThrows(IllegalArgumentException.class, () -> TaskSpec.parse(invalid), value);
        }
        for (String op : List.of("gt", "lt")) {
            var invalid = stateTask(); ruleJson(invalid).getAsJsonObject("handPose").getAsJsonArray("all")
                    .get(0).getAsJsonObject().addProperty("operator", op);
            assertThrows(IllegalArgumentException.class, () -> TaskSpec.parse(invalid), op);
        }
    }

    @Test void classifiedComparisonsUseAllLabelsOnOneHandAndDoNotInferMissingStates() {
        var rule = TaskSpec.parse(stateTask()).rules().getFirst();
        assertTrue(TaskDecision.matchesObservation(rule, pose(now, classifiedHand()), now));
        assertFalse(TaskDecision.matchesObservation(rule, pose(now, matchingHand()), now), "Older numeric-only data is unclassified");
        assertTrue(TaskDecision.matchesObservation(rule(), pose(now, classifiedHand()), now), "Numeric tasks still use raw bend");
        var left = classifiedHand(); var right = classifiedHand();
        left.addProperty("handedness", "left");
        left.getAsJsonObject("fingers").addProperty("index", .5); left.getAsJsonObject("fingerStates").addProperty("index", "neutral");
        right.getAsJsonObject("fingers").addProperty("ring", .5); right.getAsJsonObject("fingerStates").addProperty("ring", "neutral");
        assertFalse(TaskDecision.matchesObservation(rule, pose(now, left, right), now));
        assertTrue(TaskDecision.matchesObservation(rule, pose(now, left, classifiedHand()), now));
        for (String state : List.of("null", "\"unknown\"", "\"INSTRUCTION_SENTINEL\"", "true", "1")) {
            var hand = classifiedHand(); hand.getAsJsonObject("fingerStates").add("middle", JsonParser.parseString(state));
            assertFalse(TaskDecision.matchesObservation(rule, pose(now, hand), now), state);
        }
        var unavailable = classifiedHand(); unavailable.getAsJsonObject("fingers").add("middle", JsonNull.INSTANCE);
        assertFalse(TaskDecision.matchesObservation(rule, pose(now, unavailable), now), "A label cannot revive an unavailable numeric reading");
    }

    @Test void neutralAndExplicitNumericConditionsCanBeCombinedWithinOneHand() {
        var json = stateTask(); var all = ruleJson(json).getAsJsonObject("handPose").getAsJsonArray("all");
        all.get(0).getAsJsonObject().addProperty("value", "neutral");
        all.get(1).getAsJsonObject().addProperty("value", .7); all.get(1).getAsJsonObject().addProperty("operator", "gt");
        var rule = TaskSpec.parse(json).rules().getFirst(); var hand = classifiedHand();
        hand.getAsJsonObject("fingers").addProperty("index", .5); hand.getAsJsonObject("fingerStates").addProperty("index", "neutral");
        hand.getAsJsonObject("fingerStates").remove("ring"); // An explicit numeric condition needs no label.
        assertTrue(TaskDecision.matchesObservation(rule, pose(now, hand), now));
        hand.getAsJsonObject("fingers").add("index", JsonNull.INSTANCE);
        assertFalse(TaskDecision.matchesObservation(rule, pose(now, hand), now), "Unknown is not neutral");
    }

    @Test void invalidOrAmbiguousPoseContractsAreRejectedBeforeTheyBecomeExecutable() {
        List<Consumer<JsonObject>> invalid = List.of(
                r -> r.addProperty("minConfidence", .8),
                r -> r.addProperty("field", "sign"),
                r -> r.addProperty("eventType", Event.TYPE_HAND_SIGN),
                r -> r.remove("handPose"),
                r -> r.getAsJsonObject("handPose").addProperty("handedness", "both"),
                r -> r.getAsJsonObject("handPose").add("all", new JsonArray()),
                r -> r.getAsJsonObject("handPose").getAsJsonArray("all").add(
                        r.getAsJsonObject("handPose").getAsJsonArray("all").get(0).deepCopy()));
        for (var change : invalid) {
            var json = task(); change.accept(ruleJson(json));
            assertThrows(IllegalArgumentException.class, () -> TaskSpec.parse(json), json::toString);
        }
        for (String value : List.of("null", "\"0.5\"", "true", "-0.1", "1.1", "1e9999", "\"NaN\"")) {
            var json = task(); ruleJson(json).getAsJsonObject("handPose").getAsJsonArray("all")
                    .get(0).getAsJsonObject().add("value", JsonParser.parseString(value));
            assertThrows(IllegalArgumentException.class, () -> TaskSpec.parse(json), value);
        }
        for (String field : List.of("finger", "operator", "extra")) {
            var json = task(); ruleJson(json).getAsJsonObject("handPose").getAsJsonArray("all")
                    .get(0).getAsJsonObject().addProperty(field, "unsupported");
            assertThrows(IllegalArgumentException.class, () -> TaskSpec.parse(json), field);
        }
        for (String op : List.of("gt", "lt")) {
            var json = task(); var comparison = ruleJson(json).getAsJsonObject("handPose").getAsJsonArray("all").get(0).getAsJsonObject();
            comparison.addProperty("operator", op); comparison.addProperty("value", op.equals("gt") ? 1 : 0);
            assertThrows(IllegalArgumentException.class, () -> TaskSpec.parse(json));
        }
    }

    @Test void allThresholdsMustHoldOnOneHandRegardlessOfArrayOrder() {
        assertTrue(matches(matchingHand()));
        var left = hand("left", .2, .3, .8, .8); var right = hand("right", .8, .3, .2, .2);
        assertFalse(matches(left, right), "Conditions cannot be combined across two hands");
        assertFalse(matches(right, left));
        assertTrue(matches(left, matchingHand())); assertTrue(matches(matchingHand(), left));
        assertFalse(matches());
    }

    @Test void thumbIsUnrestrictedUnlessIncludedAndFiveComparisonsAreTheLimit() {
        var json = task(); var all = ruleJson(json).getAsJsonObject("handPose").getAsJsonArray("all");
        all.add(JsonParser.parseString("{\"finger\":\"thumb\",\"operator\":\"lt\",\"value\":0.4}"));
        var withThumb = TaskSpec.parse(json).rules().getFirst(); var hand = matchingHand();
        assertFalse(TaskDecision.matchesObservation(withThumb, pose(now, hand), now));
        hand.getAsJsonObject("fingers").addProperty("thumb", .2);
        assertTrue(TaskDecision.matchesObservation(withThumb, pose(now, hand), now));
        hand.getAsJsonObject("fingers").addProperty("thumb", .8);
        assertFalse(TaskDecision.matchesObservation(withThumb, pose(now, hand), now));
        all.add(all.get(0).deepCopy()); assertThrows(IllegalArgumentException.class, () -> TaskSpec.parse(json));
    }

    @Test void sideSelectionAndNumericComparisonsUseTheDeclaredSemantics() {
        var json = task(); ruleJson(json).getAsJsonObject("handPose").addProperty("handedness", "left");
        var leftRule = TaskSpec.parse(json).rules().getFirst();
        assertFalse(TaskDecision.matchesObservation(leftRule, pose(now, matchingHand()), now));
        assertTrue(TaskDecision.matchesObservation(leftRule, pose(now, hand("left", .7, .3, .6, .8)), now));
        assertFalse(TaskDecision.matchesObservation(leftRule, pose(now, hand("unknown", .7, .3, .6, .8)), now));
        assertTrue(matches(hand("unknown", .7, .3, .6, .8)));
        assertFalse(matches(hand("right", .5, .3, .6, .8))); // gt is strict
        assertFalse(matches(hand("right", .7, .4, .6, .8))); // lt is strict
        var comparison = ruleJson(json).getAsJsonObject("handPose").getAsJsonArray("all").get(0).getAsJsonObject();
        comparison.addProperty("operator", "eq"); comparison.addProperty("value", .7);
        assertTrue(TaskDecision.matchesObservation(TaskSpec.parse(json).rules().getFirst(), pose(now, hand("left", .7, .3, .6, .8)), now));
    }

    @Test void unavailableMalformedAndExpiredValuesCannotTrigger() {
        for (String value : List.of("null", "\"0.7\"", "true", "-0.1", "1.1", "1e9999", "\"NaN\"", "{}", "[]")) {
            var hand = matchingHand(); hand.getAsJsonObject("fingers").add("index", JsonParser.parseString(value));
            assertFalse(matches(hand), value);
        }
        var hand = matchingHand(); hand.getAsJsonObject("fingers").remove("middle"); assertFalse(matches(hand));
        assertFalse(TaskDecision.matchesObservation(rule(), pose(now.minusSeconds(16), matchingHand()), now));
        assertFalse(TaskDecision.matchesObservation(rule(), pose(now.plusSeconds(30), matchingHand()), now));
        assertFalse(TaskDecision.matchesObservation(rule(), Event.observation(Event.TYPE_HAND_POSE, "user", "{\"hands\":false}"), now));
        assertFalse(TaskDecision.matchesObservation(rule(), pose(now, matchingHand(), matchingHand(), matchingHand()), now));
        assertFalse(TaskDecision.matchesObservation(rule(), Event.observation(Event.TYPE_HAND_SIGN, "user", "{\"sign\":\"middle_finger\"}"), now));
    }

    @Test void stabilityNeedsDistinctConsecutiveMatchingObservationsAfterTheCooldown() {
        var json = task(); ruleJson(json).addProperty("samples", 2);
        var storage = new Storage(); storage.put(TaskMemory.SPEC, json);
        TaskMemory.put(storage, TaskMemory.PHASE, "RUNNING"); TaskMemory.put(storage, TaskMemory.AFTER, now.minusSeconds(5).toString());
        var history = new EventHistory();
        var runtime = new PolicyRuntime(new PromptMessageAssembler(), mock(LanguageModelGateway.class));
        history.appendEvent(pose(now.minusSeconds(2), matchingHand()));
        assertTrue(TaskDecision.matching(storage, history, runtime, now).isEmpty());
        history.appendEvent(pose(now.minusSeconds(2), matchingHand()));
        assertTrue(TaskDecision.matching(storage, history, runtime, now).isEmpty(), "Duplicate timestamps do not add stability");
        history.appendEvent(pose(now.minusSeconds(1), matchingHand()));
        assertTrue(TaskDecision.matching(storage, history, runtime, now).isPresent());
        TaskMemory.put(storage, TaskMemory.AFTER, now.plusSeconds(3).toString());
        assertTrue(TaskDecision.matching(storage, history, runtime, now).isEmpty());
        TaskMemory.put(storage, TaskMemory.AFTER, now.minusSeconds(5).toString());
        history.appendEvent(pose(now, hand("right", .2, .3, .6, .8)));
        assertTrue(TaskDecision.matching(storage, history, runtime, now).isEmpty());
        history.appendEvent(pose(now.plusMillis(1), matchingHand()));
        assertTrue(TaskDecision.matching(storage, history, runtime, now.plusMillis(1)).isEmpty());
    }

    @Test void reorderedDuplicateGroupsCannotHideConflictingRules() {
        var json = task(); var duplicate = ruleJson(json).deepCopy();
        var all = duplicate.getAsJsonObject("handPose").getAsJsonArray("all");
        all.add(all.remove(0)); duplicate.addProperty("effect", "COMPLETE");
        json.getAsJsonArray("rules").add(duplicate);
        assertThrows(IllegalArgumentException.class, () -> TaskSpec.parse(json).executable());
    }
}
