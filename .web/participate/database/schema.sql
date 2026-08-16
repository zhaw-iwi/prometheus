CREATE TABLE IF NOT EXISTS participation_slots (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  slot_key VARCHAR(64) NOT NULL,
  label VARCHAR(255) NOT NULL,
  starts_at DATETIME NULL,
  ends_at DATETIME NULL,
  capacity INT UNSIGNED NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_participation_slots_key (slot_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS participation_registrations (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  public_token CHAR(64) NOT NULL,
  full_name VARCHAR(255) NULL,
  date_of_birth DATE NOT NULL,
  email VARCHAR(320) NOT NULL,
  email_normalized VARCHAR(320) NOT NULL,
  slot_id INT UNSIGNED NULL,
  slot_preference_key VARCHAR(64) NULL,
  slot_preference_label VARCHAR(255) NULL,
  status ENUM('received', 'cancelled') NOT NULL DEFAULT 'received',
  ip_address VARCHAR(45) NULL,
  user_agent TEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_participation_registrations_public_token (public_token),
  UNIQUE KEY uq_participation_registrations_email_normalized (email_normalized),
  KEY idx_participation_registrations_slot_id (slot_id),
  KEY idx_participation_registrations_created_at (created_at),
  CONSTRAINT fk_participation_registrations_slot
    FOREIGN KEY (slot_id) REFERENCES participation_slots (id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS participation_phase_settings (
  id TINYINT UNSIGNED NOT NULL,
  default_phase TINYINT UNSIGNED NOT NULL DEFAULT 1,
  survey_url VARCHAR(2048) NOT NULL,
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
    ON UPDATE CASCADE
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
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
