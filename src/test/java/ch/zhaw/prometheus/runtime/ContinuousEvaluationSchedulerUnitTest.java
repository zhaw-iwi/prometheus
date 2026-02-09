package ch.zhaw.prometheus.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.zhaw.prometheus.logging.AgentMonitorBroadcaster;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.repositories.AgentRepository;

@ExtendWith(MockitoExtension.class)
class ContinuousEvaluationSchedulerUnitTest {

    @Mock
    private AgentRepository repository;

    @Mock
    private AgentMonitorBroadcaster monitorBroadcaster;

    @InjectMocks
    private ContinuousEvaluationScheduler scheduler;

    @Test
    void runCycleTicksOnlyActiveAgents() {
        Agent active = org.mockito.Mockito.mock(Agent.class);
        Agent inactive = org.mockito.Mockito.mock(Agent.class);
        when(active.isActive()).thenReturn(true);
        when(inactive.isActive()).thenReturn(false);
        when(this.repository.findAll()).thenReturn(List.of(active, inactive));

        int processed = this.scheduler.runCycle();

        assertEquals(1, processed);
        verify(active).tick();
        verify(this.repository).save(active);
        verify(this.monitorBroadcaster).publish(active);
        verify(inactive, never()).tick();
        verify(this.repository, never()).save(inactive);
    }
}
