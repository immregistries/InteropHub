-- v0.24 - Canonical topic-to-neighborhood mapping (snapshot-based)
-- This migration uses explicit topic/neighborhood values from
-- neighborhood_data_2026_05_28.txt to populate es_topic_neighborhood.

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS es_topic_neighborhood (
  es_topic_neighborhood_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_topic_id              BIGINT NOT NULL,
  es_neighborhood_id       BIGINT NOT NULL,
  created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (es_topic_neighborhood_id),
  UNIQUE KEY uq_es_topic_neighborhood (es_topic_id, es_neighborhood_id),
  KEY ix_es_topic_neighborhood_topic (es_topic_id),
  KEY ix_es_topic_neighborhood_neighborhood (es_neighborhood_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TEMPORARY TABLE IF EXISTS tmp_topic_neighborhood_snapshot;
CREATE TEMPORARY TABLE tmp_topic_neighborhood_snapshot (
  es_topic_id        BIGINT UNSIGNED NOT NULL,
  neighborhood_name  VARCHAR(140) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  UNIQUE KEY uq_tmp_topic_neighborhood (es_topic_id, neighborhood_name)
) ENGINE=InnoDB;

INSERT INTO tmp_topic_neighborhood_snapshot (es_topic_id, neighborhood_name)
VALUES
(1, 'CDS'),
(1, 'FHIR'),
(1, 'Support'),
(10, 'Advanced Access'),
(10, 'FHIR'),
(100, 'Consumer Access'),
(100, 'HL7 v2'),
(101, 'Data Quality'),
(101, 'FHIR'),
(101, 'HL7 v2'),
(102, 'Data Quality'),
(11, 'Data Quality'),
(12, 'Inventory'),
(13, 'CDS'),
(13, 'FHIR'),
(13, 'Modularity'),
(13, 'Support'),
(14, 'Advanced Access'),
(14, 'FHIR'),
(15, 'Modularity'),
(15, 'Terminology'),
(16, 'Inventory'),
(17, 'Consumer Access'),
(18, 'Advanced Access'),
(19, 'Consumer Access'),
(19, 'FHIR'),
(2, 'Consumer Access'),
(20, 'CDS'),
(20, 'HL7 v2'),
(21, 'Modularity'),
(21, 'Support'),
(22, 'HL7 v2'),
(23, 'Data Quality'),
(24, 'TEFCA'),
(24, 'Terminology'),
(25, 'CDS'),
(25, 'HL7 v2'),
(26, 'CDS'),
(26, 'HL7 v2'),
(27, 'Advanced Access'),
(28, 'FHIR'),
(28, 'Terminology'),
(29, 'Inventory'),
(3, 'Data Quality'),
(30, 'Advanced Access'),
(31, 'FHIR'),
(31, 'Terminology'),
(32, 'Consumer Access'),
(32, 'FHIR'),
(33, 'Advanced Access'),
(33, 'FHIR'),
(34, 'FHIR'),
(34, 'Inventory'),
(35, 'Data Quality'),
(36, 'HL7 v2'),
(37, 'Auxiliary'),
(37, 'School'),
(38, 'HL7 v2'),
(39, 'Inventory'),
(4, 'Inventory'),
(4, 'Modularity'),
(40, 'Consumer Access'),
(41, 'FHIR'),
(41, 'HL7 v2'),
(41, 'Modularity'),
(41, 'Support'),
(42, 'FHIR'),
(42, 'HL7 v2'),
(43, 'Consumer Access'),
(43, 'FHIR'),
(44, 'HL7 v2'),
(45, 'HL7 v2'),
(46, 'National'),
(47, 'Advanced Access'),
(48, 'HL7 v2'),
(49, 'Consumer Access'),
(5, 'Consumer Access'),
(50, 'Advanced Access'),
(51, 'HL7 v2'),
(52, 'HL7 v2'),
(53, 'CDS'),
(53, 'HL7 v2'),
(54, 'Advanced Access'),
(54, 'CDS'),
(54, 'FHIR'),
(55, 'National'),
(56, 'Advanced Access'),
(57, 'HL7 v2'),
(58, 'National'),
(59, 'Advanced Access'),
(6, 'Advanced Access'),
(60, 'Auxiliary'),
(61, 'Advanced Access'),
(62, 'HL7 v2'),
(63, 'Auxiliary'),
(64, 'Auxiliary'),
(65, 'HL7 v2'),
(66, 'HL7 v2'),
(67, 'HL7 v2'),
(68, 'HL7 v2'),
(69, 'HL7 v2'),
(7, 'CDS'),
(7, 'FHIR'),
(7, 'HL7 v2'),
(70, 'Advanced Access'),
(70, 'FHIR'),
(71, 'Auxiliary'),
(72, 'Consumer Access'),
(73, 'Inventory'),
(74, 'FHIR'),
(74, 'National'),
(75, 'FHIR'),
(75, 'TEFCA'),
(76, 'FHIR'),
(76, 'TEFCA'),
(77, 'Auxiliary'),
(77, 'HL7 v2'),
(77, 'School'),
(78, 'FHIR'),
(78, 'TEFCA'),
(79, 'FHIR'),
(79, 'TEFCA'),
(8, 'National'),
(80, 'Support'),
(81, 'Support'),
(82, 'FHIR'),
(82, 'HL7 v2'),
(82, 'TEFCA'),
(83, 'Terminology'),
(84, 'CDS'),
(84, 'FHIR'),
(84, 'TEFCA'),
(85, 'HL7 v2'),
(86, 'Support'),
(87, 'HL7 v2'),
(88, 'Auxiliary'),
(89, 'Support'),
(9, 'Modularity'),
(9, 'Support'),
(90, 'FHIR'),
(91, 'HL7 v2'),
(92, 'Auxiliary'),
(93, 'Auxiliary'),
(94, 'Support'),
(95, 'Inventory'),
(96, 'FHIR'),
(97, 'HL7 v2'),
(98, 'HL7 v2'),
(99, 'Consumer Access'),
(99, 'FHIR'),
(99, 'HL7 v2');

-- Ensure any snapshot neighborhood missing from catalog is created.
SET @seed_user_id := (SELECT user_id FROM auth_user ORDER BY user_id ASC LIMIT 1);
SET @next_display_order := (SELECT COALESCE(MAX(display_order), -1) + 1 FROM es_neighborhood);

INSERT INTO es_neighborhood (
  neighborhood_code,
  neighborhood_name,
  description,
  created_at,
  updated_at,
  display_order,
  is_active,
  created_by_user_id
)
SELECT
  LOWER(REPLACE(REPLACE(TRIM(s.neighborhood_name), ' ', '-'), '--', '-')) AS neighborhood_code,
  s.neighborhood_name,
  NULL AS description,
  UTC_TIMESTAMP() AS created_at,
  UTC_TIMESTAMP() AS updated_at,
  (@next_display_order := @next_display_order + 1) AS display_order,
  1 AS is_active,
  @seed_user_id AS created_by_user_id
FROM (
  SELECT DISTINCT neighborhood_name
  FROM tmp_topic_neighborhood_snapshot
) s
LEFT JOIN es_neighborhood n
  ON LOWER(TRIM(n.neighborhood_name)) COLLATE utf8mb4_unicode_ci
   = LOWER(TRIM(s.neighborhood_name)) COLLATE utf8mb4_unicode_ci
WHERE n.es_neighborhood_id IS NULL
  AND @seed_user_id IS NOT NULL;

-- Rebuild canonical link table from snapshot values.
DELETE FROM es_topic_neighborhood;

INSERT IGNORE INTO es_topic_neighborhood (es_topic_id, es_neighborhood_id)
SELECT
  s.es_topic_id,
  n.es_neighborhood_id
FROM tmp_topic_neighborhood_snapshot s
JOIN es_neighborhood n
  ON LOWER(TRIM(n.neighborhood_name)) COLLATE utf8mb4_unicode_ci
   = LOWER(TRIM(s.neighborhood_name)) COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS tmp_topic_neighborhood_snapshot;
