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
    'TEXT' AS component_kind, b'1' AS is_required, 'SINGLE' AS cardinality, 10 AS display_order
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
