package ch.zhaw.prometheus.model.event;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** In-memory prompt/selector utility; persistence is owned by the declarative instance JSON aggregate. */
public final class EventHistory {
    private final List<Event> eventList;

    public EventHistory() {
        this.eventList = new ArrayList<>();
    }

    public EventHistory(List<Event> events) {
        this.eventList = new ArrayList<>(events == null ? List.of() : events);
    }

    public EventHistory select(EventSelector selector) {
        return new EventHistory(this.eventList.stream().filter(selector::test).toList());
    }

    public Event appendEvent(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        this.eventList.add(event);
        return event;
    }

    public Event removeLast(EventSelector selector) {
        for (int i = this.eventList.size() - 1; i >= 0; i--) {
            Event current = this.eventList.get(i);
            if (selector.test(current)) {
                this.eventList.remove(i);
                return current;
            }
        }
        return null;
    }

    public List<Event> removeAll(EventSelector selector) {
        List<Event> removed = new ArrayList<>();
        for (int i = this.eventList.size() - 1; i >= 0; i--) {
            Event current = this.eventList.get(i);
            if (selector.test(current)) {
                removed.add(this.eventList.remove(i));
            }
        }
        return removed;
    }

    public boolean isEmpty() { return this.eventList.isEmpty(); }
    public void reset() { this.eventList.clear(); }
    public void clearStateEvents(String stateName) {
        this.eventList.removeIf(event -> event.getStatePath().contains(stateName));
    }
    public List<Event> toList() { return List.copyOf(this.eventList); }
    public Stream<Event> stream() { return this.eventList.stream(); }
    public List<Event> selectList(EventSelector selector) {
        return this.eventList.stream().filter(selector::test).toList();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (Event current : this.eventList) {
            String label = current.getActor() != null ? current.getActor() : current.getType();
            result.append(label).append(": ").append(current.getPayload()).append('\n');
        }
        return result.toString();
    }
}
