package ch.zhaw.prometheus.spi;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        LOGGER.info("OpenAILanguageModelGateway.complete() with {}", messages);
        return openai(messages, 1.0f, 1.0f);
    }

    @Override
    public boolean decide(List<PromptMessage> messages) {
        LOGGER.info("OpenAILanguageModelGateway.decide() with {}", messages);
        String response = openai(messages, 0.0f, 0.0f);
        return Boolean.parseBoolean(response);
    }

    @Override
    public JsonElement extract(List<PromptMessage> messages) {
        LOGGER.info("OpenAILanguageModelGateway.extract() with {}", messages);
        String response = openai(messages, 0.0f, 0.0f);
        return GSON.fromJson(response, JsonElement.class);
    }

    @Override
    public JsonElement summarise(List<PromptMessage> messages) {
        LOGGER.info("OpenAILanguageModelGateway.summarise() with {}", messages);
        String response = openai(messages, 0.0f, 0.0f);
        return GSON.fromJson(response, JsonElement.class);
    }

    @Override
    public String summariseOffline(List<PromptMessage> messages) {
        LOGGER.info("OpenAILanguageModelGateway.summariseOffline() with {}", messages);
        return openai(messages, 0.0f, 0.0f);
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

    private String openai(List<PromptMessage> prompts, float temperature, float topP) {
        try {
            Instant start = Instant.now();

            JsonObject payload = this.properties.payload();
            payload.addProperty("temperature", temperature);
            payload.addProperty("top_p", topP);
            payload.add("messages", this.toOpenAIMessages(prompts));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(this.properties.getUrl()))
                    .header(this.properties.headerKeyNameForAPIKey(), this.properties.getKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            Instant end = Instant.now();
            LOGGER.info("OpenAILanguageModelGateway.openai() http request took {} milliseconds",
                    Duration.between(start, end).toMillis());

            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException(
                        "unable to use openai api - http request returned status code: " + response.statusCode()
                                + " (\n\t"
                                + response.body() + "\n\t" + response + "\n)");
            }

            JsonObject jsonResponse = GSON.fromJson(response.body(), JsonObject.class);
            String result = testAndObtainContent(jsonResponse);
            LOGGER.info("OpenAILanguageModelGateway.openai() returns {}", result);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("unable to request openai :-(", e);
        }
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

