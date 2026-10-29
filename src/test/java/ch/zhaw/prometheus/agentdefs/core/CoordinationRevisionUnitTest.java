package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.task.TaskMemory;

class CoordinationRevisionUnitTest {
    final CoordinationAgentUnitTest f = new CoordinationAgentUnitTest();
    @Test void contentRevisionKeepsSensorTermsAndNeedsRenewedAcceptance() {
        var agreement = f.sensor(false); f.propose(agreement, f.text); f.start(f.text);
        var edit = agreement.deepCopy();
        edit.addProperty("agentRole", "Offer a dry one-liner per rock");
        edit.getAsJsonObject("reactions").getAsJsonArray("rules").get(0).getAsJsonObject().addProperty("action", "Offer a dry one-liner");
        f.turn("PROPOSE", edit, "Make the response a dry one-liner", f.text);
        assertEquals(edit, f.storage().get(CoordinationMemory.DRAFT));
        assertEquals(agreement, f.storage().get(CoordinationMemory.AGREEMENT));
        assertFalse(TaskMemory.active(f.storage())); f.start(f.text);
        assertEquals(edit, f.storage().get(CoordinationMemory.AGREEMENT));
        assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt());
    }
    @ParameterizedTest @ValueSource(strings = {"modality", "limit", "timing", "trigger", "roles", "checks", "session"})
    void unrequestedMaterialChangesAreRejectedAtomically(String change) {
        var original = f.sensor(false); f.propose(original, f.text); f.start(f.text);
        var edit = original.deepCopy(); var task = edit.getAsJsonObject("reactions");
        switch (change) {
            case "modality" -> edit.add("reactions", com.google.gson.JsonNull.INSTANCE);
            case "limit" -> task.addProperty("maxActions", 1);
            case "timing" -> task.addProperty("firstAction", "IMMEDIATE");
            case "trigger" -> task.getAsJsonArray("rules").get(0).getAsJsonObject().addProperty("value", "scissor");
            case "roles" -> edit.addProperty("humanRole", "Ask verbally for the response");
            case "checks" -> edit.add("checks", f.sensor(true).get("checks"));
            case "session" -> task.addProperty("sessionBound", false);
        }
        var response = f.turn("PROPOSE", edit, "Make the response a dry one-liner", f.text);
        assertTrue(response.getPayload().contains("preserved the agreement"));
        assertEquals(original, f.storage().get(CoordinationMemory.AGREEMENT));
        assertFalse(f.storage().containsKey(CoordinationMemory.DRAFT)); assertTrue(TaskMemory.active(f.storage()));
    }
    @Test void replacementMustBeExplicitAndDoesNotExecuteUntilAccepted() {
        f.propose(f.sensor(false), f.text); f.start(f.text);
        f.turn("REPLACE", f.dialogue(), "Make it a dry one-liner", f.text);
        assertFalse(f.storage().containsKey(CoordinationMemory.DRAFT));
        f.turn("REPLACE", f.dialogue(), "Instead replace this with a dialogue task", f.text);
        assertEquals("READY", CoordinationMemory.stage(f.storage()));
        f.start(f.text); assertFalse(f.storage().containsKey(TaskMemory.SPEC));
    }
    @Test void discardingRestartPreparationCannotReviveACompletedRun() {
        f.propose(f.sensor(false), f.text); f.start(f.text);
        f.turn("COMPLETE", null, "The task is complete", f.text);
        f.storage().put(CoordinationMemory.AGREEMENT, f.sensor(true));
        f.turn("RESTART", null, "Restart the same task", f.text);
        assertEquals("PREPARATION", CoordinationMemory.stage(f.storage()));
        f.turn("DISCARD", null, "Discard that restart proposal", f.text);
        assertEquals("REFLECTION", CoordinationMemory.stage(f.storage()));
        f.say("Resume", f.text); assertFalse(TaskMemory.active(f.storage()));
    }
    @Test void invalidOptionalProposalExpressionDoesNotDiscardAValidAgreement() {
        var update = com.google.gson.JsonParser.parseString(f.update("PROPOSE", f.sensor(false), "Proposal")).getAsJsonObject();
        update.getAsJsonObject("reply").add("nonVerbal", com.google.gson.JsonParser.parseString(
                "{\"gaze\":{\"direction\":\"toward_user\",\"focus\":\"person|group|shared_space|none\"}}"));
        org.mockito.Mockito.when(f.gateway.infer(org.mockito.ArgumentMatchers.any())).thenReturn(update.toString());
        f.say("Propose this sensor task", f.text);
        assertEquals(f.sensor(false), f.storage().get(CoordinationMemory.DRAFT));
        assertEquals("READY", CoordinationMemory.stage(f.storage()));
    }
}
