-- v0.20: Link es_meeting_attendance rows to a specific es_meeting instance.
-- This allows the attendance servlet to record which calendar meeting the
-- attendee checked in for (as opposed to only the meeting series / topic).
-- The column is nullable so that older rows and rows recorded via the
-- legacy auto-detect path remain valid.

SET NAMES utf8mb4;
SET time_zone = '+00:00';

ALTER TABLE es_meeting_attendance
    ADD COLUMN es_meeting_id BIGINT UNSIGNED NULL AFTER es_topic_meeting_id,
    ADD KEY ix_attendance_es_meeting (es_meeting_id),
    ADD CONSTRAINT fk_attendance_es_meeting FOREIGN KEY (es_meeting_id)
        REFERENCES es_meeting (es_meeting_id);
