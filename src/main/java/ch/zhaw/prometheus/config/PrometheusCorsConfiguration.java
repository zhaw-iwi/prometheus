package ch.zhaw.prometheus.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConfigurationProperties(prefix = "prometheus.cors")
public class PrometheusCorsConfiguration implements WebMvcConfigurer {
    private List<String> allowedOrigins = new ArrayList<>();
    private List<String> allowedOriginPatterns = new ArrayList<>();

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = configured(this.allowedOrigins);
        List<String> originPatterns = configured(this.allowedOriginPatterns);
        if (origins.isEmpty() && originPatterns.isEmpty()) {
            return;
        }

        var registration = registry.addMapping("/**")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders(
                        "Accept",
                        "Content-Type",
                        "Last-Event-ID",
                        "Origin",
                        "X-Prometheus-Access-Code",
                        "X-Prometheus-Admin-Token")
                .exposedHeaders("Location")
                .maxAge(3600);

        if (!origins.isEmpty()) {
            registration.allowedOrigins(origins.toArray(String[]::new));
        }
        if (!originPatterns.isEmpty()) {
            registration.allowedOriginPatterns(originPatterns.toArray(String[]::new));
        }
    }

    public List<String> getAllowedOrigins() {
        return this.allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? new ArrayList<>() : allowedOrigins;
    }

    public List<String> getAllowedOriginPatterns() {
        return this.allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns == null ? new ArrayList<>() : allowedOriginPatterns;
    }

    private static List<String> configured(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .toList();
    }
}
