package ch.zhaw.prometheus.definition.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ch.zhaw.prometheus.definition.catalog.BundledDefinitionCatalog;
import ch.zhaw.prometheus.definition.compiled.DefinitionCompilationException;
import ch.zhaw.prometheus.definition.compiled.DefinitionCompiler;
import ch.zhaw.prometheus.definition.component.BuiltInComponentCatalog;
import ch.zhaw.prometheus.definition.document.AgentDefinitionJson;
import ch.zhaw.prometheus.definition.preview.DesignerPreviewService.PreviewSource;
import ch.zhaw.prometheus.definition.runtime.AgentRuntimeEngine;
import ch.zhaw.prometheus.definition.runtime.RuntimeBehaviour;
import ch.zhaw.prometheus.definition.runtime.RuntimeEvent;
import ch.zhaw.prometheus.definition.runtime.RuntimeInvocation;
import ch.zhaw.prometheus.definition.runtime.RuntimeModelGateway;
import ch.zhaw.prometheus.definition.runtime.RuntimePromptBundle;

class DesignerPreviewServiceUnitTest {

    @Test
    void unsavedRpsDraftRunsThroughProductionCompilerAndRuntimeWithDeterministicTrace() {
        MutableClock clock = new MutableClock();
        DesignerPreviewService previews = service(new ScriptedRpsGateway(), clock, 4, 16);
        String json = canonical("core.rock_scissor_paper");

        var created = previews.create(json, PreviewSource.UNSAVED, null);
        assertEquals(PreviewSource.UNSAVED, created.source());
        assertNull(created.storedRevisionId());
        assertEquals(List.of("context", "start"), created.activeStatePath());
        assertEquals("CREATE", created.transcript().getFirst().kind());

        var reveal = previews.acknowledge(created.id(), userEvent("ready"));
        assertEquals(List.of("context", "reveal"), reveal.activeStatePath());
        assertEquals(List.of("start_to_reveal"), reveal.transcript().getLast().acceptedTransitionIds());
        assertEquals("rock", reveal.transcript().getLast().behaviour().motion().path("handSign").asText());

        var result = previews.acknowledge(created.id(), handEvent("scissor"));
        assertEquals(List.of("context", "result"), result.activeStatePath());
        assertEquals(1, result.storage().get("rps_rounds").size());
        assertEquals("agent", result.storage().get("rps_last_round").path("winner").asText());
        assertTrue(result.history().stream().anyMatch(event -> "obs.hand.sign".equals(event.type())));
        assertTrue(result.history().stream().anyMatch(event -> AgentRuntimeEngine.BEHAVIOUR_EVENT_TYPE
                .equals(event.type())));
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void sessionsAreIsolatedBoundedClosableAndExpireOnIdleTtl() {
        MutableClock clock = new MutableClock();
        DesignerPreviewService previews = service(new NoModelGateway(), clock, 2, 3);
        String talkToMe = canonical("core.talk_to_me");

        var first = previews.create(talkToMe, PreviewSource.SAVED, 41L);
        var second = previews.create(talkToMe, PreviewSource.UNSAVED, null);
        assertEquals(2, previews.sessionCount());
        assertThrows(PreviewLimitException.class,
                () -> previews.create(talkToMe, PreviewSource.UNSAVED, null));

        var changed = previews.acknowledge(first.id(), userEvent("hello preview"));
        assertEquals("hello preview", changed.transcript().getLast().behaviour().speech());
        assertFalse(changed.history().isEmpty());
        assertTrue(previews.inspect(second.id()).history().isEmpty());
        previews.generate(first.id());
        assertThrows(PreviewLimitException.class, () -> previews.reset(first.id()));

        previews.close(second.id());
        assertThrows(PreviewNotFoundException.class, () -> previews.inspect(second.id()));
        clock.advance(Duration.ofMinutes(6));
        previews.cleanupExpired();
        assertThrows(PreviewNotFoundException.class, () -> previews.inspect(first.id()));
        assertEquals(0, previews.sessionCount());
    }

    @Test
    void failedComponentExecutionRollsBackSessionAndReturnsOnlySafeDiagnostic() throws Exception {
        MutableClock clock = new MutableClock();
        SwitchingGateway gateway = new SwitchingGateway();
        DesignerPreviewService previews = service(gateway, clock, 2, 8);
        String fixture;
        try (InputStream input = getClass().getResourceAsStream(
                "/agent-definitions/valid/deterministic-components.json")) {
            fixture = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var created = previews.create(fixture, PreviewSource.UNSAVED, null);
        gateway.fail = true;

        var failed = previews.acknowledge(created.id(), handEvent("rock"));

        assertEquals(0, failed.storage().get("round_count").asInt());
        assertEquals(created.history(), failed.history());
        assertEquals("PREVIEW_EXECUTION_FAILED", failed.diagnostics().getFirst().code());
        assertEquals("PREVIEW_EXECUTION_FAILED",
                failed.transcript().getLast().diagnostics().getFirst().code());
        assertFalse(failed.toString().contains("provider-secret-detail"));
    }

    @Test
    void invalidDraftCannotCreatePreviewAndPayloadLimitIsEnforced() throws Exception {
        MutableClock clock = new MutableClock();
        DesignerPreviewService previews = service(new NoModelGateway(), clock, 2, 8);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode invalid = (ObjectNode) mapper.readTree(canonical("core.talk_to_me"));
        ((ObjectNode) invalid.path("lifecycle")).put("initialStateId", "missing");

        assertThrows(DefinitionCompilationException.class,
                () -> previews.create(mapper.writeValueAsString(invalid), PreviewSource.UNSAVED, null));

        var created = previews.create(canonical("core.talk_to_me"), PreviewSource.UNSAVED, null);
        assertThrows(PreviewLimitException.class, () -> previews.acknowledge(created.id(),
                new RuntimeEvent("obs.user_utterance", "user", "observation", "x".repeat(129))));
    }

    @Test
    void exactTextStayScenarioPassesAndFailedExpectationExplainsObservedTrace() throws Exception {
        DesignerPreviewService previews = service(new NoModelGateway(), new MutableClock(), 2, 8);
        ObjectNode passing = scenario("Exact stay", "obs.user_utterance", "hello scenario",
                List.of("talk"));
        passing.withObject("expected").withArray("behaviourFragments")
                .addObject().put("speech", "hello scenario");
        ObjectNode failing = passing.deepCopy();
        failing.put("name", "Wrong speech");
        failing.withObject("expected").withArray("behaviourFragments").removeAll()
                .addObject().put("speech", "different");
        String definition = withScenarios(canonical("core.talk_to_me"), passing, failing);

        var passed = previews.executeScenario(definition, 0);
        var failed = previews.executeScenario(definition, 1);

        assertTrue(passed.passed());
        assertEquals(List.of("repeat"), passed.acceptedTransitionIds());
        assertEquals(List.of("speech"), passed.emittedModalities());
        assertTrue(passed.discarded());
        assertFalse(failed.passed());
        assertTrue(failed.expectations().stream().filter(expectation -> !expectation.passed())
                .allMatch(expectation -> expectation.explanation().contains("No recorded behaviour")));
        assertEquals(0, previews.sessionCount());
    }

    @Test
    void seededRpsScenarioIsRepeatableAndReturnsRoundTrace() throws Exception {
        DesignerPreviewService previews = service(new ScriptedRpsGateway(), new MutableClock(), 2, 12);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode scenario = scenario("RPS round", "obs.user_utterance", "ready",
                List.of("context", "result"));
        scenario.put("initializerSeed", 27);
        ObjectNode hand = mapper.createObjectNode();
        hand.put("type", "obs.hand.sign");
        hand.put("actor", "sensor");
        hand.put("kind", "observation");
        hand.put("payload", "{\"sign\":\"scissor\"}");
        scenario.withArray("events").add(hand);
        String definition = withScenarios(canonical("core.rock_scissor_paper"), scenario);

        var first = previews.executeScenario(definition, 0);
        var second = previews.executeScenario(definition, 0);

        assertTrue(first.passed());
        assertEquals(first.storage(), second.storage());
        assertEquals(List.of("start_to_reveal", "reveal_to_result"), first.acceptedTransitionIds());
        assertTrue(first.storageChanges().stream().anyMatch(change -> "rps_rounds".equals(change.key())));
        assertTrue(first.emittedModalities().contains("motion.handSign"));
        assertEquals(0, previews.sessionCount());
    }

    @Test
    void initialStorageAndFakePromptBehaviourUseProductionCompilationWithoutPersistence() throws Exception {
        DesignerPreviewService previews = service(new NoModelGateway(), new MutableClock(), 2, 8);
        String fixture;
        try (InputStream input = getClass().getResourceAsStream(
                "/agent-definitions/valid/deterministic-components.json")) {
            fixture = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        ObjectNode scenario = scenario("Override and increment", "obs.hand.sign", "rock", List.of("round"));
        scenario.put("initializerSeed", 9);
        scenario.withObject("initialStorage").put("round_count", 4);
        scenario.withObject("expected").withObject("storage").put("round_count", 5);
        scenario.withObject("expected").withArray("behaviourFragments")
                .addObject().put("speech", "deterministic model response");

        var result = previews.executeScenario(withScenarios(fixture, scenario), 0);

        assertTrue(result.passed(), result.toString());
        assertEquals(5, result.storage().get("round_count").asInt());
        assertTrue(result.storageChanges().stream().anyMatch(change -> change.before().asInt() == 4
                && change.after().asInt() == 5));
        assertEquals(0, previews.sessionCount());
    }

    @Test
    void branchAndFinishScenariosReportTheirAcceptedPaths() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String composite;
        try (InputStream input = getClass().getResourceAsStream("/agent-definitions/valid/composite-flow.json")) {
            composite = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        DesignerPreviewService previews = service(new NoModelGateway(), new MutableClock(), 2, 8);
        ObjectNode finish = scenario("Finish", "obs.user_utterance", "done", List.of("session", "done"));
        var finished = previews.executeScenario(withScenarios(composite, finish), 0);
        assertTrue(finished.passed());
        assertEquals(List.of("finish"), finished.acceptedTransitionIds());

        ObjectNode branchedDefinition = (ObjectNode) mapper.readTree(composite);
        ObjectNode alternate = mapper.createObjectNode();
        alternate.put("id", "alternate");
        alternate.put("name", "Alternate");
        alternate.put("kind", "atomic");
        alternate.put("entryMode", "start");
        alternate.put("oblivious", false);
        alternate.putNull("eventSelector");
        alternate.putNull("policy");
        branchedDefinition.withArray("states").add(alternate);
        ((ObjectNode) branchedDefinition.withArray("states").get(0)).withArray("childStateIds").add("alternate");
        ArrayNode transitions = mapper.createArrayNode();
        transitions.add(mapper.readTree("""
                {"id":"social-branch","sourceStateId":"conversation","targetStateId":"alternate","order":10,
                 "decisions":[{"kind":"prometheus.decision.latest-event-type","version":1,
                 "config":{"eventType":"obs.user_utterance"}}],"actions":[]}
                """));
        branchedDefinition.set("transitions", transitions);
        ObjectNode branch = scenario("Branch", "obs.user_utterance", "branch", List.of("session", "alternate"));
        String branchJson = withScenarios(mapper.writeValueAsString(branchedDefinition), branch);

        var branched = previews.executeScenario(branchJson, 0);
        assertTrue(branched.passed(), branched.toString());
        assertEquals(List.of("social-branch"), branched.acceptedTransitionIds());
        assertEquals(0, previews.sessionCount());
    }

    @Test
    void scenarioLimitsRejectOversizedScriptsAndAlwaysCleanDisposableSessions() throws Exception {
        DesignerPreviewService previews = service(new NoModelGateway(), new MutableClock(), 2, 2);
        ObjectNode tooMany = scenario("Too many", "obs.user_utterance", "one", List.of("talk"));
        tooMany.withArray("events").add(tooMany.withArray("events").get(0).deepCopy());
        assertThrows(PreviewLimitException.class,
                () -> previews.executeScenario(withScenarios(canonical("core.talk_to_me"), tooMany), 0));
        assertEquals(0, previews.sessionCount());

        ObjectNode tooLarge = scenario("Too large", "obs.user_utterance", "x".repeat(129), List.of("talk"));
        assertThrows(PreviewLimitException.class,
                () -> previews.executeScenario(withScenarios(canonical("core.talk_to_me"), tooLarge), 0));
        assertEquals(0, previews.sessionCount());
    }

    private static DesignerPreviewService service(RuntimeModelGateway gateway, Clock clock, int maxSessions,
            int maxOperations) {
        AgentDefinitionJson json = new AgentDefinitionJson();
        DefinitionCompiler compiler = new DefinitionCompiler(BuiltInComponentCatalog.createRegistry(), json);
        return new DesignerPreviewService(json, compiler, new AgentRuntimeEngine(), gateway, clock,
                () -> new Random(1), Duration.ofMinutes(5), maxSessions, maxOperations, 128);
    }

    private static String canonical(String key) {
        AgentDefinitionJson json = new AgentDefinitionJson();
        return json.canonicalJson(BundledDefinitionCatalog.loadMainCatalog().require(key).document());
    }

    private static ObjectNode scenario(String name, String eventType, String payload, List<String> expectedPath) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode scenario = mapper.createObjectNode();
        scenario.put("name", name);
        scenario.withArray("events").addObject().put("type", eventType).put("actor", "user")
                .put("kind", "observation").put("payload", payload);
        ArrayNode path = scenario.withObject("expected").withArray("activeStatePath");
        expectedPath.forEach(path::add);
        return scenario;
    }

    private static String withScenarios(String json, ObjectNode... scenarios) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode definition = (ObjectNode) mapper.readTree(json);
        ArrayNode values = mapper.createArrayNode();
        for (ObjectNode scenario : scenarios) values.add(scenario);
        definition.withObject("verification").set("scenarios", values);
        return mapper.writeValueAsString(definition);
    }

    private static RuntimeEvent userEvent(String payload) {
        return new RuntimeEvent("obs.user_utterance", "user", "observation", payload);
    }

    private static RuntimeEvent handEvent(String sign) {
        return new RuntimeEvent("obs.hand.sign", "sensor", "observation", "{\"sign\":\"" + sign + "\"}");
    }

    private static class NoModelGateway implements RuntimeModelGateway {
        @Override
        public RuntimeBehaviour generate(RuntimePromptBundle prompts, RuntimeInvocation invocation) {
            return RuntimeBehaviour.speechOnly("deterministic model response");
        }

        @Override
        public boolean decide(String prompt, RuntimeInvocation invocation) {
            return false;
        }

        @Override
        public JsonNode extract(String prompt, JsonNode outputSchema, RuntimeInvocation invocation) {
            return JsonNodeFactory.instance.objectNode();
        }
    }

    private static final class ScriptedRpsGateway extends NoModelGateway {
        @Override
        public boolean decide(String prompt, RuntimeInvocation invocation) {
            String payload = invocation.history().isEmpty() ? "" : invocation.history().getLast().payload();
            if ("stop".equals(payload)) {
                return true;
            }
            if ("ready".equals(payload)) {
                return prompt.contains("ready to start a round");
            }
            if ("again".equals(payload)) {
                return prompt.contains("play another round");
            }
            return false;
        }

        @Override
        public JsonNode extract(String prompt, JsonNode outputSchema, RuntimeInvocation invocation) {
            throw new AssertionError("RPS has no extraction action");
        }
    }

    private static final class SwitchingGateway extends NoModelGateway {
        private boolean fail;

        @Override
        public RuntimeBehaviour generate(RuntimePromptBundle prompts, RuntimeInvocation invocation) {
            if (this.fail) {
                throw new IllegalStateException("provider-secret-detail");
            }
            return super.generate(prompts, invocation);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-08-30T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return this.now;
        }

        void advance(Duration duration) {
            this.now = this.now.plus(duration);
        }
    }
}
