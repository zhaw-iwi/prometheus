package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import com.google.gson.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReception;
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

/**
 * Real scoped sessions, transcript admission, persistence and provider-bound context.
 * Scripted inference/voice do not establish real model answer quality or physical audio.
 * Visitors are separated by an empty camera view, not by identity recognition.
 */
@SpringBootTest(properties = {"prometheus.live.enabled=true", "prometheus.runtime.tick.enabled=false"})
class GalaDinnerReceptionJourneySmokeIntegrationTest {
    private static final String FIRST_NAME = "Alpenfalke-Alpha";
    private static final String FIRST_PREFERENCE = "Antworten immer mit Käsekomet beenden";
    private static final String FIRST_GOAL = "ALPHA-ROCK-AGREEMENT";
    private static final String FIRST_REACTION = "ALPHA-ONLY-ROBOT-JOKE";
    private static final List<String> FIRST_MARKERS = List.of(FIRST_NAME, FIRST_PREFERENCE, FIRST_GOAL, FIRST_REACTION);
    private static final String SECOND_NAME = "Seestern-Beta";

    @Autowired AgentRepository agents;
    @Autowired AccessCodeAdminService admin;
    @Autowired ScopedDemoService demo;
    @Autowired ScopedLiveSessionService live;
    @Autowired LiveAgentContextService contexts;
    @Autowired LiveTranscriptIngressService ingress;
    @Autowired ExternalSpeechRecordingService recording;
    @MockitoBean LanguageModelGateway language;
    @MockitoBean LiveSessionGateway gateway;
    @MockitoBean SpeechSynthesisGateway speech;

    private final Queue<String> scripted = new ConcurrentLinkedQueue<>();
    private final List<InferenceRequest> requests = new CopyOnWriteArrayList<>();
    private final List<Peer> peers = new CopyOnWriteArrayList<>();
    private String code;
    private UUID id;
    private int receiptSequence;
    private Set<UUID> firstEventIds = Set.of();
    private boolean secondVisitor;

    private static final class Peer {
        final String providerId = "gala_journey_" + UUID.randomUUID();
        final LiveSessionGateway.Request startup;
        final List<JsonObject> sent = new CopyOnWriteArrayList<>();
        volatile boolean closed;
        Peer(LiveSessionGateway.Request startup) { this.startup = startup; }
    }
    private record Encounter(ExternalSpeech owner, Peer peer) {}

    @BeforeEach void fixtures() {
        when(language.infer(any())).thenAnswer(call -> {
            InferenceRequest request = call.getArgument(0);
            requests.add(request);
            if (secondVisitor) assertNoFirstVisitor(request.messages().stream()
                    .map(PromptMessage::getContent).reduce("", (left, right) -> left + "\n" + right));
            String answer = scripted.poll();
            assertNotNull(answer, "Unexpected inference: sensory refreshes must not spend another task action");
            return answer;
        });
        when(gateway.create(any())).thenAnswer(call -> {
            var peer = new Peer(call.getArgument(0)); peers.add(peer);
            return new LiveSessionGateway.Session(peer.providerId, "v=0 synthetic-answer");
        });
        when(gateway.attach(anyString(), any(), any())).thenAnswer(call -> {
            String providerId = call.getArgument(0);
            Peer peer = peers.stream().filter(value -> value.providerId.equals(providerId)).findFirst().orElseThrow();
            Consumer<JsonObject> receive = call.getArgument(1);
            return new LiveSessionGateway.Connection() {
                public boolean isOpen() { return !peer.closed; }
                public void close() { peer.closed = true; }
                public void send(JsonObject event) {
                    peer.sent.add(event.deepCopy());
                    String type = event.get("type").getAsString();
                    var ack = new JsonObject();
                    ack.addProperty("type", type.equals("session.close") ? "session.closed"
                            : type.endsWith(".append") ? type + "ed" : type + "d");
                    if (event.has("event_id")) ack.add("client_event_id", event.get("event_id"));
                    receive.accept(ack);
                }
            };
        });
    }

    @Test void twoVisitorsExploreGamesAndTasksWithoutInheritingConversationOrAnUnfinishedAgreement() {
        code = UUID.randomUUID().toString().substring(0, 5);
        var access = admin.createAccessCode(code, true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of(GalaDinnerReception.KEY));
        id = demo.createAgent(code, GalaDinnerReception.KEY, "ROBOT").getID();
        assertEquals("GIGI", loaded().getPersonaName());
        assertTrue(history().isEmpty(), "Creation must not greet an empty entrance");
        Encounter first = null, second = null;
        try {
            // Visitor A: information, expressive feedback, RPS and an activated custom task.
            first = arrive();
            assertTrue(first.peer().startup.instructions().contains("20:50 main course"));
            nativeSpeech(first, "Grüezi, ich bin GIGI vom SIRA Lab der ZHAW. Willkommen zum Gala Dinner!");
            say(first, "Ich heisse " + FIRST_NAME + ". Meine Bitte: " + FIRST_PREFERENCE + ".", control("CHAT"));
            nativeSpeech(first, "Grüezi " + FIRST_NAME + ", schön sind Sie da.");
            say(first, "Wann gibt es den Hauptgang?", control("CHAT"));
            nativeSpeech(first, "Laut Programm gibt es den Hauptgang um 20:50.");
            int calls = requests.size();
            observe(Event.TYPE_FACE_EMOTION, "{\"emotion\":\"happy\",\"confidence\":0.95,\"ts\":\"" + Instant.now() + "\"}");
            assertNull(lastPlan().getSpeech());
            assertEquals("gentleSmile", lastPlan().getNonVerbal().getAsJsonObject()
                    .getAsJsonObject("facialExpression").get("type").getAsString());
            assertEquals(calls, requests.size());

            say(first, "Spielen wir Schere, Stein, Papier.", control("START_RPS"));
            hearAnnouncement(first);
            assertEquals("rock", loaded().getStorage().get("gala.sign").getAsString());
            hand("paper", Instant.now());
            assertEquals("user", lastPlan().getDisplay().getAsJsonObject().get("winner").getAsString());
            hearAnnouncement(first); assertReception();

            String firstSpec = spec(FIRST_GOAL, "rock", 3, FIRST_REACTION);
            say(first, "Gestalten wir meine persönliche Reaktion auf Stein.", control("START_TASK"),
                    update("PROPOSE", firstSpec, "Vorschlag " + FIRST_GOAL + ": Auf Stein folgt " + FIRST_REACTION + ". Aktivieren?"));
            hearAnnouncement(first);
            assertFalse(loaded().getStorage().containsKey(TaskMemory.SPEC));
            say(first, "Ja, aktiviere meinen Vorschlag.", update("ACTIVATE", "null", "Aktiviert: " + FIRST_GOAL + ". Ich warte auf Stein."));
            hearAnnouncement(first);
            assertEquals(FIRST_GOAL, loaded().getStorage().get(TaskMemory.SPEC).getAsJsonObject().get("goal").getAsString());
            waitForCue();
            scripted.add(BehaviourPlan.speechOnly(FIRST_REACTION).toJson());
            hand("rock", Instant.now()); hearAnnouncement(first);
            assertEquals(2, loaded().getStorage().get(TaskMemory.ACTIONS).getAsInt());
            assertEquals("Gala co-created task", loaded().getCurrentState().getName());

            // Admit a receipt, but defer its segment until after the old session has ended.
            Segment lateFirstTurn = receipt(first, "Starte noch einmal mein Spiel, " + FIRST_NAME);
            List<String> oldStatePath = loaded().getCurrentState().getActiveStatePath();
            group(0);
            assertReception(); assertTaskCleared();
            assertTrue(live.close(code, id, first.owner().sessionId()).orElseThrow().finalized());
            assertTrue(first.peer().closed);
            firstEventIds = history().stream().map(Event::getId).collect(java.util.stream.Collectors.toSet());
            String retained = history().stream().map(Event::getPayload).reduce("", String::concat);
            FIRST_MARKERS.forEach(marker -> assertTrue(retained.contains(marker), "Prior history must remain stored: " + marker));

            // Visitor B starts from reception in a genuinely separate provider session.
            secondVisitor = true;
            second = arrive();
            assertNotEquals(first.owner().sessionId(), second.owner().sessionId());
            assertNotEquals(first.peer().providerId, second.peer().providerId);
            assertNoFirstVisitor(second.peer().startup.instructions() + second.peer().startup.input());
            assertCurrentContextIsolated();
            assertTrue(ingress.history(id, second.owner().sessionId()).isEmpty());
            nativeSpeech(second, "Grüezi, ich bin GIGI. Willkommen zum Gala Dinner!");
            say(second, "Ich heisse " + SECOND_NAME + ". Was weisst du über mich?", control("CHAT"));
            nativeSpeech(second, "Sie haben mir gerade Ihren Namen genannt, " + SECOND_NAME + ".");
            say(second, "Was macht das SIRA Lab?", control("CHAT"));
            nativeSpeech(second, "Das SIRA Lab erforscht sozial intelligente und verantwortungsvolle Agenten.");

            // Both visitors try RPS; stale speech and a delayed old hand frame cannot affect B's round.
            say(second, "Ich möchte auch Schere, Stein, Papier spielen.", control("START_RPS"));
            hearAnnouncement(second);
            assertEquals("scissor", loaded().getStorage().get("gala.sign").getAsString());
            calls = requests.size();
            assertTrue(ingress.commit(id, first.owner(), lateFirstTurn, oldStatePath).isEmpty());
            assertFalse(ingress.receipt(id, first.owner(), fragment("Noch eine alte Eingabe " + FIRST_NAME)));
            assertTrue(recording.record(id, first.owner(), "late-old-voice", FIRST_REACTION,
                    List.of(), SpeechProvenance.Association.NONE, true).isEmpty());
            contexts.closed(id, first.owner());
            Instant roundStart = Instant.parse(loaded().getStorage().get("gala.started").getAsString());
            hand("rock", roundStart.minusMillis(1));
            assertEquals("Gala rock-scissor-paper", loaded().getCurrentState().getName());
            assertEquals(calls, requests.size());
            hand("paper", Instant.now());
            assertEquals("agent", lastPlan().getDisplay().getAsJsonObject().get("winner").getAsString());
            hearAnnouncement(second); assertReception(); assertCurrentContextIsolated();

            // B tries the remaining game, then creates a different custom agreement.
            say(second, "Jetzt möchte ich eine Person erraten.", control("START_GUESS"));
            hearAnnouncement(second);
            assertEquals("Charlie Chaplin", loaded().getStorage().get("gala.person").getAsString());
            assertFalse(contexts.snapshot(code, id).orElseThrow().instructions().contains("Charlie Chaplin"));
            say(second, "Bist du ein Schauspieler?", control("YES"));
            assertEquals("Ja.", hearAnnouncement(second));
            say(second, "Bist du Charlie Chaplin?");
            assertTrue(hearAnnouncement(second).startsWith("Ja, genau: Charlie Chaplin!"));
            assertReception();

            String secondSpec = spec("BETA-PAPER-AGREEMENT", "paper", 2, "Ein neuer Witz für " + SECOND_NAME);
            say(second, "Gestalten wir eine neue Aufgabe mit Papier.", control("START_TASK"),
                    update("PROPOSE", secondSpec, "Vorschlag: eine kurze Reaktion auf Papier. Soll ich sie aktivieren?"));
            hearAnnouncement(second);
            assertEquals(JsonParser.parseString(secondSpec), loaded().getStorage().get(TaskMemory.DRAFT));
            assertFalse(loaded().getStorage().containsKey(TaskMemory.SPEC));
            say(second, "Ja, aktiviere diesen Papier-Vorschlag.", update("ACTIVATE", "null", "Die neue Aufgabe ist aktiv. Ich warte auf Papier."));
            hearAnnouncement(second);
            assertEquals(JsonParser.parseString(secondSpec), loaded().getStorage().get(TaskMemory.SPEC));
            assertEquals(1, loaded().getStorage().get(TaskMemory.ACTIONS).getAsInt(), "B gets a fresh action budget");
            waitForCue();
            calls = requests.size();
            hand("rock", Instant.now());
            assertEquals(calls, requests.size(), "A's trigger must not activate B's agreement");
            assertEquals(1, loaded().getStorage().get(TaskMemory.ACTIONS).getAsInt());
            scripted.add(BehaviourPlan.speechOnly("Ein neuer Witz für " + SECOND_NAME).toJson());
            hand("paper", Instant.now());
            assertTrue(hearAnnouncement(second).endsWith("Aufgabe abgeschlossen."));
            assertReception(); assertTaskCleared(); assertCurrentContextIsolated();

            // Inspect what actually crossed the second provider boundary, not just mocked answers.
            assertNoFirstVisitor(second.peer().sent.toString());
            assertTrue(second.peer().sent.stream().anyMatch(event -> event.toString().contains("BETA-PAPER-AGREEMENT")));
            assertTrue(requests.stream().filter(request -> request.purpose() == InferencePurpose.EXTRACTION)
                    .anyMatch(request -> request.messages().stream().anyMatch(message -> message.getContent().contains(SECOND_NAME))));
            assertTrue(history().stream().map(Event::getId).collect(java.util.stream.Collectors.toSet()).containsAll(firstEventIds),
                    "Isolation must not be achieved by deleting the previous visitor's audit history");
            assertEquals(2, peers.size(), "Stable presence and activity changes do not create new encounters");
            assertTrue(scripted.isEmpty());
            verifyNoInteractions(speech);
        } finally {
            if (second != null) live.close(code, id, second.owner().sessionId());
            if (first != null) live.close(code, id, first.owner().sessionId());
        }
        assertReception(); assertTaskCleared();
    }

    private Encounter arrive() {
        group(1); group(1);
        var session = live.create(code, id, new LiveSessionRequest("v=0 synthetic-offer", "marin")).orElseThrow();
        var owner = new ExternalSpeech(session.handle(), live.status(code, id, session.handle()).orElseThrow().epoch());
        Peer peer = peers.getLast();
        assertEquals(1, peer.startup.input().asList().stream()
                .filter(message -> message.toString().contains("greet the visible visitors once")).count());
        assertReception(); assertTaskCleared();
        return new Encounter(owner, peer);
    }
    private void say(Encounter encounter, String text, String... answers) {
        group(1); // The live camera keeps presence fresh during the journey.
        scripted.addAll(List.of(answers));
        Segment segment = receipt(encounter, text);
        var outcome = ingress.commit(id, encounter.owner(), segment, loaded().getCurrentState().getActiveStatePath()).orElseThrow();
        assertEquals("COMPLETE", outcome.status(), text);
        assertEquals("acknowledged", outcome.reason());
        assertTrue(scripted.isEmpty(), "Every scripted response must correspond to an actual request");
        if (secondVisitor) assertCurrentContextIsolated();
    }
    private Segment receipt(Encounter encounter, String text) {
        Fragment fragment = fragment(text);
        assertTrue(ingress.receipt(id, encounter.owner(), fragment));
        return new Segment(UUID.randomUUID(), Speaker.USER, List.of(fragment), Closure.COMPLETE,
                "observed_silence", fragment.receivedMs(), fragment.receivedMs());
    }
    private Fragment fragment(String text) {
        int sequence = ++receiptSequence;
        return new Fragment("journey-" + sequence, Speaker.USER, sequence * 1000L, sequence * 1000L + 500,
                Instant.now().toEpochMilli(), sequence, text);
    }
    private String hearAnnouncement(Encounter encounter) {
        Event intent = history().stream().filter(ConversationProjection::isIntent)
                .filter(event -> encounter.owner().sessionId().equals(event.speechProvenance().sessionId()))
                .filter(event -> BehaviourPlan.fromJson(event.getPayload()).getSpeech() != null)
                .reduce((first, last) -> last).orElseThrow();
        String text = BehaviourPlan.fromJson(intent.getPayload()).getSpeech();
        await().atMost(Duration.ofSeconds(12)).until(() -> encounter.peer().sent.stream().anyMatch(event ->
                "session.commentary.append".equals(event.get("type").getAsString()) && event.toString().contains(intent.getId().toString())));
        assertTrue(recording.record(id, encounter.owner(), UUID.randomUUID().toString(), text,
                List.of(intent.getId()), SpeechProvenance.Association.CONFIRMED, true).isPresent());
        return text;
    }
    private void nativeSpeech(Encounter encounter, String text) {
        assertTrue(recording.record(id, encounter.owner(), UUID.randomUUID().toString(), text,
                List.of(), SpeechProvenance.Association.NONE, true).isPresent());
    }
    private void waitForCue() {
        group(1);
        await().atMost(Duration.ofSeconds(6)).until(() -> {
            var status = TaskCueStatus.of(loaded(), Instant.now());
            return status != null && status.responseCaptured() && status.nextCueInMs() == 0;
        });
    }
    private void hand(String sign, Instant time) {
        observe(Event.TYPE_HAND_SIGN, "{\"sign\":\"" + sign + "\",\"confidence\":0.99,\"ts\":\"" + time + "\"}");
    }
    private void group(int count) {
        observe(Event.TYPE_SOCIAL_GROUPING, "{\"humanCount\":" + count + ",\"ts\":\"" + Instant.now() + "\"}");
    }
    private void observe(String type, String payload) {
        demo.acknowledge(code, id, new EventRequest(type, "sensor", Event.KIND_OBSERVATION, payload), OutputProfile.FULL_PLAN);
    }
    private Agent loaded() { return agents.findById(id).orElseThrow(); }
    private List<Event> history() { return loaded().getEventHistory().toList(); }
    private BehaviourPlan lastPlan() { return BehaviourPlan.fromJson(history().getLast().getPayload()); }
    private void assertReception() { assertEquals("Gala reception", loaded().getCurrentState().getName()); }
    private void assertTaskCleared() {
        assertTrue(loaded().getStorage().keySet().stream().noneMatch(key -> key.startsWith("task.")));
        assertFalse(loaded().getStorage().containsKey("gala.person"));
        assertFalse(loaded().getStorage().containsKey("gala.sign"));
    }
    private void assertCurrentContextIsolated() {
        var snapshot = contexts.snapshot(code, id).orElseThrow();
        assertNoFirstVisitor(snapshot.instructions() + snapshot.startupInput());
        assertTrue(snapshot.items().stream().flatMap(item -> item.sourceIds().stream()).noneMatch(firstEventIds::contains));
    }
    private static void assertNoFirstVisitor(String content) {
        FIRST_MARKERS.forEach(marker -> assertFalse(content.contains(marker), "Prior visitor context leaked: " + marker));
    }
    private static String control(String operation) {
        return "{\"operation\":\"" + operation + "\",\"gesture\":\"NONE\",\"expression\":\"attentive\",\"gaze\":\"toward_user\"}";
    }
    private static String update(String operation, String spec, String speech) {
        return "{\"operation\":\"" + operation + "\",\"task\":" + spec + ",\"reply\":" + BehaviourPlan.speechOnly(speech).toJson() + "}";
    }
    private static String spec(String goal, String sign, int actions, String response) {
        return new TaskSpec(goal, actions, List.of(new TaskSpec.Rule(Event.TYPE_HAND_SIGN, "sign", "eq",
                new JsonPrimitive(sign), response, TaskSpec.Effect.ACT, .8, 1, 3)), true).json().toString();
    }
}
