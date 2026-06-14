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

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.agentdefs.AgentDefinitionRegistry;
import ch.zhaw.prometheus.controllers.views.AdminAgentTypeView;
import ch.zhaw.prometheus.controllers.views.AgentInfoView;
import ch.zhaw.prometheus.controllers.views.AgentStateInfoView;
import ch.zhaw.prometheus.controllers.views.DemoSessionView;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.controllers.views.PolicyResponseView;
import ch.zhaw.prometheus.controllers.views.ResponseView;
import ch.zhaw.prometheus.controllers.views.StorageEntryView;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.access.AccessCode;
import ch.zhaw.prometheus.model.access.AccessCodeAgent;
import ch.zhaw.prometheus.model.access.AccessCodeAllowedAgentType;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.repositories.AccessCodeAgentRepository;
import ch.zhaw.prometheus.repositories.AccessCodeRepository;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@Service
public class ScopedDemoService {
    private final AccessCodeRepository accessCodes;
    private final AccessCodeAgentRepository accessCodeAgents;
    private final AgentRepository agents;
    private final AgentDefinitionRegistry agentDefinitions;
    private final AgentApplicationService agentService;
    private final PromptMessageAssembler promptMessageAssembler;
    private final LanguageModelGateway languageModelGateway;

    public ScopedDemoService(AccessCodeRepository accessCodes, AccessCodeAgentRepository accessCodeAgents,
            AgentRepository agents, AgentDefinitionRegistry agentDefinitions, AgentApplicationService agentService,
            PromptMessageAssembler promptMessageAssembler, LanguageModelGateway languageModelGateway) {
        this.accessCodes = accessCodes;
        this.accessCodeAgents = accessCodeAgents;
        this.agents = agents;
        this.agentDefinitions = agentDefinitions;
        this.agentService = agentService;
        this.promptMessageAssembler = promptMessageAssembler;
        this.languageModelGateway = languageModelGateway;
    }

    public DemoSessionView openSession(String accessCodeValue) {
        AccessCode accessCode = this.requireEnabledAccessCode(accessCodeValue);
        return new DemoSessionView(accessCode.getCode(), this.listAllowedAgentTypes(accessCode),
                this.listLinkedAgents(accessCode));
    }

    public List<AdminAgentTypeView> listAgentTypes(String accessCodeValue) {
        return this.listAllowedAgentTypes(this.requireEnabledAccessCode(accessCodeValue));
    }

    public List<AgentInfoView> listAgents(String accessCodeValue) {
        return this.listLinkedAgents(this.requireEnabledAccessCode(accessCodeValue));
    }

    @Transactional
    public AgentInfoView createAgent(String accessCodeValue, String agentDefinitionKey) {
        AccessCode accessCode = this.requireEnabledAccessCode(accessCodeValue);
        String key = this.requireAgentDefinitionKey(agentDefinitionKey);
        if (!this.allowedKeys(accessCode).contains(key)) {
            throw new DemoAgentTypeForbiddenException(key);
        }
        AgentDefinition definition = this.agentDefinitions.findByKey(key)
                .orElseThrow(() -> new DemoAgentTypeForbiddenException(key));
        AgentCreationResult created = definition.createInstance(
                new AgentCreationContext(this.promptMessageAssembler, this.languageModelGateway));
        Agent saved = this.agentService.persistCreatedAgent(created);
        this.accessCodeAgents.save(new AccessCodeAgent(accessCode, saved));
        return this.toAgentInfo(saved);
    }

    @Transactional
    public boolean deleteAgent(String accessCodeValue, UUID agentId) {
        AccessCode accessCode = this.requireEnabledAccessCode(accessCodeValue);
        if (agentId == null) {
            return false;
        }
        Optional<AccessCodeAgent> link = this.accessCodeAgents.findByAccessCode_IdAndAgent_Id(accessCode.getId(),
                agentId);
        if (link.isEmpty()) {
            return false;
        }
        this.accessCodeAgents.delete(link.get());
        this.accessCodeAgents.flush();
        if (this.accessCodeAgents.countByAgent_Id(agentId) == 0) {
            this.agents.deleteById(agentId);
        }
        return true;
    }

    public Optional<AgentInfoView> getAgentInfo(String accessCodeValue, UUID agentId) {
        if (!this.hasVisibleAgent(accessCodeValue, agentId)) {
            return Optional.empty();
        }
        return this.agentService.getAgentInfo(agentId);
    }

    public Optional<List<Event>> getAgentEventHistory(String accessCodeValue, UUID agentId) {
        if (!this.hasVisibleAgent(accessCodeValue, agentId)) {
            return Optional.empty();
        }
        return this.agentService.getAgentEventHistory(agentId);
    }

    public Optional<AgentStateInfoView> getAgentState(String accessCodeValue, UUID agentId) {
        if (!this.hasVisibleAgent(accessCodeValue, agentId)) {
            return Optional.empty();
        }
        return this.agentService.getAgentState(agentId);
    }

    public Optional<List<String>> getAgentStates(String accessCodeValue, UUID agentId) {
        if (!this.hasVisibleAgent(accessCodeValue, agentId)) {
            return Optional.empty();
        }
        return this.agentService.getAgentStates(agentId);
    }

    public Optional<List<StorageEntryView>> getAgentStorage(String accessCodeValue, UUID agentId) {
        if (!this.hasVisibleAgent(accessCodeValue, agentId)) {
            return Optional.empty();
        }
        return this.agentService.getAgentStorage(agentId);
    }

    public Optional<ResponseView> start(String accessCodeValue, UUID agentId) {
        if (!this.hasVisibleAgent(accessCodeValue, agentId)) {
            return Optional.empty();
        }
        return this.agentService.start(agentId);
    }

    public Optional<ResponseView> reset(String accessCodeValue, UUID agentId) {
        if (!this.hasVisibleAgent(accessCodeValue, agentId)) {
            return Optional.empty();
        }
        return this.agentService.reset(agentId);
    }

    public Optional<ResponseView> acknowledge(String accessCodeValue, UUID agentId, EventRequest request,
            OutputProfile outputProfile) {
        if (!this.hasVisibleAgent(accessCodeValue, agentId)) {
            return Optional.empty();
        }
        return this.agentService.acknowledge(agentId, request, outputProfile);
    }

    public BehaviourGenerationOutcome generate(String accessCodeValue, UUID agentId, List<String> omitModalities,
            OutputProfile outputProfile) {
        if (!this.hasVisibleAgent(accessCodeValue, agentId)) {
            return BehaviourGenerationOutcome.AGENT_NOT_FOUND;
        }
        return this.agentService.generate(agentId, omitModalities, outputProfile);
    }

    public Optional<SseEmitter> subscribeBehaviour(String accessCodeValue, UUID agentId, String lastEventId) {
        if (!this.hasVisibleAgent(accessCodeValue, agentId)) {
            return Optional.empty();
        }
        return this.agentService.subscribeBehaviour(agentId, lastEventId);
    }

    public Optional<SseEmitter> subscribeMonitor(String accessCodeValue, UUID agentId) {
        if (!this.hasVisibleAgent(accessCodeValue, agentId)) {
            return Optional.empty();
        }
        return this.agentService.subscribeMonitor(agentId);
    }

    public Optional<PolicyResponseView> prompt(String accessCodeValue, UUID agentId, OutputProfile outputProfile) {
        if (!this.hasVisibleAgent(accessCodeValue, agentId)) {
            return Optional.empty();
        }
        return this.agentService.prompt(agentId, outputProfile);
    }

    private boolean hasVisibleAgent(String accessCodeValue, UUID agentId) {
        AccessCode accessCode = this.requireEnabledAccessCode(accessCodeValue);
        return agentId != null && this.accessCodeAgents.existsByAccessCode_IdAndAgent_Id(accessCode.getId(), agentId);
    }

    private AccessCode requireEnabledAccessCode(String accessCodeValue) {
        this.requireAccessCodeFormat(accessCodeValue);
        return this.accessCodes.findByCode(accessCodeValue)
                .filter(AccessCode::isEnabled)
                .orElseThrow(DemoAccessDeniedException::new);
    }

    private void requireAccessCodeFormat(String accessCodeValue) {
        if (accessCodeValue == null || !AccessCodeAdminService.ACCESS_CODE_PATTERN.matcher(accessCodeValue).matches()) {
            throw new DemoAccessDeniedException();
        }
    }

    private String requireAgentDefinitionKey(String agentDefinitionKey) {
        if (agentDefinitionKey == null || agentDefinitionKey.isBlank()) {
            throw new IllegalArgumentException("agentDefinitionKey must be provided");
        }
        return agentDefinitionKey;
    }

    private List<AdminAgentTypeView> listAllowedAgentTypes(AccessCode accessCode) {
        return this.allowedKeys(accessCode).stream()
                .sorted()
                .map(this.agentDefinitions::findByKey)
                .flatMap(Optional::stream)
                .map(definition -> new AdminAgentTypeView(definition.key(), definition.displayName(),
                        definition.description()))
                .toList();
    }

    private List<AgentInfoView> listLinkedAgents(AccessCode accessCode) {
        return this.accessCodeAgents.findByAccessCodeId(accessCode.getId()).stream()
                .map(AccessCodeAgent::getAgent)
                .map(this::toAgentInfo)
                .sorted(Comparator.comparing(AgentInfoView::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(view -> view.getID().toString()))
                .toList();
    }

    private Set<String> allowedKeys(AccessCode accessCode) {
        return accessCode.getAllowedAgentTypes().stream()
                .map(AccessCodeAllowedAgentType::getAgentTypeKey)
                .collect(Collectors.toSet());
    }

    private AgentInfoView toAgentInfo(Agent agent) {
        return new AgentInfoView(agent.getId(), agent.getName(), agent.getDescription(), agent.isActive(),
                agent.getInteractionProfile());
    }
}
