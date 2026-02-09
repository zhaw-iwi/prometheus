package ch.zhaw.prometheus.model.regulation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RegulationEffect(
        Map<String, Double> deltas,
        double confidence,
        List<String> provenance) {

    public RegulationEffect {
        deltas = deltas == null ? Map.of() : Map.copyOf(deltas);
        provenance = provenance == null ? List.of() : List.copyOf(provenance);
    }

    public static RegulationEffect none() {
        return new RegulationEffect(Map.of(), 0.0d, List.of());
    }

    public static RegulationEffect single(String variable, double delta, double confidence, List<String> provenance) {
        if (variable == null || variable.isBlank()) {
            return none();
        }
        Map<String, Double> values = new LinkedHashMap<>();
        values.put(variable, delta);
        return new RegulationEffect(values, confidence, provenance);
    }

    public double deltaFor(String variable) {
        if (variable == null) {
            return 0.0d;
        }
        return this.deltas.getOrDefault(variable, 0.0d);
    }
}
