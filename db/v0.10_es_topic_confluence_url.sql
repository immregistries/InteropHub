-- v0.10 - Add optional Confluence URL to ES topics

SET NAMES utf8mb4;
SET time_zone = '+00:00';

ALTER TABLE es_topic
  ADD COLUMN confluence_url VARCHAR(500) NULL AFTER topic_type;
