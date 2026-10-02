package ch.zhaw.prometheus.application.live;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.agentdefs.core.LiveMultimodal;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.interaction.*;
import ch.zhaw.prometheus.model.policy.*;

class AgentCapabilityLiveContextUnitTest {
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    private static LiveContextSnapshot snapshot(Agent agent, Instant now) {
        return new LiveContextProjection(new PromptMessageAssembler(), new LiveVoicePolicyAdapter(),
                Clock.fixed(now, ZoneOffset.UTC)).project(agent);
    }

    @Test void capabilitiesRemainInGuidanceDespiteSelectionHistoryEvictionAndTime() {
        Agent agent = new LiveMultimodal().createAgent();
        String context = AgentCapabilityDescription.context(agent.getInteractionProfile());
        for (int i = 0; i < 60; i++) agent.getEventHistory().appendEvent(
                Event.observation(Event.TYPE_USER_UTTERANCE, "user", "history " + i + "x".repeat(500)));
        var first = snapshot(agent, NOW);
        assertTrue(first.omitted() > 0);
        assertTrue(first.instructions().endsWith(context));
        assertFalse(first.startupInput().toString().contains(AgentCapabilityDescription.MARKER));
        agent.getCurrentState().setEventSelectorSpec(EventSelectorSpec.type("unselected"));
        var selected = snapshot(agent, NOW.plusSeconds(3600));
        assertTrue(selected.items().isEmpty());
        assertEquals(first.instructions(), selected.instructions());
        assertTrue(selected.instructions().contains("obs.emotion.face"));
    }

    @Test void stateGuidanceUpdatesRetainStartupCapabilitiesWithoutResendingThem() {
        State state = new State("state", new PromptPolicy("First state guidance", null, null), List.of());
        Agent agent = new Agent("fixture", "", state);
        agent.setInteractionProfile(AgentInteractionProfiles.speechOnly()
                .withExternalRealtimeSpeech(true).withCapabilityAwareness(true));
        var first = snapshot(agent, NOW);
        var delivery = new LiveContextDelivery(first); delivery.ready();
        assertTrue(delivery.plan(snapshot(agent, NOW.plusSeconds(1)), List.of()).commands().isEmpty());
        state.setPolicy(new PromptPolicy("Changed state guidance", null, null));
        var changed = snapshot(agent, NOW.plusSeconds(2));
        var commands = delivery.plan(changed, List.of()).commands();
        assertNotEquals(first.revision(), changed.revision());
        assertFalse(commands.isEmpty());
        assertTrue(commands.stream().allMatch(c -> c.type().equals("session.instructions.append")));
        String joined = commands.stream().map(LiveContextDelivery.Command::content).reduce("", String::concat);
        assertFalse(joined.contains(AgentCapabilityDescription.MARKER));
        assertFalse(joined.contains("You are the spoken interface"));
        assertTrue(joined.contains("Changed state guidance"));
        agent.reset();
        var reconnect = snapshot(agent, NOW.plusSeconds(3));
        assertNotEquals(first.epoch(), reconnect.epoch());
        assertEquals(changed.instructions(), reconnect.instructions());
    }

    @Test void optOutOmitsReferenceAndAwarenessDoesNotGrantLiveEligibility() {
        Agent agent = new LiveMultimodal().createAgent();
        agent.setInteractionProfile(agent.getInteractionProfile().withCapabilityAwareness(false));
        var off = snapshot(agent, NOW);
        assertFalse(off.instructions().contains(AgentCapabilityDescription.MARKER));
        agent.setInteractionProfile(agent.getInteractionProfile().withCapabilityAwareness(true));
        assertNotEquals(off.revision(), snapshot(agent, NOW).revision());
        agent.setInteractionProfile(agent.getInteractionProfile().withExternalRealtimeSpeech(false));
        assertFalse(new LiveVoicePolicyAdapter().supports(agent));
        assertThrows(IllegalArgumentException.class, () -> snapshot(agent, NOW));
    }

    @Test void combinedGuidanceBudgetFailsExplicitlyInsteadOfTruncatingCapabilitiesOrTaskRules() {
        Agent agent = new Agent("fixture", "", new State("state", new PromptPolicy("x".repeat(14000), null, null), List.of()));
        agent.setInteractionProfile(new LiveMultimodal().createAgent().getInteractionProfile().withCapabilityAwareness(false));
        assertDoesNotThrow(() -> snapshot(agent, NOW));
        agent.setInteractionProfile(agent.getInteractionProfile().withCapabilityAwareness(true));
        assertThrows(IllegalArgumentException.class, () -> snapshot(agent, NOW));
    }
}
