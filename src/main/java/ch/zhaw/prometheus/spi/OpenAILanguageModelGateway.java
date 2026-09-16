package ch.zhaw.prometheus.spi;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ch.zhaw.prometheus.model.policy.PromptMessage;

@Component
@ConditionalOnProperty(name = "prometheus.gateway.mode", havingValue = "openai", matchIfMissing = true)
public class OpenAILanguageModelGateway implements LanguageModelGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAILanguageModelGateway.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
    private static final Gson GSON = new GsonBuilder().addSerializationExclusionStrategy(new ExclusionStrategy() {

        @Override
        public boolean shouldSkipField(FieldAttributes f) {
            return f.getAnnotation(GsonExclude.class) != null;
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return clazz == Instant.class;
        }

    }).create();

    private final OpenAIProperties properties;
    private final GuardInferenceExecutor guardExecutor;

    public OpenAILanguageModelGateway(OpenAIProperties properties) {
        this(properties, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OpenAILanguageModelGateway(OpenAIProperties properties, GuardInferenceExecutor guardExecutor) {
        this.properties = properties;
        this.guardExecutor = guardExecutor;
    }

    @Override public GuardInferenceExecutor guardExecutor() { return guardExecutor; }

    @Override public GuardInferenceOptions guardInferenceOptions() {
        return new GuardInferenceOptions(GuardInferenceOptions.Strategy.valueOf(properties.getGuardStrategy().toUpperCase(java.util.Locale.ROOT)),
                properties.getGuardBatchSize(), properties.getGuardMaxCharacters());
    }

    @Override public Object guardCompatibilityKey(InferenceRequest request) {
        return InferenceRouting.resolve(properties, request.purpose());
    }

    @Override public String complete(List<PromptMessage> messages) {
        return infer(new InferenceRequest(InferencePurpose.BEHAVIOUR, messages, InferenceRequest.Output.TEXT));
    }
    @Override public boolean decide(List<PromptMessage> messages) {
        return InferenceResult.bool(infer(new InferenceRequest(InferencePurpose.DECISION, messages, InferenceRequest.Output.BOOLEAN)));
    }
    @Override public JsonElement extract(List<PromptMessage> messages) {
        return InferenceResult.json(infer(new InferenceRequest(InferencePurpose.EXTRACTION, messages, InferenceRequest.Output.JSON)));
    }
    @Override public JsonElement summarise(List<PromptMessage> messages) {
        return InferenceResult.json(infer(new InferenceRequest(InferencePurpose.SUMMARY, messages, InferenceRequest.Output.JSON)));
    }
    @Override public String summariseOffline(List<PromptMessage> messages) {
        return infer(new InferenceRequest(InferencePurpose.SUMMARY, messages, InferenceRequest.Output.TEXT));
    }

    JsonArray toOpenAIMessages(List<PromptMessage> prompts) {
        JsonArray messages = new JsonArray();
        if (prompts == null) {
            return messages;
        }
        for (PromptMessage prompt : prompts) {
            JsonObject message = new JsonObject();
            message.addProperty("role", prompt.getRole());
            message.addProperty("content", prompt.getContent());
            messages.add(message);
        }
        return messages;
    }

    @Override public String infer(InferenceRequest inference) {
        InferenceRouting.Route route = InferenceRouting.resolve(properties, inference.purpose());
        long start = System.nanoTime();
        boolean success = false;
        int requests = 0;
        try {
            JsonObject payload = payload(inference, route);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(route.url()))
                    .timeout(java.time.Duration.ofMillis(route.timeoutMs()))
                    .header(properties.headerKeyNameForAPIKey(), properties.getKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                    .build();
            requests++;
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("Inference provider returned HTTP " + response.statusCode()
                        + "; check account access, configured route and provider limits");
            }
            JsonElement parsed = InferenceResult.json(response.body());
            if (!parsed.isJsonObject()) throw new IllegalStateException("Invalid inference response envelope");
            JsonObject envelope = parsed.getAsJsonObject();
            JsonObject usage = envelope.has("usage") && envelope.get("usage").isJsonObject()
                    ? envelope.getAsJsonObject("usage") : new JsonObject();
            LOGGER.info("latency trace={} request={} stage=inference_usage purpose={} model={} effort={} promptTokens={} completionTokens={}",
                    inference.traceId(), inference.requestId(), inference.purpose(), route.model(),
                    route.effort() == null ? "default" : route.effort(), tokenCount(usage, "prompt_tokens"), tokenCount(usage, "completion_tokens"));
            String result = InferenceResult.validate(testAndObtainContent(envelope), inference.output());
            success = true;
            return result;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Inference request interrupted");
        } catch (java.net.http.HttpTimeoutException timeout) {
            throw new IllegalStateException("Inference request exceeded configured deadline");
        } catch (java.io.IOException transport) {
            throw new IllegalStateException("Inference provider transport failed");
        } finally {
            LOGGER.info("latency trace={} request={} stage=inference purpose={} model={} effort={} status={} durationMs={} requests={}",
                    inference.traceId(), inference.requestId(), inference.purpose(), route.model(),
                    route.effort() == null ? "default" : route.effort(), success ? "ok" : "error",
                    (System.nanoTime() - start) / 1_000_000.0, requests);
        }
    }

    JsonObject payload(InferenceRequest inference, InferenceRouting.Route route) {
        JsonObject payload = new JsonObject();
        if ("openai".equals(properties.getOpenaivsazureopenai())) payload.addProperty("model", route.model());
        if (route.effort() != null) payload.addProperty("reasoning_effort", route.effort());
        if (route.maxCompletionTokens() != null) payload.addProperty("max_completion_tokens", route.maxCompletionTokens());
        if (route.samplingParameters()) payload.addProperty("temperature",
                inference.purpose() == InferencePurpose.BEHAVIOUR || inference.purpose() == InferencePurpose.NONVERBAL ? 1.0 : 0.0);
        payload.add("messages", toOpenAIMessages(inference.messages()));
        JsonObject schema = inference.schema();
        if (schema != null) {
            JsonObject format = new JsonObject();
            format.addProperty("type", "json_schema");
            JsonObject definition = new JsonObject();
            definition.addProperty("name", "prometheus_result");
            definition.addProperty("strict", true);
            definition.add("schema", schema);
            format.add("json_schema", definition);
            payload.add("response_format", format);
        } else if (inference.output() == InferenceRequest.Output.JSON_OBJECT) {
            JsonObject format = new JsonObject();
            format.addProperty("type", "json_object");
            payload.add("response_format", format);
        }
        return payload;
    }

    private static Integer tokenCount(JsonObject usage, String key) {
        try { return usage.has(key) ? usage.get(key).getAsInt() : null; }
        catch (RuntimeException invalid) { return null; }
    }

    private static String testAndObtainContent(JsonObject envelope) {
        try {
            JsonArray choices = envelope.getAsJsonArray("choices");
            if (choices == null || choices.size() != 1) throw new IllegalStateException();
            JsonObject choice = choices.get(0).getAsJsonObject();
            if (!"stop".equals(choice.get("finish_reason").getAsString())) {
                throw new IllegalStateException();
            }
            JsonObject message = choice.getAsJsonObject("message");
            if (message.has("refusal") && !message.get("refusal").isJsonNull()) throw new IllegalStateException();
            JsonElement content = message.get("content");
            if (content == null || !content.isJsonPrimitive() || !content.getAsJsonPrimitive().isString()) {
                throw new IllegalStateException();
            }
            return content.getAsString();
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("Inference response was missing, refused, filtered or truncated");
        }
    }
}
