# Topic Tools for the InteropHub API and MCP Server

**Status:** Proposed task. Do this after the API and MCP server from `docs/topic-reference-comments-design.md` (Section 9) is in place.
**Primary purpose:** Let a user, through an agent, read the topics in a Topic Space, update a topic's basic information, and add new topics to a Topic Space they have rights in, without going through the admin pages or the JSON Lines import.

## 1. Decision Summary

- Reuse what the comments work builds: the remote MCP endpoint over streamable HTTP, personal access tokens and their scopes (Section 4.5 of the comments design), and the rule that every MCP tool calls the same service layer as an HTTP endpoint. This task adds no new sign-in mechanism and no new scopes.
- Add six tools: `list_topic_spaces`, `get_topic_space`, `list_topics`, `get_topic`, `update_topic`, and `create_topic`.
- Topics are addressed by `topicCode`, which is already globally unique (`es_topic.topic_code`) and is what people and the import file use. Topic Spaces are addressed by `spaceCode`.
- Updates are partial, with the same rules as the import (`docs/es-topic-import-format.md`): a field left out is unchanged, a field sent as `null` is cleared.
- Updates carry the `updatedAt` value the agent last read. If the topic has changed since, the update is refused, so an agent never overwrites an edit someone made on the web page in the meantime.
- Validation for creating and editing a topic moves out of the three places it lives today into one `TopicEditService`, which the web pages, the import, and the API all call.
- Topic Space admins get an "Add topic" page in InteropHub, built on the same service, so creating a topic is not API-only.
- Every change made through the API or MCP is recorded in a new `es_topic_change` table with who, when, the source, and each field's old and new value.
- Together with guide registration from the comments design (Section 9.4 there), these tools let an agent set up a new Topic Reference in one session: find or create the topic, then register the guide against it (Section 6.1).

Out of scope for now: deleting topics, changing a topic's code, moving a topic to another Topic Space, topic meetings, campaign assignments, priorities, relationships, curation, followers, and resources.

## 2. What Exists Today

### 2.1 Where topics are created and edited

| Path | Who | Notes |
|---|---|---|
| `AdminEsTopicServlet` (`/admin/es/topics?mode=new`) | Global admins only | The only page that creates a single topic. Requires `topicCode`, checks it is unused, requires an active Topic Space. |
| `EsTopicEditServlet` (`/es/topic/{id}/edit`) | Global admins, and users with an active `CHAMPION` or `SUPPORT` subscription on the topic | Full-form replace. Also moves topics between spaces and turns the topic meeting on or off. Sets the legacy `neighborhood` column to `null`. |
| `EsTopicImportService` (`/admin/es-topic-import`) | Global admins only | JSON Lines upsert within one Topic Space. Partial updates. Resolves stage, path, and neighborhood **by name**. Fills the legacy `neighborhood` column with the joined names. |

All three repeat the same checks: required name, stage and path must belong to the topic's space, neighborhoods must be active and in the space, Confluence URL must be valid, summary at most 300 characters, emoji at most 64.

### 2.2 Permission rules disagree

`TopicSpaceAccessService` already has the rules this task needs:

- `canViewSpaceId`: public spaces, spaces the user is a member of, or any space for a global admin.
- `canAdministerSpace`: `ADMIN` role in `es_topic_space_member`, or global admin.
- `canEditTopic`: `canAdministerSpace` on the topic's space. Used by the topic page, resources, and communication bundles.

But the topic edit page uses its own rule (champion or support subscriber, or global admin) and does **not** let a Topic Space admin edit a topic unless they also champion it. No page lets a Topic Space admin create a topic.

## 3. Permissions

This task settles one rule, kept in `TopicSpaceAccessService` and used by the web pages as well as the API:

| Action | Allowed for |
|---|---|
| See a space and its topics | `canViewSpaceId` (unchanged) |
| Edit a topic's basic information | Global admin, an `ADMIN` of the topic's space, or an active `CHAMPION`/`SUPPORT` subscriber of the topic |
| Create a topic in a space | Global admin, or an `ADMIN` of that space. The space must be active. |

`canEditTopic` changes to the combined rule in the second row, and `EsTopicEditServlet` switches to it, so space admins gain edit on the web page too. Check the other callers of `canEditTopic` (resources, communication bundles, topic page) and decide whether they should keep the narrower space-admin rule under a new name, for example `canAdministerTopic`.

Creating topics widens from global admins to space admins, both through the API and through a new "Add topic" page (Section 8).

## 4. Token Scopes

Token scopes are defined in `docs/topic-reference-comments-design.md`, Section 4.5. This task uses two of them:

- `topics:read` for `list_topic_spaces`, `get_topic_space`, `list_topics`, and `get_topic`;
- `topics:write` for `update_topic` and `create_topic`.

If the task needs a scope that is not listed there, add it to that table rather than defining it here.

## 5. Topic Fields Exposed

"Basic information" here means the fields on the topic edit page other than the meeting and Topic Space, and other than priorities.

| Field | Read | Create | Update | Notes |
|---|---|---|---|---|
| `topicCode` | yes | required | no | Unique across all spaces. Letters, digits, and hyphens, upper case, at most 80 characters. |
| `spaceCode` | yes | required (tool argument) | no | |
| `topicName` | yes | required | yes | Cannot be cleared. |
| `topicSummary` | yes | yes | yes | At most 300 characters. |
| `description` | yes | yes | yes | |
| `searchKeywords` | yes | yes | yes | |
| `topicEmoji` | yes | yes | yes | At most 64 characters. |
| `neighborhoods` | yes | yes | yes | List of names. Replaces the whole set. Must be active neighborhoods in the space. |
| `stage` | yes | yes | yes | Name of a stage defined for the space. |
| `path` | yes | yes | yes | Name of an advancement path defined for the space. |
| `status` | yes | yes (default `ACTIVE`) | yes | `ACTIVE`, `ARCHIVED`, `RETIRED`. Cannot be cleared. |
| `policyStatus` | yes | yes | yes | Free text. |
| `topicType` | yes | yes | yes | Free text. |
| `confluenceUrl` | yes | yes | yes | Must be a valid http(s) URL. |
| `url` | yes | | | Link to the topic page in InteropHub. |
| `topicReferences` | yes | | | Guides registered for the topic: document key, title, repository, published URLs. Changed with the guide tools, not here. |
| `updatedAt` | yes | | | Used for the concurrency check. |
| `canEdit` | yes | | | Whether the caller may update this topic. |

Names, not IDs, are used for stage, path, and neighborhoods, as in the import. They match ignoring case and surrounding spaces. New topics get priorities of `0`, as on the admin page and in the import.

The legacy `es_topic.neighborhood` text column is handled the way the import handles it (filled with the joined names) for now. Whether the edit page's practice of clearing it is right should be settled in `TopicEditService` so all paths agree.

## 6. MCP Tools

| Tool | Scope | Purpose |
|---|---|---|
| `list_topic_spaces()` | `topics:read` | Spaces the caller can see, with `spaceCode`, name, visibility, whether active, topic count, and the caller's `canCreateTopics`. |
| `get_topic_space(space)` | `topics:read` | The space's description, stage and path names with their descriptions (and the space's stage and path concept text), active neighborhood names, and the `topicType` and `policyStatus` values already in use there. Gives the agent the vocabulary before it creates or edits. |
| `list_topics(space, status?, query?, updated_since?)` | `topics:read` | Topic rows: code, name, summary, emoji, stage, path, neighborhoods, status, `updatedAt`, `url`. Default status `ACTIVE`. `query` uses the existing name, summary, and keyword search. |
| `get_topic(topic)` | `topics:read` | One topic with every field in Section 5. |
| `update_topic(topic, expected_updated_at, fields, dry_run?)` | `topics:write` | Partial update. Returns the topic as saved and a list of fields that changed. Refuses with a conflict error, including the current values, if `expected_updated_at` does not match. |
| `create_topic(space, topic_code, topic_name, fields?, dry_run?)` | `topics:write` | Creates the topic, recorded as created by the caller. Returns it. |

`dry_run` runs every check and returns what would be saved, and for `update_topic` the field-by-field differences, without saving. This lets the agent show the user the change before making it.

### 6.1 Setting up a new Topic Reference

The guide tools (`get_guide`, `register_guide`, `update_guide`) are defined in `docs/topic-reference-comments-design.md`, Section 9.4, and need `topics:write`. When a user asks an agent to set up a new Topic Reference repository, the agent:

1. Finds the topic with `list_topics(query=…)`, or proposes one with `create_topic` (dry run first) if it does not exist.
2. Optionally updates the topic's summary or description with `update_topic`.
3. Creates the repository from the template (`content-publishing-templates`).
4. Registers the guide with `register_guide`, dry run first, using the topic code, the repository URL, and the published URLs.
5. Writes the returned topic ID and the `comments` setting into the repository's `guide.json`.

The `topic-reference` entry in `app_registry` already exists, and the allowed sign-in URLs follow from the registration, so the agent never touches `app_registry` directly.

Tool descriptions should tell the agent to:

- Call `get_topic_space` before creating or editing, and use only stage, path, and neighborhood names it returns.
- Search with `list_topics(query=…)` before creating, and report likely duplicates to the user instead of creating another topic.
- Propose a `topicCode` in the style of the space's existing codes and confirm it with the user.
- Show the user the dry run and get agreement before creating a topic or changing a topic's name, status, or stage.
- Re-read the topic and try again only after showing the user what changed when an update is refused for a conflict.

## 7. HTTP API

The same operations, with the same bearer tokens and scopes:

| Method and path | Purpose |
|---|---|
| `GET /api/topic-spaces` | `list_topic_spaces` |
| `GET /api/topic-spaces/{spaceCode}` | `get_topic_space` |
| `GET /api/topic-spaces/{spaceCode}/topics?status=&q=&updatedSince=` | `list_topics` |
| `POST /api/topic-spaces/{spaceCode}/topics?dryRun=` | `create_topic` |
| `GET /api/topics/{topicCode}` | `get_topic` |
| `PATCH /api/topics/{topicCode}?dryRun=` | `update_topic`. The expected `updatedAt` goes in an `If-Unmodified-Since`-style header or an `expectedUpdatedAt` body field; a mismatch returns `409`. |

Errors use one JSON shape with a field name where one applies, for example `{"error": "invalid_field", "field": "stage", "message": "No stage named 'Monitor' in Topic Space IVC."}`. No CORS: these endpoints are for agents and scripts, not browser pages.

## 8. Add Topic Page for Space Admins

- The topics page for a space (`/es/topics?space={spaceCode}`) shows an "Add topic" button when the viewer can create topics in that space (Section 3) and the space is active.
- The button opens `/es/topics/new?space={spaceCode}`, a form with the create fields from Section 5: code, name, summary, description, search keywords, emoji, neighborhoods, stage, path, status, policy status, topic type, and Confluence URL. The space is fixed by the link and not editable on the form. Stage, path, and neighborhood choices are limited to the space's own.
- Saving calls `TopicEditService.create` with source `WEB` and goes to the new topic's page. Errors redisplay the form with the entered values, like the current edit page.
- No meeting settings and no priorities on this form. A topic meeting can be turned on afterwards from the topic's edit page, and priorities start at `0`.
- The global admin page (`/admin/es/topics?mode=new`) stays for admins, who can pick any space, and also switches to `TopicEditService`.
- Server-side checks repeat the permission rule on both GET and POST; hiding the button is not the protection.

## 9. Change Record

There is no history of topic edits today. Agent edits make one more important, and it matches how other AI-assisted work in InteropHub keeps a permanent record.

Add `es_topic_change`:

| Column | Notes |
|---|---|
| `es_topic_change_id` | |
| `es_topic_id` | |
| `changed_by_user_id` | |
| `changed_at` | |
| `source` | `WEB`, `IMPORT`, `API` (MCP calls record `API`) |
| `change_type` | `CREATE` or `UPDATE` |
| `changes_json` | `{"field": {"old": …, "new": …}}` for each changed field |
| `app_user_token_id` | The token used, for `API` changes; otherwise null |

`TopicEditService` writes it for every save, so the edit page and import are covered too. A "History" view on the topic manage page can follow later. Schema changes go in `db/unapplied_updates.sql` following `docs/database-release-practice.md`.

Edits through the API update `es_topic.updated_at`, so they are picked up by the next topic relationship review round like any other change.

## 10. Implementation Steps

1. **`TopicEditService`.** Move validation and saving out of `EsTopicEditServlet`, `AdminEsTopicServlet`, and `EsTopicImportService` into one service with `create(user, space, fields, source)`, `update(user, topic, expectedUpdatedAt, fields, source)`, and a dry-run form of each. It resolves stage, path, and neighborhood names, writes the topic and its `es_topic_neighborhood` rows in one transaction, and writes `es_topic_change`. Switch the three existing callers to it with no change in what users see, except as in step 2.
2. **Permissions.** Update `TopicSpaceAccessService` per Section 3 and switch `EsTopicEditServlet` to it.
3. **Database.** Add `es_topic_change` in `db/unapplied_updates.sql`.
4. **Token scopes.** Apply the existing `topics:read` and `topics:write` scopes (Section 4) to the new endpoints and tools. The scope column, account page choices, and filter already exist from the comments work.
5. **Add topic page.** Add the button and `/es/topics/new` page from Section 8.
6. **HTTP API.** Add the endpoints in Section 7.
7. **MCP tools.** Register the tools in Section 6 on the existing MCP server, each calling the same service methods as the HTTP endpoints, with the descriptions from Section 6.
8. **Tests.** Permission cases (public-space member who is not an admin can read but not write; space admin can create and edit in their space only, through the API and the Add topic page; the Add topic button shows only for those who can create; champion can edit their topic but not create; token without `topics:write` is refused), the conflict check, dry run saving nothing, name resolution errors, duplicate `topicCode` across spaces, and the change record contents.
9. **Docs.** A short "Topic tools" section in the MCP user guide that the comments work produces, with two example sessions: list spaces, read a space, search, dry-run a new topic, and create it; and set up a new Topic Reference as in Section 6.1.

## 11. Decisions

Settled with Nathan on 2026-10-30:

1. Champions and support subscribers keep edit rights to their topics.
2. Space admins get an "Add topic" page in this task (Section 8).
3. Priorities (`priorityIis`, `priorityEhr`, `priorityCdc`) are left out of the API and the new page for now. New topics get `0`.

## 12. Open Questions

1. Should the other callers of `canEditTopic` (resources, communication bundles) get the wider rule, or keep the space-admin rule under a new name?
2. Should `create_topic` and the Add topic page accept `topicCode` as given, or should InteropHub propose one from the name and space?
