-- Pending schema/data changes for the next production release.
-- Process, conventions, and how to fold local admin/UI edits (e.g. Topic
-- Board layout changes made in the running app) back into this file before
-- a refresh discards them: see docs/database-release-practice.md.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Supporters: organizations that have agreed to be publicly identified as
-- supporting efforts related to one or more Topics. System-wide (no Topic
-- Space ownership column) - see docs/add-supporters.md. No delete operation
-- exists for either table; a Supporter is deactivated instead, and a
-- relationship is removed by deleting only its es_topic_supporter row.
CREATE TABLE supporter (
  supporter_id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  short_name     VARCHAR(120) NOT NULL,
  full_name      VARCHAR(200) NOT NULL,
  description    TEXT NULL,
  website_url    VARCHAR(500) NULL,
  is_active      TINYINT(1) NOT NULL DEFAULT 1,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (supporter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Many-to-many Topic <-> Supporter. Mirrors es_topic_neighborhood: a plain
-- join table with a uniqueness constraint on the pair and no status/type
-- column, since support either exists or it doesn't.
CREATE TABLE es_topic_supporter (
  es_topic_supporter_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_topic_id            BIGINT NOT NULL,
  supporter_id           BIGINT NOT NULL,
  created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (es_topic_supporter_id),
  UNIQUE KEY uq_es_topic_supporter (es_topic_id, supporter_id),
  KEY ix_es_topic_supporter_topic (es_topic_id),
  KEY ix_es_topic_supporter_supporter (supporter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
