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
        ApplicationPolicy(Boolean acknowledgements) { super(new Storage(), "Application voice"); this.acknowledgements = acknowledgements; }
        public boolean supportsLiveState(State state) { return true; }
        public String voiceInstructions() { return "Application voice"; }
        public boolean livePendingAcknowledgements() {
            return acknowledgements == null ? ExternalVoicePolicy.super.livePendingAcknowledgements() : acknowledgements;
        }
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
