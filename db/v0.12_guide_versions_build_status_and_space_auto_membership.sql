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

-- Topic relationship review round 2 (2026-11-11): Immunization CDS cluster.
-- See docs/topic-relationships/rounds/2026-11-11-round-2-immunization-cds-cluster.md
INSERT INTO es_topic_relationship
  (from_topic_id, to_topic_id, relationship_type, display_order, created_by_user_id, created_at)
SELECT ft.es_topic_id, tt.es_topic_id, x.rel_type, 0, 2, UTC_TIMESTAMP(6)
FROM (
  VALUES
    ROW('CDS-Contextual-Conditions', 'hl7-v2-contextual-conditions-exchange', 'FEEDS_INTO'),
    ROW('Immunization-CDS', 'hl7-v2-contextual-conditions-exchange', 'RELATED_TO')
) AS x (from_code, to_code, rel_type)
JOIN es_topic ft ON ft.topic_code = x.from_code
JOIN es_topic tt ON tt.topic_code = x.to_code
WHERE NOT EXISTS (
  SELECT 1 FROM es_topic_relationship r
  WHERE r.from_topic_id = ft.es_topic_id
    AND r.to_topic_id = tt.es_topic_id
    AND r.relationship_type = x.rel_type
);

-- ImmDS 1.0.0 is frozen; Contextual Conditions reach FHIR through its successor.
DELETE r FROM es_topic_relationship r
JOIN es_topic ft ON ft.es_topic_id = r.from_topic_id
JOIN es_topic tt ON tt.es_topic_id = r.to_topic_id
WHERE ft.topic_code = 'CDS-Contextual-Conditions'
  AND tt.topic_code = 'Immunization-Decision-Support'
  AND r.relationship_type = 'FEEDS_INTO';

-- Build records for Topic Reference guides. One row per hosted upload, plus
-- one per manifest-only report from an externally hosted guide. The manifest
-- is kept whole; the columns are copies of its fields used for listing and filtering.
-- See docs/tasks/topic-reference-summary-annotations-and-guide-status.md, Workstream B.
CREATE TABLE es_guide_build (
  guide_build_id BIGINT NOT NULL AUTO_INCREMENT,
  guide_document_id BIGINT NOT NULL,
  guide_hosted_version_id BIGINT NULL,
  source VARCHAR(16) NOT NULL COMMENT 'UPLOAD or MANIFEST',
  label VARCHAR(64) NULL,
  manifest_schema INT NULL,
  process_version VARCHAR(32) NULL,
  document_version VARCHAR(64) NULL,
  document_status VARCHAR(40) NULL,
  built_at DATETIME(6) NULL,
  source_commit VARCHAR(64) NULL,
  warning_count INT NULL,
  manifest_json JSON NULL,
  manifest_error VARCHAR(300) NULL,
  received_by_user_id BIGINT NOT NULL,
  received_at DATETIME(6) NOT NULL,
  PRIMARY KEY (guide_build_id),
  KEY ix_es_guide_build_document (guide_document_id, received_at),
  CONSTRAINT fk_es_guide_build_document FOREIGN KEY (guide_document_id) REFERENCES es_guide_document (guide_document_id),
  CONSTRAINT fk_es_guide_build_version FOREIGN KEY (guide_hosted_version_id) REFERENCES es_guide_hosted_version (guide_hosted_version_id),
  CONSTRAINT fk_es_guide_build_user FOREIGN KEY (received_by_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT chk_es_guide_build_source CHECK (source IN ('UPLOAD', 'MANIFEST'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Topic Reference version display and comment modes.
-- One row per label of a guide that an author has configured. A label with no
-- row behaves as UNLISTED / OPEN. "At most one CURRENT per guide" is enforced
-- in the service, in one transaction, not by a constraint.
-- See docs/tasks/topic-reference-version-display-and-comment-modes.md, Section 4.
CREATE TABLE es_guide_label (
  guide_label_id BIGINT NOT NULL AUTO_INCREMENT,
  guide_document_id BIGINT NOT NULL,
  label VARCHAR(64) NOT NULL,
  display_state VARCHAR(16) NOT NULL DEFAULT 'UNLISTED',
  comment_mode VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  comment_mode_set_at DATETIME(6) NULL,
  updated_by_user_id BIGINT NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (guide_label_id),
  UNIQUE KEY uq_es_guide_label (guide_document_id, label),
  KEY fk_es_guide_label_user (updated_by_user_id),
  CONSTRAINT fk_es_guide_label_document FOREIGN KEY (guide_document_id) REFERENCES es_guide_document (guide_document_id),
  CONSTRAINT fk_es_guide_label_user FOREIGN KEY (updated_by_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT chk_es_guide_label_display CHECK (display_state IN ('CURRENT', 'LISTED', 'UNLISTED', 'AUTHORS_ONLY', 'RETIRED')),
  CONSTRAINT chk_es_guide_label_mode CHECK (comment_mode IN ('OPEN', 'RELEASE', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- The label each new comment, reply, or review flag was written on, resolved
-- from the guide's pageUrl. NULL for older rows and older guides (no backfill).
ALTER TABLE es_guide_comment ADD COLUMN made_on_label VARCHAR(64) NULL;
ALTER TABLE es_guide_review_flag ADD COLUMN made_on_label VARCHAR(64) NULL;
