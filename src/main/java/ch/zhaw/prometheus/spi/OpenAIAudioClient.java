package ch.zhaw.prometheus.spi;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Component
public class OpenAIAudioClient {
    private static final String DEFAULT_AUDIO_TRANSCRIPTIONS_URL = "https://api.openai.com/v1/audio/transcriptions";
    private static final String DEFAULT_AUDIO_SPEECH_URL = "https://api.openai.com/v1/audio/speech";
    private static final String DEFAULT_TRANSCRIPTION_MODEL = "gpt-4o-transcribe";
    private static final String DEFAULT_SPEECH_MODEL = "gpt-4o-mini-tts";
    private static final String DEFAULT_VOICE = "marin";
    private static final String DEFAULT_SPEECH_CONTENT_TYPE = "audio/mpeg";

    private static final Gson GSON = new Gson();

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final OpenAIProperties properties;

    public OpenAIAudioClient(OpenAIProperties properties) {
        this.properties = properties;
    }

    public String transcribe(byte[] audio, String filename, String contentType, String languageCode) {
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("audio must not be empty");
        }
        requireOpenAI("audio transcription");
        String boundary = "----prometheus-audio-" + UUID.randomUUID();
        byte[] body = multipartTranscriptionBody(boundary, audio, filename, contentType,
                transcriptionModel(), languageCode);
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(new URI(valueOrDefault(this.properties.getAudioTranscriptionsUrl(),
                            DEFAULT_AUDIO_TRANSCRIPTIONS_URL)))
                    .header(this.properties.headerKeyNameForAPIKey(), this.properties.getKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            addSafetyIdentifier(requestBuilder);
            HttpResponse<String> response = this.httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < HttpURLConnection.HTTP_OK || response.statusCode() >= 300) {
                throw new RuntimeException("audio transcription failed with status " + response.statusCode()
                        + " (" + response.body() + ")");
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!json.has("text") || json.get("text").isJsonNull()) {
                throw new RuntimeException("audio transcription response missing text: " + response.body());
            }
            return json.get("text").getAsString();
        } catch (Exception failure) {
            throw new RuntimeException("unable to transcribe audio", failure);
        }
    }

    public GeneratedSpeechAudio createSpeech(String text, String voice) {
        if (!isPresent(text)) {
            throw new IllegalArgumentException("speech text must not be blank");
        }
        requireOpenAI("audio speech generation");
        JsonObject payload = new JsonObject();
        payload.addProperty("model", valueOrDefault(this.properties.getSpeechModel(), DEFAULT_SPEECH_MODEL));
        payload.addProperty("input", text.trim());
        payload.addProperty("voice", valueOrDefault(voice, DEFAULT_VOICE));
        payload.addProperty("response_format", "mp3");
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(new URI(valueOrDefault(this.properties.getAudioSpeechUrl(), DEFAULT_AUDIO_SPEECH_URL)))
                    .header(this.properties.headerKeyNameForAPIKey(), this.properties.getKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8));
            addSafetyIdentifier(requestBuilder);
            HttpResponse<byte[]> response = this.httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < HttpURLConnection.HTTP_OK || response.statusCode() >= 300) {
                throw new RuntimeException("audio speech generation failed with status " + response.statusCode());
            }
            String responseContentType = response.headers().firstValue("Content-Type")
                    .orElse(DEFAULT_SPEECH_CONTENT_TYPE);
            return new GeneratedSpeechAudio(response.body(), responseContentType);
        } catch (Exception failure) {
            throw new RuntimeException("unable to create speech audio", failure);
        }
    }

    private String transcriptionModel() {
        return valueOrDefault(this.properties.getRecordedSpeechTranscriptionModel(),
                valueOrDefault(this.properties.getRealtimeInputTranscriptionModel(), DEFAULT_TRANSCRIPTION_MODEL));
    }

    private void requireOpenAI(String operation) {
        if (!"openai".equals(this.properties.getOpenaivsazureopenai())) {
            throw new RuntimeException(operation + " is only supported for openai at the moment");
        }
    }

    private void addSafetyIdentifier(HttpRequest.Builder requestBuilder) {
        if (isPresent(this.properties.getRealtimeSafetyIdentifier())) {
            requestBuilder.header("OpenAI-Safety-Identifier", this.properties.getRealtimeSafetyIdentifier().trim());
        }
    }

    private static byte[] multipartTranscriptionBody(String boundary, byte[] audio, String filename,
            String contentType, String model, String languageCode) {
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            writeField(body, boundary, "model", model);
            writeField(body, boundary, "response_format", "json");
            if (isPresent(languageCode)) {
                writeField(body, boundary, "language", languageCode.trim());
            }
            writeFile(body, boundary, "file", valueOrDefault(filename, "speech-turn.webm"),
                    valueOrDefault(contentType, "audio/webm"), audio);
            body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return body.toByteArray();
        } catch (Exception failure) {
            throw new RuntimeException("unable to build audio transcription request body", failure);
        }
    }

    private static void writeField(ByteArrayOutputStream body, String boundary, String name, String value)
            throws Exception {
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write(value.getBytes(StandardCharsets.UTF_8));
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFile(ByteArrayOutputStream body, String boundary, String name, String filename,
            String contentType, byte[] bytes) throws Exception {
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(bytes);
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return isPresent(value) ? value.trim() : defaultValue;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
