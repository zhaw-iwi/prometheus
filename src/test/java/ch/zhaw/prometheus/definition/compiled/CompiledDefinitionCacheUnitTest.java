package ch.zhaw.prometheus.definition.compiled;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import ch.zhaw.prometheus.definition.component.BuiltInComponentCatalog;
import ch.zhaw.prometheus.definition.document.AgentDefinitionDocument;
import ch.zhaw.prometheus.definition.document.AgentDefinitionJson;

class CompiledDefinitionCacheUnitTest {
    private AgentDefinitionJson json;
    private AgentDefinitionDocument document;
    private DefinitionRevisionSource source;
    private DefinitionCompiler compiler;

    @BeforeEach
    void setUp() {
        this.json = new AgentDefinitionJson();
        try (InputStream input = getClass().getResourceAsStream(
                "/agent-definitions/valid/deterministic-components.json")) {
            this.document = this.json.parse(input);
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
        this.source = new DefinitionRevisionSource(41, this.json.contentHash(this.document), this.document);
        this.compiler = new DefinitionCompiler(BuiltInComponentCatalog.createRegistry(), this.json);
    }

    @Test
    void concurrentResolutionCompilesExactlyOnce() throws Exception {
        AtomicInteger compilations = new AtomicInteger();
        DefinitionCacheMetrics metrics = new DefinitionCacheMetrics();
        CompiledDefinitionCache cache = new CompiledDefinitionCache(definition -> {
            compilations.incrementAndGet();
            return this.compiler.compile(definition);
        }, null, metrics);
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<CompiledAgentDefinition>> calls = new ArrayList<>();
            for (int index = 0; index < 40; index++) {
                calls.add(() -> cache.resolve(this.source));
            }
            List<CompiledAgentDefinition> results = executor.invokeAll(calls).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }).toList();

            results.forEach(result -> assertSame(results.getFirst(), result));
            assertEquals(1, compilations.get());
            assertEquals(1, metrics.snapshot().compilations());
            assertEquals(1, cache.size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void revisionsNeverAliasAndSameRevisionIsHashGuarded() {
        CompiledDefinitionCache cache = new CompiledDefinitionCache(this.compiler);
        CompiledAgentDefinition first = cache.resolve(this.source);
        CompiledAgentDefinition second = cache.resolve(new DefinitionRevisionSource(42,
                this.source.contentHash(), this.document));

        assertNotSame(first, second);
        assertSame(first, cache.resolve(this.source));
        assertThrows(IllegalStateException.class, () -> cache.resolve(new DefinitionRevisionSource(41,
                "0".repeat(64), this.document)));
    }

    @Test
    void failedCompilationIsRemovedAndCanBeRetried() {
        AtomicInteger attempts = new AtomicInteger();
        CompiledDefinitionCache cache = new CompiledDefinitionCache(definition -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("synthetic failure");
            }
            return this.compiler.compile(definition);
        });

        assertThrows(IllegalStateException.class, () -> cache.resolve(this.source));
        assertEquals(0, cache.size());
        assertEquals(this.document.key(), cache.resolve(this.source).key());
        assertEquals(2, attempts.get());
    }

    @Test
    void syntheticInstancesShareDefinitionButKeepMutableStorageIsolated() {
        CompiledDefinitionCache cache = new CompiledDefinitionCache(this.compiler);
        List<SyntheticInstance> instances = new ArrayList<>();
        for (int index = 0; index < 1_000; index++) {
            instances.add(new SyntheticInstance(cache.resolve(this.source), new HashMap<>()));
        }
        instances.getFirst().storage().put("round_count", this.json.parse(this.json.canonicalJson(this.document))
                .storage().getFirst().examples().getFirst());

        instances.forEach(instance -> assertSame(instances.getFirst().definition(), instance.definition()));
        assertEquals(1, instances.getFirst().storage().size());
        assertEquals(0, instances.get(1).storage().size());
    }

    private record SyntheticInstance(CompiledAgentDefinition definition, Map<String, JsonNode> storage) {
    }
}
