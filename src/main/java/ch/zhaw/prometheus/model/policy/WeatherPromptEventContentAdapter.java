package ch.zhaw.prometheus.model.policy;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.zhaw.prometheus.model.event.Event;

public class WeatherPromptEventContentAdapter implements PromptEventContentAdapter {
    @Override
    public boolean supports(Event event) {
        return event != null && (Event.TYPE_WEATHER_CURRENT.equals(event.getType())
                || Event.TYPE_WEATHER_FORECAST.equals(event.getType()));
    }

    @Override
    public String toPromptContent(Event event) {
        if (event == null || event.getPayload() == null || event.getPayload().isBlank()) {
            return "Weather observation received.";
        }
        try {
            JsonObject payload = JsonParser.parseString(event.getPayload()).getAsJsonObject();
            if (Event.TYPE_WEATHER_FORECAST.equals(event.getType())) {
                return forecastContent(payload);
            }
            return currentContent(payload);
        } catch (RuntimeException exception) {
            return "Weather observation received.";
        }
    }

    private static String currentContent(JsonObject payload) {
        String location = locationLabel(payload);
        String condition = string(payload, "condition", "unknown");
        String intensity = string(payload, "intensity", "unknown");
        String wind = string(payload, "wind", "unknown");
        String temperature = nullableNumber(payload, "temperature_c");
        String cloudCover = nullableNumber(payload, "cloud_cover");
        String observedAt = string(payload, "observed_at", "");

        StringBuilder content = new StringBuilder("Current weather");
        if (!location.isBlank()) {
            content.append(" for ").append(location);
        }
        content.append(": ").append(condition);
        if (!"unknown".equals(intensity) && !"none".equals(intensity)) {
            content.append(", ").append(intensity).append(" intensity");
        }
        if (!"unknown".equals(wind)) {
            content.append(", wind is ").append(wind);
        }
        if (!temperature.isBlank()) {
            content.append(", ").append(temperature).append(" C");
        }
        if (!cloudCover.isBlank()) {
            content.append(", cloud cover ").append(cloudCover).append("%");
        }
        if (!observedAt.isBlank()) {
            content.append(". Observed at ").append(observedAt);
        }
        return content.toString();
    }

    private static String forecastContent(JsonObject payload) {
        String location = locationLabel(payload);
        List<String> days = forecastDays(payload);

        StringBuilder content = new StringBuilder("Weather forecast");
        if (!location.isBlank()) {
            content.append(" for ").append(location);
        }
        if (days.isEmpty()) {
            return content.append(" received.").toString();
        }
        content.append(": ").append(String.join("; ", days));
        return content.toString();
    }

    private static List<String> forecastDays(JsonObject payload) {
        if (payload == null || !payload.has("days") || !payload.get("days").isJsonArray()) {
            return List.of();
        }
        JsonArray days = payload.getAsJsonArray("days");
        List<String> values = new ArrayList<>();
        for (JsonElement element : days) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject day = element.getAsJsonObject();
            String date = string(day, "date", "unknown date");
            String condition = string(day, "condition", "unknown");
            String intensity = string(day, "intensity", "none");
            String min = nullableNumber(day, "temperature_min_c");
            String max = nullableNumber(day, "temperature_max_c");
            StringBuilder value = new StringBuilder(date).append(" ").append(condition);
            if (!"none".equals(intensity) && !"unknown".equals(intensity)) {
                value.append(" (").append(intensity).append(")");
            }
            if (!min.isBlank() || !max.isBlank()) {
                value.append(", ").append(min.isBlank() ? "?" : min)
                        .append("-")
                        .append(max.isBlank() ? "?" : max)
                        .append(" C");
            }
            values.add(value.toString());
        }
        return values;
    }

    private static String locationLabel(JsonObject payload) {
        String label = string(payload, "location_label", "");
        if (!label.isBlank()) {
            return label;
        }
        String name = string(payload, "location_name", "");
        String country = string(payload, "country", "");
        if (!name.isBlank() && !country.isBlank()) {
            return name + ", " + country;
        }
        return name;
    }

    private static String string(JsonObject payload, String key, String fallback) {
        if (payload == null || key == null || !payload.has(key) || payload.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return payload.get(key).getAsString();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static String nullableNumber(JsonObject payload, String key) {
        if (payload == null || key == null || !payload.has(key) || payload.get(key).isJsonNull()) {
            return "";
        }
        try {
            return payload.get(key).getAsString();
        } catch (RuntimeException exception) {
            return "";
        }
    }
}
