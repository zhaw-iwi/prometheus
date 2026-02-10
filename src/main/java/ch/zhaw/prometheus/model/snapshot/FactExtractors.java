package ch.zhaw.prometheus.model.snapshot;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

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
