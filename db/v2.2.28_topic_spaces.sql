SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE es_topic_space (
  es_topic_space_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  space_code        VARCHAR(80) NOT NULL,
  space_name        VARCHAR(140) NOT NULL,
  description       TEXT NULL,
  visibility        ENUM('PUBLIC','PRIVATE') NOT NULL,
  display_order     INT NOT NULL DEFAULT 0,
  is_active         TINYINT(1) NOT NULL DEFAULT 1,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_topic_space_id),
  UNIQUE KEY uq_es_topic_space_code (space_code),
  KEY ix_es_topic_space_visible_order (visibility, is_active, display_order, space_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE es_topic_space_member (
  es_topic_space_member_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_topic_space_id        BIGINT UNSIGNED NOT NULL,
  user_id                  BIGINT NOT NULL,
  role                     ENUM('MEMBER','ADMIN') NOT NULL DEFAULT 'MEMBER',
  created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_topic_space_member_id),
  UNIQUE KEY uq_es_topic_space_member_space_user (es_topic_space_id, user_id),
  KEY ix_es_topic_space_member_user (user_id),
  KEY ix_es_topic_space_member_space_role (es_topic_space_id, role),
  CONSTRAINT fk_es_topic_space_member_space FOREIGN KEY (es_topic_space_id)
    REFERENCES es_topic_space(es_topic_space_id),
  CONSTRAINT fk_es_topic_space_member_user FOREIGN KEY (user_id)
    REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE es_topic
  ADD COLUMN es_topic_space_id BIGINT UNSIGNED NULL AFTER confluence_url,
  ADD KEY ix_es_topic_space_id (es_topic_space_id),
  ADD CONSTRAINT fk_es_topic_topic_space FOREIGN KEY (es_topic_space_id)
    REFERENCES es_topic_space(es_topic_space_id);

ALTER TABLE es_neighborhood
  ADD COLUMN es_topic_space_id BIGINT UNSIGNED NULL AFTER description,
  ADD KEY ix_es_neighborhood_topic_space (es_topic_space_id),
  ADD CONSTRAINT fk_es_neighborhood_topic_space FOREIGN KEY (es_topic_space_id)
    REFERENCES es_topic_space(es_topic_space_id);

ALTER TABLE es_meeting
  ADD COLUMN es_topic_space_id BIGINT UNSIGNED NULL AFTER es_topic_meeting_id,
  ADD KEY ix_es_meeting_topic_space (es_topic_space_id),
  ADD CONSTRAINT fk_es_meeting_topic_space FOREIGN KEY (es_topic_space_id)
    REFERENCES es_topic_space(es_topic_space_id);

DELIMITER $$

DROP PROCEDURE IF EXISTS migrate_topic_spaces $$
CREATE PROCEDURE migrate_topic_spaces()
BEGIN
  DECLARE v_emerging_standards_space_id BIGINT UNSIGNED;
  DECLARE v_building_bridges_space_id BIGINT UNSIGNED;
  DECLARE v_nursery_space_id BIGINT UNSIGNED;
  DECLARE v_country_interview_neighborhood_id BIGINT UNSIGNED;
  DECLARE v_topic_count_before BIGINT UNSIGNED DEFAULT 0;
  DECLARE v_topic_count_after BIGINT UNSIGNED DEFAULT 0;
  DECLARE v_count BIGINT UNSIGNED DEFAULT 0;

  SELECT COUNT(*)
    INTO v_topic_count_before
  FROM es_topic;

  INSERT INTO es_topic_space (
    space_code,
    space_name,
    description,
    visibility,
    display_order,
    is_active,
    created_at,
    updated_at
  )
  VALUES
    ('emerging-standards', 'Emerging Standards', 'Legacy default Topic Space for existing InteropHub behavior.', 'PUBLIC', 10, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    ('building-bridges', 'Building Bridges', 'Public Topic Space for country and international organization topics.', 'PUBLIC', 20, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    ('aira-opportunity-nursery', 'AIRA Opportunity Nursery', 'Private Topic Space for internal strategic opportunity review and leadership discussion.', 'PRIVATE', 30, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP())
  ON DUPLICATE KEY UPDATE
    space_name = VALUES(space_name),
    description = VALUES(description),
    visibility = VALUES(visibility),
    display_order = VALUES(display_order),
    is_active = VALUES(is_active),
    updated_at = UTC_TIMESTAMP();

  SELECT COUNT(*)
    INTO v_count
  FROM es_topic_space
  WHERE space_code IN ('emerging-standards', 'building-bridges', 'aira-opportunity-nursery');

  IF v_count <> 3 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: expected three seeded Topic Spaces.';
  END IF;

  SELECT es_topic_space_id
    INTO v_emerging_standards_space_id
  FROM es_topic_space
  WHERE space_code = 'emerging-standards';

  SELECT es_topic_space_id
    INTO v_building_bridges_space_id
  FROM es_topic_space
  WHERE space_code = 'building-bridges';

  SELECT es_topic_space_id
    INTO v_nursery_space_id
  FROM es_topic_space
  WHERE space_code = 'aira-opportunity-nursery';

  IF v_emerging_standards_space_id IS NULL
     OR v_building_bridges_space_id IS NULL
     OR v_nursery_space_id IS NULL THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: one or more seeded Topic Spaces could not be resolved.';
  END IF;

  SELECT COUNT(*)
    INTO v_count
  FROM es_neighborhood
  WHERE LOWER(TRIM(neighborhood_name)) = 'country interview';

  IF v_count <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: expected exactly one Country Interview neighborhood.';
  END IF;

  SELECT es_neighborhood_id
    INTO v_country_interview_neighborhood_id
  FROM es_neighborhood
  WHERE LOWER(TRIM(neighborhood_name)) = 'country interview';

  SELECT COUNT(*)
    INTO v_count
  FROM es_topic_neighborhood tn
  LEFT JOIN es_topic t
    ON t.es_topic_id = tn.es_topic_id
  WHERE tn.es_neighborhood_id = v_country_interview_neighborhood_id
    AND t.es_topic_id IS NULL;

  IF v_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: Country Interview neighborhood assignments reference missing topics.';
  END IF;

  DROP TEMPORARY TABLE IF EXISTS tmp_building_bridges_topic_ids;
  CREATE TEMPORARY TABLE tmp_building_bridges_topic_ids (
    es_topic_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (es_topic_id)
  ) ENGINE=InnoDB;

  INSERT INTO tmp_building_bridges_topic_ids (es_topic_id)
  SELECT DISTINCT tn.es_topic_id
  FROM es_topic_neighborhood tn
  JOIN es_topic t
    ON t.es_topic_id = tn.es_topic_id
  WHERE tn.es_neighborhood_id = v_country_interview_neighborhood_id;

  UPDATE es_topic
  SET es_topic_space_id = v_emerging_standards_space_id;

  UPDATE es_neighborhood
  SET es_topic_space_id = v_emerging_standards_space_id;

  UPDATE es_meeting
  SET es_topic_space_id = v_emerging_standards_space_id;

  UPDATE es_topic t
  JOIN tmp_building_bridges_topic_ids bb
    ON bb.es_topic_id = t.es_topic_id
  SET t.es_topic_space_id = v_building_bridges_space_id,
      t.neighborhood = NULL;

  DELETE tn
  FROM es_topic_neighborhood tn
  JOIN tmp_building_bridges_topic_ids bb
    ON bb.es_topic_id = tn.es_topic_id;

  DELETE FROM es_neighborhood
  WHERE es_neighborhood_id = v_country_interview_neighborhood_id;

  SELECT COUNT(*)
    INTO v_count
  FROM es_topic_space
  WHERE (space_code = 'emerging-standards' AND visibility = 'PUBLIC')
     OR (space_code = 'building-bridges' AND visibility = 'PUBLIC')
     OR (space_code = 'aira-opportunity-nursery' AND visibility = 'PRIVATE');

  IF v_count <> 3 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: seeded Topic Space visibilities are not correct.';
  END IF;

  SELECT COUNT(*)
    INTO v_count
  FROM es_topic
  WHERE es_topic_space_id IS NULL;

  IF v_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: one or more topics were left without a Topic Space.';
  END IF;

  SELECT COUNT(*)
    INTO v_count
  FROM es_neighborhood
  WHERE es_topic_space_id IS NULL;

  IF v_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: one or more neighborhoods were left without a Topic Space.';
  END IF;

  SELECT COUNT(*)
    INTO v_count
  FROM es_meeting
  WHERE es_topic_space_id IS NULL;

  IF v_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: one or more meetings were left without a host Topic Space.';
  END IF;

  SELECT COUNT(*)
    INTO v_count
  FROM es_topic_neighborhood tn
  JOIN es_topic t
    ON t.es_topic_id = tn.es_topic_id
  WHERE t.es_topic_space_id = v_building_bridges_space_id;

  IF v_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: Building Bridges topics still have neighborhood assignments.';
  END IF;

  SELECT COUNT(*)
    INTO v_count
  FROM es_neighborhood
  WHERE LOWER(TRIM(neighborhood_name)) = 'country interview';

  IF v_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: Country Interview neighborhood still exists after conversion.';
  END IF;

  SELECT COUNT(*)
    INTO v_count
  FROM (
    SELECT space_code
    FROM es_topic_space
    GROUP BY space_code
    HAVING COUNT(*) > 1
  ) duplicate_codes;

  IF v_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: duplicate Topic Space codes exist.';
  END IF;

  SELECT COUNT(*)
    INTO v_topic_count_after
  FROM es_topic;

  IF v_topic_count_before <> v_topic_count_after THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: total topic count changed during conversion.';
  END IF;

  DROP TEMPORARY TABLE IF EXISTS tmp_building_bridges_topic_ids;
END $$

CALL migrate_topic_spaces() $$
DROP PROCEDURE IF EXISTS migrate_topic_spaces $$

DELIMITER ;

ALTER TABLE es_topic
  MODIFY COLUMN es_topic_space_id BIGINT UNSIGNED NOT NULL;

ALTER TABLE es_neighborhood
  MODIFY COLUMN es_topic_space_id BIGINT UNSIGNED NOT NULL,
  ADD UNIQUE KEY uq_es_neighborhood_space_name (es_topic_space_id, neighborhood_name);

ALTER TABLE es_meeting
  MODIFY COLUMN es_topic_space_id BIGINT UNSIGNED NOT NULL;