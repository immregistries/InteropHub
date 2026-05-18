# Email Support in InteropHub

This document describes how email is implemented in InteropHub: the libraries used, the database schema, the service layer, the admin configuration screen, and the end-to-end flow. Use this as a blueprint for replicating the same capability in another application.

---

## Library

InteropHub uses **Jakarta Mail** (formerly JavaMail) for all SMTP operations.

**Maven dependency** (`pom.xml`):
```xml
<dependency>
  <groupId>com.sun.mail</groupId>
  <artifactId>jakarta.mail</artifactId>
  <version>2.0.1</version>
</dependency>
```

The relevant Jakarta Mail classes used are:
- `jakarta.mail.Session` — creates the mail session from `Properties` and an `Authenticator`
- `jakarta.mail.internet.MimeMessage` — constructs the message
- `jakarta.mail.internet.InternetAddress` — sets From/To addresses with display name support
- `jakarta.mail.Transport` — performs the actual SMTP send
- `jakarta.mail.Authenticator` / `jakarta.mail.PasswordAuthentication` — handles SMTP credentials
- `jakarta.mail.MessagingException` / `jakarta.mail.SendFailedException` — exception handling

---

## Database Schema

### `hub_settings` — SMTP configuration

All SMTP settings are stored in a single database row (one active row at a time). This table is created in `db/schema.sql`.

```sql
CREATE TABLE hub_settings (
  setting_id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  active             TINYINT(1) NOT NULL DEFAULT 1,
  external_base_url  VARCHAR(300) NOT NULL,   -- used to construct magic-link URLs
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
```

A seed row using Mailtrap sandbox defaults is inserted at schema creation time so the application works out of the box without further configuration.

### `auth_magic_link_send_event` — Audit log

Every step in the email send lifecycle is written to this append-only table. This enables debugging of SMTP failures, duplicate sends, and delivery confirmation.

```sql
CREATE TABLE auth_magic_link_send_event (
  send_event_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  magic_id           BIGINT UNSIGNED NULL,
  user_id            BIGINT NOT NULL,
  app_id             BIGINT UNSIGNED NULL,
  email_normalized   VARCHAR(254) NOT NULL,
  event_type         ENUM('SEND_REQUESTED','SMTP_SEND_STARTED','SMTP_SEND_SUCCEEDED','SMTP_SEND_FAILED') NOT NULL,
  event_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  request_id         CHAR(36) NULL,     -- UUID grouping all events for a single send attempt
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
  ...
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### `v_email_prospect` — Funnel reporting view

Created in `db/v0.13_email_prospect_view.sql`. Surfaces every `email_normalized` that has interacted with the system (campaign registrations, comments, subscriptions, meeting memberships) but has not yet completed full user registration. Used in admin reporting to track conversion funnel counts.

---

## JPA Model — `HubSetting`

**File:** `src/main/java/org/airahub/interophub/model/HubSetting.java`

A JPA `@Entity` mapped to the `hub_settings` table. Key fields:

| Java field | Column | Notes |
|---|---|---|
| `settingId` | `setting_id` | Auto-generated PK |
| `active` | `active` | Only the active row is used |
| `externalBaseUrl` | `external_base_url` | Base URL for constructing magic-link sign-in URLs |
| `smtpHost` | `smtp_host` | Hostname of the SMTP server |
| `smtpPort` | `smtp_port` | Integer port (1–65535) |
| `smtpUsername` | `smtp_username` | SMTP auth username |
| `smtpPassword` | `smtp_password` | SMTP auth password (stored in plain text) |
| `smtpAuth` | `smtp_auth` | Enable SMTP authentication |
| `smtpStarttls` | `smtp_starttls` | Enable STARTTLS upgrade |
| `smtpSsl` | `smtp_ssl` | Enable SSL/TLS connection (alternative to STARTTLS) |
| `smtpFromEmail` | `smtp_from_email` | From address |
| `smtpFromName` | `smtp_from_name` | From display name |

`@PrePersist` sets sensible defaults (`smtpAuth=true`, `smtpStarttls=true`, `smtpSsl=false`) and stamps `created_at` / `updated_at`. `@PreUpdate` refreshes `updated_at`.

---

## DAO Layer

### `HubSettingDao`

**File:** `src/main/java/org/airahub/interophub/dao/HubSettingDao.java`

Extends `GenericDao<HubSetting, Long>`. Key methods:

- `findActive()` — returns the first `active=true` row, ordered by `setting_id asc`
- `findFirst()` — fallback, returns the row with the lowest `setting_id`
- `saveOrUpdate(HubSetting)` — merges and commits within a transaction

Throughout the application, the pattern for loading settings is:
```java
hubSettingDao.findActive()
    .or(() -> hubSettingDao.findFirst())
    .orElse(/* default or throw */);
```

### `MagicLinkSendEventDao`

**File:** `src/main/java/org/airahub/interophub/dao/MagicLinkSendEventDao.java`

- `log(MagicLinkSendEvent)` — persists a single event row; silently absorbs failures (logs but does not re-throw) so audit failures never break the send flow
- `findRecentByUserId(Long userId, int limit)` — used by the admin user-detail screen

### `EmailProspectDao`

**File:** `src/main/java/org/airahub/interophub/dao/EmailProspectDao.java`

Queries the `v_email_prospect` view:
- `countProspects()` — total count
- `findRecentProspects(int limit)` — most recently active
- `searchProspects(String query)` — `LIKE` search on `email_normalized`

---

## Service Layer — `EmailService`

**File:** `src/main/java/org/airahub/interophub/service/EmailService.java`

Central class for all outbound email. Reads SMTP configuration from `HubSettingDao` at call time (not cached), so settings changes take effect immediately.

### Key methods

#### `sendTestEmail(String recipientEmail)`
Sends a minimal plain-text confirmation message directly to the given address. Bypasses the email-enabled flag — intended for explicit admin use from the Settings screen. Validates that settings exist before attempting send.

#### `sendWelcomeEmail(String recipientEmail)`
Overload that builds the home/login link from `HubSetting.externalBaseUrl` automatically.

#### `sendWelcomeEmail(String recipientEmail, String loginLink)`
Overload that accepts an explicit link.

#### `sendWelcomeEmailWithResult(String recipientEmail, String loginLink)`
The primary method called by the registration servlet. Returns `SendWelcomeEmailResult` containing the SMTP `Message-ID` and the provider hostname for audit logging.

### SMTP property mapping

```java
Properties props = new Properties();
props.put("mail.smtp.host",               settings.getSmtpHost());
props.put("mail.smtp.port",               String.valueOf(settings.getSmtpPort()));
props.put("mail.smtp.auth",               String.valueOf(settings.getSmtpAuth()));
props.put("mail.smtp.starttls.enable",    String.valueOf(settings.getSmtpStarttls()));
props.put("mail.smtp.starttls.required",  String.valueOf(settings.getSmtpStarttls()));
props.put("mail.smtp.ssl.enable",         String.valueOf(settings.getSmtpSsl()));
props.put("mail.smtp.ssl.trust",          "*");
props.put("mail.smtp.connectiontimeout",  "10000");  // 10 seconds
props.put("mail.smtp.timeout",            "10000");  // 10 seconds
```

The `From` address and display name are taken from `smtpFromEmail` / `smtpFromName`, falling back to the username / literal "InteropHub" if blank.

### Validation

`validateSettings(HubSetting)` throws `IllegalStateException` (with a user-friendly message pointing to `/admin/settings`) if:
- `smtpHost` is blank
- `smtpPort` is null or out of range
- `smtpAuth` is true but `smtpUsername` or `smtpPassword` is blank

### Exception types

- `EmailService.SendWelcomeEmailResult` — value object returned on success: contains `smtpMessageId` and `smtpProvider`
- `EmailService.EmailSendException extends IllegalStateException` — thrown on SMTP failure; carries `smtpReplyCode` and `smtpProvider` for structured audit logging

---

## Admin Configuration Screen — `SettingsServlet`

**File:** `src/main/java/org/airahub/interophub/servlet/SettingsServlet.java`  
**URL mapping:** `/admin/settings` and `/settings`  
**Linked from:** Admin navigation bar as "Hub Settings"

### Access control
GET and POST both call `ensureAdminAccess()`, which checks:
1. A valid authenticated session cookie is present
2. The user's email domain ends with `@immregistries.org` (configured in `AuthFlowService.ADMIN_EMAIL_DOMAIN`)

Unauthenticated users are redirected to `/home`. Non-admin users receive a 403 Forbidden page.

### GET — display form
Loads the current active settings row (or creates defaults in memory if none exist). Renders a plain HTML form using `AdminShellRenderer` for consistent admin chrome.

### POST — save and optionally test
1. Loads the current settings row
2. Calls `populateFromRequest()` to bind all form fields, with validation:
   - All fields are required (including SMTP host, port, username, password)
   - Port is validated as an integer between 1 and 65535
3. Saves via `hubSettingDao.saveOrUpdate()`
4. If the `sendTestEmail` checkbox is checked, calls `emailService.sendTestEmail()` with the logged-in admin's own email address and displays the result inline

### Form fields rendered

| Field | Type | Notes |
|---|---|---|
| Active | Checkbox | Whether these settings are active |
| External Base URL | Text | Used to build magic-link URLs in emails |
| SMTP Host | Text | |
| SMTP Port | Number (1–65535) | |
| SMTP Username | Text | |
| SMTP Password | Text | Displayed in plain text |
| SMTP Auth | Checkbox | |
| SMTP STARTTLS | Checkbox | |
| SMTP SSL | Checkbox | |
| From Email | Email | |
| From Name | Text | |
| Send test email after saving | Checkbox | Sends to the admin's own address |

---

## User-Facing Registration + Email Flow — `SendWelcomeEmailServlet`

**File:** `src/main/java/org/airahub/interophub/servlet/SendWelcomeEmailServlet.java`  
**URL mapping:** `/send-welcome-email`

This servlet handles both new-user registration and email dispatch. It renders server-side HTML (no template engine).

### Step-by-step flow

1. **User submits email** on the home page (POST to `/send-welcome-email` with `email` parameter)
2. **Email validation** — normalized to lowercase, trimmed, checked for `@` character, max 254 chars (local part max 64)
3. **External auth forwarding** — if the request arrived via an OAuth-style `app_code`/`return_to` flow, those parameters are parsed and stored in the HTTP session via `AuthFlowService`
4. **User lookup** — `AuthService.findUserByEmail(normalizedEmail)`:
   - **Existing user** → skip to step 6
   - **New user, first visit** → render the profile collection form (see below) and return
5. **Profile form submission** (`profileSubmission` hidden field set) — validates first name, last name, organization, role title, and acceptance of all required legal terms. Creates the user via `AuthService.registerUser()`.
6. **Issue magic link** — `AuthFlowService.issueMagicLinkWithMetadata()` generates a single-use token, hashes it with SHA-256, stores it in `auth_magic_link`, and returns the full URL
7. **Audit: `SEND_REQUESTED`** — logged to `auth_magic_link_send_event`
8. **Audit: `SMTP_SEND_STARTED`** — logged
9. **Send email** — `EmailService.sendWelcomeEmailWithResult(email, magicLinkUrl)` transmits via SMTP
10. **Audit: `SMTP_SEND_SUCCEEDED`** or **`SMTP_SEND_FAILED`** — logged with SMTP Message-ID, provider, reply code, and exception details as applicable
11. **Render result** — on success: "Email Sent" confirmation page; on failure: error message with reason

### Profile registration form fields

| Field | Max length | Required |
|---|---|---|
| First Name | 60 | Yes |
| Last Name | 60 | No |
| Organization | 120 | Yes |
| Role Title | 120 | Yes |
| Legal terms checkboxes | — | All required terms must be checked |

---

## Admin User Detail Screen

**File:** `src/main/java/org/airahub/interophub/servlet/AdminUserDetailServlet.java`

Calls `magicLinkSendEventDao.findRecentByUserId(userId, 50)` and renders the last 50 send events for a user as a table, including event type, timestamp, SMTP Message-ID, provider, reply code, and any error details. This allows admins to troubleshoot email delivery problems per user.

---

## Magic Link Model

**File:** `src/main/java/org/airahub/interophub/model/MagicLink.java`  
**Table:** `auth_magic_link`

Magic links are single-use, time-limited sign-in tokens embedded in welcome emails. Key fields:

- `tokenHash` — SHA-256 of the raw token (never stored in plain text)
- `expiresAt` — token expiry (short-lived)
- `consumedAt` — set when the link is clicked; prevents reuse
- `appId`, `returnTo`, `stateNonce`, `requestedUrl` — carry OAuth/external-app context through the email round-trip

The `MagicLinkServlet` at `/magic-link` consumes the token, validates it, and redirects the user to the appropriate destination.

---

## Email Address Normalization

All email addresses stored in the database are normalized before storage:
```java
String normalized = rawEmail.trim().toLowerCase();
```

Two columns are typically stored side by side:
- `email` — original as entered by the user (for display)
- `email_normalized` — lowercase trimmed (for lookup and deduplication)

---

## Recommended SMTP Providers for Development

The schema seed row and the in-code defaults both target **Mailtrap** sandbox:

| Setting | Default value |
|---|---|
| SMTP Host | `sandbox.smtp.mailtrap.io` |
| SMTP Port | `2525` |
| SMTP Auth | `true` |
| SMTP STARTTLS | `true` |
| SMTP SSL | `false` |
| From Email | `no-reply@interophub.local` |
| From Name | `InteropHub` |

Replace the username and password with your Mailtrap mailbox credentials from the Mailtrap dashboard. For production, swap host/port/credentials for your production SMTP provider (e.g. SendGrid, AWS SES, Postmark) without changing any code.

---

## Summary of Files to Replicate

| File | Purpose |
|---|---|
| `pom.xml` | Add `com.sun.mail:jakarta.mail:2.0.1` |
| `db/schema.sql` | `hub_settings` and `auth_magic_link_send_event` DDL |
| `db/v0.13_email_prospect_view.sql` | `v_email_prospect` view (optional, for funnel reporting) |
| `model/HubSetting.java` | JPA entity for SMTP config |
| `dao/HubSettingDao.java` | DAO for loading and saving settings |
| `dao/MagicLinkSendEventDao.java` | DAO for appending send audit events |
| `dao/EmailProspectDao.java` | DAO for prospect funnel queries |
| `dao/EmailProspectBrowseRow.java` | Read-only result row for prospect queries |
| `service/EmailService.java` | Core SMTP send logic; `sendTestEmail`, `sendWelcomeEmailWithResult` |
| `servlet/SettingsServlet.java` | Admin screen at `/admin/settings` to configure SMTP |
| `servlet/SendWelcomeEmailServlet.java` | User registration + magic-link email dispatch |
| `WEB-INF/web.xml` | Servlet URL mappings |

---

## Adding New Email Types (current pattern)

All email added after the initial implementation must follow this pattern. It keeps message content out of servlets and creates an audit record for every delivery.

### The four moving parts

| What | Where | Rule |
|---|---|---|
| Reason code | `EmailReason` | One `public static final String` per email type. Never rename a value once rows exist in production. |
| Subject + body | `EmailTemplates` | One `subject()` + one `body(…)` method pair per type. All message text lives here — not in servlets. |
| Send + log | Calling servlet | Compose with `EmailTemplates`, call `EmailService.send()`, write a row to `email_send_log` via `EmailSendLogDao`. |
| DB table | `email_send_log` | Already exists. No schema changes needed for new email types. |

### Checklist

**1. Add a constant to `EmailReason.java`**

```java
/** Brief description of when this email is sent. */
public static final String MY_NEW_EMAIL = "MY_NEW_EMAIL";
```

**2. Add methods to `EmailTemplates.java`**

```java
// -------------------------------------------------------------------------
// MY_NEW_EMAIL — brief description
// -------------------------------------------------------------------------

public static String myNewEmailSubject() {
    return "Your subject line here";
}

public static String myNewEmailBody(String someParam) {
    return "Body text.\n\n" + someParam + "\n";
}
```

**3. Send and log in the calling servlet**

```java
String subject = EmailTemplates.myNewEmailSubject();
String body    = EmailTemplates.myNewEmailBody(someParam);
EmailService.SendResult result = emailService.send(recipientEmail, subject, body);

EmailSendLog logEntry = new EmailSendLog();
logEntry.setEmailReason(EmailReason.MY_NEW_EMAIL);
logEntry.setRecipientEmail(recipientEmail);           // as provided
logEntry.setRecipientEmailNormalized(normalizedEmail); // lowercase trimmed
logEntry.setUserId(user.getUserId());                 // null if no account yet
logEntry.setSubject(subject);
logEntry.setBodyText(body);
logEntry.setSmtpMessageId(result.getSmtpMessageId());
logEntry.setSmtpProvider(result.getSmtpProvider());
logEntry.setMagicId(magicId);                         // null if not a magic-link email
emailSendLogDao.log(logEntry);
```

`EmailSendLogDao.log()` swallows and logs any persistence exception so that a logging failure never blocks delivery.

### `EmailService.send()` contract

- Throws `IllegalArgumentException` if `recipientEmail`, `subject`, or `bodyText` are blank.  
- Throws `EmailService.EmailSendException` on SMTP failure (carries `smtpReplyCode` and `smtpProvider`). Do not swallow this silently in critical flows — callers should log a failure event before re-throwing or surfacing an error to the user.
- Returns `EmailService.SendResult` with `smtpMessageId` and `smtpProvider` on success.

### Where sent emails appear

- `/admin/emails` — last 20 sent, searchable by address (up to 100 results)
- `/admin/users/{id}` — "Email Log" section, last 50 for that user
