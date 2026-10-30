# H1: Sign-in Foundation (InteropHub)

You are working in the InteropHub repository. Other agents work in this repository at the same time, so work only in your own worktree and branch.

## Setup

Create a worktree under `C:\dev\immregistries\InteropHub.worktrees\` on a branch named `feature/h1-sign-in`, from the current `main`. Do not touch the main working copy. Local commits on your branch are fine; do not push, merge, or commit to `main`.

Local Tomcat and the local MySQL database are shared. Tomcat may be running another branch's WAR. **Ask the product owner before you deploy.** Additive schema changes to the local database are fine.

## Read first

- `CLAUDE.md`; `docs/database-release-practice.md` before touching `db/`; `docs/aira-web/README.md` before adding CSS.
- `docs/tasks/topic-reference-comments-api-contract.md`: **this is your specification.** You build Sections 1, 2, 3.1 to 3.5, and 4, and the token parts of 9 and 10. Where this prompt and the contract disagree, the contract wins; tell G.
- `docs/topic-reference-comments-design.md`, Sections 4 and 4.5, for background.
- `docs/tasks/topic-reference-comments-workstreams.md`, Section 8 (Decisions Log).
- `docs/external-application-login-flow.md` and `AuthFlowService`, `ApiAuthExchangeServlet`, and the `/home` and `/account` servlets: the flow you extend.
- `docs/localhost-detection.md` (`PublicUrlService.isLocalhostMode()`).
- **The H0 experiment** in `C:\dev\immregistries\InteropHub.worktrees\h0-mcp` (branch `experiment/h0-mcp`): `BearerTokenAuthenticator`, `UserTokenDao.findActiveByTokenHash`, and its tests. Port the authentication into the real filter; do not merge the branch, and do not bring over `/mcp` or `/mcp-sdk`.

Follow existing patterns: DAO per table, services hold rules, servlets render. Tests in `src/test/java`. No new libraries.

## Task

1. **Database**, in one clearly commented block at the **end** of `db/unapplied_updates.sql` (another stream also appends there; G merges):
   - `auth_login_code`: `code_challenge`, `code_challenge_method`.
   - `app_user_token`: `scopes`.
   - `app_registry` rows `topic-reference` (the guides' public client) and `interophub-api` (personal access tokens, not visible to users), both created idempotently.
2. **Login flow (contract 3.4, step 2).** `/home` accepts `code_challenge`, `code_challenge_method`, `response_mode`, and `prompt`. For `topic-reference`, PKCE with `S256` and `response_mode=fragment` are required, and `return_to` with a fragment is refused. The challenge is stored on the login code. Redirect to `return_to#code=…&state=…`. These parameters must survive the email, magic link, and new-user registration path, so the reader always lands back on the guide. Other apps behave exactly as today.
3. **`prompt=login`** (contract 3.4, 3.5): end the current InteropHub session, then continue as if there were none, keeping the external request so the reader still returns to the guide. Works for any app.
4. **`POST /api/guide/token`** and **`DELETE /api/guide/token`** per contract 3.4 step 4 and 3.5, including the order of checks and the single error message. `/api/auth/exchange` refuses `topic-reference` codes.
5. **Bearer-token filter** per contract 3.3, including both exemptions (preflight, and `POST /api/guide/token`). Map it to `/api/guide/*` now; the other paths come with later steps. Add the shared `requireScope` helper and the "servlet refuses if the user attribute is missing" guard. Token formats and storage per contract 3.1.
6. **CORS** per contract 4, as one reusable helper the later comment endpoints (H2) will use. Allowed origins come from enabled `app_redirect_allowlist` rows of `topic-reference`.
7. **Account page.** List the user's guide sign-ins and personal access tokens (label, kind, scopes, issued, expires, last used), revoke any of them, and create a personal access token with a label, scopes (`comments`, `topics:read`, `topics:write`), and a lifetime of 30, 90, or 365 days. Show the raw token once, with a note that it will not be shown again.
8. **Dev test setup script**, `db/scripts/topic_reference_dev_setup.sql`, per test plan Section 1.3, Gate 1 part only: idempotent; test users Author (active `CHAMPION` of topic 7), Reader, and Admin, with obviously fake emails; and an enabled `app_redirect_allowlist` row for `topic-reference` at `http://localhost:8001/`. Never applied to production; say so at the top of the file.
9. **Tests**: PKCE verification (good, wrong verifier, `plain`), code reuse and expiry, `returnTo` mismatch, filter failures (each returning the identical body), scope refusal, CORS allowed and refused origins and preflight, and `prompt=login`.

## Out of scope

- `es_guide_*` tables, guide registration, approved URL prefixes, `/guide/open` (H2).
- Comment and review flag endpoints (H2, H3).
- `/mcp` (H4). The filter will be mapped there later.

## Done when (test plan Gate 1, checks 1.20 to 1.34, 1.36, and 1.37)

- `mvn test` passes.
- With the setup script applied, G's test page `src/test/resources/guide-signin-test.html` on `main` (copy it into your worktree if needed), served from `http://localhost:8001/`, completes the round trip with and without an InteropHub session, and is refused from `http://localhost:8002/`.
- Existing external app login works as before.

## Report back

Files changed; the SQL added; how `prompt=login` keeps the external request through logout; the curl commands you used for checks 1.23 to 1.33 with their results; anything in the contract that was unclear or that you had to interpret. Do not push or merge.
