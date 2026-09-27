package ch.zhaw.prometheus.spi.live;

import java.util.function.Consumer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public interface LiveSessionGateway {
    record Request(String sdp, String voice, String instructions, JsonArray input) {
        @Override public String toString() { return "LiveRequest[voice=" + voice + "]"; }
    }
    record Session(String id, String sdp) {
        @Override public String toString() { return "LiveSession[redacted]"; }
    }
    interface Connection extends AutoCloseable {
        void send(JsonObject event);
        boolean isOpen();
        /** Nonblocking WebSocket ping/pong liveness, independent of speech activity. */
        default void heartbeat(long nowMs) {}
        @Override void close();
    }
    Session create(Request request);
    Connection attach(String sessionId, Consumer<JsonObject> receiver, Runnable disconnected);
    void hangup(String sessionId);
}
