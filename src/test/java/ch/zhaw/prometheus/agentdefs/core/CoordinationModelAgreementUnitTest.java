package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.task.TaskMemory;

class CoordinationModelAgreementUnitTest {
    JsonObject canonical() throws Exception {
        try (var stream = getClass().getResourceAsStream("/hand-interaction/composed-pose-agreement.json")) {
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    JsonObject rule(JsonObject agreement) { return agreement.getAsJsonObject("reactions").getAsJsonArray("rules").get(0).getAsJsonObject(); }
    JsonObject wrapped(JsonObject agreement) {
        return wrapped(agreement, "handPose");
    }
    JsonObject wrapped(JsonObject agreement, String field) {
        var copy = agreement.deepCopy(); var rule = rule(copy);
        rule.add("value", rule.remove(field));rule.addProperty("field", field);rule.addProperty("operator", "eq");return copy;
    }
    JsonObject wrappedCheck(JsonObject agreement, String field) {
        var copy = agreement.deepCopy();var check = copy.getAsJsonArray("checks").get(0).getAsJsonObject();
        check.add("value", check.remove(field));check.addProperty("field", field);check.addProperty("operator", "eq");return copy;
    }
    @Test void modelWrapperPreservesEveryConditionAndStillRequiresPreparationAndConsent() throws Exception {
        var canonical = canonical();var condition = rule(canonical).getAsJsonObject("handPose");
        condition.getAsJsonArray("hands").get(0).getAsJsonObject().add("relations", JsonParser.parseString(
                "[{\"kind\":\"tip_proximity\",\"fingers\":[\"thumb\",\"index\"],\"operator\":\"lt\",\"value\":0.18}]"));
        var expected = CoordinationAgreement.parse(canonical).json();var model = wrapped(canonical);var original = model.deepCopy();
        assertThrows(IllegalArgumentException.class, () -> CoordinationAgreement.parse(model));
        assertEquals(expected, CoordinationAgreement.fromModel(model).json());assertEquals(original, model);
        assertEquals(expected, CoordinationAgreement.fromModel(wrappedCheck(model, "handPose")).json());
        var f = new CoordinationAgentUnitTest();f.propose(model, f.text);
        assertEquals(expected, f.storage().get(CoordinationMemory.DRAFT));
        assertEquals("PREPARATION", CoordinationMemory.stage(f.storage()));
        assertFalse(f.storage().containsKey(CoordinationMemory.AGREEMENT));assertFalse(f.storage().containsKey(TaskMemory.ACTIONS));
    }
    @Test void canonicalAgreementsRemainUnchanged() throws Exception {
        for (var value : List.of(canonical(), CoordinationAgentUnitTest.dialogue(), CoordinationAgentUnitTest.sensor(true)))
            assertEquals(CoordinationAgreement.parse(value).json(), CoordinationAgreement.fromModel(value).json());
    }
    @Test void unknownSequenceResponseKeepsItsCueUntilClarifiedWithoutStarting() {
        var complete = fixtures.behaviour.HandSequenceFixture.agreement(fixtures.behaviour.HandSequenceFixture.counts("right", 1, 2, 3));
        var partial = complete.deepCopy();partial.add("reactions", JsonNull.INSTANCE);
        partial.addProperty("agentRole", "");partial.getAsJsonArray("questions").add("What response should follow the sequence?");
        var f = new CoordinationAgentUnitTest();f.propose(partial, f.text);
        assertEquals(partial, f.storage().get(CoordinationMemory.DRAFT));
        assertEquals("COORDINATION", CoordinationMemory.stage(f.storage()));
        f.start(f.text);
        assertFalse(f.storage().containsKey(CoordinationMemory.AGREEMENT));
        assertFalse(f.storage().containsKey(TaskMemory.ACTIONS));
        f.turn("PROPOSE", complete, "Say Ready once after acceptance and a fresh sequence", f.text);
        assertEquals(complete, f.storage().get(CoordinationMemory.DRAFT));
        assertEquals(partial.get("checks"), f.storage().get(CoordinationMemory.DRAFT).getAsJsonObject().get("checks"));
        assertEquals("PREPARATION", CoordinationMemory.stage(f.storage()));
        f.start(f.text);
        assertFalse(f.storage().containsKey(CoordinationMemory.AGREEMENT));
        assertFalse(f.storage().containsKey(TaskMemory.ACTIONS));
    }
    @Test void typedWrappersPreserveFrameTargetSequenceAndSignWithoutAcceptingConflicts() {
        var pointing = fixtures.behaviour.PointingFixture.agreement();
        var targetPointing = pointing.deepCopy();
        var target = JsonParser.parseString("{\"id\":\"registered-display\",\"revision\":2}").getAsJsonObject();
        target.add("frame", fixtures.behaviour.PointingFixture.frame());
        for (var condition : List.of(rule(targetPointing).getAsJsonObject("handPoint"),
                targetPointing.getAsJsonArray("checks").get(0).getAsJsonObject().getAsJsonObject("handPoint"))) {
            condition.remove("direction");condition.remove("frame");condition.add("target", target.deepCopy());
        }
        var sequence = fixtures.behaviour.HandSequenceFixture.agreement(fixtures.behaviour.HandSequenceFixture.counts("right", 1, 2, 3));
        var sign = fixtures.behaviour.NamedHandSignFixture.agreement();
        var agreements = List.of(pointing, targetPointing, sequence, sign);
        var fields = List.of("handPoint", "handPoint", "handSequence", "handSign");
        for (int i = 0; i < agreements.size(); i++) {
            var canonical = agreements.get(i);var field = fields.get(i);var model = wrappedCheck(wrapped(canonical, field), field);
            var expected = CoordinationAgreement.parse(canonical).json();
            assertEquals(expected, CoordinationAgreement.fromModel(canonical).json());
            assertEquals(expected, CoordinationAgreement.fromModel(wrapped(canonical, field)).json());
            assertEquals(expected, CoordinationAgreement.fromModel(wrappedCheck(canonical, field)).json());
            assertEquals(expected, CoordinationAgreement.fromModel(model).json());
            assertThrows(IllegalArgumentException.class, () -> CoordinationAgreement.parse(model));
            var f = new CoordinationAgentUnitTest();
            if (i <= 1) f.agent.acknowledge(ch.zhaw.prometheus.model.event.Event.observation("obs.hand.pointing", "camera",
                    fixtures.behaviour.PointingFixture.observation("frame", "right", java.time.Instant.now()).toString()), f.text);
            f.propose(model, f.text);
            if (i == 1) assertFalse(f.storage().containsKey(CoordinationMemory.DRAFT), "Unregistered target must still fail grounding");
            else assertEquals(expected, f.storage().get(CoordinationMemory.DRAFT));
            assertFalse(f.storage().containsKey(CoordinationMemory.AGREEMENT));assertFalse(f.storage().containsKey(TaskMemory.ACTIONS));
            for (String changed : List.of("operator", "field", "eventType", "unexpected")) {
                var invalid = model.deepCopy();rule(invalid).addProperty(changed, "unsupported");
                assertThrows(RuntimeException.class, () -> CoordinationAgreement.fromModel(invalid));
            }
            var mixed = model.deepCopy();rule(mixed).add(field, rule(mixed).get("value").deepCopy());
            assertThrows(RuntimeException.class, () -> CoordinationAgreement.fromModel(mixed));
            var extraCheck = model.deepCopy();extraCheck.getAsJsonArray("checks").get(0).getAsJsonObject().addProperty("unexpected", true);
            assertThrows(RuntimeException.class, () -> CoordinationAgreement.fromModel(extraCheck));
        }
    }
    @Test void ambiguousInvalidOrExtraTermsCannotBeNormalizedAway() throws Exception {
        List<Consumer<JsonObject>> changes = List.of(
                r -> r.addProperty("operator", "lt"), r -> r.addProperty("field", "other"),
                r -> r.add("handPose", r.get("value").deepCopy()), r -> r.addProperty("unexpected", true),
                r -> r.getAsJsonObject("value").addProperty("schemaVersion", 99),
                r -> r.addProperty("value", "guess"), r -> r.addProperty("minConfidence", .8),
                r -> r.remove("action"));
        var f = new CoordinationAgentUnitTest();var saved = canonical();f.propose(saved, f.text);
        for (var change : changes) {
            var model = wrapped(saved);change.accept(rule(model));
            assertThrows(RuntimeException.class, () -> CoordinationAgreement.fromModel(model));
            f.propose(model, f.text);assertEquals(CoordinationAgreement.parse(saved).json(), f.storage().get(CoordinationMemory.DRAFT));
            assertFalse(f.storage().containsKey(TaskMemory.ACTIONS));
        }
    }
}
