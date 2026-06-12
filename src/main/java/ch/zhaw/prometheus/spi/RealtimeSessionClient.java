package ch.zhaw.prometheus.spi;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.springframework.stereotype.Component;

@Component
public class RealtimeSessionClient {

    private static final String DEFAULT_REALTIME_CLIENT_SECRET_URL = "https://api.openai.com/v1/realtime/client_secrets";
    private static final String DEFAULT_REALTIME_CALLS_URL = "https://api.openai.com/v1/realtime/calls";
    private static final String DEFAULT_REALTIME_MODEL = "gpt-realtime";
    private static final String DEFAULT_REALTIME_TRANSCRIPTION_MODEL = "gpt-realtime-whisper";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();
    private final OpenAIProperties properties;

    public RealtimeSessionClient(OpenAIProperties properties) {
        this.properties = properties;
    }

    public RealtimeSessionInfo createSession() {
        String model = valueOrDefault(this.properties.getRealtimeModel(), DEFAULT_REALTIME_MODEL);
        JsonObject payload = new JsonObject();
        JsonObject session = new JsonObject();
        session.addProperty("type", "realtime");
        session.addProperty("model", model);
        session.add("output_modalities", GSON.toJsonTree(new String[] { "audio" }));
        JsonObject transcription = new JsonObject();
        transcription.addProperty("model", "whisper-1");
        JsonObject audioInput = new JsonObject();
        audioInput.add("transcription", transcription);
        JsonObject audio = new JsonObject();
        audio.add("input", audioInput);
        session.add("audio", audio);
        payload.add("session", session);

        return createClientSecret(payload, model, "realtime session");
    }

    public RealtimeSessionInfo createTranscriptionSession() {
        String model = valueOrDefault(this.properties.getRealtimeTranscriptionModel(),
                DEFAULT_REALTIME_TRANSCRIPTION_MODEL);
        JsonObject payload = new JsonObject();
        JsonObject session = new JsonObject();
        session.addProperty("type", "transcription");
        JsonObject transcription = new JsonObject();
        transcription.addProperty("model", model);
        addOptionalProperty(transcription, "language", this.properties.getRealtimeTranscriptionLanguage());
        addOptionalProperty(transcription, "delay", this.properties.getRealtimeTranscriptionDelay());
        JsonObject audioInput = new JsonObject();
        audioInput.add("transcription", transcription);
        JsonObject audio = new JsonObject();
        audio.add("input", audioInput);
        session.add("audio", audio);
        payload.add("session", session);

        return createClientSecret(payload, model, "realtime transcription session");
    }

    private RealtimeSessionInfo createClientSecret(JsonObject payload, String model, String sessionKind) {
        OpenAIProperties props = this.properties;
        if (!"openai".equals(props.getOpenaivsazureopenai())) {
            throw new RuntimeException(sessionKind + " creation is only supported for openai at the moment");
        }

        String clientSecretUrl = valueOrDefault(props.getRealtimeClientSecretUrl(),
                DEFAULT_REALTIME_CLIENT_SECRET_URL);
        String callsUrl = valueOrDefault(props.getRealtimeCallsUrl(), DEFAULT_REALTIME_CALLS_URL);

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(new URI(clientSecretUrl))
                    .header(props.headerKeyNameForAPIKey(), props.getKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)));
            if (isPresent(props.getRealtimeSafetyIdentifier())) {
                requestBuilder.header("OpenAI-Safety-Identifier", props.getRealtimeSafetyIdentifier().trim());
            }
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < HttpURLConnection.HTTP_OK || response.statusCode() >= 300) {
                throw new RuntimeException(
                        "unable to create " + sessionKind + " - http request returned status code: "
                                + response.statusCode()
                                + " (\n\t"
                                + response.body() + "\n\t" + response.toString() + "\n)");
            }

            JsonObject jsonResponse = GSON.fromJson(response.body(), JsonObject.class);
            if (jsonResponse == null || !jsonResponse.has("value") || jsonResponse.get("value").isJsonNull()) {
                throw new RuntimeException(sessionKind + " client secret response missing value: " + jsonResponse);
            }
            String clientSecretValue = jsonResponse.get("value").getAsString();
            return new RealtimeSessionInfo(clientSecretValue, model, callsUrl);
        } catch (Exception e) {
            throw new RuntimeException("unable to create " + sessionKind, e);
        }
    }

    private static void addOptionalProperty(JsonObject object, String name, String value) {
        if (isPresent(value)) {
            object.addProperty(name, value.trim());
        }
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return isPresent(value) ? value.trim() : defaultValue;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

