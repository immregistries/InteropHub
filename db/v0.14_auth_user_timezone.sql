SET NAMES utf8mb4;
SET time_zone = '+00:00';

ALTER TABLE auth_user
  ADD COLUMN timezone_id VARCHAR(64) NULL AFTER role_title;
