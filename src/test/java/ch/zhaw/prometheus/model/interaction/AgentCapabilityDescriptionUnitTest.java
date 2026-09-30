package ch.zhaw.prometheus.model.interaction;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;
import ch.zhaw.prometheus.agentdefs.core.LiveMultimodal;

class AgentCapabilityDescriptionUnitTest {
    @Test void optInRoundTripsIndependentlyOfLiveAndLegacyProfilesRemainOff() {
        var original = AgentInteractionProfiles.speechOnly();
        var loaded = AgentInteractionProfile.fromJson(original.withCapabilityAwareness(true)
                .withExternalRealtimeSpeech(true).toJson());
        assertTrue(loaded.isCapabilityAwareness());
        assertTrue(loaded.isExternalRealtimeSpeech());
        assertTrue(loaded.withExternalRealtimeSpeech(false).isCapabilityAwareness());
        assertTrue(loaded.withCapabilityAwareness(false).isExternalRealtimeSpeech());
        assertFalse(original.isCapabilityAwareness());
        assertFalse(AgentInteractionProfile.fromJson("{\"externalRealtimeSpeech\":true}").isCapabilityAwareness());
        assertFalse(AgentInteractionProfile.fromJson(null).isCapabilityAwareness());
        assertEquals(original.getSupportedObservations(), loaded.getSupportedObservations());
    }

    @Test void descriptionContainsExactlyDeclaredChannelsAndNoTagInferredAbilities() {
        var profile = AgentInteractionProfile.of(List.of("obs.user_utterance", "obs.emotion.face"),
                List.of("speech"), List.of("can.read.minds")).withCapabilityAwareness(true);
        String raw = AgentCapabilityDescription.json(profile);
        var json = JsonParser.parseString(raw).getAsJsonObject();
        assertEquals(2, json.getAsJsonArray("supportedObservations").size());
        assertEquals(1, json.getAsJsonArray("supportedBehaviourModalities").size());
        assertEquals(3, json.getAsJsonObject("meanings").size());
        assertFalse(raw.contains("can.read.minds"));
        assertFalse(raw.contains("obs.hand.sign"));
        assertTrue(raw.contains("not knowledge of feelings"));
        assertEquals(raw, AgentCapabilityDescription.json(AgentInteractionProfile.fromJson(profile.toJson())));
    }

    @Test void emptyAndCustomProfilesDoNotInventCapabilitiesAndOptOutAddsNothing() {
        assertEquals("", AgentCapabilityDescription.context(AgentInteractionProfiles.speechOnly()));
        assertEquals("", AgentCapabilityDescription.context(null));
        var empty = JsonParser.parseString(AgentCapabilityDescription.json(
                AgentInteractionProfile.empty().withCapabilityAwareness(true))).getAsJsonObject();
        assertTrue(empty.getAsJsonArray("supportedObservations").isEmpty());
        assertTrue(empty.getAsJsonArray("limits").toString().contains("unspecified"));
        String custom = AgentCapabilityDescription.json(AgentInteractionProfile.of(
                List.of("custom.sensor"), List.of(), List.of()).withCapabilityAwareness(true));
        assertTrue(custom.contains("semantics are unspecified"));
    }

    @Test void fullCoreProfileFitsBudgetAndOversizedExtensionsFailWithoutTruncation() {
        var profile = new LiveMultimodal().createAgent().getInteractionProfile();
        assertTrue(AgentCapabilityDescription.json(profile).getBytes(StandardCharsets.UTF_8).length
                < AgentCapabilityDescription.MAX_JSON_BYTES);
        var huge = AgentInteractionProfile.of(List.of("x".repeat(6001)), List.of(), List.of())
                .withCapabilityAwareness(true);
        assertThrows(IllegalArgumentException.class, () -> AgentCapabilityDescription.json(huge));
    }
}
