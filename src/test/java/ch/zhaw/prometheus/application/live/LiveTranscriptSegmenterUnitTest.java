package ch.zhaw.prometheus.application.live;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    @Test void aCompletedInvitationPrecedesItsQuickReplyWhenBothBecomeReadyTogether() {
        segmenter.fragment(fragment("reply", Speaker.USER, 500, 700, 1000, "Ja"));
        segmenter.fragment(fragment("offer", Speaker.ASSISTANT, 0, 400, 1000, "Möchtest du nochmal spielen?"));
        quiet(Speaker.USER, 1000); quiet(Speaker.ASSISTANT, 1000);
        var ready = segmenter.poll(1800);
        assertEquals(2, ready.size());
        assertEquals(Speaker.ASSISTANT, ready.getFirst().speaker());
        assertEquals(Speaker.USER, ready.getLast().speaker());
        assertTrue(ready.stream().allMatch(s -> s.closure() == Closure.COMPLETE));
        assertTrue(segmenter.poll(1800).isEmpty());
    }
    @Test void incompleteOrOverlappingSpeechCannotBePromotedAheadOfAReply() {
        for (boolean incomplete : new boolean[]{false, true}) {
            var capture = new LiveTranscriptSegmenter(UUID.randomUUID());
            capture.fragment(fragment("reply", Speaker.USER, 500, 700, 1000, "Ja"));
            capture.fragment(fragment("offer", Speaker.ASSISTANT, 0, incomplete ? 400 : 550, 1000, "Nochmal?"));
            if (incomplete) capture.uncertain(Speaker.ASSISTANT, "audio_coverage_gap");
            for (Speaker speaker : Speaker.values()) for (int i = 1; i <= 8; i++) capture.audio(speaker, 2400, false, 1000 + i * 100);
            var ready = capture.poll(1800);
            assertEquals(Speaker.USER, ready.getFirst().speaker());
            assertEquals(incomplete ? Closure.INCOMPLETE : Closure.COMPLETE, ready.getLast().closure());
        }
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
    @Test void delayedAudioWithFullSampleCoverageDiffersFromAnActualCaptureGap() {
        for (boolean fullCoverage : new boolean[]{true, false}) {
            var capture = new LiveTranscriptSegmenter(UUID.randomUUID());
            capture.fragment(fragment("output", Speaker.ASSISTANT, 0, 100, 100, "Synthetic final response"));
            capture.audio(Speaker.ASSISTANT, 2400, true, 100);
            // Same 800ms delivery interval: one chunk covers it, the other leaves 700ms unobserved.
            capture.audio(Speaker.ASSISTANT, fullCoverage ? 19200 : 2400, true, 900);
            for (int i = 1; i <= 8; i++) capture.audio(Speaker.ASSISTANT, 2400, false, 900 + i * 100);
            var result = capture.poll(1700).getFirst();
            assertEquals(fullCoverage ? Closure.COMPLETE : Closure.INCOMPLETE, result.closure());
            if (!fullCoverage) assertEquals("audio_coverage_gap", result.reason());
        }
    }

    @ParameterizedTest
    @CsvSource({"770,Ist die Person männlich oder,weiblich", "1200,Ist die Person schon lange,verstorben", "1708,Etwas,spielen"})
    void applicationPauseWindowKeepsObservedHesitationsInOneUserTurn(int pauseMs, String first, String last) {
        var capture = new LiveTranscriptSegmenter(UUID.randomUUID(), 2000);
        capture.fragment(fragment("first", Speaker.USER, 0, 100, 100, first));
        for (long at = 120; at < 100 + pauseMs; at += 20) {
            capture.audio(Speaker.USER, 480, false, at);
            assertTrue(capture.poll(at).isEmpty(), "A brief pause must not authorize a partial task question");
        }
        long resumed = 100 + pauseMs;
        capture.fragment(fragment("last", Speaker.USER, resumed, resumed + 100, resumed, " " + last));
        for (int i = 1; i <= 100; i++) capture.audio(Speaker.USER, 480, false, resumed + i * 20);
        var done = capture.poll(resumed + 2000);
        assertEquals(1, done.size()); assertEquals(Closure.COMPLETE, done.getFirst().closure());
        assertEquals(first + " " + last, done.getFirst().text());
        assertTrue(capture.poll(resumed + 2100).isEmpty());
    }

    @Test void longerUserWindowPreservesAssistantTimingAndConservativeStopAndCoverageRules() {
        var capture = new LiveTranscriptSegmenter(UUID.randomUUID(), 2000);
        for (Speaker speaker : Speaker.values()) {
            capture.fragment(fragment(speaker.name(), speaker, 0, 100, 100, "Pending"));
            for (int i = 1; i <= 8; i++) capture.audio(speaker, 2400, false, 100 + i * 100);
        }
        var done = capture.poll(900);
        assertEquals(1, done.size()); assertEquals(Speaker.ASSISTANT, done.getFirst().speaker());
        assertTrue(capture.poll(2500).isEmpty(), "Network silence cannot complete a turn");
        assertEquals(Closure.INCOMPLETE, capture.interrupt(Speaker.USER, "input_muted").getFirst().closure());
        capture.fragment(fragment("gap", Speaker.USER, 3000, 3100, 3100, "Uncertain"));
        for (int i = 1; i <= 20; i++) capture.audio(Speaker.USER, 2400, false, 4100 + i * 100);
        assertEquals("audio_coverage_gap", capture.poll(6100).getFirst().reason());
        assertThrows(IllegalArgumentException.class, () -> new LiveTranscriptSegmenter(UUID.randomUUID(), 799));
        assertThrows(IllegalArgumentException.class, () -> new LiveTranscriptSegmenter(UUID.randomUUID(), 3001));
    }
    @ParameterizedTest @CsvSource({"1200,A dry,one-liner", "1700,tell me a,joke"})
    void coordinationPolicyKeepsRealPcmHesitationButSeparatesSubsequentTurns(int pause, String first, String last) {
        var policy = new ch.zhaw.prometheus.agentdefs.core.CoordinationPolicy(new ch.zhaw.prometheus.model.Storage(), "");
        var capture = new LiveTranscriptSegmenter(UUID.randomUUID(), policy.liveUserSilenceMs());
        capture.fragment(fragment("first", Speaker.USER, 0, 100, 100, first));
        for (int at = 120; at <= 100 + pause; at += 20) {
            pcm(capture, at, 0); assertTrue(capture.poll(at).isEmpty());
        }
        pcm(capture, 120 + pause, 1000);
        capture.fragment(fragment("last", Speaker.USER, 100 + pause, 200 + pause, 120 + pause, " " + last));
        for (int at = 140 + pause; at <= 2120 + pause; at += 20) pcm(capture, at, 0);
        var done = capture.poll(2120 + pause);
        assertEquals(1, done.size()); assertEquals(Closure.COMPLETE, done.getFirst().closure());
        assertEquals(first + " " + last, done.getFirst().text());
        for (int at = 2140 + pause; at <= 3100 + pause; at += 20) pcm(capture, at, 0);
        capture.fragment(fragment("separate", Speaker.USER, 3000 + pause, 3100 + pause, 3100 + pause, "Stop"));
        for (int at = 3120 + pause; at <= 5100 + pause; at += 20) pcm(capture, at, 0);
        var separate = capture.poll(5100 + pause).getFirst();
        assertEquals("Stop", separate.text()); assertEquals(Closure.COMPLETE, separate.closure());
    }
    private void pcm(LiveTranscriptSegmenter capture, long at, int level) {
        byte[] bytes = new byte[960];
        for (int i = 0; i < bytes.length; i += 2) { bytes[i] = (byte) level; bytes[i + 1] = (byte) (level >> 8); }
        var activity = LivePcmActivity.measure(bytes);
        capture.audio(Speaker.USER, activity.samples(), activity.trailingQuietSamples(), activity.voiced(), at);
    }
}
