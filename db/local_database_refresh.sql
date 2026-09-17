-- ============================================================
-- LOCAL DEVELOPMENT DATABASE REFRESH
-- Run this immediately after restoring a production snapshot.
-- Replaces production values with local development settings.
-- ============================================================

-- -------------------------
-- HUB SETTINGS
-- -------------------------
-- Replace production hub_settings with local dev values.
UPDATE hub_settings
SET
  active            = 1,
  email_enabled     = 0,
  external_base_url = 'http://localhost:8080/hub',
  smtp_host         = 'sandbox.smtp.mailtrap.io',
  smtp_port         = 587,
  smtp_username     = '',
  smtp_password     = '',
  smtp_auth         = 1,
  smtp_starttls     = 1,
  smtp_ssl          = 0,
  smtp_from_email   = 'informatics-noreply@immregistries.org',
  smtp_from_name    = 'InteropHub'
WHERE active = 1;

-- -------------------------
-- PRODUCTION → LOCAL ORIGIN
-- -------------------------
-- Single source of truth for the two URL-UPDATES sections below.

SET @prod_origin  = 'https://informatics.immregistries.org';
SET @local_origin = 'http://localhost:8080';

-- -------------------------
-- APP REGISTRY — URL UPDATES
-- -------------------------
-- Redirect any production app URL to its localhost equivalent, preserving
-- the path after the origin. Prefix match, not exact match, so this covers
-- every app automatically -- no per-app statement needs to be added here as
-- new apps reach production. See db/local_database_refresh.md.
-- Safe to run even when no row matches (0 rows updated = no-op).

UPDATE app_registry
SET default_redirect_url = CONCAT(@local_origin, SUBSTRING(default_redirect_url, LENGTH(@prod_origin) + 1))
WHERE default_redirect_url LIKE CONCAT(@prod_origin, '%');

-- -------------------------
-- APP REGISTRY — TEMPORARY LOCAL-ONLY INSERTS
-- -------------------------
-- Clear is not yet in production. Remove this block once it is deployed.
-- INSERT IGNORE prevents a duplicate-key error if it already exists.

INSERT IGNORE INTO app_registry
  (app_id, app_code, app_name, default_redirect_url, app_description, managed_by, is_enabled, kill_switch)
VALUES
  (2, 'clear', 'Clear', 'http://localhost:8080/clear/', 'Community Led Exchange and Aggregate Reporting', 'AIRA', 1, 0);

-- -------------------------
-- APP REDIRECT ALLOWLIST — URL UPDATES
-- -------------------------
-- Same prefix-match swap as above, applied to the allowlist. The path
-- suffix after the domain root is preserved. Non-production-origin entries
-- (e.g. a staging URL on a different domain) are left untouched.

UPDATE app_redirect_allowlist
SET base_url = CONCAT(@local_origin, SUBSTRING(base_url, LENGTH(@prod_origin) + 1))
WHERE base_url LIKE CONCAT(@prod_origin, '%');

-- -------------------------
-- APP REDIRECT ALLOWLIST — TEMPORARY LOCAL-ONLY INSERTS
-- -------------------------
-- Clear redirect entries. app_id=2 is assumed correct until production deploy.
-- Remove this block once Clear is in production.

INSERT IGNORE INTO app_redirect_allowlist (app_id, base_url, is_enabled)
VALUES
  (2, 'http://localhost:8080/clear/login', 1),
  (2, 'http://localhost:8080/clear/',      1);
