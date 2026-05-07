-- v0.12: Split auth_user.display_name → first_name + last_name
-- display_name is kept as a nullable override (when non-NULL it will override the computed display).
-- After migration display_name is set to NULL so it starts as a clean override slot.

ALTER TABLE auth_user
  ADD COLUMN first_name VARCHAR(100) NULL AFTER display_name,
  ADD COLUMN last_name  VARCHAR(100) NULL AFTER first_name;

-- Populate first_name / last_name from existing display_name values.

-- Case 1: display_name contains at least one space.
--   last_name  = last space-delimited token
--   first_name = everything before the last space
UPDATE auth_user
SET
  last_name  = TRIM(SUBSTRING_INDEX(display_name, ' ', -1)),
  first_name = TRIM(LEFT(display_name, CHAR_LENGTH(display_name) - CHAR_LENGTH(TRIM(SUBSTRING_INDEX(display_name, ' ', -1))) - 1))
WHERE display_name IS NOT NULL
  AND LOCATE(' ', TRIM(display_name)) > 0;

-- Case 2: display_name has no space (single token) → treat as first_name only.
UPDATE auth_user
SET
  first_name = TRIM(display_name),
  last_name  = NULL
WHERE display_name IS NOT NULL
  AND LOCATE(' ', TRIM(display_name)) = 0;

-- Reset display_name to NULL — it is now an optional user-controlled override.
-- The application layer will use it as: if display_name IS NOT NULL use it, else "first_name last_name".
UPDATE auth_user SET display_name = NULL;
