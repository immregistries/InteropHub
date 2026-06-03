-- v0.26: Add Doodle-style meeting availability poll tables.
-- Stores poll option times in UTC and user responses per option.

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE es_topic_meeting_poll (
  es_topic_meeting_poll_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_topic_meeting_id      BIGINT UNSIGNED NOT NULL,
  poll_name                VARCHAR(160) NOT NULL,
  poll_description         TEXT NULL,
  default_timezone         VARCHAR(80) NOT NULL,
  created_at               DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at               DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (es_topic_meeting_poll_id),
  KEY ix_es_tmp_poll_meeting (es_topic_meeting_id),
  CONSTRAINT fk_es_tmp_poll_meeting FOREIGN KEY (es_topic_meeting_id)
    REFERENCES es_topic_meeting(es_topic_meeting_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE es_topic_meeting_poll_option (
  es_topic_meeting_poll_option_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_topic_meeting_poll_id        BIGINT UNSIGNED NOT NULL,
  starts_at_utc                   DATETIME(6) NOT NULL,
  ends_at_utc                     DATETIME(6) NULL,
  display_order                   INT NOT NULL,
  created_at                      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at                      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (es_topic_meeting_poll_option_id),
  KEY ix_es_tmp_poll_option_poll_order (es_topic_meeting_poll_id, display_order),
  KEY ix_es_tmp_poll_option_poll_start (es_topic_meeting_poll_id, starts_at_utc),
  CONSTRAINT fk_es_tmp_poll_option_poll FOREIGN KEY (es_topic_meeting_poll_id)
    REFERENCES es_topic_meeting_poll(es_topic_meeting_poll_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE es_topic_meeting_poll_response (
  es_topic_meeting_poll_response_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_topic_meeting_poll_option_id   BIGINT UNSIGNED NOT NULL,
  user_id                           BIGINT NOT NULL,
  response                          ENUM('YES','MAYBE','NO') NOT NULL,
  created_at                        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at                        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (es_topic_meeting_poll_response_id),
  UNIQUE KEY uq_es_tmp_poll_response_user_option (user_id, es_topic_meeting_poll_option_id),
  KEY ix_es_tmp_poll_response_option (es_topic_meeting_poll_option_id),
  KEY ix_es_tmp_poll_response_user (user_id),
  KEY ix_es_tmp_poll_response_response (response),
  CONSTRAINT fk_es_tmp_poll_response_option FOREIGN KEY (es_topic_meeting_poll_option_id)
    REFERENCES es_topic_meeting_poll_option(es_topic_meeting_poll_option_id),
  CONSTRAINT fk_es_tmp_poll_response_user FOREIGN KEY (user_id)
    REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;