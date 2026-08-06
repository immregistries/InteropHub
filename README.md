# InteropHub

InteropHub is the coordination and access hub for FHIR Connectathon and implementation guide (IG) development.

Its core purpose is to give software developers and testers one place to:

- discover testing systems to connect to,
- obtain credentials and access paths for those systems,
- coordinate participation in connectathon activities,
- report and track progress toward connectathon goals.

## What This App Is For

InteropHub currently serves two primary functions:

1. Provide access to testing applications so usage can be coordinated as part of FHIR Connectathon and IG development.
2. Support connectathon processes for connecting participants and reporting progress toward connectathon goals.

In short, InteropHub is the one-stop place for implementers to find systems, authenticate, and participate in organized testing workflows.

## Core Capabilities

- External application login handoff via one-time code exchange
- User sign-in via magic link and managed session cookie
- Welcome/dashboard experience listing available applications
- Connectathon-oriented workspace and coordination data model

## External App Integration

For external apps that initiate login through InteropHub, see:

- `docs/external-application-login-flow.md`

That guide covers:

- required `/home` query parameters,
- callback behavior (`code` and `state`),
- server-side code exchange at `/api/auth/exchange`,
- response payload and integration checklist.

## Minimal Run/Build Notes

- Build: `mvn clean package`
- WAR output: `target/hub.war`
- Typical local URL after deployment: `http://localhost:8080/hub/`

Database defaults remain in `src/main/resources/hibernate.cfg.xml`, and the app now supports runtime overrides through these environment variables:

- `INTEROPHUB_DB_URL`
- `INTEROPHUB_DB_USER` or `INTEROPHUB_USER`
- `INTEROPHUB_DB_PASSWORD` or `INTEROPHUB_PASSWORD`
- `INTEROPHUB_DB_DRIVER` or `INTEROPHUB_DRIVER`

If those variables are not set, InteropHub falls back to the values configured in Hibernate and logs a warning.

## Local Dev Environment (Nathan's Windows machine)

Two Tomcat installs exist side by side as Windows services; only one of them is actually live on port 8080:

- **`Tomcat10`** (service name) — Tomcat 10.1, install path `C:\Program Files\Apache Software Foundation\Tomcat 10.1\`. This is the instance answering `http://localhost:8080/`. Its `webapps\` folder holds the real local deployment (`hub.war` / exploded `hub\`) alongside several sibling AIRA apps deployed the same way: `aira-web-demo`, `clear`, `ips-sandbox`, `step`, `virs`.
- **`Tomcat9`** (service name) — Tomcat 9.0, install path `C:\Program Files\Apache Software Foundation\Tomcat 9.0\`. Also installed and running as a service, but not the one serving port 8080 — don't deploy here expecting `localhost:8080` to reflect it.
- The `CATALINA_HOME` environment variable points at the **9.0** install, which is misleading given traffic is actually served by 10.1. Don't use `CATALINA_HOME` to infer which instance is live; check `Get-NetTCPConnection -LocalPort 8080` / the `Tomcat10` service instead.

To redeploy locally: `mvn clean package`, then copy `target\hub.war` into Tomcat 10.1's `webapps\` folder — auto-deploy will replace the exploded `hub\` directory within a few seconds.

MySQL runs as the Windows service `MySQL81` on the default port 3306, matching the `hibernate.cfg.xml` default of `jdbc:mysql://localhost:3306/interophub`.
