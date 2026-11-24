package ch.zhaw.prometheus.application.live;

import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Fragment;

/** Reflected input has no media timestamps. Only use sustained digital zero, near a fresh
 * reflected output clock, with continuous samples on both sides of the candidate caption. */
public final class LiveInputSilence {
    private static final long MARGIN_MS = 1000;
    private static final long GAP_MS = LiveTranscriptSegmenter.MAX_AUDIO_GAP_MS;
    private long zeroSince = -1, lastInput = -1, samples;
    private long outputEnd = -1, outputAt = -1;

    public void input(byte[] pcm, long now) {
        int count = pcm.length / 2;
        if (count == 0) {
            clear();
            return;
        }
        for (byte sample : pcm) {
            if (sample != 0) {
                clear();
                return;
            }
        }
        if (zeroSince < 0 || now < lastInput || now - lastInput > GAP_MS
                || Math.abs((now - zeroSince) * 24 - samples - count) > GAP_MS * 24) {
            zeroSince = now;
            samples = 0;
        }
        lastInput = now;
        samples += count;
    }
    public void output(Long start, Long end, int count, long now) {
        if (start == null || end == null || start < 0 || end <= start || end > 3_600_000
                || Math.abs((end - start) * 24 - count) > 24) {
            clear();
            outputEnd = -1;
            return;
        }
        if (outputEnd >= 0 && start != outputEnd) clear();
        outputEnd = end;
        outputAt = now;
    }
    public void clear() {
        zeroSince = -1;
        samples = 0;
        lastInput = -1;
    }
    public boolean covers(Fragment fragment, long now) {
        if (zeroSince < 0 || now < lastInput || now - lastInput > GAP_MS
                || outputEnd < 0 || now < outputAt || now - outputAt > GAP_MS
                || fragment.startMs() == null || fragment.endMs() == null || fragment.startMs() < 0
                || fragment.endMs() <= fragment.startMs() || fragment.endMs() > 3_600_000) return false;
        long mediaNow = outputEnd + now - outputAt;
        long covered = Math.min(samples / 24, lastInput - zeroSince);
        return fragment.startMs() >= mediaNow - covered + MARGIN_MS && fragment.endMs() <= mediaNow + GAP_MS;
    }
    public boolean confirmed(Fragment fragment, long now) {
        return covers(fragment, now) && lastInput - fragment.receivedMs() >= MARGIN_MS
                && outputEnd - fragment.endMs() >= MARGIN_MS;
    }
}
