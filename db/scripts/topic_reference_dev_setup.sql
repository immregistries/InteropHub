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

-- H3: one comment in each state, a reply, and one active flag, so the topic
-- page panel and the comment management page have something to show after a
-- refresh. source_note marks the rows so this stays idempotent.
SET @reader_id = (SELECT user_id FROM auth_user WHERE email_normalized = 'reader.guide-test@example.invalid');

INSERT INTO es_guide_comment
  (guide_document_id, anchor_id, block_index, block_snippet, comment_text, state, user_id, source_note,
   created_at, updated_at)
SELECT @pilot_document_id, 'secure-transport', 0, 'Each connection is authenticated with mutual TLS.',
  'Should this say which certificate authorities are acceptable?', 'OPEN', @reader_id, 'dev-setup:open',
  NOW(6), NOW(6)
FROM DUAL
WHERE @pilot_document_id IS NOT NULL AND @reader_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM es_guide_comment
    WHERE guide_document_id = @pilot_document_id AND source_note = 'dev-setup:open'
  );

INSERT INTO es_guide_comment
  (guide_document_id, anchor_id, block_index, block_snippet, comment_text, state, disposition_code, change_note,
   user_id, source_note, created_at, updated_at, disposed_at, disposed_by_user_id)
SELECT @pilot_document_id, 'consent-requirements', 0, 'Consent is recorded before the exchange.',
  'Please name the consent framework.', 'DISPOSED', 'PERSUASIVE',
  'Language updated to point to the trust framework.', @reader_id, 'dev-setup:disposed',
  NOW(6), NOW(6), NOW(6), @author_id
FROM DUAL
WHERE @pilot_document_id IS NOT NULL AND @reader_id IS NOT NULL AND @author_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM es_guide_comment
    WHERE guide_document_id = @pilot_document_id AND source_note = 'dev-setup:disposed'
  );

INSERT INTO es_guide_comment
  (guide_document_id, anchor_id, block_index, block_snippet, comment_text, state, disposition_code, change_note,
   user_id, source_note, created_at, updated_at, disposed_at, disposed_by_user_id, closed_at, closed_by_user_id)
SELECT @pilot_document_id, 'secure-transport', 1, 'Certificates are rotated on a schedule.',
  'How often is rotation required?', 'CLOSED', 'QUESTION_ANSWERED', 'Answered in the operations section.',
  @reader_id, 'dev-setup:closed', NOW(6), NOW(6), NOW(6), @author_id, NOW(6), @author_id
FROM DUAL
WHERE @pilot_document_id IS NOT NULL AND @reader_id IS NOT NULL AND @author_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM es_guide_comment
    WHERE guide_document_id = @pilot_document_id AND source_note = 'dev-setup:closed'
  );

INSERT INTO es_guide_comment
  (guide_document_id, anchor_id, block_index, block_snippet, comment_text, state, user_id, source_note,
   created_at, updated_at, withdrawn_at)
SELECT @pilot_document_id, 'consent-requirements', 1, 'A patient may decline.',
  'Withdrawn example for local review.', 'WITHDRAWN', @reader_id, 'dev-setup:withdrawn',
  NOW(6), NOW(6), NOW(6)
FROM DUAL
WHERE @pilot_document_id IS NOT NULL AND @reader_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM es_guide_comment
    WHERE guide_document_id = @pilot_document_id AND source_note = 'dev-setup:withdrawn'
  );

SET @open_comment_id = (
  SELECT guide_comment_id FROM es_guide_comment
  WHERE guide_document_id = @pilot_document_id AND source_note = 'dev-setup:open'
);

INSERT INTO es_guide_comment
  (guide_document_id, parent_comment_id, comment_text, state, user_id, source_note, created_at, updated_at)
SELECT @pilot_document_id, @open_comment_id, 'The framework already lists them.', 'OPEN', @author_id,
  'dev-setup:reply', NOW(6), NOW(6)
FROM DUAL
WHERE @pilot_document_id IS NOT NULL AND @open_comment_id IS NOT NULL AND @author_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM es_guide_comment
    WHERE guide_document_id = @pilot_document_id AND source_note = 'dev-setup:reply'
  );

-- H5: a HUB document on the private test topic. Author can edit it (space
-- admin and champion). Reader can view it (space member) and cannot edit.
-- Outsider is signed in and cannot view the private space. No guide HTML
-- is inserted; Gate 3H uploads it.
INSERT INTO es_topic_space_member (es_topic_space_id, user_id, role, created_at, updated_at)
SELECT @private_space_id, @author_id, 'ADMIN', NOW(), NOW()
FROM DUAL
WHERE @private_space_id IS NOT NULL AND @author_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM es_topic_space_member
    WHERE es_topic_space_id = @private_space_id AND user_id = @author_id
  );

INSERT INTO es_topic_space_member (es_topic_space_id, user_id, role, created_at, updated_at)
SELECT @private_space_id, @reader_id, 'MEMBER', NOW(), NOW()
FROM DUAL
WHERE @private_space_id IS NOT NULL AND @reader_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM es_topic_space_member
    WHERE es_topic_space_id = @private_space_id AND user_id = @reader_id
  );

INSERT INTO es_subscription
  (created_at, email, email_normalized, es_topic_id, status, subscription_type, updated_at, user_id,
   contact_first_name, contact_last_name, contact_organization)
SELECT UTC_TIMESTAMP(6), 'author.guide-test@example.invalid', 'author.guide-test@example.invalid', @private_topic_id,
  'CHAMPION', 'TOPIC', UTC_TIMESTAMP(6), @author_id, 'Guide', 'Author', 'Example Health Department'
FROM DUAL
WHERE @private_topic_id IS NOT NULL AND @author_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM es_subscription
    WHERE email_normalized = 'author.guide-test@example.invalid'
      AND es_topic_id = @private_topic_id
      AND subscription_type = 'TOPIC'
  );

INSERT INTO es_guide_document
  (document_key, es_topic_id, title, is_active, hosting, created_by_user_id, created_at, updated_at)
SELECT 'guide-hosted-test', @private_topic_id, 'Guide Hosted Test', 1, 'HUB', @author_id, NOW(6), NOW(6)
FROM DUAL
WHERE @private_topic_id IS NOT NULL AND @author_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM es_guide_document WHERE document_key = 'guide-hosted-test');

UPDATE es_guide_document
SET hosting = 'HUB', is_active = 1, es_topic_id = @private_topic_id
WHERE document_key = 'guide-hosted-test'
  AND @private_topic_id IS NOT NULL;

SET @hosted_document_id = (SELECT guide_document_id FROM es_guide_document WHERE document_key = 'guide-hosted-test');

-- One prefix covers every label. Per-label rows from the first H5 setup are turned off.
INSERT INTO app_redirect_allowlist (app_id, base_url, is_enabled)
SELECT @topic_reference_id, 'http://localhost:8080/hub/guide/view/guide-hosted-test/', 1
FROM DUAL
WHERE @topic_reference_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM app_redirect_allowlist
    WHERE app_id = @topic_reference_id
      AND base_url = 'http://localhost:8080/hub/guide/view/guide-hosted-test/'
  );

UPDATE app_redirect_allowlist
SET is_enabled = 0
WHERE app_id = @topic_reference_id
  AND base_url IN (
    'http://localhost:8080/hub/guide/view/guide-hosted-test/latest/',
    'http://localhost:8080/hub/guide/view/guide-hosted-test/main/'
  );

SET @hosted_prefix_allow_id = (
  SELECT allow_id FROM app_redirect_allowlist
  WHERE app_id = @topic_reference_id
    AND base_url = 'http://localhost:8080/hub/guide/view/guide-hosted-test/'
);

INSERT INTO es_guide_document_url
  (guide_document_id, url, label, sort_order, allow_id, owns_allowlist)
SELECT @hosted_document_id, 'http://localhost:8080/hub/guide/view/guide-hosted-test/latest/', 'latest', 0,
  @hosted_prefix_allow_id, 1
FROM DUAL
WHERE @hosted_document_id IS NOT NULL AND @hosted_prefix_allow_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM es_guide_document_url
    WHERE url = 'http://localhost:8080/hub/guide/view/guide-hosted-test/latest/'
  );

INSERT INTO es_guide_document_url
  (guide_document_id, url, label, sort_order, allow_id, owns_allowlist)
SELECT @hosted_document_id, 'http://localhost:8080/hub/guide/view/guide-hosted-test/main/', 'main', 1,
  @hosted_prefix_allow_id, 0
FROM DUAL
WHERE @hosted_document_id IS NOT NULL AND @hosted_prefix_allow_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM es_guide_document_url
    WHERE url = 'http://localhost:8080/hub/guide/view/guide-hosted-test/main/'
  );

UPDATE es_guide_document_url
SET allow_id = @hosted_prefix_allow_id, owns_allowlist = 1
WHERE guide_document_id = @hosted_document_id
  AND url = 'http://localhost:8080/hub/guide/view/guide-hosted-test/latest/'
  AND @hosted_prefix_allow_id IS NOT NULL;

UPDATE es_guide_document_url
SET allow_id = @hosted_prefix_allow_id, owns_allowlist = 0
WHERE guide_document_id = @hosted_document_id
  AND url = 'http://localhost:8080/hub/guide/view/guide-hosted-test/main/'
  AND @hosted_prefix_allow_id IS NOT NULL;

INSERT INTO es_guide_review_flag
  (guide_document_id, anchor_id, note, created_by_user_id, created_at, updated_at)
SELECT @pilot_document_id, 'consent-requirements', 'Please review: rewrote the consent requirements.',
  @author_id, NOW(6), NOW(6)
FROM DUAL
WHERE @pilot_document_id IS NOT NULL AND @author_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM es_guide_review_flag
    WHERE guide_document_id = @pilot_document_id AND anchor_id = 'consent-requirements' AND cleared_at IS NULL
  );
