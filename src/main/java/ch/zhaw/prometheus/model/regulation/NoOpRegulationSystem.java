package ch.zhaw.prometheus.model.regulation;

public class NoOpRegulationSystem implements RegulationSystem {

    @Override
    public RegulationResult update(RegulationContext context) {
        return RegulationResult.none();
    }
}
