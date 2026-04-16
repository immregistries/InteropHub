-- v0.4 – Change es_topic priority columns from TINYINT(1) (boolean) to INT
--         to support integer-ranked priority values (e.g. 1, 2, 3).
--
-- Safe on existing data: 0/1 values remain valid integers after conversion.

ALTER TABLE es_topic
  MODIFY COLUMN priority_iis  INT NOT NULL DEFAULT 0,
  MODIFY COLUMN priority_ehr  INT NOT NULL DEFAULT 0,
  MODIFY COLUMN priority_cdc  INT NOT NULL DEFAULT 0;
