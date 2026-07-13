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
  full_name VARCHAR(255) NOT NULL,
  date_of_birth DATE NOT NULL,
  email VARCHAR(320) NOT NULL,
  email_normalized VARCHAR(320) NOT NULL,
  slot_id INT UNSIGNED NULL,
  slot_preference_key VARCHAR(64) NOT NULL,
  slot_preference_label VARCHAR(255) NOT NULL,
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
