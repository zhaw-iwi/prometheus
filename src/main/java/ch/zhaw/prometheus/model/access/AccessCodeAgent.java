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
@Table(name = "access_code_agent", uniqueConstraints = {
        @UniqueConstraint(name = "uk_access_code_agent", columnNames = { "access_code_id", "agent_id" })
})
public class AccessCodeAgent {
    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "binary(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "access_code_id", nullable = false)
    private AccessCode accessCode;

    @Column(name = "agent_id", nullable = false, columnDefinition = "binary(16)")
    private UUID agentId;

    protected AccessCodeAgent() {
    }

    public AccessCodeAgent(AccessCode accessCode, UUID agentId) {
        this.accessCode = accessCode;
        this.agentId = agentId;
    }

    public UUID getId() {
        return this.id;
    }

    public AccessCode getAccessCode() {
        return this.accessCode;
    }

    public UUID getAgentId() {
        return this.agentId;
    }
}
