package ch.zhaw.prometheus.model.policy;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Application facts for Live to reason from, independent of behavioural instructions and speech. */
public record LiveActivityContext(String facts, Continuation continuation) {
    public LiveActivityContext { bounded(facts, 4000); }
    public enum Occasion { USER_REQUEST, PROACTIVE }
    /** A persisted identity authorizes one speaking occasion; facts are not a scripted utterance. */
    public record Continuation(UUID id, Occasion occasion, String facts) {
        public Continuation {
            Objects.requireNonNull(id); Objects.requireNonNull(occasion); bounded(facts, 1000);
        }
        @Override public String toString() { return "LiveContinuation[id=" + id + ",occasion=" + occasion + "]"; }
    }
    private static void bounded(String text, int limit) {
        if (text == null || text.isBlank() || text.getBytes(StandardCharsets.UTF_8).length > limit)
            throw new IllegalArgumentException("Invalid Live activity context size");
    }
    @Override public String toString() { return "LiveActivityContext[continuation=" + continuation + "]"; }
}
