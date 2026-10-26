package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonPrimitive;
import ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.TaskMemory;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class CoreSensoryReactionUnitTest {
    final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway);
    Event observation(String type, int count, Instant at) {
        return Event.observation(type, "sensor", "{\"humanCount\":" + count + ",\"emotion\":\"happy\",\"confidence\":0.9,\"ts\":\"" + at + "\"}");
    }
    @Test void newSocialSpaceNeedsStablePresenceAndLiveOwnsTheExpiringCommentWording() {
        Agent agent = new GenericMultimodalBehaviourAtSocialSpace().createAgent();
        var live = runtime.withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch()));
        var now = Instant.now();
        assertNull(agent.acknowledge(observation(Event.TYPE_SOCIAL_GROUPING, 1, now.minusSeconds(3)), live));
        assertNull(agent.acknowledge(observation(Event.TYPE_SOCIAL_GROUPING, 1, now), live));
        var context = ((ExternalVoicePolicy) agent.getCurrentState().ownPolicy()).liveActivityContext();
        assertNotNull(context); assertTrue(context.continuation().interruptible());
        assertTrue(context.facts().contains("humanCount")); verifyNoInteractions(gateway);
        var storage = ((ch.zhaw.prometheus.model.task.TaskPolicy) agent.getCurrentState().ownPolicy()).storage();
        storage.get(SensoryRemarks.PENDING).getAsJsonObject().addProperty("expires", now.minusSeconds(1).toString());
        var expired = ((ExternalVoicePolicy) agent.getCurrentState().ownPolicy()).liveActivityContext();
        assertNotNull(expired); assertNull(expired.continuation());
        assertTrue(expired.facts().contains("No automatic sensory comment"));
        agent.acknowledge(observation(Event.TYPE_SOCIAL_GROUPING, 2, now.plusMillis(1)), live);
        assertNull(((ExternalVoicePolicy) agent.getCurrentState().ownPolicy()).liveActivityContext());
        ((ch.zhaw.prometheus.model.task.TaskPolicy) agent.getCurrentState().ownPolicy()).storage().put(TaskMemory.PHASE, new JsonPrimitive("RUNNING"));
        agent.acknowledge(observation(Event.TYPE_SOCIAL_GROUPING, 3, now.plusMillis(2)), live);
        verifyNoInteractions(gateway);
    }
    @Test void bothSensorDemosPublishOneReactionAndKeepLiveCompatibleBudgetedPolicies() {
        for (var definition : List.of(new FacialExpressionSensitivity(), new SocialContextSensitivity())) {
            Agent agent = definition.createAgent(); String type = definition instanceof FacialExpressionSensitivity ? Event.TYPE_FACE_EMOTION : Event.TYPE_SOCIAL_CONTEXT;
            var now = Instant.now(); var live = runtime.withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch()));
            assertTrue(new LiveVoicePolicyAdapter().supports(agent));
            assertDoesNotThrow(() -> new LiveVoicePolicyAdapter().instructions(agent));
            assertNull(agent.acknowledge(observation(type, 1, now.minusSeconds(2)), live));
            assertNull(agent.acknowledge(observation(type, 1, now), live));
            var policy = (ExternalVoicePolicy) agent.reachableStates().stream().map(State::ownPolicy).filter(SensoryPolicy.class::isInstance).findFirst().orElseThrow();
            assertNotNull(policy.liveActivityContext().continuation());
            var id = policy.liveActivityContext().continuation().id();
            agent.acknowledge(observation(type, 1, now.plusMillis(1)), live);
            assertEquals(id, policy.liveActivityContext().continuation().id());
            verify(gateway, never()).infer(any()); verify(gateway, never()).decide(any());
            agent.reset(); assertNull(policy.liveActivityContext().continuation());
        }
    }
    @Test void ordinaryTextKeepsBackendSpeechAndProviderFailureDoesNotLoop() {
        for (boolean fail : List.of(false, true)) {
            Agent agent = new SocialContextSensitivity().createAgent(); var now = Instant.now(); reset(gateway);
            if (fail) when(gateway.infer(any())).thenThrow(new IllegalStateException("offline"));
            else when(gateway.infer(any())).thenReturn("{\"speech\":\"A group has joined us.\",\"nonVerbal\":{\"gesture\":\"NONE\"}}");
            assertNull(agent.acknowledge(observation(Event.TYPE_SOCIAL_CONTEXT, 2, now.minusSeconds(2)), runtime));
            var result = agent.acknowledge(observation(Event.TYPE_SOCIAL_CONTEXT, 2, now), runtime);
            if (fail) assertNull(result); else assertEquals("A group has joined us.", BehaviourPlan.fromJson(result.getPayload()).getSpeech());
            assertNull(agent.acknowledge(observation(Event.TYPE_SOCIAL_CONTEXT, 2, now.plusMillis(1)), runtime));
            verify(gateway, times(1)).infer(any());
        }
    }
}
