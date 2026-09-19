package ch.zhaw.prometheus.spi;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Validates and resolves one effective route before any provider request is dispatched. */
public final class InferenceRouting {
    private InferenceRouting() {}
    public record Route(String model, String effort, String url, int timeoutMs, Integer maxCompletionTokens,
            boolean samplingParameters) {}

    public static Route resolve(OpenAIProperties properties, InferencePurpose purpose) {
        OpenAIProperties.InferenceRoute override = properties.getRoutes().get(purpose);
        String model = value(override == null ? null : override.getModel(), properties.getModel());
        boolean azure = "azureopenai".equals(properties.getOpenaivsazureopenai());
        if (azure && model == null) model = "azure-deployment";
        String effort = value(override == null ? null : override.getReasoningEffort(), properties.getReasoningEffort());
        String url = value(override == null ? null : override.getUrl(), properties.getUrl());
        int timeout = override != null && override.getTimeoutMs() != null
                ? override.getTimeoutMs() : properties.getRequestTimeoutMs();
        Integer tokens = override != null && override.getMaxCompletionTokens() != null
                ? override.getMaxCompletionTokens() : properties.getMaxCompletionTokens();
        if (timeout < 1 || timeout > 300000 || (tokens != null && tokens < 1)) {
            throw new IllegalArgumentException("Inference timeout must be 1..300000 ms and token limit must be positive");
        }
        if (model == null || !model.matches("[a-zA-Z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("Configure a valid underlying openai.model for inference routing");
        }
        URI endpoint;
        try { endpoint = URI.create(url); }
        catch (Exception invalid) { throw new IllegalArgumentException("Invalid inference endpoint"); }
        if (endpoint.getHost() == null || endpoint.getUserInfo() != null
                || !Set.of("http", "https").contains(endpoint.getScheme())) {
            throw new IllegalArgumentException("Inference endpoint must be an HTTP(S) URL without user info");
        }
        if (azure && override != null && override.getModel() != null
                && (override.getUrl() == null || override.getUrl().isBlank())) {
            throw new IllegalArgumentException("Azure model overrides require the matching deployment URL");
        }
        String lower = model.toLowerCase(Locale.ROOT);
        boolean reasoning = lower.startsWith("gpt-5") || lower.startsWith("gpt-6") || lower.matches("o[134].*");
        if (effort != null) {
            Set<String> supported = lower.startsWith("gpt-5.6")
                    ? Set.of("none", "low", "medium", "high", "xhigh", "max")
                    : lower.startsWith("gpt-5.2") || lower.startsWith("gpt-5.1")
                    ? Set.of("none", "low", "medium", "high", "xhigh")
                    : lower.startsWith("gpt-6") ? Set.of("low", "medium", "high", "xhigh", "max")
                    : reasoning ? Set.of("low", "medium", "high") : Set.of();
            if (!supported.contains(effort)) throw new IllegalArgumentException("Unsupported reasoning effort for configured model");
        }
        // Omit optional sampling controls on reasoning families; their support varies by model and effort.
        return new Route(model, effort, url, timeout, tokens, !reasoning && !model.equals("azure-deployment"));
    }

    private static String value(String preferred, String fallback) {
        String result = preferred == null ? fallback : preferred;
        return result == null || result.isBlank() ? null : result.trim();
    }
}
