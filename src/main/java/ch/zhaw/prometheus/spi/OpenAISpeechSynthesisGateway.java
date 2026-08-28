package ch.zhaw.prometheus.spi;

import java.io.InputStream;
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
        try {
            JsonObject payload = new JsonObject();
            if ("openai".equals(this.openAIProperties.getOpenaivsazureopenai())) {
                payload.addProperty("model", this.speechProperties.getModel());
            }
            payload.addProperty("input", text);
            payload.addProperty("voice", voice);
            payload.addProperty("response_format", "mp3");
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
            String contentType = response.headers().firstValue("Content-Type").orElse("audio/mpeg");
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            return SpeechAudio.streaming(response.body(), contentType, contentLength);
        } catch (SpeechSynthesisException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new SpeechSynthesisException("unable to request OpenAI Speech synthesis", failure);
        }
    }
}
