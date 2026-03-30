-- MySQL 8.x
-- Central Auth + Connectathon Workspace schema (Level 0)
-- Notes:
--  - Store secrets hashed (SHA-256 / bcrypt/argon2 at app layer). Never store raw tokens.
--  - Keep everything UTF8MB4.
--  - Time stored in UTC in DATETIME.

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- -------------------------
-- DATABASE USER (DEV)
-- -------------------------
-- Creates a dedicated application login for local development.
CREATE USER IF NOT EXISTS 'interophub_app'@'localhost' IDENTIFIED BY 'M3fcFHmj7e9HESs3';
ALTER USER 'interophub_app'@'localhost' IDENTIFIED BY 'M3fcFHmj7e9HESs3';
GRANT ALL PRIVILEGES ON interophub.* TO 'interophub_app'@'localhost';
FLUSH PRIVILEGES;

-- -------------------------
-- HUB SETTINGS (URL + SMTP)
-- -------------------------
CREATE TABLE hub_settings (
  setting_id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  active             TINYINT(1) NOT NULL DEFAULT 1,
  external_base_url  VARCHAR(300) NOT NULL,
  smtp_host          VARCHAR(255) NOT NULL,
  smtp_port          INT NOT NULL,
  smtp_username      VARCHAR(255) NOT NULL,
  smtp_password      VARCHAR(255) NOT NULL,
  smtp_auth          TINYINT(1) NOT NULL DEFAULT 1,
  smtp_starttls      TINYINT(1) NOT NULL DEFAULT 1,
  smtp_ssl           TINYINT(1) NOT NULL DEFAULT 0,
  smtp_from_email    VARCHAR(254) NOT NULL,
  smtp_from_name     VARCHAR(160) NOT NULL,
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (setting_id),
  KEY ix_hub_settings_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Mailtrap sandbox defaults. Replace smtp_password with your mailbox/API password.
INSERT INTO hub_settings (
  active,
  external_base_url,
  smtp_host,
  smtp_port,
  smtp_username,
  smtp_password,
  smtp_auth,
  smtp_starttls,
  smtp_ssl,
  smtp_from_email,
  smtp_from_name
) VALUES (
  1,
  'http://localhost:8080/hub',
  'sandbox.smtp.mailtrap.io',
  2525,
  'd1ab59b8e6b528',
  '5fa9c8b967f462',
  1,
  1,
  0,
  'no-reply@interophub.local',
  'InteropHub'
);

-- -------------------------
-- USERS / IDENTITY
-- -------------------------
CREATE TABLE auth_user (
  user_id            BIGINT NOT NULL AUTO_INCREMENT,
  email              VARCHAR(254) NOT NULL,
  email_normalized   VARCHAR(254) NOT NULL,  -- lowercase trimmed
  display_name       VARCHAR(160) NULL,
  organization       VARCHAR(200) NULL,
  role_title         VARCHAR(200) NULL,      -- free text
  email_verified     BIT(1) NOT NULL DEFAULT b'0',
  status             ENUM('ACTIVE','DELETED','DISABLED') NOT NULL DEFAULT 'ACTIVE',
  created_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  last_login_at      DATETIME(6) NULL,
  last_seen_at       DATETIME(6) NULL,
  delete_after_at    DATETIME(6) NULL,       -- set to last_seen + 1 year for purge job
  PRIMARY KEY (user_id),
  UNIQUE KEY uq_auth_user_email_norm (email_normalized),
  KEY ix_auth_user_status (status),
  KEY ix_auth_user_delete_after (delete_after_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Legal terms and agreement text (versioned)
CREATE TABLE legal_term (
  term_id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  term_code             VARCHAR(80) NOT NULL,   -- stable logical code, e.g. 'NO_PRODUCTION_DATA'
  version_num           INT NOT NULL,           -- 1, 2, 3...
  title                 VARCHAR(200) NOT NULL,  -- short heading shown near checkbox
  short_text            VARCHAR(500) NOT NULL,  -- concise checkbox text
  full_text             TEXT NULL,              -- full agreement language or summary
  full_text_url         VARCHAR(500) NULL,      -- optional link to full policy/agreement page

  scope_type            ENUM('REGISTRATION','WORKSPACE','BOTH') NOT NULL DEFAULT 'REGISTRATION',
  is_required           TINYINT(1) NOT NULL DEFAULT 1,
  display_order         INT NOT NULL DEFAULT 0,

  is_active             TINYINT(1) NOT NULL DEFAULT 1,
  effective_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  retired_at            DATETIME NULL,

  created_by_user_id    BIGINT NULL,
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (term_id),
  UNIQUE KEY uq_term_version (term_code, version_num),
  KEY ix_term_scope_active (scope_type, is_active, effective_at),
  KEY ix_term_display (scope_type, display_order),
  CONSTRAINT fk_legal_term_creator
    FOREIGN KEY (created_by_user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One-time magic link tokens (store hash, not raw token)
CREATE TABLE auth_magic_link (
  magic_id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id            BIGINT NOT NULL,
  token_hash         BINARY(32) NOT NULL,  -- SHA-256(token)
  app_id             BIGINT UNSIGNED NULL,
  return_to          VARCHAR(500) NULL,
  state_nonce        VARCHAR(255) NULL,
  requested_url      VARCHAR(500) NULL,
  issued_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at         DATETIME NOT NULL,
  consumed_at        DATETIME NULL,
  request_ip         VARBINARY(16) NULL,   -- INET6_ATON
  user_agent         VARCHAR(300) NULL,
  PRIMARY KEY (magic_id),
  UNIQUE KEY uq_magic_token_hash (token_hash),
  KEY ix_magic_user (user_id, issued_at),
  KEY ix_magic_expires (expires_at),
  CONSTRAINT fk_magic_user FOREIGN KEY (user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Append-only audit of magic-link email send lifecycle events
-- Records each send request and SMTP outcome for troubleshooting duplicates.
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

-- Central sessions (for central dashboard + for login-code flows)
CREATE TABLE auth_session (
  session_id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id            BIGINT NOT NULL,
  session_token_hash BINARY(32) NOT NULL,  -- SHA-256(session_token)
  issued_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at         DATETIME NOT NULL,    -- 7 days
  revoked_at         DATETIME NULL,
  last_ip            VARBINARY(16) NULL,
  last_user_agent    VARCHAR(300) NULL,
  PRIMARY KEY (session_id),
  UNIQUE KEY uq_session_token_hash (session_token_hash),
  KEY ix_session_user (user_id, expires_at),
  KEY ix_session_expires (expires_at),
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One-time login code (for redirect back to edge app and exchange server-to-server)
CREATE TABLE auth_login_code (
  login_code_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id            BIGINT NOT NULL,
  app_id             BIGINT UNSIGNED NOT NULL,
  code_hash          BINARY(32) NOT NULL,  -- SHA-256(code)
  return_to          VARCHAR(500) NULL,
  state_nonce        VARCHAR(255) NULL,
  requested_url      VARCHAR(500) NULL,
  issued_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at         DATETIME NOT NULL,    -- e.g., 60 seconds
  consumed_at        DATETIME NULL,
  PRIMARY KEY (login_code_id),
  UNIQUE KEY uq_login_code_hash (code_hash),
  KEY ix_login_code_user (user_id, issued_at),
  KEY ix_login_code_expires (expires_at),
  CONSTRAINT fk_login_code_user FOREIGN KEY (user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------
-- APPLICATIONS / TOKENS (per-user per-app)
-- -------------------------
CREATE TABLE app_registry (
  app_id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  app_code           VARCHAR(60) NOT NULL,   -- e.g., 'step-cdsi'
  app_name           VARCHAR(120) NOT NULL,
  default_redirect_url VARCHAR(255) NULL,
  app_description    TEXT NULL,
  managed_by         ENUM('AIRA','THIRD_PARTY') NOT NULL DEFAULT 'AIRA', -- for registry, not per-system
  is_enabled         TINYINT(1) NOT NULL DEFAULT 1,
  kill_switch        TINYINT(1) NOT NULL DEFAULT 0, -- global disable for this app’s APIs
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (app_id),
  UNIQUE KEY uq_app_code (app_code),
  KEY ix_app_enabled (is_enabled, kill_switch)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Redirect allowlist for app callback/base URLs
CREATE TABLE app_redirect_allowlist (
  allow_id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  app_id             BIGINT UNSIGNED NOT NULL,
  base_url           VARCHAR(255) NOT NULL,  -- e.g. https://step.example.org or http://localhost:8080
  is_enabled         TINYINT(1) NOT NULL DEFAULT 1,
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (allow_id),
  UNIQUE KEY uq_app_base (app_id, base_url),
  KEY ix_allow_enabled (app_id, is_enabled),
  CONSTRAINT fk_allow_app FOREIGN KEY (app_id) REFERENCES app_registry(app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- PATs: per-user per-app (30 day expiry)
CREATE TABLE app_user_token (
  token_id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id            BIGINT NOT NULL,
  app_id             BIGINT UNSIGNED NOT NULL,
  token_hash         BINARY(32) NOT NULL,    -- SHA-256(token); store raw only once at creation
  label              VARCHAR(120) NULL,      -- optional: "Postman", "My client app"
  issued_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at         DATETIME NOT NULL,      -- 30 days
  revoked_at         DATETIME NULL,
  last_used_at       DATETIME NULL,
  PRIMARY KEY (token_id),
  UNIQUE KEY uq_app_token_hash (token_hash),
  KEY ix_app_user_active (app_id, user_id, revoked_at, expires_at),
  KEY ix_app_token_expires (expires_at),
  CONSTRAINT fk_app_token_user FOREIGN KEY (user_id) REFERENCES auth_user(user_id),
  CONSTRAINT fk_app_token_app FOREIGN KEY (app_id) REFERENCES app_registry(app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- API definitions per registered app (distinct endpoints/operations the app exposes for testing)
CREATE TABLE app_api (
  api_id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  app_id             BIGINT UNSIGNED NOT NULL,
  api_code           VARCHAR(80) NOT NULL,    -- stable identifier, e.g. 'query', 'submit'
  purpose_label      VARCHAR(160) NOT NULL,   -- human-readable label shown to users
  description        TEXT NULL,
  is_enabled         TINYINT(1) NOT NULL DEFAULT 1,
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (api_id),
  UNIQUE KEY uq_app_api_code (app_id, api_code),
  KEY ix_app_api_enabled (app_id, is_enabled),
  CONSTRAINT fk_app_api_app FOREIGN KEY (app_id) REFERENCES app_registry(app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Per-user API secrets for test system access
-- NOTE: secret_value stored plain-text by design — these are test-only keys with minimal security.
--       Revoke by nulling secret_value; update by replacing it.
CREATE TABLE app_api_secret (
  secret_id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  api_id             BIGINT UNSIGNED NOT NULL,
  user_id            BIGINT NOT NULL,
  secret_value       VARCHAR(255) NULL,       -- NULL = revoked; replace value to rotate
  label              VARCHAR(120) NULL,       -- optional: "Postman", "My test client"
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (secret_id),
  UNIQUE KEY uq_api_secret_user (api_id, user_id),
  KEY ix_api_secret_user (user_id),
  CONSTRAINT fk_api_secret_api FOREIGN KEY (api_id) REFERENCES app_api(api_id),
  CONSTRAINT fk_api_secret_user FOREIGN KEY (user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------
-- CONNECTATHON TOPICS / WORKSPACES
-- -------------------------
CREATE TABLE ig_topic (
  topic_id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  topic_code         VARCHAR(80) NOT NULL,    -- e.g., 'immds-halo'
  topic_name         VARCHAR(140) NOT NULL,   -- "ImmDS+HALO"
  description        TEXT NULL,
  created_by_user_id BIGINT NOT NULL,
  status             ENUM('ACTIVE','ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (topic_id),
  UNIQUE KEY uq_topic_code (topic_code),
  KEY ix_topic_status (status),
  CONSTRAINT fk_topic_creator FOREIGN KEY (created_by_user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE connect_workspace (
  workspace_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  topic_id           BIGINT UNSIGNED NOT NULL,
  workspace_name     VARCHAR(160) NOT NULL,     -- "Connectathon 40 - March 2026"
  description        TEXT NULL,
  start_date         DATE NULL,
  end_date           DATE NULL,
  status             ENUM('ACTIVE','CLOSED','ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
  requires_approval  TINYINT(1) NOT NULL DEFAULT 1,
  created_by_user_id BIGINT NOT NULL,
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (workspace_id),
  KEY ix_workspace_topic (topic_id, status),
  CONSTRAINT fk_workspace_topic FOREIGN KEY (topic_id) REFERENCES ig_topic(topic_id),
  CONSTRAINT fk_workspace_creator FOREIGN KEY (created_by_user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Enrollment (opt-in + approval gate; implies consent to share contact info)
CREATE TABLE workspace_enrollment (
  enrollment_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  workspace_id       BIGINT UNSIGNED NOT NULL,
  user_id            BIGINT NOT NULL,
  state              ENUM('PENDING','APPROVED','REJECTED','SUSPENDED') NOT NULL DEFAULT 'PENDING',
  consent_at         DATETIME NULL,         -- when they opted-in / accepted sharing
  approved_by_user_id BIGINT NULL,
  approved_at        DATETIME NULL,
  admin_note         VARCHAR(400) NULL,
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (enrollment_id),
  UNIQUE KEY uq_workspace_user (workspace_id, user_id),
  KEY ix_enrollment_state (workspace_id, state),
  CONSTRAINT fk_enroll_workspace FOREIGN KEY (workspace_id) REFERENCES connect_workspace(workspace_id),
  CONSTRAINT fk_enroll_user FOREIGN KEY (user_id) REFERENCES auth_user(user_id),
  CONSTRAINT fk_enroll_approver FOREIGN KEY (approved_by_user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE legal_term_acceptance (
  acceptance_id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  term_id               BIGINT UNSIGNED NOT NULL,
  user_id               BIGINT NOT NULL,

  workspace_id          BIGINT UNSIGNED NULL,   -- null for registration/global acceptance
  accepted_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

  accepted_value        TINYINT(1) NOT NULL DEFAULT 1, -- for checkbox this is normally always 1
  ip_address            VARCHAR(45) NULL,       -- IPv4/IPv6 text form
  user_agent            VARCHAR(500) NULL,      -- optional audit detail

  PRIMARY KEY (acceptance_id),
  UNIQUE KEY uq_term_acceptance_once (term_id, user_id, workspace_id),
  KEY ix_accept_user (user_id, accepted_at),
  KEY ix_accept_term (term_id, accepted_at),
  KEY ix_accept_workspace (workspace_id, user_id),
  CONSTRAINT fk_term_acceptance_term
    FOREIGN KEY (term_id) REFERENCES legal_term(term_id),
  CONSTRAINT fk_term_acceptance_user
    FOREIGN KEY (user_id) REFERENCES auth_user(user_id),
  CONSTRAINT fk_term_acceptance_workspace
    FOREIGN KEY (workspace_id) REFERENCES connect_workspace(workspace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------
-- SYSTEMS / ENDPOINTS / CONTACTS
-- -------------------------
CREATE TABLE workspace_system (
  system_id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  workspace_id       BIGINT UNSIGNED NOT NULL,
  system_name        VARCHAR(160) NOT NULL,
  managed_by         ENUM('AIRA','THIRD_PARTY') NOT NULL DEFAULT 'THIRD_PARTY',
  capability         ENUM('CLIENT','SERVER','BOTH') NOT NULL,
  availability       ENUM('UP','DOWN','INTERMITTENT','UNKNOWN') NOT NULL DEFAULT 'UNKNOWN',
  availability_note  VARCHAR(400) NULL,
  description        TEXT NULL,
  how_to_use         TEXT NULL,
  limitations        TEXT NULL,
  created_by_user_id BIGINT NOT NULL,
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME NULL,
  PRIMARY KEY (system_id),
  KEY ix_system_workspace (workspace_id, capability, managed_by),
  KEY ix_system_availability (workspace_id, availability),
  CONSTRAINT fk_system_workspace FOREIGN KEY (workspace_id) REFERENCES connect_workspace(workspace_id),
  CONSTRAINT fk_system_creator FOREIGN KEY (created_by_user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Multiple contacts per system (requires enrollment to exist and be approved at app-layer)
CREATE TABLE workspace_system_contact (
  system_contact_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  system_id          BIGINT UNSIGNED NOT NULL,
  user_id            BIGINT NOT NULL,
  contact_role       VARCHAR(120) NULL, -- free text (e.g., "Primary", "Backup", "Dev")
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (system_contact_id),
  UNIQUE KEY uq_system_user (system_id, user_id),
  KEY ix_system_contact_user (user_id),
  CONSTRAINT fk_sys_contact_system FOREIGN KEY (system_id) REFERENCES workspace_system(system_id),
  CONSTRAINT fk_sys_contact_user FOREIGN KEY (user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Endpoints (servers require URL; clients may have none)
CREATE TABLE workspace_endpoint (
  endpoint_id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  system_id          BIGINT UNSIGNED NOT NULL,
  endpoint_type      ENUM('FHIR_BASE','SMART_CONFIG','WEBHOOK','OTHER') NOT NULL DEFAULT 'FHIR_BASE',
  url                VARCHAR(500) NULL, -- required for servers by app-layer rule
  auth_type          ENUM('AIRA_TOKEN','BEARER_PAT','NONE','OTHER') NOT NULL DEFAULT 'BEARER_PAT',
  auth_instructions  TEXT NULL,
  is_active          TINYINT(1) NOT NULL DEFAULT 1,
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME NULL,
  PRIMARY KEY (endpoint_id),
  KEY ix_endpoint_system (system_id, is_active),
  CONSTRAINT fk_endpoint_system FOREIGN KEY (system_id) REFERENCES workspace_system(system_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Optionally map a workspace to which AIRA-managed apps are “in play” for that topic/workspace
CREATE TABLE workspace_app (
  workspace_app_id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  workspace_id       BIGINT UNSIGNED NOT NULL,
  app_id             BIGINT UNSIGNED NOT NULL,
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (workspace_app_id),
  UNIQUE KEY uq_workspace_app (workspace_id, app_id),
  CONSTRAINT fk_workspace_app_workspace FOREIGN KEY (workspace_id) REFERENCES connect_workspace(workspace_id),
  CONSTRAINT fk_workspace_app_app FOREIGN KEY (app_id) REFERENCES app_registry(app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------
-- STEPS + SELF-REPORTED PROGRESS (matrix)
-- -------------------------
CREATE TABLE workspace_step (
  step_id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  workspace_id       BIGINT UNSIGNED NOT NULL,
  step_name          VARCHAR(140) NOT NULL, -- "Got token", "Connected", "Good response"
  applies_to         ENUM('CLIENT_TO_SERVER','CLIENT_ONLY','SERVER_ONLY','BOTH') NOT NULL DEFAULT 'CLIENT_TO_SERVER',
  sort_order         INT NOT NULL DEFAULT 0,
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (step_id),
  KEY ix_step_workspace (workspace_id, sort_order),
  CONSTRAINT fk_step_workspace FOREIGN KEY (workspace_id) REFERENCES connect_workspace(workspace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- System-level matrix progress (simpler than endpoint-level)
CREATE TABLE workspace_progress (
  progress_id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  workspace_id       BIGINT UNSIGNED NOT NULL,
  step_id            BIGINT UNSIGNED NOT NULL,
  client_system_id   BIGINT UNSIGNED NOT NULL,
  server_system_id   BIGINT UNSIGNED NOT NULL,
  status             ENUM('NO_PROGRESS','PROBLEMS','PARTIAL','WORKS','NOT_APPLICABLE') NOT NULL DEFAULT 'NO_PROGRESS',
  note               VARCHAR(800) NULL,
  reported_by_user_id BIGINT NOT NULL,
  updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (progress_id),
  UNIQUE KEY uq_progress_cell (step_id, client_system_id, server_system_id),
  KEY ix_progress_workspace (workspace_id, client_system_id, server_system_id),
  KEY ix_progress_status (workspace_id, status),
  CONSTRAINT fk_progress_workspace FOREIGN KEY (workspace_id) REFERENCES connect_workspace(workspace_id),
  CONSTRAINT fk_progress_step FOREIGN KEY (step_id) REFERENCES workspace_step(step_id),
  CONSTRAINT fk_progress_client FOREIGN KEY (client_system_id) REFERENCES workspace_system(system_id),
  CONSTRAINT fk_progress_server FOREIGN KEY (server_system_id) REFERENCES workspace_system(system_id),
  CONSTRAINT fk_progress_reporter FOREIGN KEY (reported_by_user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------
-- USAGE AGGREGATES (durable counts; Step batches updates)
-- -------------------------
-- Coarse aggregates per day per app (+ optional per token)
CREATE TABLE usage_daily_agg (
  usage_day          DATE NOT NULL,
  app_id             BIGINT UNSIGNED NOT NULL,
  token_id           BIGINT UNSIGNED NULL,         -- NULL = overall app totals
  metric             ENUM('API_CALL','API_ERROR_4XX','API_ERROR_5XX') NOT NULL DEFAULT 'API_CALL',
  count_value        BIGINT UNSIGNED NOT NULL DEFAULT 0,
  updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (usage_day, app_id, token_id, metric),
  KEY ix_usage_app_day (app_id, usage_day),
  CONSTRAINT fk_usage_app FOREIGN KEY (app_id) REFERENCES app_registry(app_id),
  CONSTRAINT fk_usage_token FOREIGN KEY (token_id) REFERENCES app_user_token(token_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------
-- APP LOGIN EVENTS
-- -------------------------
-- Append-only log of every successful user authentication to an external app
-- via the one-time code exchange (ApiAuthExchangeServlet).
-- One row per login. Use for usage statistics and audit purposes.
CREATE TABLE app_login_event (
  event_id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id            BIGINT NOT NULL,
  app_id             BIGINT UNSIGNED NOT NULL,
  login_code_id      BIGINT UNSIGNED NULL,     -- originating one-time code; nullable for purge safety (no FK)
  logged_in_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  server_ip          VARCHAR(45) NULL,         -- IP of the app server making the exchange API call
  user_ip            VARCHAR(45) NULL,         -- IP of the end user as reported by the app (future; passed via API)
  PRIMARY KEY (event_id),
  KEY ix_app_login_user_time (user_id, logged_in_at),
  KEY ix_app_login_app_time  (app_id, logged_in_at),
  KEY ix_app_login_day       (app_id, (DATE(logged_in_at))),
  CONSTRAINT fk_app_login_user FOREIGN KEY (user_id) REFERENCES auth_user(user_id),
  CONSTRAINT fk_app_login_app  FOREIGN KEY (app_id)  REFERENCES app_registry(app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Optional: store a lightweight administrative note log (generic, as you requested)
CREATE TABLE admin_note (
  note_id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  scope_type         ENUM('APP','WORKSPACE','SYSTEM','USER','TOKEN') NOT NULL,
  scope_id           BIGINT UNSIGNED NOT NULL, -- interpret by scope_type at app layer
  note_text          TEXT NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (note_id),
  KEY ix_admin_note_scope (scope_type, scope_id, created_at),
  CONSTRAINT fk_admin_note_creator FOREIGN KEY (created_by_user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;