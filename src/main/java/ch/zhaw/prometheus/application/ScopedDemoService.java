package ch.zhaw.prometheus.application;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ch.zhaw.prometheus.controllers.views.AdminAgentTypeView;
import ch.zhaw.prometheus.controllers.views.AgentInfoView;
import ch.zhaw.prometheus.controllers.views.AgentStateInfoView;
import ch.zhaw.prometheus.controllers.views.DemoSessionView;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.controllers.views.PolicyResponseView;
import ch.zhaw.prometheus.controllers.views.ResponseView;
import ch.zhaw.prometheus.controllers.views.StorageEntryView;
import ch.zhaw.prometheus.definition.application.ActiveAgentDefinitionCatalog;
import ch.zhaw.prometheus.model.access.AccessCode;
import ch.zhaw.prometheus.model.access.AccessCodeAgent;
import ch.zhaw.prometheus.model.access.AccessCodeAllowedAgentType;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.repositories.AccessCodeAgentRepository;
import ch.zhaw.prometheus.repositories.AccessCodeRepository;

@Service
public class ScopedDemoService {
    private final AccessCodeRepository accessCodes;
    private final AccessCodeAgentRepository accessCodeAgents;
    private final ActiveAgentDefinitionCatalog definitions;
    private final AgentApplicationService agents;

    public ScopedDemoService(AccessCodeRepository accessCodes, AccessCodeAgentRepository accessCodeAgents,
            ActiveAgentDefinitionCatalog definitions, AgentApplicationService agents) {
        this.accessCodes = accessCodes;
        this.accessCodeAgents = accessCodeAgents;
        this.definitions = definitions;
        this.agents = agents;
    }

    public DemoSessionView openSession(String accessCodeValue) {
        AccessCode accessCode = requireEnabledAccessCode(accessCodeValue);
        return new DemoSessionView(accessCode.getCode(), listAllowedAgentTypes(accessCode), listLinkedAgents(accessCode));
    }

    public List<AdminAgentTypeView> listAgentTypes(String accessCodeValue) {
        return listAllowedAgentTypes(requireEnabledAccessCode(accessCodeValue));
    }

    public List<AgentInfoView> listAgents(String accessCodeValue) {
        return listLinkedAgents(requireEnabledAccessCode(accessCodeValue));
    }

    @Transactional
    public AgentInfoView createAgent(String accessCodeValue, String definitionKey) {
        AccessCode accessCode = requireEnabledAccessCode(accessCodeValue);
        String key = requireDefinitionKey(definitionKey);
        if (!allowedKeys(accessCode).contains(key) || this.definitions.find(key).isEmpty()) {
            throw new DemoAgentTypeForbiddenException(key);
        }
        CreatedDeclarativeAgent created = this.agents.create(key);
        this.accessCodeAgents.save(new AccessCodeAgent(accessCode, created.agent().getId()));
        return this.agents.getAgentInfo(created.agent().getId()).orElseThrow();
    }

    @Transactional
    public boolean deleteAgent(String accessCodeValue, UUID agentId) {
        AccessCode accessCode = requireEnabledAccessCode(accessCodeValue);
        if (agentId == null) {
            return false;
        }
        Optional<AccessCodeAgent> link = this.accessCodeAgents.findByAccessCode_IdAndAgentId(accessCode.getId(),
                agentId);
        if (link.isEmpty()) {
            return false;
        }
        this.accessCodeAgents.delete(link.get());
        this.accessCodeAgents.flush();
        if (this.accessCodeAgents.countByAgentId(agentId) == 0) {
            this.agents.delete(agentId);
        }
        return true;
    }

    public Optional<AgentInfoView> getAgentInfo(String code, UUID id) {
        return visible(code, id) ? this.agents.getAgentInfo(id) : Optional.empty();
    }
    public Optional<List<Event>> getAgentEventHistory(String code, UUID id) {
        return visible(code, id) ? this.agents.getAgentEventHistory(id) : Optional.empty();
    }
    public Optional<List<Event>> getAgentCurrentStateEventHistory(String code, UUID id) {
        return visible(code, id) ? this.agents.getAgentCurrentStateEventHistory(id) : Optional.empty();
    }
    public Optional<AgentStateInfoView> getAgentState(String code, UUID id) {
        return visible(code, id) ? this.agents.getAgentState(id) : Optional.empty();
    }
    public Optional<List<String>> getAgentStates(String code, UUID id) {
        return visible(code, id) ? this.agents.getAgentStates(id) : Optional.empty();
    }
    public Optional<List<StorageEntryView>> getAgentStorage(String code, UUID id) {
        return visible(code, id) ? this.agents.getAgentStorage(id) : Optional.empty();
    }
    public Optional<ResponseView> start(String code, UUID id) {
        return visible(code, id) ? this.agents.start(id) : Optional.empty();
    }
    public Optional<ResponseView> reset(String code, UUID id) {
        return visible(code, id) ? this.agents.reset(id) : Optional.empty();
    }
    public Optional<ResponseView> acknowledge(String code, UUID id, EventRequest request, OutputProfile profile) {
        return visible(code, id) ? this.agents.acknowledge(id, request, profile) : Optional.empty();
    }
    public BehaviourGenerationOutcome generate(String code, UUID id, List<String> omitted, OutputProfile profile) {
        return visible(code, id) ? this.agents.generate(id, omitted, profile)
                : BehaviourGenerationOutcome.AGENT_NOT_FOUND;
    }
    public Optional<SseEmitter> subscribeBehaviour(String code, UUID id, String lastEventId) {
        return visible(code, id) ? this.agents.subscribeBehaviour(id, lastEventId) : Optional.empty();
    }
    public Optional<SseEmitter> subscribeMonitor(String code, UUID id) {
        return visible(code, id) ? this.agents.subscribeMonitor(id) : Optional.empty();
    }
    public Optional<PolicyResponseView> prompt(String code, UUID id, OutputProfile profile) {
        return visible(code, id) ? this.agents.prompt(id, profile) : Optional.empty();
    }
    public Optional<String> getAgentLanguageCode(String code, UUID id) {
        return visible(code, id) ? this.agents.getAgentLanguageCode(id) : Optional.empty();
    }

    private boolean visible(String code, UUID id) {
        AccessCode accessCode = requireEnabledAccessCode(code);
        return id != null && this.accessCodeAgents.existsByAccessCode_IdAndAgentId(accessCode.getId(), id);
    }

    private AccessCode requireEnabledAccessCode(String value) {
        if (value == null || !AccessCodeAdminService.ACCESS_CODE_PATTERN.matcher(value).matches()) {
            throw new DemoAccessDeniedException();
        }
        return this.accessCodes.findByCode(value).filter(AccessCode::isEnabled)
                .orElseThrow(DemoAccessDeniedException::new);
    }

    private static String requireDefinitionKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("agentDefinitionKey must be provided");
        }
        return key;
    }

    private List<AdminAgentTypeView> listAllowedAgentTypes(AccessCode accessCode) {
        return allowedKeys(accessCode).stream().sorted().map(this.definitions::find).flatMap(Optional::stream)
                .map(active -> new AdminAgentTypeView(active.compiled().key(),
                        active.compiled().metadata().displayName(), active.compiled().metadata().description(),
                        active.packagePath())).toList();
    }

    private List<AgentInfoView> listLinkedAgents(AccessCode accessCode) {
        return this.accessCodeAgents.findByAccessCodeId(accessCode.getId()).stream()
                .map(AccessCodeAgent::getAgentId).map(this.agents::getAgentInfo).flatMap(Optional::stream)
                .sorted(Comparator.comparing(AgentInfoView::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(view -> view.getID().toString()))
                .toList();
    }

    private static Set<String> allowedKeys(AccessCode accessCode) {
        return accessCode.getAllowedAgentTypes().stream().map(AccessCodeAllowedAgentType::getAgentTypeKey)
                .collect(Collectors.toSet());
    }
}
