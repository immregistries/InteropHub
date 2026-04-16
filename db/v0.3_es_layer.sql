-- v0.3 – Emerging Standards interest and subscription layer
-- Run this against an existing interophub database that has v0.1 + v0.2 applied.
-- All tables are additive; no existing tables are altered.

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- -------------------------
-- EMERGING STANDARDS TOPICS
-- -------------------------
CREATE TABLE es_topic (
  es_topic_id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  topic_code          VARCHAR(80) NOT NULL,
  topic_name          VARCHAR(140) NOT NULL,
  description         TEXT NULL,
  neighborhood        VARCHAR(120) NULL,
  priority_iis        TINYINT(1) NOT NULL DEFAULT 0,
  priority_ehr        TINYINT(1) NOT NULL DEFAULT 0,
  priority_cdc        TINYINT(1) NOT NULL DEFAULT 0,
  stage               VARCHAR(80) NULL,         -- e.g. 'Pre-publication', 'Published'
  status              ENUM('ACTIVE','RETIRED','ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
  created_by_user_id  BIGINT NOT NULL,
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_topic_id),
  UNIQUE KEY uq_es_topic_code (topic_code),
  KEY ix_es_topic_status (status),
  CONSTRAINT fk_es_topic_creator FOREIGN KEY (created_by_user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------
-- EMERGING STANDARDS CAMPAIGNS
-- -------------------------
-- A bounded interest-collection effort (Deep Dive, virtual session, etc.).
-- campaign_type is VARCHAR so new meeting types can be added without ALTER.
CREATE TABLE es_campaign (
  es_campaign_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  campaign_code       VARCHAR(80) NOT NULL,
  campaign_name       VARCHAR(160) NOT NULL,
  description         TEXT NULL,
  campaign_type       VARCHAR(80) NOT NULL DEFAULT 'DEEP_DIVE',
  status              ENUM('DRAFT','ACTIVE','CLOSED','ARCHIVED') NOT NULL DEFAULT 'DRAFT',
  allow_topic_comments    TINYINT(1) NOT NULL DEFAULT 1,
  allow_general_comments  TINYINT(1) NOT NULL DEFAULT 1,
  start_at            DATETIME NULL,
  end_at              DATETIME NULL,
  created_by_user_id  BIGINT NOT NULL,
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (es_campaign_id),
  UNIQUE KEY uq_es_campaign_code (campaign_code),
  KEY ix_es_campaign_status (status),
  CONSTRAINT fk_es_campaign_creator FOREIGN KEY (created_by_user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Topics included in a specific campaign (with Deep Dive grouping metadata).
-- topic_set_no: 1-7 for Deep Dive sets; table_no: 1-14 for table assignments.
CREATE TABLE es_campaign_topic (
  es_campaign_topic_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_campaign_id        BIGINT UNSIGNED NOT NULL,
  es_topic_id           BIGINT UNSIGNED NOT NULL,
  topic_set_no          TINYINT UNSIGNED NULL,   -- which set (e.g., 1-7 for Deep Dive)
  table_no              TINYINT UNSIGNED NULL,   -- which table (e.g., 1-14 for Deep Dive)
  display_order         INT NOT NULL DEFAULT 0,
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (es_campaign_topic_id),
  UNIQUE KEY uq_es_campaign_topic (es_campaign_id, es_topic_id),
  KEY ix_es_ct_set   (es_campaign_id, topic_set_no, display_order),
  KEY ix_es_ct_table (es_campaign_id, table_no, display_order),
  CONSTRAINT fk_es_ct_campaign FOREIGN KEY (es_campaign_id) REFERENCES es_campaign(es_campaign_id),
  CONSTRAINT fk_es_ct_topic    FOREIGN KEY (es_topic_id)    REFERENCES es_topic(es_topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------
-- EXPRESSIONS OF INTEREST (VOTES)
-- -------------------------
-- One record per person per topic per campaign.
-- Dedup enforced at app layer (EsInterestService):
--   1. user_id match if logged in
--   2. email_normalized match if email provided
--   3. no dedup for blank-email anonymous voters (allow_multiple by design)
-- NOTE: nullable unique columns are not enforced by MySQL unique indexes;
--       app-layer checks in EsInterestService are the primary safeguard.
CREATE TABLE es_interest (
  es_interest_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_campaign_id      BIGINT UNSIGNED NOT NULL,
  es_topic_id         BIGINT UNSIGNED NOT NULL,
  user_id             BIGINT NULL,              -- populated for logged-in users
  session_key         VARCHAR(128) NULL,        -- browser session; recorded, not enforced
  first_name          VARCHAR(100) NOT NULL,
  last_name           VARCHAR(100) NULL,
  email               VARCHAR(254) NULL,
  email_normalized    VARCHAR(254) NULL,
  opt_in_topic_updates TINYINT(1) NOT NULL DEFAULT 0,
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (es_interest_id),
  KEY ix_es_interest_campaign_topic (es_campaign_id, es_topic_id),
  KEY ix_es_interest_user    (user_id, es_campaign_id),
  KEY ix_es_interest_email   (email_normalized, es_campaign_id),
  KEY ix_es_interest_session (session_key, es_campaign_id),
  CONSTRAINT fk_es_interest_campaign FOREIGN KEY (es_campaign_id) REFERENCES es_campaign(es_campaign_id),
  CONSTRAINT fk_es_interest_topic    FOREIGN KEY (es_topic_id)    REFERENCES es_topic(es_topic_id),
  CONSTRAINT fk_es_interest_user     FOREIGN KEY (user_id)        REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------
-- COMMENTS
-- -------------------------
-- comment_type='TOPIC'  requires es_topic_id.
-- comment_type='GENERAL' or 'NEW_TOPIC_SUGGESTION' has es_topic_id=NULL.
CREATE TABLE es_comment (
  es_comment_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_campaign_id      BIGINT UNSIGNED NOT NULL,
  es_topic_id         BIGINT UNSIGNED NULL,     -- NULL for GENERAL / NEW_TOPIC_SUGGESTION
  user_id             BIGINT NULL,
  session_key         VARCHAR(128) NULL,
  first_name          VARCHAR(100) NOT NULL,
  last_name           VARCHAR(100) NULL,
  email               VARCHAR(254) NULL,
  email_normalized    VARCHAR(254) NULL,
  comment_type        ENUM('TOPIC','GENERAL','NEW_TOPIC_SUGGESTION') NOT NULL,
  comment_text        TEXT NOT NULL,
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (es_comment_id),
  KEY ix_es_comment_campaign (es_campaign_id, comment_type, created_at),
  KEY ix_es_comment_topic    (es_topic_id, created_at),
  KEY ix_es_comment_user     (user_id, created_at),
  CONSTRAINT fk_es_comment_campaign FOREIGN KEY (es_campaign_id) REFERENCES es_campaign(es_campaign_id),
  CONSTRAINT fk_es_comment_topic    FOREIGN KEY (es_topic_id)    REFERENCES es_topic(es_topic_id),
  CONSTRAINT fk_es_comment_user     FOREIGN KEY (user_id)        REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------
-- DURABLE SUBSCRIPTIONS
-- -------------------------
-- Separate from campaign votes; survive across campaigns.
-- GENERAL_ES: es_topic_id IS NULL, subscription_type='GENERAL_ES'
-- TOPIC:      es_topic_id IS NOT NULL, subscription_type='TOPIC'
-- Uniqueness: app-layer only (MySQL does not enforce unique across nullable columns).
-- unsubscribe_token_hash: SHA-256 of a random token issued at creation;
--   used for one-click unsubscribe links in future email sends.
CREATE TABLE es_subscription (
  es_subscription_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id                 BIGINT NULL,
  email                   VARCHAR(254) NOT NULL,
  email_normalized        VARCHAR(254) NOT NULL,
  es_topic_id             BIGINT UNSIGNED NULL,   -- NULL = GENERAL_ES subscription
  subscription_type       ENUM('GENERAL_ES','TOPIC') NOT NULL,
  status                  ENUM('SUBSCRIBED','UNSUBSCRIBED') NOT NULL DEFAULT 'SUBSCRIBED',
  source_campaign_id      BIGINT UNSIGNED NULL,   -- which campaign produced this subscription
  unsubscribe_token_hash  BINARY(32) NULL,        -- SHA-256(raw token); for email-link unsubscribe
  created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  unsubscribed_at         DATETIME NULL,
  PRIMARY KEY (es_subscription_id),
  UNIQUE KEY uq_es_sub_token (unsubscribe_token_hash),
  KEY ix_es_sub_email  (email_normalized, status),
  KEY ix_es_sub_user   (user_id, status),
  KEY ix_es_sub_topic  (es_topic_id, status),
  CONSTRAINT fk_es_sub_user     FOREIGN KEY (user_id)            REFERENCES auth_user(user_id),
  CONSTRAINT fk_es_sub_topic    FOREIGN KEY (es_topic_id)        REFERENCES es_topic(es_topic_id),
  CONSTRAINT fk_es_sub_campaign FOREIGN KEY (source_campaign_id) REFERENCES es_campaign(es_campaign_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
