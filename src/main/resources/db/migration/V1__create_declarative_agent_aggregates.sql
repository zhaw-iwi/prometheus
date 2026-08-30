CREATE TABLE agent_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    definition_key VARCHAR(190) NOT NULL,
    active_revision_id BIGINT NULL,
    optimistic_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_agent_definition_key UNIQUE (definition_key)
);

CREATE TABLE agent_definition_revision (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    definition_id BIGINT NOT NULL,
    revision_number INT NOT NULL,
    schema_version INT NOT NULL,
    lifecycle_status VARCHAR(16) NOT NULL,
    specification_json JSON NOT NULL,
    content_hash CHAR(64) NOT NULL,
    provenance VARCHAR(16) NOT NULL,
    source_detail VARCHAR(512) NULL,
    optimistic_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at DATETIME(6) NULL,
    archived_at DATETIME(6) NULL,
    CONSTRAINT uk_agent_definition_revision UNIQUE (definition_id, revision_number),
    CONSTRAINT fk_agent_definition_revision_identity
        FOREIGN KEY (definition_id) REFERENCES agent_definition (id) ON DELETE RESTRICT,
    CONSTRAINT ck_agent_definition_revision_number CHECK (revision_number > 0),
    CONSTRAINT ck_agent_definition_schema_version CHECK (schema_version > 0),
    CONSTRAINT ck_agent_definition_revision_status
        CHECK (lifecycle_status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_agent_definition_revision_provenance
        CHECK (provenance IN ('BUNDLED', 'DESIGNER', 'IMPORTED'))
);

CREATE INDEX ix_agent_definition_revision_hash
    ON agent_definition_revision (content_hash);

ALTER TABLE agent_definition
    ADD CONSTRAINT fk_agent_definition_active_revision
    FOREIGN KEY (active_revision_id) REFERENCES agent_definition_revision (id) ON DELETE RESTRICT;

CREATE TABLE declarative_agent_instance (
    id BINARY(16) PRIMARY KEY,
    definition_revision_id BIGINT NOT NULL,
    active_leaf_state_id VARCHAR(190) NOT NULL,
    storage_json JSON NOT NULL,
    initial_storage_json JSON NOT NULL,
    history_json JSON NOT NULL,
    started BOOLEAN NOT NULL,
    runtime_status VARCHAR(16) NOT NULL,
    optimistic_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_declarative_agent_instance_revision
        FOREIGN KEY (definition_revision_id) REFERENCES agent_definition_revision (id) ON DELETE RESTRICT,
    CONSTRAINT ck_declarative_agent_instance_status
        CHECK (runtime_status IN ('ACTIVE', 'FINAL'))
);

CREATE INDEX ix_declarative_agent_instance_revision
    ON declarative_agent_instance (definition_revision_id);
