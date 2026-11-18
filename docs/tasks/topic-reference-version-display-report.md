# Topic Reference Version Display and Comment Modes: Implementation Report

**Plan:** `docs/tasks/topic-reference-version-display-and-comment-modes.md` (decisions V1 to V12, approved 2026-11-18)
**Date:** 2026-11-18
**State:** Implemented in the working tree. Not committed and not pushed. Production was not contacted.
**Contract:** `docs/tasks/topic-reference-comments-api-contract.md`, amended to match (summary in Section 4 below). The guide side (templates v0.22.0) should build from that contract.

## 1. Summary

- **Labels:** each label of a guide now has a display state (Current, Listed, Unlisted, Authors only, Retired) and a comment mode (Open, Release, Closed).
- **Where they are set:** the guide settings page, and `PATCH /api/guide/{key}/labels/{label}` with a `topics:write` token.
- **Comments:** each new comment, reply, and review flag records the label it was written on, resolved from `pageUrl`.
- **Reads:** with `label` or `pageUrl`, reads are filtered by the label's comment mode and carry `labelPolicy` and `inScope`.
- **Older guides:** reads and writes without the new fields behave as before, apart from the added `madeOnLabel` field in responses.
- **Pages:** the topic page section opens to readers only when a guide has a Current or Listed label. The hosted viewer refuses Authors only labels to non-editors and shows Retired labels to them as a retired page.
- **Tests:** full suite 439 run, 0 failures, 0 errors, 3 skipped. The 3 skips are pre-existing, not from this work.
- **Browser check:** done on local Tomcat as an editor and as a reader. Everything matched the plan after two rendering fixes made during the check (Section 6).

## 2. Files changed and why

### Database

| File | Why |
|---|---|
| `db/unapplied_updates.sql` | New table `es_guide_label`, and the new column `made_on_label` on `es_guide_comment` and `es_guide_review_flag` (Section 3). |

### Model, DAO, configuration

| File | Why |
|---|---|
| `model/EsGuideLabel.java` (new) | Entity for `es_guide_label`. |
| `dao/EsGuideLabelDao.java` (new) | Two lookups: a guide's label rows, and every guide's Current label (used by the status page). |
| `model/EsGuideComment.java`, `model/EsGuideReviewFlag.java` | Map `made_on_label`. |
| `src/main/resources/hibernate.cfg.xml` | Register `EsGuideLabel`. |

### Services

| File | Why |
|---|---|
| `service/GuideLabels.java` (new) | The rules, kept pure so they can be tested without a database. It holds the state and mode constants, how values are spelled in the API, label resolution from `pageUrl` (plan 3.3), the known-label set and publication time (`Facts`), and the scope and filtering rules (`Policy`, plan 3.2). |
| `service/GuideLabelService.java` (new) | Implements `GET /labels` and `PATCH /labels/{label}`, and the settings page's `set(...)`. It enforces one Current label per guide in one transaction, computes `openInScope`, and writes the audit record. |
| `service/HibernateGuideLabelStore.java` (new) | Persistence for the label service. Label rows and the `es_topic_change` row are saved in one transaction. |
| `service/HibernateGuideLabelFacts.java` (new) | Loads one guide's label facts in four small queries: settings, newest upload per label, newest build per label, and registered URL labels. |
| `service/GuideRows.java` | Adds `madeOnLabel` to the comment and flag rows and a new `LabelRow`. Makes the `UrlBinding(url, label)` constructor public so the servlet-package test can build one. |
| `service/GuideApiException.java` | New `comments_closed` error (403). |
| `service/GuideCommentService.java` | Changes listed below the table. |
| `service/HibernateGuideCommentStore.java` | Reads and writes `made_on_label`, fills `DocumentRow.hosting` (it was always `EXTERNAL` here before), and implements `labelFacts` and `publicBaseUrl`. |
| `service/GuideHostingService.java` | New `view(...)` returning `Viewed`, which applies the display state. `open(...)` is kept and delegates to it. The store gains a default `labels(...)` method. |
| `service/HibernateGuideHostingStore.java` | Implements `labels(...)`. |
| `service/TopicReferenceSectionData.java` | Changes listed below the table. |
| `service/GuideBuildService.java`, `service/HibernateGuideBuildStore.java` | The status report carries each guide's Current label. |

`GuideCommentService.java` changes:
- `pageUrl` on comments, replies, and flags; Closed labels are refused with `comments_closed`.
- `madeOnLabel` on threads, replies, and flags.
- A new `list(..., label, pageUrl)` overload that applies the label filtering and adds `labelPolicy` and `inScope`. The old 5-argument `list` delegates to it with no label.
- `digestFor(...)`: topic page totals as a reader of the Current label would see them.
- `threadsInScope(...)` for the comments page's label filter.
- The store gains two default methods, `labelFacts` and `publicBaseUrl`, so existing test fakes compile unchanged.

`TopicReferenceSectionData.java` changes:
- Reader visibility: a guide is shown to a reader only when it has a Current or Listed label.
- The new `presentLabels(...)` builds the label view: for readers, "Read the guide" plus "Other versions"; for editors, every label with badges plus a "Retired" group.
- The `Link` record gains `badge`. The `Guide` record gains `otherVersions`, `retired`, `reader`, `commentsHidden`, and `editorsOnly`. The `View` record gains `readersSee`.
- The existing `present` / `hubLinks` / `externalLinks` are unchanged and still used for editors of a guide with no label settings.

### Servlets

| File | Why |
|---|---|
| `servlet/GuideApiServlet.java` | Routes: `GET /api/guide/{key}/labels`, `PATCH /api/guide/{key}/labels/{label}` (the label is URL-decoded), and `label` / `pageUrl` on `GET .../comments`. |
| `servlet/GuideViewServlet.java` | Uses `view(...)` and renders the "This version has been retired" page, which links to the Current label. |
| `servlet/GuideOpenServlet.java` | `/guide/open?doc=…&label=…` now reaches any hosted label (see 5.2). |
| `servlet/TopicReferenceSection.java` | The header "Editors only" badge is shown only when readers see no guide. Adds the per-guide "Editors only" badge, "Other versions" and "Retired" groups, and link badges (a primary link's badge sits beside the button). Readers see an "Open comments" heading and the stats link into the guide. A Closed Current label shows "Comments are closed on this version." with no counts. |
| `servlet/GuideRegistrationPageServlet.java` | A "Versions and comments" table on the guide settings page. Changes listed below the table. |
| `servlet/GuideCommentsPageServlet.java` | A "Version" filter (comments in scope for that label) and "written on {label}" or "version not recorded" on each comment. |
| `servlet/GuideStatusRenderer.java` | A "Current" column (the label, or "None"). |

Settings page table, one row per known label:
- a display select and a comment-mode select;
- an "If made Current, switch comments to Release" checkbox (checked by default);
- the publication time;
- the open-in-scope count, linking to those comments on the comments page;
- a Save button for the row.

It uses only existing `aira-*` classes. All of them were checked in `docs/aira-web/reference/aira.css`, and no CSS was added.

### Tests and documents

| File | Why |
|---|---|
| `src/test/.../service/GuideLabelsTest.java` (new) | 16 tests (Section 5). |
| `src/test/.../servlet/TopicReferenceAuthorPagesTest.java` | Topic page HTML for a reader and an editor, the Closed Current label, the per-guide Editors only badge, and `hostedLabelUrl`. |
| `src/test/.../servlet/GuideStatusRendererTest.java` | The Current column. |
| `docs/tasks/topic-reference-comments-api-contract.md` | Contract amendments (Section 4). |
| `docs/tasks/topic-reference-version-display-report.md` | This report. |

## 3. Final SQL (appended to `db/unapplied_updates.sql`)

```sql
-- Topic Reference version display and comment modes.
-- One row per label of a guide that an author has configured. A label with no
-- row behaves as UNLISTED / OPEN. "At most one CURRENT per guide" is enforced
-- in the service, in one transaction, not by a constraint.
-- See docs/tasks/topic-reference-version-display-and-comment-modes.md, Section 4.
CREATE TABLE es_guide_label (
  guide_label_id BIGINT NOT NULL AUTO_INCREMENT,
  guide_document_id BIGINT NOT NULL,
  label VARCHAR(64) NOT NULL,
  display_state VARCHAR(16) NOT NULL DEFAULT 'UNLISTED',
  comment_mode VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  comment_mode_set_at DATETIME(6) NULL,
  updated_by_user_id BIGINT NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (guide_label_id),
  UNIQUE KEY uq_es_guide_label (guide_document_id, label),
  KEY fk_es_guide_label_user (updated_by_user_id),
  CONSTRAINT fk_es_guide_label_document FOREIGN KEY (guide_document_id) REFERENCES es_guide_document (guide_document_id),
  CONSTRAINT fk_es_guide_label_user FOREIGN KEY (updated_by_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT chk_es_guide_label_display CHECK (display_state IN ('CURRENT', 'LISTED', 'UNLISTED', 'AUTHORS_ONLY', 'RETIRED')),
  CONSTRAINT chk_es_guide_label_mode CHECK (comment_mode IN ('OPEN', 'RELEASE', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- The label each new comment, reply, or review flag was written on, resolved
-- from the guide's pageUrl. NULL for older rows and older guides (no backfill).
ALTER TABLE es_guide_comment ADD COLUMN made_on_label VARCHAR(64) NULL;
ALTER TABLE es_guide_review_flag ADD COLUMN made_on_label VARCHAR(64) NULL;
```

No seed data or test data was added to `unapplied_updates.sql`. Label audit rows reuse `es_topic_change` (`change_type` `GUIDE`), so that table needs no schema change.

**Applied locally:** the block above was applied to the live local `interophub` database on its own (Section 6.1 explains why this database was not restored).

**Rehearsed:**
1. Ran `restore_interophub_db_from_latest_local.py` with `INTEROPHUB_LOCAL_DB_NAME=interophub_rehearsal` and a scratch state file. This restored the latest local production backup into a separate database, applied `local_database_refresh.sql`, the whole `unapplied_updates.sql`, and the dev setup script. It exited 0.
2. Checked the result: `es_guide_label` matches the DDL (both CHECK constraints present), both `made_on_label` columns exist, and all 80 existing comments have `made_on_label` NULL.
3. Dropped the rehearsal database afterwards.

Hibernate `validate` accepted the schema at Tomcat startup.

## 4. API changes (contract amendments, for the guide side)

Diff-style summary of `topic-reference-comments-api-contract.md`. Section numbers are the contract's.

```diff
 Header
+ "Amended 2026-11-18" note: a client that sends none of the new fields gets the earlier behavior,
+  apart from the added madeOnLabel field in responses.

 2. Errors
+ 403 comments_closed: posting a comment, reply, or flag whose pageUrl belongs to a Closed label.

 3.2 Scopes
+ comments and topics:read also allow GET /api/guide/{key}/labels.
+ topics:write covers PATCH /api/guide/{key}/labels/{label}.

 3.6 /guide/open
+ For a HUB document, &label= may name any hosted label (for example a Current v0.2.0 with no registered URL).
+  It redirects to that label's view URL; the viewer applies the display state.

 5.2 Thread object
+ "madeOnLabel": string | null on threads and on replies. null for older items, for no pageUrl,
+  or for an unrecognized address.
+ "inScope": boolean on top-level threads, only in a 5.3 listing that named a known label.

 5.3 GET /comments (and the since form)
+ Query: label (at most 64 characters) or pageUrl (at most 2,000; ignored when label is given).
+ 5.3.1 Without either: unchanged, and no labelPolicy key.
+   With either: "labelPolicy": {"label","display","commentMode","publishedAt"} | null (unknown label -> unfiltered).
+   display:     current | listed | unlisted | authors_only | retired   (lower case)
+   commentMode: open | release | closed                               (lower case)
+   publishedAt: newest hosted upload, else newest build received_at, else (Release only) when the mode was set.
+   In scope: madeOnLabel == label, or created before publishedAt. Replies follow their thread.
+   open    -> everyone gets everything; every inScope is true.
+   release -> readers get in-scope items only; authors get everything, with inScope:false on the others.
+   closed  -> comments: [] and reviewFlags: [] for everyone.
+   since listings are filtered the same way; hidden threads are not sent, not even as tombstones.
+   Display state never filters comments; only the mode does.

 5.4 POST /comments
+ Optional "pageUrl" (top-level comment and reply). A reply now reads parentCommentId, text, and pageUrl.
+ 5.4.1 Resolution:
+   hosted:   {hub}/guide/view/{documentKey}/{label}/... -> label
+   external: the longest registered URL that the address starts with -> that URL's label
+   otherwise: madeOnLabel null, treated as Open
+   The query and fragment are ignored; scheme and host compare without case.
+   A Closed label -> 403 comments_closed. A reply is checked against its own pageUrl.

 6.1 Flag object
+ "madeOnLabel"; "inScope" only in a label-filtered 5.3 listing.

 6.2 POST /review-flags
+ Optional "pageUrl". A Closed label -> 403 comments_closed.
+ Re-posting the same anchor keeps the madeOnLabel the flag was created with.

+7.5 Labels
+ GET /api/guide/{key}/labels   (comments or topics:read; canView)
+   {"documentKey", "labels": [{"label","display","commentMode","publishedAt","openInScope"?}]}
+   Order: latest, main, then newest publishedAt first, then by name.
+   Readers do not get authors_only or retired labels, and get no openInScope.
+ PATCH /api/guide/{key}/labels/{label}   (topics:write and canEditTopic)
+   body {"display"?, "commentMode"?}; case is ignored; at least one field.
+   Errors: empty body 400 invalid_request; other field, null, or bad value 400 invalid_field;
+   unknown label 404 not_found; non-editor 403 forbidden.
+   Setting current demotes the previous Current label to listed in the same transaction.
+   Setting closed always succeeds.
+   200: the label object (with openInScope) plus "demoted": label object | null.
+   A no-op PATCH writes nothing. Each change -> es_topic_change GUIDE {"label": {...}}.

 10. Who builds what
+ row for 5.3.1, 5.4.1, madeOnLabel, 7.5 (InteropHub 2026-11-18 / templates v0.22.0)
```

For the guide:
- Send `pageUrl: location.href` on every comment, reply, and flag.
- Pass `pageUrl` (or `label`) on every `GET /comments` load and poll.
- Use `labelPolicy`: show a banner, hide the comment and flag controls when `commentMode` is `closed`, and mark items with `inScope: false` for authors.
- Treat a `403 comments_closed` on a write as "closed here".
- For `publish_guide_to_hub.py --display/--comment-mode`, call 7.5 `PATCH` after the upload. The label must already be known, so upload first.

## 5. Decisions the plan did not specify, and deviations

### 5.1 Deviation: no `open_comments 409`

The task prompt's scope list mentions "PATCH /labels/{label} with the open_comments 409" and "V9 (no Closed while comments in scope are OPEN)". The approved plan says the opposite:
- V9: "A label can be set to Closed at any time, even with comments still OPEN."
- 5.3: "Setting closed always succeeds."
- The browser-check instruction in the same prompt also says to expect closing to succeed.

I followed the plan and the browser instruction. There is no `open_comments` error. `openInScope` is information only, and closing `main` with open comments succeeded in the browser check. If a 409 is wanted after all, it would be a small change to `GuideLabelService.set`, but that would reopen V9.

### 5.2 Decisions made where the plan was silent

1. **API spelling.** `display` and `commentMode` are lower case in every response (`authors_only`, `release`), matching the plan's PATCH values. PATCH accepts any case. Storage is upper case.
2. **Known labels** are those listed in plan 5.2, plus any label that already has a settings row. For a hosted guide this includes the registered view-URL labels `latest` and `main` even before anything is uploaded to them; such a label has `publishedAt: null`.
3. **`inScope` appears for readers too** whenever a known label was named; for readers it is always `true` because out-of-scope items are not returned. It is absent when no label was named or the label is unknown, so older clients see nothing new apart from `madeOnLabel`.
4. **Unknown label on reads** (including a `pageUrl` that does not resolve): `labelPolicy: null` and the listing is unfiltered (treated as Open), consistent with plan 3.3's rule for writes.
5. **Display state never filters comments** in the API: an Authors only or Retired label still answers by its comment mode (V5). The readers' `GET /labels` omits those labels; a `GET /comments?label=` for them still returns `labelPolicy`, so the guide can show a banner.
6. **Tombstones** in `since` listings are filtered by the same rule as threads. A reader never receives a tombstone for a thread they could not see.
7. **Review flags:** re-posting an active flag on the same anchor keeps its original `madeOnLabel`. Clearing a flag is not affected by modes.
8. **`openInScope`:** on an Open label it counts every open, live, top-level comment. On Release and Closed labels it counts those in scope by the plan 3.2 rule, ignoring the mode, so for a label about to be closed it shows what readers will stop seeing. On a Closed label with no publication time, only tagged comments count.
9. **`comment_mode_set_at`** is updated on every mode change. It serves as the publication time only while the mode is Release and the label has neither an upload nor a build.
10. **PATCH response** includes `demoted` (the previous Current label object, or `null`) on every PATCH. A no-op PATCH writes no audit row. An empty body is `400 invalid_request`.
11. **Topic page for readers:**
    - With a Current label: "Read the guide" goes to `/guide/open?doc=…&label=<current>`. Counts, lists, and comment links follow the Current label's mode. Count and flag links go into the guide, because readers cannot use the comments page.
    - With Listed labels but no Current label: the listed labels appear under "Other versions" and counts are unfiltered.
    - With a Closed Current label: "Comments are closed on this version." and no counts.
12. **Topic page for editors:**
    - Counts are always unfiltered.
    - A guide with no label settings renders exactly as before (`present`).
    - With settings: the Current label is the primary button, with a green "Current" badge beside it. Other labels appear under "Other versions" with an "Authors only" badge, or an "Unlisted" badge when readers can see the section. Retired labels sit under "Retired".
    - With no Current label, the first label is made the primary link.
13. **"Editors only" badge:** the section header keeps it only when no reader-visible guide is shown. Otherwise each guide readers cannot see gets its own "Editors only" badge beside its hosting badge (plan 3.1: "marks only the items readers can't see").
14. **`/guide/open` and hosted labels.** Plan 3.1 makes a Current label like `v0.2.0` the readers' main link, but a hosted guide registers only `latest` and `main`. `/guide/open?label=` now builds the view URL for any valid hosted label from the registered view URL, so reader links keep the sign-in marker.
15. **Retired page:** HTTP 200, `Cache-Control: private, no-store`. It links to the Current label only when that label has an upload and is not the label being viewed.
16. **Settings page:**
    - The "If made Current, switch comments to Release" checkbox is checked by default. It applies only when the row's display changes to Current and the author left the mode unchanged in that save.
    - The count links to `/es/topic-guide-comments/{topic}?document=…&label=…&show=OPEN`.
17. **Comments page label filter:** it shows comments "in scope" for the label by the `openInScope` rule, ignoring the mode, so Closed labels still list their comments there (V9). Known limitation: after a disposition or close action, the redirect keeps `show` and `document` but not `label`.
18. **Label resolution details:**
    - A hosted path is accepted only for this document's key and a valid hosted label pattern.
    - An external address equal to a registered URL without its trailing slash matches.
    - Scheme and host compare without case; the path compares exactly.
    - `{hub}` comes from `PublicUrlService` (the configured external base URL).

### 5.3 Process deviation: rehearsal database

Restoring the live local database would have deleted another agent's local-only upload (`guide-hosted-test` / `v0.3.0-panelprototype`, uploaded at 12:14 today; the instruction was to leave it untouched). So the rehearsal ran in a separate database, and the live database got only the new block (Section 3). The other agent re-uploaded that label at about 12:52 during my session; I did not touch it.

## 6. Tests

### 6.1 Commands and results

Command, from the repository root: `mvn -o test`

Final result: Tests run 439, Failures 0, Errors 0, Skipped 3, BUILD SUCCESS. The 420 tests that existed before also passed before any new tests were added.

New and changed tests (all passing):

- `GuideLabelsTest` (16 tests):
  - Label resolution: hosted path; query, fragment, and host case; longest external prefix; unknown, blank, and null addresses.
  - Defaults: Unlisted / Open; an unknown label has no policy.
  - One Current with demotion, and the audit record contents.
  - PATCH validation: empty body, unknown field, bad value, null, unknown label 404, reader 403. A no-op writes nothing.
  - `GET /labels`: readers do not get Authors only or Retired labels, nor `openInScope`.
  - Release scope with tagged, untagged-before, untagged-after, and other-label comments; a reply following its thread; flags; `pageUrl` reads; `since` reads.
  - Release publication time falls back to when the mode was set.
  - Closed: nothing for the reader or the author; comment, reply, and flag refused with `comments_closed`; closing succeeds with open comments, which remain on other labels, in unfiltered reads, and in `threadsInScope`.
  - Older guides: a comment with no `pageUrl` gets `madeOnLabel` null; an unrecognized address; a `pageUrl` that is too long. Reads without a label have no `labelPolicy` and no `inScope`; an unknown label gives `labelPolicy` null and an unfiltered list.
  - Hosted viewer: every state, for reader and editor; the retired page's Current link.
  - Topic page data: reader and editor link groups, badges, the Closed Current label, reader counts following the mode.
  - Over HTTP: PATCH needs `topics:write` (otherwise `insufficient_scope`) and an editor (otherwise `forbidden`); `GET /labels` accepts `comments` or `topics:read`; a POST on a Closed label returns `comments_closed`.
- `TopicReferenceAuthorPagesTest` (+2): the reader and editor HTML, the Closed Current label, the section-level and per-guide "Editors only" badges, and `GuideOpenServlet.hostedLabelUrl`.
- `GuideStatusRendererTest` (+1): the Current column.

### 6.2 Failures and fixes during the work

Everything compiled and passed on the first run. The browser check found two rendering problems that tests had not covered:
- The "Current" badge was unreadable inside the filled primary button. It now sits beside the button as a success badge.
- An editor-only second guide lost its "Editors only" marking once the section opened to readers. It now has a per-guide badge.

Both are fixed, covered by tests, and re-checked in the browser.

## 7. Browser check (local Tomcat, `guide-hosted-test`, topic 189 "Guide Private Test Topic", private space)

**People:**
- Editor: `author.guide-test@example.invalid`, space admin.
- Reader: `reader.guide-test@example.invalid`, member, cannot edit.

Both signed in with the dev magic-link flow.

**Tokens:** a personal token for each (`comments`, `topics:write`, 30 days), created on the account page and kept only in scratch files. Both were revoked on the account page at the end. Each returned 401 afterwards, and the scratch files were deleted.

### 7.1 Setup, all through the API with `pageUrl`

Steps, in order:
1. Uploaded small test guides `v0.1.0`, `main`, `v0.3.0` (each 201).
2. Reader comment A on `main`.
3. Uploaded `v0.2.0` (201).
4. Reader comments: B on `main`, C on `v0.2.0` (its `pageUrl` included `?interophub=signin`), and D with no `pageUrl`.
5. Reader reply to B, written on `v0.2.0`.
6. Author flags written on `v0.3.0` and on `v0.2.0`.
7. PATCH (all 200):
   - `v0.2.0` to `current` + `release`
   - `main` to `unlisted` (a no-op)
   - `v0.3.0` to `authors_only`
   - `v0.1.0` to `retired`

Check: the reader's PATCH returned `403 forbidden`.

`latest` (registered, never uploaded) and the other agent's `v0.3.0-panelprototype` stayed Unlisted / Open and were not changed. Responses showed `madeOnLabel` `main` / `v0.2.0` / `null` as expected; the reply to B carried `v0.2.0`.

### 7.2 `GET /comments?label=` (the `old#…` threads are the guide's earlier untagged comments, all created before `v0.2.0` was published)

**`v0.2.0` (current/release):**

| | Comments | Flags |
|---|---|---|
| Reader | old×5, A, C (B and D are out of scope) | old×2, flag@v0.2.0 (flag@v0.3.0 is out) |
| Author | all 9; B and D have `inScope:false` | all 4; flag@v0.3.0 has `inScope:false` |

The reply to B is shown with B (for the author) despite being written on `v0.2.0`.

**Other reads:**
- `main`, `v0.3.0`, `v0.1.0` (all Open): everything, for both people.
- No label: everything, with no `labelPolicy`.

**`GET /labels`:**
- Reader: `latest`, `main`, `v0.2.0`, `v0.3.0-panelprototype`. No Authors only or Retired labels, and no counts.
- Author: all 6, with `openInScope` (`v0.2.0` = 6, others 8).

### 7.3 Topic page

- **Editor:**
  - `v0.2.0` is the primary button with a green "Current" badge.
  - "Other versions": `main` (Unlisted), `v0.3.0` (Authors only), `v0.3.0-panelprototype` (Unlisted). "Retired": `v0.1.0`.
  - Counts are unfiltered (8 open, 4 flags), and Manage comments / Guide settings are present.
  - The second guide, `guide-private-test` (no Current or Listed label), shows a per-guide "Editors only" badge.
- **Reader:**
  - The section is visible, with no "Editors only" badge and no management links.
  - "Read the guide" is the only version link. It goes to `/guide/open?doc=guide-hosted-test&label=v0.2.0`, which redirects (302) to `/guide/view/guide-hosted-test/v0.2.0/` with the sign-in marker.
  - Counts follow Release: 6 open (B and D excluded) and 3 flags (flag@v0.3.0 excluded). The "Open comments" list holds C, A, and older in-scope comments.
  - `guide-private-test` is not shown.

### 7.4 Hosted viewer

| Label (state) | Editor | Reader |
|---|---|---|
| `v0.2.0` (Current) | 200 guide | 200 guide |
| `main` (Unlisted) | 200 guide | 200 guide |
| `v0.3.0` (Authors only) | 200 guide | 404 private-topic refusal page |
| `v0.1.0` (Retired) | 200 guide | 200 "This version has been retired", button "Read the current version (v0.2.0)" |
| `v0.3.0-panelprototype` (Unlisted, other agent's) | 200 | 200 (read only, not modified) |

### 7.5 Settings and comments pages (editor)

- **Settings page:** the "Versions and comments" table lists all 6 labels with selects, the Release checkbox, publication times, counts, and Save.
- **Comments page:** "In scope for v0.2.0" lists the 5 older comments, A ("written on main"), and C ("written on v0.2.0"); the others show "version not recorded". B and D are excluded.

### 7.6 Closing a label with open comments

**Action:** set `main` to Closed with the settings-page form (the WEB path). It redirected with the notice "main: Unlisted, comments Closed". It succeeded although `main` had 6 open comments in scope.

**Results:**
- `GET /comments?label=main` returned `comments: []` and `reviewFlags: []` for both people, with `labelPolicy.commentMode: closed`.
- A and B are still returned on `v0.2.0` per Release scope (A in scope; B shown to the author as out of scope) and on unfiltered reads.
- The comments page still lists A and B.
- A POST with a `main` `pageUrl` returned `403 {"error":"comments_closed"}`.

**Audit:** `es_topic_change` rows 38 to 42 recorded each label change (API changes carry the token id; the WEB change does not).

### 7.7 Screenshots

All in `C:\Users\NATHAN~1\AppData\Local\Temp\claude\f002f47733d4b7e6\d353d9cd-d77f-41ff-8fe2-13320d794be9\scratchpad\screenshots\`. That is a session scratch folder, so copy them somewhere durable if you want to keep them.

| File | Shows |
|---|---|
| `01-editor-topic-page-before-badge-fix.jpg` | The unreadable Current badge, before the fix |
| `02-editor-topic-page.jpg` | Editor topic page after both fixes |
| `03-editor-settings-label-table.jpg` | Settings page label table |
| `04-editor-comments-page-label-filter.jpg` | Comments page with the label filter |
| `05-reader-topic-page.jpg` | Reader topic page |
| `06-reader-retired-v0.1.0.jpg` | Reader on the Retired label |
| `07-reader-authors-only-v0.3.0-refused.jpg` | Reader refused on the Authors only label |

The API outputs are in the same scratch folder (`reads-before-close.txt`, `reads-after-close.txt`).

### 7.8 Test data left in the local database

Only in the live local database, not in `unapplied_updates.sql`:
- the uploads `v0.1.0`, `main`, `v0.2.0`, `v0.3.0` on `guide-hosted-test`;
- the `[check]` comments and reply, and the two `[check]` flags;
- the four `es_guide_label` rows;
- the label audit rows.

The next daily refresh removes them. The browser session was switched between the two test users; sign in again as yourself.

## 8. How to verify by hand

1. **Tests:** `mvn -o test`. Expect 439 run, 0 failures, 3 skipped.
2. **Deploy:** `mvn clean package -Plocal-tomcat-deploy "-Dtomcat.webapps.dir=C:\Program Files\Apache Software Foundation\Tomcat 10.1\webapps"`. The new schema must be applied first (the daily refresh does it from `unapplied_updates.sql`). Hibernate `validate` refuses to start without it.
3. **Data:** as a topic editor of a hosted guide, upload two versions and open `/es/topic-guide/{topicId}?document={key}#labels`.
   - Make one Current: the Release checkbox switches its comments to Release. Making another Current turns the first into Listed (see the notice).
   - Set one Authors only and one Retired.
4. **Topic page:** open `/es/topic/{topicId}` as the editor (badges, Retired group, unfiltered counts), then as a member who cannot edit ("Read the guide", Other versions only for Listed labels, counts in scope).
5. **Hosted viewer as the member:** an Authors only label shows "Topic Not Found"; a Retired label shows "This version has been retired" with a link to the Current label.
6. **API with a personal token:**
   - `GET /api/guide/{key}/comments?label=<current>`: check `labelPolicy`; as an author, out-of-scope items have `inScope:false`.
   - `POST` a comment with `"pageUrl": "{hub}/guide/view/{key}/<label>/"`: the response has `madeOnLabel`.
   - `PATCH /api/guide/{key}/labels/<label>` with `{"commentMode":"closed"}`: 200 even with open comments. Then `GET ...?label=<label>` returns empty lists, and a POST with that `pageUrl` returns `403 comments_closed`.
7. **Status page:** `/admin/es/guides?view=status` shows a "Current" column.
8. **Audit:** `SELECT changes_json FROM es_topic_change WHERE change_type='GUIDE' ORDER BY es_topic_change_id DESC LIMIT 5;`
