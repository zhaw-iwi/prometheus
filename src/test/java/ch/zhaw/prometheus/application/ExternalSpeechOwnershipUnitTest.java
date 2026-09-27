package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.repositories.*;

class ExternalSpeechOwnershipUnitTest {
    @Test void revokedScopeAndLinkFenceExistingOwnershipWithoutWaitingForBrowserPolling() {
        var codes = mock(AccessCodeRepository.class); var links = mock(AccessCodeAgentRepository.class);
        var service = new ExternalSpeechOwnership(); service.scopes(codes, links);
        UUID agent = UUID.randomUUID(), scope = UUID.randomUUID(); var owner = new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID());
        when(codes.existsByIdAndEnabledTrue(scope)).thenReturn(true); when(links.existsByAccessCode_IdAndAgent_Id(scope, agent)).thenReturn(true);
        service.acquire(agent, owner, scope); assertTrue(service.acceptingInput(agent, owner));
        service.pauseInput(agent, owner.sessionId()); assertFalse(service.acceptingInput(agent, owner)); assertTrue(service.isCurrent(agent, owner));
        when(codes.existsByIdAndEnabledTrue(scope)).thenReturn(false); assertFalse(service.isCurrent(agent, owner));
        when(codes.existsByIdAndEnabledTrue(scope)).thenReturn(true); assertFalse(service.isCurrent(agent, owner));
        service.acquire(agent, owner, scope); when(links.existsByAccessCode_IdAndAgent_Id(scope, agent)).thenReturn(false);
        assertFalse(service.acceptingInput(agent, owner)); assertFalse(service.isCurrent(agent, owner));
    }
}
