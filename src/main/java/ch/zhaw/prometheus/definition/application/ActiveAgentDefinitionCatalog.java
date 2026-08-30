package ch.zhaw.prometheus.definition.application;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.zhaw.prometheus.definition.compiled.CompiledDefinitionCache;
import ch.zhaw.prometheus.definition.repository.DefinitionRepository;

@Service
@Transactional(readOnly = true)
public class ActiveAgentDefinitionCatalog {
    private final DefinitionRepository repository;
    private final CompiledDefinitionCache cache;

    public ActiveAgentDefinitionCatalog(DefinitionRepository repository, CompiledDefinitionCache cache) {
        this.repository = repository;
        this.cache = cache;
    }

    public List<ActiveAgentDefinition> list() {
        return this.repository.findActiveRevisions().stream()
                .map(revision -> {
                    var compiled = this.cache.resolve(revision.id());
                    return new ActiveAgentDefinition(revision.id(), compiled,
                            packagePath(compiled.metadata().categoryPath()));
                })
                .sorted(java.util.Comparator.comparing(item -> item.compiled().key()))
                .toList();
    }

    public Optional<ActiveAgentDefinition> find(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return this.list().stream().filter(item -> key.equals(item.compiled().key())).findFirst();
    }

    private static List<String> packagePath(String categoryPath) {
        if (categoryPath == null || categoryPath.isBlank()) {
            return List.of();
        }
        return Arrays.stream(categoryPath.trim().split("[./]+"))
                .filter(part -> !part.isBlank()).toList();
    }
}
