# Topic Reference Comments in InteropHub

**Status:** Proposed design, not yet scheduled
**Scope:** InteropHub, `content-publishing-templates`, `content-publishing-process`, and the content projects that publish Topic References
**Primary purpose:** Let signed-in InteropHub users comment on a published Topic Reference, see each other's comments live, and let the topic's authors (and their agents) dispose of those comments and record what changed.

## 1. Decision Summary

A Topic Reference is published as a static web guide on GitHub Pages, built by `content-publishing-templates/tools/build_guide.py`. Today its comments live only in the reader's browser and reach the authors as downloaded JSON files.

This design moves the comments into InteropHub:

- Readers sign in through InteropHub from the guide using a browser-safe OAuth flow (authorization code with PKCE). There is no extra password and no second registration.
- Comments attach to **anchors**: named points the author places between blocks of text. A comment is made on a paragraph or list and records the anchor area it falls in. Comments whose anchor disappears become **orphaned** and are listed at the end of the document.
- Every signed-in user who can view the topic sees all comments on it. Readers who are not signed in see the document but no comments.
- Comments have a **state** (workflow) and, once reviewed, a **disposition** (HL7 ballot language) and a short **change note** saying what was done ("Typo fixed", "Language updated to indicate need for secure transactions"). No diffs are shown.
- One level of replies under each comment. The disposition belongs only to the top-level comment.
- Anyone who can edit the topic in InteropHub is an **author** of its Topic Reference. Authors dispose of comments, reply, close, re-attach orphans, and flag areas for review.
- Authors can flag anchor areas **for review** with a note, instead of the system detecting changes.
- An HTTP API and an MCP server expose the same operations so an agent working in the content project can read comments, add comments from other sources, reply, set dispositions, and close them.
- The current browser-only mode stays as an option for projects not connected to InteropHub.
- Feedback is kept in **two records** with different jobs (Section 3.2). InteropHub records each comment and who made it, so every piece of feedback is accounted for. The project repository records the author's summarized reasons for each change, never tied to individual commenters, and these feed a readable change log.

Out of scope for now: notifications, a formal review or ballot lifecycle, automatic closing rules, change detection or diff display.

## 2. Goals

1. A reader arriving from InteropHub or from a shared link can comment without re-entering their name, after at most one sign-in round trip.
2. Comments from everyone are visible in real time to the group, so review happens in the open.
3. Authors and agents can work through comments from the content project, see where each one applies, and record a disposition and change note.
4. The topic page in InteropHub shows recent comments and links straight to their place in the guide.
5. The approach works for any Topic Reference project without project-specific code in the shared tools.

## 3. Review Rounds and Records

### 3.1 Typical review round

1. Authors publish a version of the guide and invite comments for a week.
2. Participants sign in from the guide and comment on paragraphs and lists. They reply to each other.
3. In a meeting, the group walks through open comments. The facilitator or an author enters comments raised in the meeting under their own name, giving them context without attributing them to the person who raised them.
4. An author, often with an agent using the MCP server, revises the content. For each comment the author records a disposition and change note and replies where useful.
5. The author commits the revisions with messages that give the general reason for each change, and adds a summary of the round to the project's change log (Section 3.2).
6. Authors flag the areas they want the group to look at again, publish, and close the comments that are done.
7. The group reviews the flagged areas. The next round begins.

Most comments are expected to be resolved and closed between meetings, so filtering by meeting is not needed.

### 3.2 Two records of feedback

Feedback is kept in two places, and each has a different job.

**InteropHub is the accountability record.** It records every comment and who made it, so that:

- authors can follow up with the commenter if a comment raises questions;
- commenters can be told how their comment was resolved;
- everyone's feedback is accounted for, and none is lost.

Comments are useful for the week or so they are under discussion. On their own they are too detailed to serve as a long-term record.

**The project repository is the record of the text and why it changed.** The author:

- writes commit messages that justify each edit by its general reason, not by citing a particular person or comment;
- combines duplicate themes, since many comments make the same point, into one statement of the take-home message;
- adds readable entries to the project's change log that summarize what changed in each round and why.

How revision history then appears in the published document is not yet designed (Section 15.2, question 3).

The repository does not reproduce the conversation. It never identifies who raised a point. In a public repository, that also keeps commenters' names out of public history.

**The author is responsible for the summary.** It is the author's job to summarize the feedback accurately and to make sure the updates and change log capture it correctly. If someone believes their feedback was misrepresented or missed, that is settled in meetings and conversations, not by editing the source or its history.

The MCP server supports this by giving the author and their agent the full set of comments, dispositions, and change notes for a round, from which they draft commit messages and change log entries.

## 4. Authentication

### 4.1 Why the existing flow is not enough

The external login flow (`docs/external-application-login-flow.md`) assumes the app has a server: the code returned to `return_to` is exchanged server to server at `POST /api/auth/exchange`, and the result is identity, not a credential. The guide is a static page on another origin. It cannot read InteropHub's HttpOnly session cookie, and browsers block third-party cookies, so it cannot call InteropHub using that session.

### 4.2 Flow: authorization code with PKCE

All guides share one entry in `app_registry`, a public client with `app_code = topic-reference`, created once by a database update. It is not created per repository. The allowlisted return bases in `app_redirect_allowlist` are the published URLs of the registered guides, and InteropHub maintains them from guide registrations (Section 9.4).

1. **Start.** The guide generates a random `code_verifier`, keeps it in `sessionStorage`, also saves the reader's current location (including any `#comment-…` or `#anchor` target), and redirects the top-level window to:
   `GET {hub}/home?app_code=topic-reference&return_to=<guide URL>&state=<random>&code_challenge=<S256 hash>&code_challenge_method=S256&response_mode=fragment&requested_url=<guide URL>`
2. **Sign in.** InteropHub uses its normal behavior: an immediate redirect if the user has a session, otherwise email, magic link, and registration if needed, then a return to the original request.
3. **Return.** InteropHub redirects to `return_to#code=…&state=…`. The fragment keeps the code out of GitHub's server logs and out of `Referer` headers.
4. **Token.** The guide checks `state` and calls `POST {hub}/api/guide/token` with `code` and `code_verifier`. The endpoint allows CORS only from the app's allowlisted origins. InteropHub checks the verifier against the stored challenge, consumes the code, and returns a guide access token plus the user's display name.
5. **Clean up.** The guide removes the fragment with `history.replaceState` and restores the saved location.

The guide sends the token as `Authorization: Bearer …` on every comment API call. Bearer tokens avoid CSRF concerns.

### 4.3 Links from InteropHub

Links from InteropHub must not carry a token or code, because links get copied into email and chat. Instead, InteropHub's "Open and comment" link points to a small bounce endpoint, for example `GET {hub}/guide/open?doc=<document id>&anchor=<anchor>`. It redirects to the guide with a marker that tells the guide to start the flow above at once. A user who already has an InteropHub session goes through in well under a second.

A reader who arrives at the plain GitHub URL sees the full document and, in the right rail, "Sign in to comment". Selecting it starts the same flow.

### 4.4 Tokens

- Guide access tokens are stored as hashes in `app_user_token`, scoped to the `topic-reference` app. They expire after 30 days to match the InteropHub session, and are revocable from the user's account page.
- When a token expires, the guide repeats the round trip. It is silent while the InteropHub session lives.
- The guide keeps the token in `localStorage`. All `*.github.io` pages for one GitHub organization share an origin, so any Pages site in that organization could read it. The token is limited to the comment API to keep that exposure small. A custom domain for guides removes the concern and can be adopted later.
- MCP and API clients use **personal access tokens** that the user creates on their account page, stored in the same table with a label.

### 4.5 Token scopes

Every token carries scopes that limit what it may do. A token acts as its user, and a scope can only narrow what the user may already do; it never grants more.

| Scope | Allows |
|---|---|
| `comments` | The comment and review flag operations (Sections 9.1 and 9.3). |
| `topics:read` | Reading topics, Topic Spaces, and guide registrations. |
| `topics:write` | Registering and updating guides (Section 9.4), and the topic tools in `docs/tasks/topic-mcp-tools.md`. Implies `topics:read`. |

- Guide access tokens always have `comments` only.
- The user chooses scopes when creating a personal access token on the account page.
- Scopes are stored on `app_user_token` as a comma-separated `scopes` column and checked in the API's bearer-token filter, for the HTTP API and the MCP server alike.
- New features add scopes here, so this table stays the one list.

### 4.6 InteropHub changes for authentication

- Add `code_challenge` and `code_challenge_method` columns to `auth_login_code`, and support `response_mode=fragment`.
- Add `POST /api/guide/token` with CORS limited to `app_redirect_allowlist` origins for the app.
- Add the `/guide/open` bounce endpoint.
- Add the `scopes` column to `app_user_token` and enforce scopes in the bearer-token filter (Section 4.5).
- Add token management (list, revoke, create a personal token with chosen scopes) to the account page.
- Keep `/api/auth/exchange` unchanged for server-side apps.

## 5. Anchors

### 5.1 What an anchor is

An anchor is a named point in a Markdown content file, **between blocks**. It starts an area that runs to the next anchor or heading. Headings are anchors automatically, using their IDs. Authors decide how big areas are and may move or rename anchors as the text is reorganized. In practice an agent places and maintains anchors when it edits the content, following the project's agent instructions.

Content files should stay focused on the text, so they are tight and their diffs are readable. Anchors therefore:

- use a single short line on their own, so adding or moving one is a one-line diff;
- are placed sparingly, at natural review points, not before every paragraph;
- keep stable names, so text edits around them do not touch the anchor line.

Syntax:

```markdown
## Transport

<!-- anchor: secure-transport -->
Systems exchange messages over TLS 1.2 or later...

Each connection is authenticated with...

<!-- anchor: retry-behavior -->
When a message fails, the sender retries...
```

Here the `secure-transport` area covers the two transport paragraphs. The anchor lives in the source file in the repository. Being an HTML comment, it is hidden in GitHub's rendered view of the Markdown and ignored by the Word and reading-copy builds. `build_guide.py` turns it into a marked element in the web guide. Anchor names are lowercase letters, digits, and hyphens, and are unique within a document. `check_content.py` enforces this.

The anchor name is the key shared by the Markdown file and InteropHub. A comment stores the anchor, which paragraph in the area it was on, and the start of that paragraph's text. An agent asking the MCP server for comments gets the same three things back, searches the content files for `anchor: secure-transport` to find the one place it occurs, finds the paragraph by its snippet, edits the text, and records the disposition. Because the anchor moves with its text, comments follow when the author reorganizes.

### 5.2 Uses

- **Comment targets.** Each comment records its anchor and its position within the area, and a short text snippet of the paragraph or list it was made on.
- **Deep links.** `guide-url#secure-transport` and `guide-url#comment-123` both work. The topic page links to comments this way.
- **Review flags** (Section 7) apply to anchor areas.
- **Locating text for agents.** The MCP server returns the anchor name and snippet. An agent working in the content project finds the place by searching the content files for the anchor, then the snippet. InteropHub does not need to know file paths.

### 5.3 Placement rules

1. The anchor exists and a paragraph or list in its area matches the snippet: show the comment there.
2. The anchor exists but nothing matches: show the comment at the start of the area. The commenter is close to the text, which is enough.
3. The anchor no longer exists: the comment is **orphaned** and listed in an "Orphaned comments" group at the end of the document. Authors can re-attach it to another anchor in the page or through the MCP server.

Comments are made on a whole paragraph, list, quote, or table, not on arbitrary selections. This matches what the guide's comment buttons already do.

## 6. Comments

### 6.1 Who can do what

| Action | Who |
|---|---|
| Read the document | Anyone |
| See comments and replies | Signed-in users who can view the topic (`TopicSpaceAccessService.canViewTopic`) |
| Add a comment or reply | Same as above |
| Edit or withdraw own comment or reply | Its commenter, within 14 days of creating it |
| Set state, disposition, change note; close; re-attach | Authors (`TopicSpaceAccessService.canEditTopic`) |
| Delete any comment or reply (for inappropriate content) | Authors |
| Flag areas for review | Authors |

Authors cannot edit another person's comment or reply. They reply, dispose, close, or, for inappropriate content, delete. The 14-day edit window is a placeholder until a review lifecycle exists. Author rights follow `canEditTopic`, so they will change automatically when topic roles change (`docs/topic-roles-implementation-prompt.md`).

### 6.2 Visibility and attribution

- Comments on a topic in a public topic space are visible to every signed-in user. Comments on a topic in a private space are visible only to those who can view that topic.
- Linking a private topic to a publicly published guide, or a public topic to a private one, is a setup mistake and not a supported mode. InteropHub cannot prevent it, so the admin registration page should warn about it.
- Every comment is attributed to the user who entered it, by name and organization. The first time someone comments, a short notice says that other signed-in users will see the comment and their name.
- In meetings, the facilitator or an author enters comments from the group under their own name. This gives feedback without attributing it to the person who raised it, and lets the facilitator give it context. No special anonymous support is needed.
- Comments from other sources (email, GitHub issues, survey results) are entered the same way, through the page or MCP server, with an optional source note.

### 6.3 State and disposition

State and disposition are separate fields.

**State** (deliberately small):

| State | Meaning |
|---|---|
| `OPEN` | Entered, awaiting review. |
| `DISPOSED` | An author has set a disposition. Still visible in the guide. |
| `CLOSED` | Done. Hidden from the guide by default but retained, and shown through a "Show closed" option. |
| `WITHDRAWN` | The commenter withdrew it within the edit window. |

Authors close comments manually. Automatic closing can be added later.

**Disposition** (HL7 ballot language, provisional until we decide how this relates to balloting):

- Persuasive
- Persuasive with Modification
- Not Persuasive
- Not Persuasive with Modification
- Not Related
- Considered for Future Use
- Considered – Question Answered

Alongside the disposition, an author records a **change note**: a short, reader-facing statement of what changed, or why nothing did. For example "Typo fixed" or "Language updated to indicate need for secure transactions".

Comments are not tied to document versions. The normal cycle resolves comments within a week or so, so a comment is always read against the current text.

The disposition values are stored as a lookup table, not a database enum, so the list can change without a schema migration.

### 6.4 Replies

- One level only: a reply belongs to a top-level comment and cannot itself be replied to.
- Anyone who can comment can reply, including authors.
- Replies have no state or disposition. Closing a comment closes its thread.

## 7. Review Flags

Authors decide what the group should look at again. Nothing is detected automatically.

A review flag is a record in InteropHub on an anchor area, with a short note ("Please review: rewrote the consent requirements"), set from the guide by an author or through the MCP server. The guide highlights flagged areas and lists them in the rail as "Flagged for review". Authors clear flags when the group has reviewed them.

Flags are kept in InteropHub, not in the content files. This keeps the text clean, needs no rebuild, lets other InteropHub screens show them, and leaves room to integrate with HL7 processes.

The same division applies to comments generally (Section 3.2): InteropHub holds the conversation, and the repository holds the text with the author's summarized reasons for changing it.

## 8. Data Model (InteropHub)

Table names follow the `es_` convention for topic features. Columns are indicative. All schema changes go through `db/unapplied_updates.sql` per `docs/database-release-practice.md`.

**`es_guide_document`**: a published guide that accepts comments.
`guide_document_id`, `document_key` (the `id` from the project's `guide.json`, unique), `es_topic_id`, `title`, `repository_url`, `is_active`, `created_by_user_id`, timestamps.

This record is also the link between the topic and its Topic Reference: the topic page finds its guide here, so no separate link field on the topic is needed.

**`es_guide_document_url`**: `guide_document_url_id`, `guide_document_id`, `url`, `label` (for example "Release", "Preview"). A document may be published at more than one URL, such as a release build and a `/main/` preview. All of them share one comment set. InteropHub adds and removes the matching `app_redirect_allowlist` rows for the `topic-reference` app when these rows change.

**`es_guide_comment`**: top-level comments and replies.
`guide_comment_id`, `guide_document_id`, `parent_comment_id` (null for top-level), `anchor_id`, `block_index` (position within the area), `block_snippet` (start of the paragraph's text), `comment_text`, `state`, `disposition_code`, `change_note`, `user_id` (who entered it), `source_note` (optional, for example "Meeting 2026-11-12", "Email", "GitHub issue #14"), `created_at`, `edited_at`, `disposed_at`, `disposed_by_user_id`, `closed_at`, `closed_by_user_id`.

**`es_guide_disposition`**: lookup of disposition codes, labels, and display order.

**`es_guide_review_flag`**: `review_flag_id`, `guide_document_id`, `anchor_id`, `note`, `created_by_user_id`, `created_at`, `cleared_at`, `cleared_by_user_id`.

**`es_guide_comment_audit`**: who changed state, disposition, text, or anchor, and when.

Authentication reuses `auth_login_code` (with the PKCE columns) and `app_user_token`.

## 9. API and MCP

### 9.1 HTTP API

All endpoints take a bearer token: a guide token or a personal access token. Authorization uses the rules in Section 6.1.

| Method and path | Purpose |
|---|---|
| `GET /api/guide/{documentKey}` | Document info and the caller's permissions (`canComment`, `isAuthor`). |
| `GET /api/guide/{documentKey}/comments?state=&anchor=&since=` | Comments with replies. Default excludes `CLOSED` and `WITHDRAWN`. |
| `POST /api/guide/{documentKey}/comments` | Add a comment or, with `parentCommentId`, a reply, with an optional source note. |
| `PATCH /api/guide/comments/{id}` | Edit text (commenter, within window) or set state, disposition, change note, or anchor (authors). |
| `DELETE /api/guide/comments/{id}` | Authors delete a comment or reply. |
| `POST /api/guide/comments/{id}/withdraw` | Commenter withdraws. |
| `GET/POST/DELETE /api/guide/{documentKey}/review-flags` | List, add, and clear review flags. |

CORS on these endpoints allows only the registered guide origins.

### 9.2 Refreshing the guide

The guide checks for new and changed comments with `GET …/comments?since=`, backing off when nothing happens, so a page left open costs little:

1. After the page loads, after the reader posts a comment or reply, or after a check finds something new: check again in 30 seconds.
2. Each check that finds nothing lengthens the wait: 30 seconds, 1 minute, 10 minutes, then 30 minutes, which is the maximum.
3. Posting or receiving something resets the wait to 30 seconds.
4. While the tab is hidden, checks stop. When it becomes visible again, the guide checks at once and continues from 30 seconds.

Meetings need nothing special. The author shares their screen and works in the document, so others do not need to follow the author's position, and the author sees their own comments immediately after posting.

### 9.3 MCP server

The MCP server is hosted by InteropHub as a remote endpoint over streamable HTTP, authenticated by a personal access token, so authors do not install anything. Each tool calls the same service layer as the HTTP API.

| Tool | Purpose |
|---|---|
| `list_documents` | Guides the caller can see, with topic and open comment counts. |
| `list_comments(document, state?, anchor?, since?)` | Comments with replies, anchor, snippet, commenter, source note. |
| `get_comment(id)` | One thread. |
| `add_comment(document, anchor, text, snippet?, source_note?)` | Add a comment, including one from another source such as email or a GitHub issue. It is attributed to the caller. |
| `reply(id, text)` | Add a reply. |
| `set_disposition(id, disposition, change_note)` | Authors. Moves the comment to `DISPOSED`. |
| `delete_comment(id)` | Authors. For inappropriate content. |
| `close(ids)` | Authors. |
| `reattach(id, anchor)` | Authors. Fix an orphaned comment. |
| `list_review_flags(document)`, `flag_for_review(document, anchor, note)`, `clear_review_flag(id)` | Authors. |

The tool descriptions should tell the agent to find each comment's location by searching the content files for `anchor: <name>` (or a heading ID) and then the snippet, and to report comments it cannot place rather than guessing.

### 9.4 Registering a guide

Registration is part of the API and MCP server, so an agent that sets up a new Topic Reference repository can register it in InteropHub in the same session.

| Tool | HTTP | Purpose |
|---|---|---|
| `get_guide(document)` | `GET /api/guide/{documentKey}` | Registration details: topic, title, repository, URLs. |
| `register_guide(topic, document_key, title, repository_url, urls, dry_run?)` | `POST /api/guides` | Create the registration for a topic, with its published URLs. |
| `update_guide(document, title?, repository_url?, urls?, is_active?, dry_run?)` | `PATCH /api/guide/{documentKey}` | Change details, or add or remove URLs. |

Rules:

- The caller must be able to edit the topic (`canEditTopic`), and the token must have the `topics:write` scope (Section 4.5).
- `topic` is the topic code. The response includes the topic's numeric ID and URL, which the agent writes into the project's `guide.json`.
- **Published URLs are limited to approved hosts.** Each guide URL becomes a place InteropHub will send sign-in codes. A hub setting lists the allowed URL prefixes (for example `https://immregistries.github.io/`). A URL outside them is refused unless the caller is a global admin. Without this rule, any topic editor could allowlist a site of their choosing.
- `app_registry` is not exposed. The `topic-reference` app and the approved hosts are managed by global admins.
- Changes are recorded in the same change record as topic edits (`es_topic_change` in the topic tools task) once it exists.

## 10. InteropHub User Interface

- **Topic page:** a "Topic Reference comments" panel beside the meeting notes, showing recent open comments and review flags with links into the guide (through `/guide/open`), and a link to the full list.
- **Comment management page** for authors: all comments for a document, filterable by state, with disposition editing. This mirrors what authors can do in the guide, for those who prefer to work in InteropHub.
- **Guide registration page**, reachable from the topic's manage page, for topic editors who prefer not to use the API: the same fields and rules as Section 9.4.
- **Admin:** the list of approved URL prefixes for guides, and all registrations across topics.
- **Account page:** guide sessions and personal access tokens, with revoke and create.

## 11. Changes in content-publishing-templates

This is the public repository. Nothing here may name projects or private URLs. All values come from `guide.json`.

- **`guide.json`:** add an optional `comments` setting:
  ```json
  "comments": {
    "mode": "interophub",
    "hub": "https://<interophub host>/hub",
    "document": "contextual-conditions"
  }
  ```
  `mode` is `interophub` or `local`. Without the setting, the guide keeps today's browser-only comments.
- **`build_guide.py`:** convert `<!-- anchor: name -->` lines to anchor elements, give each commentable block its anchor and position, and pass the `comments` settings to the page. Headings act as anchors.
- **`guide.js`:**
  - Put the comment storage behind one interface with two backends: local (today's code) and InteropHub (API calls).
  - Sign-in UI and PKCE flow, the first-comment notice, refreshing with back-off (Section 9.2).
  - Replies, state and disposition display, change notes, the "Show closed" option.
  - The orphaned comments group, the review flag highlight and rail list, and author controls (dispose, close, re-attach, flag).
- **`guide.css`:** styles for states, dispositions, replies, review flags, sign-in prompt.
- **`check_content.py`:** validate anchor syntax and uniqueness.
- **`build_core_markdown.py` and the Word build:** confirm anchors are dropped. Regenerate the fixture reading copy.
- **`import_guide_comments.py`:** keep for local mode. Add an option to write its output as `add_comment` calls, or a JSON file the MCP can load, for migrating existing comments.
- **`new_topic_reference.py` and setup guidance:** after creating the repository, register it with `register_guide` through the MCP server and write the returned topic ID and the `comments` setting into `guide.json`. When no MCP server is available, the guidance points to the registration page instead.
- **Topic Reference template project:**
  - Add example anchors to the starter content and the `comments` setting (commented) to `guide.json`.
  - Update `review/README.md`: comments live in InteropHub, not in the repository, and the repository keeps only the author's summary of them.
  - Add a project change log (for example `CHANGELOG.md`) with a short entry format: the round or version, the themes of feedback addressed, and what changed, without commenter names.
  - Update `AGENTS.md`: use the MCP server, place and keep anchors, record dispositions with change notes, write commit messages and change log entries that state general reasons and combine duplicate themes, and never name commenters in the repository.
- **Guidance:** update `guidance/TopicReference.md` (how commenting works in each mode, and the two records from Section 3.2) and `guidance/Conventions.md` (anchor naming, change log entries).
- **Fixture and validation:** add anchors and a `comments` setting to `tests/fixture-project/`, and extend `validate.ps1`.
- **Release:** CHANGELOG entry and a new release tag. Projects adopt it deliberately.

## 12. Changes in content-publishing-process

This is AIRA's internal counterpart. It should change to match, in its own words:

- The review-round workflow from Section 3, including adding meeting comments and closing comments between meetings.
- Agent instructions for working through comments with the MCP server, writing change notes for the community, and setting review flags.
- Guidance on placing anchors: where to put them, keeping names stable, and checking for orphaned comments after reorganizing.
- The two records from Section 3.2: InteropHub accounts for every comment and its commenter; the repository carries the author's summarized reasons and change log, without commenter names. The author owns the accuracy of that summary, and disputes are resolved in meetings.

## 13. Changes in Content Projects

For each Topic Reference project:

1. Move the `process/` submodule to the release that includes this feature.
2. Add anchors to the content at useful review points.
3. Register the guide in InteropHub with `register_guide` (or the registration page), which also allows its published URLs for sign-in.
4. Add the `comments` setting and the returned topic ID to `guide.json`.
5. Optionally import existing downloaded comments.
6. Rebuild, publish, and check sign-in and commenting on the published page.

## 14. Phases

1. **Anchors.** Add anchor support to the templates (build, validation, deep links) while still in local mode. This is useful alone and lets projects add anchors early.
2. **Sign-in and live comments.** Add PKCE sign-in, the guide document registration, the comment API, and the InteropHub backend in `guide.js`, with comments and replies, visibility rules, the notice, and the 14-day edit window.
3. **Author review.** Add state, disposition, change note, closing, re-attaching orphans, review flags, and the author controls in the guide and InteropHub.
4. **MCP and topic page.** Add personal access tokens with scopes, the remote MCP server, guide registration through the API and MCP, and the topic page panel with deep links.
5. **Later.** Notifications, the review lifecycle and its relation to HL7 balloting, automatic closing, and a custom domain for guides.

## 15. Decisions and Open Questions

### 15.1 Decided

| # | Question | Decision |
|---|---|---|
| 1 | Anchor syntax | HTML comment `<!-- anchor: name -->` on its own line, placed sparingly and maintained by agents so content files stay tight and diffs readable. |
| 2 | Where review flags live | InteropHub, so other screens can show them and they can integrate with HL7 processes. The repository shows only the clean result. |
| 3 | Disposition list | Provisional HL7 terms in a lookup table. |
| 4 | Refreshing | Back-off polling (Section 9.2). No position following in meetings. |
| 5 | Editing others' comments | Not allowed. Authors may delete, for inappropriate content. |
| 6 | Several published URLs | One comment set per document. Comments are not tied to versions. |
| 7 | Private topic with a public guide, or the reverse | A setup mistake, not a supported mode. |
| 8 | Anonymous comments | No special support. Facilitators enter meeting feedback under their own name. |
| 9 | Record of review-driven changes | Two records (Section 3.2). InteropHub keeps each comment and its commenter for accountability. The repository keeps the author's summarized reasons in commit messages and a readable change log, without naming commenters. |

### 15.2 Open

| # | Question | Notes |
|---|---|---|
| 1 | How do the statuses and dispositions integrate with HL7 balloting? | To be decided with the review lifecycle. |
| 2 | What is the review lifecycle that replaces the 14-day edit window and manual closing? | Later, with notifications. |
| 3 | How is revision history included in the published document? | Not yet designed. It should be generated with the document, not written by hand. Possibly two tiers: a detailed change log in the project that is not published, and a summary of it that appears in the published guide. Settle this before fixing the change log format in Section 11. |
