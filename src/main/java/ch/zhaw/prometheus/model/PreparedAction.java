package ch.zhaw.prometheus.model;

import java.util.Map;
import java.util.UUID;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

/** Frozen worker input. Work must capture values only, never entities or a persistence context. */
public record PreparedAction(UUID actionId, UUID storageId, Map<String, String> expectedVersions, Work work) {
    @FunctionalInterface
    public interface Work {
        /** JSON-encoded values for exactly the declared destination keys; no entity mutation. */
        Map<String, String> compute(LanguageModelGateway gateway);
    }
    public PreparedAction {
        expectedVersions = Map.copyOf(expectedVersions);
        java.util.Objects.requireNonNull(work);
    }
    @Override public String toString() { return "PreparedAction[" + actionId + "]"; }
}
