package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import ch.zhaw.prometheus.application.live.LiveContextProjection;
import ch.zhaw.prometheus.application.live.LiveContextSnapshot;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.model.policy.LiveActivityContext;
import ch.zhaw.prometheus.model.policy.LivePlaybackCue;
import ch.zhaw.prometheus.repositories.AgentRepository;

class LiveAgentContextRefreshUnitTest {
    @ParameterizedTest @ValueSource(strings = {"expired", "future", "revoked", "old_owner", "no_cue"})
    void onlyAnExpiredOwnedPlaybackCueWakesTheOrdinaryRuntime(String kind) {
        UUID id = UUID.randomUUID();
        var owner = new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID());
        var turns = mock(AgentApplicationService.class);
        when(turns.serialized(eq(id), any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(1)).get());
        var agents = mock(AgentRepository.class);
        var agent = mock(Agent.class);
        when(agent.getEventHistory()).thenReturn(new EventHistory());
        when(agents.findById(id)).thenReturn(Optional.of(agent));
        var ownership = mock(ExternalSpeechOwnership.class);
        when(ownership.current(agent)).thenReturn(kind.equals("old_owner")
                ? new ExternalSpeech(UUID.randomUUID(), owner.epoch()) : owner);
        when(ownership.taskWorkAllowed(id, owner)).thenReturn(!kind.equals("revoked"));
        var projection = mock(LiveContextProjection.class);
        var cue = kind.equals("no_cue") ? null : new LivePlaybackCue(UUID.randomUUID(), null,
                Instant.now().plusSeconds(kind.equals("future") ? 30 : -30));
        var pending = new LiveActivityContext("Waiting for countdown", null, null, cue);
        var fresh = new LiveActivityContext("Countdown cancelled", null);
        when(projection.project(agent)).thenReturn(snapshot(id, owner, pending), snapshot(id, owner, fresh));
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var service = new LiveAgentContextService(mock(ScopedDemoService.class), turns, agents,
                projection, transactions, ownership);

        var result = service.refresh(id, owner);
        if (kind.equals("expired")) {
            verify(turns).tick(id);
            assertEquals("Countdown cancelled", result.orElseThrow().context().activity().facts());
        } else {
            verify(turns, never()).tick(any());
            assertEquals(!kind.equals("old_owner"), result.isPresent());
        }
    }

    private static LiveContextSnapshot snapshot(UUID id, ExternalSpeech owner, LiveActivityContext activity) {
        return new LiveContextSnapshot(id, owner.epoch(), "revision", Instant.now(), List.of("state"),
                "instructions", List.of(), 0, Map.of(), activity);
    }
}
