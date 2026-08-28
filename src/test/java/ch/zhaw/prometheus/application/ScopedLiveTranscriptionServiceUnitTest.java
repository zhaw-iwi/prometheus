package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.controllers.dto.LiveTranscriptionSettingsRequest;
import ch.zhaw.prometheus.controllers.views.AgentInfoView;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.spi.LiveTranscriptionSessionClient;
import ch.zhaw.prometheus.spi.LiveTranscriptionSessionInfo;

class ScopedLiveTranscriptionServiceUnitTest {

    @Test
    void visibleAgentLanguageDrivesDefaultsAndIssuedMetadata() {
        ScopedDemoService demoService = org.mockito.Mockito.mock(ScopedDemoService.class);
        LiveTranscriptionSessionClient sessionClient = org.mockito.Mockito.mock(LiveTranscriptionSessionClient.class);
        UUID agentId = UUID.randomUUID();
        when(demoService.getAgentInfo("ABCDE", agentId)).thenReturn(Optional.of(
                new AgentInfoView(agentId, "Agent", "Description", true,
                        AgentInteractionProfile.empty(), "ar")));
        when(sessionClient.createSession(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new LiveTranscriptionSessionInfo("ek_test", "gpt-live-transcribe",
                        "https://example.test/v1/realtime/calls"));
        ScopedLiveTranscriptionService service = service(demoService, sessionClient);

        var capabilities = service.capabilities("ABCDE", agentId).orElseThrow();
        var languages = capabilities.settings().stream()
                .filter(setting -> "languages".equals(setting.key()))
                .findFirst()
                .orElseThrow();
        assertEquals(java.util.List.of("ar"), languages.defaultValue());
        assertEquals(java.util.List.of("ar", "de", "en"), languages.allowedValues());
        assertEquals(3, languages.maxItems());

        var result = service.createSession("ABCDE", agentId,
                new LiveTranscriptionSettingsRequest(null, null, null, null, null, null)).orElseThrow();

        assertEquals("ek_test", result.clientSecret());
        assertEquals("transcription", result.sessionType());
        assertEquals(java.util.List.of("ar"), result.effectiveSettings().languages());
        assertEquals("far_field", result.effectiveSettings().noiseReduction());
    }

    @Test
    void invisibleAgentDoesNotIssueProviderSession() {
        ScopedDemoService demoService = org.mockito.Mockito.mock(ScopedDemoService.class);
        LiveTranscriptionSessionClient sessionClient = org.mockito.Mockito.mock(LiveTranscriptionSessionClient.class);
        UUID agentId = UUID.randomUUID();
        when(demoService.getAgentInfo("ABCDE", agentId)).thenReturn(Optional.empty());

        var result = service(demoService, sessionClient).createSession("ABCDE", agentId,
                new LiveTranscriptionSettingsRequest(null, null, null, null, null, null));

        assertTrue(result.isEmpty());
        verify(sessionClient, never()).createSession(org.mockito.ArgumentMatchers.any());
    }

    private static ScopedLiveTranscriptionService service(ScopedDemoService demoService,
            LiveTranscriptionSessionClient sessionClient) {
        return new ScopedLiveTranscriptionService(
                demoService,
                new LiveTranscriptionSettingsNormalizer(),
                new LiveTranscriptionSettingsDescriptorFactory(),
                sessionClient);
    }
}
