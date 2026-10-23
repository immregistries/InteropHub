-- Pending schema/data changes for the next production release.
-- Process, conventions, and how to fold local admin/UI edits (e.g. Topic
-- Board layout changes made in the running app) back into this file before
-- a refresh discards them: see docs/database-release-practice.md.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Remove bot sign-ups from testemailblasttest@gmail.com.
-- On 2026-10-14 17:30:51-54 UTC an automated client submitted the public
-- DD26 campaign form (es_campaign_id 1) many times concurrently, creating 81
-- es_subscription rows (79 TOPIC across ~45 topics, often duplicated, plus 2
-- GENERAL_ES) with no auth_user account, no contact details, and no email
-- ever sent to it. They were showing up as "New Followers" in the daily
-- digest. No other table references this address.
DELETE FROM es_subscription
 WHERE email_normalized = 'testemailblasttest@gmail.com';
