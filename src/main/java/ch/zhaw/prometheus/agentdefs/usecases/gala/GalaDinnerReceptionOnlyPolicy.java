package ch.zhaw.prometheus.agentdefs.usecases.gala;

import ch.zhaw.prometheus.model.Storage;
import jakarta.persistence.Entity;

/** The persisted policy type keeps the variant fixed across reset and reload. */
@Entity
public class GalaDinnerReceptionOnlyPolicy extends GalaDinnerPolicy {
    protected GalaDinnerReceptionOnlyPolicy() {}
    public GalaDinnerReceptionOnlyPolicy(Storage storage) { super(storage); }
    @Override boolean activitiesEnabled() { return false; }
}
