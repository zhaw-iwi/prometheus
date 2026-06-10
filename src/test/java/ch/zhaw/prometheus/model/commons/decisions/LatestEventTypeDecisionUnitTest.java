package ch.zhaw.prometheus.model.commons.decisions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class LatestEventTypeDecisionUnitTest {
    private final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), new NoOpGateway());

    @Test
    void returnsTrueWhenLatestEventTypeMatches() {
        EventHistory history = new EventHistory();
        history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "hello"));
        history.appendEvent(Event.observation(Event.TYPE_SOCIAL_SITUATION_CHANGE, Event.ACTOR_SYSTEM,
                "{\"changeType\":\"arrival\"}"));

        LatestEventTypeDecision decision = new LatestEventTypeDecision(Event.TYPE_SOCIAL_SITUATION_CHANGE);

        assertTrue(decision.decide(history, this.runtime));
    }

    @Test
    void returnsFalseWhenLatestEventTypeDoesNotMatch() {
        EventHistory history = new EventHistory();
        history.appendEvent(Event.observation(Event.TYPE_SOCIAL_SITUATION_CHANGE, Event.ACTOR_SYSTEM,
                "{\"changeType\":\"arrival\"}"));
        history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "hello"));

        LatestEventTypeDecision decision = new LatestEventTypeDecision(Event.TYPE_SOCIAL_SITUATION_CHANGE);

        assertFalse(decision.decide(history, this.runtime));
    }

    @Test
    void returnsFalseForEmptyHistory() {
        LatestEventTypeDecision decision = new LatestEventTypeDecision(Event.TYPE_SOCIAL_SITUATION_CHANGE);

        assertFalse(decision.decide(new EventHistory(), this.runtime));
    }

    private static final class NoOpGateway implements LanguageModelGateway {
        @Override
        public String complete(java.util.List<ch.zhaw.prometheus.model.policy.PromptMessage> messages) {
            return "";
        }

        @Override
        public boolean decide(java.util.List<ch.zhaw.prometheus.model.policy.PromptMessage> messages) {
            return false;
        }

        @Override
        public com.google.gson.JsonElement extract(
                java.util.List<ch.zhaw.prometheus.model.policy.PromptMessage> messages) {
            return com.google.gson.JsonNull.INSTANCE;
        }

        @Override
        public com.google.gson.JsonElement summarise(
                java.util.List<ch.zhaw.prometheus.model.policy.PromptMessage> messages) {
            return com.google.gson.JsonNull.INSTANCE;
        }

        @Override
        public String summariseOffline(java.util.List<ch.zhaw.prometheus.model.policy.PromptMessage> messages) {
            return "";
        }
    }
}
