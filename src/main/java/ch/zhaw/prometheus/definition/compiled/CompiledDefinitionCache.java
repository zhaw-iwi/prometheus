package ch.zhaw.prometheus.definition.compiled;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Revision-identity cache with hash guards and single-flight compilation. */
public final class CompiledDefinitionCache {
    private final CompiledDefinitionFactory compiler;
    private final DefinitionRevisionLoader loader;
    private final DefinitionCacheObserver observer;
    private final ConcurrentMap<Long, CacheEntry> entries = new ConcurrentHashMap<>();

    public CompiledDefinitionCache(CompiledDefinitionFactory compiler) {
        this(compiler, null, DefinitionCacheObserver.none());
    }

    public CompiledDefinitionCache(CompiledDefinitionFactory compiler, DefinitionRevisionLoader loader,
            DefinitionCacheObserver observer) {
        if (compiler == null) {
            throw new IllegalArgumentException("compiler must not be null");
        }
        this.compiler = compiler;
        this.loader = loader;
        this.observer = observer == null ? DefinitionCacheObserver.none() : observer;
    }

    public CompiledAgentDefinition resolve(long revisionId) {
        if (this.loader == null) {
            throw new IllegalStateException("No definition revision loader is configured");
        }
        return resolve(this.loader.load(revisionId));
    }

    public CompiledAgentDefinition resolve(DefinitionRevisionSource source) {
        while (true) {
            CacheEntry existing = this.entries.get(source.revisionId());
            if (existing != null) {
                requireMatchingHash(source, existing);
                this.observer.hit(source.revisionId());
                return await(existing.compiled());
            }

            CacheEntry candidate = new CacheEntry(source.contentHash(), new CompletableFuture<>());
            CacheEntry winner = this.entries.putIfAbsent(source.revisionId(), candidate);
            if (winner != null) {
                continue;
            }
            this.observer.miss(source.revisionId());
            try {
                CompiledAgentDefinition compiled = this.compiler.compile(source.definition());
                if (!source.contentHash().equals(compiled.contentHash())) {
                    throw new IllegalStateException("Revision " + source.revisionId()
                            + " content hash does not match its canonical definition");
                }
                candidate.compiled().complete(compiled);
                this.observer.compiled(source.revisionId());
                return compiled;
            } catch (RuntimeException failure) {
                candidate.compiled().completeExceptionally(failure);
                this.entries.remove(source.revisionId(), candidate);
                this.observer.failed(source.revisionId(), failure);
                throw failure;
            }
        }
    }

    /** Installs an already validated and compiled revision after its publication commit. */
    public CompiledAgentDefinition install(DefinitionRevisionSource source, CompiledAgentDefinition compiled) {
        if (source == null || compiled == null) {
            throw new IllegalArgumentException("source and compiled definition must not be null");
        }
        if (!source.contentHash().equals(compiled.contentHash())
                || !source.definition().key().equals(compiled.key())
                || source.definition().revision() != compiled.revision()) {
            throw new IllegalArgumentException("Compiled definition does not match its revision source");
        }
        CompletableFuture<CompiledAgentDefinition> completed = CompletableFuture.completedFuture(compiled);
        CacheEntry candidate = new CacheEntry(source.contentHash(), completed);
        CacheEntry existing = this.entries.putIfAbsent(source.revisionId(), candidate);
        if (existing == null) {
            return compiled;
        }
        requireMatchingHash(source, existing);
        return await(existing.compiled());
    }

    public List<CompiledAgentDefinition> prewarm(List<DefinitionRevisionSource> revisions) {
        if (revisions == null) {
            throw new IllegalArgumentException("revisions must not be null");
        }
        return revisions.stream().map(this::resolve).toList();
    }

    public int size() {
        return this.entries.size();
    }

    private static void requireMatchingHash(DefinitionRevisionSource source, CacheEntry cached) {
        if (!cached.contentHash().equals(source.contentHash())) {
            throw new IllegalStateException("Revision " + source.revisionId()
                    + " was resolved with two different content hashes");
        }
    }

    private static CompiledAgentDefinition await(CompletableFuture<CompiledAgentDefinition> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw exception;
        }
    }

    private record CacheEntry(String contentHash, CompletableFuture<CompiledAgentDefinition> compiled) {
    }
}
