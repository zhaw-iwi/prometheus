package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.behaviour.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.*;

@SpringBootTest(properties = "prometheus.runtime.tick.enabled=false")
class DirectCommandIntegrationTest {
    @Autowired AccessCodeAdminService access;
    @Autowired ScopedDemoService demo;
    @Autowired AgentRepository agents;
    @MockitoBean LanguageModelGateway gateway;

    @ParameterizedTest @CsvSource({"core.coordination,rock", "core.coordination,thumbs_up", "core.generic_multimodal_behaviour,rock", "core.generic_multimodal_behaviour_at_social_space,rock", "core.live_multimodal,rock"})
    void declaredSavedInstancesPublishScopedCommandsAcrossReloadAndReset(String type, String sign) {
        String code = UUID.randomUUID().toString().substring(0, 5);
        var scope = access.createAccessCode(code, true); access.replaceAllowedAgentTypes(scope.getId(), List.of(type));
        var agentId = demo.createAgent(code, type).getID();
        var otherScope = access.createAccessCode(UUID.randomUUID().toString().substring(0, 5), true);
        // No new definition flag/migration is required: these persisted profiles and policies existed before commanding.
        var saved = agents.findById(agentId).orElseThrow();
        assertFalse(saved.getStorage().containsKey(DirectCommands.LAST));
        String profile = saved.getInteractionProfile().toJson();
        var root = new JsonObject(); root.add("command", JsonParser.parseString("""
            {"intent":"ACTION","kind":"HAND_SIGN","value":"rock","silent":true}
            """));
        if (!type.equals("core.live_multimodal")) {
            root.addProperty("operation", "KEEP"); root.add(type.equals("core.coordination") ? "agreement" : "task", JsonNull.INSTANCE);
            root.add("reply", new JsonObject());
        }
        root.getAsJsonObject("command").addProperty("value", sign);
        when(gateway.infer(any())).thenAnswer(c -> ((InferenceRequest)c.getArgument(0)).purpose() == InferencePurpose.DECISION ? "false" : root.toString());
        try {
            for (int i = 0; i < 2; i++) {
                var acknowledged = demo.acknowledge(code, agentId, new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION,
                        "Show " + sign + " without speaking"), OutputProfile.FULL_PLAN).orElseThrow();
                if (acknowledged.getResponseEvent() == null) assertEquals(BehaviourGenerationOutcome.GENERATED,
                        demo.generate(code, agentId, List.of(), OutputProfile.FULL_PLAN));
                var loaded = agents.findById(agentId).orElseThrow();
                assertEquals(profile, loaded.getInteractionProfile().toJson());
                var receipt = loaded.getStorage().get(DirectCommands.LAST).getAsJsonObject();
                assertEquals("ACCEPTED", receipt.get("status").getAsString());
                var source = loaded.getEventHistory().toList().stream().filter(e -> e.sourcePosition().equals(receipt.get("sourcePosition").getAsLong())).findFirst().orElseThrow();
                assertEquals(Event.TYPE_USER_UTTERANCE, source.getType()); assertNotNull(source.getId());
                var restored = new ch.zhaw.prometheus.model.Storage();
                restored.put(DirectCommands.HISTORY, loaded.getStorage().get(DirectCommands.HISTORY).deepCopy());
                var selected = loaded.getEventHistory().select(EventSelector.of(e -> Objects.equals(source.sourcePosition(), e.sourcePosition())));
                assertTrue(DirectCommands.handled(restored, selected), "Persisted append identity also survives detached event selection");
                assertEquals(i + 1, loaded.getStorage().get(DirectCommands.HISTORY).getAsJsonArray().size());
                long plans = loaded.getEventHistory().toList().stream().filter(e -> Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(e.getType()))
                        .map(e -> BehaviourPlan.fromJson(e.getPayload())).filter(p -> p.getMotion() != null
                                && p.getMotion().getAsJsonObject().has(sign.equals("rock") ? "handSign" : "handAction")).count();
                assertEquals(i + 1, plans);
                // Re-generating this admitted command after reload cannot publish a second action.
                if (sign.equals("thumbs_up")) {
                    demo.generate(code, agentId, List.of(), OutputProfile.FULL_PLAN);
                    assertEquals(i + 1, agents.findById(agentId).orElseThrow().getEventHistory().toList().stream()
                            .filter(e -> Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(e.getType()))
                            .map(e -> BehaviourPlan.fromJson(e.getPayload())).filter(p -> p.getMotion() != null).count());
                }
                assertFalse(loaded.getStorage().containsKey("task.spec")); assertFalse(loaded.getStorage().containsKey("coordination.agreement"));
                assertTrue(demo.acknowledge(otherScope.getCode(), agentId,
                        new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION, "Make paper"), OutputProfile.FULL_PLAN).isEmpty());
                assertEquals(i + 1, agents.findById(agentId).orElseThrow().getStorage().get(DirectCommands.HISTORY).getAsJsonArray().size());
            }
            root.getAsJsonObject("command").addProperty("intent", "UNSUPPORTED");
            root.getAsJsonObject("command").add("kind", JsonNull.INSTANCE);
            root.getAsJsonObject("command").add("value", JsonNull.INSTANCE);
            var rejected = demo.acknowledge(code, agentId, new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION,
                    "Walk over here without speaking"), OutputProfile.FULL_PLAN).orElseThrow();
            if (rejected.getResponseEvent() == null) demo.generate(code, agentId, List.of(), OutputProfile.FULL_PLAN);
            assertEquals("UNSUPPORTED", agents.findById(agentId).orElseThrow().getStorage().get(DirectCommands.LAST).getAsJsonObject().get("status").getAsString());
            // Reset must clear the identity ledger with the history, rather than suppressing a new turn at position zero.
            doReturn("{\"nonVerbal\":{\"gesture\":\"NONE\"}}").when(gateway).infer(any());
            demo.reset(code, agentId).orElseThrow();
            assertFalse(agents.findById(agentId).orElseThrow().getStorage().containsKey(DirectCommands.HISTORY));
        } finally { demo.deleteAgent(code, agentId); }
    }
}
