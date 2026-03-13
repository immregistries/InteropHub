# InteropHub OAuth Architecture (Connectathon Environment)

## Purpose

InteropHub is the central portal for all Connectathon interoperability testing activities.  
It provides:

- Developer onboarding
- Identity verification via email
- Discovery of test APIs
- OAuth credential issuance
- Access tokens for API execution
- Usage tracking across systems

The design intentionally mirrors real-world OAuth and FHIR authorization workflows while remaining simple enough for a Connectathon environment.

Security is **not the primary objective**. The goal is:

- developer convenience
- realistic OAuth workflow practice
- observability of system usage
- lightweight centralized coordination

InteropHub therefore acts as a **lightweight OAuth Authorization Server** and developer portal.

---

# Core Conceptual Model

Three roles exist in the architecture.

### 1. Human Developer
A person participating in the Connectathon.

They:

- register in InteropHub
- verify their email
- log into the portal
- view available test APIs
- generate OAuth client credentials

### 2. OAuth Client (Application)
An application or integration created by a developer.

Examples:

- an EHR test harness
- a CDS engine
- a FHIR client script
- a Postman collection

Each client receives credentials used to obtain OAuth access tokens.

### 3. Resource Server (Dependent APIs)

These are the testing APIs that developers interact with.

Examples:

- Step Into CDSi
- FHIR sandbox
- forecasting engines
- Connectathon validation services

Resource servers validate OAuth tokens issued by InteropHub.

---

# Architectural Roles

InteropHub provides the following capabilities:

| Capability | Role |
|---|---|
| User onboarding | Identity system |
| Client registration | OAuth client registry |
| Token issuance | OAuth Authorization Server |
| Token signing | Trust anchor for APIs |
| Usage tracking | Observability hub |

Dependent APIs function as **OAuth Resource Servers**.

---

# Key Architectural Principles

### 1. Simple developer workflow

The workflow should require minimal configuration.

Typical flow:

1. Developer registers in InteropHub
2. Developer verifies email
3. Developer logs in
4. Developer creates an OAuth client
5. Developer receives `client_id` and `client_secret`
6. Client requests OAuth token
7. Client calls API using bearer token

---

### 2. OAuth tokens represent applications, not users

OAuth tokens are issued to **clients**, not directly to users.

InteropHub maintains the relationship:

User -> OAuth Client -> Access Token



This allows:

- accurate usage reporting
- multiple clients per user
- clearer API authorization semantics

---

### 3. Short-lived access tokens

Tokens should be short lived (example: 1 hour).

This matches real OAuth behavior and prevents stale credentials.

---

### 4. JWT tokens validated locally

Resource servers validate tokens locally using InteropHub's public signing key.

They do **not call InteropHub on every request**.

Benefits:

- faster execution
- fewer network dependencies
- simpler architecture

---

# Existing Core Tables

InteropHub already includes identity and application registry tables.

### `auth_user`

Represents a registered developer.

Important attributes:

- email
- verification status
- activity timestamps
- lifecycle state

auth_user


This table forms the basis of the developer identity model.

---

### `app_registry`

Represents known APIs participating in the Connectathon environment.

Example entries:

- step-cdsi
- forecasting-api
- fhir-sandbox

app_registry


This registry is used to display APIs available to developers.

---

# Minimum Viable Build (Phase 1)

Phase 1 delivers the minimal OAuth workflow necessary for Connectathon testing.

This phase focuses on:

- developer onboarding
- OAuth client creation
- token issuance
- resource server token validation
- usage logging

---

# Phase 1 Architecture

Developer
|
v
InteropHub
| (token request)
v
Access Token (JWT)
|
v
Dependent API
|
v
Usage Event -> InteropHub


---

# Phase 1 Components

## 1. Developer Portal

Developers must be able to:

- register with email
- verify email
- log in
- view APIs available for testing
- create OAuth clients

The existing `auth_user` table supports this functionality.

---

## 2. OAuth Client Registry

Developers create **OAuth Clients** within InteropHub.

Example:

My Test EHR Client
My Postman Collection
My CDS Engine


Each client receives:

client_id
client_secret


A new table should be introduced.

### Suggested table: `oauth_client`

oauth_client

client_id (pk)
user_id
client_name
client_secret_hash
status
created_at
last_used_at


Clients are associated with the developer who created them.

---

## 3. OAuth Token Endpoint

InteropHub must expose a token endpoint.

Example:

POST /oauth/token

Request:


grant_type=client_credentials
client_id=abc123
client_secret=xyz789


Response:


{
"access_token": "...",
"token_type": "Bearer",
"expires_in": 3600
}


---

## 4. Access Token Format

Access tokens should be **JWTs signed by InteropHub**.

Suggested claims:


iss issuer (InteropHub)
sub client_id
aud target API
exp expiration
iat issued time
scope permissions
jti token id
hub_user_id


Example payload:


{
"iss": "interop-hub",
"sub": "client_123",
"hub_user_id": 55,
"scope": "system/*.read",
"exp": 1710000000,
"iat": 1709996400
}


---

## 5. Resource Server Token Validation

Dependent APIs must:

1. Validate JWT signature
2. Validate expiration
3. Validate issuer
4. Validate scope (optional)

If valid, request proceeds.

Otherwise:


401 Unauthorized


---

## 6. Usage Event Reporting

Resource servers should report usage events to InteropHub.

Example endpoint:


POST /hub/events


Payload example:


{
"client_id": "abc123",
"app_code": "step-cdsi",
"operation": "$forecast",
"timestamp": "...",
"status": "success"
}


This allows the hub to build usage reports.

---

## 7. Reporting and Observability

InteropHub will generate dashboards showing:

- active developers
- active clients
- API usage frequency
- operations executed
- unused endpoints

This information helps improve Connectathon design.

---

# Phase 1 Summary

Minimum functionality:

| Feature | Required |
|---|---|
User registration | Yes |
Email verification | Yes |
OAuth client creation | Yes |
Token endpoint | Yes |
JWT token issuance | Yes |
Token validation by APIs | Yes |
Usage event logging | Yes |

Everything else can wait.

---

# Phase 2 (Operational Improvements)

Phase 2 expands OAuth capabilities and improves operational reliability.

Planned additions:

### Token introspection endpoint

Allows APIs or administrators to inspect token state.


POST /oauth/introspect


Returns:


active: true
client_id: ...
scope: ...
exp: ...


---

### Token revocation

Ability to revoke:

- clients
- tokens
- compromised credentials

---

### Signing key rotation

Allow periodic rotation of JWT signing keys.

Expose:


/oauth/jwks


---

### API audience restrictions

Allow tokens to specify intended APIs.

Example:


aud = step-cdsi


---

### Developer tooling

Portal improvements:

- copy-paste OAuth examples
- sample curl commands
- Postman collections

---

# Phase 3 (Full Ecosystem Alignment)

Phase 3 introduces features closer to production OAuth ecosystems.

These features are optional but may be valuable for interoperability testing.

### SMART Backend Services

Support asymmetric authentication using `private_key_jwt`.

Clients authenticate using signed JWTs instead of shared secrets.

---

### JWKS client registration

Clients upload public keys.

InteropHub validates signatures for token requests.

---

### OAuth metadata endpoint

Expose discovery information.

Example:


/.well-known/oauth-authorization-server


---

### FHIR SMART configuration

Publish SMART capabilities on FHIR servers.

---

### Multi-environment Connectathons

Support environments such as:


connectathon
staging
demo


---

# Design Philosophy

InteropHub is intentionally **not a full identity platform**.

The goal is:

- realistic OAuth workflows
- minimal operational complexity
- developer-friendly testing

InteropHub focuses on:

- client identity
- token issuance
- ecosystem visibility

Not:

- enterprise authentication
- complex identity federation
- long-term production security models

---

# Long Term Vision

InteropHub becomes the operational center of interoperability testing.

It enables:

- coordinated Connectathon testing
- shared identity across systems
- consistent OAuth workflows
- ecosystem-wide usage analytics

This allows AIRA to understand:

- what APIs are actually used
- what workflows developers struggle with
- where interoperability gaps exist

InteropHub therefore becomes both a **technical coordination platform** and a **learning system for the community**.