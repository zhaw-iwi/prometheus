package fixtures.provider;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import ch.zhaw.prometheus.spi.live.LiveSessionGateway;
import ch.zhaw.prometheus.logging.LatencyTrace;
import com.google.gson.*;

/** Local acceptance only. Synthetic provider evidence never enters production logs. */
public final class ProviderCaptureApplication {
    public static void main(String[] args) {
        new org.springframework.boot.SpringApplication(ch.zhaw.prometheus.PrometheusApplication.class, Capture.class).run(args);
    }
    @Configuration(proxyBeanMethods = false)
    static class Capture {
        @Bean static BeanPostProcessor providerRecorder() {
            int maxRequests = Integer.parseInt(System.getenv().getOrDefault("PROMETHEUS_PROVIDER_MAX_REQUESTS", "60"));
            long maxTokens = Long.parseLong(System.getenv().getOrDefault("PROMETHEUS_PROVIDER_MAX_TOKENS", "1000000"));
            int maxSessions = Integer.parseInt(System.getenv().getOrDefault("PROMETHEUS_PROVIDER_MAX_SESSIONS", "1"));
            return recorder(Path.of(System.getenv("PROMETHEUS_PROVIDER_ARTIFACT")), maxRequests, maxTokens, maxSessions);
        }
        static BeanPostProcessor recorder(Path output, int maxRequests, long maxTokens, int maxSessions) {
            var count = new AtomicInteger();
            var sessions = new AtomicInteger(); var tokens = new AtomicLong();
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if (bean instanceof LiveSessionGateway) return Proxy.newProxyInstance(bean.getClass().getClassLoader(),
                            new Class<?>[]{LiveSessionGateway.class}, (proxy, method, args) -> {
                                if (method.getName().equals("create") && sessions.incrementAndGet() > maxSessions)
                                    throw new IllegalStateException("Provider session budget exhausted");
                                try { return method.invoke(bean, args); }
                                catch (InvocationTargetException failure) { throw failure.getCause(); }
                            });
                    if (!(bean instanceof LanguageModelGateway)) return bean;
                    return Proxy.newProxyInstance(bean.getClass().getClassLoader(), new Class<?>[]{LanguageModelGateway.class}, (proxy, method, args) -> {
                        boolean inference = java.util.Set.of("infer", "complete", "decide", "extract", "summarise", "summariseOffline").contains(method.getName());
                        if (inference && (count.incrementAndGet() > maxRequests || tokens.get() >= maxTokens))
                            throw new IllegalStateException("Provider journey request/token budget exhausted");
                        if (inference && new Gson().toJson(args).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 80000)
                            throw new IllegalStateException("Provider request input budget exhausted");
                        var record = new JsonObject();
                        try (var trace = inference ? new LatencyTrace(null, ignored -> {}) : null) {
                            try {
                                Object result = method.invoke(bean, args);
                                if (inference) record.add("result", new Gson().toJsonTree(result));
                                return result;
                            } catch (InvocationTargetException failure) {
                                record.addProperty("failureType", failure.getCause().getClass().getSimpleName());
                                throw failure.getCause();
                            } finally {
                                if (inference) {
                                    record.addProperty("method", method.getName()); record.add("request", new Gson().toJsonTree(args));
                                    var timing = JsonParser.parseString(new String(java.util.Base64.getDecoder().decode(LatencyTrace.responseHeader()), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                                    record.add("timing", timing);
                                    for (var item : timing.getAsJsonArray("spans")) {
                                        var span = item.getAsJsonObject();
                                        for (String key : java.util.List.of("promptTokens", "completionTokens"))
                                            if (span.has(key)) tokens.addAndGet(span.get(key).getAsLong());
                                    }
                                    record.addProperty("cumulativeTokens", tokens.get());
                                    synchronized (count) { Files.writeString(output, record + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
                                }
                            }
                        }
                    });
                }
            };
        }
    }
}
