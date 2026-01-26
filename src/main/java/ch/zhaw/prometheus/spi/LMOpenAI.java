package ch.zhaw.prometheus.spi;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

import ch.zhaw.prometheus.model.Event;
import ch.zhaw.prometheus.model.EventHistory;

public class LMOpenAI {
    private static final Logger LOGGER = LoggerFactory.getLogger(LMOpenAI.class);

    private static final String REMINDER_DECISION = "Remember to reply with either true or false only so that it can be parsed with the Java programming language. Your answer needs to work with Boolean.parseBoolean() method, which only accepts English true or false.";
    private static final String REMINDER_EXTRACTION = """
            Return valid JSON data that can be parsed with the GSON library for Java.
            If the value extracted is of type string, ensure it is enclosed in double quotes.
            If your response is a JSON object, ensure it starts and ends with curly brackets.
            If your response is a JSON list, ensure it starts and ends with square brackets.
            Return only the raw JSON text without any markdown formatting (do not include triple backticks), explanations, or additional text.
            """;
    private static final String REMINDER_SUMMARISATION = "Remember to reply with the summary in JSON format only so that it can be parsed with a Java program using the GSON library.";

    public static String complete(EventHistory eventHistory, String systemPrepend, String stateName) {
        List<Event> totalPrompt = LMOpenAI.composePrompt(eventHistory, systemPrepend, stateName);
        LMOpenAI.LOGGER.info("LMOpenAI.complete() with " + totalPrompt);
        String result = LMOpenAI.openai(totalPrompt);
        return result;
    }

    public static String complete(EventHistory eventHistory, String systemPrepend, String systemAppend, String stateName) {
        List<Event> totalPrompt = LMOpenAI.composePrompt(eventHistory, systemPrepend, systemAppend, stateName); // Corrected
                                                                                                                  // call
        LMOpenAI.LOGGER.info("LMOpenAI.complete() with " + totalPrompt);
        String result = LMOpenAI.openai(totalPrompt);
        return result;
    }

    public static boolean decide(EventHistory eventHistory, String systemPrepend) {
        if (eventHistory.isEmpty()) {
            throw new RuntimeException("cannot decide about empty events");
        }
        List<Event> totalPrompt = LMOpenAI.composePromptCondensed(eventHistory, systemPrepend,
                LMOpenAI.REMINDER_DECISION);
        LMOpenAI.LOGGER.info("LMOpenAI.decide() with " + totalPrompt);
        String response = LMOpenAI.openai(totalPrompt, 0.0f, 0.0f);
        return Boolean.parseBoolean(response);
    }

    public static JsonElement extract(EventHistory eventHistory, String systemPrepend) {
        if (eventHistory.isEmpty()) {
            throw new RuntimeException("cannot extract from empty events");
        }
        List<Event> totalPrompt = LMOpenAI.composePromptCondensed(eventHistory, systemPrepend,
                LMOpenAI.REMINDER_EXTRACTION);
        LMOpenAI.LOGGER.info("LMOpenAI.extract() with " + totalPrompt);
        String response = LMOpenAI.openai(totalPrompt, 0.0f, 0.0f);
        return new Gson().fromJson(response, JsonElement.class);
    }

    public static JsonElement summarise(EventHistory eventHistory, String systemPrepend) {
        if (eventHistory.isEmpty()) {
            throw new RuntimeException("cannot summarise from empty event");
        }
        List<Event> totalPrompt = LMOpenAI.composePromptCondensed(eventHistory, systemPrepend,
                LMOpenAI.REMINDER_SUMMARISATION);
        LMOpenAI.LOGGER.info("LMOpenAI.summarise() with " + totalPrompt);
        String response = LMOpenAI.openai(totalPrompt, 0.0f, 0.0f);
        return new Gson().fromJson(response, JsonElement.class);
    }

    public static String summariseOffline(EventHistory eventHistory, String systemPrepend) {
        if (eventHistory.isEmpty()) {
            throw new RuntimeException("cannot summarise offline from empty event");
        }
        List<Event> totalPrompt = LMOpenAI.composePromptCondensed(eventHistory, systemPrepend);
        LMOpenAI.LOGGER.info("LMOpenAI.summariseOffline() with " + totalPrompt);
        String result = LMOpenAI.openai(totalPrompt, 0.0f, 0.0f);
        return result;
    }

    private static List<Event> composePrompt(EventHistory eventHistory, String systemPrepend, String stateName) {
        List<Event> result = new ArrayList<Event>();
        if (systemPrepend == null) {
            throw new NullPointerException(systemPrepend + " systemPrepend (Decision prompt) cannot be null.");
        }
        result.add(Event.systemPrompt(systemPrepend, stateName));
        result.addAll(eventHistory.toList());
        return result;
    }

    private static List<Event> composePrompt(EventHistory eventHistory, String systemPrepend, String systemAppend,
            String stateName) {
        List<Event> result = new ArrayList<>();
        if (systemPrepend == null) {
            throw new NullPointerException("systemPrepend (Decision prompt) cannot be null.");
        }
        result.add(Event.systemPrompt(systemPrepend, stateName));
        result.addAll(eventHistory.toList());
        if (systemAppend != null) {
            result.add(Event.systemPrompt(systemAppend, stateName));
        }
        return result;
    }

    private static List<Event> composePromptCondensed(EventHistory eventHistory, String systemPrepend) {
        List<Event> result = new ArrayList<>();
        if (systemPrepend == null) {
            throw new NullPointerException("systemPrepend (Decision prompt) cannot be null.");
        }
        result.add(Event.systemPrompt(systemPrepend, null));
        result.add(Event.systemPrompt("<eventhistory>" + eventHistory.toString() + "</eventhistory>", null));
        return result;
    }

    private static List<Event> composePromptCondensed(EventHistory eventHistory, String systemPrepend,
            String systemAppend) {
        List<Event> result = composePromptCondensed(eventHistory, systemPrepend);
        if (systemAppend == null) {
            throw new NullPointerException("systemAppend cannot be null.");
        }
        result.add(Event.systemPrompt(systemAppend, null));
        return result;
    }

    private static String openai(List<Event> messages) {
        return LMOpenAI.openai(messages, 1, 1);
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

    public static String openai(List<Event> message, float temperature, float topP) {
        try {

            Instant start = Instant.now();

            JsonObject payload = OpenAIProperties.instance().payload();
            payload.addProperty("temperature", temperature);
            payload.addProperty("top_p", topP);
            payload.add("messages", LMOpenAI.GSON.toJsonTree(message));

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
}
