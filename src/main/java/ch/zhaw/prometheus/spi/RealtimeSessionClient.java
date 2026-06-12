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

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();
    private final OpenAIProperties properties;

    public RealtimeSessionClient(OpenAIProperties properties) {
        this.properties = properties;
    }

    public RealtimeSessionInfo createSession() {
        OpenAIProperties props = this.properties;
        if (!"openai".equals(props.getOpenaivsazureopenai())) {
            throw new RuntimeException("realtime session creation is only supported for openai at the moment");
        }

        String model = props.getRealtimeModel() != null ? props.getRealtimeModel() : DEFAULT_REALTIME_MODEL;
        String clientSecretUrl = props.getRealtimeClientSecretUrl() != null ? props.getRealtimeClientSecretUrl()
                : DEFAULT_REALTIME_CLIENT_SECRET_URL;
        String callsUrl = props.getRealtimeCallsUrl() != null ? props.getRealtimeCallsUrl() : DEFAULT_REALTIME_CALLS_URL;

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

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(clientSecretUrl))
                    .header(props.headerKeyNameForAPIKey(), props.getKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                    .build();
            HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < HttpURLConnection.HTTP_OK || response.statusCode() >= 300) {
                throw new RuntimeException(
                        "unable to create realtime session - http request returned status code: "
                                + response.statusCode()
                                + " (\n\t"
                                + response.body() + "\n\t" + response.toString() + "\n)");
            }

            JsonObject jsonResponse = GSON.fromJson(response.body(), JsonObject.class);
            if (jsonResponse == null || !jsonResponse.has("value") || jsonResponse.get("value").isJsonNull()) {
                throw new RuntimeException("realtime client secret response missing value: " + jsonResponse);
            }
            String clientSecretValue = jsonResponse.get("value").getAsString();
            return new RealtimeSessionInfo(clientSecretValue, model, callsUrl);
        } catch (Exception e) {
            throw new RuntimeException("unable to create realtime session", e);
        }
    }
}

