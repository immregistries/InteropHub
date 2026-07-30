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

CREATE TABLE es_topic_stage_definition (
  es_topic_stage_definition_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_topic_space_id            BIGINT UNSIGNED NOT NULL,
  stage_code                   VARCHAR(80) NOT NULL,
  stage_name                   VARCHAR(120) NOT NULL,
  stage_description            TEXT NULL,
  display_order                INT NOT NULL DEFAULT 0,
  is_active                    TINYINT(1) NOT NULL DEFAULT 1,
  created_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_topic_stage_definition_id),
  UNIQUE KEY uq_es_topic_stage_space_code (es_topic_space_id, stage_code),
  UNIQUE KEY uq_es_topic_stage_space_name (es_topic_space_id, stage_name),
  KEY ix_es_topic_stage_space_active_order (es_topic_space_id, is_active, display_order, stage_name),
  CONSTRAINT fk_es_topic_stage_space FOREIGN KEY (es_topic_space_id)
    REFERENCES es_topic_space(es_topic_space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE es_topic_path_definition (
  es_topic_path_definition_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_topic_space_id           BIGINT UNSIGNED NOT NULL,
  path_code                   VARCHAR(80) NOT NULL,
  path_name                   VARCHAR(120) NOT NULL,
  path_description            TEXT NULL,
  display_order               INT NOT NULL DEFAULT 0,
  is_active                   TINYINT(1) NOT NULL DEFAULT 1,
  created_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_topic_path_definition_id),
  UNIQUE KEY uq_es_topic_path_space_code (es_topic_space_id, path_code),
  UNIQUE KEY uq_es_topic_path_space_name (es_topic_space_id, path_name),
  KEY ix_es_topic_path_space_active_order (es_topic_space_id, is_active, display_order, path_name),
  CONSTRAINT fk_es_topic_path_space FOREIGN KEY (es_topic_space_id)
    REFERENCES es_topic_space(es_topic_space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE es_topic
  ADD COLUMN es_topic_space_id BIGINT UNSIGNED NULL AFTER confluence_url,
  ADD COLUMN path VARCHAR(80) NULL AFTER stage,
  ADD COLUMN es_topic_stage_definition_id BIGINT UNSIGNED NULL AFTER path,
  ADD COLUMN es_topic_path_definition_id BIGINT UNSIGNED NULL AFTER es_topic_stage_definition_id,
  ADD KEY ix_es_topic_space_id (es_topic_space_id),
  ADD KEY ix_es_topic_stage_definition_id (es_topic_stage_definition_id),
  ADD KEY ix_es_topic_path_definition_id (es_topic_path_definition_id),
  ADD CONSTRAINT fk_es_topic_topic_space FOREIGN KEY (es_topic_space_id)
    REFERENCES es_topic_space(es_topic_space_id),
  ADD CONSTRAINT fk_es_topic_stage_definition FOREIGN KEY (es_topic_stage_definition_id)
    REFERENCES es_topic_stage_definition(es_topic_stage_definition_id),
  ADD CONSTRAINT fk_es_topic_path_definition FOREIGN KEY (es_topic_path_definition_id)
    REFERENCES es_topic_path_definition(es_topic_path_definition_id);

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

  UPDATE es_topic
  SET path = NULL,
      es_topic_stage_definition_id = NULL,
      es_topic_path_definition_id = NULL;

  UPDATE es_topic
  SET stage = NULL,
      path = NULL,
      es_topic_stage_definition_id = NULL,
      es_topic_path_definition_id = NULL
  WHERE es_topic_space_id IN (v_building_bridges_space_id, v_nursery_space_id);

  INSERT INTO es_topic_stage_definition (
    es_topic_space_id,
    stage_code,
    stage_name,
    stage_description,
    display_order,
    is_active,
    created_at,
    updated_at
  )
  VALUES
    (v_emerging_standards_space_id, 'rollout', 'Rollout', 'Rollout topics are ready for broader adoption and implementation support.', 10, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_emerging_standards_space_id, 'pilot', 'Pilot', 'Pilot topics are in trial implementations to validate feasibility and workflow impact.', 20, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_emerging_standards_space_id, 'draft', 'Draft', 'Draft topics are early-stage ideas gathering initial interest and problem framing.', 30, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_emerging_standards_space_id, 'start', 'Start', 'Start topics are beginning active development work toward practical implementation.', 40, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_emerging_standards_space_id, 'gather', 'Gather', 'Gather topics are collecting broader input from implementers and stakeholders.', 50, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_emerging_standards_space_id, 'monitor', 'Monitor', 'Monitor topics are active efforts being tracked for readiness and real-world momentum.', 60, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_emerging_standards_space_id, 'parked', 'Parked', 'Parked topics are intentionally paused while dependencies or timing constraints are addressed.', 70, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_building_bridges_space_id, 'IDENTIFIED', 'Identified', 'A country, organization, community, or relationship has been identified as potentially relevant, but little engagement or analysis has occurred.', 10, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_building_bridges_space_id, 'CONTEXT-DEVELOPING', 'Context Developing', 'Background, participants, needs, priorities, and strategic relevance are being researched and understood.', 20, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_building_bridges_space_id, 'READY-FOR-ENGAGEMENT', 'Ready for Engagement', 'Enough context and a clear purpose exist to begin, renew, or broaden substantive outreach.', 30, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_building_bridges_space_id, 'ENGAGEMENT-UNDERWAY', 'Engagement Underway', 'Outreach, interviews, meetings, or substantive conversations are actively occurring.', 40, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_building_bridges_space_id, 'RELATIONSHIP-ESTABLISHED', 'Relationship Established', 'Mutual understanding and a continuing relationship now exist, even if no specific collaboration has been selected.', 50, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_building_bridges_space_id, 'OPPORTUNITY-SHAPING', 'Opportunity Shaping', 'A specific collaboration, exchange, demonstration, proposal, or strategic opportunity is being developed.', 60, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_building_bridges_space_id, 'ACTIVE-COLLABORATION', 'Active Collaboration', 'Concrete joint work or an ongoing partnership is underway.', 70, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_nursery_space_id, 'NEW-SEEDLINGS', 'New Seedlings', 'Newly captured opportunities that have not yet been sufficiently researched, challenged, or shaped for active development.', 10, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_nursery_space_id, 'GROWING-IDEAS', 'Growing Ideas', 'Opportunities being actively researched, clarified, prototyped, connected to related work, or discussed with relevant people.', 20, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_nursery_space_id, 'READY-FOR-REVIEW', 'Ready for Review', 'Opportunities sufficiently developed for leadership discussion and accompanied by a clear question, reaction needed, or requested direction.', 30, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_nursery_space_id, 'DIRECTED-NEXT-STEPS', 'Directed Next Steps', 'Leadership or the review group has provided direction, and the next meaningful path or action has been documented.', 40, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP())
  ON DUPLICATE KEY UPDATE
    stage_name = VALUES(stage_name),
    stage_description = VALUES(stage_description),
    display_order = VALUES(display_order),
    is_active = VALUES(is_active),
    updated_at = UTC_TIMESTAMP();

  INSERT INTO es_topic_path_definition (
    es_topic_space_id,
    path_code,
    path_name,
    path_description,
    display_order,
    is_active,
    created_at,
    updated_at
  )
  VALUES
    (v_emerging_standards_space_id, 'KEEP-GROWING', 'Keep Growing', 'Continue community exploration, research, discussion, and refinement without yet assigning the topic to a more specific advancement route.', 10, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_emerging_standards_space_id, 'CULTIVATE-WITH-CDC', 'Cultivate with CDC', 'Progress depends primarily on CDC participation, policy direction, national coordination, sponsorship, funding, or another federal role.', 20, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_emerging_standards_space_id, 'ADVANCE-THROUGH-STANDARDS', 'Advance through Standards', 'Progress should occur primarily through HL7, IHE, a terminology organization, or another formal standards-development process.', 30, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_emerging_standards_space_id, 'PILOT-WITH-IMPLEMENTERS', 'Pilot with Implementers', 'Progress now depends on prototypes, demonstrations, vendor participation, jurisdiction testing, or implementation evidence.', 40, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_emerging_standards_space_id, 'MONITOR-EXTERNAL-DEVELOPMENT', 'Monitor External Development', 'The topic is relevant to IIS, but its direction is primarily controlled elsewhere. The community should monitor, interpret, and respond rather than lead.', 50, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_emerging_standards_space_id, 'PAUSE', 'Pause', 'Preserve the topic and its history, but stop active advancement until priorities, demand, policy, funding, or technology change.', 60, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_building_bridges_space_id, 'LEARN-AND-LISTEN', 'Learn and Listen', 'Use the relationship primarily to understand the country, organization, ecosystem, needs, and perspectives.', 10, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_building_bridges_space_id, 'MAINTAIN-RELATIONSHIP', 'Maintain the Relationship', 'Preserve trust and communication without actively expanding the relationship or pursuing a specific opportunity.', 20, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_building_bridges_space_id, 'DEEPEN-RELATIONSHIP', 'Deepen the Relationship', 'Invest in more sustained engagement, reciprocal exchange, and stronger mutual understanding.', 30, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_building_bridges_space_id, 'CONNECT-TO-AIRA-WORK', 'Connect to AIRA Work', 'Link the relationship to an existing AIRA initiative, community, standard, service, or area of expertise.', 40, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_building_bridges_space_id, 'DEVELOP-JOINT-OPPORTUNITY', 'Develop a Joint Opportunity', 'Shape a new collaboration, pilot, proposal, service, funding opportunity, or formal partnership.', 50, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_building_bridges_space_id, 'PAUSE', 'Pause', 'Preserve the relationship history and context, but stop active advancement until conditions change.', 60, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_nursery_space_id, 'KEEP-GROWING', 'Keep Growing', 'Continue exploration, research, prototyping, and refinement without broader organizational commitment yet.', 10, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_nursery_space_id, 'CULTIVATE-IN-AIRA', 'Cultivate inside AIRA', 'Develop the opportunity through internal discussion, sponsorship, coordination, capacity building, or incorporation into AIRA programs.', 20, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_nursery_space_id, 'CROSS-POLLINATE-EXTERNALLY', 'Cross-pollinate Externally', 'Advance the opportunity primarily through engagement with CDC, members, standards organizations, vendors, funders, international groups, or other partners.', 30, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_nursery_space_id, 'PAUSE', 'Pause', 'Preserve the opportunity and its history, but stop active advancement until capacity, priorities, funding, demand, or other conditions change.', 40, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP())
  ON DUPLICATE KEY UPDATE
    path_name = VALUES(path_name),
    path_description = VALUES(path_description),
    display_order = VALUES(display_order),
    is_active = VALUES(is_active),
    updated_at = UTC_TIMESTAMP();

  UPDATE es_topic t
  JOIN es_topic_stage_definition sd
    ON sd.es_topic_space_id = v_emerging_standards_space_id
   AND sd.stage_code = CASE
     WHEN LOWER(TRIM(t.stage)) = 'start' THEN 'start'
     WHEN LOWER(TRIM(t.stage)) = 'draft' THEN 'draft'
     WHEN LOWER(TRIM(t.stage)) = 'gather' THEN 'gather'
     WHEN LOWER(TRIM(t.stage)) = 'monitor' THEN 'monitor'
     WHEN LOWER(TRIM(t.stage)) = 'monnitor' THEN 'monitor'
     WHEN LOWER(TRIM(t.stage)) = 'parked' THEN 'parked'
     WHEN LOWER(TRIM(t.stage)) = 'pilot' THEN 'pilot'
     WHEN LOWER(TRIM(t.stage)) = 'rollout' THEN 'rollout'
     ELSE NULL
   END
  SET t.es_topic_stage_definition_id = sd.es_topic_stage_definition_id
  WHERE t.es_topic_space_id = v_emerging_standards_space_id
    AND t.stage IS NOT NULL
    AND TRIM(t.stage) <> '';

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
  FROM es_topic_stage_definition
  WHERE es_topic_space_id = v_emerging_standards_space_id;

  IF v_count <> 7 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: Emerging Standards must have exactly seven stage definitions.';
  END IF;

  SELECT COUNT(*)
    INTO v_count
  FROM es_topic_stage_definition
  WHERE es_topic_space_id = v_building_bridges_space_id;

  IF v_count <> 7 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: Building Bridges must have exactly seven stage definitions.';
  END IF;

  SELECT COUNT(*)
    INTO v_count
  FROM es_topic_stage_definition
  WHERE es_topic_space_id = v_nursery_space_id;

  IF v_count <> 4 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: AIRA Opportunity Nursery must have exactly four stage definitions.';
  END IF;

  SELECT COUNT(*)
    INTO v_count
  FROM es_topic_path_definition
  WHERE es_topic_space_id = v_emerging_standards_space_id;

  IF v_count <> 6 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: Emerging Standards must have exactly six advancement path definitions.';
  END IF;

  SELECT COUNT(*)
    INTO v_count
  FROM es_topic_path_definition
  WHERE es_topic_space_id = v_building_bridges_space_id;

  IF v_count <> 6 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: Building Bridges must have exactly six advancement path definitions.';
  END IF;

  SELECT COUNT(*)
    INTO v_count
  FROM es_topic_path_definition
  WHERE es_topic_space_id = v_nursery_space_id;

  IF v_count <> 4 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: AIRA Opportunity Nursery must have exactly four advancement path definitions.';
  END IF;

  SELECT COUNT(*)
    INTO v_count
  FROM es_topic
  WHERE es_topic_space_id = v_emerging_standards_space_id
    AND stage IS NOT NULL
    AND TRIM(stage) <> ''
    AND es_topic_stage_definition_id IS NULL;

  IF v_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: one or more Emerging Standards topics have a stage value that does not map to the preserved Emerging Standards stage definitions.';
  END IF;

  SELECT COUNT(*)
    INTO v_count
  FROM es_topic
  WHERE es_topic_space_id = v_emerging_standards_space_id
    AND ((path IS NOT NULL AND TRIM(path) <> '') OR es_topic_path_definition_id IS NOT NULL);

  IF v_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: Emerging Standards topics must begin with no advancement path assignment.';
  END IF;

  SELECT COUNT(*)
    INTO v_count
  FROM es_topic
  WHERE es_topic_space_id = v_building_bridges_space_id
    AND (((stage IS NOT NULL AND TRIM(stage) <> '') OR es_topic_stage_definition_id IS NOT NULL)
      OR ((path IS NOT NULL AND TRIM(path) <> '') OR es_topic_path_definition_id IS NOT NULL));

  IF v_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: Building Bridges topics must begin with no stage or advancement path assignment.';
  END IF;

  SELECT COUNT(*)
    INTO v_count
  FROM es_topic
  WHERE es_topic_space_id = v_nursery_space_id
    AND (((stage IS NOT NULL AND TRIM(stage) <> '') OR es_topic_stage_definition_id IS NOT NULL)
      OR ((path IS NOT NULL AND TRIM(path) <> '') OR es_topic_path_definition_id IS NOT NULL));

  IF v_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Topic Space conversion failed: AIRA Opportunity Nursery topics must begin with no stage or advancement path assignment.';
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

CREATE TABLE es_topic_board_definition (
  es_topic_board_definition_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  board_code                   VARCHAR(80) NOT NULL,
  board_name                   VARCHAR(140) NOT NULL,
  board_description            TEXT NULL,
  es_topic_space_id            BIGINT UNSIGNED NOT NULL,
  curator_topic_id             BIGINT NULL,
  show_unassigned_stage        TINYINT(1) NOT NULL DEFAULT 0,
  show_unassigned_path         TINYINT(1) NOT NULL DEFAULT 0,
  is_active                    TINYINT(1) NOT NULL DEFAULT 1,
  created_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                               ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_topic_board_definition_id),
  UNIQUE KEY uq_es_topic_board_code (board_code),
  KEY ix_es_topic_board_space (es_topic_space_id),
  KEY ix_es_topic_board_curator (curator_topic_id),
  CONSTRAINT fk_es_topic_board_space
    FOREIGN KEY (es_topic_space_id)
    REFERENCES es_topic_space(es_topic_space_id),
  CONSTRAINT fk_es_topic_board_curator
    FOREIGN KEY (curator_topic_id)
    REFERENCES es_topic(es_topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE es_topic_board_stage (
  es_topic_board_stage_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_topic_board_definition_id BIGINT UNSIGNED NOT NULL,
  es_topic_stage_definition_id BIGINT UNSIGNED NOT NULL,
  display_order                INT NOT NULL DEFAULT 0,
  PRIMARY KEY (es_topic_board_stage_id),
  UNIQUE KEY uq_es_topic_board_stage (
    es_topic_board_definition_id,
    es_topic_stage_definition_id
  ),
  KEY ix_es_topic_board_stage_order (
    es_topic_board_definition_id,
    display_order
  ),
  CONSTRAINT fk_es_topic_board_stage_board
    FOREIGN KEY (es_topic_board_definition_id)
    REFERENCES es_topic_board_definition(es_topic_board_definition_id),
  CONSTRAINT fk_es_topic_board_stage_definition
    FOREIGN KEY (es_topic_stage_definition_id)
    REFERENCES es_topic_stage_definition(es_topic_stage_definition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE es_topic_board_path (
  es_topic_board_path_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_topic_board_definition_id BIGINT UNSIGNED NOT NULL,
  es_topic_path_definition_id  BIGINT UNSIGNED NOT NULL,
  display_order                INT NOT NULL DEFAULT 0,
  PRIMARY KEY (es_topic_board_path_id),
  UNIQUE KEY uq_es_topic_board_path (
    es_topic_board_definition_id,
    es_topic_path_definition_id
  ),
  KEY ix_es_topic_board_path_order (
    es_topic_board_definition_id,
    display_order
  ),
  CONSTRAINT fk_es_topic_board_path_board
    FOREIGN KEY (es_topic_board_definition_id)
    REFERENCES es_topic_board_definition(es_topic_board_definition_id),
  CONSTRAINT fk_es_topic_board_path_definition
    FOREIGN KEY (es_topic_path_definition_id)
    REFERENCES es_topic_path_definition(es_topic_path_definition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO es_topic_board_definition (
  board_code,
  board_name,
  board_description,
  es_topic_space_id,
  curator_topic_id,
  show_unassigned_stage,
  show_unassigned_path,
  is_active,
  created_at,
  updated_at
)
VALUES
  ('emerging-standards', 'Emerging Standards', NULL, 1, NULL, 0, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
  ('ifg-topics', 'Immunization Focus Group Topics', NULL, 1, 74, 0, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
  ('aira-opportunity-nursery', 'AIRA Opportunity Nursery', NULL, 3, NULL, 1, 1, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP())
ON DUPLICATE KEY UPDATE
  board_name = VALUES(board_name),
  board_description = VALUES(board_description),
  es_topic_space_id = VALUES(es_topic_space_id),
  curator_topic_id = VALUES(curator_topic_id),
  show_unassigned_stage = VALUES(show_unassigned_stage),
  show_unassigned_path = VALUES(show_unassigned_path),
  is_active = VALUES(is_active),
  updated_at = UTC_TIMESTAMP();

INSERT INTO es_topic_board_stage (
  es_topic_board_definition_id,
  es_topic_stage_definition_id,
  display_order
)
SELECT
  b.es_topic_board_definition_id,
  sd.es_topic_stage_definition_id,
  CASE sd.stage_code
    WHEN 'monitor' THEN 10
    WHEN 'gather' THEN 20
    WHEN 'start' THEN 30
    WHEN 'draft' THEN 40
  END AS board_display_order
FROM es_topic_board_definition b
JOIN es_topic_stage_definition sd
  ON sd.es_topic_space_id = 1
 AND sd.is_active = 1
 AND sd.stage_code IN ('monitor', 'gather', 'start', 'draft')
WHERE b.board_code = 'emerging-standards'
ON DUPLICATE KEY UPDATE
  display_order = VALUES(display_order);

INSERT INTO es_topic_board_stage (
  es_topic_board_definition_id,
  es_topic_stage_definition_id,
  display_order
)
SELECT
  b.es_topic_board_definition_id,
  sd.es_topic_stage_definition_id,
  CASE sd.stage_code
    WHEN 'monitor' THEN 10
    WHEN 'gather' THEN 20
    WHEN 'start' THEN 30
    WHEN 'draft' THEN 40
  END AS board_display_order
FROM es_topic_board_definition b
JOIN es_topic_stage_definition sd
  ON sd.es_topic_space_id = 1
 AND sd.is_active = 1
 AND sd.stage_code IN ('monitor', 'gather', 'start', 'draft')
WHERE b.board_code = 'ifg-topics'
ON DUPLICATE KEY UPDATE
  display_order = VALUES(display_order);

INSERT INTO es_topic_board_stage (
  es_topic_board_definition_id,
  es_topic_stage_definition_id,
  display_order
)
SELECT
  b.es_topic_board_definition_id,
  sd.es_topic_stage_definition_id,
  sd.display_order
FROM es_topic_board_definition b
JOIN es_topic_stage_definition sd
  ON sd.es_topic_space_id = 3
 AND sd.is_active = 1
WHERE b.board_code = 'aira-opportunity-nursery'
ORDER BY sd.display_order, sd.stage_name
ON DUPLICATE KEY UPDATE
  display_order = VALUES(display_order);

INSERT INTO es_topic_board_path (
  es_topic_board_definition_id,
  es_topic_path_definition_id,
  display_order
)
SELECT
  b.es_topic_board_definition_id,
  pd.es_topic_path_definition_id,
  pd.display_order
FROM es_topic_board_definition b
JOIN es_topic_path_definition pd
  ON pd.es_topic_space_id = 1
 AND pd.is_active = 1
WHERE b.board_code = 'emerging-standards'
ORDER BY pd.display_order, pd.path_name
ON DUPLICATE KEY UPDATE
  display_order = VALUES(display_order);

INSERT INTO es_topic_board_path (
  es_topic_board_definition_id,
  es_topic_path_definition_id,
  display_order
)
SELECT
  b.es_topic_board_definition_id,
  pd.es_topic_path_definition_id,
  pd.display_order
FROM es_topic_board_definition b
JOIN es_topic_path_definition pd
  ON pd.es_topic_space_id = 1
 AND pd.is_active = 1
WHERE b.board_code = 'ifg-topics'
ORDER BY pd.display_order, pd.path_name
ON DUPLICATE KEY UPDATE
  display_order = VALUES(display_order);

INSERT INTO es_topic_board_path (
  es_topic_board_definition_id,
  es_topic_path_definition_id,
  display_order
)
SELECT
  b.es_topic_board_definition_id,
  pd.es_topic_path_definition_id,
  pd.display_order
FROM es_topic_board_definition b
JOIN es_topic_path_definition pd
  ON pd.es_topic_space_id = 3
 AND pd.is_active = 1
WHERE b.board_code = 'aira-opportunity-nursery'
ORDER BY pd.display_order, pd.path_name
ON DUPLICATE KEY UPDATE
  display_order = VALUES(display_order);

-- Meeting lifecycle, notes, outcomes, and live voting foundation.
ALTER TABLE es_meeting
  MODIFY COLUMN status ENUM('CANCELLED','COMPLETED','CLOSED','DRAFT','FINALIZED','IN_SESSION','PROPOSED') NOT NULL,
  ADD COLUMN designated_chair_user_id BIGINT DEFAULT NULL AFTER status,
  ADD COLUMN designated_scribe_user_id BIGINT DEFAULT NULL AFTER designated_chair_user_id,
  ADD COLUMN current_chair_user_id BIGINT DEFAULT NULL AFTER designated_scribe_user_id,
  ADD COLUMN current_scribe_user_id BIGINT DEFAULT NULL AFTER current_chair_user_id,
  ADD COLUMN current_agenda_item_id BIGINT DEFAULT NULL AFTER current_scribe_user_id,
  ADD COLUMN started_at DATETIME(6) DEFAULT NULL AFTER current_agenda_item_id,
  ADD COLUMN started_by_user_id BIGINT DEFAULT NULL AFTER started_at,
  ADD COLUMN completed_by_user_id BIGINT DEFAULT NULL AFTER started_by_user_id,
  ADD COLUMN close_due_at DATETIME(6) DEFAULT NULL AFTER completed_by_user_id,
  ADD COLUMN closed_at DATETIME(6) DEFAULT NULL AFTER close_due_at,
  ADD COLUMN closed_by_user_id BIGINT DEFAULT NULL AFTER closed_at,
  ADD COLUMN close_method ENUM('AUTOMATIC','MANUAL') DEFAULT NULL AFTER closed_by_user_id,
  ADD KEY ix_es_meeting_status_close_due (status, close_due_at, es_meeting_id),
  ADD KEY ix_es_meeting_current_agenda (current_agenda_item_id);

UPDATE es_meeting
SET close_due_at = DATE_ADD(completed_at, INTERVAL 7 DAY),
    close_method = 'MANUAL'
WHERE status = 'COMPLETED'
  AND completed_at IS NOT NULL
  AND close_due_at IS NULL;

ALTER TABLE es_meeting_communication
  MODIFY COLUMN expected_meeting_status ENUM('CANCELLED','COMPLETED','CLOSED','DRAFT','FINALIZED','IN_SESSION','PROPOSED') DEFAULT NULL;

CREATE TABLE es_meeting_status_history (
  es_meeting_status_history_id BIGINT NOT NULL AUTO_INCREMENT,
  es_meeting_id BIGINT NOT NULL,
  from_status ENUM('CANCELLED','COMPLETED','CLOSED','DRAFT','FINALIZED','IN_SESSION','PROPOSED') DEFAULT NULL,
  to_status ENUM('CANCELLED','COMPLETED','CLOSED','DRAFT','FINALIZED','IN_SESSION','PROPOSED') NOT NULL,
  changed_at DATETIME(6) NOT NULL,
  changed_by_user_id BIGINT DEFAULT NULL,
  transition_method ENUM('AUTOMATIC','USER') NOT NULL,
  PRIMARY KEY (es_meeting_status_history_id),
  KEY ix_es_msh_meeting_changed (es_meeting_id, changed_at, es_meeting_status_history_id),
  KEY ix_es_msh_to_status (to_status, changed_at),
  CONSTRAINT fk_es_msh_meeting FOREIGN KEY (es_meeting_id) REFERENCES es_meeting (es_meeting_id),
  CONSTRAINT fk_es_msh_changed_by FOREIGN KEY (changed_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_meeting_role_assignment (
  es_meeting_role_assignment_id BIGINT NOT NULL AUTO_INCREMENT,
  es_meeting_id BIGINT NOT NULL,
  role_type ENUM('CHAIR','SCRIBE') NOT NULL,
  user_id BIGINT NOT NULL,
  started_at DATETIME(6) NOT NULL,
  ended_at DATETIME(6) DEFAULT NULL,
  assigned_by_user_id BIGINT NOT NULL,
  PRIMARY KEY (es_meeting_role_assignment_id),
  KEY ix_es_mra_meeting_role_open (es_meeting_id, role_type, ended_at, started_at),
  KEY ix_es_mra_user (user_id, started_at),
  CONSTRAINT fk_es_mra_meeting FOREIGN KEY (es_meeting_id) REFERENCES es_meeting (es_meeting_id),
  CONSTRAINT fk_es_mra_user FOREIGN KEY (user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_mra_assigned_by FOREIGN KEY (assigned_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_meeting_agenda_activity (
  es_meeting_agenda_activity_id BIGINT NOT NULL AUTO_INCREMENT,
  es_meeting_id BIGINT NOT NULL,
  es_meeting_agenda_item_id BIGINT NOT NULL,
  es_topic_id BIGINT NOT NULL,
  started_at DATETIME(6) NOT NULL,
  started_by_user_id BIGINT NOT NULL,
  ended_at DATETIME(6) DEFAULT NULL,
  ended_by_user_id BIGINT DEFAULT NULL,
  PRIMARY KEY (es_meeting_agenda_activity_id),
  KEY ix_es_maa_meeting_open (es_meeting_id, ended_at, started_at),
  KEY ix_es_maa_agenda_item (es_meeting_agenda_item_id, started_at),
  KEY ix_es_maa_topic (es_topic_id, started_at),
  CONSTRAINT fk_es_maa_meeting FOREIGN KEY (es_meeting_id) REFERENCES es_meeting (es_meeting_id),
  CONSTRAINT fk_es_maa_agenda_item FOREIGN KEY (es_meeting_agenda_item_id) REFERENCES es_meeting_agenda_item (es_meeting_agenda_item_id),
  CONSTRAINT fk_es_maa_topic FOREIGN KEY (es_topic_id) REFERENCES es_topic (es_topic_id),
  CONSTRAINT fk_es_maa_started_by FOREIGN KEY (started_by_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_maa_ended_by FOREIGN KEY (ended_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_meeting_participant_count (
  es_meeting_participant_count_id BIGINT NOT NULL AUTO_INCREMENT,
  es_meeting_agenda_activity_id BIGINT NOT NULL,
  participant_count INT NOT NULL,
  recorded_at DATETIME(6) NOT NULL,
  recorded_by_user_id BIGINT NOT NULL,
  PRIMARY KEY (es_meeting_participant_count_id),
  KEY ix_es_mpc_activity_recorded (es_meeting_agenda_activity_id, recorded_at, es_meeting_participant_count_id),
  CONSTRAINT fk_es_mpc_activity FOREIGN KEY (es_meeting_agenda_activity_id) REFERENCES es_meeting_agenda_activity (es_meeting_agenda_activity_id),
  CONSTRAINT fk_es_mpc_recorded_by FOREIGN KEY (recorded_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_topic_note (
  es_topic_note_id BIGINT NOT NULL AUTO_INCREMENT,
  es_topic_id BIGINT NOT NULL,
  es_meeting_id BIGINT DEFAULT NULL,
  es_meeting_agenda_item_id BIGINT DEFAULT NULL,
  note_title VARCHAR(200) DEFAULT NULL,
  document_json JSON NOT NULL,
  document_text LONGTEXT,
  revision_no BIGINT NOT NULL,
  status ENUM('FINALIZED','OPEN') NOT NULL,
  active_editor_user_id BIGINT DEFAULT NULL,
  active_editor_started_at DATETIME(6) DEFAULT NULL,
  active_editor_version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  finalize_at DATETIME(6) DEFAULT NULL,
  finalized_at DATETIME(6) DEFAULT NULL,
  finalized_by_user_id BIGINT DEFAULT NULL,
  finalization_method ENUM('AUTOMATIC','MANUAL') DEFAULT NULL,
  PRIMARY KEY (es_topic_note_id),
  KEY ix_es_topic_note_topic_created (es_topic_id, created_at, es_topic_note_id),
  KEY ix_es_topic_note_meeting_status (es_meeting_id, status, finalize_at, es_topic_note_id),
  KEY ix_es_topic_note_agenda_status (es_meeting_agenda_item_id, status, es_topic_note_id),
  KEY ix_es_topic_note_finalize_at (finalize_at, es_topic_note_id),
  CONSTRAINT fk_es_topic_note_topic FOREIGN KEY (es_topic_id) REFERENCES es_topic (es_topic_id),
  CONSTRAINT fk_es_topic_note_meeting FOREIGN KEY (es_meeting_id) REFERENCES es_meeting (es_meeting_id),
  CONSTRAINT fk_es_topic_note_agenda_item FOREIGN KEY (es_meeting_agenda_item_id) REFERENCES es_meeting_agenda_item (es_meeting_agenda_item_id),
  CONSTRAINT fk_es_topic_note_created_by FOREIGN KEY (created_by_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_topic_note_finalized_by FOREIGN KEY (finalized_by_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_topic_note_active_editor FOREIGN KEY (active_editor_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_topic_note_revision (
  es_topic_note_revision_id BIGINT NOT NULL AUTO_INCREMENT,
  es_topic_note_id BIGINT NOT NULL,
  revision_no BIGINT NOT NULL,
  document_json JSON NOT NULL,
  document_text LONGTEXT,
  saved_at DATETIME(6) NOT NULL,
  saved_by_user_id BIGINT NOT NULL,
  PRIMARY KEY (es_topic_note_revision_id),
  UNIQUE KEY uq_es_tnr_note_revision (es_topic_note_id, revision_no),
  KEY ix_es_tnr_note_saved (es_topic_note_id, revision_no, es_topic_note_revision_id),
  CONSTRAINT fk_es_tnr_note FOREIGN KEY (es_topic_note_id) REFERENCES es_topic_note (es_topic_note_id),
  CONSTRAINT fk_es_tnr_saved_by FOREIGN KEY (saved_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_topic_note_editor_history (
  es_topic_note_editor_history_id BIGINT NOT NULL AUTO_INCREMENT,
  es_topic_note_id BIGINT NOT NULL,
  previous_editor_user_id BIGINT DEFAULT NULL,
  new_editor_user_id BIGINT NOT NULL,
  changed_at DATETIME(6) NOT NULL,
  changed_by_user_id BIGINT NOT NULL,
  PRIMARY KEY (es_topic_note_editor_history_id),
  KEY ix_es_tneh_note_changed (es_topic_note_id, changed_at, es_topic_note_editor_history_id),
  CONSTRAINT fk_es_tneh_note FOREIGN KEY (es_topic_note_id) REFERENCES es_topic_note (es_topic_note_id),
  CONSTRAINT fk_es_tneh_previous_editor FOREIGN KEY (previous_editor_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_tneh_new_editor FOREIGN KEY (new_editor_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_tneh_changed_by FOREIGN KEY (changed_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_recorded_outcome (
  es_recorded_outcome_id BIGINT NOT NULL AUTO_INCREMENT,
  es_topic_note_id BIGINT NOT NULL,
  source_node_id VARCHAR(128) NOT NULL,
  outcome_type ENUM('ACTION','DIRECTION','FORMAL_MOTION','OPEN_ISSUE','RATIONALE','WORKING_CONSENSUS') NOT NULL,
  short_title VARCHAR(200) DEFAULT NULL,
  outcome_text TEXT NOT NULL,
  display_order INT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  updated_by_user_id BIGINT NOT NULL,
  PRIMARY KEY (es_recorded_outcome_id),
  UNIQUE KEY uq_es_ro_note_source (es_topic_note_id, source_node_id),
  KEY ix_es_ro_note_order (es_topic_note_id, display_order, es_recorded_outcome_id),
  CONSTRAINT fk_es_ro_note FOREIGN KEY (es_topic_note_id) REFERENCES es_topic_note (es_topic_note_id),
  CONSTRAINT fk_es_ro_created_by FOREIGN KEY (created_by_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_ro_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_live_vote (
  es_live_vote_id BIGINT NOT NULL AUTO_INCREMENT,
  es_recorded_outcome_id BIGINT NOT NULL,
  status ENUM('CLOSED','OPEN','PREPARED') NOT NULL,
  motion_text TEXT NOT NULL,
  moved_by_user_id BIGINT DEFAULT NULL,
  moved_by_name VARCHAR(160) DEFAULT NULL,
  seconded_by_user_id BIGINT DEFAULT NULL,
  seconded_by_name VARCHAR(160) DEFAULT NULL,
  presiding_chair_user_id BIGINT NOT NULL,
  opened_at DATETIME(6) DEFAULT NULL,
  opened_by_user_id BIGINT DEFAULT NULL,
  closed_at DATETIME(6) DEFAULT NULL,
  closed_by_user_id BIGINT DEFAULT NULL,
  participant_count_observation_id BIGINT DEFAULT NULL,
  call_participant_count INT DEFAULT NULL,
  expected_voter_count INT DEFAULT NULL,
  electronic_for_count INT NOT NULL DEFAULT 0,
  electronic_against_count INT NOT NULL DEFAULT 0,
  electronic_abstain_count INT NOT NULL DEFAULT 0,
  manual_for_count INT NOT NULL DEFAULT 0,
  manual_against_count INT NOT NULL DEFAULT 0,
  manual_abstain_count INT NOT NULL DEFAULT 0,
  final_for_count INT DEFAULT NULL,
  final_against_count INT DEFAULT NULL,
  final_abstain_count INT DEFAULT NULL,
  result ENUM('APPROVED','FAILED','NO_RESULT','WITHDRAWN') DEFAULT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  updated_by_user_id BIGINT NOT NULL,
  PRIMARY KEY (es_live_vote_id),
  UNIQUE KEY uq_es_live_vote_outcome (es_recorded_outcome_id),
  KEY ix_es_live_vote_status_created (status, created_at, es_live_vote_id),
  KEY ix_es_live_vote_result_created (result, created_at),
  CONSTRAINT fk_es_lv_outcome FOREIGN KEY (es_recorded_outcome_id) REFERENCES es_recorded_outcome (es_recorded_outcome_id),
  CONSTRAINT fk_es_lv_moved_by FOREIGN KEY (moved_by_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_lv_seconded_by FOREIGN KEY (seconded_by_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_lv_presiding_chair FOREIGN KEY (presiding_chair_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_lv_opened_by FOREIGN KEY (opened_by_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_lv_closed_by FOREIGN KEY (closed_by_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_lv_participant_count FOREIGN KEY (participant_count_observation_id) REFERENCES es_meeting_participant_count (es_meeting_participant_count_id),
  CONSTRAINT fk_es_lv_created_by FOREIGN KEY (created_by_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_lv_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_live_vote_response (
  es_live_vote_response_id BIGINT NOT NULL AUTO_INCREMENT,
  es_live_vote_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  response ENUM('ABSTAIN','AGAINST','FOR') NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (es_live_vote_response_id),
  UNIQUE KEY uq_es_lvr_vote_user (es_live_vote_id, user_id),
  KEY ix_es_lvr_vote_response (es_live_vote_id, response, es_live_vote_response_id),
  CONSTRAINT fk_es_lvr_vote FOREIGN KEY (es_live_vote_id) REFERENCES es_live_vote (es_live_vote_id),
  CONSTRAINT fk_es_lvr_user FOREIGN KEY (user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_topic_meeting_cochair (
  es_topic_meeting_cochair_id BIGINT NOT NULL AUTO_INCREMENT,
  es_topic_meeting_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  status ENUM('ACTIVE','INACTIVE') NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  inactive_at DATETIME(6) DEFAULT NULL,
  inactive_by_user_id BIGINT DEFAULT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (es_topic_meeting_cochair_id),
  KEY ix_es_tm_cchair_meeting_status (es_topic_meeting_id, status, created_at),
  KEY ix_es_tm_cchair_user (user_id, created_at),
  CONSTRAINT fk_es_tm_cchair_meeting FOREIGN KEY (es_topic_meeting_id) REFERENCES es_topic_meeting (es_topic_meeting_id),
  CONSTRAINT fk_es_tm_cchair_user FOREIGN KEY (user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_tm_cchair_created_by FOREIGN KEY (created_by_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_tm_cchair_inactive_by FOREIGN KEY (inactive_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE es_topic_user_view (
  es_topic_user_view_id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  es_topic_id BIGINT NOT NULL,
  first_viewed_at DATETIME(6) NOT NULL,
  last_viewed_at DATETIME(6) NOT NULL,
  visit_count BIGINT UNSIGNED NOT NULL DEFAULT 1,
  last_counted_at DATETIME(6) NOT NULL,
  PRIMARY KEY (es_topic_user_view_id),
  UNIQUE KEY uq_es_topic_user_view_user_topic (user_id, es_topic_id),
  KEY ix_es_topic_user_view_user_recent (user_id, last_viewed_at),
  KEY ix_es_topic_user_view_topic_recent (es_topic_id, last_viewed_at),
  CONSTRAINT fk_es_topic_user_view_user FOREIGN KEY (user_id) REFERENCES auth_user (user_id),
  CONSTRAINT fk_es_topic_user_view_topic FOREIGN KEY (es_topic_id) REFERENCES es_topic (es_topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


ALTER TABLE es_topic
  ADD COLUMN topic_summary varchar(300) NULL AFTER description,
  ADD COLUMN topic_emoji varchar(64) NULL AFTER topic_summary;


SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

START TRANSACTION;

-- 1: Immunization CDS (ImmDS + HALO)
UPDATE es_topic
SET topic_summary = 'Defines FHIR-based interfaces for requesting and returning immunization clinical decision support (CDS) evaluations and recommendations, including contextual conditions.',
    topic_emoji = '🧠'
WHERE es_topic_id = 1;

-- 2: Consumer Access to Records
UPDATE es_topic
SET topic_summary = 'Enables individuals to securely retrieve their immunization records from an immunization information system (IIS) through portals, APIs, or digital credentials.',
    topic_emoji = '📱'
WHERE es_topic_id = 2;

-- 3: Data Quality Notifications
UPDATE es_topic
SET topic_summary = 'Provides structured notifications to submitters when immunization data quality problems are detected during processing or later review.',
    topic_emoji = '⚠️'
WHERE es_topic_id = 3;

-- 4: Vaccine Lot Validation
UPDATE es_topic
SET topic_summary = 'Validates vaccine lot numbers against reference data to improve the accuracy and safety of reported immunizations.',
    topic_emoji = '🔍'
WHERE es_topic_id = 4;

-- 5: Consumer Identity & Access
UPDATE es_topic
SET topic_summary = 'Addresses identity verification, authentication, and secure access for consumers retrieving their immunization records.',
    topic_emoji = '🔐'
WHERE es_topic_id = 5;

-- 6: Flat File Data Import
UPDATE es_topic
SET topic_summary = 'Supports importing bulk or legacy immunization data into an immunization information system (IIS) through structured flat files.',
    topic_emoji = '📥'
WHERE es_topic_id = 6;

-- 7: CDS Contextual Conditions
UPDATE es_topic
SET topic_summary = 'Represents medical, occupational, and other patient conditions that affect immunization clinical decision support (CDS) recommendations.',
    topic_emoji = '🩺'
WHERE es_topic_id = 7;

-- 8: TEFCA (National Exchange Framework)
UPDATE es_topic
SET topic_summary = 'Examines how the Trusted Exchange Framework and Common Agreement (TEFCA) may affect immunization information system participation in nationwide health information exchange.',
    topic_emoji = '🌐'
WHERE es_topic_id = 8;

-- 9: Immunization Record Matching
UPDATE es_topic
SET topic_summary = 'Addresses matching and deduplicating vaccination events within an immunization information system, separately from matching patient identities.',
    topic_emoji = '🧩'
WHERE es_topic_id = 9;

-- 10: Population Analytics
UPDATE es_topic
SET topic_summary = 'Uses immunization data for population-level public health analysis, reporting, surveillance, and decision-making.',
    topic_emoji = '📊'
WHERE es_topic_id = 10;

-- 11: Data Provenance Tracking
UPDATE es_topic
SET topic_summary = 'Captures the source and processing history of immunization data to support trust, auditing, and data quality.',
    topic_emoji = '🧾'
WHERE es_topic_id = 11;

-- 12: Vaccine Ordering Integration
UPDATE es_topic
SET topic_summary = 'Explores integrating vaccine ordering workflows among clinical systems, immunization information systems, and vaccine supply systems.',
    topic_emoji = '📦'
WHERE es_topic_id = 12;

-- 13: Immunization Decision Support (ImmDS)
UPDATE es_topic
SET topic_summary = 'Defines standardized interfaces for requesting immunization evaluations and recommendations from clinical decision support (CDS) engines.',
    topic_emoji = '💡'
WHERE es_topic_id = 13;

-- 14: Event-Based Exchange (FHIR Subscriptions)
UPDATE es_topic
SET topic_summary = 'Uses FHIR Subscriptions to notify systems when relevant immunization data changes instead of requiring repeated polling.',
    topic_emoji = '🔔'
WHERE es_topic_id = 14;

-- 15: Received Code Validation
UPDATE es_topic
SET topic_summary = 'Validates and interprets incoming immunization codes, including vaccine and manufacturer codes, before they are accepted by an immunization information system.',
    topic_emoji = '✅'
WHERE es_topic_id = 15;

-- 16: Vaccine Barcode Scanning
UPDATE es_topic
SET topic_summary = 'Uses barcode scanning at the point of care to capture accurate vaccine product, lot, and expiration information.',
    topic_emoji = '📷'
WHERE es_topic_id = 16;

-- 17: Consumer Record Corrections
UPDATE es_topic
SET topic_summary = 'Enables authenticated consumers to request corrections to inaccurate or incomplete immunization records and track their resolution.',
    topic_emoji = '✏️'
WHERE es_topic_id = 17;

-- 18: Reminder/Recall Services
UPDATE es_topic
SET topic_summary = 'Uses immunization information system data to identify and contact patients who are due or overdue for vaccination.',
    topic_emoji = '⏰'
WHERE es_topic_id = 18;

-- 19: Digital Vaccine Cards (SMART Health Cards)
UPDATE es_topic
SET topic_summary = 'Issues verifiable digital vaccination credentials through SMART Health Cards using FHIR data and QR codes.',
    topic_emoji = '🪪'
WHERE es_topic_id = 19;

-- 20: CDS Shared Decision Support (SCDM)
UPDATE es_topic
SET topic_summary = 'Represents recommendations that require shared clinical decision-making rather than routine application of the immunization schedule.',
    topic_emoji = '🤝'
WHERE es_topic_id = 20;

-- 21: Data Validation Services
UPDATE es_topic
SET topic_summary = 'Provides reusable services for validating data elements such as names, contact information, codes, and vaccine lot numbers.',
    topic_emoji = '🧪'
WHERE es_topic_id = 21;

-- 22: Patient Updates (ADT)
UPDATE es_topic
SET topic_summary = 'Uses HL7 version 2 Admission, Discharge, and Transfer (ADT) messages to keep patient demographics in immunization information systems current.',
    topic_emoji = '🔄'
WHERE es_topic_id = 22;

-- 23: Data Merge Visibility
UPDATE es_topic
SET topic_summary = 'Shows submitters how an immunization information system matched, merged, accepted, updated, or ignored submitted patient and vaccination data.',
    topic_emoji = '🔎'
WHERE es_topic_id = 23;

-- 24: Provider Enrollment Integration
UPDATE es_topic
SET topic_summary = 'Automates provider enrollment and onboarding into immunization information systems through standardized electronic interfaces.',
    topic_emoji = '📝'
WHERE es_topic_id = 24;

-- 25: CDS Schedule Source
UPDATE es_topic
SET topic_summary = 'Identifies the schedule authority responsible for an immunization evaluation or recommendation, such as the Advisory Committee on Immunization Practices.',
    topic_emoji = '📚'
WHERE es_topic_id = 25;

-- 26: CDC WSDL Authentication
UPDATE es_topic
SET topic_summary = 'Modernizes authentication for the Centers for Disease Control and Prevention web-service interface used for HL7 version 2 exchange.',
    topic_emoji = '🔑'
WHERE es_topic_id = 26;

-- 27: Digital Record Provenance
UPDATE es_topic
SET topic_summary = 'Associates historical immunizations with verifiable original digital records that document their source and authenticity.',
    topic_emoji = '📜'
WHERE es_topic_id = 27;

-- 28: IIS Terminology Services
UPDATE es_topic
SET topic_summary = 'Publishes standard and local terminology supported by an immunization information system for validation and interoperability.',
    topic_emoji = '🏷️'
WHERE es_topic_id = 28;

-- 29: Inventory Synchronization
UPDATE es_topic
SET topic_summary = 'Keeps vaccine inventory in clinical and immunization information systems synchronized through bidirectional exchange.',
    topic_emoji = '🔄'
WHERE es_topic_id = 29;

-- 30: Record Synchronization
UPDATE es_topic
SET topic_summary = 'Maintains alignment of patient and immunization records between an immunization information system and external systems over time.',
    topic_emoji = '🔁'
WHERE es_topic_id = 30;

-- 31: Immunization Vocabularies Collaboration (IVC)
UPDATE es_topic
SET topic_summary = 'Coordinates international vaccine terminology, mappings, and code-system maintenance through the Immunization Vocabularies Collaboration (IVC).',
    topic_emoji = '🌍'
WHERE es_topic_id = 31;

-- 32: International Patient Summary (IPS)
UPDATE es_topic
SET topic_summary = 'Uses the FHIR International Patient Summary (IPS) to share a concise patient health record, including immunizations, across systems and countries.',
    topic_emoji = '🌐'
WHERE es_topic_id = 32;

-- 33: Bulk Data Exchange (FHIR Bulk Data)
UPDATE es_topic
SET topic_summary = 'Uses the FHIR Bulk Data specification to retrieve large populations of immunization records for analytics and data sharing.',
    topic_emoji = '📤'
WHERE es_topic_id = 33;

-- 34: Vaccine Inventory Management
UPDATE es_topic
SET topic_summary = 'Tracks vaccine quantities, lots, ordering, distribution, and usage within immunization programs and connected systems.',
    topic_emoji = '📦'
WHERE es_topic_id = 34;

-- 35: Data Quality Reporting
UPDATE es_topic
SET topic_summary = 'Provides submitters with periodic reports and trends describing the quality, completeness, and validity of their immunization data.',
    topic_emoji = '📈'
WHERE es_topic_id = 35;

-- 36: Vaccine Exemptions and Deferrals
UPDATE es_topic
SET topic_summary = 'Represents vaccine exemptions and temporary or permanent deferrals that affect immunization requirements and clinical decisions.',
    topic_emoji = '⏸️'
WHERE es_topic_id = 36;

-- 37: School Data Exchange
UPDATE es_topic
SET topic_summary = 'Exchanges student immunization information between immunization information systems and schools for compliance and public health workflows.',
    topic_emoji = '🏫'
WHERE es_topic_id = 37;

-- 38: Newborn Identity Handling
UPDATE es_topic
SET topic_summary = 'Handles temporary names, changing identifiers, and other identity challenges when matching newborn records.',
    topic_emoji = '👶'
WHERE es_topic_id = 38;

-- 39: Inventory Reconciliation
UPDATE es_topic
SET topic_summary = 'Compares and corrects vaccine inventory balances between provider systems and immunization information systems.',
    topic_emoji = '⚖️'
WHERE es_topic_id = 39;

-- 40: Official Record Documents (PDF)
UPDATE es_topic
SET topic_summary = 'Generates official, portable immunization record documents in Portable Document Format (PDF) for patients and providers.',
    topic_emoji = '📄'
WHERE es_topic_id = 40;

-- 41: Patient Record Matching Integration
UPDATE es_topic
SET topic_summary = 'Standardizes how immunization information systems integrate patient-matching and identity-resolution services.',
    topic_emoji = '🧩'
WHERE es_topic_id = 41;

-- 42: Pharmacy Integration
UPDATE es_topic
SET topic_summary = 'Supports vaccine reporting and bidirectional immunization exchange between pharmacies and immunization information systems.',
    topic_emoji = '💊'
WHERE es_topic_id = 42;

-- 43: Digital Vaccine Cards (SMART Health Links)
UPDATE es_topic
SET topic_summary = 'Uses SMART Health Links to provide individuals with shareable, updateable access to verifiable digital vaccination records.',
    topic_emoji = '🔗'
WHERE es_topic_id = 43;

-- 44: ACK Error Reporting (ERR-5)
UPDATE es_topic
SET topic_summary = 'Standardizes detailed error codes in field ERR-5 of HL7 version 2 acknowledgement messages to give submitters clearer feedback.',
    topic_emoji = '🚨'
WHERE es_topic_id = 44;

-- 45: Acknowledgements (ACK)
UPDATE es_topic
SET topic_summary = 'Defines how HL7 version 2 acknowledgement (ACK) messages communicate whether immunization submissions succeeded, produced warnings, or failed.',
    topic_emoji = '📬'
WHERE es_topic_id = 45;

-- 46: Adverse Event Reporting (VAERS)
UPDATE es_topic
SET topic_summary = 'Examines whether and how immunization information systems should support reporting vaccine adverse events to the Vaccine Adverse Event Reporting System (VAERS).',
    topic_emoji = '🛡️'
WHERE es_topic_id = 46;

-- 47: Aggregate Data Exchange (IHE)
UPDATE es_topic
SET topic_summary = 'Uses Integrating the Healthcare Enterprise (IHE) profiles to exchange aggregate or population-level immunization data.',
    topic_emoji = '📊'
WHERE es_topic_id = 47;

-- 48: Antiviral Tracking
UPDATE es_topic
SET topic_summary = 'Explores whether immunization information systems should capture and exchange information about antiviral medications.',
    topic_emoji = '💊'
WHERE es_topic_id = 48;

-- 49: Appointment Scheduling Integration
UPDATE es_topic
SET topic_summary = 'Explores integration between immunization information systems and vaccination appointment scheduling services.',
    topic_emoji = '📅'
WHERE es_topic_id = 49;

-- 50: Bulk Data Submission
UPDATE es_topic
SET topic_summary = 'Supports submitting large volumes of immunization updates through files or APIs instead of individual real-time HL7 version 2 messages.',
    topic_emoji = '📦'
WHERE es_topic_id = 50;

-- 51: CDC HL7 v2 Guide (Release 1.5)
UPDATE es_topic
SET topic_summary = 'Covers the current national HL7 version 2.5.1 implementation guide for immunization update, acknowledgement, query, and response exchange.',
    topic_emoji = '📘'
WHERE es_topic_id = 51;

-- 52: CDC HL7 v2 Guide (Release 2)
UPDATE es_topic
SET topic_summary = 'Develops the next national HL7 version 2.5.1 implementation guide for immunization update, acknowledgement, query, and response exchange.',
    topic_emoji = '📗'
WHERE es_topic_id = 52;

-- 53: CDS Response Improvements (RSP)
UPDATE es_topic
SET topic_summary = 'Improves how HL7 version 2 response messages represent immunization evaluations, recommendations, schedules, and explanatory details.',
    topic_emoji = '💬'
WHERE es_topic_id = 53;

-- 54: Clinical Decision Support (CDS Hooks)
UPDATE es_topic
SET topic_summary = 'Uses CDS Hooks to deliver immunization clinical decision support within electronic health record workflows.',
    topic_emoji = '🪝'
WHERE es_topic_id = 54;

-- 55: COVID-19 / Mpox Reporting
UPDATE es_topic
SET topic_summary = 'Covers specialized immunization and public health reporting developed for the COVID-19 and mpox responses.',
    topic_emoji = '🦠'
WHERE es_topic_id = 55;

-- 56: Data Export Format (DAR-Based)
UPDATE es_topic
SET topic_summary = 'Extends the Data at Rest (DAR) extract format into a reusable flat-file standard for immunization information system data exports.',
    topic_emoji = '🗂️'
WHERE es_topic_id = 56;

-- 57: Disability Data Collection
UPDATE es_topic
SET topic_summary = 'Explores whether immunization information systems should collect disability-related information required by programs or jurisdictions.',
    topic_emoji = '♿'
WHERE es_topic_id = 57;

-- 58: Emergency Vaccination Reporting
UPDATE es_topic
SET topic_summary = 'Defines consistent, rapid vaccination reporting to public health authorities during emergencies and outbreak responses.',
    topic_emoji = '🚑'
WHERE es_topic_id = 58;

-- 59: Flat File Data Export
UPDATE es_topic
SET topic_summary = 'Defines a common flat-file format for exporting immunization information system data when messaging or APIs are not practical.',
    topic_emoji = '📤'
WHERE es_topic_id = 59;

-- 60: Homeless Data Integration (HMIS)
UPDATE es_topic
SET topic_summary = 'Explores data exchange between immunization information systems and Homeless Management Information Systems (HMIS).',
    topic_emoji = '🏠'
WHERE es_topic_id = 60;

-- 61: IIS Metrics Access (IISAR)
UPDATE es_topic
SET topic_summary = 'Provides direct, on-demand access to aggregate immunization information system metrics currently reported through the IIS Annual Report (IISAR).',
    topic_emoji = '📊'
WHERE es_topic_id = 61;

-- 62: IIS-to-IIS Exchange
UPDATE es_topic
SET topic_summary = 'Exchanges patient and immunization records between jurisdictional immunization information systems through national HL7 version 2 and IZ Gateway workflows.',
    topic_emoji = '↔️'
WHERE es_topic_id = 62;

-- 63: Lead Test Query
UPDATE es_topic
SET topic_summary = 'Enables authorized systems to query lead blood test information from jurisdictions that store it in an immunization information system.',
    topic_emoji = '🩸'
WHERE es_topic_id = 63;

-- 64: Lead Test Reporting
UPDATE es_topic
SET topic_summary = 'Enables submission of lead blood test results to jurisdictions that store them in an immunization information system.',
    topic_emoji = '🧪'
WHERE es_topic_id = 64;

-- 65: LOINC Usage in OBX
UPDATE es_topic
SET topic_summary = 'Defines consistent use of Logical Observation Identifiers Names and Codes (LOINC) in HL7 version 2 observation segments for immunization data.',
    topic_emoji = '🔬'
WHERE es_topic_id = 65;

-- 66: Preferred Name (Patient)
UPDATE es_topic
SET topic_summary = 'Transmits a patient''s preferred name alongside legal identifiers in HL7 version 2 immunization messages.',
    topic_emoji = '🏷️'
WHERE es_topic_id = 66;

-- 67: Priority Group Classification
UPDATE es_topic
SET topic_summary = 'Classifies patients into priority groups used for vaccine allocation, outreach, or emergency response.',
    topic_emoji = '🎯'
WHERE es_topic_id = 67;

-- 68: Serology Reporting
UPDATE es_topic
SET topic_summary = 'Explores capturing and exchanging antibody test results that may inform interpretation of immunization status.',
    topic_emoji = '🧫'
WHERE es_topic_id = 68;

-- 69: Sexual Orientation & Gender Identity (SOGI)
UPDATE es_topic
SET topic_summary = 'Explores representation of sexual orientation and gender identity (SOGI) information in immunization information systems.',
    topic_emoji = '👥'
WHERE es_topic_id = 69;

-- 70: SMART Applications (SMART on FHIR)
UPDATE es_topic
SET topic_summary = 'Uses SMART on FHIR applications to provide secure, user-facing tools and workflows connected to immunization information systems.',
    topic_emoji = '📲'
WHERE es_topic_id = 70;

-- 71: TB Screening Data
UPDATE es_topic
SET topic_summary = 'Explores capturing and exchanging tuberculosis screening information through immunization information systems.',
    topic_emoji = '🫁'
WHERE es_topic_id = 71;

-- 72: Vaccine Availability Search
UPDATE es_topic
SET topic_summary = 'Helps individuals locate providers with available vaccines by combining location, provider, and inventory information.',
    topic_emoji = '📍'
WHERE es_topic_id = 72;

-- 73: Vaccine Storage & Temperature
UPDATE es_topic
SET topic_summary = 'Explores monitoring vaccine storage conditions and temperatures through immunization information systems or connected equipment.',
    topic_emoji = '🌡️'
WHERE es_topic_id = 73;

-- 74: Immunization Focus Group (IFG)
UPDATE es_topic
SET topic_summary = 'Provides an HL7 Public Health Work Group forum for discussing new and emerging immunization interoperability standards.',
    topic_emoji = '🗣️'
WHERE es_topic_id = 74;

-- 75: VXU on FHIR
UPDATE es_topic
SET topic_summary = 'Defines a FHIR alternative to HL7 version 2 vaccine update and acknowledgement workflows for submitting immunization histories and receiving processing results.',
    topic_emoji = '📨'
WHERE es_topic_id = 75;

-- 76: QBP on FHIR
UPDATE es_topic
SET topic_summary = 'Defines FHIR alternatives to HL7 version 2 immunization query and response workflows, including patient matching, history, evaluations, and recommendations.',
    topic_emoji = '🔎'
WHERE es_topic_id = 76;

-- 77: HL7 v2.5.1 School Roster Reporting
UPDATE es_topic
SET topic_summary = 'Uses HL7 version 2.5.1 messages to exchange student roster information between school information systems and immunization information systems.',
    topic_emoji = '🏫'
WHERE es_topic_id = 77;

-- 78: ADT on FHIR
UPDATE es_topic
SET topic_summary = 'Explores FHIR-based automation for establishing and maintaining data exchange relationships with immunization information systems.',
    topic_emoji = '⚙️'
WHERE es_topic_id = 78;

-- 79: Bulk FHIR Query
UPDATE es_topic
SET topic_summary = 'Uses bulk FHIR queries to obtain demographic or other patient updates from healthcare organizations for public health purposes.',
    topic_emoji = '📚'
WHERE es_topic_id = 79;

-- 80: Clinical Quality Language
UPDATE es_topic
SET topic_summary = 'Uses Clinical Quality Language (CQL) to express computable clinical quality measures and decision-support logic.',
    topic_emoji = '🧮'
WHERE es_topic_id = 80;

-- 81: Direct Trust
UPDATE es_topic
SET topic_summary = 'Explores the DirectTrust privacy-enhancing health record locator and credential ecosystem for nationwide patient identity and matching.',
    topic_emoji = '🕵️'
WHERE es_topic_id = 81;

-- 82: HL7 v2 to FHIR Translation
UPDATE es_topic
SET topic_summary = 'Translates immunization data between HL7 version 2.5.1 messages and FHIR resources in either direction.',
    topic_emoji = '🔄'
WHERE es_topic_id = 82;

-- 83: Identification of Medicinal Products (IDMP)
UPDATE es_topic
SET topic_summary = 'Applies the Identification of Medicinal Products (IDMP) standards to uniquely identify regulated vaccine and medicinal products internationally.',
    topic_emoji = '🧬'
WHERE es_topic_id = 83;

-- 84: IIS to EHR Query
UPDATE es_topic
SET topic_summary = 'Explores whether an immunization information system should query electronic health records for non-immunization patient information through FHIR.',
    topic_emoji = '🏥'
WHERE es_topic_id = 84;

-- 85: Insurance
UPDATE es_topic
SET topic_summary = 'Examines limited use cases for collecting or exchanging patient insurance information through immunization information systems.',
    topic_emoji = '🧾'
WHERE es_topic_id = 85;

-- 86: Machine Learning
UPDATE es_topic
SET topic_summary = 'Explores machine learning, artificial intelligence, and large language models for immunization interoperability and program operations.',
    topic_emoji = '🤖'
WHERE es_topic_id = 86;

-- 87: Multi-Jurisdictional Query
UPDATE es_topic
SET topic_summary = 'Allows organizations operating across jurisdictions to query multiple immunization information systems through a coordinated request.',
    topic_emoji = '🗺️'
WHERE es_topic_id = 87;

-- 88: Newborn Admission Notification Information  (NANI)
UPDATE es_topic
SET topic_summary = 'Uses the Newborn Admission Notification Information (NANI) profile to notify public health programs promptly when a newborn is admitted.',
    topic_emoji = '👶'
WHERE es_topic_id = 88;

-- 89: National Immunization Technical Advisory Groups
UPDATE es_topic
SET topic_summary = 'Examines national expert advisory groups that develop evidence-based vaccine policy and immunization schedule recommendations.',
    topic_emoji = '👩‍⚕️'
WHERE es_topic_id = 89;

-- 90: Product Identifiers/Serialization
UPDATE es_topic
SET topic_summary = 'Uses serialized identifiers to distinguish individual vaccine or medicinal-product packages throughout the supply chain.',
    topic_emoji = '🔢'
WHERE es_topic_id = 90;

-- 91: Race/Ethnicity
UPDATE es_topic
SET topic_summary = 'Addresses changes to the collection and exchange of race and ethnicity information in United States immunization workflows.',
    topic_emoji = '👥'
WHERE es_topic_id = 91;

-- 92: Social Determinants of Health (SDOH)
UPDATE es_topic
SET topic_summary = 'Explores the appropriate role of social determinants of health (SDOH) information within immunization information systems.',
    topic_emoji = '🏘️'
WHERE es_topic_id = 92;

-- 93: Space Health
UPDATE es_topic
SET topic_summary = 'Explores FHIR-based health records and immunization interoperability for astronauts and spaceflight missions.',
    topic_emoji = '🚀'
WHERE es_topic_id = 93;

-- 94: Synthetic Data
UPDATE es_topic
SET topic_summary = 'Creates realistic but non-identifying immunization data for software testing, demonstrations, training, and research.',
    topic_emoji = '🧪'
WHERE es_topic_id = 94;

-- 95: VTrcks-IIS API
UPDATE es_topic
SET topic_summary = 'Integrates immunization information systems with the Centers for Disease Control and Prevention Vaccine Tracking System (VTrckS) for publicly funded vaccine ordering and distribution.',
    topic_emoji = '🚚'
WHERE es_topic_id = 95;

-- 96: FHIR Open Tickets
UPDATE es_topic
SET topic_summary = 'Reviews and coordinates open FHIR specification issues related to immunization workflows and implementation guidance.',
    topic_emoji = '🎫'
WHERE es_topic_id = 96;

-- 97: IIP Collaborative
UPDATE es_topic
SET topic_summary = 'Coordinates the Immunization Integration Program (IIP) partnership to improve immunization data exchange, management, testing, and use.',
    topic_emoji = '🤝'
WHERE es_topic_id = 97;

-- 98: Provider Identifier
UPDATE es_topic
SET topic_summary = 'Develops a national strategy for consistently identifying immunizing provider organizations and sites across jurisdictions.',
    topic_emoji = '🏥'
WHERE es_topic_id = 98;

-- 99: Patient Record Matching
UPDATE es_topic
SET topic_summary = 'Improves how patient identities are represented, matched, and resolved between systems during immunization interoperability.',
    topic_emoji = '🧩'
WHERE es_topic_id = 99;

-- 100: Query by Parameter (QBP)
UPDATE es_topic
SET topic_summary = 'Uses HL7 version 2 Query by Parameter (QBP) messages to request immunization histories, evaluations, and recommendations.',
    topic_emoji = '❓'
WHERE es_topic_id = 100;

-- 101: Estimated Vaccination Dates
UPDATE es_topic
SET topic_summary = 'Represents partially known vaccination dates without inventing an exact day that could mislead clinical decisions or analytics.',
    topic_emoji = '📅'
WHERE es_topic_id = 101;

-- 102: Subpotent Vaccinations
UPDATE es_topic
SET topic_summary = 'Records administered vaccinations that should not count toward immunity because the product, storage, dose, or administration was inadequate.',
    topic_emoji = '⚠️'
WHERE es_topic_id = 102;

-- 103: NDC Transition to 12-Digit Format
UPDATE es_topic
SET topic_summary = 'Prepares immunization systems and vocabularies for the United States transition from 10-digit to 12-digit National Drug Codes (NDCs).',
    topic_emoji = '🔢'
WHERE es_topic_id = 103;

-- 104: England
UPDATE es_topic
SET topic_summary = 'Examines England''s National Health Service immunization systems, vaccine terminology, standards, governance, and data exchange practices.',
    topic_emoji = '🏴'
WHERE es_topic_id = 104;

-- 105: Canada
UPDATE es_topic
SET topic_summary = 'Examines Canada''s immunization registries, vaccine coding, standards, and exchange practices across provinces and territories.',
    topic_emoji = '🇨🇦'
WHERE es_topic_id = 105;

-- 106: Mexico
UPDATE es_topic
SET topic_summary = 'Examines Mexico''s immunization information systems, vaccine coding, standards, and public health data exchange practices.',
    topic_emoji = '🇲🇽'
WHERE es_topic_id = 106;

-- 107: France
UPDATE es_topic
SET topic_summary = 'Examines France''s immunization recommendations, vaccine terminology, digital health infrastructure, and interoperability practices.',
    topic_emoji = '🇫🇷'
WHERE es_topic_id = 107;

-- 108: Norway
UPDATE es_topic
SET topic_summary = 'Examines Norway''s national digital health infrastructure, immunization registry practices, standards, and data governance.',
    topic_emoji = '🇳🇴'
WHERE es_topic_id = 108;

-- 109: Australia
UPDATE es_topic
SET topic_summary = 'Examines Australia''s national immunization systems, vaccine terminology, standards, and interoperability community.',
    topic_emoji = '🇦🇺'
WHERE es_topic_id = 109;

-- 110: New Zealand
UPDATE es_topic
SET topic_summary = 'Examines New Zealand''s immunization systems, vaccine coding, standards, and collaboration with regional partners.',
    topic_emoji = '🇳🇿'
WHERE es_topic_id = 110;

-- 111: Philippines
UPDATE es_topic
SET topic_summary = 'Examines immunization data collection, coding, exchange, and collaboration opportunities in the Philippines.',
    topic_emoji = '🇵🇭'
WHERE es_topic_id = 111;

-- 112: Netherlands
UPDATE es_topic
SET topic_summary = 'Examines the Netherlands'' digital health infrastructure, immunization programs, terminology, and interoperability practices.',
    topic_emoji = '🇳🇱'
WHERE es_topic_id = 112;

-- 113: Finland
UPDATE es_topic
SET topic_summary = 'Examines Finland''s national health information infrastructure, vaccine information systems, standards, and public health practices.',
    topic_emoji = '🇫🇮'
WHERE es_topic_id = 113;

-- 114: Spain
UPDATE es_topic
SET topic_summary = 'Examines Spain''s regional and national immunization systems, governance, terminology, and standards coordination.',
    topic_emoji = '🇪🇸'
WHERE es_topic_id = 114;

-- 115: Scotland
UPDATE es_topic
SET topic_summary = 'Examines NHS Scotland''s immunization systems, vaccine terminology, standards, and national digital health infrastructure.',
    topic_emoji = '🏴'
WHERE es_topic_id = 115;

-- 116: Pan American Health Organization (PAHO)
UPDATE es_topic
SET topic_summary = 'Examines how the Pan American Health Organization (PAHO) supports regional immunization programs, digital health, terminology, and cross-country collaboration.',
    topic_emoji = '🌎'
WHERE es_topic_id = 116;

-- 117: Unified Nomenclature of Vaccines (NUVA)
UPDATE es_topic
SET topic_summary = 'Uses the Unified Nomenclature of Vaccines (NUVA) to identify vaccine products and interpret vaccination histories across countries and source systems.',
    topic_emoji = '💉'
WHERE es_topic_id = 117;

-- 118: FHIR Dev Days
UPDATE es_topic
SET topic_summary = 'Tracks the international FHIR DevDays conference for implementation experience, education, collaboration, and emerging Fast Healthcare Interoperability Resources practices.',
    topic_emoji = '👩‍💻'
WHERE es_topic_id = 118;

-- 119: Fact of Death Exchange
UPDATE es_topic
SET topic_summary = 'Standardizes exchange of verified patient death information among vital records, healthcare, public health, and immunization information systems.',
    topic_emoji = '🕯️'
WHERE es_topic_id = 119;

-- 120: Building Bridges
UPDATE es_topic
SET topic_summary = 'Builds relationships with countries, regional organizations, and terminology authorities to understand vaccine vocabularies and align interoperability efforts.',
    topic_emoji = '🌉'
WHERE es_topic_id = 120;

-- 121: Message Quality Evaluation Tool (MQE)
UPDATE es_topic
SET topic_summary = 'Uses the open-source Message Quality Evaluation Tool (MQE) to assess and improve the quality of incoming HL7 version 2 immunization data.',
    topic_emoji = '🧰'
WHERE es_topic_id = 121;

-- 122: Dubai
UPDATE es_topic
SET topic_summary = 'Examines Dubai''s immunization systems, vaccine coding, and health information exchange through organizations such as Dubai Health Authority and NABIDH.',
    topic_emoji = '🇦🇪'
WHERE es_topic_id = 122;

-- 123: Interface Security
UPDATE es_topic
SET topic_summary = 'Coordinates security architecture for immunization interfaces, including authentication, authorization, transport protection, credentials, and operational responsibilities.',
    topic_emoji = '🛡️'
WHERE es_topic_id = 123;

-- 124: Certificate Management
UPDATE es_topic
SET topic_summary = 'Addresses issuance, installation, renewal, rotation, monitoring, revocation, and ownership of digital certificates used by immunization interfaces.',
    topic_emoji = '📜'
WHERE es_topic_id = 124;

COMMIT;

-- Allow a Topic Space to designate a primary Topic Board that represents
-- its default board view. Optional: not every space needs one yet.
ALTER TABLE es_topic_space
  ADD COLUMN primary_es_topic_board_definition_id BIGINT UNSIGNED NULL AFTER display_order,
  ADD KEY ix_es_topic_space_primary_board (primary_es_topic_board_definition_id),
  ADD CONSTRAINT fk_es_topic_space_primary_board FOREIGN KEY (primary_es_topic_board_definition_id)
    REFERENCES es_topic_board_definition (es_topic_board_definition_id);

START TRANSACTION;

UPDATE es_topic_space
SET primary_es_topic_board_definition_id = (
  SELECT es_topic_board_definition_id FROM es_topic_board_definition WHERE board_code = 'emerging-standards'
)
WHERE space_code = 'emerging-standards';

UPDATE es_topic_space
SET primary_es_topic_board_definition_id = (
  SELECT es_topic_board_definition_id FROM es_topic_board_definition WHERE board_code = 'aira-opportunity-nursery'
)
WHERE space_code = 'aira-opportunity-nursery';

COMMIT;