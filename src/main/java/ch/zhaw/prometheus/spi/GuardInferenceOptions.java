package ch.zhaw.prometheus.spi;

public record GuardInferenceOptions(Strategy strategy, int maxBatchSize, int maxCharacters) {
    public enum Strategy { ORDERED, COMBINED, PARALLEL, COMBINED_PARALLEL }
    public static final GuardInferenceOptions ORDERED = new GuardInferenceOptions(Strategy.ORDERED, 16, 65536);
    public GuardInferenceOptions {
        if (strategy == null || maxBatchSize < 1 || maxBatchSize > 64 || maxCharacters < 1024 || maxCharacters > 1_000_000) {
            throw new IllegalArgumentException("Invalid guard strategy or batch limits");
        }
    }
}
