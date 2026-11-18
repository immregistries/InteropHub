# Topic Reference: Version Display and Comment Modes

**Status:** Plan, decisions approved 2026-11-18 by the product owner (Section 2). Not started.
**Repositories:** `InteropHub` (this plan, for the next production release). The guide side (`content-publishing-templates` v0.22.0) adapts afterwards to the API contract in Section 5.
**Related:** `topic-reference-summary-annotations-and-guide-status.md` (v0.21.0/v0.22.0 plan), `topic-reference-comments-api-contract.md`, `topic-reference-private-hosting.md`.

## 1. Problem

A guide's versions are labels: `latest`, `main`, and version tags such as `v0.2.0`. Hosted guides store an upload per label, and external guides register a URL per label. Today, visibility is all or nothing:

- The topic page's Topic Reference section is shown only to topic editors ("Editors only", `TopicReferenceSectionData.visibleTo`).
- A hosted guide opens for anyone who can view the topic and has the link.
- Comments and review flags belong to the guide as a whole, so draft comments appear on a released version, where their context may not fit.

Authors want to choose which versions readers are pointed to, keep work-in-progress versions behind a curtain, retire old versions, and keep a released version's comments limited to what belongs to it, without splitting the conversation across versions.

This is a **privacy curtain, not a security wall.** Public guides remain public on GitHub. The goal is to focus readers.

## 2. Decisions

| # | Decision |
|---|---|
| V1 | Each label of a guide has a **display state**: Current, Listed, Unlisted, Authors only, or Retired (Section 3.1). |
| V2 | Making a label Current is always deliberate. Nothing is promoted automatically, including new version tags. New labels start as **Unlisted**, which is today's behavior. |
| V3 | At most one label per guide is Current. Making a label Current turns the previous Current label into Listed. |
| V4 | Retired labels are not visible to readers anywhere. |
| V5 | Authors only does **not** restrict comments on external guides beyond what its comment mode does (V7, V8). It hides the label from readers on the topic page, and for hosted guides it refuses non-editors. |
| V6 | Comments stay one conversation per guide. There is no per-version split. Each new comment records the label it was written on (`made_on_label`), as context. |
| V7 | Each label has a **comment mode**: Open, Release, or Closed (Section 3.2). New labels start as Open. When a label is made Current, the settings page suggests Release. |
| V8 | **Closed means no comments.** Readers and authors alike see no comments or review flags on a Closed label, and nobody can add any there. |
| V9 | A label **can be set to Closed at any time**, even with comments still `OPEN`. Those comments simply stop being visible on that label. They remain on the guide's other labels and on the comments page. This is accepted as low risk; the settings page shows the open count for information only. |
| V10 | **Authors (topic editors) see every comment on every label except Closed ones**, whatever the mode, with a marker on comments outside the label's scope. Modes limit what readers see. |
| V11 | Replies follow their thread: a reply is shown wherever its top-level comment is shown, whatever label the reply was written on. |
| V12 | Display state and comment mode can be set on the guide settings page and through the API with a `topics:write` token, so an agent can do every step. |

## 3. Behavior

### 3.1 Display states

| State | Topic page, readers | Topic page, editors | Hosted guide opened by a reader | External guide |
|---|---|---|---|---|
| **Current** (at most one) | Shown prominently: "Read the guide" | Shown | Opens | Public; linked |
| **Listed** | Under "Other versions" | Shown | Opens | Public; linked |
| **Unlisted** (default) | Not shown | Shown | Opens with the link (today's behavior) | Public; not linked |
| **Authors only** | Not shown | Shown, badge "Authors only" | **Refused** (the existing refusal page) | Public on GitHub; not linked |
| **Retired** | Not shown | Under "Retired" | A short page: "This version has been retired", with a link to the Current label if there is one | Public on GitHub; not linked |

The topic page section is shown to readers who can view the topic when the guide has at least one Current or Listed label. Otherwise it stays editors-only, as today. The "Editors only" badge then marks only the items readers can't see, not the whole section. Editors' management links stay behind `canEditTopic`.

### 3.2 Comment modes and scope

A label's **publication time** is:
- for a hosted label: the time of its newest upload;
- for an external label: the `received_at` of its newest `es_guide_build` row with that label;
- otherwise: the time its comment mode was last set to Release.

A comment is **in scope** for label L when its top-level comment was written on L (`made_on_label = L`), or was created before L's publication time. Untagged comments count by their creation time.

| Mode | What a reader sees on label L | New comments on L | What an author sees on L |
|---|---|---|---|
| **Open** (default) | Every comment on the guide | Allowed | Every comment |
| **Release** | Comments in scope for L | Allowed, tagged L | Every comment; those out of scope are marked |
| **Closed** | Nothing | Refused for everyone | Nothing |

Review flags follow the same rules. Each flag also records `made_on_label`, and its scope is judged the same way.

Rolling labels such as `main` are normally Open, so they show the whole conversation.

### 3.3 Resolving the label a comment was written on

The guide sends its page address, `pageUrl`, with each new comment or flag. InteropHub resolves it like this:
- **Hosted:** `{hub}/guide/view/{documentKey}/{label}/…` gives `label`.
- **External:** the longest registered URL of the document (`es_guide_document_url`) that `pageUrl` starts with gives that binding's label.
- Anything else, or no `pageUrl`: `made_on_label` is null. The comment is accepted and treated as Open (older guides send no `pageUrl`).

The same resolution answers the `label` query on reads (Section 5.2), so a guide can pass `pageUrl` or `label`.

## 4. Data

In `db/unapplied_updates.sql`:

- **New table `es_guide_label`:**
  - columns: `guide_label_id`, `guide_document_id`, `label` (`VARCHAR(64)`), `display_state` (`CURRENT`, `LISTED`, `UNLISTED`, `AUTHORS_ONLY`, `RETIRED`; default `UNLISTED`), `comment_mode` (`OPEN`, `RELEASE`, `CLOSED`; default `OPEN`), `comment_mode_set_at`, `updated_by_user_id`, `updated_at`
  - unique on (`guide_document_id`, `label`), with a foreign key to the document and the user
  - a label with no row behaves as `UNLISTED` / `OPEN`
  - "At most one Current per guide" is enforced in the service, in one transaction, not with a constraint.
- **New column** `made_on_label VARCHAR(64) NULL` on `es_guide_comment` and `es_guide_review_flag`, with no backfill.
- **Audit:** label changes are recorded as `es_topic_change` rows (`change_type` `GUIDE`, `{"label": {...}}`), as uploads already are.

## 5. API contract changes (the guide side builds to this)

Update `topic-reference-comments-api-contract.md` with all of the following. Existing fields and behavior stay unchanged for clients that send nothing new.

### 5.1 Writes

- `POST /api/guide/{key}/comments` and the review-flag create gain an optional `pageUrl` (string, at most 2,000 characters), resolved per 3.3.
- Posting a comment, reply, or flag on a Closed label returns `403` with `{"error": "comments_closed"}`. A reply is checked against its own `pageUrl`'s label.
- Each comment, reply, and flag in responses gains `madeOnLabel` (string or null).

### 5.2 Reads

- `GET /api/guide/{key}/comments`, and the poll / `since` form, gain an optional `label` query, or `pageUrl`, resolved per 3.3.
  - **Without them:** today's behavior, unfiltered.
  - **With them:** filtered per 3.2 for readers. Authors get everything except on Closed labels, with each top-level comment and flag carrying `inScope` (boolean).
  - **The response gains `labelPolicy`:** `{"label", "display", "commentMode", "publishedAt"}`, or null when the label is unknown. The guide uses it to show a banner, hide the comment controls on Closed labels, and mark out-of-scope items for authors.
- `GET /api/guide/{key}/labels` (scopes `comments` or `topics:read`; requires view permission on the topic) lists every known label with `display`, `commentMode`, `publishedAt`, and, for editors, `openInScope` (information only).
  - **Known labels:** a hosted guide's uploaded labels, an external guide's registered URL labels, and labels named in its builds.
  - **For readers:** Authors only and Retired labels are left out.

### 5.3 Label settings

- `PATCH /api/guide/{key}/labels/{label}` with `{"display"?: "current" | "listed" | "unlisted" | "authors_only" | "retired", "commentMode"?: "open" | "release" | "closed"}`.
  - **Permissions:** scope `topics:write`, and the caller must pass `canEditTopic`.
  - **Effects:** setting `current` turns the previous Current label into `listed` and returns both. Setting `closed` always succeeds.
  - **Unknown label:** `404 not_found` for a label that isn't known (5.2).
  - **Responses:** `200` with the label object.
  - **Apache:** PATCH already passes through Apache (AART #3962).

## 6. User interface

- **Guide settings page** (`/es/topic-guide/{topicId}`): a table of the guide's labels, each with a display state select, a comment mode select, its publication time, and its open-in-scope count.
  - Making a label Current offers Release in the same form.
  - The open-in-scope count links to those comments on the comments page. It is information only and never blocks a change.
  - Uses existing `aira-*` classes only (read `docs/aira-web/README.md` first).
- **Topic page section** (`TopicReferenceSection` / `TopicReferenceSectionData`):
  - **Readers** see the Current label as the main link and Listed labels under "Other versions". Counts and comment lists follow the Current label's comment mode: none when it is Closed.
  - **Editors** see everything, with badges on Unlisted, Authors only, and Retired labels.
- **Hosted viewer** (`GuideViewServlet` / `GuideHostingService.open`):
  - Authors only: non-editors are refused.
  - Retired: non-editors get the retired page.
- **Comments page** (`/es/topic-guide-comments/…`): show `madeOnLabel` on each comment and filter by label.
- **Guide status page:** add a "Current" column, showing the Current label or "None".

## 7. Tests

- **State rules:** one Current per guide, with demotion; the defaults.
- **Hosted viewer:** each state for reader and editor.
- **Topic page:**
  - reader with and without any Current or Listed label
  - editor badges
  - Retired never shown to readers
- **Label resolution** (3.3): hosted path; longest external prefix; unknown and null.
- **Comment scope** (3.2): Release with tagged, untagged-before, and untagged-after comments; replies following their thread.
- **Closed:** no comments for anyone; posting refused (`comments_closed`); closing succeeds with comments still open, and those comments stay visible on other labels and on the comments page.
- **API:**
  - `labelPolicy`, `inScope`, and `madeOnLabel` in responses
  - reads without `label` unchanged
  - an older guide (no `pageUrl`) still works
- **PATCH permissions:** `topics:write` and `canEditTopic`; a reader gets `403`.
- **Browser check** on local Tomcat: a reader and an editor on a hosted guide with a Current label in Release mode, a Unlisted `main`, an Authors only label, and a Retired label.

## 8. Not in this plan

- **Guide side, in templates v0.22.0:**
  - send `pageUrl` with comments and flags
  - pass `label` on reads
  - use `labelPolicy`: show a banner, hide comment controls when Closed, mark out-of-scope items for authors
  - `publish_guide_to_hub.py --display/--comment-mode` (calls 5.3)

  That agent adapts once this lands.
- **Per-version comment threads:** rejected (V6).
