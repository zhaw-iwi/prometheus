package ch.zhaw.prometheus.definition.compiled;

public interface DefinitionCacheObserver {
    default void hit(long revisionId) {
    }

    default void miss(long revisionId) {
    }

    default void compiled(long revisionId) {
    }

    default void failed(long revisionId, RuntimeException failure) {
    }

    static DefinitionCacheObserver none() {
        return new DefinitionCacheObserver() {
        };
    }
}
