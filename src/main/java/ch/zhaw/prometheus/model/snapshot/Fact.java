package ch.zhaw.prometheus.model.snapshot;

import java.util.List;
import java.util.Objects;

public final class Fact {
    private final String key;
    private final Object value;
    private final double confidence;
    private final List<String> provenance;

    public Fact(String key, Object value, double confidence, List<String> provenance) {
        this.key = Objects.requireNonNull(key, "key");
        this.value = value;
        this.confidence = clampConfidence(confidence);
        this.provenance = provenance == null ? List.of() : List.copyOf(provenance);
    }

    public static Fact of(String key, Object value) {
        return new Fact(key, value, 1.0d, List.of());
    }

    public static Fact of(String key, Object value, double confidence, List<String> provenance) {
        return new Fact(key, value, confidence, provenance);
    }

    public String getKey() {
        return this.key;
    }

    public Object getValue() {
        return this.value;
    }

    public double getConfidence() {
        return this.confidence;
    }

    public List<String> getProvenance() {
        return this.provenance;
    }

    private static double clampConfidence(double confidence) {
        if (Double.isNaN(confidence)) {
            return 0.0d;
        }
        if (confidence < 0.0d) {
            return 0.0d;
        }
        if (confidence > 1.0d) {
            return 1.0d;
        }
        return confidence;
    }
}
