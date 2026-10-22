package ch.zhaw.prometheus.application.live;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.agentdefs.core.GenericMultimodalBehaviour;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.policy.ExternalVoicePolicy;
import ch.zhaw.prometheus.model.task.TaskPolicy;

class LiveVoiceAcknowledgementUnitTest {
    static final class ApplicationPolicy extends TaskPolicy implements ExternalVoicePolicy {
        private final Boolean acknowledgements;
        private boolean activity;
        private boolean filterInput;
        ApplicationPolicy(Boolean acknowledgements) { super(new Storage(), "Application voice"); this.acknowledgements = acknowledgements; }
        public boolean supportsLiveState(State state) { return true; }
        public String voiceInstructions() { return "Application voice"; }
        public boolean liveFilterIsolatedInputActivity() { return filterInput; }
        public ch.zhaw.prometheus.model.policy.LiveActivityContext liveActivityContext() {
            return activity ? new ch.zhaw.prometheus.model.policy.LiveActivityContext("Task facts", null) : null;
        }
        public boolean livePendingAcknowledgements() {
            return acknowledgements == null ? ExternalVoicePolicy.super.livePendingAcknowledgements() : acknowledgements;
        }
    }
    @Test void isolatedInputFilteringRequiresExplicitApplicationOptIn() {
        var projection = new LiveContextProjection(new ch.zhaw.prometheus.model.policy.PromptMessageAssembler(), new LiveVoicePolicyAdapter());
        var core = new GenericMultimodalBehaviour().createAgent();
        assertFalse(projection.project(core).filterIsolatedInputActivity());
        var policy = new ApplicationPolicy(false);
        var agent = new Agent("application", "", new State("task", policy, List.of()));
        agent.setInteractionProfile(core.getInteractionProfile());
        assertFalse(projection.project(agent).filterIsolatedInputActivity());
        policy.filterInput = true;
        assertTrue(projection.project(agent).filterIsolatedInputActivity());
    }
    @Test void activityAwareTaskUsesItsApplicationVoiceWithoutLegacyTaskDuplication() {
        var policy = new ApplicationPolicy(false); policy.activity = true;
        policy.storage().put("task.draft", new ch.zhaw.prometheus.model.task.TaskSpec("Quoted visitor goal", 1, List.of(
                new ch.zhaw.prometheus.model.task.TaskSpec.Rule("obs.hand.sign", "sign", "eq",
                        new com.google.gson.JsonPrimitive("paper"), "Respond", ch.zhaw.prometheus.model.task.TaskSpec.Effect.ACT,
                        .7, 1, 3))).json());
        var agent = new Agent("application", "", new State("task", policy, List.of()));
        agent.setInteractionProfile(new GenericMultimodalBehaviour().createAgent().getInteractionProfile());
        var sections = new LiveVoicePolicyAdapter().sections(agent);
        assertFalse(sections.containsKey("taskVoice"));
        assertTrue(sections.get("policy").contains("Application voice"));
        assertTrue(sections.get("state").contains("Task phase: CONFIGURATION"));
        assertFalse(String.join("\n", sections.values()).contains("Quoted visitor goal"));
    }

    @Test void applicationMayDisableBothPermissionsWhileDefaultGuidanceRemainsIdentical() {
        var adapter = new LiveVoicePolicyAdapter();
        var core = new GenericMultimodalBehaviour().createAgent();
        var baseline = adapter.sections(core);
        for (Boolean allowed : java.util.Arrays.asList(null, true, false)) {
            var agent = new Agent("application", "", new State("task", new ApplicationPolicy(allowed), List.of()));
            agent.setInteractionProfile(core.getInteractionProfile());
            var sections = adapter.sections(agent);
            if (Boolean.FALSE.equals(allowed)) {
                assertTrue(sections.get("voice").contains("Wait silently while work is pending"));
                assertTrue(sections.get("taskVoice").contains("Wait silently while work is pending"));
                assertFalse(sections.get("voice").contains("You may acknowledge immediately"));
                assertFalse(sections.get("taskVoice").contains("you may say you are checking"));
            } else {
                assertEquals(baseline.get("voice"), sections.get("voice"));
                assertEquals(baseline.get("taskVoice"), sections.get("taskVoice"));
            }
        }
    }
}
