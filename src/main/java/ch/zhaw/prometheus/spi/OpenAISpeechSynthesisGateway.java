package ch.zhaw.prometheus.spi;

import java.io.InputStream;
import ch.zhaw.prometheus.logging.LatencyTrace;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

@Component
public class OpenAISpeechSynthesisGateway implements SpeechSynthesisGateway {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final OpenAIProperties openAIProperties;
    private final SpeechSynthesisProperties speechProperties;

    public OpenAISpeechSynthesisGateway(OpenAIProperties openAIProperties,
            SpeechSynthesisProperties speechProperties) {
        this.openAIProperties = openAIProperties;
        this.speechProperties = speechProperties;
    }

    @Override
    public SpeechAudio synthesize(String text, String voice, double speed) {
        return synthesize(text, voice, speed, SpeechAudioFormat.MP3);
    }

    @Override
    public SpeechAudio synthesize(String text, String voice, double speed, SpeechAudioFormat format) {
        java.util.Objects.requireNonNull(format, "speech format");
        long start = LatencyTrace.now();
        boolean success = false;
        try {
            JsonObject payload = new JsonObject();
            if ("openai".equals(this.openAIProperties.getOpenaivsazureopenai())) {
                payload.addProperty("model", this.speechProperties.getModel());
            }
            payload.addProperty("input", text);
            payload.addProperty("voice", voice);
            payload.addProperty("response_format", format.wireValue());
            payload.addProperty("speed", speed);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(this.speechProperties.getUrl()))
                    .header(this.openAIProperties.headerKeyNameForAPIKey(), this.openAIProperties.getKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                    .build();
            HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                response.body().close();
                throw new SpeechSynthesisException(
                        "OpenAI Speech request returned status code " + response.statusCode());
            }
            String contentType = response.headers().firstValue("Content-Type").orElse(format.contentType());
            if (format == SpeechAudioFormat.PCM) {
                String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
                if (!mediaType.equals("audio/pcm") && !mediaType.equals("application/octet-stream")) {
                    response.body().close();
                    throw new SpeechSynthesisException("speech provider returned an unexpected PCM content type");
                }
                contentType = format.contentType();
            }
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            success = true;
            return SpeechAudio.streaming(response.body(), contentType, contentLength);
        } catch (SpeechSynthesisException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new SpeechSynthesisException("unable to request OpenAI Speech synthesis", failure);
        } finally {
            LatencyTrace.record("speech_headers", LatencyTrace.elapsedMs(start), success,
                    null, null, speechProperties.getModel(), null, null, null);
            org.slf4j.LoggerFactory.getLogger(getClass()).info(
                    "latency trace={} stage=speech_headers model={} format={} status={} durationMs={}",
                    LatencyTrace.currentId(), speechProperties.getModel(), format.wireValue(), success ? "ok" : "error", LatencyTrace.elapsedMs(start));
        }
    }
}
