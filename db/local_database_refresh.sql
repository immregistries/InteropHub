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
-- APP REGISTRY — URL UPDATES
-- -------------------------
-- Redirect production app URLs to localhost.
-- Safe to run even when the production URL is absent (0 rows updated = no-op).

UPDATE app_registry
SET default_redirect_url = 'http://localhost:8080/step'
WHERE default_redirect_url = 'https://informatics.immregistries.org/step';

UPDATE app_registry
SET default_redirect_url = 'http://localhost:8080/clear/'
WHERE default_redirect_url = 'https://informatics.immregistries.org/clear/';

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
-- Swap any production base URLs for localhost equivalents.
-- The path suffix after the domain root is preserved.

UPDATE app_redirect_allowlist
SET base_url = 'http://localhost:8080/step'
WHERE base_url = 'https://informatics.immregistries.org/step';

UPDATE app_redirect_allowlist
SET base_url = 'http://localhost:8080/clear/login'
WHERE base_url = 'https://informatics.immregistries.org/clear/login';

UPDATE app_redirect_allowlist
SET base_url = 'http://localhost:8080/clear/'
WHERE base_url = 'https://informatics.immregistries.org/clear/';

-- -------------------------
-- APP REDIRECT ALLOWLIST — TEMPORARY LOCAL-ONLY INSERTS
-- -------------------------
-- Clear redirect entries. app_id=2 is assumed correct until production deploy.
-- Remove this block once Clear is in production.

INSERT IGNORE INTO app_redirect_allowlist (app_id, base_url, is_enabled)
VALUES
  (2, 'http://localhost:8080/clear/login', 1),
  (2, 'http://localhost:8080/clear/',      1);
