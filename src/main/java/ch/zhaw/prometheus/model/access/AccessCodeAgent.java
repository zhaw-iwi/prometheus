package ch.zhaw.prometheus.model.access;

import java.util.UUID;

import ch.zhaw.prometheus.model.Agent;
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
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "access_code_id", nullable = false)
    private AccessCode accessCode;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    protected AccessCodeAgent() {
    }

    public AccessCodeAgent(AccessCode accessCode, Agent agent) {
        this.accessCode = accessCode;
        this.agent = agent;
    }

    public UUID getId() {
        return this.id;
    }

    public AccessCode getAccessCode() {
        return this.accessCode;
    }

    public Agent getAgent() {
        return this.agent;
    }
}
