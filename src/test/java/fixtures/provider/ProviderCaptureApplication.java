package fixtures.provider;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import com.google.gson.*;

/** Local acceptance only. Synthetic provider evidence never enters production logs. */
public final class ProviderCaptureApplication {
    public static void main(String[] args) {
        new org.springframework.boot.SpringApplication(ch.zhaw.prometheus.PrometheusApplication.class, Capture.class).run(args);
    }
    @Configuration(proxyBeanMethods = false)
    static class Capture {
        @Bean static BeanPostProcessor providerRecorder() {
            var count = new AtomicInteger();
            var output = Path.of(System.getenv("PROMETHEUS_PROVIDER_ARTIFACT"));
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof LanguageModelGateway)) return bean;
                    return Proxy.newProxyInstance(bean.getClass().getClassLoader(), new Class<?>[]{LanguageModelGateway.class}, (proxy, method, args) -> {
                        boolean inference = java.util.Set.of("infer", "complete", "decide", "extract", "summarise", "summariseOffline").contains(method.getName());
                        if (inference && count.incrementAndGet() > 60) throw new IllegalStateException("Provider journey request budget exhausted");
                        try {
                            Object result = method.invoke(bean, args);
                            if (inference) {
                                var record = new JsonObject(); record.addProperty("method", method.getName());
                                record.add("request", new Gson().toJsonTree(args)); record.add("result", new Gson().toJsonTree(result));
                                synchronized (count) { Files.writeString(output, record + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
                            }
                            return result;
                        } catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
                }
            };
        }
    }
}
