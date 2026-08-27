START TRANSACTION;

CREATE TABLE IF NOT EXISTS participation_phase_settings (
  id TINYINT UNSIGNED NOT NULL,
  default_phase TINYINT UNSIGNED NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT chk_participation_phase_settings_singleton CHECK (id = 1),
  CONSTRAINT chk_participation_phase_settings_phase CHECK (default_phase BETWEEN 1 AND 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS participation_assignments (
  registration_id BIGINT UNSIGNED NOT NULL,
  access_code VARCHAR(64) NULL,
  participant_role VARCHAR(32) NULL,
  team_id VARCHAR(64) NULL,
  half_day_slot VARCHAR(64) NULL,
  time_slot VARCHAR(64) NULL,
  room VARCHAR(64) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (registration_id),
  UNIQUE KEY uq_participation_assignments_access_code (access_code),
  CONSTRAINT fk_participation_assignments_registration
    FOREIGN KEY (registration_id) REFERENCES participation_registrations (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS participation_participant_state (
  registration_id BIGINT UNSIGNED NOT NULL,
  phase_override TINYINT UNSIGNED NULL,
  results_interest TINYINT(1) NULL,
  results_interest_updated_at DATETIME NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (registration_id),
  CONSTRAINT chk_participation_participant_state_phase
    CHECK (phase_override IS NULL OR phase_override BETWEEN 1 AND 4),
  CONSTRAINT chk_participation_participant_state_interest
    CHECK (results_interest IS NULL OR results_interest IN (0, 1)),
  CONSTRAINT fk_participation_participant_state_registration
    FOREIGN KEY (registration_id) REFERENCES participation_registrations (id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO participation_phase_settings (id, default_phase)
VALUES (1, 1)
ON DUPLICATE KEY UPDATE id = VALUES(id);

COMMIT;
