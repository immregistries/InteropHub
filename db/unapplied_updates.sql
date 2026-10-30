-- Pending schema/data changes for the next production release.
-- Process, conventions, and how to fold local admin/UI edits (e.g. Topic
-- Board layout changes made in the running app) back into this file before
-- a refresh discards them: see docs/database-release-practice.md.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Remove bot sign-ups from testemailblasttest@gmail.com.
-- On 2026-10-14 17:30:51-54 UTC an automated client submitted the public
-- DD26 campaign form (es_campaign_id 1) many times concurrently, creating 81
-- es_subscription rows (79 TOPIC across ~45 topics, often duplicated, plus 2
-- GENERAL_ES) with no auth_user account, no contact details, and no email
-- ever sent to it. They were showing up as "New Followers" in the daily
-- digest. No other table references this address.
DELETE FROM es_subscription
 WHERE email_normalized = 'testemailblasttest@gmail.com';

-- Initiatives phase 1: shared organizations, initiative roster, and timeline.
-- Status and type columns are varchar codes, not MySQL enums, so the lists
-- can change while the module is still settling. See
-- docs/initiatives/InteropHub_Initiatives_Design.md.

CREATE TABLE organization (
  organization_id bigint NOT NULL AUTO_INCREMENT,
  organization_name varchar(200) NOT NULL,
  organization_type varchar(40) NOT NULL,
  website_url varchar(500) DEFAULT NULL,
  external_system varchar(64) DEFAULT NULL,
  external_id varchar(128) DEFAULT NULL,
  is_active tinyint(1) NOT NULL DEFAULT 1,
  created_by_user_id bigint DEFAULT NULL,
  created_at datetime(6) NOT NULL,
  updated_at datetime(6) NOT NULL,
  PRIMARY KEY (organization_id),
  KEY idx_organization_name (organization_name),
  KEY idx_organization_active_name (is_active, organization_name),
  CONSTRAINT fk_organization_created_by FOREIGN KEY (created_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_initiative (
  es_initiative_id bigint NOT NULL AUTO_INCREMENT,
  initiative_code varchar(80) NOT NULL,
  initiative_name varchar(200) NOT NULL,
  summary text,
  description text,
  primary_es_topic_id bigint NOT NULL,
  status varchar(32) NOT NULL,
  visibility varchar(32) NOT NULL,
  join_policy varchar(32) NOT NULL,
  start_date date DEFAULT NULL,
  end_date date DEFAULT NULL,
  created_by_user_id bigint DEFAULT NULL,
  created_at datetime(6) NOT NULL,
  updated_at datetime(6) NOT NULL,
  PRIMARY KEY (es_initiative_id),
  UNIQUE KEY uq_es_initiative_code (initiative_code),
  KEY idx_es_initiative_topic (primary_es_topic_id),
  CONSTRAINT fk_es_initiative_primary_topic FOREIGN KEY (primary_es_topic_id) REFERENCES es_topic (es_topic_id),
  CONSTRAINT fk_es_initiative_created_by FOREIGN KEY (created_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_initiative_manager (
  es_initiative_manager_id bigint NOT NULL AUTO_INCREMENT,
  es_initiative_id bigint NOT NULL,
  user_id bigint NOT NULL,
  manager_role varchar(32) NOT NULL,
  created_by_user_id bigint DEFAULT NULL,
  created_at datetime(6) NOT NULL,
  PRIMARY KEY (es_initiative_manager_id),
  UNIQUE KEY uq_es_initiative_manager_user (es_initiative_id, user_id),
  KEY idx_es_initiative_manager_user (user_id),
  CONSTRAINT fk_es_initiative_manager_initiative FOREIGN KEY (es_initiative_id) REFERENCES es_initiative (es_initiative_id),
  CONSTRAINT fk_es_initiative_manager_user FOREIGN KEY (user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_initiative_manager_created_by FOREIGN KEY (created_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_initiative_link (
  es_initiative_link_id bigint NOT NULL AUTO_INCREMENT,
  es_initiative_id bigint NOT NULL,
  link_label varchar(200) NOT NULL,
  link_url varchar(1000) NOT NULL,
  display_order int NOT NULL DEFAULT 0,
  created_by_user_id bigint DEFAULT NULL,
  created_at datetime(6) NOT NULL,
  PRIMARY KEY (es_initiative_link_id),
  KEY idx_es_initiative_link_initiative (es_initiative_id, display_order),
  CONSTRAINT fk_es_initiative_link_initiative FOREIGN KEY (es_initiative_id) REFERENCES es_initiative (es_initiative_id),
  CONSTRAINT fk_es_initiative_link_created_by FOREIGN KEY (created_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_initiative_organization (
  es_initiative_organization_id bigint NOT NULL AUTO_INCREMENT,
  es_initiative_id bigint NOT NULL,
  organization_id bigint NOT NULL,
  participation_status varchar(32) NOT NULL,
  status_changed_at datetime(6) DEFAULT NULL,
  status_changed_by_user_id bigint DEFAULT NULL,
  created_by_user_id bigint DEFAULT NULL,
  created_at datetime(6) NOT NULL,
  updated_at datetime(6) NOT NULL,
  PRIMARY KEY (es_initiative_organization_id),
  UNIQUE KEY uq_es_initiative_organization (es_initiative_id, organization_id),
  KEY idx_es_initiative_organization_org (organization_id),
  CONSTRAINT fk_es_initiative_org_initiative FOREIGN KEY (es_initiative_id) REFERENCES es_initiative (es_initiative_id),
  CONSTRAINT fk_es_initiative_org_organization FOREIGN KEY (organization_id) REFERENCES organization (organization_id),
  CONSTRAINT fk_es_initiative_org_status_user FOREIGN KEY (status_changed_by_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_initiative_org_created_by FOREIGN KEY (created_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_initiative_member (
  es_initiative_member_id bigint NOT NULL AUTO_INCREMENT,
  es_initiative_id bigint NOT NULL,
  user_id bigint DEFAULT NULL,
  email varchar(254) DEFAULT NULL,
  email_normalized varchar(254) DEFAULT NULL,
  contact_first_name varchar(100) DEFAULT NULL,
  contact_last_name varchar(100) DEFAULT NULL,
  es_initiative_organization_id bigint DEFAULT NULL,
  initiative_role varchar(200) DEFAULT NULL,
  member_status varchar(32) NOT NULL,
  member_source varchar(32) NOT NULL,
  status_changed_at datetime(6) DEFAULT NULL,
  status_changed_by_user_id bigint DEFAULT NULL,
  joined_at datetime(6) DEFAULT NULL,
  created_by_user_id bigint DEFAULT NULL,
  created_at datetime(6) NOT NULL,
  updated_at datetime(6) NOT NULL,
  PRIMARY KEY (es_initiative_member_id),
  UNIQUE KEY uq_es_initiative_member_email (es_initiative_id, email_normalized),
  KEY idx_es_initiative_member_email (email_normalized),
  KEY idx_es_initiative_member_user (user_id),
  KEY idx_es_initiative_member_org (es_initiative_organization_id),
  CONSTRAINT fk_es_initiative_member_initiative FOREIGN KEY (es_initiative_id) REFERENCES es_initiative (es_initiative_id),
  CONSTRAINT fk_es_initiative_member_user FOREIGN KEY (user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_initiative_member_org FOREIGN KEY (es_initiative_organization_id) REFERENCES es_initiative_organization (es_initiative_organization_id),
  CONSTRAINT fk_es_initiative_member_status_user FOREIGN KEY (status_changed_by_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_initiative_member_created_by FOREIGN KEY (created_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_initiative_timeline_entry (
  es_initiative_timeline_entry_id bigint NOT NULL AUTO_INCREMENT,
  es_initiative_id bigint NOT NULL,
  subject_type varchar(32) NOT NULL,
  subject_id bigint NOT NULL,
  entry_type varchar(32) NOT NULL,
  from_value text,
  to_value text,
  note_text text,
  created_by_user_id bigint DEFAULT NULL,
  created_at datetime(6) NOT NULL,
  edited_at datetime(6) DEFAULT NULL,
  PRIMARY KEY (es_initiative_timeline_entry_id),
  KEY idx_es_initiative_timeline_subject (subject_type, subject_id, created_at),
  KEY idx_es_initiative_timeline_initiative (es_initiative_id, created_at),
  CONSTRAINT fk_es_initiative_timeline_initiative FOREIGN KEY (es_initiative_id) REFERENCES es_initiative (es_initiative_id),
  CONSTRAINT fk_es_initiative_timeline_user FOREIGN KEY (created_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Initiatives phase 2: checklist templates and checklist copies.
-- Initiatives phase 3: a Small Group has its own participant list. The meeting
-- series is compared with that list. Status columns stay varchar codes. See
-- docs/initiatives/InteropHub_Initiatives_Design.md.

CREATE TABLE es_small_group (
  es_small_group_id bigint NOT NULL AUTO_INCREMENT,
  es_initiative_id bigint NOT NULL,
  es_topic_id bigint NOT NULL,
  es_topic_meeting_id bigint DEFAULT NULL,
  small_group_name varchar(200) NOT NULL,
  description text,
  status varchar(32) NOT NULL,
  cycle_start_date date DEFAULT NULL,
  working_day_date date DEFAULT NULL,
  closeout_date date DEFAULT NULL,
  break_note text,
  primary_product_areas varchar(80) DEFAULT NULL,
  charter_url varchar(2048) DEFAULT NULL,
  created_by_user_id bigint DEFAULT NULL,
  created_at datetime(6) NOT NULL,
  updated_at datetime(6) NOT NULL,
  PRIMARY KEY (es_small_group_id),
  KEY idx_es_small_group_initiative (es_initiative_id, cycle_start_date),
  KEY idx_es_small_group_topic (es_topic_id),
  KEY idx_es_small_group_series (es_topic_meeting_id),
  CONSTRAINT fk_es_small_group_initiative FOREIGN KEY (es_initiative_id) REFERENCES es_initiative (es_initiative_id),
  CONSTRAINT fk_es_small_group_topic FOREIGN KEY (es_topic_id) REFERENCES es_topic (es_topic_id),
  CONSTRAINT fk_es_small_group_series FOREIGN KEY (es_topic_meeting_id) REFERENCES es_topic_meeting (es_topic_meeting_id),
  CONSTRAINT fk_es_small_group_created_by FOREIGN KEY (created_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_small_group_participant (
  es_small_group_participant_id bigint NOT NULL AUTO_INCREMENT,
  es_small_group_id bigint NOT NULL,
  es_initiative_member_id bigint NOT NULL,
  participant_role varchar(200) DEFAULT NULL,
  participant_status varchar(32) NOT NULL,
  created_by_user_id bigint DEFAULT NULL,
  created_at datetime(6) NOT NULL,
  updated_at datetime(6) NOT NULL,
  PRIMARY KEY (es_small_group_participant_id),
  UNIQUE KEY uq_es_small_group_participant (es_small_group_id, es_initiative_member_id),
  KEY idx_es_small_group_participant_member (es_initiative_member_id),
  CONSTRAINT fk_es_small_group_participant_group FOREIGN KEY (es_small_group_id) REFERENCES es_small_group (es_small_group_id),
  CONSTRAINT fk_es_small_group_participant_member FOREIGN KEY (es_initiative_member_id) REFERENCES es_initiative_member (es_initiative_member_id),
  CONSTRAINT fk_es_small_group_participant_created_by FOREIGN KEY (created_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_checklist_template (
  es_checklist_template_id bigint NOT NULL AUTO_INCREMENT,
  template_kind varchar(32) NOT NULL,
  version_no int NOT NULL,
  title varchar(200) NOT NULL,
  source_markdown mediumtext NOT NULL,
  is_current tinyint(1) NOT NULL DEFAULT 0,
  uploaded_by_user_id bigint DEFAULT NULL,
  uploaded_at datetime(6) NOT NULL,
  PRIMARY KEY (es_checklist_template_id),
  UNIQUE KEY uq_es_checklist_template_version (template_kind, version_no),
  KEY idx_es_checklist_template_current (template_kind, is_current),
  CONSTRAINT fk_es_checklist_template_user FOREIGN KEY (uploaded_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_checklist_template_item (
  es_checklist_template_item_id bigint NOT NULL AUTO_INCREMENT,
  es_checklist_template_id bigint NOT NULL,
  item_key varchar(80) NOT NULL,
  phase_label varchar(200) NOT NULL,
  title varchar(500) NOT NULL,
  guidance text,
  due_anchor varchar(40) DEFAULT NULL,
  due_offset_days int DEFAULT NULL,
  check_code varchar(80) DEFAULT NULL,
  display_order int NOT NULL,
  PRIMARY KEY (es_checklist_template_item_id),
  UNIQUE KEY uq_es_checklist_template_item_key (es_checklist_template_id, item_key),
  KEY idx_es_checklist_template_item_order (es_checklist_template_id, display_order),
  CONSTRAINT fk_es_checklist_template_item_template FOREIGN KEY (es_checklist_template_id) REFERENCES es_checklist_template (es_checklist_template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_checklist (
  es_checklist_id bigint NOT NULL AUTO_INCREMENT,
  es_initiative_id bigint NOT NULL,
  es_small_group_id bigint DEFAULT NULL,
  template_kind varchar(32) NOT NULL,
  applied_es_checklist_template_id bigint NOT NULL,
  created_at datetime(6) NOT NULL,
  updated_at datetime(6) NOT NULL,
  initiative_only_id bigint GENERATED ALWAYS AS (
    CASE WHEN es_small_group_id IS NULL THEN es_initiative_id ELSE NULL END
  ) STORED,
  PRIMARY KEY (es_checklist_id),
  UNIQUE KEY uq_es_checklist_initiative (initiative_only_id),
  UNIQUE KEY uq_es_checklist_small_group (es_small_group_id),
  KEY idx_es_checklist_template (applied_es_checklist_template_id),
  CONSTRAINT fk_es_checklist_initiative FOREIGN KEY (es_initiative_id) REFERENCES es_initiative (es_initiative_id),
  CONSTRAINT fk_es_checklist_small_group FOREIGN KEY (es_small_group_id) REFERENCES es_small_group (es_small_group_id),
  CONSTRAINT fk_es_checklist_template FOREIGN KEY (applied_es_checklist_template_id) REFERENCES es_checklist_template (es_checklist_template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_checklist_item (
  es_checklist_item_id bigint NOT NULL AUTO_INCREMENT,
  es_checklist_id bigint NOT NULL,
  item_key varchar(80) NOT NULL,
  item_origin varchar(32) NOT NULL,
  in_current_template tinyint(1) NOT NULL DEFAULT 1,
  is_removed tinyint(1) NOT NULL DEFAULT 0,
  is_locally_edited tinyint(1) NOT NULL DEFAULT 0,
  phase_label varchar(200) NOT NULL,
  title varchar(500) NOT NULL,
  guidance text,
  due_anchor varchar(40) DEFAULT NULL,
  due_offset_days int DEFAULT NULL,
  due_date_override date DEFAULT NULL,
  check_code varchar(80) DEFAULT NULL,
  display_order int NOT NULL,
  item_status varchar(32) NOT NULL,
  status_changed_at datetime(6) DEFAULT NULL,
  status_changed_by_user_id bigint DEFAULT NULL,
  assigned_user_id bigint DEFAULT NULL,
  created_at datetime(6) NOT NULL,
  updated_at datetime(6) NOT NULL,
  PRIMARY KEY (es_checklist_item_id),
  UNIQUE KEY uq_es_checklist_item_key (es_checklist_id, item_key),
  KEY idx_es_checklist_item_order (es_checklist_id, display_order),
  CONSTRAINT fk_es_checklist_item_checklist FOREIGN KEY (es_checklist_id) REFERENCES es_checklist (es_checklist_id),
  CONSTRAINT fk_es_checklist_item_status_user FOREIGN KEY (status_changed_by_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_checklist_item_assignee FOREIGN KEY (assigned_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- BEGIN generated checklist templates
-- Generated by org.airahub.interophub.service.ChecklistTemplateSql
-- from docs/initiatives/templates/initiative-checklist-v1.md and
-- small-group-checklist-v1.md via ChecklistTemplateParser.
-- Regenerate this block from those files instead of editing the item rows.
INSERT INTO es_checklist_template
  (template_kind, version_no, title, source_markdown, is_current, uploaded_by_user_id, uploaded_at)
VALUES ('INITIATIVE', 1, 'Initiative Checklist', '# Initiative Checklist
kind: INITIATIVE
version: 1

<!--
DRAFT. Generic steps drawn from the Indiana School Immunization Interoperability Project plan
and the Initiatives design. Project-specific work (contracts, travel, technical tasks) stays in
the project''s own plan and can be added to an individual checklist as custom items.
See docs/initiatives/InteropHub_Initiatives_Design.md, section "Templates", for the format.
Keep item keys stable when rewording; give an item a new key only if its meaning changes.
-->

## Set up
- [initiative-purpose] Write the initiative''s purpose, scope and expected duration in its description. (due: start)
- [sponsor-context] Record the sponsor or funding context and any reporting expectations in a note. Keep financial details out of InteropHub.
- [primary-topic] Choose the primary topic and confirm it has a Champion. (check: topic-champion)
- [managers] Add the initiative''s managers and set their roles.
- [join-policy] Set the join policy (invite only, apply, or open).
- [project-links] Add links to the project plan and working tools (Smartsheet, Slack, document share, GitHub).
- [slack-channel] Create the project''s Slack channel. (due: start+7d)
- [document-sharing] Set up document sharing and decide internal and external access. (due: start+14d)
- [starter-packet] Prepare a Starter Packet that collects existing resources, prior work and relevant standards for the project team. (due: start+21d)

## Build the roster
- [identify-organizations] List the organizations the initiative needs, before knowing who the contacts are. (due: start+7d)
- [find-contacts] Find a contact at each organization. (check: orgs-need-contact)
- [import-prospects] Add other prospects (from spreadsheets, topic followers and direct outreach).
- [outreach] Contact organizations and people, and record each response as a status change with a note.
- [representation] Review the mix of committed organizations and confirm an IIS/jurisdiction perspective. (check: org-representation)
- [members-follow] Confirm joined members follow the primary topic. (check: members-follow-primary-topic)

## Get started
- [meeting-cadence] Agree on a meeting schedule with the project team and partners.
- [internal-kickoff] Hold the internal project kickoff. (due: start+21d)
- [partner-kickoff] Hold the kickoff with the project''s partners. (due: start+30d)
- [visibility] Decide when the initiative should be listed publicly, and list it when ready.
- [announce] Once listed, confirm the primary topic advertises the initiative.

## Plan Small Groups
- [small-group-plan] Decide how many Small Group cycles the initiative needs and their approximate timing.
- [create-small-groups] Create each Small Group with its topic, and start its checklist.

## Ongoing
- [roster-review] Review the roster periodically: follow up on outstanding outreach, and replace contacts who change.
- [progress-notes] Record milestones and progress for the sponsor as initiative notes.

## Close
- [deliverable-approval] Get the sponsor or partner''s approval of the project deliverables. (due: end-14d)
- [lookback] Write an internal project lookback. (due: end)
- [final-report] Prepare the initiative''s final summary or report. (due: end)
- [roster-disposition] Decide what happens to the roster: carry it into a follow-on initiative, or close it. (due: end)
- [close-initiative] Move the initiative to Closed, with a summary note. (due: end+30d)
', 1, NULL, UTC_TIMESTAMP(6));
SET @template_id = LAST_INSERT_ID();
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'initiative-purpose', 'Set up', 'Write the initiative''s purpose, scope and expected duration in its description.', NULL, 'start', 0, NULL, 1);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'sponsor-context', 'Set up', 'Record the sponsor or funding context and any reporting expectations in a note. Keep financial details out of InteropHub.', NULL, NULL, NULL, NULL, 2);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'primary-topic', 'Set up', 'Choose the primary topic and confirm it has a Champion.', NULL, NULL, NULL, 'topic-champion', 3);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'managers', 'Set up', 'Add the initiative''s managers and set their roles.', NULL, NULL, NULL, NULL, 4);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'join-policy', 'Set up', 'Set the join policy (invite only, apply, or open).', NULL, NULL, NULL, NULL, 5);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'project-links', 'Set up', 'Add links to the project plan and working tools (Smartsheet, Slack, document share, GitHub).', NULL, NULL, NULL, NULL, 6);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'slack-channel', 'Set up', 'Create the project''s Slack channel.', NULL, 'start', 7, NULL, 7);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'document-sharing', 'Set up', 'Set up document sharing and decide internal and external access.', NULL, 'start', 14, NULL, 8);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'starter-packet', 'Set up', 'Prepare a Starter Packet that collects existing resources, prior work and relevant standards for the project team.', NULL, 'start', 21, NULL, 9);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'identify-organizations', 'Build the roster', 'List the organizations the initiative needs, before knowing who the contacts are.', NULL, 'start', 7, NULL, 10);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'find-contacts', 'Build the roster', 'Find a contact at each organization.', NULL, NULL, NULL, 'orgs-need-contact', 11);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'import-prospects', 'Build the roster', 'Add other prospects (from spreadsheets, topic followers and direct outreach).', NULL, NULL, NULL, NULL, 12);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'outreach', 'Build the roster', 'Contact organizations and people, and record each response as a status change with a note.', NULL, NULL, NULL, NULL, 13);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'representation', 'Build the roster', 'Review the mix of committed organizations and confirm an IIS/jurisdiction perspective.', NULL, NULL, NULL, 'org-representation', 14);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'members-follow', 'Build the roster', 'Confirm joined members follow the primary topic.', NULL, NULL, NULL, 'members-follow-primary-topic', 15);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'meeting-cadence', 'Get started', 'Agree on a meeting schedule with the project team and partners.', NULL, NULL, NULL, NULL, 16);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'internal-kickoff', 'Get started', 'Hold the internal project kickoff.', NULL, 'start', 21, NULL, 17);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'partner-kickoff', 'Get started', 'Hold the kickoff with the project''s partners.', NULL, 'start', 30, NULL, 18);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'visibility', 'Get started', 'Decide when the initiative should be listed publicly, and list it when ready.', NULL, NULL, NULL, NULL, 19);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'announce', 'Get started', 'Once listed, confirm the primary topic advertises the initiative.', NULL, NULL, NULL, NULL, 20);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'small-group-plan', 'Plan Small Groups', 'Decide how many Small Group cycles the initiative needs and their approximate timing.', NULL, NULL, NULL, NULL, 21);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'create-small-groups', 'Plan Small Groups', 'Create each Small Group with its topic, and start its checklist.', NULL, NULL, NULL, NULL, 22);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'roster-review', 'Ongoing', 'Review the roster periodically: follow up on outstanding outreach, and replace contacts who change.', NULL, NULL, NULL, NULL, 23);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'progress-notes', 'Ongoing', 'Record milestones and progress for the sponsor as initiative notes.', NULL, NULL, NULL, NULL, 24);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'deliverable-approval', 'Close', 'Get the sponsor or partner''s approval of the project deliverables.', NULL, 'end', -14, NULL, 25);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'lookback', 'Close', 'Write an internal project lookback.', NULL, 'end', 0, NULL, 26);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'final-report', 'Close', 'Prepare the initiative''s final summary or report.', NULL, 'end', 0, NULL, 27);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'roster-disposition', 'Close', 'Decide what happens to the roster: carry it into a follow-on initiative, or close it.', NULL, 'end', 0, NULL, 28);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'close-initiative', 'Close', 'Move the initiative to Closed, with a summary note.', NULL, 'end', 30, NULL, 29);
INSERT INTO es_checklist_template
  (template_kind, version_no, title, source_markdown, is_current, uploaded_by_user_id, uploaded_at)
VALUES ('SMALL_GROUP', 1, 'Small Group Checklist', '# Small Group Checklist
kind: SMALL_GROUP
version: 1

<!--
DRAFT. Based on the Small Group Roadmap''s Operating Checklist (06-operating-checklist.md),
plus the setup and teardown steps that come from InteropHub''s Initiative design.
See docs/initiatives/InteropHub_Initiatives_Design.md, section "Templates", for the format.
Keep item keys stable when rewording; give an item a new key only if its meaning changes.
-->

## Qualify and charter
- [technical-question] State the specific technical problem and why a Small Group is appropriate. (due: cycle-start-42d)
- [community-need] Confirm the work reflects a community-driven need and includes meaningful participation beyond AIRA.
- [org-representation] Confirm participating organizations: normally at least three for unfunded work; for funded work, record how the funder, AIRA, IIS, vendors and partners provide enough representation. (check: org-representation)
- [iis-champion-sisc] Confirm an IIS perspective, a credible Champion, and a clear SISC decision path. (check: topic-champion)
- [primary-deliverables] Select no more than one or two primary deliverables and state the maturity expected of each.
- [product-areas] Select the areas of focus from guidance, tests and software (normally two) and explain how they complement each other.
  If only one area is primary, explain how another perspective will check the work.
- [third-area-support] Identify supporting work that may happen in the third area without becoming a primary commitment.
- [charter] Define completion, exclusions and future work in the Small Group charter, and add the charter link to this Small Group. (due: cycle-start-21d)

## Roles and recruitment
- [sisc-sponsor] Confirm the SISC sponsor and AIRA project support. (due: cycle-start-28d)
- [leadership] Select the Champion, facilitator and, where possible, a co-facilitator. (due: cycle-start-21d)
- [responsibilities] Assign administrative, documentation, review, publication and communication responsibilities.
- [recruit-perspectives] Recruit the perspectives needed for the question and both areas of focus. Start from the initiative roster and add prospects as needed.
- [confirm-commitment] Confirm working members can take part actively for four working weeks and reserve most of their normal working hours within the working-day window.
- [reviewers] Ask people with major scheduling conflicts, or who can''t make the commitment, to take part as reviewers instead.

## Calendar and workspace
- [small-group-topic] Choose the Small Group''s topic and confirm it has a meeting series. (check: series-exists)
- [cycle-dates] Choose exactly four working weeks, the working day, and any holiday or event break, and record the dates on this Small Group. (due: cycle-start-28d)
  The working day is a 25-hour window (1:00 p.m. Eastern to 1:00 p.m. Eastern the next day) containing about 6–8 hours of group work.
- [poll-schedule] Poll participants to set the kickoff and working-day times; these two matter most. (due: cycle-start-21d)
- [invite-to-series] Invite confirmed participants to register for the topic''s meeting series. (check: members-registered-for-series)
- [participants-follow] Confirm participants follow the Small Group topic. (check: members-follow-topic)
- [orientation-bundle] Review the topic''s Orientation Bundle so newcomers can get up to speed. (check: orientation-bundle)
- [technical-bundle] Prepare a dated Technical Bundle (Starter Packet) for this cycle. (due: cycle-start-7d) (check: technical-bundle)
- [schedule-meetings] Schedule kickoff, preparation, the working day, completion and the intended SISC review in InteropHub. (due: cycle-start-14d) (check: meetings-scheduled)
- [agendas] Create each meeting agenda and connect its items to topics. (check: agenda-topics-linked)
- [team-checkpoints] Reserve team sessions and checkpoints where the two teams reconcile assumptions and products.
- [tools] Set up Slack or similar, Zoom, shared drafting, project tracking and technical workspaces.
- [access-test] Test participant access to InteropHub and linked resources before kickoff. (due: cycle-start-3d)

## Run the cycle
- [send-charter] Send the charter and source material before kickoff. (due: cycle-start-7d)
- [kickoff-expectations] At kickoff, explain member and reviewer expectations, the six collaboration principles, and the group''s authority. (due: cycle-start)
- [capture-plan] Show participants how contributions and concerns will be captured. (due: cycle-start)
- [distribute-agendas] Use InteropHub communications to send agenda and topic information to followers and participants.
- [products-visible] Keep the promised products visible and editable to participants.
- [reconcile-teams] Reconcile the teams'' work into one coherent technical approach. (due: working-day+1d)
- [concerns-disposition] Record decisions, constraints, risks, and the outcome of every material concern.
- [notes-finalized] Review and finalize each meeting''s notes within seven days. (check: notes-finalized)
- [protect-scope] Protect scope: move additional ideas to future work instead of extending the cycle.

## Complete and hand off
- [product-maturity] Confirm each product meets the maturity promised in the charter. (due: closeout)
- [final-recommendation] Complete the Final Recommendation, including limitations and unresolved concerns. (due: closeout)
- [future-work] Separate the current recommendation from possible future work or another cycle.
- [sisc-decisions] State the decisions requested from SISC.
- [publication-checks] Complete technical, leadership, editorial, accessibility and publication checks.
- [sisc-review] Submit the material for SISC review and record how feedback was reconciled.
- [preserve-record] Preserve the final products and supporting record in the designated repository and in InteropHub.
- [topic-history] Add products, outcomes, rationale, unresolved concerns and proposed future work to the topic history. (due: closeout+14d)
- [review-series-registrants] Review the meeting series registrants and remove people who aren''t continuing into another cycle. (due: closeout+14d)
- [communicate-result] Communicate the result through current community channels.
- [close-small-group] Move this Small Group to Closed, with a note summarizing the outcome. (due: closeout+30d)
', 1, NULL, UTC_TIMESTAMP(6));
SET @template_id = LAST_INSERT_ID();
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'technical-question', 'Qualify and charter', 'State the specific technical problem and why a Small Group is appropriate.', NULL, 'cycle-start', -42, NULL, 1);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'community-need', 'Qualify and charter', 'Confirm the work reflects a community-driven need and includes meaningful participation beyond AIRA.', NULL, NULL, NULL, NULL, 2);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'org-representation', 'Qualify and charter', 'Confirm participating organizations: normally at least three for unfunded work; for funded work, record how the funder, AIRA, IIS, vendors and partners provide enough representation.', NULL, NULL, NULL, 'org-representation', 3);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'iis-champion-sisc', 'Qualify and charter', 'Confirm an IIS perspective, a credible Champion, and a clear SISC decision path.', NULL, NULL, NULL, 'topic-champion', 4);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'primary-deliverables', 'Qualify and charter', 'Select no more than one or two primary deliverables and state the maturity expected of each.', NULL, NULL, NULL, NULL, 5);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'product-areas', 'Qualify and charter', 'Select the areas of focus from guidance, tests and software (normally two) and explain how they complement each other.', 'If only one area is primary, explain how another perspective will check the work.', NULL, NULL, NULL, 6);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'third-area-support', 'Qualify and charter', 'Identify supporting work that may happen in the third area without becoming a primary commitment.', NULL, NULL, NULL, NULL, 7);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'charter', 'Qualify and charter', 'Define completion, exclusions and future work in the Small Group charter, and add the charter link to this Small Group.', NULL, 'cycle-start', -21, NULL, 8);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'sisc-sponsor', 'Roles and recruitment', 'Confirm the SISC sponsor and AIRA project support.', NULL, 'cycle-start', -28, NULL, 9);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'leadership', 'Roles and recruitment', 'Select the Champion, facilitator and, where possible, a co-facilitator.', NULL, 'cycle-start', -21, NULL, 10);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'responsibilities', 'Roles and recruitment', 'Assign administrative, documentation, review, publication and communication responsibilities.', NULL, NULL, NULL, NULL, 11);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'recruit-perspectives', 'Roles and recruitment', 'Recruit the perspectives needed for the question and both areas of focus. Start from the initiative roster and add prospects as needed.', NULL, NULL, NULL, NULL, 12);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'confirm-commitment', 'Roles and recruitment', 'Confirm working members can take part actively for four working weeks and reserve most of their normal working hours within the working-day window.', NULL, NULL, NULL, NULL, 13);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'reviewers', 'Roles and recruitment', 'Ask people with major scheduling conflicts, or who can''t make the commitment, to take part as reviewers instead.', NULL, NULL, NULL, NULL, 14);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'small-group-topic', 'Calendar and workspace', 'Choose the Small Group''s topic and confirm it has a meeting series.', NULL, NULL, NULL, 'series-exists', 15);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'cycle-dates', 'Calendar and workspace', 'Choose exactly four working weeks, the working day, and any holiday or event break, and record the dates on this Small Group.', 'The working day is a 25-hour window (1:00 p.m. Eastern to 1:00 p.m. Eastern the next day) containing about 6–8 hours of group work.', 'cycle-start', -28, NULL, 16);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'poll-schedule', 'Calendar and workspace', 'Poll participants to set the kickoff and working-day times; these two matter most.', NULL, 'cycle-start', -21, NULL, 17);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'invite-to-series', 'Calendar and workspace', 'Invite confirmed participants to register for the topic''s meeting series.', NULL, NULL, NULL, 'members-registered-for-series', 18);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'participants-follow', 'Calendar and workspace', 'Confirm participants follow the Small Group topic.', NULL, NULL, NULL, 'members-follow-topic', 19);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'orientation-bundle', 'Calendar and workspace', 'Review the topic''s Orientation Bundle so newcomers can get up to speed.', NULL, NULL, NULL, 'orientation-bundle', 20);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'technical-bundle', 'Calendar and workspace', 'Prepare a dated Technical Bundle (Starter Packet) for this cycle.', NULL, 'cycle-start', -7, 'technical-bundle', 21);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'schedule-meetings', 'Calendar and workspace', 'Schedule kickoff, preparation, the working day, completion and the intended SISC review in InteropHub.', NULL, 'cycle-start', -14, 'meetings-scheduled', 22);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'agendas', 'Calendar and workspace', 'Create each meeting agenda and connect its items to topics.', NULL, NULL, NULL, 'agenda-topics-linked', 23);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'team-checkpoints', 'Calendar and workspace', 'Reserve team sessions and checkpoints where the two teams reconcile assumptions and products.', NULL, NULL, NULL, NULL, 24);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'tools', 'Calendar and workspace', 'Set up Slack or similar, Zoom, shared drafting, project tracking and technical workspaces.', NULL, NULL, NULL, NULL, 25);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'access-test', 'Calendar and workspace', 'Test participant access to InteropHub and linked resources before kickoff.', NULL, 'cycle-start', -3, NULL, 26);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'send-charter', 'Run the cycle', 'Send the charter and source material before kickoff.', NULL, 'cycle-start', -7, NULL, 27);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'kickoff-expectations', 'Run the cycle', 'At kickoff, explain member and reviewer expectations, the six collaboration principles, and the group''s authority.', NULL, 'cycle-start', 0, NULL, 28);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'capture-plan', 'Run the cycle', 'Show participants how contributions and concerns will be captured.', NULL, 'cycle-start', 0, NULL, 29);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'distribute-agendas', 'Run the cycle', 'Use InteropHub communications to send agenda and topic information to followers and participants.', NULL, NULL, NULL, NULL, 30);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'products-visible', 'Run the cycle', 'Keep the promised products visible and editable to participants.', NULL, NULL, NULL, NULL, 31);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'reconcile-teams', 'Run the cycle', 'Reconcile the teams'' work into one coherent technical approach.', NULL, 'working-day', 1, NULL, 32);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'concerns-disposition', 'Run the cycle', 'Record decisions, constraints, risks, and the outcome of every material concern.', NULL, NULL, NULL, NULL, 33);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'notes-finalized', 'Run the cycle', 'Review and finalize each meeting''s notes within seven days.', NULL, NULL, NULL, 'notes-finalized', 34);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'protect-scope', 'Run the cycle', 'Protect scope: move additional ideas to future work instead of extending the cycle.', NULL, NULL, NULL, NULL, 35);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'product-maturity', 'Complete and hand off', 'Confirm each product meets the maturity promised in the charter.', NULL, 'closeout', 0, NULL, 36);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'final-recommendation', 'Complete and hand off', 'Complete the Final Recommendation, including limitations and unresolved concerns.', NULL, 'closeout', 0, NULL, 37);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'future-work', 'Complete and hand off', 'Separate the current recommendation from possible future work or another cycle.', NULL, NULL, NULL, NULL, 38);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'sisc-decisions', 'Complete and hand off', 'State the decisions requested from SISC.', NULL, NULL, NULL, NULL, 39);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'publication-checks', 'Complete and hand off', 'Complete technical, leadership, editorial, accessibility and publication checks.', NULL, NULL, NULL, NULL, 40);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'sisc-review', 'Complete and hand off', 'Submit the material for SISC review and record how feedback was reconciled.', NULL, NULL, NULL, NULL, 41);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'preserve-record', 'Complete and hand off', 'Preserve the final products and supporting record in the designated repository and in InteropHub.', NULL, NULL, NULL, NULL, 42);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'topic-history', 'Complete and hand off', 'Add products, outcomes, rationale, unresolved concerns and proposed future work to the topic history.', NULL, 'closeout', 14, NULL, 43);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'review-series-registrants', 'Complete and hand off', 'Review the meeting series registrants and remove people who aren''t continuing into another cycle.', NULL, 'closeout', 14, NULL, 44);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'communicate-result', 'Complete and hand off', 'Communicate the result through current community channels.', NULL, NULL, NULL, NULL, 45);
INSERT INTO es_checklist_template_item
  (es_checklist_template_id, item_key, phase_label, title, guidance, due_anchor, due_offset_days, check_code, display_order)
VALUES (@template_id, 'close-small-group', 'Complete and hand off', 'Move this Small Group to Closed, with a note summarizing the outcome.', NULL, 'closeout', 30, NULL, 46);
-- END generated checklist templates

-- E1 topic edit history. Keep app_user_token_id nullable until API token attribution is wired up.
CREATE TABLE es_topic_change (
  es_topic_change_id BIGINT NOT NULL AUTO_INCREMENT,
  es_topic_id BIGINT NOT NULL,
  changed_by_user_id BIGINT NOT NULL,
  changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  source VARCHAR(16) NOT NULL,
  change_type VARCHAR(16) NOT NULL,
  changes_json JSON NOT NULL,
  app_user_token_id BIGINT UNSIGNED NULL,
  PRIMARY KEY (es_topic_change_id),
  KEY ix_es_topic_change_topic_changed_at (es_topic_id, changed_at),
  KEY ix_es_topic_change_user (changed_by_user_id),
  KEY ix_es_topic_change_token (app_user_token_id),
  CONSTRAINT fk_es_topic_change_topic
    FOREIGN KEY (es_topic_id) REFERENCES es_topic (es_topic_id),
  CONSTRAINT fk_es_topic_change_user
    FOREIGN KEY (changed_by_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_topic_change_token
    FOREIGN KEY (app_user_token_id) REFERENCES app_user_token (token_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =============================================================================
-- H1 sign-in foundation (topic-reference comments).
-- PKCE on the login code, scopes on access tokens, and the two app_registry
-- rows the guide and personal access tokens use. Keep this block at the end
-- so another stream can append above it and the merge stays local.
--
-- auth_magic_link gets the same PKCE columns as auth_login_code. The contract
-- stores the challenge on the login code; the magic-link copy is what carries
-- it across the email, including when the reader opens the link in a browser
-- that does not have the original servlet session. Registration forms also
-- repeat the values in hidden fields.
-- =============================================================================

ALTER TABLE auth_login_code
  ADD COLUMN code_challenge varchar(128) DEFAULT NULL AFTER requested_url,
  ADD COLUMN code_challenge_method varchar(16) DEFAULT NULL AFTER code_challenge;

ALTER TABLE auth_magic_link
  ADD COLUMN code_challenge varchar(128) DEFAULT NULL AFTER requested_url,
  ADD COLUMN code_challenge_method varchar(16) DEFAULT NULL AFTER code_challenge;

ALTER TABLE app_user_token
  ADD COLUMN scopes varchar(255) DEFAULT NULL AFTER label;

-- Public client shared by every Topic Reference guide. No secret; PKCE is required.
-- No default redirect, so it does not appear as a launchable app on the welcome page.
INSERT INTO app_registry
  (app_code, app_name, default_redirect_url, app_description, managed_by, is_enabled, kill_switch, is_visible)
SELECT 'topic-reference', 'Topic Reference', NULL,
  'Public client shared by Topic Reference guides. No client secret; PKCE is required.',
  'AIRA', 1, 0, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM app_registry WHERE app_code = 'topic-reference');

-- Personal access tokens. Not shown to users (is_visible = 0) and no redirect allowlist.
INSERT INTO app_registry
  (app_code, app_name, default_redirect_url, app_description, managed_by, is_enabled, kill_switch, is_visible)
SELECT 'interophub-api', 'InteropHub API', NULL,
  'Personal access tokens for the InteropHub API. Not shown to users.',
  'AIRA', 1, 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM app_registry WHERE app_code = 'interophub-api');
