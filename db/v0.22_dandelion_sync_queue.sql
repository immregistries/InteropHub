CREATE TABLE dandelion_sync_queue (
  sync_queue_id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  entity_type          ENUM('TOPIC','CONTACT','ASSIGNMENT') NOT NULL,
  entity_id            BIGINT UNSIGNED NOT NULL,
  secondary_entity_id  BIGINT UNSIGNED NULL,
  operation            ENUM('UPSERT','ASSIGN_ADD','ASSIGN_REMOVE') NOT NULL,
  status               ENUM('PENDING','SENT','FAILED') NOT NULL DEFAULT 'PENDING',
  attempt_count        INT NOT NULL DEFAULT 0,
  last_error           TEXT NULL,
  sent_at              DATETIME NULL,
  created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (sync_queue_id),
  KEY ix_dd_sync_queue_status_created (status, created_at),
  KEY ix_dd_sync_queue_entity (entity_type, entity_id, secondary_entity_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;