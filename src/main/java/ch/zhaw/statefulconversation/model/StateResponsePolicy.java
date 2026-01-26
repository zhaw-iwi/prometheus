package ch.zhaw.statefulconversation.model;

import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(length = 60)
public abstract class StateResponsePolicy extends PersistedNode {
    public abstract StateResponsePolicy withOuterPolicy(StateResponsePolicy outerPolicy);

    public abstract String onStart(State state);

    public abstract String onRespond(State state);

    public abstract String summarise(State state);

    public abstract String describe();
}
