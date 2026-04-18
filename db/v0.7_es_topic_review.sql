-- v0.7 - Internal campaign topic review scores
-- Adds campaign-scoped per-user review scores for ACTIVE ES topics.

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE es_topic_review (
  es_topic_review_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_campaign_id          BIGINT UNSIGNED NOT NULL,
  es_topic_id             BIGINT UNSIGNED NOT NULL,
  user_id                 BIGINT NOT NULL,
  community_value_score   TINYINT UNSIGNED NOT NULL,
  created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_topic_review_id),
  UNIQUE KEY uq_es_topic_review_campaign_topic_user (es_campaign_id, es_topic_id, user_id),
  KEY ix_es_topic_review_campaign_user (es_campaign_id, user_id, updated_at),
  KEY ix_es_topic_review_campaign_topic (es_campaign_id, es_topic_id, updated_at),
  CONSTRAINT fk_es_topic_review_campaign FOREIGN KEY (es_campaign_id) REFERENCES es_campaign(es_campaign_id),
  CONSTRAINT fk_es_topic_review_topic    FOREIGN KEY (es_topic_id)    REFERENCES es_topic(es_topic_id),
  CONSTRAINT fk_es_topic_review_user     FOREIGN KEY (user_id)        REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
