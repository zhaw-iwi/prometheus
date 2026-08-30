package ch.zhaw.prometheus.model.access;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "access_code_allowed_agent_type", uniqueConstraints = {
        @UniqueConstraint(name = "uk_access_code_allowed_agent_type", columnNames = { "access_code_id",
                "agent_type_key" })
})
public class AccessCodeAllowedAgentType {
    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "binary(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "access_code_id", nullable = false)
    private AccessCode accessCode;

    @Column(name = "agent_type_key", columnDefinition = "varchar(128) collate utf8mb4_bin not null")
    private String agentTypeKey;

    protected AccessCodeAllowedAgentType() {
    }

    AccessCodeAllowedAgentType(AccessCode accessCode, String agentTypeKey) {
        this.accessCode = accessCode;
        this.agentTypeKey = agentTypeKey;
    }

    public UUID getId() {
        return this.id;
    }

    public AccessCode getAccessCode() {
        return this.accessCode;
    }

    public String getAgentTypeKey() {
        return this.agentTypeKey;
    }
}
