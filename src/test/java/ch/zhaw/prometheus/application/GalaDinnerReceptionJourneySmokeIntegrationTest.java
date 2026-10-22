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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import fixtures.behaviour.TaskReplyFixture;

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
    @Autowired ExternalSpeechOwnership ownership;
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
    private boolean compact;

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
            if (request.messages().getFirst().getContent().startsWith("Determine whether the captured assistant speech")) {
                var response = JsonParser.parseString(request.messages().getLast().getContent()).getAsJsonObject();
                response.remove("confirmedTaskFacts"); response.remove("capturedSpeech"); response.addProperty("verdict", "MATCH");
                return response.toString();
            }
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exitConfirmationSurvivesReloadAndBareRepliesKeepOrEndTheCorrectGame(boolean compact) {
        this.compact = compact;
        code = UUID.randomUUID().toString().substring(0, 5);
        var access = admin.createAccessCode(code, true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of(GalaDinnerReception.KEY));
        id = demo.createAgent(code, GalaDinnerReception.KEY, "ROBOT").getID();
        var encounter = arrive();
        try {
            say(encounter, "Personenraten bitte", control("START_GUESS")); hearAnnouncement(encounter);
            say(encounter, "Was macht das IWI?", control("CHECK_EXIT"));
            assertEquals("Albert Einstein", loaded().getStorage().get("gala.person").getAsString());
            assertTrue(hearAnnouncement(encounter).contains("Exit confirmation pending"));
            capturedSpeech(encounter, "Soll ich das Spiel beenden und dir vom IWI erzählen?");
            int calls = requests.size();
            say(encounter, "Ja");
            assertEquals(calls, requests.size()); assertReception();
            var facts = contexts.snapshot(code, id).orElseThrow().activity().continuation().facts();
            assertTrue(facts.contains("Was macht das IWI?")); assertFalse(facts.contains("Albert Einstein"));
            assertFalse(loaded().getStorage().containsKey("gala.exit_request"));
            assertFalse(loaded().getStorage().containsKey("gala.outcome"));
            hearAnnouncement(encounter);

            say(encounter, "Schere Stein Papier", control("START_RPS"));
            String chosen = loaded().getStorage().get("gala.sign").getAsString();
            assertEquals(chosen, lastPlan().getMotion().getAsJsonObject().get("handSign").getAsString());
            String initialCapture = lastPlan().getDisplay().getAsJsonObject().get("captureId").getAsString();
            assertEquals(initialCapture, loaded().getStorage().get("gala.rps_capture_id").getAsString());
            hearAnnouncement(encounter);
            say(encounter, "Wann beginnt das Essen?", control("CHECK_EXIT"));
            hearAnnouncement(encounter);
            Instant paused = Instant.now(); hand("paper", paused);
            assertEquals("RPS", loaded().getStorage().get("gala.mode").getAsString());
            capturedSpeech(encounter, "Soll ich das Spiel beenden und deine Frage zum Essen beantworten?");
            calls = requests.size(); say(encounter, "Nein");
            assertEquals(calls, requests.size());
            assertEquals(chosen, loaded().getStorage().get("gala.sign").getAsString());
            String resumedCapture = lastPlan().getDisplay().getAsJsonObject().get("captureId").getAsString();
            assertNotEquals(initialCapture, resumedCapture);
            hearAnnouncement(encounter);
            hand("paper", paused);
            assertEquals("RPS", loaded().getStorage().get("gala.mode").getAsString());
            hand("paper", Instant.now().plusSeconds(1), initialCapture);
            assertEquals("RPS", loaded().getStorage().get("gala.mode").getAsString());
            hand("paper", Instant.now(), resumedCapture); assertReception();
            assertNull(lastPlan().getMotion());
            assertEquals(chosen, lastPlan().getDisplay().getAsJsonObject().get("agentSign").getAsString());
            assertEquals(resumedCapture, lastPlan().getDisplay().getAsJsonObject().get("captureId").getAsString());
            assertTrue(hearAnnouncement(encounter).contains("RPS round completed"));
            String outcome = loaded().getStorage().get("gala.continuation_id").getAsString();
            hand("paper", Instant.now(), resumedCapture);
            assertEquals(outcome, loaded().getStorage().get("gala.continuation_id").getAsString());

            say(encounter, "Noch einmal Schere Stein Papier", control("START_RPS"));
            String nextCapture = lastPlan().getDisplay().getAsJsonObject().get("captureId").getAsString();
            assertNotEquals(resumedCapture, nextCapture); hearAnnouncement(encounter);
            hand("paper", Instant.now(), resumedCapture);
            assertEquals("RPS", loaded().getStorage().get("gala.mode").getAsString());
            hand("paper", Instant.now(), nextCapture); assertReception();
            assertTrue(hearAnnouncement(encounter).contains("RPS round completed"));
        } finally {
            live.close(code, id, encounter.owner().sessionId());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void twoVisitorsExploreGamesAndTasksWithoutInheritingConversationOrAnUnfinishedAgreement(boolean compact) {
        this.compact = compact;
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
            String suppliedReference = first.peer().startup.instructions();
            for (String fact : List.of("20:50 Hauptgang", "18:30 Seating", "Triple Crown",
                    "Theresa Schmiedel", "Socially Intelligent and Responsible Agents",
                    "CHF 250 im ersten Jahr", "ich bin djee-djee"))
                assertTrue(suppliedReference.contains(fact), fact);
            assertFalse(suppliedReference.contains("https://"));
            nativeSpeech(first, "Grüezi, ich bin djee-djee vom SIRA Lab der ZHAW. Willkommen zum Gala Dinner!");
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

            // Replay the second headset trial's ambiguous request. Even a prior RPS mention is no choice.
            nativeSpeech(first, "Ich kann kleine Spiele anbieten, etwa Schere, Stein, Papier.");
            calls = requests.size();
            say(first, "Die Können wir etwas spielen");
            assertEquals(calls, requests.size());
            assertReception(); assertTaskCleared();
            nativeSpeech(first, "Personenraten oder Schere, Stein, Papier?");
            // A failed control response cannot advertise a ready game or silently retry.
            say(first, "Personenraten bitte.", "not json");
            assertReception(); assertTaskCleared();
            assertTrue(contexts.snapshot(code, id).orElseThrow().activity().continuation().facts().contains("failed"));
            calls = requests.size(); group(1);
            assertEquals(calls, requests.size());
            nativeSpeech(first, "Das Spiel ist noch nicht gestartet. Bitte wählen Sie es erneut.");
            say(first, "Wie wäre es mit einem Ratespiel?", control("START_GUESS"));
            hearAnnouncement(first);
            assertEquals("Gala guess the person", loaded().getCurrentState().getName());
            assertFalse(contexts.snapshot(code, id).orElseThrow().instructions().contains("Albert Einstein"));
            var guessing = contexts.snapshot(code, id).orElseThrow().activity();
            assertTrue(guessing.facts().contains("Albert Einstein"));
            assertTrue(guessing.facts().contains("not alive"));
            calls = requests.size();
            say(first, "Ja, gerne.");
            say(first, "Okay"); // Recorded assistant segment can still belong to the preceding state.
            say(first, "Alles klar, danke.", control("WAIT")); // Free-form wording retains the ordinary classifier.
            assertEquals(calls + 1, requests.size());
            assertEquals("Albert Einstein", loaded().getStorage().get("gala.person").getAsString());
            long intentsBefore = speechIntents();
            for (String question : List.of("Lebt die Person noch?", "Hat die Person etwas erfunden?",
                    "Hatte die Person Schuhgrösse 42?", "Lebt die", "Was passiert, wenn ich aufgebe?")) {
                say(first, question, control("WAIT"));
                assertEquals(intentsBefore, speechIntents(), "Live questions must not create competing backend speech");
                assertEquals(guessing.continuation().id(), contexts.snapshot(code, id).orElseThrow().activity().continuation().id());
            }
            nativeSpeech(first, "Wenn Sie aufgeben, verrate ich die gesuchte Person.");
            assertEquals("Albert Einstein", loaded().getStorage().get("gala.person").getAsString());
            var giveUp = say(first, "Ich gebe auf, kannst du mir sagen, wenn du herausgedenkt hast", control("GIVE_UP"));
            assertEquals(intentsBefore, speechIntents());
            assertNull(contexts.snapshot(code, id).orElseThrow().activity().continuation());
            assertTrue(contexts.snapshot(code, id).orElseThrow().activity().facts()
                    .contains("give_up; round ended; permitted reveal: Albert Einstein"));
            var outcomeIdentity = loaded().getStorage().get("gala.outcome_id").getAsString();
            nativeSpeech(first, "Es war Albert Einstein.");
            assertReception();
            assertFalse(loaded().getStorage().containsKey("gala.person"));
            assertFalse(loaded().getStorage().containsKey("gala.started"));
            int afterReveal = history().size(), afterRevealCalls = requests.size();
            assertEquals("COMPLETE", ingress.commit(id, first.owner(), giveUp,
                    loaded().getCurrentState().getActiveStatePath()).orElseThrow().status());
            assertEquals(afterReveal, history().size(), "Duplicate give-up must not create a second reveal");
            assertEquals(afterRevealCalls, requests.size());
            assertEquals(outcomeIdentity, loaded().getStorage().get("gala.outcome_id").getAsString());

            say(first, "Albert Einstein");
            assertEquals(afterRevealCalls, requests.size(), "A repeated revealed name needs no classification");
            say(first, "Hat er in der Schweiz gelebt?", control("CHAT"));
            assertReception(); assertNull(contexts.snapshot(code, id).orElseThrow().activity().continuation());
            assertEquals(outcomeIdentity, loaded().getStorage().get("gala.outcome_id").getAsString());
            assertTrue(requests.getLast().messages().stream().anyMatch(message ->
                    message.getContent().startsWith("Recent completed guessing round") && message.getContent().contains("Albert Einstein")));

            say(first, "Spielen wir Schere, Stein, Papier.", control("START_RPS"));
            assertFalse(loaded().getStorage().containsKey("gala.outcome"));
            hearAnnouncement(first);
            assertEquals("scissor", loaded().getStorage().get("gala.sign").getAsString());
            calls = requests.size();
            say(first, "[laugh]");
            assertEquals(calls, requests.size());
            assertEquals("Gala rock-scissor-paper", loaded().getCurrentState().getName());
            assertFalse(loaded().getStorage().containsKey("gala.quiet"));
            say(first, "Einen Moment bitte", control("WAIT"));
            assertEquals("Gala rock-scissor-paper", loaded().getCurrentState().getName());
            hand("paper", Instant.now());
            assertEquals("agent", lastPlan().getDisplay().getAsJsonObject().get("winner").getAsString());
            assertTrue(hearAnnouncement(first).contains("winner: agent"));
            assertReception();
            calls = requests.size();
            say(first, "Okay");
            assertEquals(calls, requests.size()); assertReception();
            long results = history().stream().filter(event -> event.getPayload().contains("\"phase\":\"round_result\"")).count();
            hand("paper", Instant.now());
            assertEquals(results, history().stream().filter(event -> event.getPayload().contains("\"phase\":\"round_result\"")).count());

            String firstSpec = spec(FIRST_GOAL, "rock", 3, "Sage " + FIRST_REACTION + " und zeige Papier.");
            say(first, "Gestalten wir meine persönliche Reaktion auf Stein.", control("START_TASK"),
                    update("PROPOSE", firstSpec, "Vorschlag " + FIRST_GOAL + ": Auf Stein folgt " + FIRST_REACTION + ". Aktivieren?"));
            assertNull(lastPlan().getSpeech());
            assertTrue(contexts.snapshot(code, id).orElseThrow().activity().facts().contains("\"maxActions\":3"));
            assertNull(lastPlan().getMotion());
            hearAnnouncement(first);
            assertFalse(loaded().getStorage().containsKey(TaskMemory.SPEC));
            String initial = FIRST_REACTION + ". Noch höchstens zwei Reaktionen auf Stein.";
            say(first, "Ja, aktiviere meinen Vorschlag.", update("ACTIVATE", "null", initial));
            assertEquals(TaskReplyFixture.plan(null, false), lastPlan().toJsonObject());
            assertEquals("paper", lastPlan().getMotion().getAsJsonObject().get("handSign").getAsString());
            assertEquals(1, loaded().getStorage().get(TaskMemory.ACTIONS).getAsInt());
            hearAnnouncement(first);
            assertEquals(FIRST_GOAL, loaded().getStorage().get(TaskMemory.SPEC).getAsJsonObject().get("goal").getAsString());
            var accepted = loaded().getStorage().get(TaskMemory.SPEC).deepCopy();
            say(first, "Was passiert, wenn ich Pause sage oder weggehe?",
                    update("KEEP", "null", "Dann endet die Aufgabe. Ein Neustart braucht einen neuen Vorschlag."));
            hearAnnouncement(first);
            assertEquals(accepted, loaded().getStorage().get(TaskMemory.SPEC));
            assertEquals(1, loaded().getStorage().get(TaskMemory.ACTIONS).getAsInt());
            assertEquals("RUNNING", loaded().getStorage().get(TaskMemory.PHASE).getAsString());
            waitForCue();
            scripted.add(TaskReplyFixture.plan(FIRST_REACTION, compact).toString());
            hand("rock", Instant.now());
            assertEquals(TaskReplyFixture.plan(null, false), lastPlan().toJsonObject());
            hearAnnouncement(first);
            assertEquals(2, loaded().getStorage().get(TaskMemory.ACTIONS).getAsInt());
            assertEquals("Gala co-created task", loaded().getCurrentState().getName());

            // Admit a receipt, but defer its segment until after the old session has ended.
            Segment lateFirstTurn = receipt(first, "Starte noch einmal mein Spiel, " + FIRST_NAME);
            List<String> oldStatePath = loaded().getCurrentState().getActiveStatePath();
            group(0);
            assertEquals("Gala co-created task", loaded().getCurrentState().getName());
            assertTrue(live.close(code, id, first.owner().sessionId()).orElseThrow().finalized());
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> { assertReception(); assertTaskCleared(); });
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
            nativeSpeech(second, "Grüezi, ich bin djee-djee. Willkommen zum Gala Dinner!");
            say(second, "Ich heisse " + SECOND_NAME + ". Was weisst du über mich?", control("CHAT"));
            nativeSpeech(second, "Sie haben mir gerade Ihren Namen genannt, " + SECOND_NAME + ".");
            say(second, "Was macht das SIRA Lab?", control("CHAT"));
            nativeSpeech(second, "Das SIRA Lab erforscht sozial intelligente und verantwortungsvolle Agenten.");

            // Both visitors try RPS; stale speech and a delayed old hand frame cannot affect B's round.
            say(second, "Ich möchte auch Schere, Stein, Papier spielen.", control("START_RPS"));
            hearAnnouncement(second);
            assertEquals("paper", loaded().getStorage().get("gala.sign").getAsString());
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
            assertEquals("draw", lastPlan().getDisplay().getAsJsonObject().get("winner").getAsString());
            hearAnnouncement(second); assertReception(); assertCurrentContextIsolated();

            // B tries the remaining game, then creates a different custom agreement.
            calls = requests.size(); say(second, "Okay"); assertEquals(calls, requests.size());
            say(second, "Jetzt möchte ich eine Person erraten.", control("START_GUESS"));
            hearAnnouncement(second);
            assertEquals("Ada Lovelace", loaded().getStorage().get("gala.person").getAsString());
            assertFalse(contexts.snapshot(code, id).orElseThrow().instructions().contains("Ada Lovelace"));
            calls = requests.size(); say(second, "Ja, gerne."); assertEquals(calls, requests.size());
            var round = loaded().getStorage().get("gala.round").deepCopy();
            int sessionsBefore = peers.size();
            group(0);
            assertEquals("Ada Lovelace", loaded().getStorage().get("gala.person").getAsString());
            group(1);
            assertEquals(round, loaded().getStorage().get("gala.round"));
            assertEquals(sessionsBefore, peers.size()); assertTrue(ownership.isCurrent(id, second.owner()));
            say(second, "Bist du eine Mathematikerin?", control("WAIT"));
            nativeSpeech(second, "Ja.");
            say(second, "Ich gebe auf");
            assertNull(contexts.snapshot(code, id).orElseThrow().activity().continuation());
            nativeSpeech(second, "Es war Ada Lovelace!");
            assertReception();
            observe(Event.TYPE_FACE_EMOTION, "{\"emotion\":\"neutral\",\"confidence\":0.99,\"ts\":\"" + Instant.now() + "\"}");
            say(second, "Wie wirkt mein Gesichtsausdruck?", control("CHAT"));
            nativeSpeech(second, "Dein Ausdruck wirkt gerade eher neutral.");
            observe(Event.TYPE_FACE_EMOTION, "{\"emotion\":\"happy\",\"confidence\":0.99,\"ts\":\"" + Instant.now() + "\"}");
            calls = requests.size(); say(second, "Und jetzt?"); assertEquals(calls, requests.size());
            var face = contexts.snapshot(code, id).orElseThrow().items().stream()
                    .filter(item -> Event.TYPE_FACE_EMOTION.equals(item.type())).findFirst().orElseThrow();
            assertTrue(face.text().contains("happy")); assertEquals("fresh", face.freshness());
            nativeSpeech(second, "Jetzt wirkt dein Gesicht eher fröhlich.");
            assertReception(); assertCurrentContextIsolated();

            var secondTerms = JsonParser.parseString(spec("BETA-PAPER-AGREEMENT", "paper", 2,
                    "Ein neuer Witz für " + SECOND_NAME)).getAsJsonObject();
            secondTerms.addProperty("firstAction", "ON_CUE");
            String secondSpec = secondTerms.toString();
            say(second, "Gestalten wir eine neue Aufgabe mit Papier.", control("START_TASK"),
                    update("PROPOSE", secondSpec, "Vorschlag: eine kurze Reaktion auf Papier. Soll ich sie aktivieren?"));
            hearAnnouncement(second);
            assertEquals(JsonParser.parseString(secondSpec), loaded().getStorage().get(TaskMemory.DRAFT));
            assertFalse(loaded().getStorage().containsKey(TaskMemory.SPEC));
            say(second, "Ja, aktiviere diesen Papier-Vorschlag.", update("ACTIVATE", "null", "Die neue Aufgabe ist aktiv. Ich warte auf Papier."));
            hearAnnouncement(second);
            assertEquals(JsonParser.parseString(secondSpec), loaded().getStorage().get(TaskMemory.SPEC));
            assertEquals(0, loaded().getStorage().get(TaskMemory.ACTIONS).getAsInt(), "Future cue must precede B's first action");
            waitForCue();
            calls = requests.size();
            hand("rock", Instant.now());
            assertEquals(calls, requests.size(), "A's trigger must not activate B's agreement");
            assertEquals(0, loaded().getStorage().get(TaskMemory.ACTIONS).getAsInt());
            scripted.add(TaskReplyFixture.plan("Ein neuer Witz für " + SECOND_NAME, compact).toString());
            hand("paper", Instant.now());
            hearAnnouncement(second);
            assertEquals(1, loaded().getStorage().get(TaskMemory.ACTIONS).getAsInt());
            waitForCue();
            scripted.add(TaskReplyFixture.plan("Noch ein Witz für " + SECOND_NAME, compact).toString());
            hand("paper", Instant.now());
            assertTrue(hearAnnouncement(second).contains("final allowed action"));
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
            assertFalse(history().stream().filter(event -> Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType()))
                    .anyMatch(event -> JsonParser.parseString(event.getPayload()).getAsJsonObject().has("nv")));
            assertTrue(requests.stream().filter(request -> request.purpose() == InferencePurpose.EXTRACTION)
                    .filter(request -> !request.messages().getFirst().getContent().startsWith("Determine whether the captured assistant speech"))
                    .allMatch(request -> request.messages().getFirst().getContent().contains("only inside reply")));
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
        assertEquals(2000, live.capabilities(code, id).orElseThrow().departureGraceMs());
        assertEquals(2000L, live.status(code, id, session.handle()).orElseThrow()
                .providerTelemetry().configuration().get("userSilenceMs"));
        var owner = new ExternalSpeech(session.handle(), live.status(code, id, session.handle()).orElseThrow().epoch());
        Peer peer = peers.getLast();
        assertEquals(1, peer.startup.input().asList().stream()
                .filter(message -> message.toString().contains("greet the visible visitors once")).count());
        assertReception(); assertTaskCleared();
        return new Encounter(owner, peer);
    }
    private Segment say(Encounter encounter, String text, String... answers) {
        group(1); // The live camera keeps presence fresh during the journey.
        scripted.addAll(List.of(answers));
        Segment segment = receipt(encounter, text);
        var outcome = ingress.commit(id, encounter.owner(), segment, loaded().getCurrentState().getActiveStatePath()).orElseThrow();
        assertEquals("COMPLETE", outcome.status(), text);
        assertEquals("acknowledged", outcome.reason());
        assertTrue(scripted.isEmpty(), "Every scripted response must correspond to an actual request");
        if (secondVisitor) assertCurrentContextIsolated();
        return segment;
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
        var activity = contexts.snapshot(code, id).orElseThrow().activity();
        if (activity != null && activity.continuation() != null) {
            var occasion = activity.continuation();
            await().atMost(Duration.ofSeconds(12)).until(() -> encounter.peer().sent.stream().anyMatch(event ->
                    "session.commentary.append".equals(event.get("type").getAsString()) && event.toString().contains(occasion.id().toString())));
            assertEquals(1, encounter.peer().sent.stream().filter(event ->
                    "session.commentary.append".equals(event.get("type").getAsString()) && event.toString().contains(occasion.id().toString())).count());
            assertTrue(encounter.peer().sent.stream().noneMatch(event -> "session.instructions.append".equals(event.get("type").getAsString())),
                    "Gala activity changes must be quiet context");
            // Scripted voice is only a persistence fixture; real wording is tested in the provider probe.
            var storage = loaded().getStorage();
            var agreement = storage.get(storage.containsKey(TaskMemory.DRAFT) ? TaskMemory.DRAFT : TaskMemory.SPEC);
            nativeSpeech(encounter, loaded().getStorage().containsKey("gala.task_facts")
                    ? (agreement == null ? "" : agreement.getAsJsonObject().get("goal").getAsString() + ". ")
                            + loaded().getStorage().get("gala.task_facts").getAsString()
                    : occasion.facts().contains("Guessing round started")
                    ? "Ich denke an eine bekannte Person. Stellen Sie mir Ja-Nein-Fragen."
                    : occasion.facts().contains("RPS round started") ? "Zeigen Sie jetzt Ihr Zeichen." : "Die Runde ist beendet.");
            return occasion.facts() + "\n" + activity.facts();
        }

        Event intent = history().stream().filter(ConversationProjection::isIntent)
                .filter(event -> encounter.owner().sessionId().equals(event.speechProvenance().sessionId()))
                .filter(event -> BehaviourPlan.fromJson(event.getPayload()).getSpeech() != null)
                .reduce((first, last) -> last).orElseThrow();
        String text = BehaviourPlan.fromJson(intent.getPayload()).getSpeech();
        await().atMost(Duration.ofSeconds(12)).until(() -> encounter.peer().sent.stream().anyMatch(event ->
                "session.commentary.append".equals(event.get("type").getAsString()) && event.toString().contains(intent.getId().toString())));
        assertTrue(encounter.peer().sent.stream().noneMatch(event ->
                "session.instructions.append".equals(event.get("type").getAsString()) && event.toString().contains("GALA REFERENZ")),
                "Activity changes must not resend the stable reference before narration");
        assertTrue(encounter.peer().sent.stream().noneMatch(event -> event.toString().contains("ich bin djee-djee")),
                "Activity updates must not send another opening greeting");
        assertEquals(1, encounter.peer().sent.stream().filter(event ->
                "session.commentary.append".equals(event.get("type").getAsString()) && event.toString().contains(intent.getId().toString())).count(),
                "A confirmed result must be announced only once");
        assertTrue(recording.record(id, encounter.owner(), UUID.randomUUID().toString(), text,
                List.of(intent.getId()), SpeechProvenance.Association.CONFIRMED, true).isPresent());
        return text;
    }
    private long speechIntents() {
        return history().stream().filter(ConversationProjection::isIntent)
                .filter(e -> BehaviourPlan.fromJson(e.getPayload()).getSpeech() != null).count();
    }
    private void nativeSpeech(Encounter encounter, String text) {
        boolean pending = loaded().getStorage().containsKey(TaskResponse.KEY);
        assertTrue(recording.record(id, encounter.owner(), UUID.randomUUID().toString(), text,
                List.of(), SpeechProvenance.Association.NONE, true, Instant.now()).isPresent());
        if (pending) await().atMost(Duration.ofSeconds(8)).until(() -> loaded().getStorage()
                .get(TaskResponse.KEY).getAsJsonObject().get("status").getAsString().equals("CAPTURED"));
    }
    private void capturedSpeech(Encounter encounter, String text) {
        int sequence = ++receiptSequence;
        var fragment = new Fragment("journey-assistant-" + sequence, Speaker.ASSISTANT,
                sequence * 1000L, sequence * 1000L + 500, Instant.now().toEpochMilli(), sequence, text);
        assertTrue(ingress.receipt(id, encounter.owner(), fragment));
        var segment = new Segment(UUID.randomUUID(), Speaker.ASSISTANT, List.of(fragment), Closure.COMPLETE,
                "observed_silence", fragment.receivedMs(), fragment.receivedMs());
        assertEquals("COMPLETE", ingress.commit(id, encounter.owner(), segment,
                loaded().getCurrentState().getActiveStatePath()).orElseThrow().status());
    }
    private void waitForCue() {
        group(1);
        await().atMost(Duration.ofSeconds(6)).until(() -> {
            var status = TaskCueStatus.of(loaded(), Instant.now());
            return status != null && status.responseCaptured() && status.nextCueInMs() == 0;
        });
    }
    private void hand(String sign, Instant time) {
        var capture = loaded().getStorage().get("gala.rps_capture_id");
        hand(sign, time, capture == null ? "" : capture.getAsString());
    }
    private void hand(String sign, Instant time, String captureId) {
        observe(Event.TYPE_HAND_SIGN, "{\"sign\":\"" + sign + "\",\"confidence\":0.99,\"captureId\":\"" + captureId + "\",\"ts\":\"" + time + "\"}");
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
    private String update(String operation, String spec, String speech) {
        return TaskReplyFixture.update(operation, spec, speech, compact);
    }
    private static String spec(String goal, String sign, int actions, String response) {
        return new TaskSpec(goal, actions, List.of(new TaskSpec.Rule(Event.TYPE_HAND_SIGN, "sign", "eq",
                new JsonPrimitive(sign), response, TaskSpec.Effect.ACT, .8, 1, 3)), true).json().toString();
    }
}
