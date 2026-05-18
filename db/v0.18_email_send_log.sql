-- v0.18: email_send_log
-- Business-level audit trail of every email successfully sent by InteropHub.
-- One row per delivered email (not per SMTP lifecycle event).
-- Stores reason, subject, and body so admins can see what was sent and why.
-- Magic link emails are dual-recorded here and in auth_magic_link_send_event.

CREATE TABLE email_send_log (
  email_log_id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  email_reason                 VARCHAR(80) NOT NULL,           -- stable code; see EmailReason.java for all values
  recipient_email              VARCHAR(254) NOT NULL,
  recipient_email_normalized   VARCHAR(254) NOT NULL,
  user_id                      BIGINT NULL,                    -- FK auth_user; NULL if recipient has no account
  subject                      VARCHAR(500) NOT NULL,
  body_text                    TEXT NULL,
  sent_at                      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  smtp_message_id              VARCHAR(255) NULL,
  smtp_provider                VARCHAR(80) NULL,
  magic_id                     BIGINT UNSIGNED NULL,           -- cross-ref to auth_magic_link for magic link emails
  PRIMARY KEY (email_log_id),
  KEY ix_email_log_email_time   (recipient_email_normalized, sent_at),
  KEY ix_email_log_user_time    (user_id, sent_at),
  KEY ix_email_log_reason_time  (email_reason, sent_at),
  KEY ix_email_log_sent_at      (sent_at),
  CONSTRAINT fk_email_log_user  FOREIGN KEY (user_id)  REFERENCES auth_user(user_id),
  CONSTRAINT fk_email_log_magic FOREIGN KEY (magic_id) REFERENCES auth_magic_link(magic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
