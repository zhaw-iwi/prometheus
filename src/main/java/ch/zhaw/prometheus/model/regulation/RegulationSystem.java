package ch.zhaw.prometheus.model.regulation;

public interface RegulationSystem {
    RegulationResult update(RegulationContext context);
}
