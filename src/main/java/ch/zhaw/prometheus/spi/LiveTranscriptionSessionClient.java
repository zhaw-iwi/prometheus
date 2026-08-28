package ch.zhaw.prometheus.spi;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import ch.zhaw.prometheus.application.LiveTranscriptionSettings;

@Component
public class LiveTranscriptionSessionClient {

    public static final String MODEL = "gpt-live-transcribe";
    public static final String SESSION_TYPE = "transcription";
    public static final int CLIENT_SECRET_TTL_SECONDS = 60;
    private static final String DEFAULT_CLIENT_SECRET_URL = "https://api.openai.com/v1/realtime/client_secrets";
    private static final String DEFAULT_WEBRTC_URL = "https://api.openai.com/v1/realtime/calls";

    private static final Gson GSON = new Gson();
    private final OpenAIProperties properties;
    private final LiveTranscriptionProviderPayloadBuilder payloadBuilder;
    private final HttpClient httpClient;

    @Autowired
    public LiveTranscriptionSessionClient(OpenAIProperties properties,
            LiveTranscriptionProviderPayloadBuilder payloadBuilder) {
        this(properties, payloadBuilder, HttpClient.newHttpClient());
    }

    LiveTranscriptionSessionClient(OpenAIProperties properties,
            LiveTranscriptionProviderPayloadBuilder payloadBuilder, HttpClient httpClient) {
        this.properties = properties;
        this.payloadBuilder = payloadBuilder;
        this.httpClient = httpClient;
    }

    public LiveTranscriptionSessionInfo createSession(LiveTranscriptionSettings settings) {
        if (!"openai".equals(this.properties.getOpenaivsazureopenai())) {
            throw new LiveTranscriptionProviderException(
                    "live transcription session creation is only supported for OpenAI");
        }
        String clientSecretUrl = valueOrDefault(this.properties.getLiveTranscriptionClientSecretUrl(),
                DEFAULT_CLIENT_SECRET_URL);
        String webRtcUrl = valueOrDefault(this.properties.getLiveTranscriptionWebRtcUrl(), DEFAULT_WEBRTC_URL);
        JsonObject payload = this.payloadBuilder.buildClientSecretEnvelope(settings, CLIENT_SECRET_TTL_SECONDS);
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(new URI(clientSecretUrl))
                    .header(this.properties.headerKeyNameForAPIKey(), this.properties.getKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)));
            if (isPresent(this.properties.getLiveTranscriptionSafetyIdentifier())) {
                request.header("OpenAI-Safety-Identifier", this.properties.getLiveTranscriptionSafetyIdentifier().trim());
            }
            HttpResponse<String> response = this.httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < HttpURLConnection.HTTP_OK || response.statusCode() >= 300) {
                throw new LiveTranscriptionProviderException(
                        "the live-transcription provider rejected session creation");
            }
            JsonObject body = GSON.fromJson(response.body(), JsonObject.class);
            if (body == null || !body.has("value") || body.get("value").isJsonNull()
                    || body.get("value").getAsString().isBlank()) {
                throw new LiveTranscriptionProviderException(
                        "the live-transcription provider returned an invalid client secret");
            }
            return new LiveTranscriptionSessionInfo(body.get("value").getAsString(), MODEL, webRtcUrl);
        } catch (LiveTranscriptionProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new LiveTranscriptionProviderException(
                    "unable to create a live-transcription session", exception);
        }
    }

    private static String valueOrDefault(String value, String fallback) {
        return isPresent(value) ? value.trim() : fallback;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
