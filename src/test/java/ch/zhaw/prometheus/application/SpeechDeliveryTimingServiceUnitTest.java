package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.controllers.views.AgentInfoView;

class SpeechDeliveryTimingServiceUnitTest {
    @Test void visibilityIdentityEvictionAndExpiryAreCheckedWithoutRetainingCredentials() {
        var access = mock(ScopedDemoService.class);
        var clock = new AtomicLong();
        var service = new SpeechDeliveryTimingService(access, 2, Duration.ofSeconds(10), clock::get);
        var agent = UUID.randomUUID(); var event = UUID.randomUUID();
        when(access.getAgentInfo("owner", agent)).thenReturn(Optional.of(mock(AgentInfoView.class)));
        when(access.getAgentInfo("foreign", agent)).thenReturn(Optional.empty());
        when(access.getAgentInfo("disabled", agent)).thenThrow(new DemoAccessDeniedException());
        var first = service.start(agent, event);
        assertTrue(service.find("owner", agent, event, first.id()).isPresent());
        assertTrue(service.find("foreign", agent, event, first.id()).isEmpty());
        assertTrue(service.find("owner", agent, UUID.randomUUID(), first.id()).isEmpty());
        assertThrows(DemoAccessDeniedException.class, () -> service.find("disabled", agent, event, first.id()));
        var second = service.start(agent, event); var third = service.start(agent, event);
        assertTrue(service.find("owner", agent, event, first.id()).isEmpty());
        assertNotEquals(second.id(), third.id());
        assertTrue(service.find("owner", agent, event, third.id()).isPresent());
        clock.set(Duration.ofSeconds(10).toNanos());
        assertTrue(service.find("owner", agent, event, third.id()).isEmpty());
    }
}
