package ch.zhaw.prometheus.model.regulation;

public class NoOpRegulationSystem implements PersistableRegulationSystem {

    @Override
    public RegulationResult update(RegulationContext context) {
        return RegulationResult.none();
    }

    @Override
    public RegulationSystemSpec toSpec() {
        return RegulationSystemSpec.noOp();
    }
}
