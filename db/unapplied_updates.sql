-- Pending schema/data changes for the next production release.
-- Process, conventions, and how to fold local admin/UI edits (e.g. Topic
-- Board layout changes made in the running app) back into this file before
-- a refresh discards them: see docs/database-release-practice.md.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Shared file storage, Communication Bundles task 1a.
-- Apply once, after v0.8. Verify these endpoint/container literals match the
-- former demo configuration BEFORE migrating existing rows. Never put a SAS here.
CREATE TABLE hub_stored_file (
  stored_file_id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  storage_backend VARCHAR(10) NOT NULL,
  storage_key VARCHAR(64) NOT NULL,
  blob_endpoint VARCHAR(500) DEFAULT NULL,
  blob_container VARCHAR(63) DEFAULT NULL,
  original_filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL,
  uploaded_by_user_id BIGINT NOT NULL,
  uploaded_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  revision BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (stored_file_id),
  UNIQUE KEY uq_hub_stored_file_public_id (public_id),
  CONSTRAINT fk_hub_stored_file_uploader FOREIGN KEY (uploaded_by_user_id)
    REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE es_artifact_demo ADD COLUMN stored_file_id BIGINT DEFAULT NULL;

INSERT INTO hub_stored_file (
  public_id, storage_backend, storage_key, blob_endpoint, blob_container,
  original_filename, content_type, size_bytes, uploaded_by_user_id, uploaded_at, created_at
)
SELECT UUID(), 'BLOB', object_key,
  'https://testsabbiastorage.blob.core.windows.net', 'artifacts',
  original_filename, content_type, size_bytes, uploaded_by_user_id, uploaded_at, created_at
FROM es_artifact_demo;

UPDATE es_artifact_demo demo JOIN hub_stored_file file
  ON file.storage_backend = 'BLOB' AND file.storage_key = demo.object_key
SET demo.stored_file_id = file.stored_file_id;

-- Keep the old Blob association and Welcome fallback; new demo uploads are local.
UPDATE es_artifact_demo SET slot_key = 'WELCOME_BLOB_DEMO' WHERE slot_key = 'WELCOME_DEMO';

ALTER TABLE es_artifact_demo
  MODIFY stored_file_id BIGINT NOT NULL,
  ADD CONSTRAINT fk_es_artifact_demo_file FOREIGN KEY (stored_file_id)
    REFERENCES hub_stored_file (stored_file_id),
  DROP FOREIGN KEY fk_es_artifact_demo_uploaded_by,
  DROP COLUMN object_key,
  DROP COLUMN original_filename,
  DROP COLUMN content_type,
  DROP COLUMN size_bytes,
  DROP COLUMN uploaded_by_user_id,
  DROP COLUMN uploaded_at,
  DROP COLUMN created_at,
  DROP COLUMN updated_at;

-- Meeting agenda attachments, Communication Bundles task 1b.
ALTER TABLE hub_stored_file
  ADD COLUMN download_only BIT NOT NULL DEFAULT b'0';

CREATE TABLE es_meeting_agenda_attachment (
  attachment_id BIGINT NOT NULL AUTO_INCREMENT,
  es_meeting_agenda_item_id BIGINT NOT NULL,
  stored_file_id BIGINT NOT NULL,
  attached_by_user_id BIGINT NOT NULL,
  attached_at DATETIME(6) NOT NULL,
  removed_by_user_id BIGINT DEFAULT NULL,
  removed_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (attachment_id),
  UNIQUE KEY uq_meeting_attachment_file (stored_file_id),
  KEY ix_meeting_attachment_item (es_meeting_agenda_item_id, removed_at, attached_at, attachment_id),
  CONSTRAINT fk_meeting_attachment_item FOREIGN KEY (es_meeting_agenda_item_id)
    REFERENCES es_meeting_agenda_item (es_meeting_agenda_item_id),
  CONSTRAINT fk_meeting_attachment_file FOREIGN KEY (stored_file_id)
    REFERENCES hub_stored_file (stored_file_id),
  CONSTRAINT fk_meeting_attachment_uploader FOREIGN KEY (attached_by_user_id)
    REFERENCES auth_user (user_id),
  CONSTRAINT fk_meeting_attachment_remover FOREIGN KEY (removed_by_user_id)
    REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Communication Bundles Phase 2: Topic Resources and minimum Orientation structure.
CREATE TABLE es_topic_resource (
  topic_resource_id BIGINT NOT NULL AUTO_INCREMENT,
  es_topic_id BIGINT NOT NULL,
  stored_file_id BIGINT DEFAULT NULL,
  resource_type VARCHAR(24) NOT NULL,
  external_url VARCHAR(2000) DEFAULT NULL,
  title VARCHAR(255) NOT NULL,
  description TEXT DEFAULT NULL,
  attribution VARCHAR(500) DEFAULT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_by_user_id BIGINT NOT NULL,
  updated_by_user_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (topic_resource_id),
  UNIQUE KEY uq_topic_resource_stored_file (stored_file_id),
  UNIQUE KEY uq_topic_resource_topic (topic_resource_id, es_topic_id),
  KEY ix_topic_resource_topic_status (es_topic_id, status, created_at, topic_resource_id),
  CONSTRAINT fk_topic_resource_topic FOREIGN KEY (es_topic_id)
    REFERENCES es_topic (es_topic_id),
  CONSTRAINT fk_topic_resource_file FOREIGN KEY (stored_file_id)
    REFERENCES hub_stored_file (stored_file_id),
  CONSTRAINT fk_topic_resource_creator FOREIGN KEY (created_by_user_id)
    REFERENCES auth_user (user_id),
  CONSTRAINT fk_topic_resource_updater FOREIGN KEY (updated_by_user_id)
    REFERENCES auth_user (user_id),
  CONSTRAINT chk_topic_resource_type CHECK (
    resource_type IN ('IMAGE', 'PDF', 'DOCUMENT', 'PRESENTATION', 'EXTERNAL_LINK')
  ),
  CONSTRAINT chk_topic_resource_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
  CONSTRAINT chk_topic_resource_source CHECK (
    (stored_file_id IS NOT NULL AND external_url IS NULL AND resource_type <> 'EXTERNAL_LINK')
    OR
    (stored_file_id IS NULL AND external_url IS NOT NULL AND resource_type = 'EXTERNAL_LINK')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE es_communication_bundle_purpose (
  purpose_id BIGINT NOT NULL AUTO_INCREMENT,
  purpose_key VARCHAR(80) NOT NULL,
  display_name VARCHAR(140) NOT NULL,
  description TEXT DEFAULT NULL,
  mode VARCHAR(16) NOT NULL,
  instance_policy VARCHAR(16) NOT NULL,
  default_audience VARCHAR(16) NOT NULL,
  active_template_id BIGINT DEFAULT NULL,
  is_active BIT NOT NULL DEFAULT b'1',
  PRIMARY KEY (purpose_id),
  UNIQUE KEY uq_bundle_purpose_key (purpose_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE es_communication_bundle_template (
  template_id BIGINT NOT NULL AUTO_INCREMENT,
  purpose_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  created_by_user_id BIGINT DEFAULT NULL,
  PRIMARY KEY (template_id),
  UNIQUE KEY uq_bundle_template_version (purpose_id, version_no),
  UNIQUE KEY uq_bundle_template_purpose (template_id, purpose_id),
  CONSTRAINT fk_bundle_template_purpose FOREIGN KEY (purpose_id)
    REFERENCES es_communication_bundle_purpose (purpose_id),
  CONSTRAINT fk_bundle_template_creator FOREIGN KEY (created_by_user_id)
    REFERENCES auth_user (user_id),
  CONSTRAINT chk_bundle_template_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE es_communication_bundle_purpose
  ADD CONSTRAINT fk_bundle_purpose_active_template FOREIGN KEY (active_template_id)
    REFERENCES es_communication_bundle_template (template_id);

CREATE TABLE es_communication_bundle_template_component (
  component_id BIGINT NOT NULL AUTO_INCREMENT,
  template_id BIGINT NOT NULL,
  semantic_key VARCHAR(80) NOT NULL,
  display_name VARCHAR(140) NOT NULL,
  authoring_prompt TEXT DEFAULT NULL,
  component_kind VARCHAR(24) NOT NULL,
  is_required BIT NOT NULL DEFAULT b'0',
  cardinality VARCHAR(16) NOT NULL,
  display_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (component_id),
  UNIQUE KEY uq_bundle_component_key (template_id, semantic_key),
  UNIQUE KEY uq_bundle_component_template (component_id, template_id),
  KEY ix_bundle_component_order (template_id, display_order, component_id),
  CONSTRAINT fk_bundle_component_template FOREIGN KEY (template_id)
    REFERENCES es_communication_bundle_template (template_id),
  CONSTRAINT chk_bundle_component_kind CHECK (
    component_kind IN ('TEXT', 'STRUCTURED_LIST', 'RESOURCE', 'RESOURCE_COLLECTION')
  ),
  CONSTRAINT chk_bundle_component_cardinality CHECK (cardinality IN ('SINGLE', 'REPEATING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE es_communication_bundle (
  bundle_id BIGINT NOT NULL AUTO_INCREMENT,
  es_topic_id BIGINT NOT NULL,
  purpose_id BIGINT NOT NULL,
  template_id BIGINT NOT NULL,
  single_instance_guard INT DEFAULT NULL,
  status VARCHAR(16) NOT NULL,
  audience_scope VARCHAR(16) NOT NULL,
  communication_month INT DEFAULT NULL,
  communication_year INT DEFAULT NULL,
  created_by_user_id BIGINT NOT NULL,
  updated_by_user_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  published_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (bundle_id),
  UNIQUE KEY uq_bundle_single_instance (es_topic_id, purpose_id, single_instance_guard),
  UNIQUE KEY uq_bundle_id_template (bundle_id, template_id),
  UNIQUE KEY uq_bundle_template_purpose (bundle_id, purpose_id, template_id),
  UNIQUE KEY uq_bundle_topic_template (bundle_id, es_topic_id, template_id),
  KEY ix_bundle_topic_purpose (es_topic_id, purpose_id),
  CONSTRAINT fk_bundle_topic FOREIGN KEY (es_topic_id)
    REFERENCES es_topic (es_topic_id),
  CONSTRAINT fk_bundle_purpose FOREIGN KEY (purpose_id)
    REFERENCES es_communication_bundle_purpose (purpose_id),
  CONSTRAINT fk_bundle_template FOREIGN KEY (template_id, purpose_id)
    REFERENCES es_communication_bundle_template (template_id, purpose_id),
  CONSTRAINT fk_bundle_creator FOREIGN KEY (created_by_user_id)
    REFERENCES auth_user (user_id),
  CONSTRAINT fk_bundle_updater FOREIGN KEY (updated_by_user_id)
    REFERENCES auth_user (user_id),
  CONSTRAINT chk_bundle_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
  CONSTRAINT chk_bundle_audience CHECK (audience_scope IN ('PUBLIC', 'PARTICIPANTS', 'STEWARDS')),
  CONSTRAINT chk_bundle_single_guard CHECK (single_instance_guard IS NULL OR single_instance_guard = 1),
  CONSTRAINT chk_bundle_communication_date CHECK (
    (communication_month IS NULL AND communication_year IS NULL)
    OR
    (communication_month IS NOT NULL AND communication_year IS NOT NULL
      AND communication_month BETWEEN 1 AND 12 AND communication_year BETWEEN 1 AND 9999)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE es_communication_bundle_resource_placement (
  placement_id BIGINT NOT NULL AUTO_INCREMENT,
  bundle_id BIGINT NOT NULL,
  es_topic_id BIGINT NOT NULL,
  template_id BIGINT NOT NULL,
  component_id BIGINT NOT NULL,
  topic_resource_id BIGINT NOT NULL,
  display_order INT NOT NULL DEFAULT 0,
  context_note TEXT DEFAULT NULL,
  created_by_user_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (placement_id),
  UNIQUE KEY uq_bundle_component_order (bundle_id, component_id, display_order),
  KEY ix_bundle_placement_resource (topic_resource_id),
  CONSTRAINT fk_bundle_placement_bundle FOREIGN KEY (bundle_id, es_topic_id, template_id)
    REFERENCES es_communication_bundle (bundle_id, es_topic_id, template_id),
  CONSTRAINT fk_bundle_placement_component FOREIGN KEY (component_id, template_id)
    REFERENCES es_communication_bundle_template_component (component_id, template_id),
  CONSTRAINT fk_bundle_placement_resource FOREIGN KEY (topic_resource_id, es_topic_id)
    REFERENCES es_topic_resource (topic_resource_id, es_topic_id),
  CONSTRAINT fk_bundle_placement_creator FOREIGN KEY (created_by_user_id)
    REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE es_communication_bundle_component_value (
  component_value_id BIGINT NOT NULL AUTO_INCREMENT,
  bundle_id BIGINT NOT NULL,
  template_id BIGINT NOT NULL,
  component_id BIGINT NOT NULL,
  content_text LONGTEXT DEFAULT NULL,
  content_json LONGTEXT DEFAULT NULL,
  updated_by_user_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (component_value_id),
  UNIQUE KEY uq_bundle_component_value (bundle_id, component_id),
  CONSTRAINT fk_bundle_value_bundle FOREIGN KEY (bundle_id, template_id)
    REFERENCES es_communication_bundle (bundle_id, template_id),
  CONSTRAINT fk_bundle_value_component FOREIGN KEY (component_id, template_id)
    REFERENCES es_communication_bundle_template_component (component_id, template_id),
  CONSTRAINT fk_bundle_value_updater FOREIGN KEY (updated_by_user_id)
    REFERENCES auth_user (user_id),
  CONSTRAINT chk_bundle_component_value CHECK (
    content_text IS NULL OR content_json IS NULL
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO es_communication_bundle_purpose (
  purpose_key, display_name, description, mode, instance_policy, default_audience
) VALUES (
  'TOPIC_ORIENTATION', 'Topic Orientation',
  'The current introduction to a Topic: what it is, why it matters, and how to engage.',
  'LIVING', 'SINGLE', 'PUBLIC'
);

INSERT INTO es_communication_bundle_template (
  purpose_id, version_no, status, created_at
)
SELECT purpose_id, 1, 'ACTIVE', UTC_TIMESTAMP(6)
FROM es_communication_bundle_purpose
WHERE purpose_key = 'TOPIC_ORIENTATION';

UPDATE es_communication_bundle_purpose purpose
JOIN es_communication_bundle_template template
  ON template.purpose_id = purpose.purpose_id AND template.version_no = 1
SET purpose.active_template_id = template.template_id
WHERE purpose.purpose_key = 'TOPIC_ORIENTATION';

INSERT INTO es_communication_bundle_template_component (
  template_id, semantic_key, display_name, authoring_prompt, component_kind,
  is_required, cardinality, display_order
)
SELECT template.template_id, component.semantic_key, component.display_name,
  component.authoring_prompt, component.component_kind, component.is_required,
  component.cardinality, component.display_order
FROM es_communication_bundle_template template
JOIN es_communication_bundle_purpose purpose ON purpose.purpose_id = template.purpose_id
CROSS JOIN (
  SELECT 'introduction' AS semantic_key, 'What this Topic is' AS display_name,
    'Explain what the Topic is and what this Orientation is for.' AS authoring_prompt,
    'TEXT' AS component_kind, b'0' AS is_required, 'SINGLE' AS cardinality, 10 AS display_order
  UNION ALL SELECT 'why_it_matters', 'Why it matters',
    'Explain the need or opportunity this Topic addresses.', 'TEXT', b'0', 'SINGLE', 20
  UNION ALL SELECT 'primary_infographic', 'Infographic',
    'Select the primary visual introduction, if one exists.', 'RESOURCE', b'0', 'SINGLE', 30
  UNION ALL SELECT 'one_pager', 'One-pager',
    'Select a concise written introduction, if one exists.', 'RESOURCE', b'0', 'SINGLE', 40
  UNION ALL SELECT 'primary_presentation', 'Primary presentation',
    'Select the presentation that best introduces the Topic.', 'RESOURCE', b'0', 'SINGLE', 50
  UNION ALL SELECT 'additional_resources', 'Additional resources',
    'Add useful supporting material in the order a reader should explore it.',
    'RESOURCE_COLLECTION', b'0', 'REPEATING', 60
  UNION ALL SELECT 'how_to_get_involved', 'How to get involved',
    'Explain how interested people can participate or contribute.', 'TEXT', b'0', 'SINGLE', 70
) component
WHERE purpose.purpose_key = 'TOPIC_ORIENTATION' AND template.version_no = 1;

-- IVC historical meeting backload (June 2023 - May 2026).
-- Task: docs/tasks/backload-ivc-meetings.md. Manifest and decisions:
-- docs/tasks/ivc-historical-meetings-manifest.md. File UUID mapping:
-- docs/tasks/ivc-historical-meetings-files.tsv.
-- Adds 30 CLOSED meetings to the existing IVC series, 143 agenda items,
-- and 57 presentation attachments. Every hub_stored_file row is LOCAL; its bytes
-- must already be in INTEROPHUB_ARTIFACTS_LOCAL_DIRECTORY under storage_key BEFORE this
-- runs (copied from C:\dev\immregistries\InteropHub-artifacts).
-- Two decks exceed the 25 MiB upload cap as approved exceptions (2025-06-11, 2025-07-09);
-- downloads only check that size matches metadata.
-- Rerunnable: does nothing if the backload is already complete; fails (and rolls back)
-- on a partial prior run, unresolved lookups, or any other pre-2026-06 IVC meeting.
-- Start times are nominal: 10:00 America/New_York (Spanish same-day meetings 09:00).

DROP TEMPORARY TABLE IF EXISTS tmp_ivc_hist_meeting;
CREATE TEMPORARY TABLE tmp_ivc_hist_meeting (
  meeting_ref VARCHAR(20) NOT NULL PRIMARY KEY,
  meeting_name VARCHAR(160) NOT NULL,
  scheduled_start DATETIME(6) NOT NULL,
  is_monthly BIT NOT NULL,
  es_meeting_id BIGINT DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_ivc_hist_meeting (meeting_ref, meeting_name, scheduled_start, is_monthly) VALUES
  ('2023-06-14 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2023-06-14 10:00:00', 1),
  ('2023-07-12 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2023-07-12 10:00:00', 1),
  ('2023-08-09 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2023-08-09 10:00:00', 1),
  ('2023-09-20 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2023-09-20 10:00:00', 1),
  ('2023-10-11 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2023-10-11 10:00:00', 1),
  ('2023-12-13 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2023-12-13 10:00:00', 1),
  ('2024-03-20 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2024-03-20 10:00:00', 1),
  ('2024-04-17 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2024-04-17 10:00:00', 1),
  ('2024-06-12 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2024-06-12 10:00:00', 1),
  ('2024-07-31 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2024-07-31 10:00:00', 1),
  ('2024-10-09 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2024-10-09 10:00:00', 1),
  ('2024-11-27 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2024-11-27 10:00:00', 1),
  ('2025-01-08 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2025-01-08 10:00:00', 1),
  ('2025-03-12 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2025-03-12 10:00:00', 1),
  ('2025-03-12 09:00', 'IVC en español — Introducción a los códigos de vacunas', '2025-03-12 09:00:00', 0),
  ('2025-04-09 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2025-04-09 10:00:00', 1),
  ('2025-04-09 09:00', 'IVC en español — IVC y NUVA', '2025-04-09 09:00:00', 0),
  ('2025-05-08 10:00', 'IVC Vaccine Code Training — Bordeaux', '2025-05-08 10:00:00', 0),
  ('2025-05-09 10:00', 'International Summit on Vaccine Coding & Standards — Bordeaux', '2025-05-09 10:00:00', 0),
  ('2025-06-11 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2025-06-11 10:00:00', 1),
  ('2025-07-09 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2025-07-09 10:00:00', 1),
  ('2025-07-23 10:00', 'IVC en español — Resumen de la Cumbre de Burdeos', '2025-07-23 10:00:00', 0),
  ('2025-09-10 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2025-09-10 10:00:00', 1),
  ('2025-10-08 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2025-10-08 10:00:00', 1),
  ('2025-11-12 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2025-11-12 10:00:00', 1),
  ('2025-12-10 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2025-12-10 10:00:00', 1),
  ('2026-01-14 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2026-01-14 10:00:00', 1),
  ('2026-02-11 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2026-02-11 10:00:00', 1),
  ('2026-03-11 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2026-03-11 10:00:00', 1),
  ('2026-05-13 10:00', 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting', '2026-05-13 10:00:00', 1);

DROP TEMPORARY TABLE IF EXISTS tmp_ivc_hist_item;
CREATE TEMPORARY TABLE tmp_ivc_hist_item (
  meeting_ref VARCHAR(20) NOT NULL,
  display_order INT NOT NULL,
  title VARCHAR(200) NOT NULL,
  agenda_markdown TEXT DEFAULT NULL,
  topic_space_code VARCHAR(80) DEFAULT NULL,
  topic_name VARCHAR(255) DEFAULT NULL,
  es_topic_id BIGINT DEFAULT NULL,
  PRIMARY KEY (meeting_ref, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_ivc_hist_item (meeting_ref, display_order, title, agenda_markdown, topic_space_code, topic_name) VALUES
  ('2023-06-14 10:00', 10, 'Welcome and Introductions', 'Welcome and introductions\nPurpose of call\nNathan Bunker & Rebecca Sandtveit, AIRA', NULL, NULL),
  ('2023-06-14 10:00', 11, 'Roundtable Updates', 'Updates from AIRA\nUpdates from Mes Vaccins', NULL, NULL),
  ('2023-06-14 10:00', 12, 'Gaps Analysis', 'Comparative analysis of existing vaccine code systems\nFrançois Kaag, Mes Vaccins', NULL, NULL),
  ('2023-06-14 10:00', 13, 'Wrap Up', 'Discussion, next steps', NULL, NULL),
  ('2023-07-12 10:00', 10, 'Welcome and Introductions', 'Purpose of call\nScope\nIntroductions', NULL, NULL),
  ('2023-07-12 10:00', 11, 'Roundtable Updates', 'Washington IIS\nNUVA (Mes Vaccins)\nNetherlands (RIVM)\nAIRA', NULL, NULL),
  ('2023-07-12 10:00', 12, 'Gap Analysis Review', 'Review gap analysis\nCollect feedback to give to Mes Vaccins', NULL, NULL),
  ('2023-07-12 10:00', 13, 'SMART Health Cards', 'SMART Health Cards terminology approach (MITRE)', 'emerging-standards', 'Digital Vaccine Cards (SMART Health Cards)'),
  ('2023-07-12 10:00', 14, 'Wrap Up', 'Discussion, next steps', NULL, NULL),
  ('2023-08-09 10:00', 10, 'Welcome and Introductions', 'Introductions\nScope of discussion', NULL, NULL),
  ('2023-08-09 10:00', 11, 'Roundtable Updates', 'Short updates from attendees', NULL, NULL),
  ('2023-08-09 10:00', 12, 'Vaccine Code Set Metrics', 'Nathan Bunker, AIRA', NULL, NULL),
  ('2023-08-09 10:00', 13, 'Wrap Up', 'Discussion, next steps', NULL, NULL),
  ('2023-09-20 10:00', 10, 'Welcome and Introductions', 'Welcome', NULL, NULL),
  ('2023-09-20 10:00', 11, 'WHODrug', 'Introduction to the WHODrug dictionary\nSalvador Alvarado, UMC', 'building-bridges', 'Uppsala Monitoring Centre and WHODrug'),
  ('2023-09-20 10:00', 12, 'Roundtable Updates', 'Short updates from attendees', NULL, NULL),
  ('2023-09-20 10:00', 13, 'Vaccine Code Set Metrics', 'Wiki introduction', NULL, NULL),
  ('2023-09-20 10:00', 14, 'Wrap Up', 'Discussion, next steps', NULL, NULL),
  ('2023-10-11 10:00', 10, 'Welcome and Introductions', 'Welcome', NULL, NULL),
  ('2023-10-11 10:00', 11, 'Roundtable Updates', 'Short updates from attendees', NULL, NULL),
  ('2023-10-11 10:00', 12, 'Vaccine Code Set Metrics', 'Nathan Bunker, AIRA', NULL, NULL),
  ('2023-10-11 10:00', 13, 'Wrap Up', 'Discussion, next steps', NULL, NULL),
  ('2023-12-13 10:00', 10, 'Welcome and Introductions', 'Welcome', NULL, NULL),
  ('2023-12-13 10:00', 11, 'Roundtable Updates', 'Short updates from attendees', NULL, NULL),
  ('2023-12-13 10:00', 12, 'CVX Update Management', 'AIRA update process\nNIST update process\nOthers', 'building-bridges', 'United States'),
  ('2023-12-13 10:00', 13, 'Wrap Up', 'Discussion, next steps', NULL, NULL),
  ('2024-03-20 10:00', 10, 'Welcome and Introductions', 'Welcome', NULL, NULL),
  ('2024-03-20 10:00', 11, 'Roundtable Updates', 'Short updates from attendees', NULL, NULL),
  ('2024-03-20 10:00', 12, 'CVX Metrics', NULL, NULL, NULL),
  ('2024-03-20 10:00', 13, 'Wrap Up', 'Discussion, next steps', NULL, NULL),
  ('2024-04-17 10:00', 10, 'Welcome and Introductions', 'Welcome', NULL, NULL),
  ('2024-04-17 10:00', 11, 'Roundtable Updates', 'Short updates from attendees', NULL, NULL),
  ('2024-04-17 10:00', 12, 'NUVA', 'One-page introduction\nNathan Bunker, AIRA; Nick MacDonald, HLN', 'emerging-standards', 'Unified Nomenclature of Vaccines (NUVA)'),
  ('2024-04-17 10:00', 13, 'Metrics', 'Continued discussion on automatically generating metrics of code systems\nFrançois Kaag, Mes Vaccins', NULL, NULL),
  ('2024-04-17 10:00', 14, 'Wrap Up', 'Discussion, next steps', NULL, NULL),
  ('2024-06-12 10:00', 10, 'Welcome and Introductions', 'Welcome', NULL, NULL),
  ('2024-06-12 10:00', 11, 'Roundtable Updates', 'Short updates from attendees', NULL, NULL),
  ('2024-06-12 10:00', 12, 'Next Year Planning', 'IVC and NUVA launch event\nNathan Bunker, AIRA; François Kaag, Mes Vaccins', NULL, NULL),
  ('2024-06-12 10:00', 13, 'Wrap Up', 'Discussion, next steps', NULL, NULL),
  ('2024-07-31 10:00', 10, 'Welcome and Introductions', 'Welcome', NULL, NULL),
  ('2024-07-31 10:00', 11, 'Roundtable Updates', 'Short updates from attendees', NULL, NULL),
  ('2024-07-31 10:00', 12, 'Vision & Goals', 'Website updated with current vision and goals\nSME and WHO interviews\nIVC and NUVA launch event', NULL, NULL),
  ('2024-07-31 10:00', 13, 'Metrics', 'Assessing CVX against abstract (generic) NUVA codes', NULL, NULL),
  ('2024-07-31 10:00', 14, 'Wrap Up', 'Discussion, next steps', NULL, NULL),
  ('2024-10-09 10:00', 10, 'Welcome and Introductions', 'Welcome', NULL, NULL),
  ('2024-10-09 10:00', 11, 'Roundtable Updates', 'Short updates from attendees', NULL, NULL),
  ('2024-10-09 10:00', 12, 'HL7 WGM Update', 'International Patient Summary\nVaccine codes and connectathon\nInternational standards\nSMART Health Links', 'building-bridges', 'HL7 International'),
  ('2024-10-09 10:00', 13, 'AI Discussions', 'Could AI help us organize vaccine codes?', NULL, NULL),
  ('2024-10-09 10:00', 14, 'Country and Project Interviews', 'Malawi, Tanzania, Ghana, Ireland\nCVX use outside the US\nImpact from lack of standards', 'emerging-standards', 'Building Bridges'),
  ('2024-10-09 10:00', 15, 'Wrap Up', 'Discussion, next steps\nIVC en Español\nBordeaux 2025\nSNOMED & WHO', NULL, NULL),
  ('2024-11-27 10:00', 10, 'Welcome and Introductions', 'Welcome', NULL, NULL),
  ('2024-11-27 10:00', 11, 'Roundtable Updates', 'Short updates from attendees', NULL, NULL),
  ('2024-11-27 10:00', 12, 'Country and Project Interviews', 'Report on interviews conducted', 'emerging-standards', 'Building Bridges'),
  ('2024-11-27 10:00', 13, 'Wrap Up', 'Discussion, next steps\nIVC en Español\nBordeaux 2025\nNumbers & data', NULL, NULL),
  ('2025-01-08 10:00', 10, 'Welcome and Introductions', 'Welcome', NULL, NULL),
  ('2025-01-08 10:00', 11, 'Roundtable Updates', 'Short updates from attendees', NULL, NULL),
  ('2025-01-08 10:00', 12, 'Country and Project Outreach', 'Outreach plan 2025', 'emerging-standards', 'Building Bridges'),
  ('2025-01-08 10:00', 13, 'Bordeaux 2025', 'Agenda planning', NULL, NULL),
  ('2025-01-08 10:00', 14, 'Wrap Up', 'Discussion, next steps\nIVC en Español', NULL, NULL),
  ('2025-03-12 10:00', 10, 'Welcome and Introductions', 'Welcome', NULL, NULL),
  ('2025-03-12 10:00', 11, 'Roundtable Updates', 'Short updates from attendees', NULL, NULL),
  ('2025-03-12 10:00', 12, 'Bordeaux 2025', 'Agenda planning', NULL, NULL),
  ('2025-03-12 10:00', 13, 'Wrap Up', 'Discussion, next steps', NULL, NULL),
  ('2025-03-12 09:00', 10, 'Bienvenida', 'Dinámica de apertura\nPresentación de facilitadora de la reunión\nNathan Bunker, AIRA', NULL, NULL),
  ('2025-03-12 09:00', 11, 'Introducción a los Conjuntos de Códigos de Vacunas', 'Orientación básica y capacitación introductoria sobre los conjuntos de códigos de vacunas\nAlejandra Arias, CT WiZ IIS; Nathan Bunker, AIRA', NULL, NULL),
  ('2025-03-12 09:00', 12, 'Conjuntos de Códigos de Medicamentos', 'Una descripción general de UMC, WHODrug Global y los estándares internacionales para la identificación de medicamentos y vacunas\nSalvador Alvarado López, Centro de Monitoreo de Uppsala', 'building-bridges', 'Uppsala Monitoring Centre and WHODrug'),
  ('2025-03-12 09:00', 13, 'Discusión sobre la Iniciativa de Codificación de Vacunas (IVC)', 'Resumen del grupo y la iniciativa\nAnálisis de desafíos y puntos críticos\nExplorar las oportunidades para participación', NULL, NULL),
  ('2025-03-12 09:00', 14, 'Cierre y Próximos Pasos', 'Discusión sobre los próximos pasos para el grupo\nConclusión y despedida', NULL, NULL),
  ('2025-04-09 10:00', 10, 'Welcome and Introductions', 'Welcome', NULL, NULL),
  ('2025-04-09 10:00', 11, 'Roundtable Updates', 'Short updates from attendees', NULL, NULL),
  ('2025-04-09 10:00', 12, 'Country Interviews', 'Update on recent conversations', 'emerging-standards', 'Building Bridges'),
  ('2025-04-09 10:00', 13, 'Bordeaux 2025', 'Agenda planning\nFrançois Kaag, Syadem; Nathan Bunker, AIRA', NULL, NULL),
  ('2025-04-09 10:00', 14, 'Wrap Up', 'Discussion, next steps', NULL, NULL),
  ('2025-04-09 09:00', 10, 'Bienvenida', 'Dinámica de apertura\nPresentación de facilitadora de la reunión', NULL, NULL),
  ('2025-04-09 09:00', 11, 'Introducción y Herramientas Clave de Codificación de Vacunas (IVC)', 'Resumen del grupo y la iniciativa\nAnálisis de desafíos y puntos críticos\nPresentación de NUVA como herramienta clave de interoperabilidad', NULL, NULL),
  ('2025-04-09 09:00', 12, 'Conversación Abierta con el Grupo', 'Escuchar sus experiencias y desafíos\nIdentificar temas prioritarios para próximos pasos\nReunir ideas sobre cómo IVC puede apoyar mejor a la región', NULL, NULL),
  ('2025-04-09 09:00', 13, 'Cierre y Próximos Pasos', 'Conclusión y despedida', NULL, NULL),
  ('2025-05-08 10:00', 10, 'IVC Vaccine Code Training', 'Vocabulary in health data exchange\nVaccine code systems\nNUVA: Unified Nomenclature for Vaccines\nPractical use of NUVA tools and resources', NULL, NULL),
  ('2025-05-09 10:00', 10, 'Goals of the Meeting and Importance of Standardized Vaccine Coding', 'François Kaag and Nathan Bunker', NULL, NULL),
  ('2025-05-09 10:00', 11, 'NUVA: What It Is and Why It Matters', 'François Kaag', 'emerging-standards', 'Unified Nomenclature of Vaccines (NUVA)'),
  ('2025-05-09 10:00', 12, 'How NUVA Uses Valences', 'Jean-Louis Koeck', 'emerging-standards', 'Unified Nomenclature of Vaccines (NUVA)'),
  ('2025-05-09 10:00', 13, 'NUVA Extension to SNOMED CT', 'Suzy Roy and Peter Williams', 'building-bridges', 'SNOMED International'),
  ('2025-05-09 10:00', 14, 'Industry View: Vaccine Codification and Access to Resources', 'Ingrid Weindorfer', 'building-bridges', 'Vaccine Manufacturers and Pharmaceutical Industry'),
  ('2025-05-09 10:00', 15, 'EU Strategy for Cross-Border Vaccination Records', 'Georgios Margetidis, HaDEA (delivered virtually, without slides)', 'building-bridges', 'European Commission, HaDEA, and EU4Health'),
  ('2025-05-09 10:00', 16, 'WHODrug and IDMP for Vaccines', 'Malin Fladvad, Uppsala Monitoring Centre', 'building-bridges', 'Uppsala Monitoring Centre and WHODrug'),
  ('2025-05-09 10:00', 17, 'Luxembourg Experience', 'Maud Delporte, Agence eSanté Luxembourg', 'building-bridges', 'Luxembourg'),
  ('2025-05-09 10:00', 18, 'EUVABECO Electronic Vaccination Card Project', 'Alain Cimino, Cimbiose', 'building-bridges', 'European Vaccination Card (EVC)'),
  ('2025-05-09 10:00', 19, 'United States Vaccine-Coding Experience', 'Shannon Coleman, STCHealth', 'building-bridges', 'United States'),
  ('2025-05-09 10:00', 20, 'Canadian Vaccine-Coding Experience', 'Myriam Talantikit, Canada Health Infoway', 'building-bridges', 'Canada'),
  ('2025-05-09 10:00', 21, 'Mapping Across Code Systems', 'Timothée Doulut, Syadem', NULL, NULL),
  ('2025-05-09 10:00', 22, 'Metrics for Code Systems', 'François Kaag', NULL, NULL),
  ('2025-05-09 10:00', 23, 'Long-Term Goals and Next Actions', 'Discussion\nNathan Bunker', NULL, NULL),
  ('2025-05-09 10:00', 24, 'Final Takeaways', 'Nathan Bunker', NULL, NULL),
  ('2025-06-11 10:00', 10, 'Welcome and Introductions', 'Welcome', NULL, NULL),
  ('2025-06-11 10:00', 11, 'Summit Report', 'Review of presentations and key discussions from the Bordeaux 2025 International Summit on Vaccine Coding and Standards\nNathan Bunker, AIRA; François Kaag, Syadem', NULL, NULL),
  ('2025-06-11 10:00', 12, 'Wrap Up', 'Next meetings planned (English & Spanish)', NULL, NULL),
  ('2025-07-09 10:00', 10, 'Welcome and Introductions', 'Welcome', NULL, NULL),
  ('2025-07-09 10:00', 11, 'Summit Report, Part 2', 'Continued review of presentations and key discussions from the Bordeaux 2025 summit\nAfternoon sessions and strategy session\nNathan Bunker, AIRA; François Kaag, Syadem', NULL, NULL),
  ('2025-07-09 10:00', 12, 'Wrap Up', 'Next meetings planned (English & Spanish)', NULL, NULL),
  ('2025-07-23 10:00', 10, 'Bienvenida', NULL, NULL, NULL),
  ('2025-07-23 10:00', 11, 'Progreso de 2025', NULL, NULL, NULL),
  ('2025-07-23 10:00', 12, 'Resumen de la Cumbre de Burdeos', NULL, NULL, NULL),
  ('2025-07-23 10:00', 13, 'Discusión y Comentarios', NULL, NULL, NULL),
  ('2025-07-23 10:00', 14, 'Cierre y Próximos Pasos', 'Próximas reuniones', NULL, NULL),
  ('2025-09-10 10:00', 10, 'Welcome and Introductions', 'Welcome\nIntroductions', NULL, NULL),
  ('2025-09-10 10:00', 11, 'Flu and COVID Coding', 'Review of NUVA mappings for flu and COVID and update on code efforts in the U.S.\nUpdates from others: how are you coding this season?', NULL, NULL),
  ('2025-09-10 10:00', 12, 'Year Ahead Brainstorming', 'IVC en Español meetings\nCosta Rica liaison\nNHS England engagement\nProcess for country interviews\nCo-chair recruitment\nNathan Bunker, AIRA; François Kaag, Syadem', NULL, NULL),
  ('2025-09-10 10:00', 13, 'Wrap Up', 'Testimonials about IVC\nReview of upcoming meetings', NULL, NULL),
  ('2025-10-08 10:00', 10, 'Welcome and Introductions', 'Welcome\nIntroductions', NULL, NULL),
  ('2025-10-08 10:00', 11, 'Vaccine Genie', 'International vaccine coding and translation\nEvelyn Fang and Sean Bennick, Vaccine Genie', NULL, NULL),
  ('2025-10-08 10:00', 12, 'Automatic Recognition of Paper Records', 'Mathieu Laporte, Syadem', NULL, NULL),
  ('2025-10-08 10:00', 13, 'Wrap Up', 'Next topic: NUVA training', NULL, NULL),
  ('2025-11-12 10:00', 10, 'Welcome and Introductions', 'Welcome\nIntroductions', NULL, NULL),
  ('2025-11-12 10:00', 11, 'Update on SNOMED CT Expo 2025', 'Nathan Bunker, AIRA', 'building-bridges', 'SNOMED International'),
  ('2025-11-12 10:00', 12, 'NUVA Training', 'François Kaag, Syadem', 'emerging-standards', 'Unified Nomenclature of Vaccines (NUVA)'),
  ('2025-11-12 10:00', 13, 'Wrap Up', 'Next topic: mapping information sheets and country interview process review', NULL, NULL),
  ('2025-12-10 10:00', 10, 'Welcome and Introductions', 'Welcome\nIntroductions', NULL, NULL),
  ('2025-12-10 10:00', 11, 'Vaccination Information Sheets', 'Nancy student project demonstration\nMaï Morvan, Gauthier Vignau-Sicard, Numa Bermond', NULL, NULL),
  ('2025-12-10 10:00', 12, 'Country Interview Process', 'Review of progress\nDiscussion about process', 'emerging-standards', 'Building Bridges'),
  ('2025-12-10 10:00', 13, 'Publications of Mappings', 'Explanation of new pre-built pivot tables\nFrançois Kaag, Syadem', 'emerging-standards', 'Unified Nomenclature of Vaccines (NUVA)'),
  ('2025-12-10 10:00', 14, 'Wrap Up', 'Next topic: experience with vaccination strategy, data gaps, MenB', NULL, NULL),
  ('2026-01-14 10:00', 10, 'Welcome and Introductions', 'Welcome\nIntroductions', NULL, NULL),
  ('2026-01-14 10:00', 11, 'NUVA Use Cases: France and Switzerland', 'Transcoding and interpreting vaccine histories\nFrançois Kaag, Syadem', 'emerging-standards', 'Unified Nomenclature of Vaccines (NUVA)'),
  ('2026-01-14 10:00', 12, 'SNOMED Discussion', 'Nathan Bunker, AIRA', 'building-bridges', 'SNOMED International'),
  ('2026-01-14 10:00', 13, 'United States Update', 'Nathan Bunker, AIRA', 'building-bridges', 'United States'),
  ('2026-01-14 10:00', 14, 'NUVA Search App', 'Demonstration of student app\nNancy Telecom students', 'emerging-standards', 'Unified Nomenclature of Vaccines (NUVA)'),
  ('2026-01-14 10:00', 15, 'Wrap Up', 'Next topics: meningococcal; notion of target disease', NULL, NULL),
  ('2026-02-11 10:00', 10, 'Welcome and Introductions', 'Welcome\nIntroductions', NULL, NULL),
  ('2026-02-11 10:00', 11, 'Country Updates', NULL, NULL, NULL),
  ('2026-02-11 10:00', 12, 'Meningococcal Introduction', 'Vaccinology concepts and specific formulations of vaccinations\nDr Jean-Louis Koeck, Syadem', NULL, NULL),
  ('2026-02-11 10:00', 13, 'Meningococcal Implementation', 'Roundtable discussion', NULL, NULL),
  ('2026-02-11 10:00', 14, 'Target Disease', 'Notion of target disease\nDifference between disease and pathogen\nFrançois Kaag, Syadem', 'emerging-standards', 'Unified Nomenclature of Vaccines (NUVA)'),
  ('2026-02-11 10:00', 15, 'Wrap Up', 'Next topics: country interviews; next vaccine topic', NULL, NULL),
  ('2026-03-11 10:00', 10, 'Welcome and Introductions', 'Welcome\nIntroductions', NULL, NULL),
  ('2026-03-11 10:00', 11, 'Country Updates', NULL, NULL, NULL),
  ('2026-03-11 10:00', 12, 'Meningococcal Discussion', 'Experience in France\nFrançois Kaag, Syadem', 'building-bridges', 'France'),
  ('2026-03-11 10:00', 13, 'Other Coded Values', 'Finding a home for coded concepts for vaccine schedule authority, codeable concepts, etc.', NULL, NULL),
  ('2026-03-11 10:00', 14, 'Wrap Up', 'Next topics: country interviews; next vaccine topic (pneumococcal, flu, MMR/MMRV)', NULL, NULL),
  ('2026-05-13 10:00', 10, 'Welcome and Introductions', 'Welcome\nIntroductions', NULL, NULL),
  ('2026-05-13 10:00', 11, 'Updates', 'Country updates\nCountry interviews', 'emerging-standards', 'Building Bridges'),
  ('2026-05-13 10:00', 12, 'Scope and Name Discussion', 'Relationship between IVC and interoperability standards\nThe term "International"', NULL, NULL),
  ('2026-05-13 10:00', 13, 'MMR/MMRV', 'Keying up topic for next time: schedule changes, managing spare stock, dose number implications', NULL, NULL),
  ('2026-05-13 10:00', 14, 'Wrap Up', 'Next topics: contextual conditions; schedule authority', NULL, NULL);

DROP TEMPORARY TABLE IF EXISTS tmp_ivc_hist_file;
CREATE TEMPORARY TABLE tmp_ivc_hist_file (
  meeting_ref VARCHAR(20) NOT NULL,
  display_order INT NOT NULL,
  public_id VARCHAR(36) NOT NULL PRIMARY KEY,
  storage_key VARCHAR(64) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_ivc_hist_file (meeting_ref, display_order, public_id, storage_key, original_filename, content_type, size_bytes) VALUES
  ('2023-06-14 10:00', 10, 'ef1e8b6d-f977-4760-8236-1b9638cd5632', '9b792021-a501-4e35-94d1-8bca1009ef20', 'International Vaccine Codes 2023-06-14.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 4525555),
  ('2023-06-14 10:00', 12, 'fd5747ed-fe79-46c8-a1fe-f68bb8211156', '2538d063-b386-4847-86d0-241f3a0f1194', '230614-AIRA  codes meeting.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 435310),
  ('2023-07-12 10:00', 10, 'fd26a179-7367-4010-ab8b-6e365886873c', '3b9bd7e1-34d4-4f04-9612-55e56ff9b1ae', 'International Vaccine Codes 2023-07-12.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 6118680),
  ('2023-07-12 10:00', 13, 'd573a657-faf6-491e-8071-d52a7edbdbbd', '2d7d047d-9881-4328-945b-f4354b82dc93', '2023-07-12 SHC Terminology Approach.pdf', 'application/pdf', 2524173),
  ('2023-08-09 10:00', 10, '28a1e6d3-e4eb-4c38-94ee-95ee869e5b59', 'b5bc2540-07d9-4707-b83b-6a6212b5d649', 'International Vaccine Codes 2023-08-09.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 21972047),
  ('2023-09-20 10:00', 10, '9ec2dcad-fa0c-48be-a9e3-073b50ac642e', 'd3c84395-261c-4b8b-8373-4325f2d24432', 'International Vaccine Codes 2023-09-20.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 5678298),
  ('2023-09-20 10:00', 11, '6a6e2b95-f323-464b-9671-2194d7242b92', '66d06705-dac2-47c7-9c97-e8d82cebdff5', '2023_09_20_WHODrug Global_ENG.pdf', 'application/pdf', 3424988),
  ('2023-10-11 10:00', 10, 'b40f059a-9862-4519-9435-4eb682b414de', '3e772c7b-5033-439d-bd7b-6e51552d0b9b', 'International Vaccine Codes 2023.10.11.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 22382311),
  ('2023-12-13 10:00', 10, '0d542e20-40d5-4c9f-81d4-23f565cf7693', 'ee01c464-745a-4eee-9365-bea6d764dd45', 'International Vaccine Codes 2023.12.13.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 5663040),
  ('2024-03-20 10:00', 10, '4f400323-3ce5-4590-a92c-e9f40d141de9', '2b65ef0c-418b-4d6a-9913-5b3e65e647b3', 'International Vaccine Codes 2024.03.20.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 3980451),
  ('2024-04-17 10:00', 10, 'af9d68b7-7657-485e-9889-fc1f09449d1e', 'cd22c501-bd9e-4e90-9f2d-f0360290099e', 'International Vaccine Codes 2024.04.17.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 5251139),
  ('2024-04-17 10:00', 13, '073a804c-fe33-4b63-9faf-c62375173fbb', '3f6ca736-1560-44d4-a2a1-ca142ee3c2db', '240320-IVCI-Metrics- FINAL.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 316264),
  ('2024-06-12 10:00', 10, '3fac4989-a984-4ccb-b081-04ff5a49154a', '3abe0b9f-2f72-45a2-8d1f-001464a5a61c', 'International Vaccine Codes 2024.06.12.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 7514281),
  ('2024-06-12 10:00', 12, '769bdc87-66e3-4acd-92f6-cafd08302f08', '9b5cd91c-9116-4bad-bda6-2a2fc0a0a568', '240612-IVCI.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 269595),
  ('2024-07-31 10:00', 10, 'ff10ca07-ce40-4eed-929a-a53b10b5b3b8', '5b9bd241-d31b-44a1-9cc9-984b5c8d6d23', 'International Vaccine Codes 2024.07.31.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 6649560),
  ('2024-10-09 10:00', 10, '927a8049-93e7-4c91-adb3-6b9bf280de0f', 'acb48cae-5282-498a-91a1-57928294c1fd', 'International Vaccine Codes 2024.10.09.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 6584085),
  ('2024-11-27 10:00', 10, '97542530-5fa3-491c-ad9a-88fe55de922c', '7e5fa7a5-c3a9-4e64-a648-a35a91f34c29', 'International Vaccine Codes 2024.11.27.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 5536956),
  ('2025-01-08 10:00', 10, '5a420935-3f94-4d7d-b3c4-66ea1bfa7648', '98f647a7-120c-499e-996e-15b0c26c4676', 'International Vaccine Codes 2025.01.08.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 4540773),
  ('2025-03-12 10:00', 10, '13f827f1-19e8-4e67-ba81-51c72d4299dc', 'c88ff187-07ee-437f-8103-d2d891a07430', 'International Vaccine Codes 2025.03.12.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 3166686),
  ('2025-03-12 09:00', 10, '86fc0392-9b0f-470f-b7a6-a06a452d5371', '684cfc72-af32-436b-b980-2d2309b36ebb', 'IVC en español 2025-03_meeting review version.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 9877242),
  ('2025-03-12 09:00', 12, '97604564-c91a-479b-9d82-ff557abaace7', '9573439b-5c76-46ad-b49f-0d7d8b92eee7', '2025_03_12_USA_AIRA_SPA_IVC en español_WHODrug Global.pdf', 'application/pdf', 4195844),
  ('2025-04-09 10:00', 10, '3bb9af1d-94b9-4313-985e-7952ad9e3941', '47741fce-7bbd-47e8-922c-3f17b927325d', 'International Vaccine Codes 2025.04.09.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 3145901),
  ('2025-04-09 09:00', 10, '74b88ed7-22a8-4efa-b1e4-2d1de5b6cc11', 'b7125759-da3e-4d24-84f0-ad70a1786fdc', 'IVC en español 2025-04.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 8054013),
  ('2025-05-08 10:00', 10, 'f5f83daa-f515-48ed-9e84-605a7867d96b', '523da3f4-5059-4ac5-ad10-4341421bb3d1', 'IVC Training 2025-05-08-Nathan.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 16322148),
  ('2025-05-09 10:00', 10, '81d8a650-4ece-4239-a0f0-1079f0921868', '95d43404-8dc9-4e5d-847c-ca07a0e2aeac', 'A01-FK-Welcome and goals of the meeting.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 2010485),
  ('2025-05-09 10:00', 11, '1a983f34-9e63-4a58-b7f8-1a4611ae1943', '2b3212f9-8feb-401f-889d-65e81d32c8ec', 'A03-FK-NUVA- Why it matters.pdf', 'application/pdf', 845906),
  ('2025-05-09 10:00', 12, 'ee7067e8-7628-46cf-8fca-a6ee636f7825', '461922ed-aab1-4153-b7ed-b7a0437d122e', 'a04-jlk-_valence_concept.pdf', 'application/pdf', 641594),
  ('2025-05-09 10:00', 13, 'bccc2cc6-168c-47ae-9817-6a4146ca4872', '3824018d-0780-45fc-b7ed-3ad3968016ba', 'A05_NUVA Extension to SNOMED CT.pdf', 'application/pdf', 3849368),
  ('2025-05-09 10:00', 14, '920b504a-59b2-4caa-927f-bdca04f20c4a', '808939ff-8a14-41b0-bff5-9c0e158df50a', 'B01-IW-View from the industry.pdf', 'application/pdf', 1936414),
  ('2025-05-09 10:00', 16, 'a8afc867-8430-4a58-8b54-6598777b7a77', '1197c519-ad94-4ef5-bd74-c7180e5da1d1', 'B04-MF-WHODrug and IDMP for vaccines.pdf', 'application/pdf', 1883984),
  ('2025-05-09 10:00', 17, 'bb631c76-278a-4bb7-933a-534d1a71f438', '0f9cf0fc-fa1f-4982-bfda-2c9d1314d285', 'c01-md-luxembourg_experience.pdf', 'application/pdf', 1475396),
  ('2025-05-09 10:00', 18, 'fe9a5ef0-c26c-45f2-aa4e-852895a48e33', 'f85be11b-ad1a-414e-bbaf-c58733e1dfc1', 'C02-AC-The EUVABECO EVC project.pdf', 'application/pdf', 1104906),
  ('2025-05-09 10:00', 19, '85a08544-db55-4fe2-9304-ec70c662e6eb', 'e8d3cafa-7224-4558-8fb2-83f4e0e0b8d5', 'D02-SC-US - Vaccine Coding.pdf', 'application/pdf', 3600199),
  ('2025-05-09 10:00', 20, '61253c43-b3be-49e6-8137-2360799bd73c', 'ead609c6-f6e3-4807-b350-ce9d6531554a', 'D03-MT-Canada_Experiences_in_Vaccine_Coding.pdf', 'application/pdf', 1120770),
  ('2025-05-09 10:00', 21, 'c31f73ec-9095-47ad-ae77-a3878922fb43', '17954148-34bf-478c-b4e7-7f0d72ade25b', 'E01-TD-Transcoding and aligning.pdf', 'application/pdf', 854339),
  ('2025-05-09 10:00', 22, '370cefb5-a8a4-4175-a012-8114be04b5af', '37283452-cd58-4734-8007-81f4549e6a94', 'E02-FK-Metrics.pdf', 'application/pdf', 668256),
  ('2025-06-11 10:00', 10, 'de5870b8-089d-469b-beef-0b85682ee7fe', '86ee6e14-47a0-4cf6-93c1-70c74d654731', 'International Vaccine Codes 2025.06.11.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 60078075),
  ('2025-07-09 10:00', 10, '23154db6-eb06-4de2-984f-700570d37e20', '570d89ad-b96c-49ac-9ab9-f6ae52a7126f', 'International Vaccine Codes 2025.07.09.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 26738884),
  ('2025-07-23 10:00', 10, 'c4f2e791-046d-4325-821e-7844eccde5a1', '054177c9-f49d-4c8c-bd0d-b3f5e90c3282', 'International Vaccine Codes 2025.07.23 Esp.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 5821232),
  ('2025-09-10 10:00', 10, 'ceb10568-13c6-45fa-87f9-ef32b5e298b8', '08b77bea-7985-4b4d-993c-2fcf7db9ef2e', 'International Vaccine Codes 2025.09.10.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 2825992),
  ('2025-10-08 10:00', 10, '599dd208-2bb2-46ac-95ea-29f78a78285a', 'dae6787a-e556-4447-b9c6-412c590ff1ad', 'International Vaccine Codes 2025.10.08.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 3165283),
  ('2025-10-08 10:00', 11, '35ee0f3e-8eb0-4c40-8e57-6dd3b70bb55e', 'e38a1b82-1040-4405-bb0d-ccc9cd377223', 'AIRA_15min (2).pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 6346905),
  ('2025-10-08 10:00', 12, '3b6e4506-f655-4f84-86d9-50779a41dddb', '2123cace-6ff4-4a87-b068-49befc58eda2', 'Syadem Presentation.pdf', 'application/pdf', 9805025),
  ('2025-11-12 10:00', 10, '5daac571-61fa-4216-bb29-4a1ccf70b69a', '2034ccf8-8853-4c89-b833-8218d042b589', 'International Vaccine Codes 2025.11.12.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 2244724),
  ('2025-11-12 10:00', 12, 'd64fe397-923d-4301-bb2d-622b5054aa80', '21bd3776-4278-4e32-a2d1-c8cfd0d8c583', '251112-NUVA presentation for IVCI.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 4806532),
  ('2025-12-10 10:00', 10, '55e47dd4-c213-4680-bb55-30f7fe6c476b', '960e016d-3df4-469f-a55e-be01ddeef366', 'International Vaccine Codes 2025.12.10.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 9960233),
  ('2025-12-10 10:00', 11, '2b6dffba-dc86-4b2f-8b5a-2bdda354fab5', '6a934910-c2be-4e6e-8293-854f11729216', 'NUVAccess.pdf', 'application/pdf', 517251),
  ('2025-12-10 10:00', 12, '8c860970-c5ca-4669-b947-5a1976d25cdf', '533d3bd7-6f47-477f-8ba8-b987c6378298', 'Country Interview Review with IVC.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 8619611),
  ('2025-12-10 10:00', 13, 'e6470415-dbcc-483e-9d53-6d361056d2d2', '4a4e16ad-ee87-4a6a-a52d-7fe12fa29d6d', '251210-NUVA alignment files.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 2615064),
  ('2026-01-14 10:00', 10, 'aa8061b1-0c8b-4e50-97ed-82f4f71d43da', '397e5856-22a7-4126-8533-a41501b5eecd', 'International Vaccine Codes 2026.01.14.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 2490720),
  ('2026-01-14 10:00', 11, 'ec4f46b6-2830-41ad-b803-9ba127b9da1b', '677f6d60-125d-4837-b767-742093c469e7', '260114-NUVA use cases in France and Switzerland.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 2583827),
  ('2026-02-11 10:00', 10, '9b598401-b4d7-4dd5-b8b9-5327a592b6d5', 'fb7f575f-3e55-4aa7-a874-6f615d5fffee', 'International Vaccine Codes 2026.02.11.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 2647119),
  ('2026-02-11 10:00', 12, '54c62a5b-c80f-44fa-b6c7-5bf83d415f79', '2cd998dd-ed40-4478-9983-d3eed27bd4ce', '20260210 Neisseria meningitidis.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 1486066),
  ('2026-02-11 10:00', 14, 'e28fcd99-12b1-4138-9b27-a3117dac10be', '4b595958-5e74-4f3b-9489-32019b48096d', '260211-Concept of disease in NUVA.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 2617666),
  ('2026-03-11 10:00', 10, 'eb6955e5-7cfa-4829-9e69-8c9306368a6d', 'e4bc85a9-d1f8-4684-b487-e669cef74cf4', 'International Vaccine Codes 2026.03.11.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 2732052),
  ('2026-03-11 10:00', 12, 'fb7e7b04-bbf8-4c36-9f71-0a11c1b0eb1d', '2f6d7dbd-cd51-403d-8bfe-428cd071e838', 'MenB Rennes 2025_v3.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 2611266),
  ('2026-05-13 10:00', 10, '3e5a754d-08e8-429e-b350-23e2c3441adf', 'be44b8df-c3d8-44c0-9af2-bc0564c5b8fd', 'International Vaccine Codes 2026.05.13.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 7798081);

DELIMITER $$

DROP PROCEDURE IF EXISTS backload_ivc_historical_meetings $$
CREATE PROCEDURE backload_ivc_historical_meetings()
BEGIN
  DECLARE v_space_id BIGINT UNSIGNED;
  DECLARE v_series_id BIGINT;
  DECLARE v_series_description TEXT;
  DECLARE v_user_id BIGINT;
  DECLARE v_now DATETIME(6);
  DECLARE v_count BIGINT DEFAULT 0;

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  SELECT COUNT(*) INTO v_count FROM es_topic_space WHERE space_code = 'emerging-standards';
  IF v_count <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'IVC backload: Emerging Standards Topic Space not found.';
  END IF;
  SELECT es_topic_space_id INTO v_space_id FROM es_topic_space WHERE space_code = 'emerging-standards';

  SELECT COUNT(*) INTO v_count
  FROM es_topic_meeting tm JOIN es_topic t ON t.es_topic_id = tm.es_topic_id
  WHERE t.es_topic_space_id = v_space_id
    AND t.topic_name = 'Immunization Vocabularies Collaboration (IVC)'
    AND tm.meeting_name = 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting'
    AND tm.status = 'ACTIVE';
  IF v_count <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'IVC backload: expected exactly one active IVC meeting series.';
  END IF;
  SELECT tm.es_topic_meeting_id, tm.meeting_description INTO v_series_id, v_series_description
  FROM es_topic_meeting tm JOIN es_topic t ON t.es_topic_id = tm.es_topic_id
  WHERE t.es_topic_space_id = v_space_id
    AND t.topic_name = 'Immunization Vocabularies Collaboration (IVC)'
    AND tm.meeting_name = 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting'
    AND tm.status = 'ACTIVE';

  SELECT COUNT(*) INTO v_count FROM auth_user WHERE email = 'nbunker@immregistries.org';
  IF v_count <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'IVC backload: expected exactly one user nbunker@immregistries.org.';
  END IF;
  SELECT user_id INTO v_user_id FROM auth_user WHERE email = 'nbunker@immregistries.org';

  -- Already complete? Then do nothing. Partial? Fail rather than duplicate.
  SELECT COUNT(*) INTO v_count FROM hub_stored_file f JOIN tmp_ivc_hist_file h ON h.public_id = f.public_id;
  IF v_count = 57 THEN
    SELECT COUNT(*) INTO v_count
    FROM es_meeting m JOIN tmp_ivc_hist_meeting h
      ON m.es_topic_meeting_id = v_series_id AND m.scheduled_start = h.scheduled_start AND m.meeting_name = h.meeting_name;
    IF v_count <> 30 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'IVC backload: files present but meetings incomplete; inspect before rerunning.';
    END IF;
  ELSEIF v_count <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'IVC backload: partial stored-file state found; inspect before rerunning.';
  ELSE
    SELECT COUNT(*) INTO v_count FROM es_meeting
    WHERE es_topic_meeting_id = v_series_id AND scheduled_start < '2026-06-01';
    IF v_count <> 0 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'IVC backload: IVC meetings before June 2026 already exist; possible duplicates.';
    END IF;

    SELECT COUNT(*) INTO v_count FROM (
      SELECT i.meeting_ref, i.display_order
      FROM tmp_ivc_hist_item i
      JOIN es_topic_space s ON s.space_code = i.topic_space_code
      JOIN es_topic t ON t.es_topic_space_id = s.es_topic_space_id AND t.topic_name = i.topic_name
      GROUP BY i.meeting_ref, i.display_order HAVING COUNT(*) > 1
    ) ambiguous;
    IF v_count <> 0 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'IVC backload: an agenda topic name matches more than one topic.';
    END IF;

    UPDATE tmp_ivc_hist_item i
      JOIN es_topic_space s ON s.space_code = i.topic_space_code
      JOIN es_topic t ON t.es_topic_space_id = s.es_topic_space_id AND t.topic_name = i.topic_name
    SET i.es_topic_id = t.es_topic_id
    WHERE i.topic_space_code IS NOT NULL AND s.visibility = 'PUBLIC' AND t.status = 'ACTIVE';
    SELECT COUNT(*) INTO v_count FROM tmp_ivc_hist_item WHERE es_topic_id IS NOT NULL;
    IF v_count <> 31 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'IVC backload: an agenda topic link did not resolve to one active public topic.';
    END IF;

    SET v_now = UTC_TIMESTAMP(6);
    START TRANSACTION;

    INSERT INTO es_meeting (
      es_topic_meeting_id, es_topic_space_id, meeting_name, meeting_description,
      scheduled_start, timezone_id, status, created_at, created_by_user_id, updated_at,
      completed_at, completed_by_user_id, close_due_at, closed_at, closed_by_user_id, close_method
    )
    SELECT v_series_id, v_space_id, h.meeting_name, IF(h.is_monthly, v_series_description, NULL),
      h.scheduled_start, 'America/New_York', 'CLOSED', v_now, v_user_id, v_now,
      v_now, v_user_id, v_now + INTERVAL 7 DAY, v_now, v_user_id, 'MANUAL'
    FROM tmp_ivc_hist_meeting h
    ORDER BY h.scheduled_start;

    UPDATE tmp_ivc_hist_meeting h JOIN es_meeting m
      ON m.es_topic_meeting_id = v_series_id AND m.scheduled_start = h.scheduled_start AND m.meeting_name = h.meeting_name
    SET h.es_meeting_id = m.es_meeting_id;
    SELECT COUNT(*) INTO v_count FROM tmp_ivc_hist_meeting WHERE es_meeting_id IS NOT NULL;
    IF v_count <> 30 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'IVC backload: inserted meeting count mismatch.';
    END IF;

    INSERT INTO es_meeting_status_history (es_meeting_id, from_status, to_status, changed_at, changed_by_user_id, transition_method)
    SELECT es_meeting_id, NULL, 'CLOSED', v_now, v_user_id, 'USER' FROM tmp_ivc_hist_meeting;

    INSERT INTO es_meeting_agenda_item (
      es_meeting_id, display_order, title, agenda_markdown, es_topic_id, status, accepted_at, created_at, updated_at
    )
    SELECT h.es_meeting_id, i.display_order, i.title, i.agenda_markdown, i.es_topic_id, 'ACCEPTED', v_now, v_now, v_now
    FROM tmp_ivc_hist_item i JOIN tmp_ivc_hist_meeting h ON h.meeting_ref = i.meeting_ref
    ORDER BY h.scheduled_start, i.display_order;
    IF ROW_COUNT() <> 143 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'IVC backload: inserted agenda item count mismatch.';
    END IF;

    INSERT INTO hub_stored_file (
      public_id, storage_backend, storage_key, blob_endpoint, blob_container, original_filename,
      content_type, size_bytes, uploaded_by_user_id, uploaded_at, created_at, download_only
    )
    SELECT public_id, 'LOCAL', storage_key, NULL, NULL, original_filename,
      content_type, size_bytes, v_user_id, v_now, v_now, b'1'
    FROM tmp_ivc_hist_file
    ORDER BY meeting_ref, display_order;

    INSERT INTO es_meeting_agenda_attachment (es_meeting_agenda_item_id, stored_file_id, attached_by_user_id, attached_at)
    SELECT ai.es_meeting_agenda_item_id, sf.stored_file_id, v_user_id, v_now
    FROM tmp_ivc_hist_file f
    JOIN tmp_ivc_hist_meeting h ON h.meeting_ref = f.meeting_ref
    JOIN es_meeting_agenda_item ai ON ai.es_meeting_id = h.es_meeting_id AND ai.display_order = f.display_order
    JOIN hub_stored_file sf ON sf.public_id = f.public_id
    ORDER BY f.meeting_ref, f.display_order;
    IF ROW_COUNT() <> 57 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'IVC backload: attachment count mismatch.';
    END IF;

    COMMIT;
  END IF;
END $$

DELIMITER ;

CALL backload_ivc_historical_meetings();
DROP PROCEDURE backload_ivc_historical_meetings;
DROP TEMPORARY TABLE tmp_ivc_hist_meeting;
DROP TEMPORARY TABLE tmp_ivc_hist_item;
DROP TEMPORARY TABLE tmp_ivc_hist_file;

-- Communication Bundles Phase 4: steward-visible audit history.
CREATE TABLE es_communication_bundle_audit (
  audit_id BIGINT NOT NULL AUTO_INCREMENT,
  bundle_id BIGINT DEFAULT NULL,
  topic_resource_id BIGINT DEFAULT NULL,
  event_type VARCHAR(40) NOT NULL,
  details VARCHAR(1000) DEFAULT NULL,
  changed_by_user_id BIGINT NOT NULL,
  changed_at DATETIME(6) NOT NULL,
  PRIMARY KEY (audit_id),
  KEY ix_bundle_audit_bundle (bundle_id, changed_at, audit_id),
  KEY ix_bundle_audit_resource (topic_resource_id, changed_at, audit_id),
  CONSTRAINT fk_bundle_audit_bundle FOREIGN KEY (bundle_id)
    REFERENCES es_communication_bundle (bundle_id),
  CONSTRAINT fk_bundle_audit_resource FOREIGN KEY (topic_resource_id)
    REFERENCES es_topic_resource (topic_resource_id),
  CONSTRAINT fk_bundle_audit_user FOREIGN KEY (changed_by_user_id)
    REFERENCES auth_user (user_id),
  CONSTRAINT chk_bundle_audit_subject CHECK (
    bundle_id IS NOT NULL OR topic_resource_id IS NOT NULL
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Also relax existing Orientation templates if their seed data was already applied.
UPDATE es_communication_bundle_template_component component
JOIN es_communication_bundle_template template ON template.template_id = component.template_id
JOIN es_communication_bundle_purpose purpose ON purpose.purpose_id = template.purpose_id
SET component.is_required = b'0'
WHERE purpose.purpose_key = 'TOPIC_ORIENTATION'
  AND component.semantic_key IN ('introduction', 'why_it_matters', 'how_to_get_involved')
  AND component.component_kind = 'TEXT';

-- Phase 6: intentionally preserved resource metadata and independent stored-file identities.
CREATE TABLE es_topic_resource_version (
  resource_version_id BIGINT NOT NULL AUTO_INCREMENT,
  topic_resource_id BIGINT NOT NULL,
  es_topic_id BIGINT NOT NULL,
  stored_file_id BIGINT DEFAULT NULL,
  resource_type VARCHAR(24) NOT NULL,
  title VARCHAR(255) NOT NULL,
  description TEXT DEFAULT NULL,
  attribution VARCHAR(500) DEFAULT NULL,
  external_url VARCHAR(2000) DEFAULT NULL,
  preserved_by_user_id BIGINT NOT NULL,
  preserved_at DATETIME(6) NOT NULL,
  PRIMARY KEY (resource_version_id),
  UNIQUE KEY uq_resource_version_subject (resource_version_id, topic_resource_id, es_topic_id),
  CONSTRAINT fk_resource_version_resource FOREIGN KEY (topic_resource_id, es_topic_id)
    REFERENCES es_topic_resource (topic_resource_id, es_topic_id),
  CONSTRAINT fk_resource_version_file FOREIGN KEY (stored_file_id)
    REFERENCES hub_stored_file (stored_file_id),
  CONSTRAINT fk_resource_version_user FOREIGN KEY (preserved_by_user_id)
    REFERENCES auth_user (user_id),
  CONSTRAINT chk_resource_version_source CHECK (
    (stored_file_id IS NOT NULL AND external_url IS NULL AND resource_type <> 'EXTERNAL_LINK')
    OR (stored_file_id IS NULL AND external_url IS NOT NULL AND resource_type = 'EXTERNAL_LINK')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE es_communication_bundle_resource_placement
  ADD COLUMN resource_version_id BIGINT DEFAULT NULL,
  ADD CONSTRAINT fk_bundle_placement_version FOREIGN KEY (resource_version_id, topic_resource_id, es_topic_id)
    REFERENCES es_topic_resource_version (resource_version_id, topic_resource_id, es_topic_id);

INSERT INTO es_communication_bundle_purpose (
  purpose_key, display_name, description, mode, instance_policy, default_audience
) VALUES (
  'STARTER_PACKET', 'Starter Packet',
  'A dated starting point for a new project, available to this Topic''s champions/support contacts and global admins.',
  'SNAPSHOT', 'MULTIPLE', 'STEWARDS'
);
INSERT INTO es_communication_bundle_template (purpose_id, version_no, status, created_at)
SELECT purpose_id, 1, 'ACTIVE', UTC_TIMESTAMP(6)
FROM es_communication_bundle_purpose WHERE purpose_key = 'STARTER_PACKET';
UPDATE es_communication_bundle_purpose p
JOIN es_communication_bundle_template t ON t.purpose_id = p.purpose_id AND t.version_no = 1
SET p.active_template_id = t.template_id WHERE p.purpose_key = 'STARTER_PACKET';

INSERT INTO es_communication_bundle_template_component (
  template_id, semantic_key, display_name, authoring_prompt, component_kind, is_required, cardinality, display_order
)
SELECT t.template_id, c.semantic_key, c.display_name, c.prompt, c.kind, c.required, c.cardinality, c.display_order
FROM es_communication_bundle_template t
JOIN es_communication_bundle_purpose p ON p.purpose_id = t.purpose_id
CROSS JOIN (
  SELECT 'title' semantic_key, 'Packet title' display_name, 'Name this project starting point.' prompt,
    'TEXT' kind, b'1' required, 'SINGLE' cardinality, 10 display_order
  UNION ALL SELECT 'teaser_summary', 'Teaser summary', 'A short invitation shown on the Topic page.', 'TEXT', b'0', 'SINGLE', 20
  UNION ALL SELECT 'teaser_image', 'Teaser image', 'Choose an image for the Topic-page teaser and packet.', 'RESOURCE', b'0', 'SINGLE', 30
  UNION ALL SELECT 'explanation', 'Project context', 'Explain the project and what this packet helps people begin.', 'TEXT', b'0', 'SINGLE', 40
  UNION ALL SELECT 'starting_points', 'Suggested starting points', 'One starting point per line.', 'STRUCTURED_LIST', b'0', 'REPEATING', 50
  UNION ALL SELECT 'next_actions', 'Next actions', 'One next action per line.', 'STRUCTURED_LIST', b'0', 'REPEATING', 60
  UNION ALL SELECT 'supporting_resources', 'Supporting resources', 'Select resources in the order they should be explored.', 'RESOURCE_COLLECTION', b'0', 'REPEATING', 70
) c
WHERE p.purpose_key = 'STARTER_PACKET' AND t.version_no = 1;
