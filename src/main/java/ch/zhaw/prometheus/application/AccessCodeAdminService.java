package ch.zhaw.prometheus.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.agentdefs.AgentDefinitionRegistry;
import ch.zhaw.prometheus.controllers.views.AccessCodeView;
import ch.zhaw.prometheus.controllers.views.AdminAgentTypeView;
import ch.zhaw.prometheus.controllers.views.AgentInfoView;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.access.AccessCode;
import ch.zhaw.prometheus.model.access.AccessCodeAgent;
import ch.zhaw.prometheus.model.access.AccessCodeAllowedAgentType;
import ch.zhaw.prometheus.repositories.AccessCodeAgentRepository;
import ch.zhaw.prometheus.repositories.AccessCodeRepository;

@Service
public class AccessCodeAdminService {
    public static final Pattern ACCESS_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9]{5}$");

    private final AccessCodeRepository accessCodes;
    private final AccessCodeAgentRepository accessCodeAgents;
    private final AgentDefinitionRegistry agentDefinitions;

    public AccessCodeAdminService(AccessCodeRepository accessCodes, AccessCodeAgentRepository accessCodeAgents,
            AgentDefinitionRegistry agentDefinitions) {
        this.accessCodes = accessCodes;
        this.accessCodeAgents = accessCodeAgents;
        this.agentDefinitions = agentDefinitions;
    }

    public List<AdminAgentTypeView> listAgentTypes() {
        return this.agentDefinitions.list().stream()
                .map(definition -> new AdminAgentTypeView(definition.key(), definition.displayName(),
                        definition.description(), definition.packagePath()))
                .toList();
    }

    @Transactional
    public AccessCodeView createAccessCode(String code, Boolean enabled) {
        this.validateCode(code);
        if (this.accessCodes.findAll().stream().anyMatch(existing -> code.equals(existing.getCode()))) {
            throw new DuplicateAccessCodeException(code);
        }
        AccessCode accessCode = new AccessCode(code, enabled == null || enabled.booleanValue());
        return this.toView(this.accessCodes.saveAndFlush(accessCode));
    }

    public List<AccessCodeView> listAccessCodes() {
        return this.accessCodes.findAll().stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public Optional<AccessCodeView> updateAccessCodeEnabled(UUID id, Boolean enabled) {
        if (id == null) {
            return Optional.empty();
        }
        if (enabled == null) {
            throw new IllegalArgumentException("enabled must be provided");
        }
        Optional<AccessCode> accessCode = this.accessCodes.findById(id);
        if (accessCode.isEmpty()) {
            return Optional.empty();
        }
        accessCode.get().setEnabled(enabled.booleanValue());
        return Optional.of(this.toView(this.accessCodes.save(accessCode.get())));
    }

    @Transactional
    public Optional<AccessCodeView> replaceAllowedAgentTypes(UUID id, List<String> agentTypeKeys) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<AccessCode> accessCode = this.accessCodes.findById(id);
        if (accessCode.isEmpty()) {
            return Optional.empty();
        }
        Set<String> resolvedKeys = this.validateAgentTypeKeys(agentTypeKeys);
        accessCode.get().replaceAllowedAgentTypes(resolvedKeys);
        return Optional.of(this.toView(this.accessCodes.save(accessCode.get())));
    }

    public Optional<List<AgentInfoView>> listAgents(UUID id) {
        if (id == null || this.accessCodes.findById(id).isEmpty()) {
            return Optional.empty();
        }
        List<AgentInfoView> result = new ArrayList<>();
        for (AccessCodeAgent link : this.accessCodeAgents.findByAccessCodeId(id)) {
            Agent agent = link.getAgent();
            result.add(new AgentInfoView(agent.getId(), agent.getName(), agent.getDescription(), agent.isActive(),
                    agent.getInteractionProfile(), agent.getLanguageCode()));
        }
        return Optional.of(result);
    }

    private void validateCode(String code) {
        if (code == null || !ACCESS_CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("access code must be exactly five ASCII letters or digits");
        }
    }

    private Set<String> validateAgentTypeKeys(List<String> agentTypeKeys) {
        if (agentTypeKeys == null) {
            return Set.of();
        }
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        for (String key : agentTypeKeys) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("agent type key must not be blank");
            }
            if (this.agentDefinitions.findByKey(key).isEmpty()) {
                throw new IllegalArgumentException("unknown agent type key: " + key);
            }
            if (!resolved.add(key)) {
                throw new IllegalArgumentException("duplicate agent type key: " + key);
            }
        }
        return resolved;
    }

    private AccessCodeView toView(AccessCode accessCode) {
        List<String> allowedTypeKeys = accessCode.getAllowedAgentTypes().stream()
                .map(AccessCodeAllowedAgentType::getAgentTypeKey)
                .sorted()
                .toList();
        return new AccessCodeView(accessCode.getId(), accessCode.getCode(), accessCode.isEnabled(),
                allowedTypeKeys);
    }
}
