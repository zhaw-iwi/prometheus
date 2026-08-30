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
