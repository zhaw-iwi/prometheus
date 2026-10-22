package ch.zhaw.prometheus.application.live;

/** Coarse 20 ms RMS activity gate. An experimental acoustic cue, not a provider turn or echo detector. */
public record LivePcmActivity(int samples, int trailingQuietSamples, boolean voiced, int voicedSamples, int peakRms) {
    public static LivePcmActivity measure(byte[] pcm) {
        return measure(pcm, null);
    }
    private static LivePcmActivity measure(byte[] pcm, OnsetFilter filter) {
        if (pcm.length % 2 != 0 || pcm.length > 200000) throw new IllegalArgumentException("Invalid reflected PCM");
        int samples = pcm.length / 2, quiet = 0, voicedSamples = 0, peak = 0; boolean voiced = false;
        for (int start = 0; start < samples; start += 480) {
            int count = Math.min(480, samples - start); long energy = 0;
            for (int i = start; i < start + count; i++) {
                int value = (short) ((pcm[i * 2] & 255) | (pcm[i * 2 + 1] << 8)); energy += (long) value * value;
            }
            double rms = Math.sqrt((double) energy / count); peak = Math.max(peak, (int) rms);
            boolean active = filter == null ? rms > 500 : filter.frame(count, rms > 500);
            if (active) { quiet = 0; voiced = true; voicedSamples += count; }
            else quiet += count;
        }
        return new LivePcmActivity(samples, quiet, voiced, voicedSamples, peak);
    }

    /** Two consecutive 20-ms activity frames establish onset. No audio or transcript is retained. */
    public static final class OnsetFilter {
        private static final int MIN_SAMPLES = 960; // 40 ms at 24 kHz; RMS threshold remains 500.
        private int activeSamples;
        private long lastReceived = -1;
        public LivePcmActivity measure(byte[] pcm, long receivedMs) {
            if (lastReceived >= 0 && receivedMs - lastReceived > pcm.length / 48L + LiveTranscriptSegmenter.MAX_AUDIO_GAP_MS)
                reset();
            var result = LivePcmActivity.measure(pcm, this);
            lastReceived = receivedMs;
            return result;
        }
        private boolean frame(int samples, boolean active) {
            activeSamples = active ? Math.min(MIN_SAMPLES, activeSamples + samples) : 0;
            return activeSamples >= MIN_SAMPLES;
        }
        /** Do not commit a boundary while an onset is still being decided. */
        public boolean pending() { return activeSamples > 0 && activeSamples < MIN_SAMPLES; }
        public void reset() { activeSamples = 0; lastReceived = -1; }
    }
}
