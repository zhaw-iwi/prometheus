package ch.zhaw.prometheus.model.regulation;

import java.time.Instant;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.snapshot.ObservationSnapshot;

public record RegulationContext(
        Event triggerEvent,
        EventHistory eventHistory,
        ObservationSnapshot snapshot,
        Instant now) {
}
