package ch.zhaw.prometheus.application;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final TransactionTemplate creationTransaction;
    private AgentPersistenceContext persistenceContext;
    @org.springframework.beans.factory.annotation.Autowired
    void persistenceContext(AgentPersistenceContext context) { this.persistenceContext = context; }

    public record ObservationResult(int status, ResponseView response) {}
    private static final Set<String> SENSOR_TYPES = Set.of(Event.TYPE_FACE_EMOTION, Event.TYPE_HUMAN_PRESENCE,
            Event.TYPE_SOCIAL_GROUPING, Event.TYPE_SOCIAL_CONTEXT, Event.TYPE_HAND_SIGN,
            Event.TYPE_WEATHER_CURRENT, Event.TYPE_WEATHER_FORECAST);

    public List<ObservationResult> acknowledgeObservations(String code, UUID id, List<EventRequest> requests) {
        if (requests == null || requests.isEmpty() || requests.size() > 4 || requests.stream().anyMatch(request ->
                request == null || request.getType() == null || !SENSOR_TYPES.contains(request.getType()) || !Event.KIND_OBSERVATION.equals(request.getKind())
                || request.getActor() == null || request.getActor().isBlank() || request.getPayload() == null
                || request.getPayload().isBlank())) throw new IllegalArgumentException("Invalid sensor observations");
        // One camera sample reuses its loaded graph. Each event keeps its own ordinary commit,
        // access revalidation, transitions, response and publication; no provider wait owns a transaction.
        return agentService.serialized(id, () -> persistenceContext.call(() -> {
            var results = new java.util.ArrayList<ObservationResult>();
            for (EventRequest request : requests) {
                try {
                    var response = acknowledge(code, id, request, OutputProfile.FULL_PLAN);
                    results.add(new ObservationResult(response.isPresent() ? 200 : 404, response.orElse(null)));
                } catch (DemoAccessDeniedException denied) {
                    persistenceContext.clear(); results.add(new ObservationResult(401, null));
                } catch (RuntimeException failure) {
                    // Failed turns must not leak their in-memory changes into the next event.
                    persistenceContext.clear(); results.add(new ObservationResult(500, null));
                    org.slf4j.LoggerFactory.getLogger(ScopedDemoService.class).warn("Sensor observation failed; agentId={}", id, failure);
                }
            }
            return List.copyOf(results);
        }));
    }

    public ScopedDemoService(AccessCodeRepository accessCodes, AccessCodeAgentRepository accessCodeAgents,
            AgentRepository agents, AgentDefinitionRegistry agentDefinitions, AgentApplicationService agentService,
            PromptMessageAssembler promptMessageAssembler, LanguageModelGateway languageModelGateway,
            PlatformTransactionManager transactions) {
        this.accessCodes = accessCodes;
        this.accessCodeAgents = accessCodeAgents;
        this.agents = agents;
        this.agentDefinitions = agentDefinitions;
        this.agentService = agentService;
        this.promptMessageAssembler = promptMessageAssembler;
        this.languageModelGateway = languageModelGateway;
        this.creationTransaction = new TransactionTemplate(transactions);
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
        applyDefinitionLanguage(created.agent(), definition);
        // Provider work above owns no creation transaction. Recheck scope after that
        // potentially slow work, then commit the agent and its visibility together.
        return this.creationTransaction.execute(status -> {
            AccessCode current = this.requireEnabledAccessCode(accessCodeValue);
            if (!current.getId().equals(accessCode.getId())) {
                throw new DemoAccessDeniedException();
            }
            if (!this.allowedKeys(current).contains(key)) {
                throw new DemoAgentTypeForbiddenException(key);
            }
            Agent saved = this.agentService.persistCreatedAgent(created);
            this.accessCodeAgents.saveAndFlush(new AccessCodeAgent(current, saved));
            return this.toAgentInfo(saved);
        });
    }

    @Transactional
    public boolean deleteAgent(String accessCodeValue, UUID agentId) {
        if (agentId == null) {
            this.requireEnabledAccessCode(accessCodeValue);
            return false;
        }
        return this.agentService.serialized(agentId, () -> {
            AccessCode accessCode = this.requireEnabledAccessCode(accessCodeValue);
            Optional<AccessCodeAgent> link = this.accessCodeAgents.findByAccessCode_IdAndAgent_Id(accessCode.getId(),
                    agentId);
            if (link.isEmpty()) {
                return false;
            }
            this.accessCodeAgents.delete(link.get());
            this.accessCodeAgents.flush();
            if (this.accessCodeAgents.countByAgent_Id(agentId) == 0) {
                this.agentService.discardSpeculation(agentId, "delete");
                this.agents.deleteById(agentId);
            }
            return true;
        });
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

    public Optional<List<Event>> getAgentCurrentStateEventHistory(String accessCodeValue, UUID agentId) {
        if (!this.hasVisibleAgent(accessCodeValue, agentId)) {
            return Optional.empty();
        }
        return this.agentService.getAgentCurrentStateEventHistory(agentId);
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

    public Optional<SseEmitter> subscribeConversationBehaviour(String code, UUID agentId, String lastEventId) {
        if (!hasVisibleAgent(code, agentId)) return Optional.empty();
        return agentService.subscribeConversationBehaviour(agentId, lastEventId);
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

    public Optional<String> getAgentLanguageCode(String accessCodeValue, UUID agentId) {
        if (!this.hasVisibleAgent(accessCodeValue, agentId)) {
            return Optional.empty();
        }
        return this.agentService.getAgentLanguageCode(agentId);
    }

    Optional<UUID> speechScope(String accessCodeValue, UUID agentId) {
        this.requireAccessCodeFormat(accessCodeValue);
        var scope = this.accessCodes.findScope(accessCodeValue, agentId)
                .filter(ch.zhaw.prometheus.repositories.AccessCodeRepository.Scope::getEnabled)
                .orElseThrow(DemoAccessDeniedException::new);
        return scope.getLinked() ? Optional.of(scope.getId()) : Optional.empty();
    }

    public boolean hasVisibleAgent(String accessCodeValue, UUID agentId) {
        return this.speechScope(accessCodeValue, agentId).isPresent();
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
                        definition.description(), definition.packagePath()))
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
                agent.getInteractionProfile(), agent.getLanguageCode());
    }

    private static void applyDefinitionLanguage(Agent agent, AgentDefinition definition) {
        if (agent == null || definition == null || isPresent(agent.getLanguageCode())
                || !isPresent(definition.languageCode())) {
            return;
        }
        agent.setLanguageCode(definition.languageCode());
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
