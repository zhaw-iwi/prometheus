package ch.zhaw.prometheus.model.task;

import java.util.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.EventSelectorSpec;
import jakarta.persistence.Entity;

/** Ordinary transition semantics with task-storage reset tied to the state graph's lifecycle. */
@Entity
public class TaskState extends State {
    protected TaskState() {}
    public TaskState(String name, TaskPolicy policy, boolean starting) {
        super(name, policy, List.of(), starting, false, EventSelectorSpec.any());
    }
    @Override protected void reset(Set<State> visited) {
        if (visited.contains(this)) return;
        super.reset(visited); TaskMemory.clear(((TaskPolicy) ownPolicy()).storage());
    }
}
