-- Pending schema/data changes for the next production release.
-- Process, conventions, and how to fold local admin/UI edits (e.g. Topic
-- Board layout changes made in the running app) back into this file before
-- a refresh discards them: see docs/database-release-practice.md.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Private Topic Space auto-membership by email domain.
-- A PRIVATE space can list email domains (comma-separated, e.g.
-- 'immregistries.org, cdc.gov'). Any active user with a verified email at one
-- of those domains is added as a MEMBER on sign-in, and in a backfill when an
-- admin saves the list - unless they already have a membership row of any kind.
-- Removing a member now marks the row REMOVED instead of deleting it, so a
-- removal sticks and auto-add never re-adds that person. Only ACTIVE rows grant
-- access.
ALTER TABLE es_topic_space
  ADD COLUMN auto_member_email_domains VARCHAR(500) NULL AFTER visibility;

ALTER TABLE es_topic_space_member
  ADD COLUMN member_status ENUM('ACTIVE','REMOVED') NOT NULL DEFAULT 'ACTIVE' AFTER role,
  ADD COLUMN member_source ENUM('MANUAL','AUTO_DOMAIN') NOT NULL DEFAULT 'MANUAL' AFTER member_status,
  ADD COLUMN status_changed_at DATETIME NULL AFTER member_source,
  ADD COLUMN status_changed_by_user_id BIGINT NULL AFTER status_changed_at,
  ADD KEY ix_es_topic_space_member_user_status (user_id, member_status),
  ADD CONSTRAINT fk_es_topic_space_member_status_changed_by FOREIGN KEY (status_changed_by_user_id)
    REFERENCES auth_user (user_id);

-- AIRA Opportunity Nursery: open to everyone with an AIRA email.
UPDATE es_topic_space
SET auto_member_email_domains = 'immregistries.org'
WHERE space_code = 'aira-opportunity-nursery';

-- One-time backfill for already-registered users, same rule the app applies on
-- sign-in: verified, active, exact domain match, and no existing row.
INSERT INTO es_topic_space_member
  (es_topic_space_id, user_id, role, member_status, member_source, created_at, updated_at)
SELECT s.es_topic_space_id, u.user_id, 'MEMBER', 'ACTIVE', 'AUTO_DOMAIN', NOW(), NOW()
FROM es_topic_space s
JOIN auth_user u
  ON u.status = 'ACTIVE'
 AND u.email_verified = 1
 AND SUBSTRING_INDEX(u.email_normalized, '@', -1) = 'immregistries.org'
WHERE s.space_code = 'aira-opportunity-nursery'
  AND NOT EXISTS (
    SELECT 1 FROM es_topic_space_member m
    WHERE m.es_topic_space_id = s.es_topic_space_id AND m.user_id = u.user_id
  );
