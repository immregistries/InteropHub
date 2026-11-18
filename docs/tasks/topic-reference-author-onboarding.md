# Topic Reference: Author Onboarding and Agent Breadcrumbs

**Status:** Plan, approved 2026-11-18 by the product owner. Not started.
**Repositories:** `InteropHub` (this plan, for the next production release). The templates side (the project `AGENTS.md`, tool messages, guidance) follows in templates v0.22.0 and links to what this plan builds.
**Related:** `topic-reference-comments-api-contract.md`, `topic-reference-version-display-and-comment-modes.md`, `topic-reference-summary-annotations-and-guide-status.md`.

## 1. Problem

More people will author Topic References. Each needs a personal access token, and most will work through an AI agent. Only a person can create a token, because creating one needs an InteropHub sign-in. Everything after that can be done by an agent.

So InteropHub must leave breadcrumbs that:
1. tell an agent what it can do;
2. give it the exact words for the steps it must hand back to the author;
3. let it diagnose a failure on its own.

## 2. Decisions

| # | Decision |
|---|---|
| A1 | One public help page is the reference that every other breadcrumb links to. Its address is stable, and it holds nothing secret. |
| A2 | A token can describe itself (`GET /api/token`), so an agent's first call can confirm who it is acting as, what it may do, and when the token expires. |
| A3 | Every guide API error carries a `help` link to the relevant part of the help page. |
| A4 | The token page explains the scopes, offers an "Author" preset, and, right after creating a token, shows how to use it. |
| A5 | The guide settings page shows editors the exact command an agent runs to publish that guide. |
| A6 | Text written for agents says what the agent should **ask the author** to do, in words the agent can pass on as they are. |
| A7 | Tokens are never printed back after creation, never sent in a URL, and the help page tells agents never to echo them, commit them, or paste them into a guide or chat. |

## 3. What to build

### 3.1 Help page: `/hub/help/topic-reference-authors` (A1, A6, A7)

Public. It needs no sign-in and uses the normal page shell. It has stable section anchors, because error links point to them. It is plain enough for an agent to read as text. Sections:

- **`#who-can-author`:** who counts as a Topic Reference author. That is anyone who passes `canEditTopic`: global admins, admins of the topic's space, and active champions and support subscribers of the topic. Also how to ask to become one, by contacting the topic's champions or the space admin from the topic page.
- **`#create-a-token`:** Account → Personal access tokens. The Author preset, what each scope allows, the lifetimes (30, 90, 365 days), and that the token is shown once.
- **`#where-the-token-goes`:**
  - the `INTEROPHUB_TOKEN` environment variable on the author's machine, with PowerShell and bash lines;
  - the `INTEROPHUB_TOKEN` repository secret for GitHub publishing (`gh secret set INTEROPHUB_TOKEN`);
  - the never-rules from A7.
- **`#what-an-agent-can-do`:** each action, with its endpoint and scope:
  - check the token (`GET /api/token`);
  - register a guide (`POST /api/guides`);
  - upload a hosted version;
  - report a build (`/builds`);
  - set a label's display state and comment mode (`PATCH /labels/{label}`);
  - read, answer, and dispose of comments;
  - set and clear review flags.

  It also links to the content-publishing-templates tools that wrap these: `publish_guide_to_hub.py`.
- **`#if-you-are-an-agent`:** the order to work in:
  1. Call `GET /api/token`.
  2. If there is no token, or it is expired, or a scope is missing, stop and tell the author the exact words given here.
  3. Never ask the author to paste the token into the conversation. Ask them to set the variable or secret themselves.
- **`#troubleshooting`:** one entry per error code (`invalid_token`, `insufficient_scope`, `forbidden`, `not_found`, `document_inactive`, `comments_closed`, `invalid_request`, `invalid_field`, `invalid_state`), each with its cause and what to ask the author.

  It also covers the case where a request is stopped before InteropHub, recognized by an HTML response instead of JSON: a firewall. What to report: the status, the time in UTC, and the endpoint.

Write it for a reader who has never seen InteropHub. Keep it short. The page must not include any real token, internal URL beyond the public hub, or person's name.

### 3.2 `GET /api/token` (A2)

- **Access:** any valid bearer token, guide or personal. It needs no particular scope, because it only describes the caller. No CORS beyond the existing `/api/guide*` rules. It does not have to be callable from a guide page.
- **Response:**

  ```json
  {
    "user": {"userId": 123, "displayName": "Jane Doe"},
    "kind": "personal",
    "label": "laptop agent",
    "scopes": ["comments", "topics:write"],
    "expiresAt": "2027-02-16T15:04:05Z",
    "expiresInDays": 90,
    "guides": [
      {"documentKey": "immunization-cds", "title": "…", "topicId": 1, "topicName": "…",
       "hosting": "EXTERNAL", "active": true, "canPublish": true}
    ],
    "help": "{hub}/help/topic-reference-authors#if-you-are-an-agent"
  }
  ```

  - **`kind`:** `personal` or `guide`.
  - **`guides`:** every active registered guide on a topic where the user passes `canEditTopic`. Leave it empty, not omitted, for a reader.
  - **`canPublish`:** true when the token has `topics:write`.
  - **Leave out:** email, the token's hash, and the token itself.
- **Errors:** an invalid token gets the usual `401 invalid_token`, with `help` (3.3).
- **Wiring:** route it under the existing bearer filter (`/api/token`, or `/api/guide/token-info` if the filter mapping makes that simpler; document the final path). Do not disturb `/api/guide/token`, which is the guide's token exchange.

### 3.3 `help` in every guide API error (A3)

- Every JSON error written by `GuideApiErrors` / `GuideApiException` gains `"help": "{hub}/help/topic-reference-authors#troubleshooting-<code>"`. The anchor is per error code, falling back to `#troubleshooting`.
- `forbidden` on a topic action says, in `message`, that the account must be an editor of that topic, and names the topic when the caller may see it.
- `insufficient_scope` says which scope is needed (the existing `requiredScope`).
- Existing fields (`error`, `message`, `field`, …) stay unchanged. `help` is additive.

### 3.4 Token page (A4, A7)

On `/hub/account`, in the personal access tokens part:

- **Scope descriptions:** one short line under each checkbox.
  - `comments`: read and answer comments on guides.
  - `topics:read`: read topic and guide information.
  - `topics:write`: publish and manage guides on topics you edit.
- **Author preset:** an "Author (publish guides and manage comments)" option that ticks `comments` and `topics:write`.
- **After creating a token,** below the existing "Copy this personal access token now" box, a "How to use this token" panel:
  - the variable name `INTEROPHUB_TOKEN`;
  - copyable PowerShell (`$env:INTEROPHUB_TOKEN = "<token>"`, and the `setx` form for persisting) and bash lines, showing a placeholder, **not** the token again;
  - `gh secret set INTEROPHUB_TOKEN --repo <owner>/<repo>`;
  - the guides this account can publish to (as in 3.2);
  - the expiry date;
  - a link to the help page;
  - one sentence for agents: "If an agent asked you to make this token, set it as INTEROPHUB_TOKEN yourself; don't paste it into the chat."
- **Token list:** shows each token's expiry. A token that expires within 14 days gets a warning badge.
- Use existing `aira-*` classes (read `docs/aira-web/README.md` first).

### 3.5 Guide settings page: "Publish from your agent" (A5)

On `/es/topic-guide/{topicId}`, for each registered guide, a compact box with:
- the document key and the hub address;
- hosting (`HUB` or `EXTERNAL`);
- the commands for that hosting:
  - **HUB:** `python process/tools/publish_guide_to_hub.py output/guide/index.html --label vX.Y.Z --label latest`
  - **EXTERNAL:** `… --manifest-only --label vX.Y.Z`
  - **Both:** the `PATCH /labels/{label}` example for making a release Current;
- a link to the help page.

Editors only, like the rest of the page.

## 4. Contract

Amend `topic-reference-comments-api-contract.md`:
- 3.x: `GET /api/token`.
- The error section: the `help` field.
- 3.4: the token page notes.

The templates side will link to the help page anchors and call `GET /api/token`, so treat the anchors and the response shape as part of the contract.

## 5. Tests

- **Help page:**
  - renders without sign-in;
  - every anchor named in 3.1 and every `#troubleshooting-<code>` exists;
  - it contains no token-like strings.
- **`GET /api/token`:**
  - personal and guide tokens;
  - a reader with no editable topics (`guides: []`);
  - an editor of two topics;
  - `canPublish` follows `topics:write`;
  - invalid, expired, and revoked tokens get 401 with `help`;
  - the response never contains email or a token.
- **Errors:** every code from `GuideApiErrors` / `GuideApiException` carries `help` with the right anchor; existing fields unchanged.
- **Token page:** the preset ticks the right boxes; the panel appears only right after creation and never repeats the token; the expiry warning badge.
- **Settings page:** the box shows the right command for HUB and EXTERNAL guides.
- **Browser check** on local Tomcat:
  - Create an Author token as a non-admin editor.
  - Follow the panel's instructions in a shell.
  - Call `GET /api/token`.
  - Trigger `insufficient_scope` with a `comments`-only token, and `forbidden` as a reader, and confirm their `help` links land on the right sections.
