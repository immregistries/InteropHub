-- v0.14: Add es_meeting_attendance table for per-day meeting check-in.
-- This is independent of es_topic_meeting_member (formal membership).
-- Anyone can check in whether or not they are a formal meeting member.
-- Deduplication key: (es_topic_meeting_id, attendance_date, email_normalized).
--
-- Also updates v_email_prospect to include meeting attendance as a 5th prospect source.

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE es_meeting_attendance (
  es_meeting_attendance_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_topic_meeting_id       BIGINT UNSIGNED NOT NULL,
  attendance_date           DATE NOT NULL,
  user_id                   BIGINT NULL,
  first_name                VARCHAR(100) NOT NULL,
  last_name                 VARCHAR(100) NULL,
  email                     VARCHAR(254) NOT NULL,
  email_normalized          VARCHAR(254) NOT NULL,
  organization              VARCHAR(200) NULL,
  hope_text                 TEXT NULL,
  created_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_meeting_attendance_id),
  UNIQUE KEY uq_attendance_meeting_date_email (es_topic_meeting_id, attendance_date, email_normalized),
  KEY ix_attendance_meeting_date (es_topic_meeting_id, attendance_date),
  KEY ix_attendance_user (user_id),
  CONSTRAINT fk_attendance_meeting FOREIGN KEY (es_topic_meeting_id)
    REFERENCES es_topic_meeting (es_topic_meeting_id),
  CONSTRAINT fk_attendance_user FOREIGN KEY (user_id)
    REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Update v_email_prospect to include meeting attendance as a 5th source.
CREATE OR REPLACE VIEW v_email_prospect AS
SELECT
    t.email_normalized,
    MIN(t.created_at)                           AS first_contact_at,
    MAX(t.created_at)                           AS last_contact_at,
    SUM(t.src_campaign_reg)                     AS campaign_registration_count,
    SUM(t.src_comment)                          AS comment_count,
    SUM(t.src_subscription)                     AS subscription_count,
    SUM(t.src_meeting_member)                   AS meeting_member_count,
    SUM(t.src_meeting_attendance)               AS meeting_attendance_count
FROM (
    SELECT email_normalized, created_at, 1, 0, 0, 0, 0
      FROM es_campaign_registration
     WHERE email_normalized IS NOT NULL

    UNION ALL

    SELECT email_normalized, created_at, 0, 1, 0, 0, 0
      FROM es_comment
     WHERE email_normalized IS NOT NULL
       AND user_id IS NULL

    UNION ALL

    SELECT email_normalized, created_at, 0, 0, 1, 0, 0
      FROM es_subscription
     WHERE email_normalized IS NOT NULL
       AND user_id IS NULL

    UNION ALL

    SELECT email_normalized, created_at, 0, 0, 0, 1, 0
      FROM es_topic_meeting_member
     WHERE email_normalized IS NOT NULL
       AND user_id IS NULL

    UNION ALL

    SELECT email_normalized, created_at, 0, 0, 0, 0, 1
      FROM es_meeting_attendance
     WHERE email_normalized IS NOT NULL
       AND user_id IS NULL
) AS t (email_normalized, created_at, src_campaign_reg, src_comment, src_subscription, src_meeting_member, src_meeting_attendance)
WHERE t.email_normalized NOT IN (
    SELECT email_normalized
      FROM auth_user
     WHERE status <> 'DELETED'
)
GROUP BY t.email_normalized;
