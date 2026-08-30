package ch.zhaw.prometheus.definition.document;

public record AtomicStateDefinition(
        String id,
        String name,
        String entryMode,
        boolean oblivious,
        ComponentEnvelope eventSelector,
        ComponentEnvelope policy) implements StateDefinition {
}
