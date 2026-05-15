-- v0.17 — Add online meeting URL and connection details to es_topic_meeting and es_meeting
-- Both columns are nullable; safe to apply to existing data with no backfill required.

ALTER TABLE es_topic_meeting
  ADD COLUMN online_meeting_url     VARCHAR(2048) NULL AFTER disabled_by_user_id,
  ADD COLUMN online_meeting_details TEXT         NULL AFTER online_meeting_url;

ALTER TABLE es_meeting
  ADD COLUMN online_meeting_url     VARCHAR(2048) NULL AFTER cancellation_reason,
  ADD COLUMN online_meeting_details TEXT         NULL AFTER online_meeting_url;
