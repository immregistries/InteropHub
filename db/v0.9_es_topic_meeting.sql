-- v0.9 - Add optional ES topic meeting and meeting membership tables.

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE es_topic_meeting (
  es_topic_meeting_id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_topic_id            BIGINT UNSIGNED NOT NULL,
  meeting_name           VARCHAR(160) NOT NULL,
  meeting_description    TEXT NULL,
  join_requires_approval TINYINT(1) NOT NULL DEFAULT 0,
  status                 ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
  disabled_at            DATETIME NULL,
  disabled_by_user_id    BIGINT NULL,
  created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_topic_meeting_id),
  UNIQUE KEY uq_es_topic_meeting_topic (es_topic_id),
  KEY ix_es_topic_meeting_status (status),
  CONSTRAINT fk_es_topic_meeting_topic FOREIGN KEY (es_topic_id) REFERENCES es_topic(es_topic_id),
  CONSTRAINT fk_es_topic_meeting_disabled_user FOREIGN KEY (disabled_by_user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE es_topic_meeting_member (
  es_topic_meeting_member_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_topic_meeting_id        BIGINT UNSIGNED NOT NULL,
  user_id                    BIGINT NULL,
  email                      VARCHAR(254) NOT NULL,
  email_normalized           VARCHAR(254) NOT NULL,
  membership_status          ENUM('REQUESTED','APPROVED','DECLINED','REMOVED') NOT NULL DEFAULT 'REQUESTED',
  source_campaign_id         BIGINT UNSIGNED NULL,
  approved_by_user_id        BIGINT NULL,
  approved_at                DATETIME NULL,
  created_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_topic_meeting_member_id),
  UNIQUE KEY uq_es_topic_meeting_member_email (es_topic_meeting_id, email_normalized),
  KEY ix_es_topic_meeting_member_status (es_topic_meeting_id, membership_status),
  KEY ix_es_topic_meeting_member_email (email_normalized, membership_status),
  KEY ix_es_topic_meeting_member_user (user_id),
  CONSTRAINT fk_es_tmm_meeting FOREIGN KEY (es_topic_meeting_id)
    REFERENCES es_topic_meeting(es_topic_meeting_id),
  CONSTRAINT fk_es_tmm_user FOREIGN KEY (user_id)
    REFERENCES auth_user(user_id),
  CONSTRAINT fk_es_tmm_source_campaign FOREIGN KEY (source_campaign_id)
    REFERENCES es_campaign(es_campaign_id),
  CONSTRAINT fk_es_tmm_approved_by_user FOREIGN KEY (approved_by_user_id)
    REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
