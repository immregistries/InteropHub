SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ---------------------------------------------------------------------------
-- es_meeting
-- ---------------------------------------------------------------------------
CREATE TABLE es_meeting (
  es_meeting_id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  meeting_key           VARCHAR(80) NULL,
  meeting_name          VARCHAR(160) NOT NULL,
  meeting_description   TEXT NULL,
  scheduled_start       DATETIME NOT NULL,
  scheduled_end         DATETIME NULL,
  timezone_id           VARCHAR(64) NULL,
  status                ENUM('DRAFT','PROPOSED','FINALIZED','COMPLETED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  created_by_user_id    BIGINT UNSIGNED NOT NULL,
  finalized_at          DATETIME NULL,
  completed_at          DATETIME NULL,
  cancelled_at          DATETIME NULL,
  cancellation_reason   TEXT NULL,
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_meeting_id),
  KEY ix_es_meeting_scheduled_start (scheduled_start),
  KEY ix_es_meeting_status (status),
  KEY ix_es_meeting_key (meeting_key),
  CONSTRAINT fk_es_meeting_created_by FOREIGN KEY (created_by_user_id)
    REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- es_meeting_agenda_item
-- ---------------------------------------------------------------------------
CREATE TABLE es_meeting_agenda_item (
  es_meeting_agenda_item_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_meeting_id              BIGINT UNSIGNED NOT NULL,
  es_topic_id                BIGINT UNSIGNED NULL,
  display_order              INT NOT NULL DEFAULT 0,
  title                      VARCHAR(200) NOT NULL,
  agenda_markdown            TEXT NULL,
  time_minutes               INT NULL,
  status                     ENUM('DRAFT','PROPOSED','ACCEPTED','NEEDS_REVISION','POSTPONED','COVERED','NOT_COVERED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  proposed_by_user_id        BIGINT UNSIGNED NULL,
  accepted_at                DATETIME NULL,
  postponed_to_meeting_id    BIGINT UNSIGNED NULL,
  status_note                TEXT NULL,
  created_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_meeting_agenda_item_id),
  KEY ix_es_mai_meeting_order (es_meeting_id, display_order),
  KEY ix_es_mai_topic (es_topic_id),
  KEY ix_es_mai_status (status),
  CONSTRAINT fk_es_mai_meeting FOREIGN KEY (es_meeting_id)
    REFERENCES es_meeting(es_meeting_id),
  CONSTRAINT fk_es_mai_topic FOREIGN KEY (es_topic_id)
    REFERENCES es_topic(es_topic_id),
  CONSTRAINT fk_es_mai_proposed_by FOREIGN KEY (proposed_by_user_id)
    REFERENCES auth_user(user_id),
  CONSTRAINT fk_es_mai_postponed_to FOREIGN KEY (postponed_to_meeting_id)
    REFERENCES es_meeting(es_meeting_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- es_agenda_item_presenter
-- ---------------------------------------------------------------------------
CREATE TABLE es_agenda_item_presenter (
  es_agenda_item_presenter_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_meeting_agenda_item_id    BIGINT UNSIGNED NOT NULL,
  user_id                      BIGINT UNSIGNED NULL,
  email                        VARCHAR(254) NOT NULL,
  email_normalized             VARCHAR(254) NOT NULL,
  display_name                 VARCHAR(160) NULL,
  presenter_role               ENUM('LEAD','SUPPORTING','FACILITATOR','REQUESTED_REVIEWER') NOT NULL DEFAULT 'LEAD',
  status                       ENUM('INVITED','ACCEPTED','DECLINED','NEEDS_CHANGES','REMOVED') NOT NULL DEFAULT 'INVITED',
  response_note                TEXT NULL,
  responded_at                 DATETIME NULL,
  created_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_agenda_item_presenter_id),
  KEY ix_es_aip_item_status (es_meeting_agenda_item_id, status),
  KEY ix_es_aip_email_status (email_normalized, status),
  CONSTRAINT fk_es_aip_agenda_item FOREIGN KEY (es_meeting_agenda_item_id)
    REFERENCES es_meeting_agenda_item(es_meeting_agenda_item_id),
  CONSTRAINT fk_es_aip_user FOREIGN KEY (user_id)
    REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- es_agenda_item_comment
-- ---------------------------------------------------------------------------
CREATE TABLE es_agenda_item_comment (
  es_agenda_item_comment_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_meeting_agenda_item_id  BIGINT UNSIGNED NOT NULL,
  user_id                    BIGINT UNSIGNED NULL,
  email                      VARCHAR(254) NULL,
  comment_type               ENUM('COMMENT','CHANGE_REQUEST','POSTPONE_REQUEST','DECLINE_REASON','MEETING_NOTE') NOT NULL DEFAULT 'COMMENT',
  comment_markdown           TEXT NOT NULL,
  created_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (es_agenda_item_comment_id),
  KEY ix_es_aic_item_created (es_meeting_agenda_item_id, created_at),
  CONSTRAINT fk_es_aic_agenda_item FOREIGN KEY (es_meeting_agenda_item_id)
    REFERENCES es_meeting_agenda_item(es_meeting_agenda_item_id),
  CONSTRAINT fk_es_aic_user FOREIGN KEY (user_id)
    REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
