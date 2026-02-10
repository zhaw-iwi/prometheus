package ch.zhaw.prometheus.model.regulation;

public class NoOpRegulationSystem implements PersistableRegulationSystem {

    @Override
    public RegulationResult update(RegulationContext context) {
        return RegulationResult.none();
    }

    @Override
    public void reset() {
        // no-op
    }

    @Override
    public RegulationSystemSpec toSpec() {
        return RegulationSystemSpec.noOp();
    }
}
