package ch.zhaw.prometheus.spi;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.springframework.stereotype.Component;

@Component
public class RealtimeSessionClient {

    private static final String DEFAULT_REALTIME_CLIENT_SECRET_URL = "https://api.openai.com/v1/realtime/client_secrets";
    private static final String DEFAULT_REALTIME_CALLS_URL = "https://api.openai.com/v1/realtime/calls";
    private static final String DEFAULT_REALTIME_MODEL = "gpt-realtime-2";
    private static final String DEFAULT_REALTIME_INPUT_TRANSCRIPTION_MODEL = "gpt-4o-transcribe";
    private static final String DEFAULT_REALTIME_TRANSCRIPTION_MODEL = "gpt-realtime-whisper";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private final OpenAIProperties properties;

    public RealtimeSessionClient(OpenAIProperties properties) {
        this.properties = properties;
    }

    public RealtimeCallInfo createCall(String offerSdp, RealtimeCallConfig config) {
        if (!isPresent(offerSdp)) {
            throw new IllegalArgumentException("offer SDP must not be blank");
        }
        OpenAIProperties props = this.properties;
        if (!"openai".equals(props.getOpenaivsazureopenai())) {
            throw new RuntimeException("realtime call creation is only supported for openai at the moment");
        }

        String model = valueOrDefault(this.properties.getRealtimeModel(), DEFAULT_REALTIME_MODEL);
        String callsUrl = valueOrDefault(props.getRealtimeCallsUrl(), DEFAULT_REALTIME_CALLS_URL);
        String inputTranscriptionModel = valueOrDefault(this.properties.getRealtimeInputTranscriptionModel(),
                DEFAULT_REALTIME_INPUT_TRANSCRIPTION_MODEL);
        JsonObject session = realtimeSessionPayload(model, inputTranscriptionModel, config);

        String boundary = "----prometheus-realtime-" + UUID.randomUUID();
        String body = multipartCallBody(boundary, offerSdp, GSON.toJson(session));
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(new URI(callsUrl))
                    .header(props.headerKeyNameForAPIKey(), props.getKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (isPresent(props.getRealtimeSafetyIdentifier())) {
                requestBuilder.header("OpenAI-Safety-Identifier", props.getRealtimeSafetyIdentifier().trim());
            }
            HttpResponse<String> response = this.httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < HttpURLConnection.HTTP_OK || response.statusCode() >= 300) {
                throw new RuntimeException(
                        "unable to create realtime call - http request returned status code: "
                                + response.statusCode()
                                + " (\n\t"
                                + response.body() + "\n\t" + response.toString() + "\n)");
            }

            String callId = extractCallId(response.headers().firstValue("Location").orElse(null));
            if (!isPresent(callId)) {
                throw new RuntimeException("realtime call response missing Location call id");
            }
            return new RealtimeCallInfo(response.body(), model, callId, sidebandUrl(callsUrl, callId));
        } catch (Exception e) {
            throw new RuntimeException("unable to create realtime call", e);
        }
    }

    private static JsonObject realtimeSessionPayload(String model, String inputTranscriptionModel,
            RealtimeCallConfig config) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "realtime");
        payload.addProperty("model", model);
        payload.add("output_modalities", GSON.toJsonTree(new String[] { "audio" }));
        if (config != null && isPresent(config.getInstructions())) {
            payload.addProperty("instructions", config.getInstructions().trim());
        }
        JsonObject transcription = new JsonObject();
        transcription.addProperty("model", valueOrDefault(inputTranscriptionModel,
                DEFAULT_REALTIME_INPUT_TRANSCRIPTION_MODEL));
        if (config != null) {
            addOptionalProperty(transcription, "language", config.getLanguageCode());
        }
        JsonObject audioInput = new JsonObject();
        audioInput.add("transcription", transcription);
        String turnDetection = config == null ? null : config.getTurnDetection();
        if (isPresent(turnDetection)) {
            JsonObject vad = new JsonObject();
            vad.addProperty("type", turnDetection.trim());
            if ("server_vad".equals(turnDetection.trim())) {
                addOptionalNumber(vad, "threshold", config == null ? null : config.getVadThreshold());
                addOptionalNumber(vad, "prefix_padding_ms", config == null ? null : config.getVadPrefixPaddingMs());
                addOptionalNumber(vad, "silence_duration_ms", config == null ? null
                        : config.getVadSilenceDurationMs());
            } else if ("semantic_vad".equals(turnDetection.trim())) {
                addOptionalProperty(vad, "eagerness", config == null ? null : config.getVadEagerness());
            }
            vad.addProperty("create_response", false);
            vad.addProperty("interrupt_response", config != null && config.isVadInterruptResponse());
            audioInput.add("turn_detection", vad);
        }
        addInputNoiseReduction(audioInput, config);
        JsonObject audio = new JsonObject();
        audio.add("input", audioInput);
        if (config != null && (isPresent(config.getVoice()) || config.getOutputSpeed() != null)) {
            JsonObject audioOutput = new JsonObject();
            if (isPresent(config.getVoice())) {
                audioOutput.addProperty("voice", config.getVoice().trim());
            }
            addOptionalNumber(audioOutput, "speed", config.getOutputSpeed());
            audio.add("output", audioOutput);
        }
        addSessionTuning(payload, config);
        payload.add("audio", audio);
        return payload;
    }

    public RealtimeSessionInfo createTranscriptionSession() {
        return this.createTranscriptionSession(null);
    }

    public RealtimeSessionInfo createTranscriptionSession(String languageCode) {
        String model = valueOrDefault(this.properties.getRealtimeTranscriptionModel(),
                DEFAULT_REALTIME_TRANSCRIPTION_MODEL);
        JsonObject payload = new JsonObject();
        JsonObject session = new JsonObject();
        session.addProperty("type", "transcription");
        JsonObject transcription = new JsonObject();
        transcription.addProperty("model", model);
        addOptionalProperty(transcription, "language", valueOrDefault(languageCode,
                this.properties.getRealtimeTranscriptionLanguage()));
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

    private static void addOptionalNumber(JsonObject object, String name, Number value) {
        if (value != null) {
            object.addProperty(name, value);
        }
    }

    private static void addInputNoiseReduction(JsonObject audioInput, RealtimeCallConfig config) {
        if (config == null || !isPresent(config.getInputNoiseReduction())) {
            return;
        }
        String noiseReduction = config.getInputNoiseReduction().trim();
        if ("off".equals(noiseReduction)) {
            audioInput.add("noise_reduction", JsonNull.INSTANCE);
            return;
        }
        JsonObject inputNoiseReduction = new JsonObject();
        inputNoiseReduction.addProperty("type", noiseReduction);
        audioInput.add("noise_reduction", inputNoiseReduction);
    }

    private static void addSessionTuning(JsonObject payload, RealtimeCallConfig config) {
        if (config == null) {
            return;
        }
        if (isPresent(config.getReasoningEffort())) {
            JsonObject reasoning = new JsonObject();
            reasoning.addProperty("effort", config.getReasoningEffort().trim());
            payload.add("reasoning", reasoning);
        }
        addOptionalNumber(payload, "max_output_tokens", config.getMaxOutputTokens());
        if (config.isIncludeInputTranscriptionLogprobs()) {
            payload.add("include", GSON.toJsonTree(new String[] { "item.input_audio_transcription.logprobs" }));
        }
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return isPresent(value) ? value.trim() : defaultValue;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String multipartCallBody(String boundary, String offerSdp, String sessionJson) {
        String line = "\r\n";
        return "--" + boundary + line
                + "Content-Disposition: form-data; name=\"sdp\"" + line
                + "Content-Type: application/sdp" + line
                + line
                + offerSdp + line
                + "--" + boundary + line
                + "Content-Disposition: form-data; name=\"session\"" + line
                + "Content-Type: application/json" + line
                + line
                + sessionJson + line
                + "--" + boundary + "--" + line;
    }

    private static String extractCallId(String location) {
        if (!isPresent(location)) {
            return null;
        }
        String value = location.trim();
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        int slashIndex = value.lastIndexOf('/');
        return slashIndex >= 0 ? value.substring(slashIndex + 1) : value;
    }

    private static String sidebandUrl(String callsUrl, String callId) throws Exception {
        URI uri = new URI(callsUrl);
        String scheme = "http".equalsIgnoreCase(uri.getScheme()) ? "ws" : "wss";
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            path = "/v1/realtime";
        } else if (path.endsWith("/calls")) {
            path = path.substring(0, path.length() - "/calls".length());
        }
        if (path.isBlank()) {
            path = "/";
        }
        String query = "call_id=" + URLEncoder.encode(callId, StandardCharsets.UTF_8);
        return new URI(scheme, uri.getAuthority(), path, query, null).toString();
    }
}

