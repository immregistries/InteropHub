-- Pending schema/data changes for the next production release.
-- Process, conventions, and how to fold local admin/UI edits (e.g. Topic
-- Board layout changes made in the running app) back into this file before
-- a refresh discards them: see docs/database-release-practice.md.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- New private Topic Space: AIRA Business Operations.
-- Business operations topics (policies, infrastructure, services, internal
-- practices such as AI use and governance) brought to senior leadership for
-- review and direction. Open to all AIRA staff by email domain, same as the
-- AIRA Opportunity Nursery. Has its own stages, paths, and default Topic Board.
-- Every statement is safe to re-run.
INSERT INTO es_topic_space
  (space_code, space_name, description, visibility, auto_member_email_domains,
   display_order, is_active, created_at, updated_at)
VALUES
  ('aira-business-operations', 'AIRA Business Operations',
   'Private Topic Space for AIRA business operations topics (policies, infrastructure, services, and internal practices such as AI use and governance) brought forward for review and direction by senior leadership. Open to all AIRA staff.',
   'PRIVATE', 'immregistries.org', 35, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP())
ON DUPLICATE KEY UPDATE
  space_name = VALUES(space_name),
  description = VALUES(description),
  visibility = VALUES(visibility),
  auto_member_email_domains = VALUES(auto_member_email_domains),
  display_order = VALUES(display_order),
  is_active = VALUES(is_active),
  updated_at = UTC_TIMESTAMP();

INSERT INTO es_topic_stage_definition
  (es_topic_space_id, stage_code, stage_name, stage_description, display_order, is_active)
SELECT s.es_topic_space_id, x.stage_code, x.stage_name, x.stage_description, x.display_order, 1
FROM (
  VALUES
    ROW('DRAFTING', 'Drafting', 'The topic is being written up and is not yet ready for leadership.', 10),
    ROW('READY-FOR-LEADERSHIP-REVIEW', 'Ready for Leadership Review', 'Written up and accompanied by a clear question or decision needed from senior leadership.', 20),
    ROW('UNDER-DISCUSSION', 'Under Discussion', 'Senior leadership is actively reviewing and discussing the topic.', 30),
    ROW('DECIDED-ADOPTED', 'Decided / Adopted', 'Leadership has given direction; the outcome and next steps are recorded.', 40)
) AS x (stage_code, stage_name, stage_description, display_order)
JOIN es_topic_space s ON s.space_code = 'aira-business-operations'
ON DUPLICATE KEY UPDATE
  stage_name = VALUES(stage_name),
  stage_description = VALUES(stage_description),
  display_order = VALUES(display_order),
  is_active = 1;

INSERT INTO es_topic_path_definition
  (es_topic_space_id, path_code, path_name, path_description, display_order, is_active)
SELECT s.es_topic_space_id, x.path_code, x.path_name, x.path_description, x.display_order, 1
FROM (
  VALUES
    ROW('NEEDS-LEADERSHIP-DECISION', 'Needs Leadership Decision', 'The topic is waiting on a decision or direction from senior leadership.', 10),
    ROW('NEEDS-STAFF-INPUT', 'Needs Staff Input', 'More information, review, or input from AIRA staff is needed before it can move forward.', 20),
    ROW('READY-TO-IMPLEMENT', 'Ready to Implement', 'Direction is settled and the work can be carried out.', 30),
    ROW('ON-HOLD', 'On Hold', 'Kept with its history, but not actively advancing until priorities, capacity, or conditions change.', 40)
) AS x (path_code, path_name, path_description, display_order)
JOIN es_topic_space s ON s.space_code = 'aira-business-operations'
ON DUPLICATE KEY UPDATE
  path_name = VALUES(path_name),
  path_description = VALUES(path_description),
  display_order = VALUES(display_order),
  is_active = 1;

-- Default Topic Board, set up like the Nursery's: every stage and path, with
-- the unassigned column and row shown.
INSERT INTO es_topic_board_definition
  (board_code, board_name, board_description, es_topic_space_id, curator_topic_id,
   show_unassigned_stage, show_unassigned_path, is_active, created_at, updated_at)
SELECT 'aira-business-operations', 'AIRA Business Operations', NULL, s.es_topic_space_id, NULL,
       1, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()
FROM es_topic_space s
WHERE s.space_code = 'aira-business-operations'
ON DUPLICATE KEY UPDATE
  board_name = VALUES(board_name),
  es_topic_space_id = VALUES(es_topic_space_id),
  show_unassigned_stage = VALUES(show_unassigned_stage),
  show_unassigned_path = VALUES(show_unassigned_path),
  is_active = VALUES(is_active),
  updated_at = UTC_TIMESTAMP();

INSERT INTO es_topic_board_stage (es_topic_board_definition_id, es_topic_stage_definition_id, display_order)
SELECT b.es_topic_board_definition_id, sd.es_topic_stage_definition_id, sd.display_order
FROM es_topic_board_definition b
JOIN es_topic_stage_definition sd
  ON sd.es_topic_space_id = b.es_topic_space_id AND sd.is_active = 1
WHERE b.board_code = 'aira-business-operations'
ON DUPLICATE KEY UPDATE display_order = VALUES(display_order);

INSERT INTO es_topic_board_path (es_topic_board_definition_id, es_topic_path_definition_id, display_order)
SELECT b.es_topic_board_definition_id, pd.es_topic_path_definition_id, pd.display_order
FROM es_topic_board_definition b
JOIN es_topic_path_definition pd
  ON pd.es_topic_space_id = b.es_topic_space_id AND pd.is_active = 1
WHERE b.board_code = 'aira-business-operations'
ON DUPLICATE KEY UPDATE display_order = VALUES(display_order);

UPDATE es_topic_space s
JOIN es_topic_board_definition b ON b.board_code = 'aira-business-operations'
SET s.primary_es_topic_board_definition_id = b.es_topic_board_definition_id
WHERE s.space_code = 'aira-business-operations';

-- Backfill members, same rule the app applies on sign-in and on an admin
-- save: verified, active, exact domain match, and no existing row.
INSERT INTO es_topic_space_member
  (es_topic_space_id, user_id, role, member_status, member_source, created_at, updated_at)
SELECT s.es_topic_space_id, u.user_id, 'MEMBER', 'ACTIVE', 'AUTO_DOMAIN', NOW(), NOW()
FROM es_topic_space s
JOIN auth_user u
  ON u.status = 'ACTIVE'
 AND u.email_verified = 1
 AND SUBSTRING_INDEX(u.email_normalized, '@', -1) = 'immregistries.org'
WHERE s.space_code = 'aira-business-operations'
  AND NOT EXISTS (
    SELECT 1 FROM es_topic_space_member m
    WHERE m.es_topic_space_id = s.es_topic_space_id AND m.user_id = u.user_id
  );
