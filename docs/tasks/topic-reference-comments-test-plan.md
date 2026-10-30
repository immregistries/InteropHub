# Topic Reference Comments: Integration Test Plan

**Status:** Approved by the product owner 2026-10-30 (Gate 0)
**Implements:** the gates in `docs/tasks/topic-reference-comments-workstreams.md`, Section 5
**Primary purpose:** What stream G runs at each gate, how, and what counts as a pass. Each step's own unit tests are the step's job; this plan checks that the parts work together and match `docs/tasks/topic-reference-comments-api-contract.md` (cited as "contract").

## 1. Test Environment

### 1.1 Fixed values

| Item | Value |
|---|---|
| Local InteropHub | `http://localhost:8080/hub` |
| Guide origin A (approved) | `http://localhost:8001` serving the built guide at `/contextual-conditions/` |
| Guide origin B (not approved) | `http://localhost:8002` serving the same files |
| Pilot | `C:\dev\writing\contextual-conditions`, `documentKey` `contextual-conditions`, topic 7 |
| Templates fixture | `C:\dev\writing\content-publishing-templates\tests\fixture-project` |

Serve with `python -m http.server 8001 --directory <build output parent>` so the URL path matches the registration.

### 1.2 Test users

Signed in with the dev sign-in skill (`interophub-dev-signin`), each in its own browser profile:

| User | Role |
|---|---|
| **Author** | Champion of topic 7 (not a space admin), to prove the 2026-10-30 rule |
| **Reader** | Signed-in user who can view topic 7, with no rights on it |
| **Outsider** | Signed-in user who cannot view a private test topic (for visibility checks) |
| **Admin** | Global admin |

### 1.3 Repeatable setup

The dev database is rebuilt daily, so test data cannot be set up by hand. H1 adds `db/scripts/topic_reference_dev_setup.sql`: idempotent, run after a refresh, never applied to production. It:

- creates or finds the four test users and gives Author a `CHAMPION` subscription on topic 7;
- creates a private test topic space with one topic and a guide registration for it, for the visibility checks (from H2 on);
- registers the pilot guide at `http://localhost:8001/contextual-conditions/` (from H2 on; at Gate 1 it inserts the allowlist row directly).

Approved URL prefixes for localhost need no data, thanks to the contract's dev rule (contract 7.3).

### 1.4 Tools

- Two browser profiles for two-user checks, or Claude in Chrome for one of them.
- `curl` for API checks. Every API check below names the contract section it proves.
- Claude Code for MCP checks.
- `validate.ps1` in the templates repository.

## 2. Gate 0

| # | Check | Pass |
|---|---|---|
| 0.1 | Product owner reads the contract and this plan | Approved, with any changes recorded in the Decisions Log |
| 0.2 | H0 report: Claude Code lists and calls the experiment tool on local InteropHub | Shown working; bad token refused |
| 0.3 | H0 library decision | Recorded in Decisions Log; contract Section 8 updated |

## 3. Gate 1

### 3.1 T1, anchors in local mode

| # | Check | Pass |
|---|---|---|
| 1.1 | `validate.ps1` | Passes |
| 1.2 | Fixture build: anchor elements and `data-anchor`, `data-block-index` on every commentable block | Present, indices 0-based per area |
| 1.3 | Local comment on a block; edit the block's text slightly; rebuild | Comment stays on that block |
| 1.4 | Rewrite the block completely; rebuild | Comment shows at the start of the area |
| 1.5 | Remove the anchor; rebuild | Comment under "Orphaned comments" |
| 1.6 | Comments saved by the previous release (no anchor fields) | Still load; none lost |
| 1.7 | `#<anchor>` and `#comment-<id>`, on load and on hash change | Scrolls; comment highlighted |
| 1.8 | Duplicate anchor, malformed anchor, anchor equal to a heading ID | `check_content.py` reports each |
| 1.9 | Word build and reading copy | No anchor text |
| 1.10 | Release tag created by the product owner; no internal names in the diff | Tag exists; diff reviewed by G |
| 1.11 | **P1:** pilot adds anchors on the new release and builds | Builds clean; checks 1.3 and 1.7 pass on the pilot |

### 3.2 H1, sign-in foundation

Run the guide side with a small test page, since T2 does not exist yet. G writes it: a static HTML page served from origin A that runs contract 3.4 steps 1, 4, and 5 and shows the response. It lives in `src/test/resources/guide-signin-test.html` in InteropHub.

| # | Check | Contract | Pass |
|---|---|---|---|
| 1.20 | Test page from origin A, Reader with an InteropHub session | 3.4 | Returns to the page with `#code` and `state`, gets a token, fragment removed, original `#anchor` restored |
| 1.21 | Same with no InteropHub session | 3.4 step 2 | Email, magic link (dev bypass), then back to the page with a token |
| 1.22 | Same page from origin B | 3.4, 4 | InteropHub refuses `return_to`; no code issued |
| 1.23 | Token call from origin B with a valid code (curl with `Origin`) | 4 | `403`, no CORS headers; code not consumed |
| 1.24 | Preflight from A and from B | 4 | A: `204` with the listed headers. B: `403` |
| 1.25 | Wrong `codeVerifier`; reused code; expired code; mismatched `returnTo` | 3.4 step 4 | Each `400 invalid_request`, same message |
| 1.26 | Request without `code_challenge`, or with `plain`, for `topic-reference` | 3.4 step 2 | Refused |
| 1.27 | `topic-reference` code at `/api/auth/exchange`; another app's code at `/api/guide/token` | 3.4 | Both refused |
| 1.28 | Existing external app login (`/api/auth/exchange` for an existing app) | 3.4 | Unchanged |
| 1.29 | Token in the database | 3.1 | Hashed, `ihg_` prefix on the raw value, `topic-reference` app, scopes `comments`, expires in 30 days, label names the origin. A PAT is on the `interophub-api` app with `ihp_`. |
| 1.30 | Bearer filter: no token, bad token, revoked token, expired token (set `expires_at` in the past), token of another app, inactive user | 2, 3.3 | `401 invalid_token` with `WWW-Authenticate`, identical body each time |
| 1.31 | Guide token on an endpoint that needs `topics:write` | 2, 3.2 | `403 insufficient_scope` with `requiredScope` |
| 1.32 | Session cookie only, no bearer, on `/api/guide/*` | 3.3 | `401` |
| 1.33 | `DELETE /api/guide/token` with a guide token; then use it; same with a PAT | 3.5 | `204`, then `401`; PAT gets `403` |
| 1.34 | Account page: list guide sign-ins, revoke one, create a PAT with chosen scopes and shown once | 3.1 | Works; revoked token gets `401` |
| 1.36 | `topic-reference` entry in `app_registry` comes from `db/unapplied_updates.sql` and survives a DB refresh | Release practice | Present after `interophub-dev-db-restore` |
| 1.37 | `/home?…&prompt=login` with an existing session | 3.4 step 2 | Session ended, email requested, returns to the page as the newly signed-in user |

### 3.3 E1, topic edit service

| # | Check | Pass |
|---|---|---|
| 1.40 | Edit page, admin topic creation, and JSON Lines import behave as before (same validation messages, same saved values) on a few existing topics | No visible change other than the permission change |
| 1.41 | Author (champion) edits topic 7 on the edit page; Reader cannot | As decided 2026-10-30 |
| 1.42 | Space admin creates a topic through Add topic; champion cannot; button shows only for those who may | Per topic tools Section 8 |
| 1.43 | Callers of `canEditTopic` and topic-level `canAdministerSpace` checks | E1 report lists each and what it now uses; G reviews |
| 1.44 | `es_topic_change` rows for web, import, and admin saves | Correct `source`, old and new values |

**Gate 1 passes** when 3.1, 3.2, and 3.3 pass and the product owner approves.

## 4. Gate 2

Pilot built with T2 and the `comments` setting pointing at local InteropHub, served from origin A.

### 4.1 API checks (curl, before the browser)

| # | Check | Contract | Pass |
|---|---|---|---|
| 2.1 | `GET /api/guide/contextual-conditions` as Reader, Outsider (private topic), and with an unknown key | 5.1 | Reader: `canView` true. Outsider on the private guide: `200`, `canView` false, `topic` null. Unknown: `404` |
| 2.2 | Post a comment, a reply, a reply to a reply | 5.4 | `201` thread; `201` thread; `400 invalid_field` |
| 2.3 | Field limits: empty text, 4,001 characters, 121-character snippet, bad anchor | 1.2, 5.4 | `400 invalid_field` naming the field |
| 2.4 | `since` returns new comments, edited comments, replies (as their thread), withdrawn comments, and tombstones | 5.3 | All present; `serverTime` usable as the next `since` |
| 2.5 | Edit own comment; edit another's; edit after the window (set `created_at` back 15 days) | 5.6 | `200`; `403 forbidden`; `403 edit_window_closed` |
| 2.6 | Withdraw own comment and own reply | 5.7 | Text `null`; reply to the withdrawn thread `409 invalid_state` |
| 2.7 | `expectedUpdatedAt` stale | 5.6 | `409 conflict` with `current` |
| 2.8 | Registration: register with a non-approved URL as Author, then as Admin; duplicate URL; dry run | 7 | `400 url_not_approved`; `201`; `409 duplicate`; dry run saves nothing |
| 2.9 | Registration adds and removes allowlist rows; inactive document refuses writes | 7.3, 2 | Rows follow URLs; `409 document_inactive` |
| 2.10 | Audit rows for each change | 5.6 | Present |

### 4.2 Browser checks

| # | Check | Pass |
|---|---|---|
| 2.20 | Reader opens the plain URL | Document shows; rail says "Sign in to comment"; no automatic redirect |
| 2.21 | Reader signs in, once with a session and once as a brand-new user who must register | Both return to the same place in the guide; first-comment notice shown once |
| 2.22 | Reader and Author, two profiles, both on the guide | Each sees the other's comment and reply without reloading, within the back-off timing |
| 2.23 | Back-off: watch network calls for 15 minutes idle, then post | Intervals 30 s, 1 min, 10 min, 30 min; reset to 30 s after posting |
| 2.24 | Hide the tab, wait, show it | No calls while hidden; immediate check on show |
| 2.25 | Outsider opens the private test guide | Signed in, told they cannot see comments; no comments shown |
| 2.26 | Not signed in | No comments visible at all |
| 2.27 | Revoke the guide token on the account page while the guide is open | Next call shows "Sign in to comment"; no redirect |
| 2.28 | "Open and comment" from InteropHub via `/guide/open` | Lands on the comment, signed in, in under a second with a session |
| 2.29 | Local mode still works for a guide with no `comments` setting | As in T1 |
| 2.30 | T2's mock server tests in the templates repository | Pass |
| 2.31 | Rail shows "Signed in as …"; Sign out; Not you? (if built in T2) | Sign out forgets the token; Not you? lands on the email prompt and returns as the other user |
| 2.32 | `/guide/open?doc=…&anchor=…` and `&comment=…` | 3.6 | `302` to the URL with `?interophub=signin#…`; unknown doc gets not-found page |

### 4.3 Production (P3)

After release, with the pilot registered on production at its real URL: repeat 2.20 to 2.22 and 2.28 with two real accounts. **Gate 2 passes** after 4.1, 4.2, and 4.3, and the product owner's approval.

## 5. Gate 3

A real review round on the pilot, on production or a local copy, with the product owner as Author.

| # | Check | Contract | Pass |
|---|---|---|---|
| 3.1 | Dispose several comments with change notes, each disposition at least once | 5.6 | Shown in the guide to everyone |
| 3.2 | Dispose without a change note; change a disposition; clear it | 5.6 | `400`; replaced; back to `OPEN` |
| 3.3 | Close comments; "Show closed" | 5.6 | Hidden by default; shown with the option; replies refused |
| 3.4 | Reopen a closed comment | 5.6 | Returns to `DISPOSED` or `OPEN` |
| 3.5 | Delete an inappropriate comment and a reply | 5.8 | Gone for everyone at next refresh; row kept in the database |
| 3.6 | Rename an anchor, rebuild, publish | 5.2 | Its comments become orphans; Author re-attaches one in the guide |
| 3.7 | Flag two areas; repeat a flag; clear one | 6 | Highlighted and listed for all viewers; repeat updates the note; cleared one disappears |
| 3.8 | Reader tries author actions through the API | 5.6, 5.8, 6 | `403 forbidden` |
| 3.9 | Topic page panel and comment management page | design 10 | Recent open comments and flags; links go through `/guide/open` to the right place |
| 3.10 | P2 guidance in `content-publishing-process` | design 12 | Reviewed by G against design 3 and 12 |

## 6. Gate 4

| # | Check | Pass |
|---|---|---|
| 4.1 | Create a PAT with `comments`; connect Claude Code to `/mcp` | Tools listed |
| 4.2 | In the pilot repository, an agent works through a round using only MCP tools: lists comments, finds each by anchor and snippet, edits the text, disposes with change notes, replies, flags, closes | Every comment placed or reported as unplaceable; no guesses |
| 4.3 | Same operations through MCP and HTTP give the same JSON | Matches contract shapes |
| 4.4 | PAT without `topics:write` calls `register_guide`; guide token used against `/mcp` | `insufficient_scope`; `forbidden` |
| 4.5 | An agent sets up a new Topic Reference repository in one session (topic tools 6.1): find or create topic, dry run, register guide, write `guide.json` | Done in one session; result builds and signs in |
| 4.6 | E2 topic tool permission tests (topic tools step 8) | Pass |
| 4.7 | Agent drafts commit messages and a change log entry from a round | No commenter names; duplicate themes combined (design 3.2) |

## 7. Recording Results

For each gate, G writes a short report to `docs/tasks/gates/gate-<n>.md`: the date, each check with pass or fail and a note, failures sent back to which step, and the product owner's approval. The status table in the workstreams document links to it.
