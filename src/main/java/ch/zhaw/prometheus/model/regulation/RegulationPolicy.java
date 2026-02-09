package ch.zhaw.prometheus.model.regulation;

public interface RegulationPolicy {
    RegulationEffect evaluate(RegulationContext context);
}
