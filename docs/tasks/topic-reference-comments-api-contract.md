# Topic Reference Comments: API Contract

**Status:** Approved by the product owner 2026-10-30 (Gate 0). Section 8 and the refinements in 3.1 and 3.3 were added by G the same day from the H0 result. 3.4 step 4 corrected the same day: a disallowed origin is refused with `403` before the code checks (found by T2).
**Amended 2026-11-18** for version display and comment modes (`docs/tasks/topic-reference-version-display-and-comment-modes.md`, decisions V1 to V12): `pageUrl` and `madeOnLabel` (5.2, 5.4, 6), `comments_closed` (2), label-filtered reads with `labelPolicy` and `inScope` (5.3), and the label endpoints (7.5). A client that sends none of the new fields gets exactly the earlier behavior, apart from the added `madeOnLabel` field in responses.
**Amended 2026-11-18** for author onboarding (`docs/tasks/topic-reference-author-onboarding.md`, decisions A1 to A7): the `help` field on every error (2), the public help page and its anchors (2.1), `GET /api/token` (3.7), and the account and guide settings page notes (3.8). All additive; existing fields are unchanged.
**Implements:** `docs/topic-reference-comments-design.md`, Sections 4 and 9
**Primary purpose:** The single agreement between the guide (`guide.js` in `content-publishing-templates`) and InteropHub. Both sides are built against this document and tested against it. A change here is recorded in the workstreams Decisions Log and announced to both streams.

Section numbers like "design 6.1" refer to the design document.

## 1. Conventions

- `{hub}` is InteropHub's external base URL including the context path, for example `https://<host>/hub` or `http://localhost:8080/hub`. The guide takes it from `guide.json` `comments.hub`, with no trailing slash.
- Requests and responses are JSON, UTF-8, `Content-Type: application/json`.
- Field names are `camelCase`.
- Timestamps are ISO 8601 UTC with a `Z` and millisecond precision, for example `2026-11-02T15:04:05.123Z`.
- IDs are JSON numbers. A comment's deep link is `#comment-<id>`.
- Absent optional fields in a request mean "unchanged" (`PATCH`) or "none" (`POST`). In `PATCH`, `null` means "clear" where clearing is allowed.
- Every response with a body includes no secrets other than the token in the token response.

### 1.1 Identifiers

| Identifier | Rule |
|---|---|
| `documentKey` | The `id` in the project's `guide.json`. `^[a-z0-9]+(-[a-z0-9]+)*$`, at most 100 characters, unique in InteropHub. The words `comments`, `review-flags`, and `token` are reserved and refused. |
| `anchor` | An explicit anchor name (`^[a-z0-9]+(-[a-z0-9]+)*$`) or a heading ID produced by `build_guide.py`. InteropHub accepts `^[A-Za-z0-9][A-Za-z0-9_-]*$`, at most 100 characters, so heading IDs always fit. InteropHub never checks that an anchor exists in the guide; it cannot see the content. |
| `blockIndex` | 0-based position of the commented block within its anchor area. The guide reads it from `data-block-index`. |
| `snippet` | The block's text with whitespace collapsed to single spaces, trimmed, first 120 characters. Computed by one function in `guide.js`. InteropHub stores it as given and refuses more than 120 characters. |
| `topicCode` | `es_topic.topic_code`. |

### 1.2 Field limits

| Field | Limit |
|---|---|
| `text` (comment or reply) | 1 to 4,000 characters after trimming |
| `changeNote` | 1 to 500 characters after trimming |
| `sourceNote` | at most 200 characters |
| review flag `note` | 1 to 500 characters after trimming |
| `title` (guide) | 1 to 200 characters |

## 2. Errors

Every error response uses one shape:

```json
{
  "error": "invalid_field",
  "field": "changeNote",
  "message": "A change note is required when setting a disposition.",
  "help": "https://<host>/hub/help/topic-reference-authors#troubleshooting-invalid_field"
}
```

`field` is present only where one applies. `message` is for people; clients branch on `error`.

`help` (string, always present) links to the error's entry on the public help page (2.1): `{hub}/help/topic-reference-authors#troubleshooting-<error>`, with `<error>` exactly as in the table below, underscores included (for example `#troubleshooting-invalid_token`). A code with no entry links to `#troubleshooting`. `{hub}` comes from `hub_settings.external_base_url`. `help` is additive: every other field keeps its meaning. It is also on the bearer filter's `401`, whose body is still the same for every cause.

- `forbidden` for a topic action by someone who is not an editor says so in `message`, and names the topic when the caller may view it: "Your account must be an editor of the topic "…" to do this."
- `insufficient_scope` names the needed scope in `message` as well as in `requiredScope`.

| Status | `error` | When |
|---|---|---|
| 400 | `invalid_request` | Malformed JSON, missing body, unknown query value |
| 400 | `invalid_field` | A field fails validation (`field` names it) |
| 401 | `invalid_token` | No bearer token, unknown, expired, or revoked token. Response carries `WWW-Authenticate: Bearer realm="interophub", error="invalid_token"`. The body is the same for every cause, so it does not reveal whether a token exists. |
| 403 | `insufficient_scope` | Token lacks the scope. Adds `"requiredScope": "comments"`. `WWW-Authenticate: Bearer realm="interophub", error="insufficient_scope", scope="comments"`. |
| 403 | `forbidden` | The user may not do this (not an author, not the commenter, cannot view the topic) |
| 403 | `edit_window_closed` | Commenter edit or withdraw after the 14-day window |
| 403 | `comments_closed` | Posting a comment, reply, or review flag whose `pageUrl` belongs to a label whose comment mode is Closed (7.5) |
| 404 | `not_found` | Unknown document key, comment, or flag, or a comment that is deleted |
| 409 | `conflict` | `expectedUpdatedAt` did not match. Adds `"current": <thread>`. |
| 409 | `invalid_state` | The action does not fit the comment's state, for example replying to a closed thread or disposing a withdrawn comment |
| 409 | `document_inactive` | Writing to a document whose registration is inactive |
| 409 | `duplicate` | Registering a `documentKey` or URL that is already registered |
| 400 | `url_not_approved` | A guide URL outside the approved prefixes, by a caller who is not a global admin |
| 500 | `server_error` | Anything else. No stack traces in the body. |

### 2.1 The help page

`GET {hub}/help/topic-reference-authors` is a public HTML page (no sign-in) for authors and their agents. Its anchors are part of this contract; the templates project and the error `help` links point to them.

| Anchor | Content |
|---|---|
| `#who-can-author` | Who counts as an author (`canEditTopic`), and how to ask to become one |
| `#create-a-token` | Creating a personal access token: the Author preset, the scopes, the lifetimes, shown once |
| `#where-the-token-goes` | `INTEROPHUB_TOKEN` (PowerShell and bash), the `INTEROPHUB_TOKEN` repository secret (`gh secret set`), and the never-rules |
| `#what-an-agent-can-do` | Each action with its endpoint and scope, and `publish_guide_to_hub.py` |
| `#if-you-are-an-agent` | The order to work in, with the exact words to pass on to the author |
| `#troubleshooting` | All error entries |
| `#troubleshooting-<error>` | One per Section 2 code: `invalid_token`, `insufficient_scope`, `forbidden`, `not_found`, `document_inactive`, `comments_closed`, `invalid_request`, `invalid_field`, `invalid_state`, `edit_window_closed`, `conflict`, `duplicate`, `url_not_approved`, `server_error` |
| `#troubleshooting-html-response` | An HTML answer instead of JSON: a firewall in front of InteropHub. Report the status, the time in UTC, and the endpoint. |

The page holds no token, no internal address beyond the public hub, and no person's name.

## 3. Authentication

### 3.1 Tokens

| Kind | Issued by | Scopes | Lifetime | Raw form |
|---|---|---|---|---|
| Guide token | `POST /api/guide/token` | `comments` only | 30 days from issue, not extended by use | `ihg_` + 43 base64url characters (32 random bytes) |
| Personal access token | Account page | chosen by the user from Section 3.2 | user chooses 30, 90, or 365 days | `ihp_` + 43 base64url characters |

- Both are stored only as the SHA-256 hash of the full raw string (UTF-8) in `app_user_token.token_hash`, with a `scopes` column (comma-separated) and a `label`. Guide tokens get the label `Guide sign-in: <origin>`.
- **`app_id` tells the kinds apart**, as well as the prefix. Guide tokens use the `topic-reference` app. Personal access tokens use a second `app_registry` row, `app_code = interophub-api`, created by the same database update, not visible to users, with no redirect allowlist. The bearer filter accepts tokens of these two apps only.
- The prefixes make leaked tokens easy to recognize and let the filter tell the kinds apart without a lookup.
- `last_used_at` is updated at most once per minute per token.
- Sent as `Authorization: Bearer <raw token>` on every API and MCP call. Tokens are never accepted in a query string.

### 3.2 Scopes

| Scope | Allows |
|---|---|
| `comments` | Section 5 and Section 6 endpoints, `GET /api/guide/{documentKey}`, `GET /api/guide/{documentKey}/versions`, and `GET /api/guide/{documentKey}/labels` |
| `topics:read` | `GET /api/guide/{documentKey}`, `GET /api/guide/{documentKey}/versions`, `GET /api/guide/{documentKey}/labels`, and the topic read endpoints in `docs/tasks/topic-mcp-tools.md` |
| `topics:write` | Section 7 endpoints (including `PATCH /api/guide/{documentKey}/labels/{label}`) and the topic write endpoints. Implies `topics:read`. |

A scope only narrows what the user may already do. Each endpoint below names its required scope; a token with any one of the listed scopes is accepted.

### 3.3 The bearer-token filter

One filter serves `/api/guide/*`, `/api/guides`, `/api/token`, `/api/topic-spaces/*`, `/api/topics/*`, and `/mcp`. It does authentication only:

1. Requires exactly one `Authorization: Bearer <token>` header (scheme compared ignoring case). Never reads a token from the query string or body.
2. Hashes the token and finds an `app_user_token` row of the `topic-reference` or `interophub-api` app that is not revoked and not expired, whose user is active.
3. On any failure: `401 invalid_token` as in Section 2, with the same body for every cause.
4. On success: sets request attributes for the user, the `token_id`, the token kind (`guide` or `personal`), and the scope set. The raw token is not put on the request. Updates `last_used_at` (at most once a minute).
5. Lets `OPTIONS` preflight requests through without a token, to the CORS handling in Section 4.
6. Lets `POST /api/guide/token` through without a token, since that call is how a guide gets one. `DELETE /api/guide/token` does need the token.

**Scope checks are not in the filter.** The filter cannot see which MCP tool a request calls without consuming the body. Each API servlet, and the MCP `tools/call` dispatcher for each tool, calls one shared helper such as `requireScope(request, "comments")`, which returns `403 insufficient_scope`. Servlets behind the filter also refuse with `401` if the user attribute is missing, so a missing filter mapping cannot leave an endpoint open.

The existing session cookie is **not** accepted on these paths.

### 3.4 Sign-in round trip (authorization code with PKCE)

All guides share the `topic-reference` entry in `app_registry` (design 4.2). It is a public client: it has no secret, and PKCE is required.

**Step 1, start (guide).** The guide:

1. Generates `codeVerifier`: 64 characters from `[A-Za-z0-9-._~]` (RFC 7636), using `crypto.getRandomValues`.
2. Computes `codeChallenge` = base64url(SHA-256(`codeVerifier`)), no padding.
3. Generates `state`: 32 base64url characters.
4. Saves `{codeVerifier, state, returnLocation}` in `sessionStorage` under one key, where `returnLocation` is the current URL including any `#anchor` or `#comment-<id>`, with the `interophub=signin` marker (Section 3.6) removed.
5. Sets the top-level window to:

```text
{hub}/home?app_code=topic-reference
  &return_to=<returnTo>
  &state=<state>
  &code_challenge=<codeChallenge>
  &code_challenge_method=S256
  &response_mode=fragment
  &requested_url=<returnTo>
```

`returnTo` is the current page URL with no query and no fragment. It must fall under an enabled `app_redirect_allowlist` base for the app, which InteropHub maintains from guide registrations (Section 7).

**Step 2, sign in (InteropHub).** Unchanged from today's flow: immediate if a session exists; otherwise InteropHub asks for the reader's email, sends the magic link, registers the reader if they are new, and then continues to Step 3, so the reader always ends up back in the guide where they started.

Optional parameter `prompt=login` (Section 3.5, switching accounts): InteropHub ends its own session first, then asks for an email as if no session existed.

InteropHub rules for this app:

- `code_challenge` and `code_challenge_method=S256` are required. `plain` is refused.
- `response_mode=fragment` is required. `return_to` with a fragment is refused.
- Invalid requests show InteropHub's existing error page; nothing is sent back to the guide.
- `code_challenge`, `code_challenge_method`, and `return_to` are stored on `auth_login_code`. The code lives 5 minutes, as today.

**Step 3, return (InteropHub).** Redirect to `<returnTo>#code=<code>&state=<state>`.

**Step 4, token (guide).** The guide reads the fragment, checks `state` against `sessionStorage` (on mismatch it discards everything and shows "Sign-in did not complete. Try again."), then calls:

```http
POST {hub}/api/guide/token
Content-Type: application/json
Origin: https://<org>.github.io

{
  "code": "Z8Lh8L2tX5xYV...",
  "codeVerifier": "…64 characters…",
  "returnTo": "https://<org>.github.io/contextual-conditions/"
}
```

First, the request's `Origin` must be allowed (Section 4). A disallowed origin gets `403 forbidden` with no CORS headers, and the code is **not** consumed, so a page on the wrong origin cannot burn a reader's code.

Then InteropHub checks, in this order, refusing all failures with `400 invalid_request` and the same message ("Sign-in code is invalid or expired.") so the response does not reveal which check failed:

1. The code exists, was issued for `topic-reference`, is not expired, and is not consumed.
2. `returnTo` equals the stored `return_to`.
3. base64url(SHA-256(`codeVerifier`)) equals the stored `code_challenge`.

Then it consumes the code and responds `200`:

```json
{
  "accessToken": "ihg_4m8tR0…",
  "tokenType": "Bearer",
  "expiresAt": "2026-12-02T15:04:05.000Z",
  "scopes": ["comments"],
  "user": {
    "userId": 123,
    "displayName": "Jane Doe",
    "organization": "Example Health Department"
  }
}
```

`displayName` is `auth_user.display_name`, or first and last name when that is empty.

`POST /api/auth/exchange` refuses codes issued for `topic-reference`, and `/api/guide/token` refuses codes for any other app.

**Step 5, clean up (guide).** Remove the fragment with `history.replaceState`, restore `returnLocation` (scroll to its anchor or comment), clear the `sessionStorage` entry, and store `{accessToken, expiresAt, user}` in `localStorage` under a key that includes the `{hub}` and `documentKey`.

### 3.5 Expiry, sign-out, and failures

- On any `401` the guide deletes its stored token and shows "Sign in to comment" in the rail. It does **not** redirect on its own; the reader selects the button. The one automatic start is the `/guide/open` marker (Section 3.6). This keeps a reader from being sent to an email prompt without asking.
- The guide also treats a stored token past `expiresAt` as absent, without calling the API.
- **Sign out** from the guide:

```http
DELETE {hub}/api/guide/token
Authorization: Bearer ihg_…
```

`204`. Revokes the calling token only. The InteropHub session is not touched. Only guide tokens may call this; a personal access token gets `403 forbidden`.

**Who am I, and switching accounts.** Low priority; may follow the rest of T2 and H1.

- While signed in, the rail shows "Signed in as <displayName>, <organization>" with **Sign out** and **Not you?**.
- **Sign out** calls `DELETE /api/guide/token` and forgets the stored token. The reader can sign in again later; with an InteropHub session this returns them as the same person at once.
- **Not you?** calls `DELETE /api/guide/token`, forgets the token, and starts Step 1 with `&prompt=login` added, so InteropHub signs out its session and asks for an email. The reader then signs in (or registers) as the right person and returns to the guide.
- `prompt=login` is accepted for any app, not only `topic-reference`.

### 3.6 Links from InteropHub: `/guide/open`

```text
GET {hub}/guide/open?doc=<documentKey>[&anchor=<anchor> | &comment=<id>][&label=<url label>]
```

- No sign-in needed to follow the link.
- InteropHub picks the document's URL whose label matches `label`, or else its first URL in registration order, and redirects (`302`) to `<url>?interophub=signin#<anchor>` or `#comment-<id>`. For a hosted (`HUB`) document, `label` may also name any hosted label (for example a Current `v0.2.0`, which has no registered URL); the redirect then goes to that label's view URL (7.4), and the viewer applies the label's display state.
- Unknown, inactive, or URL-less documents show InteropHub's normal not-found page.
- The guide, on load, sees `interophub=signin`: if it holds an unexpired token it removes the marker with `history.replaceState` and does nothing else; otherwise it starts Step 1 at once.
- The link never carries a token or code.

### 3.7 `GET /api/token`

Describes the calling token, so an agent's first call can confirm who it acts as, what it may do, and when the token expires.

- **Access:** any valid bearer token, guide or personal, with no particular scope. It sits behind the bearer filter (3.3), so the Section 4 rules apply as for `/api/guide/*`; it is not meant to be called from a guide page. `GET` only; any other method gets `405`, `Allow: GET`, and an `invalid_request` body.
- **Errors:** a missing, unknown, expired, or revoked token gets the usual `401 invalid_token` (Section 2), with `help`.
- **Response** `200`, `Cache-Control: no-store`:

```json
{
  "user": { "userId": 123, "displayName": "Jane Doe" },
  "kind": "personal",
  "label": "laptop agent",
  "scopes": ["comments", "topics:write"],
  "expiresAt": "2027-02-16T15:04:05.000Z",
  "expiresInDays": 90,
  "guides": [
    { "documentKey": "immunization-cds", "title": "…", "topicId": 1, "topicName": "…",
      "hosting": "EXTERNAL", "active": true, "canPublish": true }
  ],
  "help": "https://<host>/hub/help/topic-reference-authors#if-you-are-an-agent"
}
```

- `kind` is `personal` or `guide`. `label` is the token's label (guide tokens: `Guide sign-in: <origin>`).
- `scopes` are in the order `comments`, `topics:read`, `topics:write`.
- `expiresInDays` is the whole days left, rounded up (a new 90-day token reports `90`), and `0` once expired.
- `guides` is every **active** registered guide on a topic where the user passes `canEditTopic`, ordered by `documentKey`. It is `[]`, never absent, for a reader. `active` is therefore always `true`; it is kept so the object reads the same as other guide lists.
- `canPublish` is true when the token has `topics:write` (the same value on every guide).
- The response never carries the user's email, the token's hash, or the token.

### 3.8 Personal access tokens on the account page

`{hub}/account`, Access tokens (decisions A4 and A7):

- Each scope checkbox has a one-line description: `comments`, read and answer comments on guides; `topics:read`, read topic and guide information; `topics:write`, publish and manage guides on topics you edit.
- An **Author (publish guides and manage comments)** preset ticks `comments` and `topics:write`. The form posts `preset=author` and the server adds those two scopes, so it works without script.
- Right after a token is created, below the one-time copy box, a **How to use this token** panel shows: `INTEROPHUB_TOKEN`; PowerShell (`$env:` and `setx`) and bash lines with a placeholder, never the token; `gh secret set INTEROPHUB_TOKEN --repo <owner>/<repo>`; the guides the account can publish to (as `guides` in 3.7); the expiry; a link to the help page; and "If an agent asked you to make this token, set it as INTEROPHUB_TOKEN yourself; don't paste it into the chat." The panel appears only with the one-time reveal and never repeats the token.
- The token list shows each token's expiry, with an **Expires soon** badge within 14 days and **Expired** after.

The guide settings page (`{hub}/es/topic-guide/{topicId}`) shows editors a **Publish from your agent** box for each registered guide: its document key, the hub, its hosting, the `publish_guide_to_hub.py` command for that hosting (`--label vX.Y.Z --label latest` for `HUB`, `--manifest-only --label vX.Y.Z` for `EXTERNAL`), and the `PATCH …/labels/{label}` example for making a release Current.

## 4. CORS

Applies to every `/api/guide/*` and `/api/guides` endpoint, and to `/api/token` (3.7), which shares the filter. `/mcp` and the topic endpoints send no CORS headers.

- **Allowed origins** are the scheme, host, and port of every enabled `app_redirect_allowlist.base_url` for the `topic-reference` app. All guides share one list; a page on any registered origin may call the API for any document, and authorization still comes from the token's user.
- Requests with an allowed `Origin` get:
  - `Access-Control-Allow-Origin: <that origin>` (never `*`)
  - `Vary: Origin`
  - `Access-Control-Expose-Headers: WWW-Authenticate`
- Preflight `OPTIONS` from an allowed origin returns `204` with:
  - `Access-Control-Allow-Methods: GET, POST, PATCH, DELETE`
  - `Access-Control-Allow-Headers: Authorization, Content-Type`
  - `Access-Control-Max-Age: 600`
- `Access-Control-Allow-Credentials` is never sent. Cookies play no part.
- A request with an `Origin` that is not allowed gets no CORS headers. A preflight from it gets `403`. A non-preflight request from it is refused with `403 forbidden` before any work is done.
- A request with no `Origin` header (agents, scripts, curl) is processed normally on the bearer token.

## 5. Guide and Comment Endpoints

### 5.1 `GET /api/guide/{documentKey}`

Scope: `comments` or `topics:read`.

Returns the document, the caller's permissions, and the lookup values the guide needs. A signed-in user who cannot view the topic still gets `200` with `canView: false` and `topic: null`, so the guide can say why there are no comments. Unknown keys get `404`.

```json
{
  "documentKey": "contextual-conditions",
  "title": "Contextual Conditions",
  "isActive": true,
  "hosting": "EXTERNAL",
  "topic": {
    "topicId": 7,
    "topicCode": "CONTEXTUAL-CONDITIONS",
    "topicName": "CDS Contextual Conditions",
    "url": "https://<host>/hub/es/topic/7"
  },
  "repositoryUrl": "https://github.com/<org>/contextual-conditions",
  "urls": [
    { "url": "https://<org>.github.io/contextual-conditions/", "label": "Release" },
    { "url": "https://<org>.github.io/contextual-conditions/main/", "label": "Preview" }
  ],
  "permissions": {
    "canView": true,
    "canComment": true,
    "isAuthor": false
  },
  "user": { "userId": 123, "displayName": "Jane Doe", "organization": "Example Health Department" },
  "dispositions": [
    { "code": "PERSUASIVE", "label": "Persuasive" },
    { "code": "PERSUASIVE_MOD", "label": "Persuasive with Modification" },
    { "code": "NOT_PERSUASIVE", "label": "Not Persuasive" },
    { "code": "NOT_PERSUASIVE_MOD", "label": "Not Persuasive with Modification" },
    { "code": "NOT_RELATED", "label": "Not Related" },
    { "code": "FUTURE_USE", "label": "Considered for Future Use" },
    { "code": "QUESTION_ANSWERED", "label": "Considered – Question Answered" }
  ],
  "editWindowDays": 14,
  "serverTime": "2026-11-02T15:04:05.123Z"
}
```

- `hosting` is `EXTERNAL` (published at the guide URLs) or `HUB` (served by InteropHub). Existing documents are `EXTERNAL`. See Section 7.4.
- `canView`: `TopicSpaceAccessService.canViewTopic`.
- `canComment`: `canView` and `isActive`.
- `isAuthor`: `canEditTopic` (design 6.1, and the 2026-10-30 decision that champions and support subscribers count).
- `dispositions` come from `es_guide_disposition` in display order. The guide never hard-codes them.

### 5.2 The thread object

Comments are always returned as threads: a top-level comment with its replies.

```json
{
  "id": 482,
  "documentKey": "contextual-conditions",
  "anchor": "secure-transport",
  "blockIndex": 1,
  "snippet": "Each connection is authenticated with mutual TLS using certificates issued by",
  "text": "Should this say which certificate authorities are acceptable?",
  "state": "DISPOSED",
  "disposition": { "code": "PERSUASIVE_MOD", "label": "Persuasive with Modification" },
  "changeNote": "Language updated to point to the trust framework instead of naming authorities.",
  "sourceNote": null,
  "author": { "userId": 123, "displayName": "Jane Doe", "organization": "Example Health Department" },
  "mine": false,
  "createdAt": "2026-11-02T15:04:05.123Z",
  "editedAt": null,
  "editableUntil": "2026-11-16T15:04:05.123Z",
  "updatedAt": "2026-11-04T09:12:00.000Z",
  "disposedAt": "2026-11-04T09:12:00.000Z",
  "disposedBy": { "userId": 9, "displayName": "Sam Author", "organization": "AIRA" },
  "closedAt": null,
  "closedBy": null,
  "deleted": false,
  "madeOnLabel": "v0.2.0",
  "permissions": {
    "canEdit": false,
    "canWithdraw": false,
    "canReply": true,
    "canDispose": true,
    "canClose": true,
    "canDelete": true,
    "canReattach": true
  },
  "replies": [
    {
      "id": 490,
      "parentId": 482,
      "text": "Agreed, the framework already lists them.",
      "author": { "userId": 77, "displayName": "Lee Reader", "organization": "Example IIS" },
      "mine": true,
      "createdAt": "2026-11-03T10:00:00.000Z",
      "editedAt": null,
      "editableUntil": "2026-11-17T10:00:00.000Z",
      "updatedAt": "2026-11-03T10:00:00.000Z",
      "withdrawn": false,
      "deleted": false,
      "madeOnLabel": "main",
      "permissions": { "canEdit": true, "canWithdraw": true, "canDelete": true }
    }
  ]
}
```

Rules:

- `state` is `OPEN`, `DISPOSED`, `CLOSED`, or `WITHDRAWN`. `disposition` and `changeNote` are present only when a disposition is set; otherwise `null`.
- `updatedAt` on a thread is the latest change to the comment **or any of its replies**. It drives `since`.
- **Withdrawn** comments and replies keep their place but return `text: null`. The guide shows "Withdrawn by the commenter."
- **Deleted** comments and replies are never returned by a normal listing. In a `since` listing (5.3) they appear as tombstones so the guide can remove them: `{ "id": 482, "deleted": true, "updatedAt": "…" }` for a thread, and the same shape inside `replies` for a reply. The row stays in the database, with its text, for the audit record; no API returns it.
- `mine` (threads and replies) is true when the caller wrote it. It drives the guide's "Mine" filter. Unlike `canEdit` and `canWithdraw`, it does not lapse when the edit window closes. Added in the release that introduced guide build status; older guides ignore it.
- `permissions` are computed for the caller, so the guide shows only controls that will work. The server checks again on every write. `canClose` covers both closing and reopening (5.6 `state`): for an author it is true on `OPEN`, `DISPOSED`, and `CLOSED`, and false on `WITHDRAWN`.
- Replies are ordered by `createdAt`. Threads are ordered by `createdAt` of the top-level comment.
- `madeOnLabel` (threads and replies; string or `null`) is the label the item was written on, resolved from the `pageUrl` sent when it was created (5.4). `null` for items written before this field existed, by a guide that sent no `pageUrl`, or from an address InteropHub did not recognize. It is context only; it never splits the conversation (V6).
- `inScope` (top-level threads only, boolean) appears only in a 5.3 listing that named a known label. See 5.3.

### 5.3 `GET /api/guide/{documentKey}/comments`

Scope: `comments`. Requires `canView`.

| Query | Meaning |
|---|---|
| `state` | Comma-separated states. Default `OPEN,DISPOSED`. `all` means every state. |
| `anchor` | Only threads on this anchor. |
| `since` | A `serverTime` from an earlier response. Returns every thread whose `updatedAt` is **at or after** `since`, in any state, including tombstones. `state` is ignored when `since` is given. |
| `label` | Optional. The label the guide page belongs to, such as `v0.2.0` or `main` (at most 64 characters). Filters per 5.3.1. |
| `pageUrl` | Optional. The guide page's address (at most 2,000 characters), resolved to a label as in 5.4.1. Ignored when `label` is given. |

```json
{
  "serverTime": "2026-11-04T09:15:00.000Z",
  "comments": [ { "…thread…": "" } ],
  "reviewFlags": [ { "…flag…": "" } ]
}
```

- The guide passes the last response's `serverTime` as the next `since`, and merges threads by `id`, so an item seen twice at the boundary is harmless.
- `reviewFlags` is always the full list of active flags for the document (6.1), so one poll refreshes both. It is small.
- No paging. If a document passes 1,000 threads this is revisited.

#### 5.3.1 Filtering by label

**Without `label` or `pageUrl`:** the listing is unfiltered, exactly as before, and the response has **no** `labelPolicy` key and no `inScope` fields.

**With either:** the response gains `labelPolicy`:

```json
{
  "serverTime": "…",
  "comments": [ { "…thread…": "", "inScope": true } ],
  "reviewFlags": [ { "…flag…": "", "inScope": false } ],
  "labelPolicy": {
    "label": "v0.2.0",
    "display": "current",
    "commentMode": "release",
    "publishedAt": "2026-11-18T12:43:53.621Z"
  }
}
```

- `labelPolicy` is `null` when the label is unknown (not a known label, 7.5, or a `pageUrl` that does not resolve). The listing is then unfiltered, as for an Open label, and carries no `inScope`.
- `display` is `current`, `listed`, `unlisted`, `authors_only`, or `retired`. `commentMode` is `open`, `release`, or `closed`. `publishedAt` is the label's publication time (below), or `null`.
- **Publication time:** the label's newest hosted upload; otherwise the `received_at` of its newest build report; otherwise, while its mode is Release, the time the mode was last changed.
- **Scope:** a top-level comment or a review flag is in scope for label L when it was written on L (`madeOnLabel = L`) or was created before L's publication time. Items with no `madeOnLabel` count by creation time. Replies follow their thread: a reply is shown wherever its top-level comment is shown, whatever label the reply was written on.

| `commentMode` | Readers get | Authors (`isAuthor`) get |
|---|---|---|
| `open` | Everything; every `inScope` is `true` | Everything; every `inScope` is `true` |
| `release` | Only in-scope threads and flags; every `inScope` is `true` | Everything; out-of-scope items have `inScope: false` |
| `closed` | `comments: []`, `reviewFlags: []` | `comments: []`, `reviewFlags: []` |

- The same filter applies to `since` listings, tombstones included: a thread hidden on the label is never sent, not even as a tombstone.
- `inScope` is on top-level threads and on flags, never on replies or tombstones.
- The display state does not filter comments. An Authors only or Retired label still answers with its `labelPolicy` (the guide can show a banner); only the comment mode filters.
- Closing a label hides its comments only there. The same comments still appear on other labels, in unfiltered listings, and on InteropHub's comments page.

**Guide use:** pass `pageUrl` (or `label`) on every load and poll; show a banner from `labelPolicy`; hide the comment and flag controls when `commentMode` is `closed`; for authors, mark items with `inScope: false`. When `labelPolicy.commentMode` differs from the last response, re-read the whole list without `since`: a `since` poll never returns the threads a new mode reveals or hides.

The guide's refresh timing is in design 9.2; it is a client rule and not part of this contract.

### 5.4 `POST /api/guide/{documentKey}/comments`

Scope: `comments`. Requires `canComment`.

Top-level comment:

```json
{
  "anchor": "secure-transport",
  "blockIndex": 1,
  "snippet": "Each connection is authenticated with mutual TLS using certificates issued by",
  "text": "Should this say which certificate authorities are acceptable?",
  "sourceNote": "Meeting 2026-11-12"
}
```

Reply:

```json
{
  "parentCommentId": 482,
  "text": "Agreed, the framework already lists them.",
  "pageUrl": "https://<org>.github.io/contextual-conditions/main/"
}
```

Both forms accept an optional `pageUrl`: the address of the guide page the reader is on (the guide sends `location.href`). Text, at most 2,000 characters after trimming; otherwise `400 invalid_field`, `field: pageUrl`.

#### 5.4.1 Resolving `pageUrl` to a label

1. The query and fragment are ignored. Scheme and host compare without case; the path compares exactly.
2. **Hosted:** `{hub}/guide/view/{documentKey}/{label}/…` gives `{label}` when it is a valid hosted label (7.4).
3. **External:** otherwise, the longest registered guide URL of the document that the address starts with gives that URL's label. An address equal to a registered URL without its trailing slash also matches.
4. Anything else, or no `pageUrl`: no label. The comment is accepted with `madeOnLabel: null` and treated as Open. Older guides send no `pageUrl` and keep working.

If the resolved label's comment mode is `closed`, the write is refused with `403 comments_closed`. A reply is checked against its own `pageUrl`, not its thread's label. The resolved label is stored as `madeOnLabel`.

- `anchor`, `blockIndex`, and `text` are required for a top-level comment; `snippet` is optional (agents may not have one); `sourceNote` is optional.
- For a reply, only `parentCommentId`, `text`, and `pageUrl` are read; other fields are ignored.
- Replying to a reply: `400 invalid_field`, `field: parentCommentId`.
- Replying to a thread that is `CLOSED`, `WITHDRAWN`, or deleted: `409 invalid_state`.
- The comment is attributed to the token's user.
- `201` with the **whole thread** (for a reply, the parent thread), and `Location: {hub}/api/guide/comments/<id>`.

### 5.5 `GET /api/guide/comments/{id}`

Scope: `comments`. Requires `canView` on the comment's document. `id` may be a reply's; the parent thread is returned. `200` with the thread.

### 5.6 `PATCH /api/guide/comments/{id}`

Scope: `comments`. Each field in the body is checked separately; if any field is refused, nothing is saved.

| Field | Who | Rule |
|---|---|---|
| `text` | The commenter | Within `editableUntil`; not `WITHDRAWN`, `CLOSED`, or deleted. Sets `editedAt`. |
| `dispositionCode` | Authors, top-level only | A code from `dispositions`. Requires `changeNote` in the same request unless one is already set. Moves `OPEN` to `DISPOSED`; on `DISPOSED` it replaces the disposition. `null` clears the disposition and change note and returns the comment to `OPEN`. Refused on `CLOSED` and `WITHDRAWN` (`409 invalid_state`). |
| `changeNote` | Authors, top-level only | Only with a disposition set or being set. |
| `state` | Authors, top-level only | `CLOSED` closes an `OPEN` or `DISPOSED` comment, and with it the thread. `OPEN` reopens a `CLOSED` comment, which returns to `DISPOSED` if it has a disposition, else `OPEN`. Any other value: `400`. `DISPOSED` is reached only through `dispositionCode`; `WITHDRAWN` only through 5.7. |
| `anchor`, `blockIndex`, `snippet` | Authors, top-level only | Re-attach. `anchor` is required if any of the three is sent. Allowed in any state except deleted. |
| `expectedUpdatedAt` | Anyone | Optional. If given and not equal to the thread's `updatedAt`, `409 conflict` with `current`. Agents should send it. |

- Replies accept only `text` (and `expectedUpdatedAt`). Any other field: `400 invalid_field`.
- Authors cannot change another person's `text` (`403 forbidden`).
- `200` with the whole thread.
- Every change writes `es_guide_comment_audit`: who, when, the field, old and new values.

### 5.7 `POST /api/guide/comments/{id}/withdraw`

Scope: `comments`. The commenter only, within `editableUntil`. Works for a comment or a reply. A top-level comment moves to `WITHDRAWN` and its thread is closed to new replies; its existing replies stay visible. A reply gets `withdrawn: true`. Already withdrawn: `200`, no change. `200` with the whole thread.

### 5.8 `DELETE /api/guide/comments/{id}`

Scope: `comments`. Authors only. Deleting a top-level comment deletes its replies. The rows are kept with a `deleted_at` and `deleted_by_user_id` and are no longer returned (5.2). `204`.

## 6. Review Flags

### 6.1 The flag object

```json
{
  "id": 31,
  "documentKey": "contextual-conditions",
  "anchor": "consent-requirements",
  "note": "Please review: rewrote the consent requirements.",
  "createdBy": { "userId": 9, "displayName": "Sam Author", "organization": "AIRA" },
  "createdAt": "2026-11-05T14:00:00.000Z",
  "updatedAt": "2026-11-05T14:00:00.000Z",
  "clearedAt": null,
  "clearedBy": null,
  "madeOnLabel": "v0.3.0"
}
```

`madeOnLabel` is as in 5.2. `inScope` is added only in a 5.3 listing that named a known label.

### 6.2 Endpoints

| Method and path | Scope | Who | Result |
|---|---|---|---|
| `GET /api/guide/{documentKey}/review-flags?includeCleared=false` | `comments` | `canView` | `200 {"serverTime": …, "reviewFlags": [ … ]}` |
| `POST /api/guide/{documentKey}/review-flags` with `{"anchor": …, "note": …, "pageUrl"?: …}` | `comments` | Authors | `201` with the flag. If the anchor already has an active flag, its note is replaced and `200` is returned with it, so the call is safe to repeat; that flag keeps the `madeOnLabel` it was created with. `pageUrl` resolves as in 5.4.1; on a Closed label: `403 comments_closed`. |
| `DELETE /api/guide/{documentKey}/review-flags/{id}` | `comments` | Authors | Clears the flag (sets `clearedAt`, `clearedBy`). `204`. Already cleared: `204`. |

At most one active flag per anchor per document.

## 7. Guide Registration

Scope `topics:write` for writes, `comments` or `topics:read` for the read (5.1). The caller must satisfy `canEditTopic` on the topic.

### 7.1 `POST /api/guides?dryRun=false`

```json
{
  "topicCode": "CONTEXTUAL-CONDITIONS",
  "documentKey": "contextual-conditions",
  "title": "Contextual Conditions",
  "repositoryUrl": "https://github.com/<org>/contextual-conditions",
  "urls": [
    { "url": "https://<org>.github.io/contextual-conditions/", "label": "Release" }
  ]
}
```

`201` with the document object from 5.1, plus:

```json
{
  "allowlistChanges": { "added": ["https://<org>.github.io/contextual-conditions/"], "removed": [] },
  "dryRun": false
}
```

With `dryRun=true` every check runs, nothing is saved, and the response is `200` with what would be saved.

### 7.2 `PATCH /api/guide/{documentKey}?dryRun=false`

Any of `title`, `repositoryUrl`, `urls`, `isActive`. `urls`, when sent, **replaces** the whole list. `200` with the document object and `allowlistChanges`.

### 7.3 URL rules

- Absolute `https` URL, with no query and no fragment, normalized to end in `/`. `http` is allowed only for `localhost` and `127.0.0.1`, and only under the dev rule below.
- Must start with an approved guide URL prefix (a global admin setting, design 9.4), unless the caller is a global admin. Otherwise `400 url_not_approved`, `field: urls`.
- **Dev rule:** when InteropHub runs as a local dev instance (the existing localhost detection), `http://localhost:` and `http://127.0.0.1:` URLs on any port count as approved. This is code, not data, so it survives the daily database rebuild and cannot reach production.
- A URL registered to another document: `409 duplicate`.
- Labels: 1 to 40 characters, unique within the document. Order is kept; the first URL is the default for `/guide/open`.
- InteropHub adds an `app_redirect_allowlist` row for each URL and disables the row when the URL is removed or the document becomes inactive. It never changes rows it did not create.
- Each change is recorded with the user and token, in `es_topic_change` once that table exists (topic tools task).

### 7.4 Private hosting

`hosting` on the document object (5.1) is `EXTERNAL` or `HUB`. `POST /api/guides` and `PATCH /api/guide/{documentKey}` accept it. Absent on create means `EXTERNAL`. `null` is refused.

When `hosting` is `HUB`, omit `urls`. InteropHub registers these two guide URLs, replacing any external URLs:

```text
{hub}/guide/view/{documentKey}/latest/
{hub}/guide/view/{documentKey}/main/
```

`latest` is first, so `/guide/open` uses it. These hub view URLs count as approved without an approved-prefix setting. Sending `urls` together with `HUB` is `400 invalid_field`, `field: urls`.

The document's allowlist row is the prefix `{hub}/guide/view/{documentKey}/`, maintained through the rules in 7.3. Prefix matching accepts every label of that document, including a version tag, for guide sign-in. It is not `{hub}/guide/view/` for every document. The row is removed the same way as other owned allowlist rows when hosting returns to `EXTERNAL` or the document becomes inactive.

Setting `hosting` back to `EXTERNAL` removes the view URLs and that prefix. Send `urls` in the same request to replace the view URLs; omitting `urls` leaves the document with no guide URLs.

```text
GET {hub}/guide/view/{documentKey}/{label}/
```

A URL without the trailing slash redirects to it. Any further path after the label is `404`. The response is HTML, not JSON. Signed-out readers are sent to sign-in. The magic link carries the guide path, including its query, and after the link is used the reader lands on that same URL. Only a relative path under `/guide/view/` is carried; anything else is ignored and sign-in finishes on the normal page. A signed-in reader who cannot view the topic, or an inactive document, a document that is not `HUB`, or an unknown label, gets the same not-found page as a private topic. An allowed response is the stored HTML with `Content-Type: text/html; charset=utf-8`, `Cache-Control: private, no-store`, `X-Robots-Tag: noindex`, `X-Content-Type-Options: nosniff`, and `Referrer-Policy: same-origin`. InteropHub streams the file. It does not redirect to storage.

`label` is `latest`, `main`, or `v` followed by digits and dots, with an optional `-suffix` such as `v1.2.3` or `v1.2.3-rc1`.

```http
POST {hub}/api/guide/{documentKey}/versions/{label}
Authorization: Bearer ihp_…
Content-Type: text/html

<!DOCTYPE html>
<html>…one self-contained guide…</html>
```

Scope: `topics:write`. The caller must satisfy `canEditTopic`. The document must be active and `HUB`. The body is the HTML, at most 20 MB, and it must start like an HTML document after an optional BOM and whitespace. `POST` only. `201` when the label is new, `200` when it replaces the label. Older uploads are kept. The body is the version object:

```json
{
  "label": "latest",
  "sizeBytes": 409600,
  "uploadedBy": "Sam Author",
  "uploadedAt": "2026-11-09T18:04:05.123Z",
  "viewUrl": "https://<host>/hub/guide/view/contextual-conditions/latest/"
}
```

`uploadedBy` is the uploader's display name. `uploadedAt` is UTC. `viewUrl` is the URL in 7.4 above. The response does not include the stored file's `public_id` or a storage URL.

```http
GET {hub}/api/guide/{documentKey}/versions
```

Scope: `comments` or `topics:read`. Requires `canView`. `200`:

```json
{
  "versions": [
    {
      "label": "latest",
      "sizeBytes": 409600,
      "uploadedBy": "Sam Author",
      "uploadedAt": "2026-11-09T18:04:05.123Z",
      "viewUrl": "https://<host>/hub/guide/view/contextual-conditions/latest/",
      "historyCount": 2,
      "earlier": [
        {
          "sizeBytes": 400000,
          "uploadedBy": "Sam Author",
          "uploadedAt": "2026-11-09T17:00:00.000Z"
        }
      ]
    }
  ]
}
```

One object per label, the newest upload, plus `historyCount` (every upload of that label, including the newest) and `earlier` (the uploads that label no longer serves). `latest`, then `main`, then other labels in alphabetical order.

Errors use Section 2. `400 invalid_field` names `label`, `type`, or `size`. `403 forbidden` when the caller cannot edit (upload) or view (list). `403 insufficient_scope` when the token lacks the scope. `404 not_found` for an unknown document key. `409 document_inactive` when uploading to an inactive document. `409 invalid_state` when uploading to a document that is not `HUB`. Each upload is recorded in `es_topic_change`.

### 7.5 Labels: display state and comment mode

A guide's **known labels** are: a hosted guide's uploaded labels, the labels of its registered guide URLs (for a hosted guide, `latest` and `main`), labels named in its build reports, and labels that already have a setting. A known label with no setting is `unlisted` / `open`.

| Display | Topic page, readers | Hosted viewer, readers |
|---|---|---|
| `current` (at most one) | Main link, "Read the guide" | Opens |
| `listed` | Under "Other versions" | Opens |
| `unlisted` (default) | Not shown | Opens with the link |
| `authors_only` | Not shown | Refused (the private-topic page) |
| `retired` | Not shown | "This version has been retired", linking to the Current label if it has an upload |

Topic editors see and open every label. Comment modes (`open`, default; `release`; `closed`) are applied as in 5.3.1.

```http
GET {hub}/api/guide/{documentKey}/labels
```

Scope: `comments` or `topics:read`. Requires `canView` (`403 forbidden` otherwise; `404 not_found` for an unknown key). `200`:

```json
{
  "documentKey": "contextual-conditions",
  "labels": [
    { "label": "latest", "display": "unlisted", "commentMode": "open", "publishedAt": null, "openInScope": 8 },
    { "label": "v0.2.0", "display": "current", "commentMode": "release",
      "publishedAt": "2026-11-18T12:43:53.621Z", "openInScope": 6 }
  ]
}
```

- Order: `latest`, `main`, then the newest `publishedAt` first, then by name.
- Readers (not `isAuthor`) do not get `authors_only` or `retired` labels, and get no `openInScope`.
- `openInScope` (authors only, information only): open, live, top-level comments a reader would see on the label: every open comment on an `open` label; otherwise those in scope (5.3.1), whatever the mode. It never blocks a change.

```http
PATCH {hub}/api/guide/{documentKey}/labels/{label}
Authorization: Bearer ihp_…
Content-Type: application/json

{ "display": "current", "commentMode": "release" }
```

- Scope `topics:write`, and the caller must satisfy `canEditTopic` (`403 forbidden` otherwise, for example a reader).
- `{label}` is the label as listed, percent-encoded in the path when needed.
- Body: `display` and/or `commentMode`, values as listed above; case is ignored. At least one is required (`400 invalid_request` for an empty body). Any other field, `null`, or an unknown value: `400 invalid_field` naming the field.
- Unknown label: `404 not_found`.
- Setting `current` turns the previous Current label into `listed` in the same transaction.
- Setting `closed` always succeeds, even with open comments in scope (decision V9). Those comments disappear on that label only.
- Changing the comment mode records the time (it becomes the publication time of a Release label that has no upload or build).
- `200` with the label object (as in `GET /labels`, with `openInScope`) plus `demoted`: the label object of the previous Current label, or `null`.
- A request that changes nothing returns `200` and records nothing. Each change is recorded in `es_topic_change` (`change_type` `GUIDE`, `{"label": {"documentKey", "label", "display": {"from", "to"}, "commentMode": {"from", "to"}, "demoted": {…}}}`, values in API spelling).
- `PATCH` already passes production Apache (AART #3962). The same settings are on the guide settings page.

```json
{
  "label": "v0.3.0",
  "display": "current",
  "commentMode": "release",
  "publishedAt": "2026-11-18T12:43:50.923Z",
  "openInScope": 4,
  "demoted": { "label": "v0.2.0", "display": "listed", "commentMode": "release",
               "publishedAt": "2026-11-18T12:43:53.621Z", "openInScope": 6 }
}
```

## 8. MCP

Endpoint: `{hub}/mcp`, `Authorization: Bearer <personal access token>`. Guide tokens are refused (`403 forbidden`), since they belong to a browser.

### 8.1 Transport (from H0, 2026-10-30)

Hand-written in InteropHub, with no MCP library (Decisions Log, 2026-10-30). Streamable HTTP, session-based, as proven with Claude Code in H0:

| Request | Response |
|---|---|
| `POST` `initialize` | `200` JSON. Negotiates protocol `2025-11-25`; also accepts `2025-03-26` and `2025-06-18`; answers `2025-11-25` to anything else. Sets `Mcp-Session-Id` (random UUID) bound to the token's user. |
| `POST` `notifications/initialized` and other notifications | `202`, empty body |
| `POST` `tools/list`, `tools/call`, `ping` | `200`, `Content-Type: application/json`, one JSON-RPC response. No SSE. |
| `GET` | `405`, `Allow: POST, DELETE`. No standalone event stream. |
| `DELETE` with the session ID | `204`, session dropped |
| Missing `Mcp-Session-Id` after `initialize` | `400` |
| Unknown or expired session (12 hours idle), or a session of another user | `404` |
| Unsupported `MCP-Protocol-Version` header | `400`. If absent, the version from `initialize` is used. |

Sessions are held in memory; a restart drops them and clients re-initialize.

### 8.2 Tools

Tools are those in design 9.3 and 9.4. Each tool is one class that calls the same service method as its HTTP endpoint above, needs the same scope (checked in the `tools/call` dispatcher), and returns the same JSON objects (threads, flags, documents) as text content in its result. Argument names are `snake_case` as in the design; result fields are `camelCase` as here. A tool error is returned as a tool result with `isError: true` and the Section 2 error object as text, never a stack trace. `close(ids)` closes each in turn and reports per-ID success or error.

## 9. Data Model Additions to the Design

The contract needs these on top of design Section 8. H1 to H3 own the schema.

- `es_guide_comment`: `updated_at` (thread-level, bumped by reply changes too), `deleted_at`, `deleted_by_user_id`, and `withdrawn_at` (used for replies, and for top-level comments alongside `state`).
- `es_guide_review_flag`: `updated_at`.
- `es_guide_document_url`: `sort_order`, and the `allow_id` of the allowlist row it created.
- `auth_login_code`: `code_challenge`, `code_challenge_method` (design 4.6).
- `app_user_token`: `scopes` (design 4.5).
- Approved guide URL prefixes: a small table edited by global admins, not a column on `hub_settings`, since there can be several.

## 10. Who Builds What

| Section | InteropHub step | Guide step |
|---|---|---|
| 3.1–3.4, 4, PAT creation on the account page | H1 | T2 |
| 3.5 sign-out, `prompt=login` | H1 | T2 ("Not you?" may slip to T3) |
| 3.6 `/guide/open`, approved URL prefixes | H2 (they need the guide registration tables) | T2 |
| 5.1, 5.3, 5.4, 5.5, `text` in 5.6, 5.7 | H2 | T2 |
| 7 | H2 (HTTP and registration page) | T4 (setup tooling) |
| 5.3.1, 5.4.1, `madeOnLabel`, 7.5 (version display and comment modes) | InteropHub, 2026-11-18 | Templates v0.22.0 |
| 5.6 author fields, 5.8, 6 | H3 | T3 |
| 8 | H4, E2 | none |
| 2 `help`, 2.1, 3.7, 3.8 (author onboarding) | InteropHub, 2026-11-18 | Templates v0.22.0 (links to the anchors, calls `GET /api/token`) |

T2 builds against a mock server in the templates repository that follows this document. The mock is generic and contains no InteropHub URLs or names.
