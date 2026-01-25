package ch.zhaw.statefulconversation.model;

import java.util.Set;
import java.util.function.Predicate;

public final class EventFilter {
    private final Predicate<Event> predicate;

    private EventFilter(Predicate<Event> predicate) {
        this.predicate = predicate;
    }

    public static EventFilter of(Predicate<Event> predicate) {
        return new EventFilter(predicate);
    }

    public static EventFilter any() {
        return new EventFilter(event -> true);
    }

    public static EventFilter type(String... types) {
        Set<String> typeSet = Set.of(types);
        return new EventFilter(event -> event != null && typeSet.contains(event.getType()));
    }

    public static EventFilter role(String... roles) {
        Set<String> roleSet = Set.of(roles);
        return new EventFilter(event -> event != null && roleSet.contains(event.getRole()));
    }

    public static EventFilter stateName(String... stateNames) {
        Set<String> stateSet = Set.of(stateNames);
        return new EventFilter(event -> event != null && stateSet.contains(event.getStateName()));
    }

    public EventFilter and(EventFilter other) {
        return new EventFilter(this.predicate.and(other.predicate));
    }

    public EventFilter or(EventFilter other) {
        return new EventFilter(this.predicate.or(other.predicate));
    }

    public boolean test(Event event) {
        return predicate.test(event);
    }
}
