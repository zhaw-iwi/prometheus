package ch.zhaw.prometheus.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.zhaw.prometheus.controllers.views.AccessCodeView;
import ch.zhaw.prometheus.controllers.views.AccessCodePresetEntryView;
import ch.zhaw.prometheus.controllers.views.AccessCodePresetView;
import ch.zhaw.prometheus.controllers.views.AdminAgentTypeView;
import ch.zhaw.prometheus.controllers.views.AgentInfoView;
import ch.zhaw.prometheus.definition.application.ActiveAgentDefinitionCatalog;
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
    private final ActiveAgentDefinitionCatalog agentDefinitions;
    private final AccessCodePresetCatalog accessCodePresets;
    private final AgentApplicationService agents;

    public AccessCodeAdminService(AccessCodeRepository accessCodes, AccessCodeAgentRepository accessCodeAgents,
            ActiveAgentDefinitionCatalog agentDefinitions, AccessCodePresetCatalog accessCodePresets,
            AgentApplicationService agents) {
        this.accessCodes = accessCodes;
        this.accessCodeAgents = accessCodeAgents;
        this.agentDefinitions = agentDefinitions;
        this.accessCodePresets = accessCodePresets;
        this.agents = agents;
    }

    public List<AdminAgentTypeView> listAgentTypes() {
        return this.agentDefinitions.list().stream()
                .map(active -> new AdminAgentTypeView(active.compiled().key(),
                        active.compiled().metadata().displayName(), active.compiled().metadata().description(),
                        active.packagePath()))
                .toList();
    }

    public List<AccessCodePresetView> listAccessCodePresets() {
        return this.accessCodePresets.list().stream()
                .map(this::toPresetView)
                .toList();
    }

    @Transactional
    public AccessCodeView createAccessCode(String code, Boolean enabled) {
        this.validateCode(code);
        if (this.accessCodes.findByCode(code).isPresent()) {
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

    @Transactional
    public Optional<List<AccessCodeView>> applyAccessCodePreset(String presetKey,
            List<AccessCodePresetEntrySpec> requestedEntries) {
        Optional<AccessCodePresetSpec> preset = this.findPreset(presetKey);
        if (preset.isEmpty()) {
            return Optional.empty();
        }
        Map<String, List<String>> resolvedAssignments = this.resolvePresetAssignments(preset.get(), requestedEntries);
        for (String code : resolvedAssignments.keySet()) {
            if (this.accessCodes.findByCode(code).isPresent()) {
                throw new DuplicateAccessCodeException(code);
            }
        }
        List<AccessCodeView> created = new ArrayList<>();
        for (Map.Entry<String, List<String>> assignment : resolvedAssignments.entrySet()) {
            AccessCode accessCode = new AccessCode(assignment.getKey(), true);
            accessCode.replaceAllowedAgentTypes(assignment.getValue());
            created.add(this.toView(this.accessCodes.save(accessCode)));
        }
        this.accessCodes.flush();
        return Optional.of(created);
    }

    public Optional<List<AgentInfoView>> listAgents(UUID id) {
        if (id == null || this.accessCodes.findById(id).isEmpty()) {
            return Optional.empty();
        }
        List<AgentInfoView> result = new ArrayList<>();
        for (AccessCodeAgent link : this.accessCodeAgents.findByAccessCodeId(id)) {
            this.agents.getAgentInfo(link.getAgentId()).ifPresent(result::add);
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
            if (this.agentDefinitions.find(key).isEmpty()) {
                throw new IllegalArgumentException("unknown agent type key: " + key);
            }
            if (!resolved.add(key)) {
                throw new IllegalArgumentException("duplicate agent type key: " + key);
            }
        }
        return resolved;
    }

    private Optional<AccessCodePresetSpec> findPreset(String presetKey) {
        if (presetKey == null || presetKey.isBlank()) {
            return Optional.empty();
        }
        return this.accessCodePresets.list().stream()
                .filter(preset -> presetKey.equals(preset.key()))
                .findFirst();
    }

    private AccessCodePresetView toPresetView(AccessCodePresetSpec preset) {
        if (preset.key() == null || preset.key().isBlank()) {
            throw new IllegalArgumentException("access-code preset key must not be blank");
        }
        List<AccessCodePresetEntryView> entries = preset.entries().stream()
                .map(entry -> {
                    this.validateCode(entry.code());
                    List<String> keys = this.validatePresetAgentTypeKeys(entry.agentTypeKeys());
                    return new AccessCodePresetEntryView(entry.code(), keys);
                })
                .toList();
        return new AccessCodePresetView(preset.key(), preset.displayName(), entries);
    }

    private List<String> validatePresetAgentTypeKeys(List<String> agentTypeKeys) {
        return List.copyOf(this.validateAgentTypeKeys(agentTypeKeys));
    }

    private Map<String, List<String>> resolvePresetAssignments(AccessCodePresetSpec preset,
            List<AccessCodePresetEntrySpec> requestedEntries) {
        if (requestedEntries == null) {
            throw new IllegalArgumentException("preset entries must be provided");
        }
        Map<String, Set<String>> allowedByCode = new LinkedHashMap<>();
        for (AccessCodePresetEntrySpec entry : preset.entries()) {
            this.validateCode(entry.code());
            if (allowedByCode.put(entry.code(), this.validateAgentTypeKeys(entry.agentTypeKeys())) != null) {
                throw new IllegalArgumentException("duplicate preset access code: " + entry.code());
            }
        }
        Map<String, List<String>> resolved = new LinkedHashMap<>();
        for (AccessCodePresetEntrySpec requested : requestedEntries) {
            if (requested == null) {
                throw new IllegalArgumentException("preset entry must not be null");
            }
            this.validateCode(requested.code());
            Set<String> allowed = allowedByCode.get(requested.code());
            if (allowed == null) {
                throw new IllegalArgumentException("access code is not part of preset: " + requested.code());
            }
            if (resolved.containsKey(requested.code())) {
                throw new IllegalArgumentException("duplicate access code in preset request: " + requested.code());
            }
            Set<String> selectedKeys = this.validateAgentTypeKeys(requested.agentTypeKeys());
            for (String key : selectedKeys) {
                if (!allowed.contains(key)) {
                    throw new IllegalArgumentException(
                            "agent type key is not part of preset entry " + requested.code() + ": " + key);
                }
            }
            resolved.put(requested.code(), List.copyOf(selectedKeys));
        }
        if (!resolved.keySet().equals(allowedByCode.keySet())) {
            throw new IllegalArgumentException("preset request must include every preset access code");
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
