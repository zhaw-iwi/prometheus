package ch.zhaw.prometheus.model.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class PromptPolicyGestureUnitTest {

    @Test
    void onRespondAddsGestureWhenGesturePromptConfigured() {
        PromptPolicy policy = new PromptPolicy("base prompt", null, PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        policy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);

        SequencedGateway gateway = new SequencedGateway(List.of("Here is an explanation.", "EXPLAIN"));
        BehaviourPlan plan = policy.onRespond(new State("s", policy, List.of()), new EventHistory(),
                new PromptMessageAssembler(), gateway);

        assertNotNull(plan);
        assertEquals("Here is an explanation.", plan.getSpeech());
        assertEquals("EXPLAIN", plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        assertEquals(2, gateway.completeCallCount);
    }

    @Test
    void onRespondKeepsNonverbalNullWhenGesturePromptMissing() {
        PromptPolicy policy = new PromptPolicy("base prompt", null, PromptPolicy.DEFAULT_SUMMARISE_PROMPT);

        SequencedGateway gateway = new SequencedGateway(List.of("Thanks for sharing."));
        BehaviourPlan plan = policy.onRespond(new State("s", policy, List.of()), new EventHistory(),
                new PromptMessageAssembler(), gateway);

        assertNotNull(plan);
        assertEquals("Thanks for sharing.", plan.getSpeech());
        assertNull(plan.getNonVerbal());
        assertEquals(1, gateway.completeCallCount);
    }

    @Test
    void onRespondUsesStructuredNonverbalWhenPlanPromptConfigured() {
        PromptPolicy policy = new PromptPolicy("base prompt", null, PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        policy.setNonVerbalPlanPrompt(PromptPolicy.DEFAULT_NONVERBAL_PLAN_PROMPT);

        SequencedGateway gateway = new SequencedGateway(List.of(
                "Here is an explanation.",
                "{\"gesture\":\"POLITE\",\"gaze\":{\"direction\":\"forward\",\"focus\":\"user\"},\"facialExpression\":{\"type\":\"friendlyNeutral\",\"intensity\":0.6}}"));
        BehaviourPlan plan = policy.onRespond(new State("s", policy, List.of()), new EventHistory(),
                new PromptMessageAssembler(), gateway);

        assertNotNull(plan);
        assertNotNull(plan.getNonVerbal());
        assertEquals("POLITE", plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        assertEquals("forward", plan.getNonVerbal().getAsJsonObject().getAsJsonObject("gaze").get("direction")
                .getAsString());
        assertEquals(2, gateway.completeCallCount);
    }

    @Test
    void onRespondFallsBackToGestureWhenStructuredPlanInvalid() {
        PromptPolicy policy = new PromptPolicy("base prompt", null, PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        policy.setNonVerbalPlanPrompt(PromptPolicy.DEFAULT_NONVERBAL_PLAN_PROMPT);
        policy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);

        SequencedGateway gateway = new SequencedGateway(List.of(
                "Thanks for sharing.",
                "not-json",
                "ACKNOWLEDGE"));
        BehaviourPlan plan = policy.onRespond(new State("s", policy, List.of()), new EventHistory(),
                new PromptMessageAssembler(), gateway);

        assertNotNull(plan);
        assertNotNull(plan.getNonVerbal());
        assertEquals("ACKNOWLEDGE", plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        assertEquals(3, gateway.completeCallCount);
    }

    private static final class SequencedGateway implements LanguageModelGateway {
        private final List<String> values;
        private int index = 0;
        private int completeCallCount = 0;

        private SequencedGateway(List<String> values) {
            this.values = values;
        }

        @Override
        public String complete(List<PromptMessage> messages) {
            this.completeCallCount++;
            if (this.index >= this.values.size()) {
                return "";
            }
            return this.values.get(this.index++);
        }

        @Override
        public boolean decide(List<PromptMessage> messages) {
            return false;
        }

        @Override
        public com.google.gson.JsonElement extract(List<PromptMessage> messages) {
            return null;
        }

        @Override
        public com.google.gson.JsonElement summarise(List<PromptMessage> messages) {
            return null;
        }

        @Override
        public String summariseOffline(List<PromptMessage> messages) {
            return "";
        }
    }
}
