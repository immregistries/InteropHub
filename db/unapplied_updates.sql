-- Pending schema/data changes for the next production release.
-- Process, conventions, and how to fold local admin/UI edits (e.g. Topic
-- Board layout changes made in the running app) back into this file before
-- a refresh discards them: see docs/database-release-practice.md.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Meeting Attendance Console, Phase 1: observed-attendance foundation
-- (docs/meeting-attendance-console-design.md). Distinguishes what a person
-- self-reported from what meeting staff observed, and allows incomplete
-- identity (e.g. a Zoom display name with no email) to still be recorded.
-- display_name is backfilled below from first_name/last_name before being
-- locked NOT NULL, since every existing row today came from self sign-in.
ALTER TABLE es_meeting_attendance
  ADD COLUMN display_name VARCHAR(150) NULL;

UPDATE es_meeting_attendance
SET display_name = TRIM(CONCAT(first_name, ' ', COALESCE(last_name, '')))
WHERE display_name IS NULL;

ALTER TABLE es_meeting_attendance
  MODIFY COLUMN display_name VARCHAR(150) NOT NULL,
  MODIFY COLUMN first_name VARCHAR(100) NULL,
  MODIFY COLUMN email VARCHAR(254) NULL,
  MODIFY COLUMN email_normalized VARCHAR(254) NULL,
  ADD COLUMN self_signed_at DATETIME NULL,
  ADD COLUMN observed_at DATETIME NULL,
  ADD COLUMN observed_by_user_id BIGINT NULL,
  ADD COLUMN observation_note TEXT NULL,
  ADD COLUMN removed_at DATETIME NULL,
  ADD COLUMN removed_by_user_id BIGINT NULL,
  ADD CONSTRAINT fk_es_meeting_attendance_observed_by FOREIGN KEY (observed_by_user_id) REFERENCES auth_user (user_id),
  ADD CONSTRAINT fk_es_meeting_attendance_removed_by FOREIGN KEY (removed_by_user_id) REFERENCES auth_user (user_id);

-- No observed-attendance entry path exists until Phase 2 (the staff
-- console), so every row that exists today is a self sign-in - backfill
-- self_signed_at from created_at rather than leaving it null.
UPDATE es_meeting_attendance
SET self_signed_at = created_at
WHERE self_signed_at IS NULL;
