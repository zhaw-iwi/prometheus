package ch.zhaw.prometheus.model.access;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "access_code", uniqueConstraints = {
        @UniqueConstraint(name = "uk_access_code_code", columnNames = "code")
})
public class AccessCode {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "code", columnDefinition = "varchar(5) collate utf8mb4_bin not null")
    private String code;

    @Column(nullable = false)
    private boolean enabled;

    @OneToMany(mappedBy = "accessCode", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private List<AccessCodeAllowedAgentType> allowedAgentTypes = new ArrayList<>();

    protected AccessCode() {
    }

    public AccessCode(String code, boolean enabled) {
        this.code = code;
        this.enabled = enabled;
    }

    public UUID getId() {
        return this.id;
    }

    public String getCode() {
        return this.code;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<AccessCodeAllowedAgentType> getAllowedAgentTypes() {
        if (this.allowedAgentTypes == null) {
            this.allowedAgentTypes = new ArrayList<>();
        }
        return this.allowedAgentTypes;
    }

    public void replaceAllowedAgentTypes(Collection<String> agentTypeKeys) {
        this.getAllowedAgentTypes().clear();
        if (agentTypeKeys == null) {
            return;
        }
        for (String key : agentTypeKeys) {
            this.getAllowedAgentTypes().add(new AccessCodeAllowedAgentType(this, key));
        }
    }
}
