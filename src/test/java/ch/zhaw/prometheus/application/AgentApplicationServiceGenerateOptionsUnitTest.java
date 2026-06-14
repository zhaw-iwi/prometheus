package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import ch.zhaw.prometheus.logging.AgentBehaviourBroadcaster;
import ch.zhaw.prometheus.logging.AgentMonitorBroadcaster;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class AgentApplicationServiceGenerateOptionsUnitTest {

    @Test
    void generateCanOmitSpeechBeforePersistenceAndPublish() {
        UUID agentId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        BehaviourPlan plan = new BehaviourPlan();
        plan.setSpeech("hello");
        plan.setNonVerbal(com.google.gson.JsonParser.parseString("{\"gesture\":\"EXPLAIN\"}"));
        Event response = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, plan.toJson());

        Agent agent = mock(Agent.class);
        EventHistory history = mock(EventHistory.class);
        AgentRepository repository = mock(AgentRepository.class);
        AgentMonitorBroadcaster monitorBroadcaster = mock(AgentMonitorBroadcaster.class);
        AgentBehaviourBroadcaster behaviourBroadcaster = mock(AgentBehaviourBroadcaster.class);
        LanguageModelGateway languageModelGateway = mock(LanguageModelGateway.class);
        PromptMessageAssembler assembler = new PromptMessageAssembler();

        when(repository.findById(agentId)).thenReturn(Optional.of(agent));
        when(agent.generate(any())).thenReturn(response);
        when(repository.save(agent)).thenReturn(agent);
        when(agent.getEventHistory()).thenReturn(history);
        when(history.toList()).thenReturn(List.of(response));
        when(agent.getId()).thenReturn(agentId);

        AgentApplicationService service = new AgentApplicationService(repository, monitorBroadcaster, behaviourBroadcaster,
                assembler, languageModelGateway);

        BehaviourGenerationOutcome outcome = service.generate(agentId, List.of("speech"));

        assertSame(BehaviourGenerationOutcome.GENERATED, outcome);
        BehaviourPlan updated = BehaviourPlan.fromJson(response.getPayload());
        assertNotNull(updated);
        assertNull(updated.getSpeech());
        assertNotNull(updated.getNonVerbal());
        assertEquals("EXPLAIN", updated.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
    }

    @Test
    void generatePublishesAssistantBehaviourApplicationEvent() {
        UUID agentId = UUID.fromString("12121212-1212-1212-1212-121212121212");
        Event response = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                "{\"speech\":\"hello\"}");
        Agent agent = mock(Agent.class);
        EventHistory history = mock(EventHistory.class);
        AgentRepository repository = mock(AgentRepository.class);
        AgentMonitorBroadcaster monitorBroadcaster = mock(AgentMonitorBroadcaster.class);
        AgentBehaviourBroadcaster behaviourBroadcaster = mock(AgentBehaviourBroadcaster.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        LanguageModelGateway languageModelGateway = mock(LanguageModelGateway.class);
        PromptMessageAssembler assembler = new PromptMessageAssembler();

        when(repository.findById(agentId)).thenReturn(Optional.of(agent));
        when(agent.generate(any())).thenReturn(response);
        when(repository.save(agent)).thenReturn(agent);
        when(agent.getEventHistory()).thenReturn(history);
        when(history.toList()).thenReturn(List.of(response));
        when(agent.getId()).thenReturn(agentId);

        AgentApplicationService service = new AgentApplicationService(repository, monitorBroadcaster, behaviourBroadcaster,
                assembler, languageModelGateway);
        service.setApplicationEventPublisher(eventPublisher);

        assertSame(BehaviourGenerationOutcome.GENERATED, service.generate(agentId, null, OutputProfile.FULL_PLAN));

        ArgumentCaptor<AssistantBehaviourPublishedEvent> published = ArgumentCaptor
                .forClass(AssistantBehaviourPublishedEvent.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertEquals(agentId, published.getValue().agentId());
        assertSame(response, published.getValue().event());
    }

    @Test
    void generatePersistsOmittedModalitiesInRecordedHistoryAndPublishesThatEvent() {
        UUID agentId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        AgentRepository repository = mock(AgentRepository.class);
        AgentMonitorBroadcaster monitorBroadcaster = mock(AgentMonitorBroadcaster.class);
        AgentBehaviourBroadcaster behaviourBroadcaster = mock(AgentBehaviourBroadcaster.class);
        LanguageModelGateway languageModelGateway = mock(LanguageModelGateway.class);
        PromptMessageAssembler assembler = new PromptMessageAssembler();

        PromptPolicy policy = new PromptPolicy("Respond naturally.", null, PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        State state = new State("conversation", policy, List.of());
        Agent agent = new Agent("agent", "desc", state);

        when(repository.findById(agentId)).thenReturn(Optional.of(agent));
        when(repository.save(agent)).thenReturn(agent);
        when(languageModelGateway.complete(any())).thenReturn("hello");

        AgentApplicationService service = new AgentApplicationService(repository, monitorBroadcaster, behaviourBroadcaster,
                assembler, languageModelGateway);

        BehaviourGenerationOutcome outcome = service.generate(agentId, List.of("speech"), OutputProfile.FULL_PLAN);

        assertSame(BehaviourGenerationOutcome.GENERATED, outcome);
        List<Event> recordedEvents = agent.getEventHistory().toList();
        assertEquals(1, recordedEvents.size());
        Event recorded = recordedEvents.get(0);
        BehaviourPlan recordedPlan = BehaviourPlan.fromJson(recorded.getPayload());
        assertNotNull(recordedPlan);
        assertNull(recordedPlan.getSpeech());
        verify(behaviourBroadcaster).publish(isNull(), same(recorded));
    }

    @Test
    void generateReturnsNotFoundWhenAgentMissing() {
        UUID agentId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        AgentRepository repository = mock(AgentRepository.class);
        AgentMonitorBroadcaster monitorBroadcaster = mock(AgentMonitorBroadcaster.class);
        AgentBehaviourBroadcaster behaviourBroadcaster = mock(AgentBehaviourBroadcaster.class);
        LanguageModelGateway languageModelGateway = mock(LanguageModelGateway.class);
        PromptMessageAssembler assembler = new PromptMessageAssembler();

        when(repository.findById(agentId)).thenReturn(Optional.empty());

        AgentApplicationService service = new AgentApplicationService(repository, monitorBroadcaster, behaviourBroadcaster,
                assembler, languageModelGateway);

        BehaviourGenerationOutcome outcome = service.generate(agentId, List.of("speech"));

        assertSame(BehaviourGenerationOutcome.AGENT_NOT_FOUND, outcome);
    }

    @Test
    void generateReturnsNoBehaviourWhenRuntimeProducesNull() {
        UUID agentId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        Agent agent = mock(Agent.class);
        AgentRepository repository = mock(AgentRepository.class);
        AgentMonitorBroadcaster monitorBroadcaster = mock(AgentMonitorBroadcaster.class);
        AgentBehaviourBroadcaster behaviourBroadcaster = mock(AgentBehaviourBroadcaster.class);
        LanguageModelGateway languageModelGateway = mock(LanguageModelGateway.class);
        PromptMessageAssembler assembler = new PromptMessageAssembler();

        when(repository.findById(agentId)).thenReturn(Optional.of(agent));
        when(agent.generate(any())).thenReturn(null);

        AgentApplicationService service = new AgentApplicationService(repository, monitorBroadcaster, behaviourBroadcaster,
                assembler, languageModelGateway);

        BehaviourGenerationOutcome outcome = service.generate(agentId, List.of("speech"));

        assertSame(BehaviourGenerationOutcome.NO_BEHAVIOUR_GENERATED, outcome);
    }

    @Test
    void realtimeSpeechGenerationPersistsSpeechBeforeBackendComplement() {
        UUID agentId = UUID.fromString("23232323-2323-2323-2323-232323232323");
        AgentRepository repository = mock(AgentRepository.class);
        AgentMonitorBroadcaster monitorBroadcaster = mock(AgentMonitorBroadcaster.class);
        AgentBehaviourBroadcaster behaviourBroadcaster = mock(AgentBehaviourBroadcaster.class);
        LanguageModelGateway languageModelGateway = mock(LanguageModelGateway.class);
        PromptMessageAssembler assembler = new PromptMessageAssembler();

        PromptPolicy policy = new PromptPolicy("Respond naturally.", null, PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        policy.setNonVerbalPlanPrompt(PromptPolicy.DEFAULT_NONVERBAL_PLAN_PROMPT);
        State state = new State("conversation", policy, List.of());
        Agent agent = new Agent("agent", "desc", state);

        when(repository.findById(agentId)).thenReturn(Optional.of(agent));
        when(repository.save(agent)).thenReturn(agent);
        when(languageModelGateway.complete(any()))
                .thenReturn("Canonical realtime speech.")
                .thenReturn("{\"gesture\":\"OPEN_QUESTION\"}");

        AgentApplicationService service = new AgentApplicationService(repository, monitorBroadcaster,
                behaviourBroadcaster, assembler, languageModelGateway);

        assertSame(BehaviourGenerationOutcome.GENERATED,
                service.generate(agentId, null, OutputProfile.REALTIME_SPEECH));
        Event speechEvent = agent.getEventHistory().toList().get(0);
        BehaviourPlan speechPlan = BehaviourPlan.fromJson(speechEvent.getPayload());
        assertNotNull(speechPlan);
        assertEquals("Canonical realtime speech.", speechPlan.getSpeech());
        assertNull(speechPlan.getNonVerbal());

        assertSame(BehaviourGenerationOutcome.GENERATED,
                service.generate(agentId, List.of("speech"), OutputProfile.BACKEND_COMPLEMENT));
        Event complementEvent = agent.getEventHistory().toList().get(1);
        BehaviourPlan complementPlan = BehaviourPlan.fromJson(complementEvent.getPayload());
        assertNotNull(complementPlan);
        assertNull(complementPlan.getSpeech());
        assertNotNull(complementPlan.getNonVerbal());
        assertEquals("OPEN_QUESTION", complementPlan.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
    }

    @Test
    void generatePassesRequestedOutputProfileIntoRuntime() {
        UUID agentId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        Event response = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                "{\"speech\":\"hello\"}");
        Agent agent = mock(Agent.class);
        EventHistory history = mock(EventHistory.class);
        AgentRepository repository = mock(AgentRepository.class);
        AgentMonitorBroadcaster monitorBroadcaster = mock(AgentMonitorBroadcaster.class);
        AgentBehaviourBroadcaster behaviourBroadcaster = mock(AgentBehaviourBroadcaster.class);
        LanguageModelGateway languageModelGateway = mock(LanguageModelGateway.class);
        PromptMessageAssembler assembler = new PromptMessageAssembler();

        when(repository.findById(agentId)).thenReturn(Optional.of(agent));
        when(agent.generate(any())).thenReturn(response);
        when(repository.save(agent)).thenReturn(agent);
        when(agent.getEventHistory()).thenReturn(history);
        when(history.toList()).thenReturn(List.of(response));
        when(agent.getId()).thenReturn(agentId);

        AgentApplicationService service = new AgentApplicationService(repository, monitorBroadcaster, behaviourBroadcaster,
                assembler, languageModelGateway);

        BehaviourGenerationOutcome outcome = service.generate(agentId, null, OutputProfile.BACKEND_COMPLEMENT);

        assertSame(BehaviourGenerationOutcome.GENERATED, outcome);
        verify(agent).generate(eq(new PolicyRuntime(assembler, languageModelGateway, OutputProfile.BACKEND_COMPLEMENT)));
    }

    @Test
    void generateStillSucceedsWhenSsePublishThrowsThrowable() {
        UUID agentId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        Event response = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                "{\"speech\":\"hello\"}");
        Agent agent = mock(Agent.class);
        EventHistory history = mock(EventHistory.class);
        AgentRepository repository = mock(AgentRepository.class);
        AgentMonitorBroadcaster monitorBroadcaster = mock(AgentMonitorBroadcaster.class);
        AgentBehaviourBroadcaster behaviourBroadcaster = mock(AgentBehaviourBroadcaster.class);
        LanguageModelGateway languageModelGateway = mock(LanguageModelGateway.class);
        PromptMessageAssembler assembler = new PromptMessageAssembler();

        when(repository.findById(agentId)).thenReturn(Optional.of(agent));
        when(agent.generate(any())).thenReturn(response);
        when(repository.save(agent)).thenReturn(agent);
        when(agent.getEventHistory()).thenReturn(history);
        when(history.toList()).thenReturn(List.of(response));
        when(agent.getId()).thenReturn(agentId);
        doThrow(new AssertionError("sse monitor failure")).when(monitorBroadcaster).publish(agent);
        doThrow(new AssertionError("sse behaviour failure")).when(behaviourBroadcaster).publish(agentId, response);

        AgentApplicationService service = new AgentApplicationService(repository, monitorBroadcaster, behaviourBroadcaster,
                assembler, languageModelGateway);

        BehaviourGenerationOutcome outcome = service.generate(agentId, null, OutputProfile.FULL_PLAN);

        assertSame(BehaviourGenerationOutcome.GENERATED, outcome);
    }
}
