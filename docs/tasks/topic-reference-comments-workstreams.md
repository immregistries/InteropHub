# Topic Reference Comments: Workstreams and Sequence

**Status:** Proposed plan for implementation
**Implements:** `docs/topic-reference-comments-design.md` (the design) and `docs/tasks/topic-mcp-tools.md` (the topic tools task)
**Primary purpose:** Divide the work across several coding agents, set the order, and define the gates where the parts are integrated and tested before the next step starts.

## 1. Approach

- **Split by repository.** Each workstream owns one repository, so agents do not edit the same files.
- **Contract first.** The guide (in `content-publishing-templates`) and InteropHub meet only at the comment API and the sign-in exchange. A written contract is agreed before either side is built, so both can be built in parallel and integration is checked against the contract.
- **One agent at a time per repository.** In InteropHub especially, `db/unapplied_updates.sql`, `web.xml`, and shared services such as `TopicSpaceAccessService` cause conflicts, and the daily database rebuild makes conflicts in the SQL file costly. A second InteropHub agent works only on its own branch, merged in the order the gatekeeper sets.
- **Gates.** Each gate has a test that must pass, run by the gatekeeper and approved by the product owner, before the next steps start.
- **Riskiest unknown first.** A remote MCP server inside InteropHub's servlet and Tomcat setup is the least proven piece, so it gets a throwaway experiment at the start.

## 2. Roles

| Stream | Proposed agent | Repositories | Role |
|---|---|---|---|
| **G: Gate and integration** | Claude, in its own thread | All, read mostly | Writes the API contract, test plan, and each step's prompt. Reviews each step against the design. Runs integration tests. Keeps the status table in Section 7. |
| **T: Templates** | Codex | `content-publishing-templates` | Guide build, `guide.js`, validation, template project, guidance. |
| **H: InteropHub comments** | Cursor | `InteropHub` | Sign-in, registration, comments, author review, MCP server. |
| **E: Topic tools** | GitHub Copilot, on a branch | `InteropHub` | The topic tools task. |
| **P: Process and projects** | Codex | `content-publishing-process`, content projects | Internal workflow and agent guidance, then project migration. |

Agent assignments are a starting point and can change. The streams, steps, and gates do not depend on which agent does the work.

## 3. Rules Every Prompt Carries

Each step's prompt, written by G, includes:

- the design sections the step implements, by number;
- what is out of scope for the step, including later steps;
- the done criteria and how the gate will test them;
- the repository's own rules, below.

| Repository | Rules to include |
|---|---|
| `content-publishing-templates` | Read `AGENTS.md`. Public repository: no project names, private URLs, names, or internal material. No third-party Python dependencies. Do not edit `aira.css` or the logo. Run `validate.ps1`. Regenerate and review the fixture reading copy after changing `build_core_markdown.py`. A CHANGELOG entry and release tag for each change to built output. |
| `InteropHub` | Read `CLAUDE.md`. Read `docs/database-release-practice.md` before touching `db/`. Read `docs/aira-web/README.md` before adding CSS. Follow existing patterns: DAO per table, services hold rules, servlets render. Tests in `src/test/java`. No new frameworks without approval (the MCP experiment decides the one exception). |
| `content-publishing-process` | Read `AGENTS.md`. Internal material stays here and is not copied to the public templates. |
| Content projects | Read the project's `AGENTS.md`. Change the `process/` submodule only to a released tag. |

No agent commits, pushes, tags, or merges without the product owner's instruction.

## 4. Workstream Steps

### Stream G: Gate and integration

| Step | Work | Output |
|---|---|---|
| G0 | Write the API contract from design Sections 4 and 9: every endpoint, request and response JSON with examples, error shape, status codes, CORS rules, the PKCE sign-in and token exchange, the `/guide/open` redirect, and scopes. | `docs/tasks/topic-reference-comments-api-contract.md` |
| G0 | Write the integration test plan for Gates 1 to 4 (Section 5). | `docs/tasks/topic-reference-comments-test-plan.md` |
| G0 | Write the MCP experiment prompt and review its result. | Decision recorded in Section 8 |
| Each step | Write the step's prompt. Review the result against the design and contract. | Prompts kept beside this file, for example `docs/tasks/prompts/T1-anchors.md` |
| Each gate | Run the gate test. Record the result in Section 7. | Gate report |

### Stream T: Templates (`content-publishing-templates`)

| Step | Work | Design |
|---|---|---|
| T1 | **Anchors, local mode.** `<!-- anchor: name -->` in `build_guide.py`, headings as anchors, block position within areas, deep links to `#anchor` and `#comment-…`, placement and orphan rules in local comments, `check_content.py` validation, anchors dropped from Word and reading copy, fixture, guidance on anchor naming. Release tag. | 5, 11 |
| T2 | **Live comments.** Storage interface with local and InteropHub backends, `comments` setting in `guide.json`, PKCE sign-in and token handling, "Sign in to comment" rail, first-comment notice, replies, back-off refreshing. Built and tested against a small stdlib mock server that follows the contract. Release tag. | 4.2–4.4, 6.1, 6.2, 6.4, 9.1, 9.2, 11 |
| T3 | **Author review.** State, disposition, and change note display, "Show closed", author controls (dispose, close, delete, re-attach), review flag highlight and rail list. Release tag. | 6.3, 7, 11 |
| T4 | **Setup and project template.** Registration step in `new_topic_reference.py` and setup guidance, template `AGENTS.md`, `review/README.md`, project change log, `import_guide_comments.py` migration option, `guidance/TopicReference.md`. Release tag. | 3.2, 9.4, 11 |

### Stream H: InteropHub comments (`InteropHub`)

| Step | Work | Design |
|---|---|---|
| H0 | **MCP experiment.** A throwaway remote MCP endpoint over streamable HTTP inside InteropHub with one read-only tool and bearer-token auth, reached from Claude Code. Decide the library or a hand-written implementation. Not merged. | 9.3 |
| H1 | **Sign-in foundation.** PKCE columns and `response_mode=fragment` on the login flow, `prompt=login`, `POST` and `DELETE /api/guide/token` with CORS, `app_user_token` scopes, bearer-token filter, account page token management, the `topic-reference` and `interophub-api` entries in `app_registry`, the dev test setup script. | 4 |
| H2 | **Registration and comments.** `es_guide_document`, `es_guide_document_url` with allowlist upkeep, the approved URL prefix setting and its admin page, `/guide/open`, registration page, `es_guide_comment` and audit, comments API for adding, replying, editing within 14 days, withdrawing, and listing with `since`. Visibility rules. | 6.1, 6.2, 6.4, 8, 9.1, 9.4, 10 |
| H3 | **Author review.** States, disposition lookup table, change notes, closing, deleting, re-attaching, review flags, comment management page, topic page panel. | 6.3, 7, 8, 10 |
| H4 | **MCP server.** Remote MCP endpoint with the comment, review flag, and guide registration tools, calling the same services as the API. MCP user guide. | 9.3, 9.4 |

### Stream E: Topic tools (`InteropHub`, own branch)

| Step | Work | Task section |
|---|---|---|
| E1 | `TopicEditService`, permission rule change, `es_topic_change`, Add topic page. Task steps 1, 2, 3, 5, and their tests. | 3, 8, 9, 10 |
| E2 | Topic HTTP API and MCP tools on the H4 server, `topicReferences` field, scopes applied, docs with both example sessions. Task steps 4, 6, 7, 8, 9. | 4–7, 6.1 |

E1 changes `canEditTopic`, which also decides who is an author of a Topic Reference. Open question 1 in the topic tools task must be answered before E1 merges.

### Stream P: Process and projects

| Step | Work | Design |
|---|---|---|
| P1 | Pilot project adds anchors after T1 is released. | 13 |
| P2 | `content-publishing-process`: review-round workflow, two records of feedback, anchor practice, agent instructions for the MCP server. | 12 |
| P3 | Pilot project moves to InteropHub mode: submodule update, registration, `guide.json`, optional import of past comments, publish. | 13 |
| P4 | Remaining projects, one at a time. | 13 |

## 5. Sequence and Gates

```
Gate 0  G0 contract + test plan, H0 MCP experiment
           |
           v
        T1 anchors | H1 sign-in | E1 topic edit service       (parallel)
           |
Gate 1  ---+--- P1 pilot anchors
           v
        T2 live comments | H2 registration + comments API     (parallel)
           |
Gate 2  ---+--- P3 pilot goes live
           v
        T3 author review | H3 author review | P2 process guidance
           |
Gate 3  ---+
           v
        H4 MCP server -> E2 topic tools | T4 setup and template
           |
Gate 4  ---+--- P4 remaining projects
```

| Gate | Passes when |
|---|---|
| **0** | The product owner approves the API contract. The MCP experiment works from Claude Code, and the library choice is recorded. |
| **1** | T1 is released, and the pilot builds with anchors in local mode, comments placed by anchor, and deep links working. H1: on local InteropHub, a page served from `http://localhost:<port>` (approved in dev only) completes the sign-in round trip, gets a token, and is refused from an origin that is not approved. E1: topic tools open question 1 is answered and existing edit, import, and admin pages behave as before. |
| **2** | On local InteropHub, two signed-in users comment and reply on the pilot guide and each sees the other's comments without reloading. Visibility follows the topic space. The edit window and withdraw work. Then the same on production with the pilot (P3). |
| **3** | A real review round on the pilot: authors dispose with change notes, flag areas for review, close and delete comments, re-attach an orphan after an anchor is renamed. The topic page panel links into the guide. |
| **4** | An agent works through a round using only the MCP server: lists comments, finds each in the content by anchor and snippet, edits, disposes, replies, flags, closes. An agent sets up a new Topic Reference repository in one session (topic tools task Section 6.1). |

## 6. Integration Testing Notes

- Serve the built guide from a local web server, not as a file, so the sign-in round trip and CORS have a real origin. Add that origin to the approved URL prefixes in the dev database only. Remember the dev database is rebuilt daily, so the test setup must be in `db/unapplied_updates.sql` or a repeatable test script.
- Use the dev sign-in skill to sign in as different test users without email.
- Test with two browsers or profiles for the two-user checks.
- T2's mock server is kept in the templates repository's tests, so later changes to `guide.js` can be checked without InteropHub.

## 7. Status

Kept current by stream G.

| Step | Status | Notes |
|---|---|---|
| G0 contract | Approved 2026-10-30 | Section 8 (MCP) completed from H0 the same day. | `topic-reference-comments-api-contract.md` |
| G0 test plan | Approved 2026-10-30 | `topic-reference-comments-test-plan.md` |
| H0 MCP experiment | Done 2026-10-30 | Branch `experiment/h0-mcp`, not merged. Hand-written `/mcp` works with Claude Code 2.1.314; the SDK also works but brings dependency conflicts. Code kept as reference for H1 (filter) and H4. |
| Gate 0 | Passed 2026-10-30 | Contract and test plan approved; MCP experiment works from Claude Code; library decision recorded. |
| T1 | Released `v0.5.0` 2026-10-30 | Commit `8fb4298` in content-publishing-templates. Accepted by G: `validate.ps1` passes, no internal names. Legacy: paragraph comments stay attached; list-item and code-block comments become orphans, none lost. |
| E1 | In progress | `prompts/E1-topic-edit-service.md`. Branch `feature/e1-topic-edit-service`. |
| P1 | Reviewed 2026-10-30, waiting | 27 anchors placed, checks pass, uncommitted. Waits for product owner review of the anchor list, the explicit heading ID fix, and `v0.5.1`. |
| T1.1 | Prompt ready | `prompts/T1.1-anchor-patch.md`: reading-copy blank lines, duplicate-ID guidance. Patch `v0.5.1`. |
| H1 | Prompt ready | `prompts/H1-sign-in-foundation.md`. Branch `feature/h1-sign-in`. |
| Gate 1 | | |
| T2 | Not started | |
| H2 | Not started | |
| Gate 2 | | |
| T3 | Not started | |
| H3 | Not started | |
| P2 | Not started | |
| Gate 3 | | |
| H4 | Not started | |
| E2 | Not started | |
| T4 | Not started | |
| Gate 4 | | |

## 7.1 Housekeeping

Loose ends kept here so nobody has to remember them. G clears each item when it is done.

| Item | When | Notes |
|---|---|---|
| Redeploy `hub.war` from `main` | Whenever Tomcat is next free | H0 replaced the normal WAR on local Tomcat. The product owner decides which branch runs on Tomcat at any time. |
| Claude Code MCP entry `interophub-dev` (local config for this repository) | At H4, or sooner if the connection errors are annoying | Points at H0's `/mcp` with a dev token. Fails to connect once the H0 WAR is gone. Remove with `claude mcp remove interophub-dev`; at H4, add it again with a real personal access token. |
| Branch `experiment/h0-mcp` and its worktree `InteropHub.worktrees/h0-mcp` | After H4 merges | Committed locally (`d65bced`), never pushed. Reference for H1 and H4. Delete the worktree and branch once H4 no longer needs it. Its report contains a dev-only token that dies with the next database rebuild. |

## 8. Decisions Log

Record decisions made during implementation here, with the date, so later prompts carry them.

| Date | Decision |
|---|---|
| 2026-10-30 | T1 starts before Gate 0. It is local mode only and does not depend on the API contract. |
| 2026-10-30 | Topic permissions are one rule. Global admins, admins of the topic's space, and active `CHAMPION` or `SUPPORT` subscribers of the topic may do everything to that topic that a space admin can, including Topic Reference authorship. `canEditTopic` becomes this rule for all its callers; no narrower `canAdministerTopic` is kept. Callers that check `canAdministerSpace` for a topic-level action should move to `canEditTopic` (E1 audits them). Space-level actions such as creating topics stay with space admins. Reason: this replaces a Confluence process where anyone could edit, with no problems; start wide. |
| 2026-10-30 | The pilot project is `contextual-conditions` (public, `C:\dev\writing\contextual-conditions`, InteropHub topic 7). |
| 2026-10-30 | Contract details fixed in T1: commentable blocks carry `data-anchor` and `data-block-index` (0-based within the area); a block's snippet is its text with whitespace collapsed, trimmed, first 120 characters. |
| 2026-10-30 | API contract and test plan approved. Choices confirmed: no automatic sign-in redirect when a guide token expires (the reader selects "Sign in to comment"; without an InteropHub session they get the email prompt, magic link, and registration if needed, then return to the guide); withdrawn comments show as withdrawn to everyone; deletes are soft (old deletes may be cleaned up later); review flags are visible to all viewers, one per area, a new one replaces the old; PAT lifetimes 30/90/365 days; localhost approved by a dev rule in code; any registered guide origin may call the API; threads returned with replies, and the poll also returns active flags. |
| 2026-10-30 | Added "Signed in as …", Sign out, and "Not you?" to the guide. "Not you?" re-runs sign-in with `prompt=login`, which ends the InteropHub session and asks for an email. Low priority; may slip from T2/H1 to T3. Security is deliberately light: the aim is knowing who people are, in an open process. |
| 2026-10-30 | Gate 0 passed. The MCP server is hand-written in InteropHub (H0). The official Java SDK 2.1.0 also ran, but adds 3.8 MB, forces reactor-core 3.7 beside the Azure client's reactor-netty 1.0, mismatches Jackson annotations, has no logging binding, skips the protocol-version check, and leaks stack traces in errors. Revisit only if MCP needs server-initiated requests or resumable streams. Contract Section 8 records the transport. |
| 2026-10-30 | Personal access tokens use their own `app_registry` row, `interophub-api`; guide tokens use `topic-reference`. The bearer filter does authentication only; each servlet and the MCP dispatcher check scopes. |
| 2026-10-30 | `/guide/open` and the approved URL prefix setting move from H1 to H2, since both need the guide registration tables. H1 is sign-in and tokens only. Test check 1.35 becomes 2.32. |
