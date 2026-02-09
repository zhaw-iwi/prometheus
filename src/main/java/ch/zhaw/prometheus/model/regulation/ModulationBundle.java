package ch.zhaw.prometheus.model.regulation;

public record ModulationBundle(
        double aggression,
        double supplication,
        double exploration,
        double avoidance,
        double affiliation) {

    public static ModulationBundle neutral() {
        return new ModulationBundle(0.0d, 0.0d, 0.0d, 0.0d, 0.0d);
    }
}
