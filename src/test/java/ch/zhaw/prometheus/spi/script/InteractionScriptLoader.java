package ch.zhaw.prometheus.spi.script;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class InteractionScriptLoader {
    private InteractionScriptLoader() {
    }

    public static InteractionScript load(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("script location must not be blank");
        }
        String normalized = location.trim();
        try {
            String json = normalized.startsWith("classpath:")
                    ? readFromClasspath(normalized.substring("classpath:".length()))
                    : Files.readString(Path.of(normalized), StandardCharsets.UTF_8);
            return InteractionScript.fromJson(json);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to load interaction script from " + normalized, exception);
        }
    }

    private static String readFromClasspath(String resourcePath) throws IOException {
        String path = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IOException("classpath resource not found: " + path);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
