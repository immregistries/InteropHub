# Topic Reference Author Onboarding: Implementation Report

**Plan:** `topic-reference-author-onboarding.md` (decisions A1 to A7). **Date:** 2026-11-18.
**State:** All changes are in the working tree. Nothing is committed or pushed, and production was not contacted. There is no schema change, and `db/unapplied_updates.sql` is untouched.

## 1. Files changed and why

### New

| File | Why |
|---|---|
| `service/GuideHelp.java` | The help page path, its section anchors, the list of error codes, and `{hub}` resolution (`hub_settings.external_base_url`, cached once it resolves). The one place that builds help links (3.1, 3.3). |
| `service/GuideTokenInfoService.java` | `GET /api/token` body, and the "guides this account can publish to" list that the account page reuses. It has a small `Store` interface with a Hibernate implementation, so tests need no database (3.2). |
| `servlet/GuideTokenInfoServlet.java` | `/api/token`. GET only; any other method gets `405` with `Allow: GET` and an `invalid_request` body. |
| `servlet/TopicReferenceHelpServlet.java` | The public help page `/help/topic-reference-authors`. The content is a static method so tests can render it without a database (3.1). |
| `servlet/AccountTokensSection.java` | The account page's Access tokens section, moved out of `AccountServlet` so it can be tested: scope descriptions, the Author preset, the "How to use this token" panel, and expiry badges (3.4). |
| `servlet/GuidePublishBox.java` | The "Publish from your agent" box on the guide settings page (3.5). |
| `test/.../servlet/TopicReferenceAuthorOnboardingTest.java` | 16 tests covering plan Section 5. |

### Changed

| File | Why |
|---|---|
| `service/GuideApiErrors.java` | Every body gains `help`. The `INVALID_TOKEN_BODY` constant became `invalidTokenBody()`, because the hub base is resolved at runtime. The `insufficient_scope` message now names the scope. `invalidRequest` gained an overload that takes a status (used for the 405). |
| `service/GuideApiException.java` | New `notEditor(topicName)` and `notEditor(lead, topicName)` for `403 forbidden` on topic actions. |
| `service/GuideBuildService.java`, `GuideHostingService.java`, `GuideLabelService.java`, `GuideRegistrationService.java`, `GuideCommentService.java` | Topic-editor refusals now use `notEditor`, naming the topic only when the caller passes `canView`. `GuideBuildService.Store` gained a default `canView` (false) so existing test fakes still compile. |
| `service/HibernateGuideBuildStore.java` | Implements `canView`. |
| `servlet/GuideBearerTokenFilter.java` | Javadoc; `init()` wires `GuideHelp` to hub settings. |
| `servlet/AccountServlet.java` | Delegates to `AccountTokensSection`. Applies the preset on the server. The one-time flash now holds the token, its expiry, and its scopes (`AccountTokensSection.Revealed`) instead of only the raw string. Dead formatting helpers removed. |
| `servlet/GuideRegistrationPageServlet.java` | Renders the publish box. `init()` wires `GuideHelp`. |
| `webapp/WEB-INF/web.xml` | Bearer filter mapped to `/api/token`; servlets added for `/api/token` and `/help/topic-reference-authors`. |
| `test/.../service/GuideSignInFoundationTest.java` | `INVALID_TOKEN_BODY` → `invalidTokenBody()`. |
| `docs/tasks/topic-reference-comments-api-contract.md` | Amendments (Section 4 below). |

(Paths are under `src/main/java/org/airahub/interophub/` unless shown otherwise.) Several of these files already carried uncommitted work from the version-display task. These edits sit on top of that work.

## 2. `GET /api/token`

**Final path:** `/api/token` (`{hub}/api/token`), behind the existing bearer filter. `/api/guide/token` (the guide's token exchange) is unchanged.

Response from the browser check (editor `author.guide-test@example.invalid`, Author token, local hub):

```json
{
  "user": {"userId": 110, "displayName": "Guide Author"},
  "kind": "personal",
  "label": "onboarding check (browser)",
  "scopes": ["comments", "topics:write"],
  "expiresAt": "2027-02-16T13:39:08.000Z",
  "expiresInDays": 90,
  "guides": [
    {"documentKey": "contextual-conditions", "title": "Contextual Conditions", "topicId": 7,
     "topicName": "CDS Contextual Conditions", "hosting": "EXTERNAL", "active": true, "canPublish": true},
    {"documentKey": "guide-hosted-test", "title": "Guide Hosted Test", "topicId": 189,
     "topicName": "Guide Private Test Topic", "hosting": "HUB", "active": true, "canPublish": true},
    {"documentKey": "guide-private-test", "title": "Guide Private Test", "topicId": 189,
     "topicName": "Guide Private Test Topic", "hosting": "EXTERNAL", "active": true, "canPublish": true}
  ],
  "help": "http://localhost:8080/hub/help/topic-reference-authors#if-you-are-an-agent"
}
```

The reader (`reader.guide-test@example.invalid`) got `"guides": []`. With no token, or a bad one, the endpoint returns:

```text
401  WWW-Authenticate: Bearer realm="interophub", error="invalid_token"
{"error":"invalid_token","message":"The access token is missing, invalid, expired, or revoked.","help":"http://localhost:8080/hub/help/topic-reference-authors#troubleshooting-invalid_token"}
```

The response also sets `Cache-Control: no-store`.

## 3. Help page anchors, as built

`{hub}/help/topic-reference-authors` is public and uses the normal page shell.

- Sections: `#who-can-author`, `#create-a-token`, `#where-the-token-goes`, `#what-an-agent-can-do`, `#if-you-are-an-agent`, `#troubleshooting`.
- Errors: `#troubleshooting-<error>` for every contract Section 2 code, with the code spelled exactly as the API writes it, underscores included: `invalid_token`, `insufficient_scope`, `forbidden`, `not_found`, `document_inactive`, `comments_closed`, `invalid_request`, `invalid_field`, `invalid_state`, `edit_window_closed`, `conflict`, `duplicate`, `url_not_approved`, `server_error`.
- Firewall case: `#troubleshooting-html-response`.

The "exact words to relay" appear as "Tell the author:" quotes. The page shows the real `{hub}` URL. It names no person and contains no token: only the `ihp_` and `ihg_` prefixes, which a test checks.

## 4. Contract amendments in brief

- Header: a 2026-11-18 author-onboarding amendment note.
- **Section 2:** the error shape shows `help`. A new paragraph defines `help` (always present; `#troubleshooting-<error>`, falling back to `#troubleshooting`; additive). It also notes the `forbidden` and `insufficient_scope` message changes.
- **2.1 (new):** the help page and its anchor table, as a contract.
- **3.3:** `/api/token` added to the filter's paths.
- **3.7 (new):** `GET /api/token`: access, errors, response, and field rules (`expiresInDays` rounds up; `guides` is `[]` for a reader; scope order).
- **3.8 (new):** account-page token notes (scope lines, Author preset with `preset=author`, How-to-use panel, expiry badges) and the guide settings page box.
- **Section 4:** CORS also applies to `/api/token`.
- **Section 10:** a row for this work.

The plan said "3.x" for the endpoint and "3.4" for the token page notes. 3.4 is already the sign-in round trip, so they became 3.7 and 3.8.

## 5. Decisions the plan didn't cover, and deviations

1. **Anchor spelling:** `#troubleshooting-invalid_token`, with the code verbatim (underscores), so a client can build it by concatenation. The plan's `#troubleshooting-<code>` reads this way too.
2. **Entries for every code, not only the nine listed.** The five extra codes also get their own anchors, so the `#troubleshooting` fallback applies only to an unknown code.
3. **Firewall entry anchor:** `#troubleshooting-html-response`. The plan gave none.
4. **The `invalid_token` message changed** from "The access token is invalid." to "The access token is missing, invalid, expired, or revoked." It is still one body for every cause.
5. **Author-only comment refusals** ("Only an author can delete a comment." and similar) keep their first sentence and add the editor sentence with the topic name.
6. **`/api/token` with non-GET methods** returns `405` with an `invalid_request` body. The contract table lists `invalid_request` as 400; 3.7 documents the 405.
7. **The help base URL** is resolved from hub settings the first time it is needed and cached for the life of the process, like `PublicUrlService.isLocalhostMode`. The bearer filter, the help, token, and settings servlets, and `AccountServlet` wire it. Unit tests get `http://localhost:8080/hub`.
8. **Author preset:** a checkbox (`preset=author`). Script ticks the boxes, and the server adds the same scopes, so it also works without script (confirmed with curl for the reader token).
9. **Token page extras:** the panel also says to restart the agent from a new terminal after `setx`. An **Expired** badge sits next to **Expires soon**.
10. **The publish box** shows every registered guide on the topic, not just the one being edited. Its PATCH example is the bash/curl form, plus a note on `--hub` and `--document` for when `guide.json` lacks them.
11. **The help page links** to the public GitHub repository of content-publishing-templates. That repository was read for the tool's flags and not edited.
12. **Production note, not checked:** the earlier 0.11.0 finding was that Apache in front of production blocked API guide uploads. `/hub/api/token` and `/hub/help/...` are new paths. Confirm they pass Apache after release (`curl {hub}/api/token` should return the JSON 401, not HTML).

## 6. Tests

| Run | Command | Result |
|---|---|---|
| Baseline, before changes | `mvn -o test` | 439 run, 0 failures, 0 errors, 3 skipped |
| New tests | `mvn -o test -Dtest=TopicReferenceAuthorOnboardingTest` | 16 run, 0 failures |
| Full suite, after | `mvn -o test` | **455 run, 0 failures, 0 errors, 3 skipped**, BUILD SUCCESS |

The new tests cover:
- **Help page:** renders without sign-in (signed-out shell, no redirect); every section anchor and every `#troubleshooting-<code>` anchor exists; no token-like strings and no `@`.
- **`/api/token`:** personal token for an editor of two topics (third topic and inactive guide excluded, sorted, exact key set); guide token; `canPublish` follows `topics:write`; reader gets `guides: []`; no email, token, or hash in the body; 401 with `help` through the filter for a missing, rejected, or Basic token, and from the servlet without a user; 405 for POST.
- **Errors:** every `GuideApiException` factory and every `GuideApiErrors` helper carries the right `help` anchor, which exists on the page, and existing fields are unchanged. `notEditor` names the topic only when given.
- **Token page:** the preset scopes and markup; the panel appears only with the reveal; the token appears exactly once; the 14-day badge.
- **Settings page:** HUB and EXTERNAL commands.

## 7. Browser check (local Tomcat)

Deployed with `mvn clean package -Plocal-tomcat-deploy` and signed in with the dev magic link as `author.guide-test@example.invalid` (non-admin editor of topics 7 and 189).

1. **Token page:** ticking **Author (publish guides and manage comments)** ticked `comments` and `topics:write`. Scope descriptions show under each box.
   Screenshot: `C:\Users\NATHAN~1\AppData\Local\Temp\claude-chrome-screenshots-ey0XTE\screenshot-1795009143098-0.jpg`
2. **Create token:** the copy box and the **How to use this token** panel appeared (agent sentence, `$env:`, `setx`, `export`, `gh secret set`, expiry 2027-02-16, three guides, help link). A DOM check found the token exactly once in the page.
   Screenshot: `…\screenshot-1795009170457-1.jpg`. This screenshot shows the token value, which has since been revoked.
3. **Shell:** followed the panel's bash line (`export INTEROPHUB_TOKEN=…`, value read from a scratchpad file, never echoed), then `curl -H "Authorization: Bearer $INTEROPHUB_TOKEN" …/api/token`. The response is in Section 2.
4. **`insufficient_scope`:** a `comments`-only token on `PATCH /api/guide/guide-hosted-test/labels/latest` returned `403`, `requiredScope: topics:write`, `help …#troubleshooting-insufficient_scope`. The scope check comes before any change, so the label was not touched.
5. **`forbidden`:** a reader with an Author token on `POST /api/guide/guide-hosted-test/builds` returned `403`, with the message "Your account must be an editor of the topic "Guide Private Test Topic" to do this. …" and `help …#troubleshooting-forbidden`. Refused before anything was stored.
6. **Help links:** both links opened the help page scrolled to the right entry.
   Screenshots: `…\screenshot-1795009216433-2.jpg` (insufficient_scope) and `…\screenshot-1795009224870-3.jpg` (forbidden).
7. **Settings page** `/es/topic-guide/189`: "Publish from your agent" shows `guide-hosted-test` (HUB, `--label vX.Y.Z --label latest`) and `guide-private-test` (EXTERNAL, `--manifest-only --label vX.Y.Z`), each with the PATCH example.
   Screenshot: `…\screenshot-1795009234185-4.jpg`
8. **Cleanup:** my three tokens (ids 46, 47, 48) are revoked, and each returns `401` on `/api/token`.
   **Mistake, fixed:** the revoke loop also revoked three existing guide sign-in tokens on the reader account (ids 38, 40, 50). Token 50 was issued during this session, possibly by the parallel templates work. I restored all three by clearing `revoked_at` in the local database; they are active again. If a guide page lost its sign-in in the minute between, signing in again fixes it.

Screenshot folder: `C:\Users\NATHAN~1\AppData\Local\Temp\claude-chrome-screenshots-ey0XTE\`. It is temporary, so copy anything you want to keep.

No guide data on `guide-hosted-test` (labels, uploads, builds, comments) was changed.

## 8. How to verify by hand

1. `mvn -o test`: expect 455 run, 0 failures, 3 skipped.
2. Deploy locally. Open `http://localhost:8080/hub/help/topic-reference-authors` in a private window: it loads without sign-in. Try each anchor in Section 3.
3. `curl -i http://localhost:8080/hub/api/token`: expect `401` JSON with `help …#troubleshooting-invalid_token`.
4. Sign in as an editor and open **Account**. Tick the Author preset and create a token. Check that the panel appears, then reload: the panel and the token are gone. Set `INTEROPHUB_TOKEN` as the panel shows, then:
   `curl -s -H "Authorization: Bearer $INTEROPHUB_TOKEN" http://localhost:8080/hub/api/token`
5. With a `comments`-only token, PATCH any label and expect `403 insufficient_scope`. As a non-editor, POST `/builds` and expect `403 forbidden` naming the topic. Open each `help` link.
6. Open `/hub/es/topic-guide/<topicId>` and find **Publish from your agent**.
7. Revoke the test tokens on the Account page.
8. After the production release: `curl -i {hub}/api/token` must return JSON, not an Apache HTML page.
