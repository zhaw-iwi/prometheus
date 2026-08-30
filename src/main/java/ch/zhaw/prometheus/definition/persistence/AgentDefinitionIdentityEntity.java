package ch.zhaw.prometheus.definition.persistence;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(name = "agent_definition", uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_definition_key", columnNames = "definition_key")
})
public class AgentDefinitionIdentityEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "definition_key", nullable = false, length = 190)
    private String definitionKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_revision_id")
    private AgentDefinitionRevisionEntity activeRevision;

    @Version
    @Column(name = "optimistic_version", nullable = false)
    private long optimisticVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentDefinitionIdentityEntity() {
    }

    public AgentDefinitionIdentityEntity(String definitionKey) {
        this.definitionKey = definitionKey;
    }

    public Long getId() {
        return this.id;
    }

    public String getDefinitionKey() {
        return this.definitionKey;
    }

    public AgentDefinitionRevisionEntity getActiveRevision() {
        return this.activeRevision;
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

    public void activate(AgentDefinitionRevisionEntity revision) {
        this.activeRevision = revision;
    }
}
