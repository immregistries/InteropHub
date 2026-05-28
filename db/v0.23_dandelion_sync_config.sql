CREATE TABLE dandelion_sync_config (
  config_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  active         TINYINT(1) NOT NULL DEFAULT 1,
  sync_enabled   TINYINT(1) NOT NULL DEFAULT 0,
  api_endpoint   VARCHAR(500) NOT NULL,
  api_key        VARCHAR(300) NOT NULL,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (config_id),
  KEY ix_dd_sync_config_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;