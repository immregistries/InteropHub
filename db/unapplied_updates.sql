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
