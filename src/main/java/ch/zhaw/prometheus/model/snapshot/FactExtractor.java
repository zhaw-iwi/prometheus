package ch.zhaw.prometheus.model.snapshot;

import java.util.Optional;

import ch.zhaw.prometheus.model.event.EventHistory;

@FunctionalInterface
public interface FactExtractor {
    Optional<Fact> extract(EventHistory events);
}
