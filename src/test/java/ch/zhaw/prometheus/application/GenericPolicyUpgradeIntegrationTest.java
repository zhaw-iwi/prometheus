package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.google.gson.*;
import ch.zhaw.prometheus.agentdefs.core.GenericMultimodalBehaviour;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.repositories.AgentRepository;

@SpringBootTest(properties = "prometheus.runtime.tick.enabled=false")
@AutoConfigureMockMvc
class GenericPolicyUpgradeIntegrationTest {
    @Autowired AgentRepository agents;
    @Autowired GenericPolicyUpgradeService upgrade;
    @Autowired ExternalSpeechOwnership ownership;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc http;
    @Value("${prometheus.admin.token}") String token;
    UUID fixture() throws Exception {
        var raw = getClass().getResourceAsStream("/task-policy-upgrade/legacy-prompts.json");
        var old = JsonParser.parseString(new String(raw.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        var agent = new GenericMultimodalBehaviour().createAgent(); agent.executionEpoch();
        for (var state : agent.reachableStates()) ((TaskPolicy) state.ownPolicy()).setConfiguredInstructions(old.get("voice").getAsString());
        ((TaskPolicy) agent.getCurrentState().ownPolicy()).setConfiguredInstructions("My custom voice instructions.");
        for (var action : agent.reachableActions()) if (action instanceof TaskUpdateAction update)
            ((PromptPolicy) action.getPolicy()).setPromptTemplate(old.get(update.isTaskCue() ? "cue" : "configure").getAsString());
        var storage = ((TaskPolicy) agent.getCurrentState().ownPolicy()).storage();
        storage.put(TaskMemory.SPEC, JsonParser.parseString("""
                {"goal":"Preserve my original agreement","maxActions":4,"rules":[
                {"eventType":"obs.human.presence","field":"humanCount","operator":"eq","value":0,
                "action":"End the agreement","complete":true,"minConfidence":0.8,"samples":2,"cooldownSeconds":3}]}
                """));
        storage.put(TaskMemory.DRAFT, storage.get(TaskMemory.SPEC).deepCopy());
        storage.put(TaskMemory.PHASE, new JsonPrimitive("RUNNING")); storage.put(TaskMemory.ACTIONS, new JsonPrimitive(2));
        agent.getEventHistory().appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Keep this conversation unchanged."));
        return agents.saveAndFlush(agent).getId();
    }
    @Test void previewAndIdempotentApplyPreserveCustomPolicyAgreementAndHistory() throws Exception {
        UUID id = fixture(); var before = agents.findById(id).orElseThrow();
        var spec = before.getStorage().get(TaskMemory.SPEC).deepCopy(); var events = before.getEventHistory().toList();
        String endpoint = "/admin/agents/" + id + "/generic-policy-upgrade";
        http.perform(get(endpoint)).andExpect(status().isUnauthorized());
        var preview = upgrade.preview(id).orElseThrow();
        assertEquals(7, preview.targets().stream().filter(t -> t.status().equals("upgrade")).count());
        assertEquals(1, preview.targets().stream().filter(t -> t.status().equals("custom")).count());
        assertTrue(preview.ruleReview()); assertTrue(preview.pauseRequired());
        assertEquals("RUNNING", agents.findById(id).orElseThrow().getStorage().get(TaskMemory.PHASE).getAsString());
        http.perform(post(endpoint).header("X-Prometheus-Admin-Token", token).contentType("application/json")
                .content("{\"fingerprint\":\"" + "0".repeat(64) + "\"}")).andExpect(status().isConflict());
        http.perform(post(endpoint).header("X-Prometheus-Admin-Token", token).contentType("application/json")
                .content("{\"fingerprint\":\"" + preview.fingerprint() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updatedPolicies").value(7)).andExpect(jsonPath("$.paused").value(true));
        var saved = agents.findById(id).orElseThrow();
        assertEquals(spec, saved.getStorage().get(TaskMemory.SPEC)); assertEquals(spec, saved.getStorage().get(TaskMemory.DRAFT));
        assertEquals(2, saved.getStorage().get(TaskMemory.ACTIONS).getAsInt());
        assertEquals("PAUSED", saved.getStorage().get(TaskMemory.PHASE).getAsString());
        assertEquals(events.stream().map(Event::getId).toList(), saved.getEventHistory().toList().stream().map(Event::getId).toList());
        assertEquals("My custom voice instructions.", ((TaskPolicy) saved.getCurrentState().ownPolicy()).configuredInstructions());
        assertEquals(0, upgrade.apply(id, preview.fingerprint()).orElseThrow().updatedPolicies());
        assertTrue(upgrade.preview(id).orElseThrow().ruleReview());
        assertFalse(new Gson().toJson(preview).contains("original agreement"));
        // Public built-in strings for the separately run, opt-in real-provider interpretation trial.
        Files.writeString(Path.of("target/generic-policy-builtins.json"), new Gson().toJson(GenericMultimodalBehaviour.builtInPrompts()));
    }
    @Test void liveReservationAndChangedAgreementBothPreventApplyingAnOldPreview() throws Exception {
        UUID id = fixture(); var preview = upgrade.preview(id).orElseThrow();
        var owner = new ExternalSpeech(UUID.randomUUID(), agents.findById(id).orElseThrow().executionEpoch()); ownership.acquire(id, owner);
        try { assertThrows(IllegalStateException.class, () -> upgrade.apply(id, preview.fingerprint())); }
        finally { ownership.release(id, owner.sessionId()); }
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var agent = agents.findById(id).orElseThrow(); var storage = ((TaskPolicy) agent.getCurrentState().ownPolicy()).storage();
            var changed = storage.get(TaskMemory.DRAFT).deepCopy().getAsJsonObject(); changed.addProperty("goal", "A newly edited goal");
            storage.put(TaskMemory.DRAFT, changed); agents.saveAndFlush(agent);
        });
        assertThrows(IllegalStateException.class, () -> upgrade.apply(id, preview.fingerprint()));
        assertEquals(7, upgrade.preview(id).orElseThrow().targets().stream().filter(t -> t.status().equals("upgrade")).count());
    }
}
