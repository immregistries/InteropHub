-- v0.5 - Deep Dive table-round voting model
-- Adds campaign round control and redesigns es_interest for table voting.

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- -------------------------
-- CAMPAIGN ROUND TRACKING
-- -------------------------
ALTER TABLE es_campaign
  ADD COLUMN current_round_no TINYINT UNSIGNED NOT NULL DEFAULT 1 AFTER status;

-- -------------------------
-- INTEREST TABLE REDESIGN
-- -------------------------
ALTER TABLE es_interest
  DROP FOREIGN KEY fk_es_interest_user;

ALTER TABLE es_interest
  DROP COLUMN user_id,
  DROP COLUMN first_name,
  DROP COLUMN last_name,
  DROP COLUMN email,
  DROP COLUMN email_normalized,
  DROP COLUMN opt_in_topic_updates,
  ADD COLUMN es_campaign_registration_id BIGINT UNSIGNED NULL AFTER es_topic_id,
  ADD COLUMN table_no TINYINT UNSIGNED NOT NULL AFTER session_key,
  ADD COLUMN round_no TINYINT UNSIGNED NOT NULL AFTER table_no;

ALTER TABLE es_interest
  DROP INDEX ix_es_interest_user,
  DROP INDEX ix_es_interest_email,
  DROP INDEX ix_es_interest_session,
  DROP INDEX ix_es_interest_campaign_topic,
  ADD KEY ix_es_interest_campaign_table_round_topic (es_campaign_id, table_no, round_no, es_topic_id),
  ADD KEY ix_es_interest_campaign_table_round_session (es_campaign_id, table_no, round_no, session_key),
  ADD KEY ix_es_interest_campaign_registration (es_campaign_registration_id);

ALTER TABLE es_interest
  ADD CONSTRAINT fk_es_interest_campaign_registration
    FOREIGN KEY (es_campaign_registration_id)
    REFERENCES es_campaign_registration (es_campaign_registration_id);
