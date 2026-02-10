package ch.zhaw.prometheus.model.regulation;

public interface RegulationSystem {
    RegulationResult update(RegulationContext context);

    default void reset() {
        // default no-op for stateless regulation systems
    }
}
