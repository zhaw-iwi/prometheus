package ch.zhaw.prometheus.model.regulation;

import java.util.LinkedHashMap;
import java.util.Map;

public record ModulationBundle(Map<String, Double> values) {

    public ModulationBundle {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static ModulationBundle neutral() {
        return new ModulationBundle(Map.of());
    }

    public static ModulationBundle single(String key, double value) {
        if (key == null || key.isBlank()) {
            return neutral();
        }
        Map<String, Double> data = new LinkedHashMap<>();
        data.put(key, value);
        return new ModulationBundle(data);
    }

    public double get(String key) {
        if (key == null) {
            return 0.0d;
        }
        return this.values.getOrDefault(key, 0.0d);
    }
}
