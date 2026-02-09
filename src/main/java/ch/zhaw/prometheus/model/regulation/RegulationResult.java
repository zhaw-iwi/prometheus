package ch.zhaw.prometheus.model.regulation;

import java.util.List;

import ch.zhaw.prometheus.model.event.Event;

public record RegulationResult(
        ModulationBundle modulation,
        List<Event> internalEvents) {

    public static RegulationResult none() {
        return new RegulationResult(ModulationBundle.neutral(), List.of());
    }
}
