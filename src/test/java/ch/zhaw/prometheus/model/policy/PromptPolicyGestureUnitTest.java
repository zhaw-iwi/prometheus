package ch.zhaw.prometheus.model.policy;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Final;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.spi.*;

class PromptPolicyGestureUnitTest {
    @Test void onRespondKeepsTopLevelHandSignFromStructuredComplement() {
        var policy = policy();
        policy.setNonVerbalPlanPrompt("Return {\"nonVerbal\":{\"gesture\":\"ACKNOWLEDGE\"},\"motion\":{\"handSign\":\"scissors\"}}.");
        var gateway = new Gateway("""
                {"speech":"I choose scissors, very dramatically.",
                 "nonVerbal":{"gesture":"ACKNOWLEDGE","facialExpression":{"type":"playful","intensity":0.5},
                   "gaze":{"direction":"forward","focus":"user"},"motion":{"energy":0.2,"move":"forward","turn":"left"}},
                 "motion":{"handSign":"scissors","move":"forward","turn":"left"}}
                """);
        var plan = respond(policy, gateway);
        assertEquals("I choose scissors, very dramatically.", plan.getSpeech());
        var nonverbal = plan.getNonVerbal().getAsJsonObject();
        assertEquals("ACKNOWLEDGE", nonverbal.get("gesture").getAsString());
        assertEquals("playful", nonverbal.getAsJsonObject("facialExpression").get("type").getAsString());
        assertEquals("forward", nonverbal.getAsJsonObject("gaze").get("direction").getAsString());
        assertFalse(nonverbal.getAsJsonObject("motion").has("move"));
        assertFalse(nonverbal.getAsJsonObject("motion").has("turn"));
        assertEquals("{\"handSign\":\"scissor\"}", plan.getMotion().toString());
        assertEquals(1, gateway.calls);
        assertTrue(gateway.request.messages().getLast().getContent().contains("do not nest a second envelope"));
    }

    @Test void motionNormalizationPreservesOtherChannelsAndRemovesUnknownHandSigns() {
        var plan = BehaviourPlanInference.parse("""
                {"speech":"Hello.","nonVerbal":{},"motion":{"handSign":"invented","energy":0.2},"display":{"mode":"banner"}}
                """);
        assertEquals("{\"energy\":0.2}", plan.getMotion().toString());
        assertEquals("banner", plan.getDisplay().getAsJsonObject().get("mode").getAsString());
        assertNull(BehaviourPlanInference.parse("{\"speech\":\"Hi.\",\"nonVerbal\":{},\"motion\":{\"handSign\":\"invented\"}}").getMotion());
    }

    @Test void oneCombinedRequestPreservesSpeechAndNormalizesNonverbal() {
        PromptPolicy policy = policy();
        var gateway = new Gateway("{\"speech\":\"Here is an explanation.\",\"nonVerbal\":{\"gesture\":\"open question\",\"gaze\":{\"direction\":\"forward\"},\"motion\":{\"move\":1,\"turn\":2,\"energy\":0.2}}}");
        BehaviourPlan plan = respond(policy, gateway);
        assertEquals("Here is an explanation.", plan.getSpeech());
        assertEquals("OPEN_QUESTION", plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        assertEquals("forward", plan.getNonVerbal().getAsJsonObject().getAsJsonObject("gaze").get("direction").getAsString());
        assertFalse(plan.getNonVerbal().getAsJsonObject().getAsJsonObject("motion").has("move"));
        assertFalse(plan.getNonVerbal().getAsJsonObject().getAsJsonObject("motion").has("turn"));
        assertNull(plan.getMotion()); assertNull(plan.getDisplay());
        assertEquals(1, gateway.calls);
        assertEquals(InferencePurpose.BEHAVIOUR, gateway.request.purpose());
        assertEquals(InferenceRequest.Output.JSON_OBJECT, gateway.request.output());
    }

    @Test void speechOnlyPolicyUsesOneMinimalJsonBehaviourRequest() {
        var gateway = new Gateway("{\"speech\":\"Thanks for sharing.\"}");
        BehaviourPlan plan = respond(new PromptPolicy("base", null, "summary"), gateway);
        assertEquals("Thanks for sharing.", plan.getSpeech()); assertNull(plan.getNonVerbal());
        assertEquals(InferencePurpose.BEHAVIOUR, gateway.request.purpose());
        assertEquals(InferenceRequest.Output.JSON_OBJECT, gateway.request.output());
        assertEquals(1, gateway.calls);
        assertTrue(gateway.request.messages().getLast().getContent().contains("only the string field \"speech\""));
    }

    @Test void everyFinalConstructorUsesTheSameStructuredBehaviourPath() {
        for (Final finalState : List.of(new Final("f"), new Final("f", true, "summary"),
                new Final("f", "task"), new Final("f", "task", true, "summary"),
                new Final("f", "task", "starter"), new Final("f", "task", "starter", true, "summary"))) {
            var gateway = new Gateway("{\"speech\":\"Goodbye.\"}");
            var agent = new Agent("synthetic", "final fixture", finalState);
            var runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway);
            var starter = agent.start(runtime);
            assertFalse(agent.isActive());
            assertEquals("{\"speech\":\"Goodbye.\"}", starter.getPayload());
            assertEquals(InferencePurpose.BEHAVIOUR, gateway.request.purpose());
            assertEquals(InferenceRequest.Output.JSON_OBJECT, gateway.request.output());
            assertEquals(1, gateway.calls);
            var response = agent.generate(runtime);
            assertEquals(starter.getPayload(), response.getPayload());
            assertEquals(2, gateway.calls);
        }
    }

    @Test void malformedSpeechOnlyResultFailsWithoutPublishingOrRetrying() {
        for (String raw : List.of("Goodbye.", "{}", "{\"speech\":7}", "{\"speech\":\" \"}")) {
            var gateway = new Gateway(raw);
            var agent = new Agent("synthetic", "final fixture", new Final("f"));
            assertThrows(IllegalStateException.class,
                    () -> agent.start(new PolicyRuntime(new PromptMessageAssembler(), gateway)));
            assertEquals(1, gateway.calls);
            assertTrue(agent.getEventHistory().isEmpty());
        }
    }

    @Test void starterAndOuterInstructionsComposeWithStoredCustomGesturePrompt() {
        var inner = new PromptPolicy("inner instruction", "starter instruction", "summary");
        inner.setNonVerbalGesturePrompt("Custom gesture instruction: choose POLITE.");
        var composed = (PromptPolicy) inner.withOuterPolicy(new PromptPolicy("outer instruction", null, "summary"));
        var gateway = new Gateway("{\"speech\":\"Hello.\",\"nonVerbal\":{\"gesture\":\"POLITE\"},\"display\":{\"mode\":\"banner\"}}");
        var plan = composed.onStart(new State("s", composed, List.of()), new EventHistory(), new PromptMessageAssembler(), gateway);
        String prompt = gateway.request.messages().stream().map(PromptMessage::getContent).reduce("", (a,b) -> a + b);
        for (String fragment : List.of("outer instruction", "inner instruction", "starter instruction", "Custom gesture instruction")) assertTrue(prompt.contains(fragment));
        assertEquals("banner", plan.getDisplay().getAsJsonObject().get("mode").getAsString());
        assertEquals(1, gateway.calls);
    }

    @Test void invalidCombinedOutputDoesNotRetryOrPersistPartialBehaviour() {
        var policy = policy(); var gateway = new Gateway("{\"speech\":\"Hello.\",\"nonVerbal\":{}}");
        var agent = new Agent("test", "synthetic", new State("s", policy, List.of()));
        var runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway);
        agent.start(runtime);
        int before = agent.getEventHistory().toList().size();
        for (String raw : List.of("not-json", "{}", "{\"speech\":7,\"nonVerbal\":{}}", "{\"speech\":\"hi\",\"nonVerbal\":null}", "{\"speech\":\"hi\",\"nonVerbal\":{\"gesture\":[]}}")) {
            gateway.raw = raw; gateway.calls = 0;
            assertThrows(IllegalStateException.class, () -> agent.generate(runtime));
            assertEquals(1, gateway.calls); assertEquals(before, agent.getEventHistory().toList().size());
        }
    }

    @Test void missingOrUnknownGestureBecomesNone() {
        for (String nonverbal : List.of("{}", "{\"gesture\":\"invented\"}")) {
            var plan = respond(policy(), new Gateway("{\"speech\":\"Hello.\",\"nonVerbal\":" + nonverbal + "}"));
            assertEquals("NONE", plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        }
    }

    @Test void rockPaperAndScissorArePreservedAsSemanticGestures() {
        for (String gesture : List.of("rock", "paper", "scissor")) {
            var plan = respond(policy(), new Gateway("{\"speech\":\"I play " + gesture
                    + ".\",\"nonVerbal\":{\"gesture\":\"" + gesture + "\"}}"));
                    assertEquals(gesture.toUpperCase(),
                    plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        }
    }

    private static PromptPolicy policy() {
        var policy = new PromptPolicy("base prompt", null, "summary");
        policy.setNonVerbalPlanPrompt(PromptPolicy.DEFAULT_NONVERBAL_PLAN_PROMPT);
        policy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);
        return policy;
    }
    private static BehaviourPlan respond(PromptPolicy policy, Gateway gateway) {
        return policy.onRespond(new State("s", policy, List.of()), new EventHistory(), new PromptMessageAssembler(), gateway);
    }
    private static class Gateway extends NoOpLanguageModelGateway {
        private String raw; private int calls; private InferenceRequest request;
        Gateway(String raw) { this.raw = raw; }
        @Override public String complete(List<PromptMessage> messages) { calls++; return raw; }
        @Override public String infer(InferenceRequest request) { this.request = request; calls++; return raw; }
    }
}
