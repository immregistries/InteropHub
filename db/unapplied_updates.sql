-- Pending schema/data changes for the next production release.
-- Process, conventions, and how to fold local admin/UI edits (e.g. Topic
-- Board layout changes made in the running app) back into this file before
-- a refresh discards them: see docs/database-release-practice.md.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- H5 private hosting of Topic Reference guides.
-- Existing documents stay EXTERNAL. A hosted version is one stored file per
-- upload; uploading the same label again keeps the older rows, and the newest
-- upload is the one that label serves. The stored file's public_id is never
-- shown to readers; InteropHub streams the HTML itself.
ALTER TABLE es_guide_document
  ADD COLUMN hosting varchar(16) NOT NULL DEFAULT 'EXTERNAL' COMMENT 'EXTERNAL or HUB';

ALTER TABLE es_guide_document
  ADD CONSTRAINT chk_es_guide_document_hosting CHECK (hosting IN ('EXTERNAL', 'HUB'));

CREATE TABLE es_guide_hosted_version (
  guide_hosted_version_id BIGINT NOT NULL AUTO_INCREMENT,
  guide_document_id BIGINT NOT NULL,
  label VARCHAR(64) NOT NULL,
  stored_file_id BIGINT NOT NULL,
  uploaded_by_user_id BIGINT NOT NULL,
  uploaded_at DATETIME(6) NOT NULL,
  PRIMARY KEY (guide_hosted_version_id),
  KEY ix_es_guide_hosted_version_label (guide_document_id, label, uploaded_at),
  KEY fk_es_guide_hosted_version_file (stored_file_id),
  KEY fk_es_guide_hosted_version_user (uploaded_by_user_id),
  CONSTRAINT fk_es_guide_hosted_version_document FOREIGN KEY (guide_document_id) REFERENCES es_guide_document (guide_document_id),
  CONSTRAINT fk_es_guide_hosted_version_file FOREIGN KEY (stored_file_id) REFERENCES hub_stored_file (stored_file_id),
  CONSTRAINT fk_es_guide_hosted_version_user FOREIGN KEY (uploaded_by_user_id) REFERENCES auth_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
