package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;

import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import com.google.gson.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ch.zhaw.prometheus.agentdefs.core.GenericMultimodalBehaviourAtSocialSpace;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.*;
import ch.zhaw.prometheus.controllers.dto.LiveSessionRequest;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.*;
import ch.zhaw.prometheus.spi.live.LiveSessionGateway;
import fixtures.behaviour.TaskReplyFixture;

/** Real persistence, Live ingress and announcement delivery; voice/model responses are controlled. */
@SpringBootTest(properties = {"prometheus.live.enabled=true", "prometheus.runtime.tick.enabled=false"})
class SocialSpaceLiveJourneySmokeIntegrationTest {
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
    private final List<JsonObject> sent = new CopyOnWriteArrayList<>();
    private String code;
    private UUID id;
    private int sequence;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void arrivalConversationProposalActivationCueStopAndDeparture(boolean compact) {
        code = UUID.randomUUID().toString().substring(0, 5);
        var access = admin.createAccessCode(code, true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of(GenericMultimodalBehaviourAtSocialSpace.KEY));
        id = demo.createAgent(code, GenericMultimodalBehaviourAtSocialSpace.KEY, "ROBOT").getID();
        assertEquals("Valerian", loaded().getPersonaName());
        verifyNoInteractions(language);
        when(gateway.create(any())).thenReturn(new LiveSessionGateway.Session("social_" + id, "v=0 answer"));
        when(gateway.attach(anyString(), any(), any())).thenAnswer(call -> {
            Consumer<JsonObject> receive = call.getArgument(1);
            return new LiveSessionGateway.Connection() {
                public boolean isOpen() { return true; }
                public void close() {}
                public void send(JsonObject event) {
                    sent.add(event.deepCopy());
                    var ack = new JsonObject();
                    ack.addProperty("type", event.get("type").getAsString().replace(".append", ".appended"));
                    if (event.has("event_id")) ack.add("client_event_id", event.get("event_id"));
                    receive.accept(ack);
                }
            };
        });
        var session = live.create(code, id, new LiveSessionRequest("v=0 offer", "marin")).orElseThrow();
        var owner = new ExternalSpeech(session.handle(), live.status(code, id, session.handle()).orElseThrow().epoch());
        try {
            when(language.infer(any())).thenReturn(TaskReplyFixture.plan("Hello there.", compact).toString());
            grouping(0); grouping(1); grouping(1);
            verify(language, times(1)).infer(any());
            hear(owner, "Hello there.");
            assertPlan("Hello there.");

            when(language.infer(any())).thenReturn(TaskReplyFixture.update("KEEP", "null", "Yes, how can I help?", compact));
            say(owner, "Hey Valerian.");
            hear(owner, "Yes, how can I help?");
            assertEquals("CONFIGURATION", TaskMemory.phase(((TaskPolicy) loaded().getCurrentState().ownPolicy()).storage()));
            String spec = """
                    {"goal":"Show paper when I show rock","maxActions":3,"sessionBound":true,"rules":[
                    {"eventType":"obs.hand.sign","field":"sign","operator":"eq","value":"rock",
                     "action":"Show paper and say paper","effect":"ACT","minConfidence":0.8,"samples":1,"cooldownSeconds":3}]}
                    """;
            when(language.infer(any())).thenReturn(TaskReplyFixture.update("PROPOSE", spec, "Three rounds of paper. Shall we start?", compact));
            say(owner, "Please propose a reaction to my rock hand sign.");
            hear(owner, "Three rounds of paper. Shall we start?");
            var draft = loaded().getStorage().get(TaskMemory.DRAFT).deepCopy();
            assertFalse(loaded().getStorage().containsKey(TaskMemory.SPEC));
            when(language.infer(any())).thenReturn(TaskReplyFixture.update("ACTIVATE", "null", "Paper. I am waiting for rock.", compact));
            say(owner, "Start the agreed interaction.");
            hear(owner, "Paper. I am waiting for rock.");
            assertEquals(draft, loaded().getStorage().get(TaskMemory.SPEC));
            assertEquals("RUNNING", loaded().getStorage().get(TaskMemory.PHASE).getAsString());
            assertEquals(1, loaded().getStorage().get(TaskMemory.ACTIONS).getAsInt());
            clearInvocations(language);
            grouping(2); // An active agreement owns sensing; this must not create an ambient greeting.
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                var agent = loaded();
                ((TaskPolicy) agent.getCurrentState().ownPolicy()).storage().put(TaskMemory.AFTER, new JsonPrimitive(Instant.EPOCH.toString()));
                agents.saveAndFlush(agent);
            });
            observe(Event.TYPE_HAND_SIGN, "{\"sign\":\"scissor\",\"confidence\":0.95,\"ts\":\"" + Instant.now() + "\"}");
            verifyNoInteractions(language);
            when(language.infer(any())).thenReturn(TaskReplyFixture.plan("Paper again.", compact).toString());
            String cue = "{\"sign\":\"rock\",\"confidence\":0.95,\"ts\":\"" + Instant.now() + "\"}";
            observe(Event.TYPE_HAND_SIGN, cue);
            hear(owner, "Paper again.");
            assertPlan("Paper again.");
            assertEquals(2, loaded().getStorage().get(TaskMemory.ACTIONS).getAsInt());
            observe(Event.TYPE_HAND_SIGN, cue);
            say(owner, "Stop");
            hear(owner, "The task is stopped.");
            assertEquals("COMPLETED", loaded().getStorage().get(TaskMemory.PHASE).getAsString());
            assertEquals(2, loaded().getStorage().get(TaskMemory.ACTIONS).getAsInt());
            verify(language, times(1)).infer(any()); // Cue only; duplicate and Stop are local.
            when(language.infer(any())).thenReturn(TaskReplyFixture.plan("See you.", compact).toString());
            grouping(0); grouping(0);
            hear(owner, "See you.");
            verify(language, times(2)).infer(any());
            assertFalse(history().stream().filter(event -> Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType()))
                    .anyMatch(event -> JsonParser.parseString(event.getPayload()).getAsJsonObject().has("nv")));
            verifyNoInteractions(speech);
        } finally { live.close(code, id, session.handle()); }
    }

    private Agent loaded() { return agents.findById(id).orElseThrow(); }
    private List<Event> history() { return loaded().getEventHistory().toList(); }
    private void observe(String type, String payload) {
        demo.acknowledge(code, id, new EventRequest(type, "sensor", Event.KIND_OBSERVATION, payload), OutputProfile.FULL_PLAN);
    }
    private void grouping(int count) {
        observe(Event.TYPE_SOCIAL_GROUPING, "{\"humanCount\":" + count + ",\"groupCount\":0,\"singletonCount\":" + count
                + ",\"largestGroupSize\":" + Math.min(count, 1) + ",\"ts\":\"" + Instant.now() + "\"}");
    }
    private void say(ExternalSpeech owner, String text) {
        int next = ++sequence;
        var fragment = new Fragment("social-" + next, Speaker.USER, next * 1000L, next * 1000L + 500,
                Instant.now().toEpochMilli(), next, text);
        assertTrue(ingress.receipt(id, owner, fragment));
        var segment = new Segment(UUID.randomUUID(), Speaker.USER, List.of(fragment), Closure.COMPLETE,
                "observed_silence", fragment.receivedMs(), fragment.receivedMs());
        assertEquals("COMPLETE", ingress.commit(id, owner, segment, loaded().getCurrentState().getActiveStatePath()).orElseThrow().status());
    }
    private void hear(ExternalSpeech owner, String text) {
        var intent = history().stream().filter(ConversationProjection::isIntent)
                .filter(event -> text.equals(BehaviourPlan.fromJson(event.getPayload()).getSpeech())).reduce((a, b) -> b).orElseThrow();
        await().atMost(Duration.ofSeconds(12)).until(() -> sent.stream().anyMatch(event ->
                "session.commentary.append".equals(event.get("type").getAsString()) && event.toString().contains(intent.getId().toString())));
        assertTrue(recording.record(id, owner, "heard-" + UUID.randomUUID(), text, List.of(intent.getId()),
                SpeechProvenance.Association.CONFIRMED, true).isPresent());
    }
    private void assertPlan(String text) {
        assertTrue(history().stream().filter(ConversationProjection::isIntent).anyMatch(event ->
                JsonParser.parseString(event.getPayload()).equals(TaskReplyFixture.plan(text, false))));
    }
}
