package ch.zhaw.prometheus.model.regulation;

public interface PersistableRegulationSystem extends RegulationSystem {
    RegulationSystemSpec toSpec();
}
