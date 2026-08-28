package ch.zhaw.prometheus.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import ch.zhaw.prometheus.controllers.dto.LiveTranscriptionSettingsRequest;
import ch.zhaw.prometheus.controllers.views.AgentInfoView;
import ch.zhaw.prometheus.controllers.views.LiveTranscriptionCapabilitiesView;
import ch.zhaw.prometheus.controllers.views.LiveTranscriptionEffectiveSettingsView;
import ch.zhaw.prometheus.controllers.views.LiveTranscriptionSessionView;
import ch.zhaw.prometheus.spi.LiveTranscriptionSessionClient;
import ch.zhaw.prometheus.spi.LiveTranscriptionSessionInfo;

@Service
public class ScopedLiveTranscriptionService {

    private final ScopedDemoService demoService;
    private final LiveTranscriptionSettingsNormalizer settingsNormalizer;
    private final LiveTranscriptionSettingsDescriptorFactory descriptorFactory;
    private final LiveTranscriptionSessionClient sessionClient;

    public ScopedLiveTranscriptionService(ScopedDemoService demoService,
            LiveTranscriptionSettingsNormalizer settingsNormalizer,
            LiveTranscriptionSettingsDescriptorFactory descriptorFactory,
            LiveTranscriptionSessionClient sessionClient) {
        this.demoService = demoService;
        this.settingsNormalizer = settingsNormalizer;
        this.descriptorFactory = descriptorFactory;
        this.sessionClient = sessionClient;
    }

    public Optional<LiveTranscriptionCapabilitiesView> capabilities(String accessCode, UUID agentId) {
        return this.demoService.getAgentInfo(accessCode, agentId)
                .map(info -> this.descriptorFactory.descriptor(info.getLanguageCode()));
    }

    public Optional<LiveTranscriptionSessionView> createSession(String accessCode, UUID agentId,
            LiveTranscriptionSettingsRequest request) {
        Optional<AgentInfoView> info = this.demoService.getAgentInfo(accessCode, agentId);
        if (info.isEmpty()) {
            return Optional.empty();
        }
        LiveTranscriptionSettings settings = this.settingsNormalizer.normalize(request,
                info.get().getLanguageCode());
        LiveTranscriptionSessionInfo session = this.sessionClient.createSession(settings);
        return Optional.of(new LiveTranscriptionSessionView(
                session.clientSecret(),
                LiveTranscriptionSessionClient.SESSION_TYPE,
                session.model(),
                LiveTranscriptionSettingsDescriptorFactory.SCHEMA_VERSION,
                session.webRtcUrl(),
                LiveTranscriptionEffectiveSettingsView.from(settings)));
    }
}
