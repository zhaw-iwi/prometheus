package fixtures.gptlive;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.bind.annotation.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.spi.*;
import ch.zhaw.prometheus.spi.live.LiveSessionGateway;

/** Test classpath only: real application/controller/runtime, synthetic providers. */
@TestConfiguration(proxyBeanMethods = false)
public class LiveSmokeConfiguration {
    @Bean @Primary public Provider offlineLiveGateway() { return new Provider(); }
    @Bean @Primary public LanguageModelGateway offlineLanguageGateway(Provider provider) {
        return new LanguageModelGateway() {
            public String infer(InferenceRequest request) {
                provider.inferences.add(request);
                String prompt = request.messages().stream().map(PromptMessage::getContent).reduce("", (a, b) -> a + "\n" + b);
                if (request.output() == InferenceRequest.Output.BOOLEAN)
                    return Boolean.toString(prompt.contains("Return true if the person is clearly ready to start a round"));
                if (request.output() == InferenceRequest.Output.TEXT) return "Ready for the next interaction.";
                if (prompt.contains("Produce the current embodiment")) return """
                        {"nonVerbal":{"gesture":"ACKNOWLEDGE","facialExpression":{"type":"attentive","intensity":0.4},
                        "gaze":{"direction":"toward_user","focus":"person"},"motion":{"stillness":0.8,"energy":0.2}},
                        "motion":{"handSign":"paper"},"display":{"text":"Ready"}}
                        """;
                if (prompt.contains("The output must omit speech completely")) return "{\"nonVerbal\":{\"gesture\":\"NONE\"}}";
                return "{\"speech\":\"Ready for the next interaction.\",\"nonVerbal\":{\"gesture\":\"NONE\"}}";
            }
            public String complete(List<PromptMessage> messages) { return "Ready for the next interaction."; }
            public boolean decide(List<PromptMessage> messages) {
                return messages.stream().anyMatch(message -> message.getContent().contains("Return true if the person is clearly ready to start a round"));
            }
            public JsonElement extract(List<PromptMessage> messages) { return new JsonObject(); }
            public JsonElement summarise(List<PromptMessage> messages) { return new JsonObject(); }
            public String summariseOffline(List<PromptMessage> messages) { return "Synthetic summary"; }
        };
    }
    @Bean @Primary public SpeechSynthesisGateway offlineSpeechGateway(Provider provider) {
        return new SpeechSynthesisGateway() {
            public SpeechAudio synthesize(String text, String voice, double speed) { return synthesize(text, voice, speed, SpeechAudioFormat.MP3); }
            public SpeechAudio synthesize(String text, String voice, double speed, SpeechAudioFormat format) {
                provider.tts.incrementAndGet(); return new SpeechAudio(new byte[4800], format.contentType());
            }
        };
    }
    public static final class Provider implements LiveSessionGateway, AutoCloseable {
        public final Map<String, Call> calls = new ConcurrentHashMap<>();
        public final AtomicInteger tts = new AtomicInteger();
        public final List<InferenceRequest> inferences = new CopyOnWriteArrayList<>();
        private final ScheduledExecutorService audio = Executors.newSingleThreadScheduledExecutor(work -> {
            var thread = new Thread(work, "synthetic-live-audio"); thread.setDaemon(true); return thread;
        });
        public volatile String latest;
        public Session create(Request request) {
            String id = "fixture_" + UUID.randomUUID(); var call = new Call(); call.request = request; calls.put(id, call); latest = id;
            return new Session(id, "v=0 synthetic-answer");
        }
        public Connection attach(String id, Consumer<JsonObject> receive, Runnable disconnected) {
            Call call = calls.get(id); call.receive = receive;
            return new Connection() {
                public boolean isOpen() { return !call.closed; }
                public void close() { call.closed = true; }
                public void send(JsonObject event) {
                    call.sent.add(event.deepCopy()); String type = event.get("type").getAsString();
                    var ack = new JsonObject(); ack.addProperty("type", type.equals("session.close") ? "session.closed"
                        : type.endsWith(".append") ? type + "ed" : type + "d");
                    if (event.has("event_id")) ack.add("client_event_id", event.get("event_id")); receive.accept(ack);
                    if (type.equals("session.commentary.append")) audio.schedule(() -> speak(id, "ASSISTANT", "The round is ready.", null), 50, TimeUnit.MILLISECONDS);
                }
            };
        }
        public void hangup(String id) { calls.get(id).closed = true; }
        public void speak(String id, String speaker, String text, String receipt) {
            Call call = calls.get(id); if (call == null || call.closed) throw new IllegalArgumentException("Synthetic call unavailable");
            int sequence = call.sequence.incrementAndGet(); long offset = sequence * 3000L;
            String prefix = "USER".equals(speaker) ? "input" : "output";
            var transcript = new JsonObject(); transcript.addProperty("type", "session." + prefix + "_transcript.delta");
            transcript.addProperty("event_id", receipt == null ? "fixture_receipt_" + sequence : receipt);
            transcript.addProperty("delta", text); transcript.addProperty("start_ms", offset); transcript.addProperty("end_ms", offset + 500);
            call.receive.accept(transcript);
            for (int i = 1; i <= 10; i++) {
                int chunk = i;
                audio.schedule(() -> {
                    if (call.closed) return;
                    var event = new JsonObject(); event.addProperty("type", "session." + prefix + "_audio." + (prefix.equals("input") ? "append" : "delta"));
                    event.addProperty(prefix.equals("input") ? "audio" : "delta", Base64.getEncoder().encodeToString(new byte[4800]));
                    event.addProperty("start_ms", offset + 500 + (chunk - 1) * 100); event.addProperty("end_ms", offset + 500 + chunk * 100);
                    call.receive.accept(event);
                }, i * 100L, TimeUnit.MILLISECONDS);
            }
        }
        public void close() { audio.shutdownNow(); }
        public static final class Call {
            public volatile Consumer<JsonObject> receive;
            public volatile boolean closed;
            public Request request;
            public final List<JsonObject> sent = new CopyOnWriteArrayList<>();
            final AtomicInteger sequence = new AtomicInteger();
        }
    }
    @RestController
    public static final class FixtureApi {
        private final Provider provider;
        FixtureApi(Provider provider) { this.provider = provider; }
        public record Speech(String speaker, String text, String receipt) {}
        @PostMapping("/__live-fixture/speak/{id}") public Map<String, Boolean> speak(@PathVariable String id, @RequestBody Speech speech) {
            provider.speak(id, speech.speaker(), speech.text(), speech.receipt()); return Map.of("accepted", true);
        }
        @GetMapping("/__live-fixture/latest") public Map<String, Object> latest() {
            var call = provider.latest == null ? null : provider.calls.get(provider.latest);
            return Map.of("providerId", provider.latest == null ? "" : provider.latest, "ttsCalls", provider.tts.get(),
                "commands", call == null ? List.of() : call.sent.stream().map(JsonObject::toString).toList(),
                "input", call == null ? "" : call.request.input().toString());
        }
    }
}
