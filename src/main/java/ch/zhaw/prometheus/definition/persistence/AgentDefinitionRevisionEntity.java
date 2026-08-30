package ch.zhaw.prometheus.definition.persistence;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import ch.zhaw.prometheus.definition.repository.DefinitionProvenance;
import ch.zhaw.prometheus.definition.repository.DefinitionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "agent_definition_revision", uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_definition_revision", columnNames = { "definition_id", "revision_number" })
})
public class AgentDefinitionRevisionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false)
    private AgentDefinitionIdentityEntity definition;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 16)
    private DefinitionStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specification_json", nullable = false, columnDefinition = "json")
    private JsonNode specificationJson;

    @Column(name = "content_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "provenance", nullable = false, length = 16)
    private DefinitionProvenance provenance;

    @Column(name = "source_detail", length = 512)
    private String sourceDetail;

    @Version
    @Column(name = "optimistic_version", nullable = false)
    private long optimisticVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected AgentDefinitionRevisionEntity() {
    }

    public AgentDefinitionRevisionEntity(AgentDefinitionIdentityEntity definition, int revisionNumber,
            int schemaVersion, DefinitionStatus status, JsonNode specificationJson, String contentHash,
            DefinitionProvenance provenance, String sourceDetail) {
        this.definition = definition;
        this.revisionNumber = revisionNumber;
        this.schemaVersion = schemaVersion;
        this.status = status;
        this.specificationJson = specificationJson;
        this.contentHash = contentHash;
        this.provenance = provenance;
        this.sourceDetail = sourceDetail;
        if (status == DefinitionStatus.PUBLISHED) {
            this.publishedAt = Instant.now();
        }
    }

    public Long getId() {
        return this.id;
    }

    public AgentDefinitionIdentityEntity getDefinition() {
        return this.definition;
    }

    public int getRevisionNumber() {
        return this.revisionNumber;
    }

    public int getSchemaVersion() {
        return this.schemaVersion;
    }

    public DefinitionStatus getStatus() {
        return this.status;
    }

    public JsonNode getSpecificationJson() {
        return this.specificationJson;
    }

    public String getContentHash() {
        return this.contentHash;
    }

    public DefinitionProvenance getProvenance() {
        return this.provenance;
    }

    public String getSourceDetail() {
        return this.sourceDetail;
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

    public Instant getPublishedAt() {
        return this.publishedAt;
    }

    public Instant getArchivedAt() {
        return this.archivedAt;
    }

    public void replaceDraft(JsonNode specificationJson, String contentHash) {
        this.specificationJson = specificationJson;
        this.contentHash = contentHash;
    }

    public void changeStatus(DefinitionStatus status) {
        this.status = status;
        if (status == DefinitionStatus.PUBLISHED) {
            this.publishedAt = Instant.now();
            this.archivedAt = null;
        } else if (status == DefinitionStatus.ARCHIVED) {
            this.archivedAt = Instant.now();
        }
    }
}
