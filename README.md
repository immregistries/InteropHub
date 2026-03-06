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

Database and environment settings are configured in `src/main/resources/hibernate.cfg.xml` and app settings tables.
