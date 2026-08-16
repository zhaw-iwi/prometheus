START TRANSACTION;

ALTER TABLE participation_registrations
  MODIFY full_name VARCHAR(255) NULL,
  MODIFY slot_preference_key VARCHAR(64) NULL,
  MODIFY slot_preference_label VARCHAR(255) NULL;

SET @participation_survey_column_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'participation_phase_settings'
    AND COLUMN_NAME = 'survey_url'
);
SET @participation_survey_column_sql = IF(
  @participation_survey_column_exists = 0,
  'ALTER TABLE participation_phase_settings ADD COLUMN survey_url VARCHAR(2048) NULL AFTER default_phase',
  'SELECT 1'
);
PREPARE participation_migration_statement FROM @participation_survey_column_sql;
EXECUTE participation_migration_statement;
DEALLOCATE PREPARE participation_migration_statement;

INSERT INTO participation_phase_settings (id, default_phase, survey_url)
VALUES (
  1,
  1,
  'https://www.uzh.ch/zi/cl/surveys/index.php/922424?lang=de-easy'
)
ON DUPLICATE KEY UPDATE
  survey_url = COALESCE(
    NULLIF(participation_phase_settings.survey_url, ''),
    VALUES(survey_url)
  );

ALTER TABLE participation_phase_settings
  MODIFY survey_url VARCHAR(2048) NOT NULL;

SET @participation_assignment_fk_exists = (
  SELECT COUNT(*)
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'participation_assignments'
    AND CONSTRAINT_NAME = 'fk_participation_assignments_registration'
    AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @participation_assignment_fk_sql = IF(
  @participation_assignment_fk_exists > 0,
  'ALTER TABLE participation_assignments DROP FOREIGN KEY fk_participation_assignments_registration',
  'SELECT 1'
);
PREPARE participation_migration_statement FROM @participation_assignment_fk_sql;
EXECUTE participation_migration_statement;
DEALLOCATE PREPARE participation_migration_statement;

ALTER TABLE participation_assignments
  ADD CONSTRAINT fk_participation_assignments_registration
    FOREIGN KEY (registration_id) REFERENCES participation_registrations (id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

SET @participation_state_fk_exists = (
  SELECT COUNT(*)
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'participation_participant_state'
    AND CONSTRAINT_NAME = 'fk_participation_participant_state_registration'
    AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @participation_state_fk_sql = IF(
  @participation_state_fk_exists > 0,
  'ALTER TABLE participation_participant_state DROP FOREIGN KEY fk_participation_participant_state_registration',
  'SELECT 1'
);
PREPARE participation_migration_statement FROM @participation_state_fk_sql;
EXECUTE participation_migration_statement;
DEALLOCATE PREPARE participation_migration_statement;

ALTER TABLE participation_participant_state
  ADD CONSTRAINT fk_participation_participant_state_registration
    FOREIGN KEY (registration_id) REFERENCES participation_registrations (id)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

COMMIT;
