SELECT default_phase
FROM participation_phase_settings
WHERE id = 1;

SELECT COUNT(*) AS registration_count
FROM participation_registrations;

SELECT COUNT(*) AS assignment_count
FROM participation_assignments;

SELECT r.id AS registration_id, r.email
FROM participation_registrations r
LEFT JOIN participation_assignments a ON a.registration_id = r.id
WHERE a.registration_id IS NULL
ORDER BY r.id;

SELECT registration_id, half_day_slot, time_slot
FROM participation_assignments
WHERE half_day_slot IS NULL OR time_slot IS NULL
ORDER BY registration_id;

SELECT registration_id, access_code, participant_role, team_id, room
FROM participation_assignments
WHERE half_day_slot IS NOT NULL
  AND time_slot IS NOT NULL
  AND (
    access_code IS NULL
    OR participant_role IS NULL
    OR team_id IS NULL
    OR room IS NULL
  )
ORDER BY registration_id;
