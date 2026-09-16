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
import ch.zhaw.prometheus.logging.LatencyTrace;

@Component
@ConditionalOnProperty(name = "prometheus.gateway.mode", havingValue = "openai", matchIfMissing = true)
public class OpenAILanguageModelGateway implements LanguageModelGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAILanguageModelGateway.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
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

    public OpenAILanguageModelGateway(OpenAIProperties properties) {
        this.properties = properties;
    }

    @Override
    public String complete(List<PromptMessage> messages) {
        return openai("behaviour", messages, 1.0f, 1.0f);
    }

    @Override
    public boolean decide(List<PromptMessage> messages) {
        String response = openai("decision", messages, 0.0f, 0.0f);
        return Boolean.parseBoolean(response);
    }

    @Override
    public JsonElement extract(List<PromptMessage> messages) {
        String response = openai("extraction", messages, 0.0f, 0.0f);
        return GSON.fromJson(response, JsonElement.class);
    }

    @Override
    public JsonElement summarise(List<PromptMessage> messages) {
        String response = openai("summary", messages, 0.0f, 0.0f);
        return GSON.fromJson(response, JsonElement.class);
    }

    @Override
    public String summariseOffline(List<PromptMessage> messages) {
        return openai("summary", messages, 0.0f, 0.0f);
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

    private String openai(String purpose, List<PromptMessage> prompts, float temperature, float topP) {
        long start = LatencyTrace.now();
        boolean success = false;
        int requests = 0;
        try {
            JsonObject payload = this.properties.payload();
            payload.addProperty("temperature", temperature);
            if (topP > 0) {
                payload.addProperty("top_p", topP);
            }
            payload.add("messages", this.toOpenAIMessages(prompts));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(this.properties.getUrl()))
                    .header(this.properties.headerKeyNameForAPIKey(), this.properties.getKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                    .build();
            requests++;
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException(
                        "unable to use openai api - http request returned status code: " + response.statusCode()
                                + " (\n\t"
                                + response.body() + "\n\t" + response + "\n)");
            }

            JsonObject jsonResponse = GSON.fromJson(response.body(), JsonObject.class);
            String result = testAndObtainContent(jsonResponse);
            success = true;
            JsonObject usage = jsonResponse.has("usage") && jsonResponse.get("usage").isJsonObject()
                    ? jsonResponse.getAsJsonObject("usage") : new JsonObject();
            LOGGER.info("latency trace={} stage=inference_usage purpose={} model={} effort=default promptTokens={} completionTokens={}",
                    LatencyTrace.currentId(), purpose, properties.getModel(),
                    tokenCount(usage, "prompt_tokens"), tokenCount(usage, "completion_tokens"));
            return result;
        } catch (Exception e) {
            throw new RuntimeException("unable to request openai :-(", e);
        } finally {
            LOGGER.info("latency trace={} stage=inference purpose={} status={} durationMs={} requests={}",
                    LatencyTrace.currentId(), purpose, success ? "ok" : "error", LatencyTrace.elapsedMs(start), requests);
        }
    }

    private static Integer tokenCount(JsonObject usage, String key) {
        try { return usage.has(key) ? usage.get(key).getAsInt() : null; }
        catch (RuntimeException invalid) { return null; }
    }

    private static String testAndObtainContent(JsonObject jsonResponse) {
        if (!jsonResponse.has("choices")) {
            throw new RuntimeException(
                    "unable to use openai api - json response has no choices: " + jsonResponse);
        }

        JsonArray jsonChoices = jsonResponse.getAsJsonArray("choices");

        if (jsonChoices.size() == 0) {
            throw new RuntimeException(
                    "unable to use openai api - json choices is empty: " + jsonResponse);
        }

        JsonObject jsonChoice = jsonChoices.get(0).getAsJsonObject();

        if (jsonChoice.has("finish_reason") && "content_filter".equals(jsonChoice.get("finish_reason").getAsString())) {
            throw new ContenFilterException(
                    "unable to use openai api - content of message was filtered: " + jsonResponse);
        }

        if (!jsonChoice.has("message")) {
            throw new RuntimeException(
                    "unable to use openai api - json choices is empty: " + jsonResponse);
        }

        JsonObject jsonMessage = jsonChoice.get("message").getAsJsonObject();

        if (!jsonMessage.has("content")) {
            throw new RuntimeException(
                    "unable to use openai api - json message has no content: " + jsonResponse);
        }

        return jsonMessage.get("content").getAsString();
    }
}
