package ch.zhaw.prometheus.model.snapshot;

import ch.zhaw.prometheus.model.event.EventHistory;

public interface SnapshotAggregator {
    ObservationSnapshot aggregate(EventHistory events);
}
