package ch.zhaw.prometheus.model.snapshot;

public enum SnapshotAggregatorType {
    DEFAULT_OBSERVATION;

    public SnapshotAggregator create() {
        return switch (this) {
            case DEFAULT_OBSERVATION -> DefaultObservationSnapshotAggregator.INSTANCE;
        };
    }
}
