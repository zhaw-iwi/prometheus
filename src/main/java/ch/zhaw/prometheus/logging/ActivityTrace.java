package ch.zhaw.prometheus.logging;

import java.util.*;
import java.util.function.Supplier;

/** Explicit operation context; content-free and independent of model/task decisions. */
public final class ActivityTrace {
    public interface Sink {
        UUID agent();
        AutoCloseable stage(String name);
        void bind(UUID epoch, UUID session, UUID source);
        void cue(String reason, UUID source);
        void inference(String request, String purpose, String model, String effort, Integer input, Integer output, int requests);
        void event(UUID id);
        void failed(String reason);
    }
    private static final ThreadLocal<Sink> CURRENT = new ThreadLocal<>();
    private ActivityTrace() {}
    public static Sink current() { return CURRENT.get(); }
    public static Scope attach(Sink sink) { return new Scope(sink); }
    public static final class Scope implements AutoCloseable {
        private final Sink previous = CURRENT.get();
        private Scope(Sink sink) { if (sink == null) CURRENT.remove(); else CURRENT.set(sink); }
        @Override public void close() { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
    public interface Stage extends AutoCloseable { @Override void close(); }
    public static Stage stage(String stage) {
        AutoCloseable span = CURRENT.get() == null ? () -> {} : CURRENT.get().stage(stage);
        return () -> { try { span.close(); } catch (Exception ignored) { /* Never fail application work. */ } };
    }
    public static <T> T measure(String stage, Supplier<T> work) {
        AutoCloseable span = stage(stage);
        try { return work.get(); }
        finally { try { span.close(); } catch (Exception ignored) { /* Diagnostics cannot fail work. */ } }
    }
    public static void bind(UUID epoch, UUID session, UUID source) { if (CURRENT.get() != null) CURRENT.get().bind(epoch, session, source); }
    public static void cue(String reason, UUID source) { if (CURRENT.get() != null) CURRENT.get().cue(reason, source); }
    public static void failed(String reason) { if (CURRENT.get() != null) CURRENT.get().failed(reason); }
    public static void event(UUID id) { if (CURRENT.get() != null) CURRENT.get().event(id); }
    public static void inference(String request, String purpose, String model, String effort, Integer input, Integer output, int requests) {
        if (CURRENT.get() != null) CURRENT.get().inference(request, purpose, model, effort, input, output, requests);
    }
}
