package ch.zhaw.prometheus.model.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.EventHistory;

class PromptPolicyUnitTest {

    @Test
    void describeReturnsTrimmedOwnPrompt() {
        PromptPolicy policy = new PromptPolicy("  inner prompt  ", null, PromptPolicy.DEFAULT_SUMMARISE_PROMPT);

        assertEquals("inner prompt", policy.describe());
    }

    @Test
    void withOuterPolicyComposesOuterThenInnerPrompt() {
        PromptPolicy outer = new PromptPolicy("outer prompt", null, PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        PromptPolicy inner = new PromptPolicy("inner prompt", null, PromptPolicy.DEFAULT_SUMMARISE_PROMPT);

        PromptPolicy composed = (PromptPolicy) inner.withOuterPolicy(outer);

        assertEquals("outer prompt inner prompt", composed.describe());
    }

    @Test
    void withOuterPolicyRejectsNonPromptPolicy() {
        PromptPolicy inner = new PromptPolicy("inner prompt", null, PromptPolicy.DEFAULT_SUMMARISE_PROMPT);

        assertThrows(IllegalArgumentException.class, () -> inner.withOuterPolicy(new DummyPolicy()));
    }

    private static final class DummyPolicy extends Policy {
        @Override
        public BehaviourPlan onStart(State state, EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler, ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            return null;
        }

        @Override
        public BehaviourPlan onRespond(State state, EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler, ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            return null;
        }

        @Override
        public String summarise(State state, EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler, ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            return null;
        }

        @Override
        public String describe() {
            return "dummy";
        }
    }
}

