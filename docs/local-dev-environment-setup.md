# Local Development Environment Setup

How to get InteropHub running on a developer machine against a local copy of
the production database. This describes the setup the primary developer uses
on Windows. Nothing here depends on that machine, but the examples use
Windows paths and PowerShell.

No secrets are in this document or the repo. You will need two things from an
InteropHub maintainer:

- the **production backup download credentials** (user/password for the
  nightly `interophub_backup.sql.gz`), and
- a **user account in production InteropHub** (any registered email), so you
  can sign in locally once the backup is loaded. Ask for the admin flag if you
  need the admin pages.

## 1. Install the tools

| Tool | Version | Notes |
|---|---|---|
| JDK | 17 or newer | `pom.xml` compiles for release 17. Newer JDKs (e.g. 25) work. |
| Maven | 3.9+ | |
| MySQL Server | 8.x | Default port 3306. The `mysql`, `mysqldump` clients must be on `PATH`. |
| Apache Tomcat | **10.1** | Must be 10.x — the app uses Jakarta Servlet 6 (`jakarta.*`), which Tomcat 9 can't run. |
| Python | 3.9+ | Only for the database refresh script. Standard library only. |
| curl | any | Ships with Windows 10+. Only for downloading the backup. |
| Node.js | 18+ | **Optional.** Only to rebuild the bundled meeting-notes editor (`npm install`, `npm run build:meeting-workspace-notes`); the built file is committed. |

If more than one Tomcat is installed, make sure you deploy to the one actually
listening on 8080 (`Get-NetTCPConnection -LocalPort 8080`), and don't trust
`CATALINA_HOME` to tell you which that is.

## 2. Check out the code

InteropHub depends on `org.immregistries:aira-web-components`, which is **not
published to Maven Central**. Build it from source into your local Maven
repository first:

```powershell
cd C:\dev\immregistries
git clone https://github.com/immregistries/aira-web.git
git clone https://github.com/immregistries/InteropHub.git

cd aira-web
mvn install          # puts aira-web-components / aira-web-theme into ~/.m2
```

The version InteropHub needs is in its `pom.xml`
(`aira-web-components`). If the InteropHub build later fails to resolve a
newer `aira-web` version, pull `aira-web` and run `mvn install` again. See
`docs/aira-web/README.md`.

The folder layout above isn't required, but the refresh script's defaults
keep the downloaded backup in the folder *containing* your InteropHub checkout
(here `C:\dev\immregistries`).

## 3. Create the MySQL application user

The app connects with its own scoped account. The database itself is created
by the refresh script in step 5.

```sql
CREATE USER 'interophub_app'@'localhost' IDENTIFIED BY '<choose a password>';
GRANT ALL PRIVILEGES ON interophub.* TO 'interophub_app'@'localhost';
```

The refresh script uses a separate, more powerful account (root is simplest
on a dev machine) because it drops and recreates the database.

## 4. Set the application environment variables

InteropHub reads these at startup. Set them as **system** environment
variables (Tomcat usually runs as a Windows service, which doesn't see
user-level variables), then restart the Tomcat service.

| Variable | Value |
|---|---|
| `INTEROPHUB_DB_URL` | `jdbc:mysql://localhost:3306/interophub?createDatabaseIfNotExist=true&serverTimezone=UTC` |
| `INTEROPHUB_DB_USER` | `interophub_app` |
| `INTEROPHUB_DB_PASSWORD` | the password from step 3 |
| `INTEROPHUB_DB_DRIVER` | `com.mysql.cj.jdbc.Driver` |
| `INTEROPHUB_ARTIFACTS_LOCAL_DIRECTORY` | a folder for uploaded files, e.g. `C:\dev\immregistries\InteropHub-artifacts`. The Tomcat service account must be able to write to it. Without it, local file storage is disabled. |

The `HUB_ARTIFACTS_BLOB_ENDPOINT` / `HUB_ARTIFACTS_CONTAINER` /
`HUB_ARTIFACTS_SAS_TOKEN` variables configure Azure Blob storage. Leave them
unset locally.

```powershell
# elevated PowerShell
[Environment]::SetEnvironmentVariable('INTEROPHUB_DB_URL', 'jdbc:mysql://localhost:3306/interophub?createDatabaseIfNotExist=true&serverTimezone=UTC', 'Machine')
[Environment]::SetEnvironmentVariable('INTEROPHUB_DB_USER', 'interophub_app', 'Machine')
# ...and so on for the rest
Restart-Service Tomcat10   # your Tomcat service name may differ
```

## 5. Load the database

InteropHub runs Hibernate with `hbm2ddl.auto=validate`: it never creates
tables and refuses to start if the schema doesn't match. There is no
seed-from-scratch path. Every local database starts as a copy of the
production backup.

`db/scripts/refresh_local_db.py` does this in one step:

1. (with `--download`) downloads the latest production backup;
2. drops and recreates the local `interophub` database and imports the backup;
3. applies `db/local_database_refresh.sql`, which points URLs at
   `http://localhost:8080` and **turns email sending off** (see
   `db/local_database_refresh.md`);
4. applies `db/unapplied_updates.sql`, the schema/data changes not yet
   released to production;
5. regenerates `db/schema.sql` (a reference snapshot; skip with
   `--no-schema-export`).

Configure it once:

```powershell
cd C:\dev\immregistries\InteropHub
copy db\scripts\refresh_local_db.env.example db\scripts\refresh_local_db.env
notepad db\scripts\refresh_local_db.env   # fill in backup credentials and your MySQL root password
```

`refresh_local_db.env` is gitignored. You can keep the file somewhere else
and pass `--env-file <path>` instead, or set the same names as environment
variables (those win over the file).

Then:

```powershell
python db\scripts\refresh_local_db.py --download   # first time, and whenever you want fresh production data
python db\scripts\refresh_local_db.py              # reset to the backup already on disk (no download)
```

**This wipes the local database.** Anything you changed only through the
running app (admin screens, test data) is gone unless it was also written
into `db/unapplied_updates.sql`. Read `docs/database-release-practice.md`
before working on schema changes.

If the script fails at the `unapplied_updates.sql` step, production has
usually caught up with part of that file (a release went out). The database
is left as production + local settings. Tell whoever handles the release so
the file gets frozen into a `db/vX.Y_*.sql` and emptied.

### Keeping it fresh (optional)

How often you reload is up to you. The script has no schedule built in. For
an automatic daily refresh, schedule:

```powershell
python C:\dev\immregistries\InteropHub\db\scripts\refresh_local_db.py --download --once-per-day
```

`--once-per-day` makes the run a no-op once a download-and-refresh has
succeeded that day, so the job can safely be scheduled hourly (it retries
after a failure). It records its last success/failure in
`refresh_interophub_db_state.json` (next to the backup by default).

## 6. Build and deploy

```powershell
cd C:\dev\immregistries\InteropHub
mvn clean package -Plocal-tomcat-deploy "-Dtomcat.webapps.dir=C:\Program Files\Apache Software Foundation\Tomcat 10.1\webapps"
```

This builds `target\hub.war` and copies it into Tomcat's `webapps\`. Tomcat
auto-deploys it within a few seconds (or copy `target\hub.war` there by hand
after a plain `mvn clean package`). The app is at
`http://localhost:8080/hub/`.

If it doesn't start, check Tomcat's `logs\` folder. The usual causes are a
missing environment variable (Tomcat not restarted after setting it), the
`interophub_app` grant, or a schema validation error, which means the
database doesn't match the code. Run the refresh script again.

Redeploying invalidates your session, so expect to sign in again.

## 7. Sign in

Email is disabled locally, so InteropHub shows the magic link on the page
instead of sending it:

1. Go to `http://localhost:8080/hub/home`.
2. Enter the email of a user that exists in the production data and click
   **Send sign-in link**.
3. The next page shows a yellow "Dev mode: email sending is disabled" banner
   with the sign-in URL. Open it, then click **OK** on the confirmation page.

A magic link works once. Reusing it shows "Magic Link Invalid" even if you
are signed in, so check the header for your name before assuming it failed.

## 8. Apps that sign in through InteropHub

Other AIRA apps (StepIntoCDSI, Clear, etc.) redirect to InteropHub to sign
in. The refresh script already rewrites every production app URL to
`http://localhost:8080/...`, so an app deployed to the same local Tomcat
under its usual context path works without extra setup. An app that isn't in
production yet needs local-only rows in `db/local_database_refresh.sql`; see
`db/local_database_refresh.md`. For the login protocol itself, see
`docs/external-application-login-flow.md`.
