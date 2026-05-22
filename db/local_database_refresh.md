# Local Database Refresh — Developer Guide

## Overview

`local_database_refresh.sql` is run immediately after restoring a production database snapshot to the local development environment. It replaces production values with local equivalents so that InteropHub and all connected applications function correctly on `localhost`.

This script must be re-run every time the local database is refreshed from production.

---

## Background: InteropHub-authenticated Applications

InteropHub acts as a central authentication hub. External applications (e.g. StepIntoCDSI, Clear) delegate login to InteropHub via a one-time code exchange flow. For each application, two database tables must have consistent, environment-correct values:

| Table | What it controls |
|---|---|
| `app_registry` | The app's registered `default_redirect_url` — where InteropHub sends the user after authentication |
| `app_redirect_allowlist` | The set of URLs InteropHub will accept as redirect targets for that app |

Production points these URLs at the live servers (e.g. `https://informatics.immregistries.org/...`). Local development must point them at `http://localhost:8080/...`. The script handles this translation every time a prod snapshot is restored.

---

## Application Lifecycle in This Script

Every application that uses InteropHub for authentication goes through two phases in this script:

### Phase 1 — App exists locally but not yet in production

The app is under development and needs to work locally. Since the daily prod snapshot does not contain this app yet, the rows must be inserted locally after every refresh.

**Required blocks:**
- An `INSERT IGNORE` in the **APP REGISTRY — TEMPORARY LOCAL-ONLY INSERTS** section
- One or more `INSERT IGNORE` rows in the **APP REDIRECT ALLOWLIST — TEMPORARY LOCAL-ONLY INSERTS** section

`INSERT IGNORE` is used so the script is safe to re-run — if the row already exists from a previous run it is silently skipped.

### Phase 2 — App is deployed to production

Once the app appears in the production database, the daily snapshot will carry its rows. The insert blocks are no longer needed and must be removed. The update blocks (which were no-ops until now) will start working automatically on every refresh.

**Action on production deploy:**
1. Delete the `INSERT IGNORE` block(s) for that app from both sections.
2. Verify the `UPDATE` statements for that app are already present (they should have been added in Phase 1 — see below).

---

## Adding a New Application

When you begin local development on a new InteropHub-authenticated app, update this script in two places.

### Information you need

| Item | Example |
|---|---|
| `app_id` | `3` (next available integer — check `SELECT MAX(app_id) FROM app_registry`) |
| `app_code` | `myapp` |
| `app_name` | `My Application` |
| `app_description` | `Short description` |
| Production `default_redirect_url` | `https://informatics.immregistries.org/myapp/` |
| Local `default_redirect_url` | `http://localhost:8080/myapp/` |
| Production redirect URLs (one per allowlist row) | `https://informatics.immregistries.org/myapp/login`, `https://informatics.immregistries.org/myapp/` |
| Local redirect URLs (matching set) | `http://localhost:8080/myapp/login`, `http://localhost:8080/myapp/` |

> The path suffix after the domain root is always preserved exactly — only the scheme+host changes between production and local.

---

### Step 1 — Add UPDATE statements (permanent)

Add these to the existing update sections. They are no-ops until the app reaches production, but adding them now means you only need to delete inserts when that day comes.

**APP REGISTRY — URL UPDATES**
```sql
UPDATE app_registry
SET default_redirect_url = 'http://localhost:8080/myapp/'
WHERE default_redirect_url = 'https://informatics.immregistries.org/myapp/';
```

**APP REDIRECT ALLOWLIST — URL UPDATES**
```sql
UPDATE app_redirect_allowlist
SET base_url = 'http://localhost:8080/myapp/login'
WHERE base_url = 'https://informatics.immregistries.org/myapp/login';

UPDATE app_redirect_allowlist
SET base_url = 'http://localhost:8080/myapp/'
WHERE base_url = 'https://informatics.immregistries.org/myapp/';
```

---

### Step 2 — Add INSERT blocks (temporary)

Add these to the temporary insert sections. Mark them clearly with the app name and a reminder to remove them on production deploy.

**APP REGISTRY — TEMPORARY LOCAL-ONLY INSERTS**
```sql
-- My Application is not yet in production. Remove this block once it is deployed.
INSERT IGNORE INTO app_registry
  (app_id, app_code, app_name, default_redirect_url, app_description, managed_by, is_enabled, kill_switch)
VALUES
  (3, 'myapp', 'My Application', 'http://localhost:8080/myapp/', 'Short description', 'AIRA', 1, 0);
```

**APP REDIRECT ALLOWLIST — TEMPORARY LOCAL-ONLY INSERTS**
```sql
-- My Application redirect entries. Remove this block once it is deployed to production.
INSERT IGNORE INTO app_redirect_allowlist (app_id, base_url, is_enabled)
VALUES
  (3, 'http://localhost:8080/myapp/login', 1),
  (3, 'http://localhost:8080/myapp/',      1);
```

---

## When an App Goes to Production — Cleanup Checklist

1. [ ] Confirm the app row appears in the prod snapshot (`SELECT * FROM app_registry WHERE app_code = 'myapp'`).
2. [ ] Delete the `INSERT IGNORE` block for the app from **APP REGISTRY — TEMPORARY LOCAL-ONLY INSERTS**.
3. [ ] Delete the `INSERT IGNORE` block for the app from **APP REDIRECT ALLOWLIST — TEMPORARY LOCAL-ONLY INSERTS**.
4. [ ] Verify the corresponding `UPDATE` statements are present in the permanent sections.
5. [ ] Run the script against a fresh snapshot and confirm the URLs are correct.

---

## Current Application Inventory

| App | app_id | Phase | Notes |
|---|---|---|---|
| StepIntoCDSI | 1 | Production | Updates in place |
| Clear | 2 | **Pre-production** | Temporary inserts active — remove once deployed |
