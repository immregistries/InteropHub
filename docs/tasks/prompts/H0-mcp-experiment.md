# H0: Remote MCP Server Experiment (InteropHub)

You are working in the InteropHub repository (`C:\dev\immregistries\InteropHub`). This is a **throwaway experiment**. Its output is a working spike and a short written recommendation, not code to merge.

## Context

Read first:

- `CLAUDE.md`
- `docs/topic-reference-comments-design.md`, Sections 4.4, 4.5, and 9.3 (personal access tokens, scopes, and the MCP server)
- `docs/tasks/topic-reference-comments-workstreams.md`, Sections 1 and 4 (step H0)

InteropHub later hosts a remote MCP server over **streamable HTTP**, authenticated by a bearer personal access token, so authors connect from Claude Code (and similar clients) without installing anything. Each tool will call the same service layer as the HTTP API. Nobody has run an MCP server inside this app's stack yet, so this experiment proves it works and decides how to build it.

Stack facts: Java 17, Jakarta Servlet 6.0 (Tomcat 10.1+), Hibernate 6, `org.json`, servlets registered in `src/main/webapp/WEB-INF/web.xml`, no servlet filters yet. WAR context path `/hub`.

## Do this on a separate worktree and branch

Create a worktree under `C:\dev\immregistries\InteropHub.worktrees\` on a branch named `experiment/h0-mcp`. Do not touch the main working copy; other agents work there. Do not commit to `main`, push, or merge.

## Task

1. Add an MCP endpoint at `/hub/mcp` (streamable HTTP transport) with **one read-only tool**, for example `whoami` (returns the authenticated user's display name and email) or `list_topic_spaces` (code and name of spaces the user can view, via `TopicSpaceAccessService`). Pick whichever is quicker; the point is that a real service call runs with the token's user.
2. **Bearer-token auth.** Requests without a valid `Authorization: Bearer …` get `401` with a `WWW-Authenticate: Bearer` header. For the experiment, validate against `app_user_token` (SHA-256 hash in `token_hash`, not expired, not revoked) if that is straightforward; a dev-only hard-coded token is acceptable if it is not, as long as it is clearly marked and cannot run outside dev. Do **not** add the `scopes` column or touch `db/unapplied_updates.sql`. If you need a token row, insert it by hand in the local database and record the SQL in your report.
3. Try **both** implementation routes far enough to compare them:
   - **The official MCP Java SDK** (`io.modelcontextprotocol.sdk`), using its servlet-based streamable HTTP transport, if one exists that runs on Jakarta Servlet 6 under Tomcat. Note the dependencies it pulls in (Jackson, Reactor, and so on), the WAR size change, and any conflicts with existing dependencies.
   - **A hand-written implementation**: a servlet that handles JSON-RPC 2.0 over POST for `initialize`, `notifications/initialized`, `tools/list`, `tools/call`, and `ping`, returns `application/json` responses (no SSE needed), answers `GET` with `405`, and handles the `Mcp-Session-Id` and `MCP-Protocol-Version` headers as the current spec requires.
4. Connect from **Claude Code** and call the tool, for example:
   `claude mcp add --transport http interophub-dev http://localhost:8080/hub/mcp --header "Authorization: Bearer <token>"`
   Confirm the tool is listed and a call returns real data. Confirm a bad token is refused and Claude Code shows a sensible error.

## Out of scope

- Any real comment, guide, or topic tools (H4, E2).
- The `scopes` column, the account page, token creation UI (H1).
- Any change to `db/` files, CSS, or existing pages.
- OAuth discovery for MCP clients. Bearer header only.

## Report back (this is the deliverable)

Write `docs/tasks/prompts/H0-mcp-experiment-report.md` on the experiment branch, and paste it in your final message:

1. What you built, and the exact steps to reproduce the Claude Code connection.
2. For each route: did it work, dependencies added, WAR size before and after, problems hit, lines of code.
3. MCP protocol version negotiated, and how sessions were handled.
4. **Recommendation**: SDK or hand-written, with reasons, including how each will fit the "every tool calls the same service as the HTTP API" rule and a future bearer-token filter shared with `/api/guide/*`.
5. Anything H1 or H4 needs to know (for example how the bearer filter should be shaped so both the API and MCP use it).

Done when: Claude Code lists and calls the tool against local InteropHub, a bad token is refused, and the report is written. Do not commit unless the product owner says to.
