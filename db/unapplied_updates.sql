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
