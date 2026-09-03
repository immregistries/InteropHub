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
