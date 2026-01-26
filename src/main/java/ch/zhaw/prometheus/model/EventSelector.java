package ch.zhaw.prometheus.model;

import java.util.Set;
import java.util.function.Predicate;

public final class EventSelector {
    private final Predicate<Event> predicate;

    private EventSelector(Predicate<Event> predicate) {
        this.predicate = predicate;
    }

    public static EventSelector of(Predicate<Event> predicate) {
        return new EventSelector(predicate);
    }

    public static EventSelector any() {
        return new EventSelector(event -> true);
    }

    public static EventSelector type(String... types) {
        Set<String> typeSet = Set.of(types);
        return new EventSelector(event -> event != null && typeSet.contains(event.getType()));
    }

    public static EventSelector actor(String... actors) {
        Set<String> actorSet = Set.of(actors);
        return new EventSelector(event -> event != null && actorSet.contains(event.getActor()));
    }

    public static EventSelector kind(String... kinds) {
        Set<String> kindSet = Set.of(kinds);
        return new EventSelector(event -> event != null && kindSet.contains(event.getKind()));
    }

    public static EventSelector stateName(String... stateNames) {
        Set<String> stateSet = Set.of(stateNames);
        return new EventSelector(event -> event != null && stateSet.contains(event.getStateName()));
    }

    public EventSelector and(EventSelector other) {
        return new EventSelector(this.predicate.and(other.predicate));
    }

    public EventSelector or(EventSelector other) {
        return new EventSelector(this.predicate.or(other.predicate));
    }

    public boolean test(Event event) {
        return predicate.test(event);
    }
}
