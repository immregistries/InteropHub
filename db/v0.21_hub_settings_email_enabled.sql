-- v0.21: Add email_enabled flag to hub_settings.
-- When 0, EmailService skips SMTP entirely and returns a no-op success.
-- Useful in local development where credentials are not available.
-- Default 1 preserves existing production behavior with no data migration needed.

SET NAMES utf8mb4;
SET time_zone = '+00:00';

ALTER TABLE hub_settings
  ADD COLUMN email_enabled TINYINT(1) NOT NULL DEFAULT 1
    COMMENT 'When 0, all outbound email is silently suppressed (no SMTP attempt).'
    AFTER smtp_from_name;
