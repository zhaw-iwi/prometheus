package fixtures.live;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import ch.zhaw.prometheus.agentdefs.core.GenericMultimodalBehaviour;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.*;

/** Explicitly paid synthetic diagnostic (one request, or seven with confirmation cases). No database. */
public final class CoreTaskProviderProbe {
    public static void main(String[] args) throws Exception {
        if ((args.length != 2 && args.length != 3) || !"--allow-paid-provider".equals(args[0])
                || args.length == 3 && !"--confirmation-cases".equals(args[2])) throw new IllegalArgumentException("Explicit paid opt-in and artifact path required");
        var values = new HashMap<String, Object>();
        for (String file : List.of("openai.properties", "application.properties")) {
            var path = Path.of("src/main/resources", file); if (!Files.exists(path)) continue;
            var properties = new Properties(); try (var input = Files.newBufferedReader(path)) { properties.load(input); }
            properties.forEach((key, value) -> { if (key.toString().startsWith("openai.")) values.put(key.toString(), value); });
        }
        if (System.getenv("OPENAI_API_KEY") != null) values.put("openai.key", System.getenv("OPENAI_API_KEY"));
        var properties = new Binder(new MapConfigurationPropertySource(values)).bind("openai", Bindable.of(OpenAIProperties.class))
                .orElseThrow(() -> new IllegalStateException("Missing provider properties"));
        var gateway = spy(new OpenAILanguageModelGateway(properties)); var report = new JsonObject(); var requests = new JsonArray();
        report.add("requests", requests);
        doAnswer(call -> {
            InferenceRequest request = call.getArgument(0); var entry = new JsonObject(); requests.add(entry);
            entry.add("messages", new Gson().toJsonTree(request.messages()));
            String raw = (String) call.callRealMethod(); entry.addProperty("raw", raw); return raw;
        }).when(gateway).infer(any());
        var agent = new GenericMultimodalBehaviour().createAgent();
        var runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway)
                .withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch()));
        agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user",
                "Please propose, but don't start, a task that waits for one rock hand sign, then shows paper silently once and finishes."), runtime);
        verify(gateway, times(1)).infer(any()); report.add("storage", new Gson().toJsonTree(agent.getStorage()));
        Files.writeString(Path.of(args[1]), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("Synthetic provider diagnostic written to " + args[1]);
        if (!agent.getStorage().containsKey("task.draft")) throw new IllegalStateException("Provider proposal was not accepted; inspect synthetic artifact");
        var task = ch.zhaw.prometheus.model.task.TaskSpec.parse(agent.getStorage().get("task.draft"));
        if (task.firstAction() != ch.zhaw.prometheus.model.task.TaskSpec.FirstAction.ON_CUE || task.maxActions() != 1
                || task.rules().stream().noneMatch(rule -> rule.effect() == ch.zhaw.prometheus.model.task.TaskSpec.Effect.ACT))
            throw new IllegalStateException("Provider proposal changed first-action or completion semantics");
        if (args.length == 3) {
            boolean passed = confirmations(gateway, task, report);
            Files.writeString(Path.of(args[1]), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            verify(gateway, times(7)).infer(any());
            if (!passed) throw new IllegalStateException("Provider confirmation case failed; inspect synthetic artifact");
            System.out.println("Six contextual confirmation cases passed.");
        }
    }
    private static boolean confirmations(LanguageModelGateway gateway, ch.zhaw.prometheus.model.task.TaskSpec task, JsonObject report) {
        var cases = new JsonArray(); report.add("confirmationCases", cases); boolean passed = true;
        String[] questions = {"Want me to activate that waiting rule now?", "Would you like coffee?",
                "Would you like to revise the plan before I activate it?", "Want me to start this when you say so?",
                "If you like, we can activate it now so it waits for that cue.", "We could talk about coffee."};
        for (int i = 0; i < questions.length; i++) {
            var agent = new GenericMultimodalBehaviour().createAgent();
            var policy = (ch.zhaw.prometheus.agentdefs.core.CoreTaskPolicy) agent.getCurrentState().ownPolicy();
            var storage = policy.storage();
            storage.put("task.draft", task.json()); storage.put("task.phase", new JsonPrimitive("CONFIGURATION"));
            storage.put("task.revision", new JsonPrimitive(1));
            storage.put("core.task.invitation", new JsonPrimitive(java.time.Instant.now().minusSeconds(1).toString()));
            var owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
            var event = agent.recordExternalSpeech(questions[i], new ch.zhaw.prometheus.model.event.SpeechProvenance(
                    ch.zhaw.prometheus.model.event.SpeechProvenance.Origin.NATIVE, owner.sessionId(), owner.epoch(),
                    "question-"+i, ch.zhaw.prometheus.model.event.SpeechProvenance.Association.NONE, List.of(), true));
            org.springframework.test.util.ReflectionTestUtils.setField(event, "createdDate", java.time.Instant.now());
            policy.liveSpeechCaptured(event, owner, java.time.Instant.now(), java.time.Instant.now(), List.of());
            agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Yes, please"),
                    new PolicyRuntime(new PromptMessageAssembler(), gateway).withExternalSpeech(owner));
            String phase = ch.zhaw.prometheus.model.task.TaskMemory.phase(storage);
            var entry = new JsonObject(); entry.addProperty("question", questions[i]); entry.addProperty("phase", phase);
            entry.addProperty("passed", (i == 0 || i == 4 ? "RUNNING" : "CONFIGURATION").equals(phase)); cases.add(entry);
            passed &= entry.get("passed").getAsBoolean();
        }
        return passed;
    }
}
