package ch.zhaw.statefulconversation.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    public void append(EventHistory source, State state) {
        for (Event current : source.toList()) {
            Event event = new Event(current.getType(), current.getRole(), current.getContent(), state.getName());
            event.setEventHistory(this);
            this.eventList.add(event);
        }
    }

    public void appendEvent(Event event, State state) {
        Event copy = new Event(event.getType(), event.getRole(), event.getContent(), state.getName());
        copy.setEventHistory(this);
        this.eventList.add(copy);
    }

    public void appendAssistantUtterance(String assistantSays, State state) {
        Event event = Event.assistantUtterance(assistantSays, state.getName());
        event.setEventHistory(this);
        this.eventList.add(event);
    }

    public void appendUserUtterance(String userSays, State state) {
        Event event = Event.userUtterance(userSays, state.getName());
        event.setEventHistory(this);
        this.eventList.add(event);
    }

    /*
     * This one is assuming the last event is from the user
     * (used in RemoveLastEventAction)
     */
    public void removeLastUserEvent() {
        if (!"user".equals(this.eventList.getLast().getRole())) {
            throw new RuntimeException("assumption that last event has role == user failed");
        }
        this.eventList.removeLast();
    }

    public String removeLastTwoUtteranceEvents() {
        if (!"assistant".equals(this.eventList.getLast().getRole())) {
            throw new RuntimeException("assumption that last event has role == assistant failed");
        }
        this.eventList.removeLast();

        // the following loop is to accomodate the possibility that the assistant had
        // multiple responses in a row (cf. HTML reponses)
        while ("assistant".equals(this.eventList.getLast().getRole())) {
            this.eventList.removeLast();
        }

        if (!"user".equals(this.eventList.getLast().getRole())) {
            throw new RuntimeException(
                    "assumption that when removing all assistant events only user event remains failed");
        }

        Event lastUserEvent = this.eventList.getLast();
        return lastUserEvent.getContent();
    }

    public boolean isEmpty() {
        return this.eventList.isEmpty();
    }

    public void reset() {
        this.eventList.clear();
    }

    public List<Event> toList() {
        List<Event> result = List.copyOf(this.eventList);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("");
        for (Event current : this.eventList) {
            String label = current.getRole() != null ? current.getRole() : current.getType();
            result.append(label + ": " + current.getContent() + "\n");
        }
        return result.toString();
    }
}
