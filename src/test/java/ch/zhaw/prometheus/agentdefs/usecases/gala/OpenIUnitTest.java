package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReceptionMultiStateGames;
import ch.zhaw.prometheus.agentdefs.usecases.OpenIReceptionMultiStateGames;
import ch.zhaw.prometheus.agentdefs.usecases.OpenIReceptionMultiStateGamesEnglish;
import ch.zhaw.prometheus.agentdefs.usecases.SwissRoboticsDayReceptionMultiStateGames;
import ch.zhaw.prometheus.application.live.LiveContextProjection;
import ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter;
import ch.zhaw.prometheus.model.AgentEmbodiment;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;

class OpenIUnitTest {
    @Test void cloneRetainsGameGraphAndCapabilitiesWithItsOwnCatalogIdentity() {
        var original = new GalaDinnerReceptionMultiStateGames();
        var definition = new OpenIReceptionMultiStateGames();
        var agent = definition.createAgent();
        assertNotEquals(original.key(), definition.key());
        assertNotEquals(new SwissRoboticsDayReceptionMultiStateGames().key(), definition.key());
        assertEquals("Gigi - Open-i with multi-state RPS and guessing game", definition.displayName());
        assertEquals(original.createAgent().reachableStates().stream()
                        .map(state -> state.getName().replaceFirst("^Gala", "Open-i")).collect(Collectors.toSet()),
                agent.reachableStates().stream().map(state -> state.getName()).collect(Collectors.toSet()));
        var before = original.createAgent().getInteractionProfile();
        var after = agent.getInteractionProfile();
        assertEquals(before.getSupportedObservations(), after.getSupportedObservations());
        assertEquals(before.getSupportedBehaviourModalities(), after.getSupportedBehaviourModalities());
        assertTrue(after.isExternalRealtimeSpeech());
        assertTrue(after.isCapabilityAwareness());
        assertTrue(after.isPresenceControlledLive());
        assertEquals(List.of("demo.gigi.open_i", "demo.gigi.sira_lab"), after.getProfileTags());
        assertEquals("de", agent.getLanguageCode());
        assertEquals("GIGI", agent.getPersonaName());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void everyStateAndEmbodimentUsesOpenIFactsWithinTheLiveBudget(boolean english) {
        for (var embodiment : AgentEmbodiment.values()) {
            var agent = (english ? new OpenIReceptionMultiStateGamesEnglish() : new OpenIReceptionMultiStateGames()).createAgent();
            agent.setEmbodiment(embodiment);
            var adapter = new LiveVoicePolicyAdapter();
            var projection = new LiveContextProjection(new PromptMessageAssembler(), adapter);
            assertTrue(adapter.supports(agent));
            for (var state : agent.reachableStates()) {
                assertInstanceOf(OpenIPolicy.class, state.ownPolicy());
                ReflectionTestUtils.setField(agent, "currentState", state);
                String instructions = adapter.instructions(agent);
                assertTrue(instructions.contains(english ? "Welcome to Open-i!" : "Willkommen an der Open-i!"));
                assertTrue(instructions.contains("SIRA Lab's robot at Open-i"));
                assertTrue(instructions.contains(english ? "English by default" : "German by default"));
                assertFalse(english && instructions.contains("German by default"));
                assertEquals(english ? "en" : "de", agent.getLanguageCode());
                for (String fact : List.of("26. November 2026", "Kongresshaus Zürich", "Gotthardstrasse 5", "NZZ Connect",
                        "Innovation Partner", "17:35–19:00"))
                    assertTrue(instructions.contains(fact), state.getName() + ": " + fact);
                assertEquals(1, instructions.split("OPEN-I REFERENZ", -1).length - 1);
                for (String excluded : List.of("Gala", "gala", "Casinotheater", "Home of Innovation", "24. Oktober", "17:30–18:30",
                        "Swiss Robotics Day", "StageOne", "13. November", "co-created", "https://"))
                    assertFalse(instructions.contains(excluded), state.getName() + ": " + excluded);
                assertFalse(state.ownPolicy().describe().contains("Gala"));
                var context = projection.project(agent);
                assertTrue(context.activity().facts().startsWith("Open-i activity:"));
                assertEquals(2000, context.userSilenceMs());
                assertTrue(context.filterIsolatedInputActivity());
                int bytes = instructions.getBytes(StandardCharsets.UTF_8).length;
                assertTrue(bytes <= 14400, state.getName() + ": " + bytes + " bytes");
            }
        }
    }

    @Test void englishDefaultUsesTheSameGraphPoliciesAndFactsAndSurvivesReset() {
        var german = new OpenIReceptionMultiStateGames();
        var english = new OpenIReceptionMultiStateGamesEnglish();
        assertNotEquals(german.key(), english.key());
        assertTrue(english.displayName().endsWith("(English)"));
        var agent = english.createAgent();
        assertEquals(german.createAgent().reachableStates().stream().map(state -> state.getName()).collect(Collectors.toSet()),
                agent.reachableStates().stream().map(state -> state.getName()).collect(Collectors.toSet()));
        for (int attempt = 0; attempt < 2; attempt++) {
            var policy = (OpenIPolicy) agent.getCurrentState().ownPolicy();
            assertTrue(policy.voiceInstructions().contains(OpenIReference.context()));
            assertTrue(policy.liveOpeningInstructions().contains("once in English"));
            assertTrue(policy.describe().contains("English by default"));
            var storage = policy.storage();
            assertTrue(GalaDinnerRpsPreparation.introduction(storage).contains("First, a quick tutorial"));
            GalaDinnerMemory.put(storage, GalaDinnerMemory.MODE, "RPS_SETUP");
            assertEquals("Rock, Paper, Scissors", GalaDinnerAction.captureDisplay(storage).get("title").getAsString());
            agent.reset();
            assertEquals("en", agent.getLanguageCode());
            assertEquals("RECEPTION", GalaDinnerMemory.mode(storage));
        }
    }

    @Test void sharedInstitutionalFactsArePreservedWithOfficiallySourcedEventFacts() {
        String openI = OpenIReference.context();
        for (var section : GalaDinnerReference.document().sections()) {
            if (GalaDinnerReference.INSTITUTION_SECTIONS.contains(section.id()))
                for (String fact : section.facts()) assertTrue(openI.contains(fact), section.id());
        }
        assertFalse(SwissRoboticsDayReference.context().contains("Open-i"));
        var event = GalaDinnerReference.load(OpenIReference.RESOURCE);
        assertEquals("2026-11-23", event.recordedOn());
        assertTrue(event.sources().stream().allMatch(source -> !source.urls().isEmpty()
                && source.urls().stream().allMatch(url -> url.startsWith("https://www.open-i.swiss/de")
                        || url.startsWith("https://kongresshaus.ch/"))));
    }
}
