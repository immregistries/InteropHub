-- v0.19: es_topic_relationship and es_topic_curation
-- es_topic_relationship: typed directional links between any two topics.
--   Champions or admins of the from_topic can create/remove links.
--   Inbound links are derived by querying to_topic_id, using inverse labels for display.
-- es_topic_curation: rich editorial curated topic lists managed by champions/admins
--   of the curator_topic. Supports alias, category grouping, editorial notes, and free-text status.

CREATE TABLE es_topic_relationship (
  es_topic_relationship_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  from_topic_id             BIGINT UNSIGNED NOT NULL,
  to_topic_id               BIGINT UNSIGNED NOT NULL,
  relationship_type         VARCHAR(40) NOT NULL,              -- stored as enum name e.g. RELATED_TO
  display_order             INT NOT NULL DEFAULT 0,
  created_by_user_id        BIGINT NOT NULL,
  created_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (es_topic_relationship_id),
  UNIQUE KEY uq_rel (from_topic_id, to_topic_id, relationship_type),
  KEY ix_rel_from (from_topic_id),
  KEY ix_rel_to   (to_topic_id),
  CONSTRAINT fk_rel_from    FOREIGN KEY (from_topic_id)       REFERENCES es_topic(es_topic_id),
  CONSTRAINT fk_rel_to      FOREIGN KEY (to_topic_id)         REFERENCES es_topic(es_topic_id),
  CONSTRAINT fk_rel_creator FOREIGN KEY (created_by_user_id)  REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE es_topic_curation (
  es_topic_curation_id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  curator_topic_id       BIGINT UNSIGNED NOT NULL,             -- the organising topic
  curated_topic_id       BIGINT UNSIGNED NOT NULL,             -- topic included in the curated list
  topic_alias            VARCHAR(140) NULL,                    -- champion's display name for this entry
  category_label         VARCHAR(80)  NULL,                    -- grouping within the list e.g. 'Core'
  editorial_note         TEXT NULL,                            -- why it's in the list / status notes
  curation_status        VARCHAR(80)  NULL,                    -- free-text; UI offers datalist of existing values
  display_order          INT NOT NULL DEFAULT 0,
  created_by_user_id     BIGINT NOT NULL,
  created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_topic_curation_id),
  UNIQUE KEY uq_curation (curator_topic_id, curated_topic_id),
  KEY ix_curation_curated (curated_topic_id),
  CONSTRAINT fk_curation_curator  FOREIGN KEY (curator_topic_id)   REFERENCES es_topic(es_topic_id),
  CONSTRAINT fk_curation_curated  FOREIGN KEY (curated_topic_id)   REFERENCES es_topic(es_topic_id),
  CONSTRAINT fk_curation_creator  FOREIGN KEY (created_by_user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
