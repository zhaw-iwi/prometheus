package ch.zhaw.prometheus.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.application.AgentApplicationService;
import ch.zhaw.prometheus.logging.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.NoOpLanguageModelGateway;

class ContinuousEvaluationSchedulerUnitTest {
    @Test void reloadsEachAgentThroughSerializedServiceAndRetainsPublishFailureTolerance() {
        var repository = mock(AgentRepository.class);
        var monitor = mock(AgentMonitorBroadcaster.class);
        var behaviour = mock(AgentBehaviourBroadcaster.class);
        var service = new AgentApplicationService(repository, monitor, behaviour, new PromptMessageAssembler(), new NoOpLanguageModelGateway());
        var scheduler = new ContinuousEvaluationScheduler(repository, service);
        UUID activeId = UUID.randomUUID(), inactiveId = UUID.randomUUID();
        Agent active = mock(Agent.class), inactive = mock(Agent.class);
        when(repository.findAllIds()).thenReturn(List.of(activeId, inactiveId));
        when(repository.findById(activeId)).thenReturn(Optional.of(active));
        when(repository.findById(inactiveId)).thenReturn(Optional.of(inactive));
        when(active.getId()).thenReturn(activeId); when(active.isActive()).thenReturn(true);
        when(active.getEventHistory()).thenReturn(new Agent("fixture", "", new State("s", new NoOpPolicy(), List.of())).getEventHistory());
        Event response = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, "assistant", "{}");
        when(active.tick(any())).thenReturn(response); when(repository.save(active)).thenReturn(active);
        doThrow(new AssertionError("monitor failed")).when(monitor).publish(active);
        doThrow(new AssertionError("behaviour failed")).when(behaviour).publish(activeId, response);
        assertEquals(1, scheduler.runCycle());
        verify(active).tick(any()); verify(inactive, never()).tick(any());
        verify(repository).save(active); verify(repository, never()).save(inactive);
        verify(monitor).publish(active); verify(behaviour).publish(activeId, response);
        verify(repository, never()).findAll();
    }

    @Test void failedAgentDoesNotPreventOtherAgentsFromTicking() {
        var repository = mock(AgentRepository.class); var service = mock(AgentApplicationService.class);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        when(repository.findAllIds()).thenReturn(List.of(first, second));
        when(service.tick(first)).thenThrow(new IllegalStateException("fixture"));
        when(service.tick(second)).thenReturn(true);
        assertEquals(1, new ContinuousEvaluationScheduler(repository, service).runCycle());
    }
}
