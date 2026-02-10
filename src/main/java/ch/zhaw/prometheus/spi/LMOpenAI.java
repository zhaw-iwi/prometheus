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

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ch.zhaw.prometheus.model.policy.PromptMessage;

public class LMOpenAI {
    private static final Logger LOGGER = LoggerFactory.getLogger(LMOpenAI.class);

    public static final String REMINDER_DECISION = "Remember to reply with either true or false only so that it can be parsed with the Java programming language. Your answer needs to work with Boolean.parseBoolean() method, which only accepts English true or false.";
    public static final String REMINDER_EXTRACTION = """
            Return valid JSON data that can be parsed with the GSON library for Java.
            If the value extracted is of type string, ensure it is enclosed in double quotes.
            If your response is a JSON object, ensure it starts and ends with curly brackets.
            If your response is a JSON list, ensure it starts and ends with square brackets.
            Return only the raw JSON text without any markdown formatting (do not include triple backticks), explanations, or additional text.
            """;
    public static final String REMINDER_SUMMARISATION = "Remember to reply with the summary in JSON format only so that it can be parsed with a Java program using the GSON library.";

    public static String complete(List<PromptMessage> messages) {
        LOGGER.info("LMOpenAI.complete() with " + messages);
        return openai(messages);
    }

    public static boolean decide(List<PromptMessage> messages) {
        LOGGER.info("LMOpenAI.decide() with " + messages);
        String response = openai(messages, 0.0f, 0.0f);
        return Boolean.parseBoolean(response);
    }

    public static JsonElement extract(List<PromptMessage> messages) {
        LOGGER.info("LMOpenAI.extract() with " + messages);
        String response = openai(messages, 0.0f, 0.0f);
        return new Gson().fromJson(response, JsonElement.class);
    }

    public static JsonElement summarise(List<PromptMessage> messages) {
        LOGGER.info("LMOpenAI.summarise() with " + messages);
        String response = openai(messages, 0.0f, 0.0f);
        return new Gson().fromJson(response, JsonElement.class);
    }

    public static String summariseOffline(List<PromptMessage> messages) {
        LOGGER.info("LMOpenAI.summariseOffline() with " + messages);
        return openai(messages, 0.0f, 0.0f);
    }

    private static String openai(List<PromptMessage> messages) {
        return openai(messages, 1, 1);
    }

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

    private static String openai(List<PromptMessage> message, float temperature, float topP) {
        try {

            Instant start = Instant.now();

            JsonObject payload = OpenAIProperties.instance().payload();
            payload.addProperty("temperature", temperature);
            payload.addProperty("top_p", topP);
            payload.add("messages", LMOpenAI.toOpenAIMessages(message));

            // @TODO seems to be available in azure.openai
            // payload.addProperty("max_tokens", 800);
            // payload.addProperty("frequency_penalty", 0);
            // payload.addProperty("presence_penalty", 0);
            // payload.addProperty("stop", null);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(OpenAIProperties.instance().getUrl()))
                    .header(OpenAIProperties.instance().headerKeyNameForAPIKey(), OpenAIProperties.instance().getKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(LMOpenAI.GSON.toJson(payload)))
                    .build();
            HttpResponse<String> response = LMOpenAI.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            Instant end = Instant.now();
            LMOpenAI.LOGGER.info(
                    "LMOpenAI.openai() http request took " + Duration.between(start, end).toMillis() + " milliseconds");

            // @todo: possibly do some more extensive testing here?
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException(
                        "unable to use openai api - http request returned status code: " + response.statusCode()
                                + " (\n\t"
                                + response.body() + "\n\t" + response.toString() + "\n)");
            }

            JsonObject jsonResponse = LMOpenAI.GSON.fromJson(response.body(), JsonObject.class);
            String result = LMOpenAI.testAndObtainContent(jsonResponse);
            LMOpenAI.LOGGER.info("LMOpenAI.openai() returns " + result);
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

    static JsonArray toOpenAIMessages(List<PromptMessage> events) {
        JsonArray messages = new JsonArray();
        if (events == null) {
            return messages;
        }
        for (PromptMessage event : events) {
            JsonObject message = new JsonObject();
            message.addProperty("role", event.getRole());
            message.addProperty("content", event.getContent());
            messages.add(message);
        }
        return messages;
    }
}
