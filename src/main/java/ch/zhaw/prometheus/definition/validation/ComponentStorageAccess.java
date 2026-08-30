package ch.zhaw.prometheus.definition.validation;

public enum ComponentStorageAccess {
    READ,
    WRITE,
    READ_WRITE;

    public boolean reads() {
        return this == READ || this == READ_WRITE;
    }

    public boolean writes() {
        return this == WRITE || this == READ_WRITE;
    }
}
