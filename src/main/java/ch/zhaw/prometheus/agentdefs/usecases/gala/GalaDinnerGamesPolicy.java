package ch.zhaw.prometheus.agentdefs.usecases.gala;

import ch.zhaw.prometheus.model.Storage;
import jakarta.persistence.Entity;

/** Persist the games-only capability across reset and reload. */
@Entity
public class GalaDinnerGamesPolicy extends GalaDinnerPolicy {
    protected GalaDinnerGamesPolicy() {}
    public GalaDinnerGamesPolicy(Storage storage) { super(storage); }
    @Override boolean coCreatedTasksEnabled() { return false; }
}
