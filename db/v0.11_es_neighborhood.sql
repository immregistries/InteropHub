-- v0.11 - Add ES neighborhood catalog for public browsing and admin maintenance

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE es_neighborhood (
  es_neighborhood_id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  neighborhood_code    VARCHAR(80) NOT NULL,
  neighborhood_name    VARCHAR(140) NOT NULL,
  description          TEXT NULL,
  display_order        INT NOT NULL DEFAULT 0,
  is_active            TINYINT(1) NOT NULL DEFAULT 1,
  created_by_user_id   BIGINT NOT NULL,
  created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_neighborhood_id),
  UNIQUE KEY uq_es_neighborhood_code (neighborhood_code),
  KEY ix_es_neighborhood_active_order (is_active, display_order, neighborhood_name),
  CONSTRAINT fk_es_neighborhood_creator FOREIGN KEY (created_by_user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
