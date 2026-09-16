-- Pending schema/data changes for the next production release.
-- Process, conventions, and how to fold local admin/UI edits (e.g. Topic
-- Board layout changes made in the running app) back into this file before
-- a refresh discards them: see docs/database-release-practice.md.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Daily digest scheduler state (single row per digest key). Tracks the last
-- successful run so each run's "since" window picks up where the last left
-- off, with no gaps or overlaps.
CREATE TABLE digest_run_state (
  digest_key     VARCHAR(40) NOT NULL,
  last_run_at    DATETIME NOT NULL,
  last_run_date  DATE NOT NULL,
  PRIMARY KEY (digest_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Single supporting link per meeting agenda item (title + URL). One link only
-- by design, no separate link table.
ALTER TABLE es_meeting_agenda_item
  ADD COLUMN link_url VARCHAR(500) NULL AFTER time_minutes,
  ADD COLUMN link_title VARCHAR(200) NULL AFTER link_url;

-- Meeting-cadence action queue (docs/interophub-meeting-cadence-design.md).
--
-- "Publish notes for review" is a deliberate milestone distinct from the
-- existing 7-day note-editing lock (es_meeting.close_due_at / the automatic
-- meeting-closure job) - it only records that someone reviewed and published
-- the notes; it does not touch es_topic_note.status or close_due_at.
ALTER TABLE es_meeting
  ADD COLUMN notes_published_at DATETIME NULL,
  ADD COLUMN notes_published_by_user_id BIGINT NULL,
  ADD CONSTRAINT fk_es_meeting_notes_published_by FOREIGN KEY (notes_published_by_user_id) REFERENCES auth_user (user_id);

-- New communication type for the notes-published-for-review notice.
ALTER TABLE es_meeting_communication
  MODIFY COLUMN communication_type enum('CALL_FOR_TOPICS','CANCELLED','FINAL_AGENDA','NOTES_AVAILABLE','PROPOSED_AGENDA','REMINDER') NOT NULL;

-- Personal read/snooze state for one user's view of one derived meeting-cadence
-- action (publish proposed agenda / finalize agenda / close meeting / publish
-- notes). The action itself - whether it applies, its due date, and whether
-- it's complete - is always derived fresh from es_meeting/es_meeting_communication;
-- this table exists only to remember what a user has already seen or snoozed.
-- Modeled directly on es_meeting_user_view's shape.
CREATE TABLE es_meeting_action_state (
  es_meeting_action_state_id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  es_meeting_id BIGINT NOT NULL,
  action_type enum('PUBLISH_PROPOSED_AGENDA','FINALIZE_AGENDA','CLOSE_MEETING','PUBLISH_NOTES') NOT NULL,
  read_at DATETIME NULL,
  snoozed_until DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (es_meeting_action_state_id),
  UNIQUE KEY uq_es_meeting_action_state_user_meeting_action (user_id, es_meeting_id, action_type),
  KEY ix_es_meeting_action_state_meeting (es_meeting_id),
  CONSTRAINT fk_es_meeting_action_state_meeting FOREIGN KEY (es_meeting_id) REFERENCES es_meeting (es_meeting_id),
  CONSTRAINT fk_es_meeting_action_state_user FOREIGN KEY (user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Day-one responsibility seed for the tiered staff-digest fallback
-- (MeetingResponsibilityResolver: meeting-specific people, then Topic-Space
-- admins, then site admins only as a last resort). Without at least one
-- specific responsible person per Topic Space and per open meeting, every
-- meeting falls through to "email all site admins" - this seeds Nathan
-- Bunker into both fallback tiers so the rollout doesn't spam the admin
-- roster on day one. Reassign individual meetings/spaces to their real
-- owners afterward; this is a starting default, not a permanent one.

-- Topic-Space admin (second-tier fallback) for all three Topic Spaces.
INSERT INTO es_topic_space_member (es_topic_space_id, user_id, role, created_at, updated_at)
SELECT s.es_topic_space_id, u.user_id, 'ADMIN', NOW(), NOW()
FROM es_topic_space s
JOIN auth_user u ON u.email_normalized = 'nbunker@immregistries.org'
WHERE NOT EXISTS (
  SELECT 1 FROM es_topic_space_member m
  WHERE m.es_topic_space_id = s.es_topic_space_id AND m.user_id = u.user_id
);

-- Designated chair (first-tier fallback) for every meeting that's still open
-- (not already Closed or Cancelled) and doesn't already have one assigned -
-- never overwrites a chair someone has actually been given.
UPDATE es_meeting m
JOIN auth_user u ON u.email_normalized = 'nbunker@immregistries.org'
SET m.designated_chair_user_id = u.user_id
WHERE m.status NOT IN ('CLOSED', 'CANCELLED')
  AND m.designated_chair_user_id IS NULL;

-- Managed topic followers (docs/topic-managed-followers.md). Lets a
-- champion/support/space-admin/app-admin add a follower on someone's behalf
-- and record why. "Last sent" invitation timestamps are deliberately not
-- stored here - they're derived from email_send_log (recipient_email_normalized
-- + email_reason) so invitation history stays centralized.
ALTER TABLE es_subscription
  ADD COLUMN contact_first_name VARCHAR(100) NULL,
  ADD COLUMN contact_last_name VARCHAR(100) NULL,
  ADD COLUMN contact_organization VARCHAR(200) NULL,
  ADD COLUMN managed_added_by_user_id BIGINT NULL,
  ADD COLUMN managed_added_at DATETIME(6) NULL,
  ADD COLUMN managed_add_reason TEXT NULL,
  ADD CONSTRAINT fk_es_subscription_managed_added_by FOREIGN KEY (managed_added_by_user_id) REFERENCES auth_user (user_id);

-- Backfill close_due_at for meetings completed before EsAgendaServlet's
-- direct-to-Completed status transition (PROPOSED -> COMPLETED, skipping
-- FINALIZED) started setting it. close_due_at drives both the 7-day
-- note-editing lock (TopicNoteService) and the publish-notes reminder cutoff
-- (MeetingActionQueueService.derivePublishNotes) - meetings completed through
-- that path never got it set, so both stayed open indefinitely. Safe to
-- re-run: only touches rows the bug actually affected.
UPDATE es_meeting
SET close_due_at = completed_at + INTERVAL 7 DAY
WHERE close_due_at IS NULL
  AND completed_at IS NOT NULL;
