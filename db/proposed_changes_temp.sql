CREATE TABLE es_meeting_communication (
  es_meeting_communication_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_meeting_id               BIGINT UNSIGNED NOT NULL,

  communication_type ENUM(
    'CALL_FOR_TOPICS',
    'PROPOSED_AGENDA',
    'FINAL_AGENDA',
    'REMINDER',
    'CANCELLED'
  ) NOT NULL,

  status ENUM(
    'DRAFT',
    'SCHEDULED',
    'SENDING',
    'SENT',
    'CANCELLED',
    'FAILED'
  ) NOT NULL DEFAULT 'DRAFT',

  scheduled_send_at DATETIME NULL,
  timezone_id       VARCHAR(64) NULL,

  expected_meeting_status ENUM(
    'DRAFT',
    'PROPOSED',
    'FINALIZED',
    'COMPLETED',
    'CANCELLED'
  ) NULL,

  include_general_members    TINYINT(1) NOT NULL DEFAULT 1,
  include_topic_subscribers  TINYINT(1) NOT NULL DEFAULT 1,
  include_topic_champions    TINYINT(1) NOT NULL DEFAULT 1,
  include_presenters         TINYINT(1) NOT NULL DEFAULT 1,

  subject_override VARCHAR(500) NULL,
  note_to_include  TEXT NULL,

  created_by_user_id BIGINT UNSIGNED NOT NULL,
  approved_by_user_id BIGINT UNSIGNED NULL,
  approved_at DATETIME NULL,

  sent_started_at DATETIME NULL,
  sent_completed_at DATETIME NULL,
  cancelled_at DATETIME NULL,
  cancelled_by_user_id BIGINT UNSIGNED NULL,
  cancellation_reason TEXT NULL,

  last_error TEXT NULL,

  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (es_meeting_communication_id),

  KEY ix_es_mc_meeting (es_meeting_id),
  KEY ix_es_mc_status_send (status, scheduled_send_at),
  KEY ix_es_mc_type (communication_type),
  KEY ix_es_mc_expected_status (expected_meeting_status),

  CONSTRAINT fk_es_mc_meeting FOREIGN KEY (es_meeting_id)
    REFERENCES es_meeting(es_meeting_id),

  CONSTRAINT fk_es_mc_created_by FOREIGN KEY (created_by_user_id)
    REFERENCES auth_user(user_id),

  CONSTRAINT fk_es_mc_approved_by FOREIGN KEY (approved_by_user_id)
    REFERENCES auth_user(user_id),

  CONSTRAINT fk_es_mc_cancelled_by FOREIGN KEY (cancelled_by_user_id)
    REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


ALTER TABLE email_send_log
  ADD COLUMN es_meeting_communication_id BIGINT UNSIGNED NULL AFTER magic_id,
  ADD KEY ix_email_log_meeting_comm (es_meeting_communication_id),
  ADD CONSTRAINT fk_email_log_meeting_comm
    FOREIGN KEY (es_meeting_communication_id)
    REFERENCES es_meeting_communication(es_meeting_communication_id);