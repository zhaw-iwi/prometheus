package fixtures.live;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import com.google.gson.*;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import ch.zhaw.prometheus.agentdefs.core.CoordinationAgent;
import ch.zhaw.prometheus.logging.LatencyTrace;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.spi.*;

/** Real production graph/actions/gateway with synthetic inputs. No Spring, database, media or deployment. */
final class CoordinationProviderSmoke {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final JsonObject report = new JsonObject();
    private final JsonArray requests = new JsonArray(), scenarios = new JsonArray();
    private final long deadline = System.nanoTime() + Duration.ofMinutes(5).toNanos();
    private final PolicyRuntime runtime;
    private Agent agent;
    private Storage storage;
    private JsonArray turns;

    private CoordinationProviderSmoke() throws Exception {
        // Same checked-in routes as the deployed profile; local overrides and an environment key are explicit.
        var values = new HashMap<String, Object>();
        for (String file : List.of("openai-prod.properties", "openai.properties", "application.properties")) {
            var path = Path.of("src/main/resources", file); if (!Files.exists(path)) continue;
            var properties = new Properties(); try (var reader = Files.newBufferedReader(path)) { properties.load(reader); }
            properties.forEach((key, value) -> { if (key.toString().startsWith("openai.")) values.put(key.toString(), value); });
        }
        String key = System.getenv("OPENAI_API_KEY");
        if (key != null) values.put("openai.key", key.replaceFirst("^Bearer ", ""));
        String configuredKey = Objects.toString(values.get("openai.key"), "");
        if (configuredKey.isBlank() || configuredKey.contains("${"))
            throw new IllegalStateException("Supply OPENAI_API_KEY or ignored local openai.key before a paid probe");
        var properties = new Binder(new MapConfigurationPropertySource(values)).bind("openai", Bindable.of(OpenAIProperties.class))
                .orElseThrow(() -> new IllegalStateException("Missing OpenAI configuration"));
        properties.setRequestTimeoutMs(Math.min(30000, properties.getRequestTimeoutMs()));
        properties.setMaxCompletionTokens(2048);
        for (var route : properties.getRoutes().values()) {
            route.setTimeoutMs(30000); route.setMaxCompletionTokens(2048);
        }
        var routes = new JsonObject();
        for (var purpose : List.of(InferencePurpose.EXTRACTION, InferencePurpose.BEHAVIOUR)) {
            var route = InferenceRouting.resolve(properties, purpose);
            if (!"https://api.openai.com/v1/chat/completions".equals(route.url()))
                throw new IllegalStateException("This probe requires the configured official OpenAI endpoint");
            routes.add(purpose.name(), JSON.toJsonTree(route));
        }
        report.add("routes", routes); report.add("requests", requests); report.add("scenarios", scenarios);
        report.addProperty("scope", "Real OpenAI text inference and in-memory production agent; persistence, HTTP, Realtime audio and physical sensing NOT tested");
        report.addProperty("maxRequests", 14); report.addProperty("maxSeconds", 300);
        var gateway = spy(new OpenAILanguageModelGateway(properties));
        doAnswer(call -> {
            if (requests.size() >= 14 || System.nanoTime() >= deadline) throw new IllegalStateException("Paid probe budget exhausted");
            int timeout = (int) Math.max(1, Math.min(30000, (deadline - System.nanoTime()) / 1_000_000));
            properties.setRequestTimeoutMs(timeout); properties.getRoutes().values().forEach(route -> route.setTimeoutMs(timeout));
            InferenceRequest request = call.getArgument(0); var entry = new JsonObject(); requests.add(entry);
            entry.addProperty("purpose", request.purpose().name()); entry.add("messages", JSON.toJsonTree(request.messages()));
            try (var trace = new LatencyTrace(null, ignored -> {})) {
                try { String raw = (String) call.callRealMethod(); entry.addProperty("raw", raw); return raw; }
                finally {
                    String timing = LatencyTrace.responseHeader();
                    if (timing != null) entry.add("timing", JsonParser.parseString(new String(Base64.getDecoder().decode(timing), java.nio.charset.StandardCharsets.UTF_8)));
                }
            }
        }).when(gateway).infer(any());
        runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway);
    }

    static void run(Path output) throws Exception {
        var probe = new CoordinationProviderSmoke();
        probe.scenario("preparation-help-acceptance", probe::preparation);
        probe.scenario("riddle-feedback-replacement", probe::dialogue);
        probe.scenario("reviewed-social-count", probe::social);
        boolean passed = probe.scenarios.asList().stream().allMatch(item -> item.getAsJsonObject().get("passed").getAsBoolean());
        probe.report.addProperty("passed", passed);
        Files.createDirectories(output.toAbsolutePath().getParent()); Files.writeString(output, JSON.toJson(probe.report));
        System.out.println("Coordination provider smoke: " + (passed ? "PASS" : "FAIL") + "; " + probe.requests.size() + " requests; " + output);
        if (!passed) throw new AssertionError("A Coordination provider scenario failed; retained evidence: " + output);
    }

    private interface Scenario { void run() throws Exception; }
    private void scenario(String name, Scenario test) {
        agent = new CoordinationAgent().createAgent();
        storage = ((ch.zhaw.prometheus.agentdefs.core.CoordinationPolicy) agent.getCurrentState().ownPolicy()).storage();
        var entry = new JsonObject(); entry.addProperty("name", name); scenarios.add(entry);
        turns = new JsonArray(); entry.add("turns", turns);
        try { test.run(); entry.addProperty("passed", true); }
        catch (Exception | AssertionError failure) {
            entry.addProperty("passed", false); entry.addProperty("error", failure.toString());
        } finally { entry.add("finalStorage", JSON.toJsonTree(storage.toMap())); }
    }

    private String event(Event input) {
        if (System.nanoTime() >= deadline) throw new IllegalStateException("Paid probe time budget exhausted");
        int before = requests.size();
        // JPA normally supplies this timestamp. In-memory histories need it for completed invitations.
        org.springframework.test.util.ReflectionTestUtils.setField(input, "createdDate", Instant.now());
        var response = agent.acknowledge(input, runtime);
        if (response != null) org.springframework.test.util.ReflectionTestUtils.setField(response, "createdDate", Instant.now());
        var entry = new JsonObject(); entry.addProperty("type", input.getType()); entry.addProperty("input", input.getPayload());
        entry.addProperty("response", response == null ? null : response.getPayload());
        entry.addProperty("providerRequests", requests.size() - before); entry.addProperty("stage", CoordinationMemory.stage(storage));
        entry.add("memory", JSON.toJsonTree(storage.toMap())); turns.add(entry);
        return response == null ? "" : Objects.toString(BehaviourPlan.fromJson(response.getPayload()).getSpeech(), "");
    }
    private String say(String text) { return event(Event.observation(Event.TYPE_USER_UTTERANCE, "user", text)); }
    private String observe(String type, String json) {
        var data = JsonParser.parseString(json).getAsJsonObject(); data.addProperty("ts", Instant.now().toString());
        return event(Event.observation(type, "synthetic-camera", data.toString()));
    }
    private int actions() { return Integer.parseInt(TaskMemory.text(storage, TaskMemory.ACTIONS, "0")); }
    private JsonElement value(String key) { return storage.get(key).deepCopy(); }
    private void stage(String expected) { assertEquals(expected, CoordinationMemory.stage(storage)); }
    private void waitForCueWindow() throws InterruptedException {
        long delay = Duration.between(Instant.now(), Instant.parse(storage.get(TaskMemory.AFTER).getAsString())).toMillis();
        assertTrue(delay < 10000, "Unexpected arming delay"); if (delay >= 0) Thread.sleep(delay + 30);
    }
    private void preparation() throws Exception {
        say("Please help me set up a small task: I show rock and judge your response; you say exactly: Welcome to the workshop. Success means I find the greeting useful. One response, only after acceptance and a fresh rock sign. First check that you can recognise rock. Propose it without starting.");
        stage("PREPARATION"); assertEquals(0, actions());
        var draft = value(CoordinationMemory.DRAFT);
        var terms = CoordinationAgreement.parse(draft);
        assertEquals(1, terms.checks().size()); assertEquals("rock", terms.checks().getFirst().value().getAsString());
        assertEquals(1, terms.reactions().maxActions()); assertEquals(TaskSpec.FirstAction.ON_CUE, terms.reactions().firstAction());
        assertTrue(say("I don't understand").contains("Remaining recognition checks"));
        observe(Event.TYPE_HAND_SIGN, "{\"sign\":\"rock\",\"confidence\":0.96}"); stage("READY");
        int calls = requests.size();
        assertTrue(say("What hand sign do you see?").contains("rock"));
        assertTrue(say("Help me").contains("no further demonstration"));
        say("let start"); stage("EXECUTION"); assertEquals(0, actions());
        assertEquals(calls, requests.size(), "Grounded perception, direct help and cue acceptance need no model call");
        assertEquals(draft, value(CoordinationMemory.AGREEMENT));
        waitForCueWindow();
        assertTrue(TaskResponse.sameWords("Welcome to the workshop.",
                observe(Event.TYPE_HAND_SIGN, "{\"sign\":\"rock\",\"confidence\":0.96}")), "Deliver only the agreed greeting");
        stage("REFLECTION"); assertEquals(1, actions());
        say("Return to conversation"); stage("CONVERSATION");
        assertFalse(storage.get(CoordinationMemory.OUTCOMES).getAsJsonArray().isEmpty());
    }
    private void dialogue() throws Exception {
        String proposal = say("Propose one dialogue riddle round. Ask me 'What has keys but cannot open a door?', let me answer, then give feedback. I supply the answer and judge the exchange. Success means I find the feedback helpful. Two agent contributions: question then feedback, no sensors or preparation. Do not start or reveal the solution yet.");
        stage("READY"); assertTrue(proposal.contains("2 agent contributions"));
        assertNull(CoordinationMemory.agreement(storage, CoordinationMemory.DRAFT).reactions());
        String question = say("Let's start"); stage("EXECUTION"); assertEquals(1, actions());
        assertTrue(question.toLowerCase(Locale.ROOT).contains("keys"));
        assertFalse(question.toLowerCase(Locale.ROOT).contains("piano"), "Do not reveal the answer in the question");
        var run = value(CoordinationMemory.RUN);
        say("Help me"); assertEquals(1, actions()); assertEquals(run, value(CoordinationMemory.RUN));
        String feedback = say("A piano.");
        assertTrue(feedback.toLowerCase(Locale.ROOT).contains("piano"), "Feedback must address the answer");
        assertTrue(feedback.toLowerCase(Locale.ROOT).matches("(?s).*\\b(correct|exactly|right|yes)\\b.*"), "Recognise the correct answer");
        assertFalse(feedback.toLowerCase(Locale.ROOT).matches("(?s).*\\b(incorrect|wrong|not correct)\\b.*"));
        stage("REFLECTION"); assertEquals(2, actions()); assertEquals("LIMIT_REACHED", storage.get(CoordinationMemory.OUTCOME).getAsString());
        say("Replace this with an output-only sensor task: on rock say exactly: Thank you for visiting. I show the sign and judge the greeting. Success means I like it. One action, no preparation checks. Propose only; wait for my acceptance.");
        stage("READY"); assertFalse(TaskMemory.active(storage)); assertEquals(run, value(CoordinationMemory.RUN));
        assertNotNull(CoordinationMemory.agreement(storage, CoordinationMemory.DRAFT).reactions());
        say("Start now"); stage("EXECUTION"); assertEquals(0, actions()); assertNotEquals(run, value(CoordinationMemory.RUN));
    }
    private void social() throws Exception {
        String proposal = say("Propose a task: when another person joins me in the scene, say one brief welcome. I arrange the scene and judge the welcome; success means I like it. One action only, no preparation checks. Do not start yet.");
        var draft = CoordinationMemory.agreement(storage, CoordinationMemory.DRAFT); assertNotNull(draft);
        assertFalse(CoordinationSocialContract.arrivalMismatch(draft, "another person"));
        assertFalse(TaskMemory.active(storage)); assertEquals(0, actions());
        if (!draft.complete()) {
            assertTrue(proposal.toLowerCase(Locale.ROOT).contains("count"), "Clarification must offer the count alternative");
            proposal = say("Yes, propose the alternative: at least two people visible, one short welcome, no preparation, same roles and success. Do not start.");
            draft = CoordinationMemory.agreement(storage, CoordinationMemory.DRAFT); assertNotNull(draft);
            assertFalse(TaskMemory.active(storage)); assertEquals(0, actions());
        }
        // The alternative must be visible to the person, not only compiled into hidden rules.
        assertTrue(proposal.contains("Visible-person conditions:"), "Show the executable count alternative before consent");
        assertTrue(proposal.contains("not a new arrival"), "Disclose the difference from the requested relative change");
        assertTrue(draft.reactions().rules().stream().anyMatch(rule -> rule.field().equals("humanCount")), "Use a supported count condition");
        stage("READY"); say("Start now"); stage("EXECUTION"); assertEquals(0, actions());
        var run = value(CoordinationMemory.RUN);
        say("Pause"); stage("PAUSED"); say("Resume"); stage("EXECUTION"); assertEquals(run, value(CoordinationMemory.RUN));
        waitForCueWindow(); observe(Event.TYPE_HUMAN_PRESENCE, "{\"humanCount\":1}"); assertEquals(0, actions());
        String welcome = observe(Event.TYPE_HUMAN_PRESENCE, "{\"humanCount\":2}");
        assertFalse(welcome.isBlank()); stage("REFLECTION"); assertEquals(1, actions());
    }
}
