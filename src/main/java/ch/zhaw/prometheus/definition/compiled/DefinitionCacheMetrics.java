package ch.zhaw.prometheus.definition.compiled;

import java.util.concurrent.atomic.LongAdder;

/** Thread-safe observer that records only counts, never definition content. */
public final class DefinitionCacheMetrics implements DefinitionCacheObserver {
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder compilations = new LongAdder();
    private final LongAdder failures = new LongAdder();

    @Override
    public void hit(long revisionId) {
        this.hits.increment();
    }

    @Override
    public void miss(long revisionId) {
        this.misses.increment();
    }

    @Override
    public void compiled(long revisionId) {
        this.compilations.increment();
    }

    @Override
    public void failed(long revisionId, RuntimeException failure) {
        this.failures.increment();
    }

    public Snapshot snapshot() {
        return new Snapshot(this.hits.sum(), this.misses.sum(), this.compilations.sum(), this.failures.sum());
    }

    public record Snapshot(long hits, long misses, long compilations, long failures) {
    }
}
