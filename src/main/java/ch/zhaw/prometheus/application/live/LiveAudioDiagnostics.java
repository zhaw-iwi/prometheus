package ch.zhaw.prometheus.application.live;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Bounded reflected-audio activity, never audio content or proof of browser playback. */
public final class LiveAudioDiagnostics {
    public record Window(long serverMs, long endServerMs, long inputSamples, long outputSamples,
            long voicedInputSamples, long voicedOutputSamples, int inputPeakRms, int outputPeakRms) {}
    public record Status(List<Window> recent, long dropped) {}
    private final ArrayDeque<Window> recent = new ArrayDeque<>();
    private long start = -1, end, input, output, voicedInput, voicedOutput, dropped;
    private int inputPeak, outputPeak;
    private long silentInputSamples, lastInputAt = -1;

    public synchronized LivePcmActivity observe(boolean incoming, byte[] pcm, long now) {
        var activity = LivePcmActivity.measure(pcm);
        if (incoming) {
            if (lastInputAt >= 0 && now - lastInputAt > pcm.length / 48L + LiveTranscriptSegmenter.MAX_AUDIO_GAP_MS)
                silentInputSamples = 0;
            silentInputSamples = activity.peakRms() == 0 ? Math.min(144000, silentInputSamples + activity.samples()) : 0;
            lastInputAt = now;
        }
        if (start >= 0 && now - start >= 1000) {
            if (recent.size() == 128) { recent.removeFirst(); dropped++; }
            recent.addLast(window());
            start = -1; input = output = voicedInput = voicedOutput = 0; inputPeak = outputPeak = 0;
        }
        if (start < 0) start = now;
        end = now;
        if (incoming) { input += activity.samples(); voicedInput += activity.voicedSamples(); inputPeak = Math.max(inputPeak, activity.peakRms()); }
        else { output += activity.samples(); voicedOutput += activity.voicedSamples(); outputPeak = Math.max(outputPeak, activity.peakRms()); }
        return activity;
    }
    private Window window() { return new Window(start, end, input, output, voicedInput, voicedOutput, inputPeak, outputPeak); }
    public synchronized Status status() {
        var windows = new ArrayList<>(recent);
        if (start >= 0) windows.add(window());
        return new Status(List.copyOf(windows), dropped);
    }
    /** Reject only isolated protocol-like fragments over sustained measured silence.
     * Ordinary words, recent voice, absent audio and gaps retain normal admission. */
    public synchronized boolean silentStructuralFragment(String text, long now) {
        return text != null && text.length() <= 32 && text.matches("[\\p{L}\\p{N}\\s:;,\\[\\]{}\"']+")
                && text.matches(".*[\\[\\]{}:].*") && text.replaceAll("[^\\p{L}\\p{N}]", "").length() <= 2
                && silentInputSamples >= 120000 && lastInputAt >= 0 && now >= lastInputAt && now - lastInputAt <= 500;
    }
}
