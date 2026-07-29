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
    (v_emerging_standards_space_id, 'start', 'Start', 'Start topics are beginning active development work toward practical implementation.', 10, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_emerging_standards_space_id, 'draft', 'Draft', 'Draft topics are early-stage ideas gathering initial interest and problem framing.', 20, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_emerging_standards_space_id, 'gather', 'Gather', 'Gather topics are collecting broader input from implementers and stakeholders.', 30, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_emerging_standards_space_id, 'monitor', 'Monitor', 'Monitor topics are active efforts being tracked for readiness and real-world momentum.', 40, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_emerging_standards_space_id, 'parked', 'Parked', 'Parked topics are intentionally paused while dependencies or timing constraints are addressed.', 50, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_emerging_standards_space_id, 'pilot', 'Pilot', 'Pilot topics are in trial implementations to validate feasibility and workflow impact.', 60, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
    (v_emerging_standards_space_id, 'rollout', 'Rollout', 'Rollout topics are ready for broader adoption and implementation support.', 70, 1, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
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