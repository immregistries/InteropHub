SET NAMES utf8mb4;
SET time_zone = '+00:00';

ALTER TABLE auth_user
  ADD COLUMN is_admin BIT(1) NOT NULL DEFAULT b'0' AFTER status;

UPDATE auth_user
SET is_admin = b'1'
WHERE status = 'ACTIVE'
  AND email_normalized LIKE '%@immregistries.org';
