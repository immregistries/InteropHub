-- Pending schema/data changes for the next production release.
-- Process, conventions, and how to fold local admin/UI edits (e.g. Topic
-- Board layout changes made in the running app) back into this file before
-- a refresh discards them: see docs/database-release-practice.md.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Remove legacy free-text stage/path columns on es_topic now that topic
-- creation/edit and all consumers exclusively use the
-- es_topic_stage_definition_id / es_topic_path_definition_id FKs into
-- es_topic_stage_definition / es_topic_path_definition. Defensive backfill
-- first, in case any production row's stage/path text drifted from its FK
-- (none do as of the last local snapshot, but this is free insurance).
UPDATE es_topic t
JOIN es_topic_stage_definition d
  ON d.es_topic_space_id = t.es_topic_space_id
  AND LOWER(d.stage_name) = LOWER(t.stage)
SET t.es_topic_stage_definition_id = d.es_topic_stage_definition_id
WHERE t.es_topic_stage_definition_id IS NULL AND t.stage IS NOT NULL;

UPDATE es_topic t
JOIN es_topic_path_definition d
  ON d.es_topic_space_id = t.es_topic_space_id
  AND LOWER(d.path_name) = LOWER(t.path)
SET t.es_topic_path_definition_id = d.es_topic_path_definition_id
WHERE t.es_topic_path_definition_id IS NULL AND t.path IS NOT NULL;

ALTER TABLE es_topic
  DROP COLUMN stage,
  DROP COLUMN path;
