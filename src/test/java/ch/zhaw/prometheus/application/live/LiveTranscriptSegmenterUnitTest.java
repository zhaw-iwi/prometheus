package ch.zhaw.prometheus.application.live;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.*;

class LiveTranscriptSegmenterUnitTest {
    final LiveTranscriptSegmenter segmenter = new LiveTranscriptSegmenter(UUID.randomUUID());
    Fragment fragment(String id, Speaker speaker, long start, long end, long arrival, String text) {
        return new Fragment(id, speaker, start, end, arrival, arrival, text);
    }
    void quiet(Speaker speaker, long after) {
        for (int i = 1; i <= 8; i++) segmenter.audio(speaker, 2400, false, after + i * 100);
    }
    @Test void observedPauseAndGraceCloseOnceButNetworkSilenceNeverDoes() {
        segmenter.fragment(fragment("one", Speaker.USER, 0, 100, 100, "Hello"));
        quiet(Speaker.USER, 100);
        var completed = segmenter.poll(900); assertEquals(1, completed.size());
        assertEquals(Closure.COMPLETE, completed.getFirst().closure()); assertEquals("Hello", completed.getFirst().text());
        assertTrue(segmenter.poll(950).isEmpty());
        segmenter.fragment(fragment("two", Speaker.USER, 1000, 1200, 1200, "Pending"));
        assertTrue(segmenter.poll(4000).isEmpty()); quiet(Speaker.USER, 4000);
        assertEquals(Closure.INCOMPLETE, segmenter.poll(4800).getFirst().closure());
    }
    @Test void hesitationReorderingRepetitionAndTwoSpeakersKeepDistinctIdentity() {
        var later = fragment("later", Speaker.USER, 200, 300, 300, " again");
        segmenter.fragment(later); segmenter.fragment(later);
        segmenter.fragment(fragment("earlier", Speaker.USER, 0, 150, 350, "again"));
        segmenter.fragment(fragment("assistant", Speaker.ASSISTANT, 100, 350, 360, "Hello"));
        for (int i = 1; i <= 4; i++) segmenter.audio(Speaker.USER, 2400, false, 350 + i * 100);
        assertTrue(segmenter.poll(750).isEmpty()); segmenter.audio(Speaker.USER, 2400, true, 800);
        segmenter.fragment(fragment("last", Speaker.USER, 500, 700, 850, " please"));
        quiet(Speaker.USER, 850); quiet(Speaker.ASSISTANT, 850);
        var done = segmenter.poll(1650); assertEquals(2, done.size());
        assertEquals("again again please", done.getFirst().text());
        assertEquals("Hello", done.getLast().text()); assertNotEquals(done.getFirst().id(), done.getLast().id());
    }
    @Test void lateAndIncompleteFragmentsNeverBecomeAnotherCompleteTurn() {
        segmenter.fragment(fragment("one", Speaker.USER, 0, 300, 300, "Yes")); quiet(Speaker.USER, 300);
        var first = segmenter.poll(1100).getFirst();
        var late = segmenter.fragment(fragment("correction", Speaker.USER, 250, 350, 1200, "actually no")).getFirst();
        assertEquals(Closure.LATE, late.closure()); assertNotEquals(first.id(), late.id());
        segmenter.fragment(new Fragment("missing", Speaker.USER, null, null, 1300, 3, "Uncertain"));
        quiet(Speaker.USER, 1300); assertEquals(Closure.INCOMPLETE, segmenter.poll(2100).getFirst().closure());
        segmenter.fragment(fragment("unfinished", Speaker.ASSISTANT, 1400, 1500, 2200, "Partial"));
        assertEquals(Closure.INCOMPLETE, segmenter.close().getFirst().closure()); assertTrue(segmenter.close().isEmpty());
    }
    @Test void coverageGapsAndLongOpenSegmentsAreExplicitAndBounded() {
        segmenter.fragment(fragment("gap", Speaker.USER, 0, 100, 100, "Pending"));
        segmenter.audio(Speaker.USER, 2400, true, 100); quiet(Speaker.USER, 1000);
        assertEquals("audio_coverage_gap", segmenter.poll(1800).getFirst().reason());
        segmenter.fragment(fragment("long", Speaker.USER, 2000, 2100, 2100, "Unfinished"));
        assertEquals(Closure.INCOMPLETE, segmenter.poll(32101).getFirst().closure());
        segmenter.fragment(fragment("large", Speaker.USER, 4000, 4100, 33000, "x".repeat(2800)));
        assertThrows(IllegalStateException.class, () -> segmenter.fragment(fragment("overflow", Speaker.USER, 4101, 4200, 33100, "x")));
    }
}
