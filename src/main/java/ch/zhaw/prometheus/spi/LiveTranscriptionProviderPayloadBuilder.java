package ch.zhaw.prometheus.spi;

import org.springframework.stereotype.Component;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import ch.zhaw.prometheus.application.LiveTranscriptionSettings;

@Component
public class LiveTranscriptionProviderPayloadBuilder {

    public JsonObject buildClientSecretEnvelope(LiveTranscriptionSettings settings, int clientSecretTtlSeconds) {
        if (settings == null) {
            throw new IllegalArgumentException("live-transcription settings are required");
        }
        if (clientSecretTtlSeconds < 10 || clientSecretTtlSeconds > 7200) {
            throw new IllegalArgumentException("client secret TTL must be between 10 and 7200 seconds");
        }

        JsonObject expiresAfter = new JsonObject();
        expiresAfter.addProperty("anchor", "created_at");
        expiresAfter.addProperty("seconds", clientSecretTtlSeconds);

        JsonObject transcription = new JsonObject();
        transcription.addProperty("model", LiveTranscriptionSessionClient.MODEL);
        if (!settings.transcriptionPrompt().isEmpty()) {
            transcription.addProperty("prompt", settings.transcriptionPrompt());
        }
        if (!settings.transcriptionKeywords().isEmpty()) {
            JsonArray keywords = new JsonArray();
            settings.transcriptionKeywords().forEach(keywords::add);
            transcription.add("keywords", keywords);
        }
        JsonArray languages = new JsonArray();
        settings.languages().forEach(language -> languages.add(language.wireValue()));
        transcription.add("languages", languages);
        transcription.addProperty("delay", settings.transcriptionDelay().wireValue());

        JsonObject input = new JsonObject();
        input.add("transcription", transcription);
        if (settings.noiseReduction() == LiveTranscriptionSettings.NoiseReduction.OFF) {
            input.add("noise_reduction", JsonNull.INSTANCE);
        } else {
            JsonObject noiseReduction = new JsonObject();
            noiseReduction.addProperty("type", settings.noiseReduction().wireValue());
            input.add("noise_reduction", noiseReduction);
        }
        input.add("turn_detection", JsonNull.INSTANCE);

        JsonObject audio = new JsonObject();
        audio.add("input", input);
        JsonObject session = new JsonObject();
        session.addProperty("type", LiveTranscriptionSessionClient.SESSION_TYPE);
        session.add("audio", audio);

        JsonObject envelope = new JsonObject();
        envelope.add("expires_after", expiresAfter);
        envelope.add("session", session);
        return envelope;
    }
}
