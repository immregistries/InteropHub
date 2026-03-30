-- Adds detailed audit logging for magic-link welcome email delivery lifecycle.

CREATE TABLE auth_magic_link_send_event (
  send_event_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  magic_id           BIGINT UNSIGNED NULL,
  user_id            BIGINT NOT NULL,
  app_id             BIGINT UNSIGNED NULL,
  email_normalized   VARCHAR(254) NOT NULL,
  event_type         ENUM('SEND_REQUESTED','SMTP_SEND_STARTED','SMTP_SEND_SUCCEEDED','SMTP_SEND_FAILED') NOT NULL,
  event_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  request_id         CHAR(36) NULL,
  request_ip         VARBINARY(16) NULL,
  user_agent         VARCHAR(300) NULL,
  smtp_message_id    VARCHAR(255) NULL,
  smtp_provider      VARCHAR(80) NULL,
  smtp_reply_code    VARCHAR(32) NULL,
  error_class        VARCHAR(120) NULL,
  error_message      VARCHAR(1000) NULL,
  server_node        VARCHAR(120) NULL,
  PRIMARY KEY (send_event_id),
  KEY ix_magic_send_email_time (email_normalized, event_at),
  KEY ix_magic_send_user_time (user_id, event_at),
  KEY ix_magic_send_magic_time (magic_id, event_at),
  KEY ix_magic_send_request (request_id),
  KEY ix_magic_send_type_time (event_type, event_at),
  CONSTRAINT fk_magic_send_magic FOREIGN KEY (magic_id) REFERENCES auth_magic_link(magic_id),
  CONSTRAINT fk_magic_send_user FOREIGN KEY (user_id) REFERENCES auth_user(user_id),
  CONSTRAINT fk_magic_send_app FOREIGN KEY (app_id) REFERENCES app_registry(app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
