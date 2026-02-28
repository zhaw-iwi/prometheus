package ch.zhaw.prometheus.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.zhaw.prometheus.application.AgentApplicationService;
import ch.zhaw.prometheus.logging.AgentBehaviourBroadcaster;
import ch.zhaw.prometheus.logging.AgentMonitorBroadcaster;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.NoOpLanguageModelGateway;

@ExtendWith(MockitoExtension.class)
class ContinuousEvaluationSchedulerUnitTest {

    @Mock
    private AgentRepository repository;

    @Mock
    private AgentMonitorBroadcaster monitorBroadcaster;
    @Mock
    private AgentBehaviourBroadcaster behaviourBroadcaster;
    @Mock
    private AgentApplicationService agentService;

    @InjectMocks
    private ContinuousEvaluationScheduler scheduler;

    @Test
    void runCycleTicksOnlyActiveAgents() {
        Agent active = org.mockito.Mockito.mock(Agent.class);
        Agent inactive = org.mockito.Mockito.mock(Agent.class);
        UUID activeId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(active.isActive()).thenReturn(true);
        when(active.getId()).thenReturn(activeId);
        when(inactive.isActive()).thenReturn(false);
        when(this.repository.findAll()).thenReturn(List.of(active, inactive));
        PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), new NoOpLanguageModelGateway());
        when(this.agentService.runtime()).thenReturn(runtime);
        Event generated = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                "{\"speech\":\"hi\"}");
        when(active.tick(runtime)).thenReturn(generated);

        int processed = this.scheduler.runCycle();

        assertEquals(1, processed);
        verify(active).tick(runtime);
        verify(this.repository).save(active);
        verify(this.monitorBroadcaster).publish(active);
        verify(this.behaviourBroadcaster).publish(activeId, generated);
        verify(inactive, never()).tick(runtime);
        verify(this.repository, never()).save(inactive);
    }

    @Test
    void runCycleContinuesWhenSsePublishThrowsThrowable() {
        Agent active = org.mockito.Mockito.mock(Agent.class);
        UUID activeId = UUID.fromString("12121212-1212-1212-1212-121212121212");
        when(active.isActive()).thenReturn(true);
        when(active.getId()).thenReturn(activeId);
        when(this.repository.findAll()).thenReturn(List.of(active));
        PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), new NoOpLanguageModelGateway());
        when(this.agentService.runtime()).thenReturn(runtime);
        Event generated = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                "{\"speech\":\"hi\"}");
        when(active.tick(runtime)).thenReturn(generated);
        doThrow(new AssertionError("monitor publish failed")).when(this.monitorBroadcaster).publish(active);
        doThrow(new AssertionError("behaviour publish failed")).when(this.behaviourBroadcaster).publish(activeId, generated);

        int processed = this.scheduler.runCycle();

        assertEquals(1, processed);
        verify(this.repository).save(active);
        verify(this.monitorBroadcaster).publish(active);
        verify(this.behaviourBroadcaster).publish(activeId, generated);
    }
}

