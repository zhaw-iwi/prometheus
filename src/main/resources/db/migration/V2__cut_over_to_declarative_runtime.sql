CREATE TABLE IF NOT EXISTS access_code (
    id BINARY(16) PRIMARY KEY,
    code VARCHAR(5) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    enabled BOOLEAN NOT NULL,
    CONSTRAINT uk_access_code_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS access_code_allowed_agent_type (
    id BINARY(16) PRIMARY KEY,
    access_code_id BINARY(16) NOT NULL,
    agent_type_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    CONSTRAINT uk_access_code_allowed_agent_type UNIQUE (access_code_id, agent_type_key),
    CONSTRAINT fk_access_code_allowed_type_code FOREIGN KEY (access_code_id)
        REFERENCES access_code (id) ON DELETE CASCADE
);

DROP TABLE IF EXISTS access_code_agent;

CREATE TABLE access_code_agent (
    id BINARY(16) PRIMARY KEY,
    access_code_id BINARY(16) NOT NULL,
    agent_id BINARY(16) NOT NULL,
    CONSTRAINT uk_access_code_agent UNIQUE (access_code_id, agent_id),
    CONSTRAINT fk_access_code_agent_code FOREIGN KEY (access_code_id)
        REFERENCES access_code (id) ON DELETE CASCADE,
    CONSTRAINT fk_access_code_agent_instance FOREIGN KEY (agent_id)
        REFERENCES declarative_agent_instance (id) ON DELETE CASCADE
);

DROP TABLE IF EXISTS action_storage_keys_from;
DROP TABLE IF EXISTS prompt_policy_storage_keys_from;
DROP TABLE IF EXISTS transition_actions;
DROP TABLE IF EXISTS transition_decisions;
DROP TABLE IF EXISTS state_transitions;
DROP TABLE IF EXISTS storage_entries;
DROP TABLE IF EXISTS event_state_path;

DROP TABLE IF EXISTS agent;
DROP TABLE IF EXISTS transition;
DROP TABLE IF EXISTS state;
DROP TABLE IF EXISTS action;
DROP TABLE IF EXISTS decision;
DROP TABLE IF EXISTS policy;
DROP TABLE IF EXISTS storage_entry;
DROP TABLE IF EXISTS storage;
DROP TABLE IF EXISTS event;
DROP TABLE IF EXISTS event_history;
