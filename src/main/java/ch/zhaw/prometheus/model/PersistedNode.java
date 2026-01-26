package ch.zhaw.prometheus.model;

import java.util.UUID;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class PersistedNode {
    @Id
    @GeneratedValue
    private UUID id;

    public UUID getId() {
        return this.id;
    }
}
