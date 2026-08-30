package ch.zhaw.prometheus.definition.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import ch.zhaw.prometheus.definition.instance.RuntimeInstanceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "declarative_agent_instance")
public class DeclarativeAgentInstanceEntity {
    @Id
    @Column(name = "id", columnDefinition = "binary(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_revision_id", nullable = false, updatable = false)
    private AgentDefinitionRevisionEntity definitionRevision;

    @Column(name = "active_leaf_state_id", nullable = false, length = 190)
    private String activeLeafStateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "storage_json", nullable = false, columnDefinition = "json")
    private JsonNode storageJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "initial_storage_json", nullable = false, updatable = false, columnDefinition = "json")
    private JsonNode initialStorageJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "history_json", nullable = false, columnDefinition = "json")
    private JsonNode historyJson;

    @Column(name = "started", nullable = false)
    private boolean started;

    @Enumerated(EnumType.STRING)
    @Column(name = "runtime_status", nullable = false, length = 16)
    private RuntimeInstanceStatus runtimeStatus;

    @Version
    @Column(name = "optimistic_version", nullable = false)
    private long optimisticVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeclarativeAgentInstanceEntity() {
    }

    public DeclarativeAgentInstanceEntity(UUID id, AgentDefinitionRevisionEntity definitionRevision,
            String activeLeafStateId, JsonNode storageJson, JsonNode initialStorageJson, JsonNode historyJson,
            boolean started, RuntimeInstanceStatus runtimeStatus) {
        this.id = id;
        this.definitionRevision = definitionRevision;
        this.activeLeafStateId = activeLeafStateId;
        this.storageJson = storageJson;
        this.initialStorageJson = initialStorageJson;
        this.historyJson = historyJson;
        this.started = started;
        this.runtimeStatus = runtimeStatus;
    }

    public UUID getId() {
        return this.id;
    }

    public AgentDefinitionRevisionEntity getDefinitionRevision() {
        return this.definitionRevision;
    }

    public String getActiveLeafStateId() {
        return this.activeLeafStateId;
    }

    public JsonNode getStorageJson() {
        return this.storageJson;
    }

    public JsonNode getInitialStorageJson() {
        return this.initialStorageJson;
    }

    public JsonNode getHistoryJson() {
        return this.historyJson;
    }

    public boolean isStarted() {
        return this.started;
    }

    public RuntimeInstanceStatus getRuntimeStatus() {
        return this.runtimeStatus;
    }

    public long getOptimisticVersion() {
        return this.optimisticVersion;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public void replaceRuntime(String activeLeafStateId, JsonNode storageJson, JsonNode historyJson,
            boolean started, RuntimeInstanceStatus runtimeStatus) {
        this.activeLeafStateId = activeLeafStateId;
        this.storageJson = storageJson;
        this.historyJson = historyJson;
        this.started = started;
        this.runtimeStatus = runtimeStatus;
    }
}
