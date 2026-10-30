# Topic Reference Comments: API Contract

**Status:** Approved by the product owner 2026-10-30 (Gate 0), except Section 8, which waits for H0
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
  "message": "A change note is required when setting a disposition."
}
```

`field` is present only where one applies. `message` is for people; clients branch on `error`.

| Status | `error` | When |
|---|---|---|
| 400 | `invalid_request` | Malformed JSON, missing body, unknown query value |
| 400 | `invalid_field` | A field fails validation (`field` names it) |
| 401 | `invalid_token` | No bearer token, unknown, expired, or revoked token. Response carries `WWW-Authenticate: Bearer error="invalid_token"`. |
| 403 | `insufficient_scope` | Token lacks the scope. Adds `"requiredScope": "comments"`. `WWW-Authenticate: Bearer error="insufficient_scope", scope="comments"`. |
| 403 | `forbidden` | The user may not do this (not an author, not the commenter, cannot view the topic) |
| 403 | `edit_window_closed` | Commenter edit or withdraw after the 14-day window |
| 404 | `not_found` | Unknown document key, comment, or flag, or a comment that is deleted |
| 409 | `conflict` | `expectedUpdatedAt` did not match. Adds `"current": <thread>`. |
| 409 | `invalid_state` | The action does not fit the comment's state, for example replying to a closed thread or disposing a withdrawn comment |
| 409 | `document_inactive` | Writing to a document whose registration is inactive |
| 409 | `duplicate` | Registering a `documentKey` or URL that is already registered |
| 400 | `url_not_approved` | A guide URL outside the approved prefixes, by a caller who is not a global admin |
| 500 | `server_error` | Anything else. No stack traces in the body. |

## 3. Authentication

### 3.1 Tokens

| Kind | Issued by | Scopes | Lifetime | Raw form |
|---|---|---|---|---|
| Guide token | `POST /api/guide/token` | `comments` only | 30 days from issue, not extended by use | `ihg_` + 43 base64url characters (32 random bytes) |
| Personal access token | Account page | chosen by the user from Section 3.2 | user chooses 30, 90, or 365 days | `ihp_` + 43 base64url characters |

- Both are stored only as the SHA-256 hash of the full raw string in `app_user_token.token_hash`, with `app_id` of the `topic-reference` app, a `scopes` column (comma-separated), and a `label`. Guide tokens get the label `Guide sign-in: <origin>`.
- The prefixes make leaked tokens easy to recognize and let the filter tell the kinds apart without a lookup.
- `last_used_at` is updated at most once per minute per token.
- Sent as `Authorization: Bearer <raw token>` on every API and MCP call. Tokens are never accepted in a query string.

### 3.2 Scopes

| Scope | Allows |
|---|---|
| `comments` | Section 5 and Section 6 endpoints, and `GET /api/guide/{documentKey}` |
| `topics:read` | `GET /api/guide/{documentKey}`, and the topic read endpoints in `docs/tasks/topic-mcp-tools.md` |
| `topics:write` | Section 7 endpoints and the topic write endpoints. Implies `topics:read`. |

A scope only narrows what the user may already do. Each endpoint below names its required scope; a token with any one of the listed scopes is accepted.

### 3.3 The bearer-token filter

One filter serves `/api/guide/*`, `/api/guides`, `/api/topic-spaces/*`, `/api/topics/*`, and `/mcp`. It resolves the token to a user and a scope set, rejects with `401`/`403` as in Section 2, and makes the user and the `app_user_token_id` available to the servlet. Servlets check the scope their operation needs. The existing session cookie is **not** accepted on these paths.

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

InteropHub checks, in this order, refusing all failures with `400 invalid_request` and the same message ("Sign-in code is invalid or expired.") so the response does not reveal which check failed:

1. The code exists, was issued for `topic-reference`, is not expired, and is not consumed.
2. `returnTo` equals the stored `return_to`.
3. base64url(SHA-256(`codeVerifier`)) equals the stored `code_challenge`.
4. The request `Origin` is an allowed origin (Section 4).

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
- InteropHub picks the document's URL whose label matches `label`, or else its first URL in registration order, and redirects (`302`) to `<url>?interophub=signin#<anchor>` or `#comment-<id>`.
- Unknown, inactive, or URL-less documents show InteropHub's normal not-found page.
- The guide, on load, sees `interophub=signin`: if it holds an unexpired token it removes the marker with `history.replaceState` and does nothing else; otherwise it starts Step 1 at once.
- The link never carries a token or code.

## 4. CORS

Applies to every `/api/guide/*` and `/api/guides` endpoint. `/mcp` and the topic endpoints send no CORS headers.

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
  "createdAt": "2026-11-02T15:04:05.123Z",
  "editedAt": null,
  "editableUntil": "2026-11-16T15:04:05.123Z",
  "updatedAt": "2026-11-04T09:12:00.000Z",
  "disposedAt": "2026-11-04T09:12:00.000Z",
  "disposedBy": { "userId": 9, "displayName": "Sam Author", "organization": "AIRA" },
  "closedAt": null,
  "closedBy": null,
  "deleted": false,
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
      "createdAt": "2026-11-03T10:00:00.000Z",
      "editedAt": null,
      "editableUntil": "2026-11-17T10:00:00.000Z",
      "updatedAt": "2026-11-03T10:00:00.000Z",
      "withdrawn": false,
      "deleted": false,
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
- `permissions` are computed for the caller, so the guide shows only controls that will work. The server checks again on every write.
- Replies are ordered by `createdAt`. Threads are ordered by `createdAt` of the top-level comment.

### 5.3 `GET /api/guide/{documentKey}/comments`

Scope: `comments`. Requires `canView`.

| Query | Meaning |
|---|---|
| `state` | Comma-separated states. Default `OPEN,DISPOSED`. `all` means every state. |
| `anchor` | Only threads on this anchor. |
| `since` | A `serverTime` from an earlier response. Returns every thread whose `updatedAt` is **at or after** `since`, in any state, including tombstones. `state` is ignored when `since` is given. |

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
  "text": "Agreed, the framework already lists them."
}
```

- `anchor`, `blockIndex`, and `text` are required for a top-level comment; `snippet` is optional (agents may not have one); `sourceNote` is optional.
- For a reply, only `parentCommentId` and `text` are read; other fields are ignored.
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
  "clearedBy": null
}
```

### 6.2 Endpoints

| Method and path | Scope | Who | Result |
|---|---|---|---|
| `GET /api/guide/{documentKey}/review-flags?includeCleared=false` | `comments` | `canView` | `200 {"serverTime": …, "reviewFlags": [ … ]}` |
| `POST /api/guide/{documentKey}/review-flags` with `{"anchor": …, "note": …}` | `comments` | Authors | `201` with the flag. If the anchor already has an active flag, its note is replaced and `200` is returned with it, so the call is safe to repeat. |
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

## 8. MCP

Endpoint: `POST {hub}/mcp`, streamable HTTP, `Authorization: Bearer <personal access token>`. Guide tokens are refused (`403 forbidden`), since they belong to a browser.

Tools are those in design 9.3 and 9.4. Each one calls the same service method as its HTTP endpoint above, needs the same scope, and returns the same JSON objects (threads, flags, documents) as its result content. Argument names are `snake_case` as in the design; result fields are `camelCase` as here. `close(ids)` closes each in turn and reports per-ID success or error.

Transport details (library or hand-written, session handling, protocol version) are settled by the H0 experiment and added here before H4 starts.

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
| 3.1–3.4, 3.6, 4, PAT creation on the account page | H1 | T2 |
| 3.5 sign-out, `prompt=login` | H1 | T2 ("Not you?" may slip to T3) |
| 5.1, 5.3, 5.4, 5.5, `text` in 5.6, 5.7 | H2 | T2 |
| 7 | H2 (HTTP and registration page) | T4 (setup tooling) |
| 5.6 author fields, 5.8, 6 | H3 | T3 |
| 8 | H4, E2 | none |

T2 builds against a mock server in the templates repository that follows this document. The mock is generic and contains no InteropHub URLs or names.
