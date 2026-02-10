package ch.zhaw.prometheus.model.snapshot;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.event.EventSelector;

public final class FactExtractors {
    private FactExtractors() {
    }

    public static FactExtractor count(String factKey, EventSelector selector) {
        return events -> Optional.of(Fact.of(factKey, events.selectList(selector).size(), 1.0d, List.of()));
    }

    public static FactExtractor lastContent(String factKey, EventSelector selector) {
        return last(factKey, selector, Event::getPayload);
    }

    public static FactExtractor last(String factKey, EventSelector selector, Function<Event, Object> valueMapper) {
        return events -> {
            Event last = lastEvent(events, selector);
            if (last == null) {
                return Optional.empty();
            }
            Object value = valueMapper == null ? last.getPayload() : valueMapper.apply(last);
            return Optional.of(Fact.of(factKey, value, 1.0d, List.of(provenance(last))));
        };
    }

    public static FactExtractor lastJsonString(String factKey, EventSelector selector, String jsonField) {
        return lastJson(factKey, selector, jsonField, json -> json.get(jsonField).getAsString());
    }

    public static FactExtractor lastJsonDouble(String factKey, EventSelector selector, String jsonField) {
        return lastJson(factKey, selector, jsonField, json -> json.get(jsonField).getAsDouble());
    }

    private static FactExtractor lastJson(String factKey, EventSelector selector, String jsonField,
            Function<JsonObject, Object> valueMapper) {
        return events -> {
            Event last = lastEvent(events, selector);
            if (last == null || last.getPayload() == null || last.getPayload().isBlank()) {
                return Optional.empty();
            }
            JsonObject payload = parseObject(last.getPayload());
            if (payload == null || !payload.has(jsonField) || payload.get(jsonField).isJsonNull()) {
                return Optional.empty();
            }
            try {
                Object value = valueMapper.apply(payload);
                return Optional.of(Fact.of(factKey, value, 1.0d, List.of(provenance(last))));
            } catch (Exception exception) {
                return Optional.empty();
            }
        };
    }

    private static JsonObject parseObject(String payload) {
        try {
            return JsonParser.parseString(payload).getAsJsonObject();
        } catch (Exception exception) {
            return null;
        }
    }

    private static Event lastEvent(EventHistory events, EventSelector selector) {
        Event last = null;
        for (Event current : events.toList()) {
            if (selector.test(current)) {
                last = current;
            }
        }
        return last;
    }

    private static String provenance(Event event) {
        return event.getType() + "|" + event.getActor() + "|" + String.join("/", event.getStatePath());
    }
}
