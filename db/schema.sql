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
  email_enabled      TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'When 0, all outbound email is silently suppressed (no SMTP attempt).',
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
  smtp_from_name,
  email_enabled
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
  'InteropHub',
  1
);

-- -------------------------
-- USERS / IDENTITY
-- -------------------------
CREATE TABLE auth_user (
  user_id            BIGINT NOT NULL AUTO_INCREMENT,
  email              VARCHAR(254) NOT NULL,
  email_normalized   VARCHAR(254) NOT NULL,  -- lowercase trimmed
  display_name       VARCHAR(160) NULL,      -- optional override; when NULL, first_name + last_name is used
  first_name         VARCHAR(100) NULL,
  last_name          VARCHAR(100) NULL,
  organization       VARCHAR(200) NULL,
  role_title         VARCHAR(200) NULL,      -- free text
  timezone_id        VARCHAR(64) NULL,         -- IANA timezone id e.g. America/New_York
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

-- -------------------------
-- EMERGING STANDARDS TOPICS
-- -------------------------
CREATE TABLE es_topic (
  es_topic_id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  topic_code          VARCHAR(80) NOT NULL,
  topic_name          VARCHAR(140) NOT NULL,
  description         TEXT NULL,
  neighborhood        VARCHAR(120) NULL,
  priority_iis        INT NOT NULL DEFAULT 0,
  priority_ehr        INT NOT NULL DEFAULT 0,
  priority_cdc        INT NOT NULL DEFAULT 0,
  stage               VARCHAR(80) NULL,         -- e.g. 'Pre-publication', 'Published'
  status              ENUM('ACTIVE','RETIRED','ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
  created_by_user_id  BIGINT NOT NULL,
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_topic_id),
  UNIQUE KEY uq_es_topic_code (topic_code),
  KEY ix_es_topic_status (status),
  CONSTRAINT fk_es_topic_creator FOREIGN KEY (created_by_user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------
-- EMERGING STANDARDS CAMPAIGNS
-- -------------------------
-- A bounded interest-collection effort (Deep Dive, virtual session, etc.).
-- campaign_type is VARCHAR so new meeting types can be added without ALTER.
CREATE TABLE es_campaign (
  es_campaign_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  campaign_code       VARCHAR(80) NOT NULL,
  campaign_name       VARCHAR(160) NOT NULL,
  description         TEXT NULL,
  campaign_type       VARCHAR(80) NOT NULL DEFAULT 'DEEP_DIVE',
  status              ENUM('DRAFT','ACTIVE','CLOSED','ARCHIVED') NOT NULL DEFAULT 'DRAFT',
  current_round_no    TINYINT UNSIGNED NOT NULL DEFAULT 1,
  allow_topic_comments    TINYINT(1) NOT NULL DEFAULT 1,
  allow_general_comments  TINYINT(1) NOT NULL DEFAULT 1,
  start_at            DATETIME NULL,
  end_at              DATETIME NULL,
  created_by_user_id  BIGINT NOT NULL,
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (es_campaign_id),
  UNIQUE KEY uq_es_campaign_code (campaign_code),
  KEY ix_es_campaign_status (status),
  CONSTRAINT fk_es_campaign_creator FOREIGN KEY (created_by_user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Topics included in a specific campaign (with Deep Dive grouping metadata).
-- topic_set_no: 1-7 for Deep Dive sets; table_no: 1-14 for table assignments.
CREATE TABLE es_campaign_topic (
  es_campaign_topic_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_campaign_id        BIGINT UNSIGNED NOT NULL,
  es_topic_id           BIGINT UNSIGNED NOT NULL,
  topic_set_no          TINYINT UNSIGNED NULL,   -- which set (e.g., 1-7 for Deep Dive)
  table_no              TINYINT UNSIGNED NULL,   -- which table (e.g., 1-14 for Deep Dive)
  display_order         INT NOT NULL DEFAULT 0,
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (es_campaign_topic_id),
  UNIQUE KEY uq_es_campaign_topic (es_campaign_id, es_topic_id, table_no),
  KEY ix_es_ct_set   (es_campaign_id, topic_set_no, display_order),
  KEY ix_es_ct_table (es_campaign_id, table_no, display_order),
  CONSTRAINT fk_es_ct_campaign FOREIGN KEY (es_campaign_id) REFERENCES es_campaign(es_campaign_id),
  CONSTRAINT fk_es_ct_topic    FOREIGN KEY (es_topic_id)    REFERENCES es_topic(es_topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Campaign registration check-in records used by table-round vote linkage.
CREATE TABLE es_campaign_registration (
  es_campaign_registration_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_campaign_id              BIGINT UNSIGNED NOT NULL,
  first_name                  VARCHAR(100) NOT NULL,
  last_name                   VARCHAR(100) NULL,
  email                       VARCHAR(254) NULL,
  email_normalized            VARCHAR(254) NULL,
  general_updates_opt_in      TINYINT(1) NOT NULL DEFAULT 0,
  session_key                 VARCHAR(128) NULL,
  created_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (es_campaign_registration_id),
  KEY ix_es_reg_campaign_time (es_campaign_id, created_at),
  KEY ix_es_reg_campaign_email (es_campaign_id, email_normalized),
  KEY ix_es_reg_session_campaign (session_key, es_campaign_id),
  CONSTRAINT fk_es_campaign_registration_campaign
    FOREIGN KEY (es_campaign_id) REFERENCES es_campaign(es_campaign_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------
-- EXPRESSIONS OF INTEREST (VOTES)
-- -------------------------
-- One record per session per selected topic per campaign+table+round.
-- Overwrite behavior is enforced at app layer by deleting the current
-- campaign+table+round+session selection set before insert.
CREATE TABLE es_interest (
  es_interest_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_campaign_id      BIGINT UNSIGNED NOT NULL,
  es_topic_id         BIGINT UNSIGNED NOT NULL,
  es_campaign_registration_id BIGINT UNSIGNED NULL,
  session_key         VARCHAR(128) NULL,
  table_no            TINYINT UNSIGNED NOT NULL,
  round_no            TINYINT UNSIGNED NOT NULL,
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (es_interest_id),
  KEY ix_es_interest_campaign_table_round_topic (es_campaign_id, table_no, round_no, es_topic_id),
  KEY ix_es_interest_campaign_table_round_session (es_campaign_id, table_no, round_no, session_key),
  KEY ix_es_interest_campaign_registration (es_campaign_registration_id),
  CONSTRAINT fk_es_interest_campaign FOREIGN KEY (es_campaign_id) REFERENCES es_campaign(es_campaign_id),
  CONSTRAINT fk_es_interest_topic    FOREIGN KEY (es_topic_id)    REFERENCES es_topic(es_topic_id),
  CONSTRAINT fk_es_interest_campaign_registration
    FOREIGN KEY (es_campaign_registration_id)
    REFERENCES es_campaign_registration(es_campaign_registration_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------
-- COMMENTS
-- -------------------------
-- comment_type='TOPIC'  requires es_topic_id.
-- comment_type='GENERAL' or 'NEW_TOPIC_SUGGESTION' has es_topic_id=NULL.
CREATE TABLE es_comment (
  es_comment_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_campaign_id      BIGINT UNSIGNED NOT NULL,
  es_topic_id         BIGINT UNSIGNED NULL,     -- NULL for GENERAL / NEW_TOPIC_SUGGESTION
  user_id             BIGINT NULL,
  session_key         VARCHAR(128) NULL,
  first_name          VARCHAR(100) NOT NULL,
  last_name           VARCHAR(100) NULL,
  email               VARCHAR(254) NULL,
  email_normalized    VARCHAR(254) NULL,
  comment_type        ENUM('TOPIC','GENERAL','NEW_TOPIC_SUGGESTION') NOT NULL,
  comment_text        TEXT NOT NULL,
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (es_comment_id),
  KEY ix_es_comment_campaign (es_campaign_id, comment_type, created_at),
  KEY ix_es_comment_topic    (es_topic_id, created_at),
  KEY ix_es_comment_user     (user_id, created_at),
  CONSTRAINT fk_es_comment_campaign FOREIGN KEY (es_campaign_id) REFERENCES es_campaign(es_campaign_id),
  CONSTRAINT fk_es_comment_topic    FOREIGN KEY (es_topic_id)    REFERENCES es_topic(es_topic_id),
  CONSTRAINT fk_es_comment_user     FOREIGN KEY (user_id)        REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------
-- TOPIC REVIEWS (INTERNAL)
-- -------------------------
-- One score per user per topic per campaign.
-- Updates overwrite the existing row via app-layer upsert.
CREATE TABLE es_topic_review (
  es_topic_review_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_campaign_id          BIGINT UNSIGNED NOT NULL,
  es_topic_id             BIGINT UNSIGNED NOT NULL,
  user_id                 BIGINT NOT NULL,
  community_value_score   TINYINT UNSIGNED NOT NULL,
  created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_topic_review_id),
  UNIQUE KEY uq_es_topic_review_campaign_topic_user (es_campaign_id, es_topic_id, user_id),
  KEY ix_es_topic_review_campaign_user (es_campaign_id, user_id, updated_at),
  KEY ix_es_topic_review_campaign_topic (es_campaign_id, es_topic_id, updated_at),
  CONSTRAINT fk_es_topic_review_campaign FOREIGN KEY (es_campaign_id) REFERENCES es_campaign(es_campaign_id),
  CONSTRAINT fk_es_topic_review_topic    FOREIGN KEY (es_topic_id)    REFERENCES es_topic(es_topic_id),
  CONSTRAINT fk_es_topic_review_user     FOREIGN KEY (user_id)        REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------
-- DURABLE SUBSCRIPTIONS
-- -------------------------
-- Separate from campaign votes; survive across campaigns.
-- GENERAL_ES: es_topic_id IS NULL, subscription_type='GENERAL_ES'
-- TOPIC:      es_topic_id IS NOT NULL, subscription_type='TOPIC'
-- Uniqueness: app-layer only (MySQL does not enforce unique across nullable columns).
-- unsubscribe_token_hash: SHA-256 of a random token issued at creation;
--   used for one-click unsubscribe links in future email sends.
CREATE TABLE es_subscription (
  es_subscription_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id                 BIGINT NULL,
  email                   VARCHAR(254) NOT NULL,
  email_normalized        VARCHAR(254) NOT NULL,
  es_topic_id             BIGINT UNSIGNED NULL,   -- NULL = GENERAL_ES subscription
  subscription_type       ENUM('GENERAL_ES','TOPIC') NOT NULL,
  status                  ENUM('SUBSCRIBED','CHAMPION','UNSUBSCRIBED') NOT NULL DEFAULT 'SUBSCRIBED',  -- CHAMPION only valid for TOPIC type
  source_campaign_id      BIGINT UNSIGNED NULL,   -- which campaign produced this subscription
  unsubscribe_token_hash  BINARY(32) NULL,        -- SHA-256(raw token); for email-link unsubscribe
  created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  unsubscribed_at         DATETIME NULL,
  PRIMARY KEY (es_subscription_id),
  UNIQUE KEY uq_es_sub_token (unsubscribe_token_hash),
  KEY ix_es_sub_email  (email_normalized, status),
  KEY ix_es_sub_user   (user_id, status),
  KEY ix_es_sub_topic  (es_topic_id, status),
  CONSTRAINT fk_es_sub_user     FOREIGN KEY (user_id)            REFERENCES auth_user(user_id),
  CONSTRAINT fk_es_sub_topic    FOREIGN KEY (es_topic_id)        REFERENCES es_topic(es_topic_id),
  CONSTRAINT fk_es_sub_campaign FOREIGN KEY (source_campaign_id) REFERENCES es_campaign(es_campaign_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------
-- TOPIC MEETINGS (OPTIONAL)
-- -------------------------
-- One topic may have zero or one meeting row.
-- status='DISABLED' represents meeting support turned off while preserving history.
CREATE TABLE es_topic_meeting (
  es_topic_meeting_id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_topic_id            BIGINT UNSIGNED NOT NULL,
  meeting_name           VARCHAR(160) NOT NULL,
  meeting_description    TEXT NULL,
  join_requires_approval TINYINT(1) NOT NULL DEFAULT 0,
  status                 ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
  disabled_at            DATETIME NULL,
  disabled_by_user_id    BIGINT NULL,
  online_meeting_url     VARCHAR(2048) NULL,
  online_meeting_details TEXT NULL,
  created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_topic_meeting_id),
  UNIQUE KEY uq_es_topic_meeting_topic (es_topic_id),
  KEY ix_es_topic_meeting_status (status),
  CONSTRAINT fk_es_topic_meeting_topic FOREIGN KEY (es_topic_id) REFERENCES es_topic(es_topic_id),
  CONSTRAINT fk_es_topic_meeting_disabled_user FOREIGN KEY (disabled_by_user_id) REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------
-- TOPIC MEETING MEMBERS
-- -------------------------
-- Membership supports both authenticated users and email-only participants.
CREATE TABLE es_topic_meeting_member (
  es_topic_meeting_member_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_topic_meeting_id        BIGINT UNSIGNED NOT NULL,
  user_id                    BIGINT NULL,
  email                      VARCHAR(254) NOT NULL,
  email_normalized           VARCHAR(254) NOT NULL,
  membership_status          ENUM('REQUESTED','APPROVED','DECLINED','REMOVED') NOT NULL DEFAULT 'REQUESTED',
  source_campaign_id         BIGINT UNSIGNED NULL,
  approved_by_user_id        BIGINT NULL,
  approved_at                DATETIME NULL,
  created_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_topic_meeting_member_id),
  UNIQUE KEY uq_es_topic_meeting_member_email (es_topic_meeting_id, email_normalized),
  KEY ix_es_topic_meeting_member_status (es_topic_meeting_id, membership_status),
  KEY ix_es_topic_meeting_member_email (email_normalized, membership_status),
  KEY ix_es_topic_meeting_member_user (user_id),
  CONSTRAINT fk_es_tmm_meeting FOREIGN KEY (es_topic_meeting_id)
    REFERENCES es_topic_meeting(es_topic_meeting_id),
  CONSTRAINT fk_es_tmm_user FOREIGN KEY (user_id)
    REFERENCES auth_user(user_id),
  CONSTRAINT fk_es_tmm_source_campaign FOREIGN KEY (source_campaign_id)
    REFERENCES es_campaign(es_campaign_id),
  CONSTRAINT fk_es_tmm_approved_by_user FOREIGN KEY (approved_by_user_id)
    REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- es_meeting
-- Standalone meeting record for Emerging Standards working group sessions.
-- ---------------------------------------------------------------------------
CREATE TABLE es_meeting (
  es_meeting_id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_topic_meeting_id   BIGINT UNSIGNED NOT NULL,
  meeting_key           VARCHAR(80) NULL,              -- optional human-readable slug
  meeting_name          VARCHAR(160) NOT NULL,
  meeting_description   TEXT NULL,
  scheduled_start       DATETIME NOT NULL,
  scheduled_end         DATETIME NULL,
  timezone_id           VARCHAR(64) NULL,              -- IANA timezone id
  status                ENUM('DRAFT','PROPOSED','FINALIZED','COMPLETED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  created_by_user_id    BIGINT UNSIGNED NOT NULL,
  finalized_at          DATETIME NULL,
  completed_at          DATETIME NULL,
  cancelled_at          DATETIME NULL,
  cancellation_reason   TEXT NULL,
  online_meeting_url    VARCHAR(2048) NULL,
  online_meeting_details TEXT NULL,
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_meeting_id),
  KEY ix_es_meeting_topic_meeting (es_topic_meeting_id),
  KEY ix_es_meeting_scheduled_start (scheduled_start),
  KEY ix_es_meeting_status (status),
  KEY ix_es_meeting_key (meeting_key),
  CONSTRAINT fk_es_meeting_topic_meeting FOREIGN KEY (es_topic_meeting_id)
    REFERENCES es_topic_meeting(es_topic_meeting_id),
  CONSTRAINT fk_es_meeting_created_by FOREIGN KEY (created_by_user_id)
    REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- es_meeting_agenda_item
-- An item on a specific meeting's agenda. Can optionally be linked to an ES topic.
-- ---------------------------------------------------------------------------
CREATE TABLE es_meeting_agenda_item (
  es_meeting_agenda_item_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_meeting_id              BIGINT UNSIGNED NOT NULL,
  es_topic_id                BIGINT UNSIGNED NULL,      -- optional link to an ES topic
  display_order              INT NOT NULL DEFAULT 0,
  title                      VARCHAR(200) NOT NULL,
  agenda_markdown            TEXT NULL,
  time_minutes               INT NULL,                  -- estimated slot length in minutes
  status                     ENUM('DRAFT','PROPOSED','ACCEPTED','NEEDS_REVISION','POSTPONED','COVERED','NOT_COVERED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  proposed_by_user_id        BIGINT UNSIGNED NULL,
  accepted_at                DATETIME NULL,
  postponed_to_meeting_id    BIGINT UNSIGNED NULL,      -- target meeting when POSTPONED
  status_note                TEXT NULL,
  created_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_meeting_agenda_item_id),
  KEY ix_es_mai_meeting_order (es_meeting_id, display_order),
  KEY ix_es_mai_topic (es_topic_id),
  KEY ix_es_mai_status (status),
  CONSTRAINT fk_es_mai_meeting FOREIGN KEY (es_meeting_id)
    REFERENCES es_meeting(es_meeting_id),
  CONSTRAINT fk_es_mai_topic FOREIGN KEY (es_topic_id)
    REFERENCES es_topic(es_topic_id),
  CONSTRAINT fk_es_mai_proposed_by FOREIGN KEY (proposed_by_user_id)
    REFERENCES auth_user(user_id),
  CONSTRAINT fk_es_mai_postponed_to FOREIGN KEY (postponed_to_meeting_id)
    REFERENCES es_meeting(es_meeting_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- es_agenda_item_presenter
-- Associates a presenter (by email, optionally by user_id) with an agenda item.
-- ---------------------------------------------------------------------------
CREATE TABLE es_agenda_item_presenter (
  es_agenda_item_presenter_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_meeting_agenda_item_id    BIGINT UNSIGNED NOT NULL,
  user_id                      BIGINT UNSIGNED NULL,    -- NULL if invited person has no account
  email                        VARCHAR(254) NOT NULL,
  email_normalized             VARCHAR(254) NOT NULL,
  display_name                 VARCHAR(160) NULL,
  presenter_role               ENUM('LEAD','SUPPORTING','FACILITATOR','REQUESTED_REVIEWER') NOT NULL DEFAULT 'LEAD',
  status                       ENUM('PROPOSED','INVITED','INVITE_BLOCKED','ACCEPTED','DECLINED','NEEDS_CHANGES','REMOVED') NOT NULL DEFAULT 'PROPOSED',
  response_note                TEXT NULL,
  responded_at                 DATETIME NULL,
  created_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (es_agenda_item_presenter_id),
  KEY ix_es_aip_item_status (es_meeting_agenda_item_id, status),
  KEY ix_es_aip_email_status (email_normalized, status),
  CONSTRAINT fk_es_aip_agenda_item FOREIGN KEY (es_meeting_agenda_item_id)
    REFERENCES es_meeting_agenda_item(es_meeting_agenda_item_id),
  CONSTRAINT fk_es_aip_user FOREIGN KEY (user_id)
    REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- es_agenda_item_comment
-- Append-only comments and notes attached to an agenda item. No updates.
-- ---------------------------------------------------------------------------
CREATE TABLE es_agenda_item_comment (
  es_agenda_item_comment_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  es_meeting_agenda_item_id  BIGINT UNSIGNED NOT NULL,
  user_id                    BIGINT UNSIGNED NULL,
  email                      VARCHAR(254) NULL,
  comment_type               ENUM('COMMENT','CHANGE_REQUEST','POSTPONE_REQUEST','DECLINE_REASON','MEETING_NOTE') NOT NULL DEFAULT 'COMMENT',
  comment_markdown           TEXT NOT NULL,
  created_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (es_agenda_item_comment_id),
  KEY ix_es_aic_item_created (es_meeting_agenda_item_id, created_at),
  CONSTRAINT fk_es_aic_agenda_item FOREIGN KEY (es_meeting_agenda_item_id)
    REFERENCES es_meeting_agenda_item(es_meeting_agenda_item_id),
  CONSTRAINT fk_es_aic_user FOREIGN KEY (user_id)
    REFERENCES auth_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------
-- EMAIL SEND LOG
-- -------------------------
-- Business-level audit trail of every outbound email attempt from InteropHub.
-- One row per send attempt (including suppressed/blocked outcomes).
-- Stores reason, subject, and body so admins can see what was attempted and why.
-- Magic link emails are dual-recorded here and in auth_magic_link_send_event.
CREATE TABLE email_send_log (
  email_log_id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  email_reason                 VARCHAR(80) NOT NULL,           -- stable code; see EmailReason.java for all values
  recipient_email              VARCHAR(254) NOT NULL,
  recipient_email_normalized   VARCHAR(254) NOT NULL,
  user_id                      BIGINT NULL,                    -- FK auth_user; NULL if recipient has no account
  subject                      VARCHAR(500) NOT NULL,
  body_text                    TEXT NULL,
  sent_at                      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  smtp_message_id              VARCHAR(255) NULL,
  smtp_provider                VARCHAR(80) NULL,
  magic_id                     BIGINT UNSIGNED NULL,           -- cross-ref to auth_magic_link for magic link emails
  PRIMARY KEY (email_log_id),
  KEY ix_email_log_email_time   (recipient_email_normalized, sent_at),
  KEY ix_email_log_user_time    (user_id, sent_at),
  KEY ix_email_log_reason_time  (email_reason, sent_at),
  KEY ix_email_log_sent_at      (sent_at),
  CONSTRAINT fk_email_log_user  FOREIGN KEY (user_id)  REFERENCES auth_user(user_id),
  CONSTRAINT fk_email_log_magic FOREIGN KEY (magic_id) REFERENCES auth_magic_link(magic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -------------------------
-- DANDELION DAILY SYNC CONFIG
-- -------------------------
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

-- -------------------------
-- DANDELION DAILY SYNC QUEUE
-- -------------------------
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