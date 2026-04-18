-- v0.8 - Add optional ES topic metadata fields
-- Adds nullable metadata used by admin import and full-description display.

SET NAMES utf8mb4;
SET time_zone = '+00:00';

ALTER TABLE es_topic
  ADD COLUMN policy_status VARCHAR(120) NULL AFTER status,
  ADD COLUMN topic_type VARCHAR(120) NULL AFTER policy_status;
