# E1: Topic Edit Service and Permissions (InteropHub, own branch)

You are working in the InteropHub repository. Other agents are working in this repository at the same time, so work only in your own worktree and branch.

## Setup

Create a worktree under `C:\dev\immregistries\InteropHub.worktrees\` on a branch named `feature/e1-topic-edit-service`, from the current `main`. Do not touch the main working copy. Do not commit to `main`, push, or merge. Local commits on your branch are fine.

Local Tomcat and the local MySQL database are shared with other agents. Ask the product owner before you deploy to local Tomcat. Additive schema changes to the local database (a new table) are fine.

## Read first

- `CLAUDE.md`, and `docs/database-release-practice.md` before touching `db/`.
- `docs/aira-web/README.md` before adding any CSS.
- `docs/tasks/topic-mcp-tools.md`, all of it. You implement Sections 3, 5 (the fields, for the service), 8, 9, and implementation steps 1, 2, 3, 5, and the step 8 tests that apply to them, including decision 4 in Section 11.
- `docs/tasks/topic-reference-comments-workstreams.md`, Section 8 (Decisions Log), the 2026-10-30 permission decision.

Follow existing patterns: DAO per table, services hold rules, servlets render. Tests in `src/test/java`. No new frameworks.

## The permission rule (decided 2026-10-30)

There is one topic rule. Global admins, `ADMIN`s of the topic's space, and active `CHAMPION` or `SUPPORT` subscribers of the topic may do everything to that topic that a space admin can. The product owner's reason: this replaces a Confluence process where anyone could edit, and it is meant to start wide.

- `TopicSpaceAccessService.canEditTopic` becomes this rule. Do not keep a narrower variant.
- `EsTopicEditServlet` uses `canEditTopic` instead of its own champion/support check.
- Creating topics is a space-level right: global admin or space `ADMIN`, in an active space. Add a method for it (for example `canCreateTopicInSpace`).
- **Audit every topic-level permission check.** These call `canAdministerSpace` with a topic's space for what looks like a topic-level action. Decide for each whether it is topic-level (switch to `canEditTopic`) or genuinely space-level (leave it):
  - `MeetingAuthorizationService` (lines near 37, 82, 98)
  - `TopicFollowerManagementService` (near 255)
  - `EsTopicManageServlet` (near 126)
  - `CommunicationBundleService` (near 683, the `STEWARDS` case)
  - and any other place you find.
  Also list the existing `canEditTopic` callers (`CommunicationBundleService`, `EsTopicResourceServlet`, `EsTopicDetailServlet`), which widen automatically. If a case is unclear, do not guess: list it in your report for the product owner.

## Task

1. **`TopicEditService`** per topic tools step 1: `create(user, space, fields, source)`, `update(user, topic, expectedUpdatedAt, fields, source)`, and a dry-run form of each. Partial update semantics (absent unchanged, `null` clears where allowed). Name resolution for stage, path, and neighborhoods, ignoring case and surrounding spaces. One transaction for the topic and its `es_topic_neighborhood` rows. Validation errors carry a field name. Settle the legacy `es_topic.neighborhood` column so all paths agree, and say what you chose.
2. Switch `EsTopicEditServlet`, `AdminEsTopicServlet`, and `EsTopicImportService` to the service, with **no visible change** apart from the permission rule. Same validation messages where possible; note any that change.
3. **`es_topic_change`** per topic tools Section 9, in `db/unapplied_updates.sql`. Put your SQL in one clearly commented block at the **end** of the file, so it merges cleanly with other streams. The `app_user_token_id` column stays null for now (no API yet).
4. **Add topic page** per topic tools Section 8.
5. **Tests:** permission cases from step 8 that apply without the API (space admin creates and edits in their space only; champion edits their topic but cannot create; public-space member who is not an admin cannot edit; the Add topic button shows only for those who may create), dry run saves nothing, name resolution errors, duplicate `topicCode` across spaces, and the change record contents.

## Out of scope

- The HTTP API, MCP tools, token scopes, `topicReferences` (E2).
- Anything in `docs/topic-reference-comments-design.md` beyond the permission rule.
- Priorities. Topic code generation (topic tools open question 2 stays open; accept the code as given).

## Done when (Gate 1, checks 1.40 to 1.44 in `docs/tasks/topic-reference-comments-test-plan.md`)

- `mvn test` passes.
- Edit page, admin create, and import behave as before on a few existing topics, other than the permission change.
- A champion of a topic can edit it; a reader cannot; a space admin can add a topic from the space's topics page.
- `es_topic_change` rows are written for web, import, and admin saves.

## Report back

Files changed; the permission audit as a table (location, old check, new check, reason); any unclear cases for the product owner; any validation message that changed; your choice on the legacy `neighborhood` column; the SQL added; test results. Do not push or merge.
