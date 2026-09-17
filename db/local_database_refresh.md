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

The translation is a **prefix swap**, not a per-app lookup: any `default_redirect_url` / `base_url` that starts with `@prod_origin` (`https://informatics.immregistries.org`) has that prefix replaced with `@local_origin` (`http://localhost:8080`), preserving whatever path follows. This means it covers every app that exists in the production snapshot automatically — nothing needs to be added to the script when a new app reaches production.

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

Once the app appears in the production database, the daily snapshot will carry its rows, already pointed at the production origin. The generalized prefix-swap `UPDATE` statements pick these up automatically — no script change needed.

**Action on production deploy:**
1. Delete the `INSERT IGNORE` block(s) for that app from both sections.
2. Run the script against a fresh snapshot and confirm the app's URLs now read `http://localhost:8080/...`.

---

## Adding a New Application

When you begin local development on a new InteropHub-authenticated app, the **URL UPDATES** sections need no changes — the prefix swap already covers any URL starting with `https://informatics.immregistries.org`, for any app, present or future. You only need to add temporary local-only inserts so the app works locally before it exists in the production snapshot.

### Information you need

| Item | Example |
|---|---|
| `app_id` | `3` (next available integer — check `SELECT MAX(app_id) FROM app_registry`) |
| `app_code` | `myapp` |
| `app_name` | `My Application` |
| `app_description` | `Short description` |
| Local `default_redirect_url` | `http://localhost:8080/myapp/` |
| Local redirect URLs (one per allowlist row) | `http://localhost:8080/myapp/login`, `http://localhost:8080/myapp/` |

---

### Add INSERT blocks (temporary)

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
4. [ ] Run the script against a fresh snapshot and confirm the URLs read `http://localhost:8080/...` (the generalized `UPDATE` statements need no per-app change).

---

## Current Application Inventory

| App | app_id | Phase | Notes |
|---|---|---|---|
| StepIntoCDSI | 1 | Production | Covered by the generalized URL swap |
| Clear | 2 | **Pre-production** | Temporary inserts active — remove once deployed |
| Mismo | 3 | Production | Covered by the generalized URL swap |
