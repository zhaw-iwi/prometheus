package ch.zhaw.prometheus.model.regulation;

import java.time.Instant;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.snapshot.ObservationSnapshot;

public record RegulationContext(
        Event triggerEvent,
        ObservationSnapshot snapshot,
        Instant now) {
}
