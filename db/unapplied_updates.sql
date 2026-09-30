-- Pending schema/data changes for the next production release.
-- Process, conventions, and how to fold local admin/UI edits (e.g. Topic
-- Board layout changes made in the running app) back into this file before
-- a refresh discards them: see docs/database-release-practice.md.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- TEMPORARY - Communication Bundles step 1, proving Azure Blob storage
-- end to end (docs/communication-bundles/InteropHub_Communication_Bundles_Implementation_Plan.md,
-- docs/communication-bundles/artifact-storage-deployment-handoff.md). Holds one
-- row per demo slot so /welcome can find the current image without a hardcoded
-- object key. Step 2 replaces this with real Topic Resources; DROP this table
-- then. object_key is the opaque, stable Blob name and never changes when the
-- image is replaced.
CREATE TABLE es_artifact_demo (
  es_artifact_demo_id BIGINT NOT NULL AUTO_INCREMENT,
  slot_key VARCHAR(40) NOT NULL,
  object_key VARCHAR(64) NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL,
  uploaded_by_user_id BIGINT NOT NULL,
  uploaded_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (es_artifact_demo_id),
  UNIQUE KEY uq_es_artifact_demo_slot (slot_key),
  CONSTRAINT fk_es_artifact_demo_uploaded_by FOREIGN KEY (uploaded_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
