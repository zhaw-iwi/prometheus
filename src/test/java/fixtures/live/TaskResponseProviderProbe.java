package fixtures.live;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import ch.zhaw.prometheus.logging.LatencyTrace;
import ch.zhaw.prometheus.model.task.TaskResponse;
import ch.zhaw.prometheus.spi.*;

/** Explicitly paid, bounded verifier evaluation. No database, voice session or task mutation. */
public final class TaskResponseProviderProbe {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3 || !"--allow-paid-provider".equals(args[0])
                || args.length == 3 && !"--repeat=2".equals(args[2]))
            throw new IllegalArgumentException("Required: --allow-paid-provider output.json [--repeat=2]");
        int repeats = args.length == 3 ? 2 : 1;
        Path output = Path.of(args[1]);
        var values = new HashMap<String, Object>();
        for (String name : List.of("openai-prod.properties", "openai.properties", "application.properties")) {
            var file = Path.of("src/main/resources", name); if (!Files.exists(file)) continue;
            var properties = new Properties(); try (var reader = Files.newBufferedReader(file)) { properties.load(reader); }
            properties.forEach((k,v) -> { if (k.toString().startsWith("openai.")) values.put(k.toString(), v); });
        }
        String key = System.getenv("OPENAI_API_KEY");
        if (key != null) values.put("openai.key", key.replaceFirst("^Bearer ", ""));
        String configuredKey = Objects.toString(values.get("openai.key"), "");
        if (configuredKey.isBlank() || configuredKey.contains("${")) throw new IllegalStateException("Supply a provider key privately before running");
        var properties = new Binder(new MapConfigurationPropertySource(values)).bind("openai", Bindable.of(OpenAIProperties.class))
                .orElseThrow(() -> new IllegalStateException("Missing OpenAI configuration"));
        properties.setRequestTimeoutMs(30000); properties.setMaxCompletionTokens(512);
        properties.getRoutes().values().forEach(r -> { r.setTimeoutMs(30000); r.setMaxCompletionTokens(512); });
        var route = InferenceRouting.resolve(properties, InferencePurpose.EXTRACTION);
        if (!"https://api.openai.com/v1/chat/completions".equals(route.url())) throw new IllegalStateException("Official OpenAI endpoint required");
        var gateway = new OpenAILanguageModelGateway(properties);
        var report = new JsonObject(); var results = new JsonArray(); report.add("cases", results);
        report.add("route", JSON.toJsonTree(route)); report.addProperty("repeats", repeats);
        report.addProperty("scope", "Synthetic response verifier only; no Live, database, task progression or playback");
        report.addProperty("maxRequests", 36); report.addProperty("maxSeconds", 240); report.addProperty("maxReportedTokens", 30000);
        long deadline = System.nanoTime() + 240_000_000_000L;
        int requests = 0, promptTokens = 0, completionTokens = 0;
        boolean passed = true, finished = false;
        try (var stream = Objects.requireNonNull(TaskResponseProviderProbe.class.getResourceAsStream("/coordination/response-verification-cases.json"))) {
            var cases = JsonParser.parseReader(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("cases");
            if (cases.size() > 18) throw new IllegalStateException("Fixture exceeds probe budget");
            for (int repetition = 1; repetition <= repeats; repetition++) for (var item : cases) {
                var fixture = item.getAsJsonObject(); var result = fixture.deepCopy(); results.add(result); result.addProperty("repetition", repetition);
                String id = fixture.get("id").getAsString();
                var check = new TaskResponse.Check(UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)), UUID.randomUUID(), UUID.randomUUID(), 1,
                        id, fixture.get("facts").getAsString(), fixture.get("speech").getAsString(), Instant.now().plusSeconds(45),
                        TaskResponse.Purpose.valueOf(fixture.get("purpose").getAsString()), fixture.has("exactSpeech") ? fixture.get("exactSpeech").getAsString() : null);
                try {
                    var verdict = TaskResponse.Verdict.MATCH;
                    boolean validOutput = true;
                    if (check.exactlyMatches()) result.addProperty("method", "word-equality");
                    else {
                        if (requests >= 36 || System.nanoTime() >= deadline || promptTokens + completionTokens >= 30000)
                            throw new IllegalStateException("Probe budget exhausted");
                        var request = check.request();
                        if (JSON.toJson(request.messages()).getBytes(StandardCharsets.UTF_8).length > 12000) throw new IllegalStateException("Oversized verifier input");
                        int remaining = (int) Math.max(1, Math.min(30000, (deadline - System.nanoTime()) / 1_000_000));
                        properties.setRequestTimeoutMs(remaining); properties.getRoutes().values().forEach(r -> r.setTimeoutMs(remaining));
                        requests++; result.addProperty("method", "provider"); result.add("messages", JSON.toJsonTree(request.messages()));
                        try (var trace = new LatencyTrace(null, ignored -> {})) {
                            String raw = gateway.infer(request); result.addProperty("raw", raw);
                            validOutput = wellFormed(raw, check); verdict = check.verdict(raw);
                            String timing = LatencyTrace.responseHeader();
                            if (timing != null) {
                                var data = JsonParser.parseString(new String(Base64.getDecoder().decode(timing), StandardCharsets.UTF_8)).getAsJsonObject();
                                result.add("timing", data);
                                for (var span : data.getAsJsonArray("spans")) {
                                    var value = span.getAsJsonObject();
                                    if (value.has("promptTokens")) promptTokens += value.get("promptTokens").getAsInt();
                                    if (value.has("completionTokens")) completionTokens += value.get("completionTokens").getAsInt();
                                }
                            }
                        }
                    }
                    String actual = verdict.name();
                    boolean correct = validOutput && fixture.getAsJsonArray("expected").asList().stream().anyMatch(v -> v.getAsString().equals(actual));
                    result.addProperty("validOutput", validOutput);
                    result.addProperty("verdict", verdict.name()); result.addProperty("passed", correct); passed &= correct;
                    System.out.println(id + " [" + repetition + "]: " + verdict + (correct ? " PASS" : " FAIL"));
                } catch (RuntimeException failure) {
                    result.addProperty("passed", false); result.addProperty("error", failure.getClass().getSimpleName()); passed = false;
                    throw failure; // Do not continue spending after transport/configuration/budget failures.
                }
            }
            finished = true;
        } finally {
            report.addProperty("passed", passed && finished); report.addProperty("requests", requests);
            report.addProperty("promptTokens", promptTokens); report.addProperty("completionTokens", completionTokens);
            Files.createDirectories(output.toAbsolutePath().getParent()); Files.writeString(output, JSON.toJson(report));
        }
        if (!passed) throw new AssertionError("Verifier evaluation failed; see " + output);
    }
    // A malformed output that production conservatively maps to AMBIGUOUS must not score as a correct negative.
    private static boolean wellFormed(String raw, TaskResponse.Check check) {
        try {
            var value = JsonParser.parseString(raw).getAsJsonObject();
            return value.keySet().equals(Set.of("responseId", "revision", "segmentId", "verdict"))
                    && value.get("responseId").getAsString().equals(check.id().toString())
                    && value.get("revision").isJsonPrimitive() && value.getAsJsonPrimitive("revision").isNumber()
                    && value.get("revision").getAsBigDecimal().intValueExact() == check.revision()
                    && value.get("segmentId").getAsString().equals(check.segment())
                    && Set.of("MATCH", "UNRELATED", "AMBIGUOUS").contains(value.get("verdict").getAsString());
        } catch (RuntimeException invalid) { return false; }
    }
}
