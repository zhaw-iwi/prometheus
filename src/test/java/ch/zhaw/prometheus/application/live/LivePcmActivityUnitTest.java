package ch.zhaw.prometheus.application.live;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.*;

class LivePcmActivityUnitTest {
    static byte[] pcm(int samples, int level) {
        var bytes = new byte[samples * 2];
        for (int i = 0; i < samples; i++) { bytes[i * 2] = (byte) level; bytes[i * 2 + 1] = (byte) (level >> 8); }
        return bytes;
    }
    @Test void isolatedFrameIsQuietButSustainedLowLevelActivityCrossesTheUnchangedThreshold() {
        var filter = new LivePcmActivity.OnsetFilter();
        assertTrue(LivePcmActivity.measure(pcm(480, 600)).voiced(), "Default gate is unchanged");
        assertFalse(filter.measure(pcm(480, 600), 20).voiced()); assertTrue(filter.pending());
        assertFalse(filter.measure(pcm(480, 0), 40).voiced()); assertFalse(filter.pending());
        assertFalse(filter.measure(pcm(480, 600), 60).voiced());
        assertTrue(filter.measure(pcm(480, 600), 80).voiced()); assertFalse(filter.pending());
        assertTrue(filter.measure(pcm(480, 600), 100).voiced());
        assertEquals(480, filter.measure(pcm(480, 0), 120).trailingQuietSamples());
    }
    @Test void splitPacketsAndCoverageGapDoNotCombineUnrelatedOnsets() {
        var filter = new LivePcmActivity.OnsetFilter();
        for (int i = 1; i < 4; i++) assertFalse(filter.measure(pcm(240, 600), i * 10).voiced());
        assertTrue(filter.measure(pcm(240, 600), 40).voiced());
        assertFalse(filter.measure(pcm(480, 600), 2000).voiced(), "Gap resets sustained activity");
        filter.reset(); assertFalse(filter.pending());
        assertFalse(filter.measure(pcm(480, 600), 2020).voiced());
        assertThrows(IllegalArgumentException.class, () -> filter.measure(new byte[3], 2040));
    }
    @Test void undecidedOnsetCannotCommitAUserBoundaryButDoesNotDelayAssistantCaptureOrStop() {
        var capture = new LiveTranscriptSegmenter(UUID.randomUUID(), 2000);
        for (var speaker : Speaker.values()) {
            capture.fragment(new Fragment(speaker.name(), speaker, 0L, 100L, 100, 1, "Pending"));
            for (long at = 120; at <= 2100; at += 20) capture.audio(speaker, 480, false, at);
        }
        var done = capture.poll(2100, true);
        assertEquals(1, done.size()); assertEquals(Speaker.ASSISTANT, done.getFirst().speaker());
        assertEquals(Closure.INCOMPLETE, capture.interrupt(Speaker.USER, "input_muted").getFirst().closure());
        assertTrue(capture.poll(2200, false).isEmpty());
    }
    @Test void confirmedOnsetAtBoundaryResetsQuietInsteadOfSplittingSpeech() {
        var capture = new LiveTranscriptSegmenter(UUID.randomUUID(), 2000);
        capture.fragment(new Fragment("first", Speaker.USER, 0L, 100L, 100, 1, "Please"));
        for (long at = 120; at <= 2080; at += 20) capture.audio(Speaker.USER, 480, false, at);
        var filter = new LivePcmActivity.OnsetFilter();
        var onset = filter.measure(pcm(480, 600), 2100);
        capture.audio(Speaker.USER, onset.samples(), onset.trailingQuietSamples(), onset.voiced(), 2100);
        assertTrue(capture.poll(2100, filter.pending()).isEmpty());
        onset = filter.measure(pcm(480, 600), 2120);
        capture.audio(Speaker.USER, onset.samples(), onset.trailingQuietSamples(), onset.voiced(), 2120);
        assertTrue(capture.poll(2120, filter.pending()).isEmpty());
        capture.fragment(new Fragment("last", Speaker.USER, 2100L, 2200L, 2200, 2, " continue"));
        for (long at = 2220; at <= 4200; at += 20) capture.audio(Speaker.USER, 480, false, at);
        assertEquals("Please continue", capture.poll(4200).getFirst().text());
    }
    @Test void filteringCannotTurnMissingAudioCoverageIntoACompleteTurn() {
        var capture = new LiveTranscriptSegmenter(UUID.randomUUID(), 2000);
        var filter = new LivePcmActivity.OnsetFilter();
        capture.fragment(new Fragment("word", Speaker.USER, 0L, 100L, 100, 1, "Retained"));
        for (long at = 1000; at <= 3000; at += 20) {
            var activity = filter.measure(pcm(480, 0), at);
            capture.audio(Speaker.USER, activity.samples(), activity.trailingQuietSamples(), activity.voiced(), at);
        }
        var done = capture.poll(3000, filter.pending()).getFirst();
        assertEquals(Closure.INCOMPLETE, done.closure()); assertEquals("audio_coverage_gap", done.reason());
        assertEquals("Retained", done.text());
    }
}
