package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.google.gson.*;
import ch.zhaw.prometheus.agentdefs.core.GenericMultimodalBehaviour;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.*;
import ch.zhaw.prometheus.controllers.dto.LiveSessionRequest;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.*;
import ch.zhaw.prometheus.spi.live.LiveSessionGateway;

@SpringBootTest(properties = {"prometheus.live.enabled=true", "prometheus.runtime.tick.enabled=false"})
class GenericMultimodalTaskIntegrationTest {
    @Autowired AgentRepository agents;
    @Autowired AccessCodeAdminService admin;
    @Autowired ScopedDemoService demo;
    @Autowired ScopedLiveSessionService live;
    @Autowired LiveTranscriptIngressService ingress;
    @Autowired ExternalSpeechRecordingService recording;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean LiveSessionGateway gateway;
    @MockitoBean LanguageModelGateway language;
    @MockitoBean SpeechSynthesisGateway speech;

    @Test void scopedCreationReloadLiveTranscriptSensorNarrationAndStopShareOneDurableTask() {
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 5);
        var access = admin.createAccessCode(code, true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of(GenericMultimodalBehaviour.KEY));
        UUID id = demo.createAgent(code, GenericMultimodalBehaviour.KEY).getID();
        assertTrue(demo.listAgentTypes(code).stream().anyMatch(type -> type.getKey().equals(GenericMultimodalBehaviour.KEY)));
        var loaded = agents.findById(id).orElseThrow();
        assertInstanceOf(TaskState.class, loaded.getCurrentState());
        assertInstanceOf(TaskPolicy.class, loaded.getCurrentState().ownPolicy());
        verifyNoInteractions(language);
        when(language.infer(any())).thenReturn("""
                {"operation":"PROPOSE","task":{"goal":"Greet arrivals","maxActions":3,"rules":[
                {"eventType":"obs.human.presence","field":"humanCount","operator":"gt","value":1,
                "action":"Welcome the visible group","complete":false,"minConfidence":0.5,"samples":1,"cooldownSeconds":3}]},
                "reply":{"speech":"I propose greeting arrivals."}}
                """);
        demo.acknowledge(code, id, new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION,
                "Let's plan greetings for arriving groups."), ch.zhaw.prometheus.model.policy.OutputProfile.FULL_PLAN);
        var proposed = agents.findById(id).orElseThrow().getStorage().get(TaskMemory.DRAFT).deepCopy();
        assertEquals("CONFIGURATION", agents.findById(id).orElseThrow().getStorage().get(TaskMemory.PHASE).getAsString());
        clearInvocations(language);
        var sent = new CopyOnWriteArrayList<JsonObject>(); var receiver = new AtomicReference<Consumer<JsonObject>>();
        when(gateway.create(any())).thenReturn(new LiveSessionGateway.Session("live_generic_task", "v=0 answer"));
        when(gateway.attach(anyString(), any(), any())).thenAnswer(call -> {
            receiver.set(call.getArgument(1));
            return new LiveSessionGateway.Connection() {
                public boolean isOpen() { return true; }
                public void close() {}
                public void send(JsonObject event) {
                    sent.add(event.deepCopy()); var ack = new JsonObject();
                    ack.addProperty("type", event.get("type").getAsString().replace(".append", ".appended"));
                    if (event.has("event_id")) ack.addProperty("client_event_id", event.get("event_id").getAsString());
                    receiver.get().accept(ack);
                }
            };
        });
        var session = live.create(code, id, new LiveSessionRequest("v=0 offer", "marin")).orElseThrow();
        var owner = new ExternalSpeech(session.handle(), live.status(code, id, session.handle()).orElseThrow().epoch());
        try {
            when(language.infer(any())).thenReturn("""
                    {"operation":"ACTIVATE","task":null,
                    "reply":{"speech":"I will greet arrivals.","nonVerbal":{"gesture":"PLAYFUL_CURIOUS"}}}
                    """, "{\"speech\":\"Welcome, everyone.\",\"nonVerbal\":{\"gesture\":\"ACKNOWLEDGE\"}}");
            Instant now = Instant.now();
            var fragment = new Fragment("generic-user-1", Speaker.USER, 100L, 2000L, now.toEpochMilli(), 1,
                    "Go ahead.");
            assertTrue(ingress.receipt(id, owner, fragment));
            var segment = new Segment(UUID.randomUUID(), Speaker.USER, List.of(fragment), Closure.COMPLETE,
                    "observed_silence", now.toEpochMilli(), now.toEpochMilli());
            ingress.commit(id, owner, segment, loaded.getCurrentState().getActiveStatePath());
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertEquals(1, commentary(sent)));
            assertEquals("RUNNING", agents.findById(id).orElseThrow().getStorage().get(TaskMemory.PHASE).getAsString());
            assertEquals(proposed, agents.findById(id).orElseThrow().getStorage().get(TaskMemory.SPEC));
            assertTrue(agents.findById(id).orElseThrow().getEventHistory().toList().stream().anyMatch(event ->
                    event.getPayload().contains("I will greet arrivals.") && event.getPayload().contains("\"gesture\":\"NONE\"")));
            recording.record(id, owner, "native-generic-1", "I will greet arrivals.", List.of(), SpeechProvenance.Association.NONE, true);
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                var agent = agents.findById(id).orElseThrow();
                ((TaskPolicy) agent.getCurrentState().ownPolicy()).storage().put(TaskMemory.AFTER, new JsonPrimitive(Instant.EPOCH.toString()));
                agents.saveAndFlush(agent);
            });
            String payload = "{\"humanCount\":2,\"avgDetectionConfidence\":0.9,\"ts\":\"" + Instant.now().minusMillis(100) + "\"}";
            demo.acknowledge(code, id, new EventRequest(Event.TYPE_HUMAN_PRESENCE, "sensor", Event.KIND_OBSERVATION, payload), ch.zhaw.prometheus.model.policy.OutputProfile.FULL_PLAN);
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertEquals(2, commentary(sent)));
            assertTrue(sent.stream().anyMatch(event -> event.toString().contains("Welcome, everyone.")));
            // The same sample cannot advance the task again, even across a database reload.
            demo.acknowledge(code, id, new EventRequest(Event.TYPE_HUMAN_PRESENCE, "sensor", Event.KIND_OBSERVATION, payload), ch.zhaw.prometheus.model.policy.OutputProfile.FULL_PLAN);
            verify(language, times(2)).infer(any());
            demo.acknowledge(code, id, new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION, "Stop"), ch.zhaw.prometheus.model.policy.OutputProfile.FULL_PLAN);
            assertEquals("COMPLETED", agents.findById(id).orElseThrow().getStorage().get(TaskMemory.PHASE).getAsString());
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertEquals(3, commentary(sent)));
            demo.reset(code, id);
            assertFalse(agents.findById(id).orElseThrow().getStorage().containsKey(TaskMemory.SPEC));
            verifyNoInteractions(speech);
        } finally { live.close(code, id, session.handle()); }
    }
    private static long commentary(List<JsonObject> events) {
        return events.stream().filter(event -> "session.commentary.append".equals(event.get("type").getAsString())).count();
    }
}
