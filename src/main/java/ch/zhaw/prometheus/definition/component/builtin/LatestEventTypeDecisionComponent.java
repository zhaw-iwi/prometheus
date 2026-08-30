package ch.zhaw.prometheus.definition.component.builtin;

import ch.zhaw.prometheus.definition.component.CompiledDecision;

public record LatestEventTypeDecisionComponent(String eventType) implements CompiledDecision {
}
