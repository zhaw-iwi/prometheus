package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReceptionMultiStateGames;
import ch.zhaw.prometheus.agentdefs.usecases.SwissRoboticsDayReceptionMultiStateGames;
import ch.zhaw.prometheus.agentdefs.usecases.SwissRoboticsDayReceptionMultiStateGamesEnglish;
import ch.zhaw.prometheus.application.live.LiveContextProjection;
import ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter;
import ch.zhaw.prometheus.model.AgentEmbodiment;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;

class SwissRoboticsDayUnitTest {
    @Test void cloneRetainsGameGraphAndCapabilitiesWithItsOwnCatalogIdentity() {
        var original = new GalaDinnerReceptionMultiStateGames();
        var definition = new SwissRoboticsDayReceptionMultiStateGames();
        var agent = definition.createAgent();
        assertNotEquals(original.key(), definition.key());
        assertEquals("Gigi - Swiss Robotics Day with multi-state RPS and guessing game", definition.displayName());
        assertEquals(original.createAgent().reachableStates().stream()
                        .map(state -> state.getName().replaceFirst("^Gala", "Swiss Robotics Day")).collect(Collectors.toSet()),
                agent.reachableStates().stream().map(state -> state.getName()).collect(Collectors.toSet()));
        var before = original.createAgent().getInteractionProfile();
        var after = agent.getInteractionProfile();
        assertEquals(before.getSupportedObservations(), after.getSupportedObservations());
        assertEquals(before.getSupportedBehaviourModalities(), after.getSupportedBehaviourModalities());
        assertTrue(after.isExternalRealtimeSpeech());
        assertTrue(after.isCapabilityAwareness());
        assertTrue(after.isPresenceControlledLive());
        assertEquals(List.of("demo.gigi.swiss_robotics_day", "demo.gigi.sira_lab"), after.getProfileTags());
        assertEquals("de", agent.getLanguageCode());
        assertEquals("GIGI", agent.getPersonaName());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void everyStateAndEmbodimentUsesSwissEventFactsWithinTheLiveBudget(boolean english) {
        for (var embodiment : AgentEmbodiment.values()) {
            var agent = (english ? new SwissRoboticsDayReceptionMultiStateGamesEnglish()
                    : new SwissRoboticsDayReceptionMultiStateGames()).createAgent();
            agent.setEmbodiment(embodiment);
            var adapter = new LiveVoicePolicyAdapter();
            var projection = new LiveContextProjection(new PromptMessageAssembler(), adapter);
            assertTrue(adapter.supports(agent));
            for (var state : agent.reachableStates()) {
                assertInstanceOf(SwissRoboticsDayPolicy.class, state.ownPolicy());
                ReflectionTestUtils.setField(agent, "currentState", state);
                String instructions = adapter.instructions(agent);
                assertTrue(instructions.contains(english ? "Welcome to Swiss Robotics Day" : "Willkommen am Swiss Robotics Day"));
                assertTrue(instructions.contains(english ? "English by default" : "German by default"));
                assertFalse(english && instructions.contains("German by default"));
                assertEquals(english ? "en" : "de", agent.getLanguageCode());
                assertTrue(instructions.contains("13. November 2026"));
                assertTrue(instructions.contains("StageOne Event & Convention Center"));
                assertTrue(instructions.contains("Elias-Canetti-Strasse 146"));
                assertTrue(instructions.contains("ETH Zürich RobotX"));
                assertTrue(instructions.contains("ausverkauft"));
                assertEquals(1, instructions.split("SWISS ROBOTICS DAY REFERENZ", -1).length - 1);
                for (String excluded : List.of("Gala", "gala", "Casinotheater", "Home of Innovation", "24. Oktober",
                        "17:30–18:30", "Winterthurer Jugend", "Community Plus", "co-created", "https://"))
                    assertFalse(instructions.contains(excluded), state.getName() + ": " + excluded);
                assertFalse(state.ownPolicy().describe().contains("Gala"));
                var context = projection.project(agent);
                assertTrue(context.activity().facts().startsWith("Swiss Robotics Day activity:"));
                assertEquals(2000, context.userSilenceMs());
                assertTrue(context.filterIsolatedInputActivity());
                assertTrue(instructions.getBytes(StandardCharsets.UTF_8).length <= 14400);
            }
        }
    }

    @Test void englishDefaultUsesTheSameGraphPoliciesAndFactsAndSurvivesReset() {
        var german = new SwissRoboticsDayReceptionMultiStateGames();
        var english = new SwissRoboticsDayReceptionMultiStateGamesEnglish();
        assertNotEquals(german.key(), english.key());
        assertTrue(english.displayName().endsWith("(English)"));
        var agent = english.createAgent();
        assertEquals(german.createAgent().reachableStates().stream().map(state -> state.getName()).collect(Collectors.toSet()),
                agent.reachableStates().stream().map(state -> state.getName()).collect(Collectors.toSet()));
        for (int attempt = 0; attempt < 2; attempt++) {
            var policy = (SwissRoboticsDayPolicy) agent.getCurrentState().ownPolicy();
            assertTrue(policy.voiceInstructions().contains(SwissRoboticsDayReference.context()));
            assertTrue(policy.liveOpeningInstructions().contains("once in English"));
            assertTrue(policy.describe().contains("English by default"));
            var storage = policy.storage();
            assertTrue(GalaDinnerRpsPreparation.introduction(storage).contains("First, a quick tutorial"));
            GalaDinnerMemory.put(storage, GalaDinnerMemory.MODE, "RPS_SETUP");
            var display = GalaDinnerAction.captureDisplay(storage);
            assertEquals("Rock, Paper, Scissors", display.get("title").getAsString());
            assertTrue(display.get("instruction").getAsString().contains("Still needed: rock, paper, scissors"));
            agent.reset();
            assertEquals("en", agent.getLanguageCode());
            assertEquals("RECEPTION", GalaDinnerMemory.mode(storage));
        }
    }

    @Test void sharedInstitutionalFactsArePreservedAndOriginalEventRemainsSeparate() {
        String swiss = SwissRoboticsDayReference.context();
        for (var section : GalaDinnerReference.document().sections()) {
            if (Set.of("sml", "iwi", "sira", "gigi").contains(section.id()))
                for (String fact : section.facts()) assertTrue(swiss.contains(fact), section.id());
        }
        String gala = new GalaDinnerReceptionMultiStateGames().createAgent().getCurrentState().ownPolicy().describe();
        assertTrue(gala.contains("Gala Dinner"));
        assertTrue(gala.contains("Home of Innovation"));
        assertFalse(gala.contains("Swiss Robotics Day"));
        var event = GalaDinnerReference.load(SwissRoboticsDayReference.RESOURCE);
        assertEquals("2026-11-11", event.recordedOn());
        assertTrue(event.sources().stream().allMatch(source -> !source.urls().isEmpty()
                && source.urls().stream().allMatch(url -> url.startsWith("https://swissroboticsday.ch/"))));
    }
}
