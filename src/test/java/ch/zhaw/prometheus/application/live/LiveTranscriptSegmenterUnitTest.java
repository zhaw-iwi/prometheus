package ch.zhaw.prometheus.application.live;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.*;

class LiveTranscriptSegmenterUnitTest {
    @ParameterizedTest @CsvSource({"0,COMPLETE", "800,INCOMPLETE"})
    void reflectedMediaRangesDistinguishArrivalDelayFromDroppedAudio(int mediaGap, Closure expected) {
        var capture = new LiveTranscriptSegmenter(UUID.randomUUID(), 2000, 2400);
        capture.audio(Speaker.ASSISTANT, 24000, 0, true, 1000, 0L, 1000L);
        capture.fragment(fragment("timed", Speaker.ASSISTANT, 0, 2000 + mediaGap, 1000, "A complete review."));
        // Three seconds of network delay, but a continuous media range unless frames really went missing.
        capture.audio(Speaker.ASSISTANT, 24000, 0, true, 4000, 1000L + mediaGap, 2000L + mediaGap);
        for (int i = 0; i < 24; i++)
            capture.audio(Speaker.ASSISTANT, 2400, 2400, false, 4100 + i * 100,
                    2000L + mediaGap + i * 100, 2100L + mediaGap + i * 100);
        var result = capture.poll(6400).getFirst();
        assertEquals(expected, result.closure());
        assertEquals(mediaGap == 0 ? "observed_silence" : "audio_coverage_gap", result.reason());
    }
    @Test void oldMediaGapsCannotPoisonANewFullyCoveredUtteranceAndDuplicatePcmCannotCloseIt() {
        var capture = new LiveTranscriptSegmenter(UUID.randomUUID(), 2000, 2400);
        capture.audio(Speaker.ASSISTANT, 2400, 2400, false, 100, 0L, 100L);
        capture.audio(Speaker.ASSISTANT, 24000, 0, true, 5000, 5000L, 6000L);
        capture.fragment(fragment("new", Speaker.ASSISTANT, 5000, 6000, 5000, "New confirmed reply."));
        for (int i = 0; i < 30; i++) capture.audio(Speaker.ASSISTANT, 2400, 2400, false, 5100 + i * 100, 0L, 100L);
        assertTrue(capture.poll(8100).isEmpty());
        for (int i = 0; i < 24; i++)
            capture.audio(Speaker.ASSISTANT, 2400, 2400, false, 8200 + i * 100, 6000L + i * 100, 6100L + i * 100);
        assertEquals(Closure.COMPLETE, capture.poll(10500).getFirst().closure());
    }
    @Test void invalidReflectedTimingCannotClaimCoverage() {
        var capture = new LiveTranscriptSegmenter(UUID.randomUUID());
        capture.fragment(fragment("invalid", Speaker.ASSISTANT, 0, 200, 1000, "Reply."));
        capture.audio(Speaker.ASSISTANT, 2400, 2400, false, 1000, 0L, 1000L);
        for (int i = 0; i < 8; i++)
            capture.audio(Speaker.ASSISTANT, 2400, 2400, false, 1100 + i * 100, i * 100L, (i + 1) * 100L);
        var result = capture.poll(1800).getFirst();
        assertEquals(Closure.INCOMPLETE, result.closure()); assertEquals("invalid_audio_timing", result.reason());
    }
    @ParameterizedTest @CsvSource({"ASSISTANT,true,COMPLETE", "ASSISTANT,false,INCOMPLETE", "USER,true,COMPLETE", "USER,false,INCOMPLETE"})
    void delayedAudioMustActuallyCatchUpBeforeSilenceCanCloseTheSegment(Speaker speaker, boolean catchesUp, Closure expected) {
        var capture = new LiveTranscriptSegmenter(UUID.randomUUID(), 2000, 2400);
        capture.fragment(fragment("review", speaker, 1000, 2000, 1000, "Ready. Shall we start?"));
        capture.audio(speaker, 2400, true, 1000);
        // One second passes before a 200-ms chunk arrives. A reliable stream may
        // deliver its remaining buffered samples together, but missing samples may not be invented.
        capture.audio(speaker, 4800, true, 2000);
        if (catchesUp) for (int i = 0; i < 4; i++) capture.audio(speaker, 4800, true, 2000);
        for (int i = 1; i <= 24; i++) capture.audio(speaker, 2400, false, 2000 + i * 100);
        var result = capture.poll(4400).getFirst();
        assertEquals(expected, result.closure());
        assertEquals(catchesUp ? "observed_silence" : "audio_coverage_gap", result.reason());
        assertEquals("Ready. Shall we start?", result.text());
    }
    @Test void aShortFeedbackPauseKeepsTheEvaluationAndExplanationInOneCandidate() {
        var capture = new LiveTranscriptSegmenter(UUID.randomUUID(), 2000, 2400);
        capture.fragment(fragment("evaluation", Speaker.ASSISTANT, 88000, 88800, 1000, "That's it, a piano!"));
        for (int i = 1; i <= 14; i++) {
            capture.audio(Speaker.ASSISTANT, 2400, false, 1000 + i * 100);
            assertTrue(capture.poll(1000 + i * 100).isEmpty(), "Do not check only the first feedback sentence");
        }
        capture.fragment(fragment("explanation", Speaker.ASSISTANT, 90200, 93200, 2400,
                " Correct, a piano has keys you press, not ones that open a door."));
        for (int i = 1; i <= 24; i++) capture.audio(Speaker.ASSISTANT, 2400, false, 2400 + i * 100);
        var done = capture.poll(4800);
        assertEquals(1, done.size()); assertEquals(Closure.COMPLETE, done.getFirst().closure());
        assertEquals(2, done.getFirst().fragments().size());
        assertEquals("That's it, a piano! Correct, a piano has keys you press, not ones that open a door.", done.getFirst().text());
        assertTrue(capture.poll(4800).isEmpty());
    }
    @ParameterizedTest @CsvSource({"0,INCOMPLETE", "800,COMPLETE"})
    void humanOnsetEndsAssistantAssemblyWithoutInventingAcousticCompletion(int quietMs, Closure closure) {
        var capture = new LiveTranscriptSegmenter(UUID.randomUUID(), 2000, 2400);
        capture.fragment(fragment("feedback", Speaker.ASSISTANT, 100, 500, 1000, "Correct."));
        capture.audio(Speaker.ASSISTANT, 2400, true, 1000);
        for (int i = 1; i <= quietMs / 100; i++) capture.audio(Speaker.ASSISTANT, 2400, false, 1000 + i * 100);
        var boundary = capture.userActivity(1000 + quietMs);
        assertEquals(1, boundary.size()); assertEquals(closure, boundary.getFirst().closure());
        assertTrue(capture.userActivity(1000 + quietMs).isEmpty());
        capture.fragment(fragment("later", Speaker.ASSISTANT, 2000, 2100, 2000, "Another response."));
        assertEquals("Another response.", capture.close().getFirst().text());
    }
    @Test void transcriptBoundaryRejectsOverlapAndDoesNotLetAnOldUserFragmentSplitCurrentSpeech() {
        var capture = new LiveTranscriptSegmenter(UUID.randomUUID(), 2000, 2400);
        capture.fragment(fragment("feedback", Speaker.ASSISTANT, 2000, 2500, 1000, "Correct."));
        for (int i = 1; i <= 8; i++) capture.audio(Speaker.ASSISTANT, 2400, false, 1000 + i * 100);
        assertTrue(capture.fragment(fragment("old-user", Speaker.USER, 100, 200, 1800, "Earlier turn")).isEmpty());
        var overlap = capture.fragment(fragment("new-user", Speaker.USER, 2400, 2600, 1800, "Stop"));
        assertEquals(Closure.INCOMPLETE, overlap.getFirst().closure());
        assertEquals("user_interrupted", overlap.getFirst().reason());
    }
    @Test void assistantAssemblyKeepsCoverageCloseAndOriginalOpenTimeLimits() {
        var capture = new LiveTranscriptSegmenter(UUID.randomUUID(), 2000, 2400);
        capture.fragment(fragment("gap", Speaker.ASSISTANT, 0, 100, 1000, "Partial."));
        for (int i = 1; i <= 24; i++) capture.audio(Speaker.ASSISTANT, 2400, false, 2000 + i * 100);
        assertEquals("audio_coverage_gap", capture.poll(4400).getFirst().reason());
        capture.fragment(fragment("long", Speaker.ASSISTANT, 5000, 5100, 5000, "First."));
        capture.fragment(fragment("later", Speaker.ASSISTANT, 34000, 34100, 34000, " Later."));
        assertEquals("activity_or_transcript_timeout", capture.poll(35001).getFirst().reason());
        capture.fragment(fragment("stopped", Speaker.ASSISTANT, 36000, 36100, 36000, "Unfinished."));
        assertEquals(Closure.INCOMPLETE, capture.close().getFirst().closure());
        assertThrows(IllegalArgumentException.class, () -> new LiveTranscriptSegmenter(UUID.randomUUID(), 2000, 799));
        assertThrows(IllegalArgumentException.class, () -> new LiveTranscriptSegmenter(UUID.randomUUID(), 2000, 3001));
    }
    @Test void mutedInputDoesNotInterruptOrCompleteAssistantAssembly() {
        var capture = new LiveTranscriptSegmenter(UUID.randomUUID(), 2000, 2400);
        capture.fragment(fragment("feedback", Speaker.ASSISTANT, 100, 500, 1000, "Correct."));
        for (int i = 1; i <= 8; i++) capture.audio(Speaker.ASSISTANT, 2400, false, 1000 + i * 100);
        assertTrue(capture.fragment(fragment("muted", Speaker.USER, 600, 700, 1800, "Stop"), false).isEmpty());
        assertTrue(capture.poll(1800).isEmpty());
        assertEquals(Closure.INCOMPLETE, capture.close().stream().filter(s -> s.speaker() == Speaker.ASSISTANT).findFirst().orElseThrow().closure());
    }
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
