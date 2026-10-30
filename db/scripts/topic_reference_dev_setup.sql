-- Topic Reference comments: local test users and the Gate 1 allowlist.
--
-- NEVER apply this file to production. It creates obviously fake accounts
-- and a localhost redirect used only by the Gate 1 test page.
--
-- refresh_local_db.py applies this after unapplied_updates.sql. Idempotent.
-- Gate 1 only: Author, Reader, and Admin. The private test topic and the
-- guide registration are added from H2 on. At Gate 1 the allowlist row is
-- inserted here directly.
--
-- Author is an active CHAMPION of es_topic_id 7 (contextual-conditions).

SET NAMES utf8mb4;

INSERT INTO auth_user
  (email, email_normalized, first_name, last_name, organization, email_verified, status, is_admin)
VALUES
  ('author.guide-test@example.invalid', 'author.guide-test@example.invalid', 'Guide', 'Author', 'Example Health Department', 1, 'ACTIVE', 0)
ON DUPLICATE KEY UPDATE
  first_name = VALUES(first_name),
  last_name = VALUES(last_name),
  organization = VALUES(organization),
  email_verified = 1,
  status = 'ACTIVE',
  is_admin = 0;

INSERT INTO auth_user
  (email, email_normalized, first_name, last_name, organization, email_verified, status, is_admin)
VALUES
  ('reader.guide-test@example.invalid', 'reader.guide-test@example.invalid', 'Guide', 'Reader', 'Example Health Department', 1, 'ACTIVE', 0)
ON DUPLICATE KEY UPDATE
  first_name = VALUES(first_name),
  last_name = VALUES(last_name),
  organization = VALUES(organization),
  email_verified = 1,
  status = 'ACTIVE',
  is_admin = 0;

INSERT INTO auth_user
  (email, email_normalized, first_name, last_name, organization, email_verified, status, is_admin)
VALUES
  ('admin.guide-test@example.invalid', 'admin.guide-test@example.invalid', 'Guide', 'Admin', 'Example Health Department', 1, 'ACTIVE', 1)
ON DUPLICATE KEY UPDATE
  first_name = VALUES(first_name),
  last_name = VALUES(last_name),
  organization = VALUES(organization),
  email_verified = 1,
  status = 'ACTIVE',
  is_admin = 1;

SET @author_id = (SELECT user_id FROM auth_user WHERE email_normalized = 'author.guide-test@example.invalid');

UPDATE es_subscription
SET status = 'CHAMPION',
    subscription_type = 'TOPIC',
    unsubscribed_at = NULL,
    user_id = @author_id,
    contact_first_name = 'Guide',
    contact_last_name = 'Author',
    contact_organization = 'Example Health Department',
    updated_at = UTC_TIMESTAMP(6)
WHERE email_normalized = 'author.guide-test@example.invalid'
  AND es_topic_id = 7
  AND subscription_type = 'TOPIC';

INSERT INTO es_subscription
  (created_at, email, email_normalized, es_topic_id, status, subscription_type, updated_at, user_id,
   contact_first_name, contact_last_name, contact_organization)
SELECT UTC_TIMESTAMP(6), 'author.guide-test@example.invalid', 'author.guide-test@example.invalid', 7,
  'CHAMPION', 'TOPIC', UTC_TIMESTAMP(6), @author_id, 'Guide', 'Author', 'Example Health Department'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM es_subscription
  WHERE email_normalized = 'author.guide-test@example.invalid'
    AND es_topic_id = 7
    AND subscription_type = 'TOPIC'
);

SET @topic_reference_id = (SELECT app_id FROM app_registry WHERE app_code = 'topic-reference');

UPDATE app_redirect_allowlist
SET is_enabled = 1
WHERE app_id = @topic_reference_id
  AND base_url = 'http://localhost:8001/';

INSERT INTO app_redirect_allowlist (app_id, base_url, is_enabled)
SELECT @topic_reference_id, 'http://localhost:8001/', 1
FROM DUAL
WHERE @topic_reference_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM app_redirect_allowlist
    WHERE app_id = @topic_reference_id AND base_url = 'http://localhost:8001/'
  );
