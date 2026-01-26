package ch.zhaw.prometheus.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

@Entity
public class EventHistory {

    @Id
    @GeneratedValue
    private UUID id;

    public UUID getID() {
        return this.id;
    }

    @OneToMany(mappedBy = "eventHistory", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @OrderBy("createdDate ASC")
    private List<Event> eventList;

    public EventHistory() {
        this.eventList = new ArrayList<Event>();
    }

    public EventHistory select(EventSelector selector) {
        EventHistory selected = new EventHistory();
        for (Event current : this.eventList) {
            if (selector.test(current)) {
                Event copy = new Event(current.getType(), current.getActor(), current.getKind(), current.getContent(),
                        current.getPayload(), current.getStateName());
                copy.setEventHistory(selected);
                selected.eventList.add(copy);
            }
        }
        return selected;
    }

    public void append(EventHistory source, State state) {
        for (Event current : source.toList()) {
            Event event = new Event(current.getType(), current.getActor(), current.getKind(), current.getContent(),
                    current.getPayload(), state.getName());
            event.setEventHistory(this);
            this.eventList.add(event);
        }
    }

    public void appendEvent(Event event, State state) {
        Event copy = new Event(event.getType(), event.getActor(), event.getKind(), event.getContent(),
                event.getPayload(), state.getName());
        copy.setEventHistory(this);
        this.eventList.add(copy);
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

    public boolean isEmpty() {
        return this.eventList.isEmpty();
    }

    public void reset() {
        this.eventList.clear();
    }

    public void clearStateEvents(String stateName) {
        this.eventList.removeIf(event -> stateName.equals(event.getStateName()));
    }

    public List<Event> toList() {
        List<Event> result = List.copyOf(this.eventList);
        return result;
    }

    public Stream<Event> stream() {
        return this.eventList.stream();
    }

    public List<Event> selectList(EventSelector selector) {
        return this.eventList.stream()
                .filter(selector::test)
                .toList();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("");
        for (Event current : this.eventList) {
            String label = current.getActor() != null ? current.getActor() : current.getType();
            result.append(label + ": " + current.getContent() + "\n");
        }
        return result.toString();
    }
}
