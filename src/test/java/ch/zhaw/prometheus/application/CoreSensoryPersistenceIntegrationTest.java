package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ch.zhaw.prometheus.agentdefs.*;
import ch.zhaw.prometheus.agentdefs.core.*;
import ch.zhaw.prometheus.model.behaviour.SensoryRemarks;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest(properties = "prometheus.runtime.tick.enabled=false")
class CoreSensoryPersistenceIntegrationTest {
    @Autowired AgentRepository agents;
    @MockitoBean LanguageModelGateway language;
    @Test void newDefinitionsKeepPendingOccasionsAcrossReloadAndWithdrawOnNewEvidence() {
        for (AgentDefinition definition : List.of(new FacialExpressionSensitivity(), new SocialContextSensitivity(), new GenericMultimodalBehaviourAtSocialSpace())) {
            var original = agents.saveAndFlush(definition.createAgent()); var id = original.getId();
            var runtime = new PolicyRuntime(new PromptMessageAssembler(), language)
                    .withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), original.executionEpoch()));
            var now = Instant.now();
            String type = definition instanceof FacialExpressionSensitivity ? Event.TYPE_FACE_EMOTION
                    : definition instanceof SocialContextSensitivity ? Event.TYPE_SOCIAL_CONTEXT : Event.TYPE_SOCIAL_GROUPING;
            for (Instant at : List.of(now.minusSeconds(3), now)) {
                var loaded = agents.findById(id).orElseThrow();
                assertNull(loaded.acknowledge(Event.observation(type, "sensor", "{\"humanCount\":1,\"emotion\":\"happy\",\"confidence\":0.95,\"ts\":\"" + at + "\"}"), runtime));
                agents.saveAndFlush(loaded);
            }
            var loaded = agents.findById(id).orElseThrow();
            assertTrue(loaded.getStorage().containsKey(SensoryRemarks.PENDING));
            assertDoesNotThrow(() -> new ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter().instructions(loaded));
            loaded.acknowledge(Event.observation(type, "sensor", "{\"humanCount\":2,\"emotion\":\"neutral\",\"confidence\":0.95,\"ts\":\"" + Instant.now() + "\"}"), runtime);
            agents.saveAndFlush(loaded);
            assertFalse(agents.findById(id).orElseThrow().getStorage().containsKey(SensoryRemarks.PENDING));
        }
        verify(language, never()).infer(any()); verify(language, never()).decide(any());
    }
}
