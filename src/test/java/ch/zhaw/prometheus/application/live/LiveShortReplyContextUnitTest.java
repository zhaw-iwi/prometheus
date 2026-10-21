package ch.zhaw.prometheus.application.live;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.application.live.LiveShortReplyContext.Spoken;

class LiveShortReplyContextUnitTest {
    private final List<String> path = List.of("gala", "reception");
    private Instant at(long ms) { return Instant.ofEpochMilli(ms); }
    private Spoken spoken(String state, Long end, long first, long recorded) {
        return new Spoken(state, end, at(first), at(recorded));
    }
    private boolean ambiguous(List<Spoken> spoken, Instant intent, boolean owned) {
        return LiveShortReplyContext.ambiguous(path, path, 600L, at(1000), spoken, intent, owned);
    }
    @Test void laterCompletedNativeQuestionSupersedesOldAnnouncementOnlyForActivityOwnedSpeech() {
        var recent = List.of(spoken("gala / reception", 500L, 200, 300));
        assertFalse(ambiguous(recent, null, false));
        assertFalse(ambiguous(recent, at(100), true));
        assertTrue(ambiguous(recent, at(100), false));
        assertTrue(ambiguous(recent, at(250), true));
    }
    @Test void missingOrUnsettledContextRequiresClarification() {
        assertTrue(ambiguous(List.of(), null, true));
        assertTrue(ambiguous(List.of(spoken("gala / game", 500L, 200, 300)), null, true));
        assertTrue(ambiguous(List.of(spoken("gala / reception", null, 200, 300)), null, true));
        assertTrue(ambiguous(List.of(spoken("gala / reception", 500L, 200, 1100)), null, true));
        var recent = List.of(spoken("gala / reception", 500L, 200, 300));
        assertTrue(LiveShortReplyContext.ambiguous(path, List.of("gala", "game"), 600L, at(1000), recent, null, true));
        assertTrue(LiveShortReplyContext.ambiguous(path, path, null, at(1000), recent, null, true));
        assertTrue(ambiguous(List.of(new Spoken("gala / reception", 500L, null, at(300))), null, true));
    }
    @Test void olderQuestionCannotHideOverlappingOrDifferentLatestSpeech() {
        var old = spoken("gala / reception", 100L, 100, 150);
        assertTrue(ambiguous(List.of(old, spoken("gala / reception", 700L, 200, 300)), null, true));
        assertTrue(ambiguous(List.of(old, spoken("gala / game", 500L, 200, 300)), null, true));
    }
}
