package ch.zhaw.prometheus.spi.live;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ch.zhaw.prometheus.spi.OpenAIProperties;

@Component
public class OpenAILiveSessionGateway implements LiveSessionGateway {
    private static final int MAX_RESPONSE_BYTES = 131072;
    private final OpenAIProperties credentials;
    private final LiveProperties properties;
    private final HttpClient client;

    @Autowired
    public OpenAILiveSessionGateway(OpenAIProperties credentials, LiveProperties properties) {
        this(credentials, properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getRequestTimeoutMs())).build());
    }

    OpenAILiveSessionGateway(OpenAIProperties credentials, LiveProperties properties, HttpClient client) {
        this.credentials = credentials;
        this.properties = properties;
        this.client = client;
    }

    @Override public Session create(Request input) {
        JsonObject session = new JsonObject();
        session.addProperty("model", properties.getModel());
        session.addProperty("instructions", input.instructions());
        session.addProperty("store", false);
        session.add("input", input.input());
        JsonObject delegation = new JsonObject(); delegation.addProperty("type", "client");
        session.add("delegation", delegation);
        JsonObject output = new JsonObject(); output.addProperty("voice", input.voice());
        JsonObject audio = new JsonObject(); audio.add("output", output); session.add("audio", audio);
        JsonObject transport = new JsonObject(); transport.addProperty("type", "webrtc");
        transport.addProperty("sdp", input.sdp());
        JsonObject body = new JsonObject(); body.add("session", session); body.add("transport", transport);
        JsonObject result = request(URI.create(properties.getSessionsUrl()), body);
        String id = null;
        try {
            id = result.getAsJsonObject("session").get("id").getAsString();
            validateId(id);
            String sdp = result.getAsJsonObject("transport").get("sdp").getAsString();
            if (!sdp.startsWith("v=0") || sdp.length() > 65536) throw new IllegalArgumentException();
            return new Session(id, sdp);
        } catch (RuntimeException failure) {
            if (id != null && id.matches("[A-Za-z0-9_-]{1,200}")) {
                try { hangup(id); } catch (RuntimeException ignored) { /* Original failure remains authoritative. */ }
            }
            throw new LiveProviderException("Invalid Live session response");
        }
    }

    @Override public Connection attach(String sessionId, Consumer<JsonObject> receiver, Runnable disconnected) {
        URI httpUri = sessionUri(sessionId, "/attach");
        URI uri = URI.create(httpUri.toString().replaceFirst("^http", "ws"));
        Sideband listener = new Sideband(receiver, disconnected, properties.getRequestTimeoutMs());
        try {
            client.newWebSocketBuilder().connectTimeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                    .header("Authorization", authorization())
                    .buildAsync(uri, listener).get(properties.getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
            return listener;
        } catch (Exception failure) {
            listener.close();
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new LiveProviderException("Unable to attach Live sideband");
        }
    }

    @Override public void hangup(String sessionId) { request(sessionUri(sessionId, "/hangup"), null); }

    private URI sessionUri(String id, String suffix) {
        validateId(id);
        return URI.create(properties.getSessionsUrl().replaceAll("/$", "") + "/" + id + suffix);
    }

    private static void validateId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,200}")) throw new IllegalArgumentException("Invalid Live session identity");
    }

    private String authorization() {
        if (!"openai".equals(credentials.getOpenaivsazureopenai())
                || credentials.getKey() == null || credentials.getKey().equals("Bearer null")
                || credentials.getKey().equals("Bearer ")) throw new LiveProviderException("OpenAI Live credentials unavailable");
        return credentials.getKey();
    }

    private JsonObject request(URI uri, JsonObject body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
                    .header("Authorization", authorization()).header("Content-Type", "application/json")
                    .POST(body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body.toString())).build();
            // Complete the bounded body under the same request deadline, including slow response bodies.
            var pending = client.sendAsync(request, info -> new BoundedBodySubscriber(MAX_RESPONSE_BYTES));
            HttpResponse<byte[]> response;
            try { response = pending.get(properties.getRequestTimeoutMs(), TimeUnit.MILLISECONDS); }
            catch (Exception failure) {
                pending.cancel(true);
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new LiveProviderException("Live provider request deadline or transport failure");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw LiveProviderException.rejected(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
            if (response.body().length == 0) return new JsonObject();
            return JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (LiveProviderException failure) { throw failure;
        } catch (RuntimeException failure) { throw new LiveProviderException("Live provider request failed"); }
    }

    /** The listener owns only transport assembly; receivers must not block provider callbacks. */
    static final class Sideband implements WebSocket.Listener, Connection {
        private final Consumer<JsonObject> receiver;
        private final Runnable disconnected;
        private final int timeout;
        private final StringBuilder fragment = new StringBuilder();
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile WebSocket socket;
        private long heartbeatAt, pingSequence;
        private boolean pingPending;

        Sideband(Consumer<JsonObject> receiver, Runnable disconnected, int timeout) {
            this.receiver = receiver; this.disconnected = disconnected; this.timeout = timeout;
        }
        @Override public void onOpen(WebSocket socket) {
            this.socket = socket;
            if (closed.get()) { socket.abort(); return; }
            socket.request(1);
        }
        @Override public synchronized CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            if (closed.get()) return null;
            if (fragment.length() + data.length() > 262144) { fail(); return null; }
            fragment.append(data);
            if (last) {
                try { receiver.accept(JsonParser.parseString(fragment.toString()).getAsJsonObject()); }
                catch (RuntimeException invalid) { fail(); return null; }
                finally { fragment.setLength(0); }
            }
            socket.request(1);
            return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) { fail(); return null; }
        @Override public void onError(WebSocket socket, Throwable error) { fail(); }
        @Override public synchronized CompletionStage<?> onPong(WebSocket socket, java.nio.ByteBuffer message) {
            if (message.remaining() == Long.BYTES && message.getLong() == pingSequence) pingPending = false;
            socket.request(1); return null;
        }
        @Override public synchronized void heartbeat(long nowMs) {
            if (!isOpen()) return;
            if (pingPending) { if (nowMs - heartbeatAt >= 10000) fail(); return; }
            if (pingSequence != 0 && nowMs - heartbeatAt < 10000) return;
            heartbeatAt = nowMs; pingPending = true;
            var payload = java.nio.ByteBuffer.allocate(Long.BYTES).putLong(++pingSequence).flip();
            try { socket.sendPing(payload).whenComplete((ignored, error) -> { if (error != null) fail(); }); }
            catch (RuntimeException error) { fail(); }
        }
        private void fail() { if (closed.compareAndSet(false, true)) { if (socket != null) socket.abort(); disconnected.run(); } }
        @Override public void send(JsonObject event) {
            if (!isOpen()) throw new LiveProviderException("Live sideband disconnected");
            try { socket.sendText(event.toString(), true).get(timeout, TimeUnit.MILLISECONDS); }
            catch (Exception failure) {
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                fail(); throw new LiveProviderException("Live command failed");
            }
        }
        @Override public boolean isOpen() { return socket != null && !closed.get(); }
        @Override public void close() { if (closed.compareAndSet(false, true) && socket != null) socket.abort(); }
    }
}
