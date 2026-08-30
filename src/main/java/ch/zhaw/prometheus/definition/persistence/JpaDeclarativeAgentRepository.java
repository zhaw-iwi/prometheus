package ch.zhaw.prometheus.definition.persistence;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import ch.zhaw.prometheus.definition.compiled.ImmutableJson;
import ch.zhaw.prometheus.definition.instance.DeclarativeAgentOptimisticLockException;
import ch.zhaw.prometheus.definition.instance.DeclarativeAgentRepository;
import ch.zhaw.prometheus.definition.instance.PersistedDeclarativeAgent;
import ch.zhaw.prometheus.definition.runtime.RuntimeEvent;

@Repository
@Transactional
public class JpaDeclarativeAgentRepository implements DeclarativeAgentRepository {
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final TypeReference<List<RuntimeEvent>> EVENTS = new TypeReference<>() {
    };

    private final DeclarativeAgentInstanceJpaRepository instances;
    private final AgentDefinitionRevisionJpaRepository revisions;

    public JpaDeclarativeAgentRepository(DeclarativeAgentInstanceJpaRepository instances,
            AgentDefinitionRevisionJpaRepository revisions) {
        this.instances = instances;
        this.revisions = revisions;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PersistedDeclarativeAgent> find(UUID id) {
        return this.instances.findById(id).map(JpaDeclarativeAgentRepository::map);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersistedDeclarativeAgent> findAll() {
        return this.instances.findAll().stream().map(JpaDeclarativeAgentRepository::map).toList();
    }

    @Override
    public PersistedDeclarativeAgent create(PersistedDeclarativeAgent instance) {
        AgentDefinitionRevisionEntity revision = this.revisions.findById(instance.definitionRevisionId())
                .orElseThrow(() -> new IllegalArgumentException("Definition revision does not exist: "
                        + instance.definitionRevisionId()));
        DeclarativeAgentInstanceEntity entity = new DeclarativeAgentInstanceEntity(instance.id(), revision,
                instance.activeLeafStateId(), storageJson(instance.storage()), storageJson(instance.initialStorage()),
                JSON.valueToTree(instance.history()), instance.started(), instance.status());
        try {
            return map(this.instances.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("Unable to persist declarative agent " + instance.id(), exception);
        }
    }

    @Override
    public PersistedDeclarativeAgent update(PersistedDeclarativeAgent instance, long expectedOptimisticVersion) {
        DeclarativeAgentInstanceEntity entity = this.instances.findById(instance.id())
                .orElseThrow(() -> new IllegalArgumentException("Declarative agent does not exist: " + instance.id()));
        if (entity.getOptimisticVersion() != expectedOptimisticVersion) {
            throw new DeclarativeAgentOptimisticLockException("Optimistic version mismatch for instance "
                    + instance.id());
        }
        if (entity.getDefinitionRevision().getId() != instance.definitionRevisionId()) {
            throw new IllegalArgumentException("A declarative agent cannot change its pinned revision");
        }
        entity.replaceRuntime(instance.activeLeafStateId(), storageJson(instance.storage()),
                JSON.valueToTree(instance.history()), instance.started(), instance.status());
        try {
            return map(this.instances.saveAndFlush(entity));
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new DeclarativeAgentOptimisticLockException("Concurrent update of instance " + instance.id(),
                    exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByDefinitionRevisionId(long revisionId) {
        return this.instances.existsByDefinitionRevision_Id(revisionId);
    }

    @Override
    public void delete(UUID id) {
        this.instances.deleteById(id);
        this.instances.flush();
    }

    private static PersistedDeclarativeAgent map(DeclarativeAgentInstanceEntity entity) {
        return new PersistedDeclarativeAgent(entity.getId(), entity.getDefinitionRevision().getId(),
                entity.getActiveLeafStateId(), storage(entity.getStorageJson()), storage(entity.getInitialStorageJson()),
                JSON.convertValue(entity.getHistoryJson(), EVENTS), entity.isStarted(), entity.getRuntimeStatus(),
                entity.getOptimisticVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private static ObjectNode storageJson(Map<String, ImmutableJson> storage) {
        ObjectNode object = JsonNodeFactory.instance.objectNode();
        storage.forEach((key, value) -> object.set(key, value.value()));
        return object;
    }

    private static Map<String, ImmutableJson> storage(JsonNode json) {
        if (json == null || !json.isObject()) {
            throw new IllegalStateException("Persisted runtime storage must be a JSON object");
        }
        Map<String, ImmutableJson> storage = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = json.fields();
        fields.forEachRemaining(field -> storage.put(field.getKey(), new ImmutableJson(field.getValue())));
        return storage;
    }
}
