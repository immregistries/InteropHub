-- Topic Reference comments: local test users and the Gate 1 allowlist.
--
-- NEVER apply this file to production. It creates obviously fake accounts
-- and a localhost redirect used only by the Gate 1 test page.
--
-- refresh_local_db.py applies this after unapplied_updates.sql. Idempotent.
-- Gate 1 only: Author, Reader, and Admin. H2 adds Outsider, a private
-- test topic, and the guide registrations below. The pilot registration
-- links to the allowlist row created above; it does not insert another one.
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

-- H2: Outsider cannot see the private test topic. No membership and no subscription.
INSERT INTO auth_user
  (email, email_normalized, first_name, last_name, organization, email_verified, status, is_admin)
VALUES
  ('outsider.guide-test@example.invalid', 'outsider.guide-test@example.invalid', 'Guide', 'Outsider', 'Example Health Department', 1, 'ACTIVE', 0)
ON DUPLICATE KEY UPDATE
  first_name = VALUES(first_name),
  last_name = VALUES(last_name),
  organization = VALUES(organization),
  email_verified = 1,
  status = 'ACTIVE',
  is_admin = 0;

INSERT INTO es_topic_space
  (space_code, space_name, description, visibility, display_order, is_active)
SELECT 'guide-private-test', 'Guide Private Test',
  'Private Topic Space for Topic Reference visibility checks. Local only.',
  'PRIVATE', 1000, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM es_topic_space WHERE space_code = 'guide-private-test');

SET @private_space_id = (SELECT es_topic_space_id FROM es_topic_space WHERE space_code = 'guide-private-test');

INSERT INTO es_topic
  (created_at, created_by_user_id, priority_cdc, priority_ehr, priority_iis, status,
   topic_code, topic_name, updated_at, es_topic_space_id)
SELECT NOW(6), @author_id, 0, 0, 0, 'ACTIVE', 'GUIDE-PRIVATE-TEST', 'Guide Private Test Topic',
  NOW(6), @private_space_id
FROM DUAL
WHERE @private_space_id IS NOT NULL
  AND @author_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM es_topic WHERE topic_code = 'GUIDE-PRIVATE-TEST');

SET @private_topic_id = (SELECT es_topic_id FROM es_topic WHERE topic_code = 'GUIDE-PRIVATE-TEST');

INSERT INTO es_guide_document
  (document_key, es_topic_id, title, is_active, created_by_user_id, created_at, updated_at)
SELECT 'guide-private-test', @private_topic_id, 'Guide Private Test', 1, @author_id, NOW(6), NOW(6)
FROM DUAL
WHERE @private_topic_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM es_guide_document WHERE document_key = 'guide-private-test');

SET @private_document_id = (SELECT guide_document_id FROM es_guide_document WHERE document_key = 'guide-private-test');

-- Not localhost:8002. That port is the unapproved origin in the test plan.
INSERT INTO app_redirect_allowlist (app_id, base_url, is_enabled)
SELECT @topic_reference_id, 'http://127.0.0.1:8099/', 1
FROM DUAL
WHERE @topic_reference_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM app_redirect_allowlist
    WHERE app_id = @topic_reference_id AND base_url = 'http://127.0.0.1:8099/'
  );

SET @private_allow_id = (
  SELECT allow_id FROM app_redirect_allowlist
  WHERE app_id = @topic_reference_id AND base_url = 'http://127.0.0.1:8099/'
);

INSERT INTO es_guide_document_url
  (guide_document_id, url, label, sort_order, allow_id, owns_allowlist)
SELECT @private_document_id, 'http://127.0.0.1:8099/', 'Local', 0, @private_allow_id, 1
FROM DUAL
WHERE @private_document_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM es_guide_document_url WHERE url = 'http://127.0.0.1:8099/');

-- Pilot registration. The allowlist row is the one H1 inserts above, so this
-- registration links to it and does not claim to own it.
INSERT INTO es_guide_document
  (document_key, es_topic_id, title, is_active, created_by_user_id, created_at, updated_at)
SELECT 'contextual-conditions', 7, 'Contextual Conditions', 1, @author_id, NOW(6), NOW(6)
FROM DUAL
WHERE @author_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM es_topic WHERE es_topic_id = 7)
  AND NOT EXISTS (SELECT 1 FROM es_guide_document WHERE document_key = 'contextual-conditions');

SET @pilot_document_id = (SELECT guide_document_id FROM es_guide_document WHERE document_key = 'contextual-conditions');
SET @pilot_allow_id = (
  SELECT allow_id FROM app_redirect_allowlist
  WHERE app_id = @topic_reference_id AND base_url = 'http://localhost:8001/'
);

INSERT INTO es_guide_document_url
  (guide_document_id, url, label, sort_order, allow_id, owns_allowlist)
SELECT @pilot_document_id, 'http://localhost:8001/', 'Local', 0, @pilot_allow_id, 0
FROM DUAL
WHERE @pilot_document_id IS NOT NULL
  AND @pilot_allow_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM es_guide_document_url WHERE url = 'http://localhost:8001/');
