package ch.zhaw.prometheus.application.study;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import ch.zhaw.prometheus.agentdefs.core.CoordinationAgent;
import ch.zhaw.prometheus.agentdefs.study.StudyCoordinationAgent;
import ch.zhaw.prometheus.model.event.Event;

class StudyMaterialsTest {
    @Test void versionedMaterialsKeepAllScenariosUnreadyUntilImplemented() {
        var materials = new StudyMaterials();
        assertEquals(3, materials.manifest().scenarios().size());
        assertTrue(materials.manifest().scenarios().stream().noneMatch(StudyMaterials.Scenario::ready));
        assertEquals(8, materials.manifest().post().size());
        assertThrows(IllegalArgumentException.class, () -> materials.scenario("unknown"));
    }
    @Test void invalidIdsLanguageAndPrematureReadinessFailClosed() {
        String json = new Gson().toJson(new StudyMaterials().manifest());
        for (String invalid : new String[]{json.replace("\"ready\":false", "\"ready\":true"),
                json.replace("\"en\"", "\"de\""), json.replace("guess.roles", "guess.limit"),
                json.replace("\"guess\"", "\"other\"")})
            assertThrows(IllegalArgumentException.class, () -> StudyMaterials.parse(invalid));
    }
    @Test void studyPresentationReusesControlWithoutCorePersonaOrVoice() {
        var study = new StudyCoordinationAgent().createAgent(); var core = new CoordinationAgent().createAgent();
        assertEquals(java.util.List.of(Event.TYPE_USER_UTTERANCE,Event.TYPE_HUMAN_PRESENCE), study.getInteractionProfile().getSupportedObservations());
        assertFalse(study.getInteractionProfile().isExternalRealtimeSpeech());
        assertEquals("en", study.getLanguageCode());
        assertFalse(study.getCurrentState().getTotalPolicy().contains("Laureline"));
        assertTrue(core.getCurrentState().getTotalPolicy().contains("Laureline"));
        assertEquals(core.reachableStates().size(), study.reachableStates().size());
        assertTrue(core.getInteractionProfile().isExternalRealtimeSpeech());
    }
}
