# Topic Roles: Adding Staff and Contributor — Evaluation

Status: **Proposal — awaiting product-owner decisions** (see §6). Nothing here is implemented.
Companion implementation prompt: [`topic-roles-implementation-prompt.md`](topic-roles-implementation-prompt.md).

Evidence for the current-state section was gathered from the code on `main` at `89008dc` and from the local database restored from the 2026-10-08 production backup.

---

## 1. Current application behavior

### 1.1 Where Topic roles live

All per-person Topic relationships live in a single column: `es_subscription.status`.

| `status` value | UI label | Meaning today |
|---|---|---|
| `SUBSCRIBED` | Follower | Gets public updates for the Topic. |
| `CHAMPION` | Champion | Topic leader; shown publicly; can manage the Topic. |
| `SUPPORT` | Support | Same management rights as Champion, but not shown publicly. |
| `UNSUBSCRIBED` | (removed) | Was following and stopped. |

There is one row per person per Topic, keyed by email (`email_normalized`), and the row may or may not have a `user_id`. Email-only rows can hold any status. Authorization checks usually match on `user_id` **or** `email_normalized`. Because sign-in uses a magic link, proving you own the email is enough to claim an email-only role.

Production volume is tiny, so a migration is cheap. There are 5 `CHAMPION` rows across 3 Topics and 2 `SUPPORT` rows across 2 Topics, and every one of them has a `user_id`. There are about 1,385 `SUBSCRIBED` Topic rows, and about half of those are email-only. Two Topic/email pairs already have duplicate rows. `EsInterestService.mergeAllDuplicateSubscriptions` picks a winner using the precedence `CHAMPION/SUPPORT > SUBSCRIBED > UNSUBSCRIBED`.

### 1.2 Role and notification preference are the same field (an existing defect)

Because the role *is* the subscription status, anything that stops a person's emails also strips their role:

- **Account → "Unsubscribe from all topics".** `EsSubscriptionDao.unsubscribeAllByEmailNormalized` sets `UNSUBSCRIBED` on every row whose status is `SUBSCRIBED`, `CHAMPION` or `SUPPORT`. A Champion who clicks it silently stops being a Champion everywhere.
- **The unsubscribe page and the account page.** Each lists Champion and Support rows as ordinary checkboxes, with only a badge. Unchecking one removes the role.
- **The meeting-attendance interest checkboxes.** These are the one place that guards against this (`// Never touch CHAMPION or SUPPORT subscriptions.`).

Nobody can currently be "a Champion who doesn't want the update emails." Fixing this is a prerequisite for the new roles: Staff and Contributor are exactly the people who will mute routine updates but must keep their access.

### 1.3 "Champion-equivalent" (`CHAMPION` or `SUPPORT`): what it grants today

The check is copied, not centralized. It appears in `EsSubscriptionDao.CHAMPION_EQUIVALENT_STATUSES` and is re-implemented privately in `EsTopicDetailServlet`, `EsTopicEditServlet`, `EsTopicManageServlet`, `EsTopicRelationshipServlet`, `EsTopicCurationServlet`, `EsMeetingAttendanceServlet`, `TopicFollowerManagementService` and `StarterPacketService`.

| Capability | Who has it today |
|---|---|
| "Manage this Topic" area; edit Topic details | Global admin, Topic-Space admin, Champion, Support |
| Manage followers: add, invite, change role, remove | Global admin, Topic-Space admin, Champion, Support |
| **Promote anyone to Champion or Support, or demote them** | Same group. The role servlet comment says this is "not treated as a high-security area." No restrictions and no audit. |
| Topic relationships and curation | Global admin, Champion, Support |
| Starter Packet: view, author, publish, upload, link | Global admin, Champion, Support. Topic-Space admins are deliberately **excluded** (Communication Bundles conceptual model §11.2). |
| Topic Resources and Topic Orientation bundle | Global admin and Topic-Space admin only (`canEditTopic` → `canAdministerSpace`). **Champions cannot.** |
| Edit or create Topic notes | Global admin, Topic-Space admin, meeting controllers, **Champion only.** Support is excluded (`hasActiveChampionForTopicAndUserId`). This is an inconsistency. |
| Control a meeting (agenda, chair, scribe) | Global admin, Topic-Space admin, meeting creator, designated or current chair or scribe, active series co-chair, accepted presenter. **Champions are not included.** |

### 1.4 Display

- **Topic page.** A `Champions` meta-chip lists `CHAMPION` names only, so Support is invisible. Right next to it is a `Supporters` chip.
- **"Supporter" already means something else.** `supporter` and `es_topic_supporter` are **organizations** publicly identified as supporting a Topic (see `docs/add-supporters.md`). Production has 0 organizations and 0 links, but the entity, admin screens, public `/es/supporters` page and Topic-page chip all exist. The proposed person-level "Supporter" role would collide with this on the same page.
- **Account and unsubscribe pages** show "Champion" or "Support" badges. The followers page has a role dropdown with the options Follower, Champion, Support and Unfollow.

### 1.5 Notifications and task alerts

- **`TopicContactResolver`** decides who gets "this Topic needs attention" mail. It returns `SUPPORT` and `CHAMPION` contacts together, with Support listed first. If there are none, it falls back to Topic-Space admins, and then to all global admins. It is used for:
  - new-comment notifications (`EsTopicReviewService`, email reasons `TOPIC_COMMENT_{SUPPORT,CHAMPION,ADMIN}_NOTIFY`)
  - the daily-digest "new followers" item (`NewFollowersDigestSource`)
- **`MeetingResponsibilityResolver`**, used by the `StaffActionDigestSource` daily digest of overdue meeting actions, falls back to the Champions and Support of every Topic on the agenda when a meeting has no chair.
- **Meeting communications** (call for topics, proposed or final agenda, reminder, notes available) have a "Topic champions/support" recipient group (`include_topic_champions`, `RecipientGroup.TOPIC_CHAMPION`). Its priority is presenter > champion > subscriber > general member.
- **Today Support receives exactly the same alerts as Champion.** The intended "Staff should not automatically receive Champion task alerts" is therefore a behavior change for current Support holders.

### 1.6 Resource and bundle visibility

- Communication bundles have three audience levels: `EsCommunicationBundlePurpose.Audience` = `PUBLIC`, `PARTICIPANTS` and `STEWARDS`. `PARTICIPANTS` currently means signed-in Topic-Space participants, which is a space-level concept and not a Topic-level one. The Starter Packet is hard-wired to `STEWARDS`, with the access rule written in `StarterPacketService.canAccess`.
- **Files are not access-controlled.** Stored-file URLs are anonymously readable by anyone who has the link (conceptual model §2 and §10). Audience restrictions control where a link is *disclosed*, not who can download it. The content boundary is explicit: InteropHub does not hold contracts, personnel information, PHI or anything that needs strong distribution control. "Limited distribution" means "not everyone needs this," not "confidential."
- Topic visibility as a whole is set at the Topic-Space level (`PUBLIC` or `PRIVATE`, plus `es_topic_space_member` with roles `MEMBER` and `ADMIN`). Holding a Topic role does not make a private space visible.

### 1.7 Meetings

- Meeting visibility follows space visibility. Series membership (`es_topic_meeting_member`) is a join list; it has an approval workflow column (`approved_by_user_id`) that nothing sets. Attendance is self sign-in plus observation from the console. Per-meeting roles are `CHAIR` and `SCRIBE`, and series co-chairs are stored in `es_topic_meeting_cochair`.
- Topic roles barely touch meetings today. Their only links are the champion recipient group, the responsibility fallback, the Champion-only note editing, and the attendance page's protection of Champion and Support rows.

### 1.8 APIs and integrations

- No public or app API exposes Topic roles. `EsTopicBoardApiServlet` does not include them.
- The **Dandelion sync** sends a Topic↔contact *assignment* for any active status (`SUBSCRIBED`, `CHAMPION` or `SUPPORT`). The assignment carries no role, so it is role-agnostic.

### 1.9 Audit history

Role changes overwrite `status`, and only `updated_at` changes. Managed additions record who added the person and why (`managed_added_*`), but promotions, demotions and removals are not recorded anywhere.

---

## 2. Proposed role model

### 2.1 Principle: one ladder, five separate dimensions

Store **one Topic role per person per Topic**, and make the roles a strict ladder in which each role includes everything below it. Derive capabilities and resource access from the role, so there are no per-person permission toggles. Treat only two things as genuinely independent settings: whether the person receives updates, and whether they receive task alerts.

```
Follower  <  Supporter*  <  Contributor  <  Staff  <  Champion
 (public)    (trusted)      (working)       (team, behind the scenes)  (team, visible lead)
                                            └──────── "Topic team" ────────┘
* name is an open question — see §3
```

| Dimension | How it is represented | Who controls it |
|---|---|---|
| **Role or relationship to the Topic** | One stored value (`topic_role`) | Assigners per §2.3; anyone can step themselves down |
| **Administrative capabilities** | Derived from the role. Staff and Champion together form the "Topic team" and manage the Topic. | Not separately assignable |
| **Resource-access level** | Each restricted item declares a *minimum role* (an audience). A person sees it if their role meets that minimum. No per-person access lists. | The item's author picks the audience |
| **Notification preferences** | `updates` (follow on or off) and `task_alerts` (on or off) are stored separately from the role | The person, with defaults set by role |
| **Public display or recognition** | Derived: Champions are shown publicly and nobody else is. A per-Topic "Topic team" list is shown to Contributors and above. | Not separately assignable (see open question Q7) |

Answers to the evaluation questions that this settles:

- **Q8: Topic-specific?** Yes. All five roles are per Topic, like Follower is today. Topic-Space membership stays a separate concept that controls visibility of private spaces.
- **Q9: Is Staff an authorization role, a display classification, or both?** **Both, and inseparably.** Staff is the existing `SUPPORT` tier renamed: full Topic-team working rights, no public leadership display, and task alerts off by default. A display-only Staff label without the rights, or the rights without the label, would recreate the confusion of today's "Support."
- **Q10: Should resource access be separate from role membership?** It should be separate in *declaration*: a resource or bundle states "Supporters and above," not a list of people. It should be *derived* from role at check time. Do not build per-resource access lists. Given that files are not access-controlled (§1.6), that machinery would add complexity without adding security.

### 2.2 Capability matrix

✅ = yes. ⚙️ = default that the person can change. ❓ = depends on an open question. "Team" = Staff + Champion. All rows are additionally limited by Topic-Space visibility.

| Capability | Follower | Supporter* | Contributor | Staff | Champion | Space admin | Global admin |
|---|---|---|---|---|---|---|---|
| View public Topic page and Public resources | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Receive public Topic updates (follow) | ⚙️ on | ⚙️ on | ⚙️ on | ⚙️ on | ⚙️ on | — | — |
| View **Supporter-level** items (e.g. Starter Packet) | | ✅ | ✅ | ✅ | ✅ | ❓ Q5 | ✅ |
| View **Contributor-level** items (working drafts, work-in-progress resources) | | | ✅ | ✅ | ✅ | ✅ | ✅ |
| See the "Topic team" list (names of Staff, Champions and Contributors) | | ❓ Q7 | ✅ | ✅ | ✅ | ✅ | ✅ |
| Included in the "Topic contributors" meeting-communication group | | | ✅ | ✅ | ✅ | | |
| Edit or create Topic meeting notes | | | ❓ Q6 | ✅ | ✅ | ✅ | ✅ |
| View **Team-level** items | | | | ✅ | ✅ | ✅ | ✅ |
| Edit Topic details, relationships and curation | | | | ✅ | ✅ | ✅ | ✅ |
| Author Starter Packets and other bundles | | | | ✅ | ✅ | ❓ Q5 | ✅ |
| Manage Topic Resources and Orientation | | | | ❓ Q9 | ❓ Q9 | ✅ | ✅ |
| Manage followers: add, invite, remove Followers | | | | ✅ | ✅ | ✅ | ✅ |
| Assign or remove Supporter and Contributor | | | | ✅ | ✅ | ✅ | ✅ |
| Assign or remove Staff | | | | | ✅ | ✅ | ✅ |
| Assign or remove Champion | | | | | ❓ Q3 | ✅ | ✅ |
| Shown publicly as a Topic leader | | | | | ✅ | | |
| Receive task alerts (comments, new followers, overdue actions) | | | | ⚙️ off | ⚙️ on | fallback | fallback |
| Included in the "Topic team" meeting-communication group (today "champions/support") | | | | ✅ | ✅ | | |

### 2.3 Who can assign and remove roles

| Target role | Can assign or remove | Notes |
|---|---|---|
| Follower | Self-service; Topic team; space admin; global admin | Unchanged |
| Supporter*, Contributor | Topic team (Staff, Champion); space admin; global admin | No self-service in v1. "Request access" is a mailto or comment to the Topic team (Q10). |
| Staff | Champion; space admin; global admin | **Staff cannot create Staff.** This prevents the team expanding itself sideways. |
| Champion | Space admin; global admin (and existing Champions if Q3 = yes) | **Fixes today's gap**, where Support can promote anyone, including themselves, to Champion. |
| Any role → step down | The person themselves | Steps down to Follower. Their follow setting is unchanged. |

Guard-rails:

- No one may assign a role higher than their own effective role, except admins.
- Removing the last Champion shows a warning but is allowed.
- Assigning a third or later Champion shows a soft warning ("Champions are normally one or two people"). It is not a hard limit.

### 2.4 Notification and task-alert behavior

- **`updates`** (follow on or off) controls public updates and digests. "Unsubscribe from all" and the unsubscribe page change **only** this setting and never the role. Unsubscribe pages must say "You'll keep your Staff role on *Topic X*; you just won't get update emails."
- **`task_alerts`** (on or off) controls the `TopicContactResolver` and `MeetingResponsibilityResolver` routes.
  - Defaults: Champion on. Staff off. Everyone else always off; the option is not offered below Staff.
  - The fallback chain becomes: Topic team members with alerts on → Topic-Space admins → global admins.
  - A Topic whose only team members are Staff with alerts off therefore escalates to space admins. This is intended.
- **Meeting-communication groups.** Rename "Topic champions/support" to **"Topic team"** (Staff and Champion). Add **"Topic contributors"** (Contributor and above). Keep **"Topic followers."**
  - Recipient-priority order: presenter > team > contributor > follower > general member.
  - A Supporter receives only the follower audience. Supporter status changes access, not email.
- The general-ES unsubscribe (`hasGeneralUnsubscribed`) continues to suppress everything, including task alerts. That is acceptable; it is the user's explicit choice.

### 2.5 Meetings

- Meeting visibility stays space-based, and Topic roles do not gate meeting pages.
- Contributors are the expected audience for working meetings. They are included in the "Topic contributors" communication group and are suggested in the attendance console's expected-people list (`MeetingExpectedPeopleService`).
- Meeting control (chair, scribe, agenda) stays assigned per meeting or series, as today. Topic roles do **not** grant meeting control, because ES meetings span many Topics. This is a non-goal.
- Note editing: Topic team in all cases. Contributors only if Q6 = yes.
- The attendance page's interest checkboxes must never change a role. They may toggle `updates` for Followers only.

### 2.6 Resource visibility (audience levels)

Replace the bundle audience values with Topic-scoped minimums.

| Audience | Visible to | Replaces |
|---|---|---|
| `PUBLIC` | Anyone who can see the Topic | `PUBLIC` |
| `SIGNED_IN` | Any signed-in person who can see the Topic | `PARTICIPANTS` (keep the meaning; rename only if desired) |
| `SUPPORTERS` | Supporter and above, plus admins | *new* |
| `CONTRIBUTORS` | Contributor and above, plus admins | *new* |
| `TEAM` | Staff, Champion, plus admins | `STEWARDS` |

- Starter Packet default audience: **`SUPPORTERS`** (Q4). Authoring stays at `TEAM`.
- Every restricted-content screen carries a fixed notice: "Limited distribution — not confidential. Anyone with a file link can open it."

---

## 3. Recommended terminology

| Concept | Recommended label | Why / alternatives |
|---|---|---|
| Public subscriber | **Follower** | Unchanged |
| Trusted, interested, gets limited-distribution material | **Insider** (recommended) or keep **Supporter** | "Supporter" already names *organizations* on the same Topic page (`Supporters` chip, `/es/supporters`, `Supporter` Java entity). Two different "Supporters" side by side will confuse users and developers. Alternatives: *Associate*, *Insider*, *Friend of the Topic*. If "Supporter" is kept for people, relabel the organization chip "Supported by" and use `SupportingOrganization` in new code. (Q1) |
| Active working participant | **Contributor** | Clear and distinct |
| Behind-the-scenes worker | **Staff** (if these are mostly AIRA employees or contractors) or **Organizer** (if volunteers are common) | "Staff" can read as "employee of AIRA." Q2. |
| Visible lead | **Champion** | Unchanged |
| Staff + Champion as a group | **Topic team** | Replaces "champion-equivalent" in code and "champions/support" in the UI |
| Old `SUPPORT` label | Retired; becomes Staff | "Support" vs "Supporter" is the most confusing pair in the current product |

---

## 4. User journeys

**J1. Champion adds a Staff member.**
1. *Manage this Topic → People*, search a user or enter an email.
2. Choose role **Staff** and enter an optional reason.
3. The new Staff member is emailed "You've been added as Staff on *Topic X*," with an explanation that they are not listed publicly and that task alerts are off with a link to turn them on.
4. The change is written to role history.

**J2. Staff adds an unregistered Contributor.**
1. Staff enters an email and selects **Contributor**.
2. An email-only row is created with `topic_role = CONTRIBUTOR`, and an invite is sent (reusing the managed-follower invite flow).
3. The person signs in with a magic link. The email match links their account, and they now see Contributor-level items and receive "Topic contributors" meeting communications.

**J3. Supporter opens the Starter Packet.**
1. The Topic page shows the Starter Packet teaser section only to Supporters and above.
2. They follow the link to the packet page. The page checks the role on every request, including direct URLs.
3. File links open (they are anonymous URLs), and the page shows the limited-distribution notice.

**J4. Someone without the role follows a shared packet link.**
1. Signed out → sign-in prompt → re-check.
2. Signed in but below Supporter → a "This is shared with *Topic X* Insiders/Supporters" page. It does **not** show packet content, titles or teasers, and it offers a "Ask the Topic team for access" action (Q10).

**J5. Staff member mutes email.**
1. They use "Unsubscribe from all." `updates` turns off for every Topic, and roles and `task_alerts` stay unchanged.
2. The confirmation lists the roles they keep and links to "step down" if that's what they meant.

**J6. Champion steps down.**
1. *My Topics → Step down to Follower*, with a confirmation.
2. If they were the last Champion, they see a warning.
3. Space admins receive a task alert for the Topic: "Topic X has no Champion."

**J7. Space admin replaces a Champion.** Both role changes are written to role history with the reason. The public chip updates immediately.

---

## 5. Impacts

### 5.1 Data model

Recommended: **Option B — extend `es_subscription`**. The table already is the person↔Topic relationship, with email-only support, managed-add metadata, unsubscribe tokens, Dandelion fan-out and duplicate merging. A second table would create two sources of truth for about 1,400 rows.

Add:
- `topic_role ENUM('FOLLOWER','SUPPORTER','CONTRIBUTOR','STAFF','CHAMPION') NOT NULL DEFAULT 'FOLLOWER'`. Use the final enum names chosen in Q1 and Q2.
- `task_alerts BIT(1) NOT NULL DEFAULT b'0'`.
- `role_assigned_by_user_id BIGINT NULL`, `role_assigned_at DATETIME(6) NULL`.

Narrow `status` to follow state only: `SUBSCRIBED` / `UNSUBSCRIBED`. During the migration window, keep `CHAMPION` and `SUPPORT` in the enum and stop writing them, then drop them in the following release.

New table `es_topic_role_change`:
- `es_topic_role_change_id`, `es_subscription_id`, `es_topic_id`, `user_id NULL`, `email_normalized`
- `old_role`, `new_role`, `changed_by_user_id`, `change_reason TEXT NULL`, `changed_at`
- Also record `task_alerts` changes, as old and new values.

Bundles: change the `audience_scope` value domain (it is `varchar(16)`, so no DDL is needed). Map `STEWARDS` → `TEAM`; `PARTICIPANTS` → `SIGNED_IN` (or keep it).

Rejected alternatives:
- **(A) Add `STAFF`, `CONTRIBUTOR` and `SUPPORTER` to the `status` enum.** This is the smallest change, but it keeps the unfollow-strips-role defect and leaves no place for a task-alert preference.
- **(C) A new `es_topic_member` table.** It is cleaner in isolation, but it duplicates identity, email-only handling, merge logic and sync fan-out.

### 5.2 Migration (all in `db/unapplied_updates.sql`, per `docs/database-release-practice.md`)

| From `status` | To `topic_role` | `status` | `task_alerts` |
|---|---|---|---|
| `CHAMPION` | `CHAMPION` | `SUBSCRIBED` | 1 |
| `SUPPORT` | `STAFF` | `SUBSCRIBED` | **1** (preserves today's behavior; Q8) |
| `SUBSCRIBED` | `FOLLOWER` | `SUBSCRIBED` | 0 |
| `UNSUBSCRIBED` | `FOLLOWER` | `UNSUBSCRIBED` | 0 |

- Write a seed role-history row per migrated Champion or Staff member, with reason "migrated from status".
- Bundle audiences: `STEWARDS` → `TEAM`. The Starter Packet default becomes `SUPPORTERS` only if Q4 = yes. Existing published packets keep their audience unless explicitly updated by the same decision.
- Duplicate merge precedence becomes "highest `topic_role`, then `SUBSCRIBED` over `UNSUBSCRIBED`, then has token, then oldest." Merge `task_alerts` with OR.
- Release: this introduces DB changes, so it ships with the next `0.X.0` / `db/v0.X` release, following the versioning convention.

### 5.3 Code

- **New `TopicRoleService`**, the single source of truth. It provides:
  - `roleFor(user, topic)`
  - `isTeam`
  - `meets(user, topic, minRole)`
  - `canAssign(actor, topic, targetRole)`
  - `canSeeAudience(user, topic, audience)`
  - `taskAlertRecipients(topic)`
- **Replace all of these with it:** the nine duplicated `isChampionEquivalentStatus` and `isChampionOf` copies, `TopicFollowerManagementService.isChampionOrSupportForTopic`, `StarterPacketService.canAccess`, and `hasActiveChampionForTopicAndUserId` (which also fixes Support being excluded from notes).
- **`EsSubscriptionDao`:**
  - Split the "active follower" queries, which filter on `status`, from the "role holder" queries, which filter on `topic_role`.
  - Recipient lists use `status`. Authorization and display use `topic_role`.
  - `unsubscribeAllByEmailNormalized` and the unsubscribe and account forms change `status` only.
- **`TopicContactResolver` and `MeetingResponsibilityResolver`:** use Topic team members with `task_alerts = 1`, then the existing fallbacks. Add `ContactRole.STAFF` and the email reason `TOPIC_COMMENT_STAFF_NOTIFY`, and keep the old reason constants so historical log rows still resolve.
- **`MeetingCommunicationRecipientResolver`, `RecipientGroup` and `es_meeting_communication`:**
  - Rename the group to `TOPIC_TEAM`.
  - Add `TOPIC_CONTRIBUTOR` with a new column `include_topic_contributors BIT(1) NOT NULL DEFAULT b'0'`.
  - Default it on for working-meeting communication types (Q11).
- **`EsTopicSubscriptionRoleServlet`:** validate the target with `canAssign`, write to `topic_role` (not `status`), and write role history.
- **Dandelion sync:** no change needed. The assignment remains "has an active relationship." Consider whether Followers should keep being synced (Q12).

### 5.4 UI

- **Followers page → "People" page.** Add a role column and dropdown filtered to the roles the viewer can assign, a separate "Gets updates" indicator, a "Task alerts" toggle shown only for team rows, and role-history disclosure per person.
- **Topic page:**
  - The `Champions` chip is unchanged.
  - Add a "Topic team" panel visible to Contributors and above (Q7).
  - The Starter Packet section is visible at `SUPPORTERS`.
  - Organization `Supporters` chip: relabel per Q1.
- **Account, unsubscribe and attendance pages.** Role badges are display-only and separate from the follow checkbox. Add a "Step down" action. Show the "You keep your role" message on unsubscribe.
- **Bundle authoring.** Add an audience selector that lists the levels in §2.6 with plain-language descriptions and the limited-distribution notice.
- **Meeting-communication screen.** Add the "Topic team" and "Topic contributors" checkboxes.
- Check `docs/aira-web/README.md` before adding any badge or panel CSS.

### 5.5 API

No external API currently exposes roles. If roles are exposed later, publish them as `topicRole`, and expose `CHAMPION` only to unauthenticated callers.

---

## 6. Open questions for the product owner

Each question has a recommendation and its implications. The implementation prompt marks every dependent step with `⟦DECISION Qn⟧`.

| # | Question | Recommendation | Implications |
|---|---|---|---|
| Q1 | What should the person-level "Supporter" tier be called, given that `Supporter` already means supporting *organizations* on the same page? | **Insider** (alternatives: *Associate*). Keep "Supporter" for organizations. | With "Supporter" for both, the Topic page would show a "Supporters" org chip while people are also called Supporters, and the code would have a `Supporter` entity next to `TopicRole.SUPPORTER`. Renaming the org side instead is cheap (0 rows in prod) but contradicts the deliberate naming in `add-supporters.md`. |
| Q2 | "Staff" or "Organizer"? | **Staff** if the people are mostly AIRA employees or contractors; otherwise **Organizer**. | "Staff" implies employment and may put volunteers off. Only labels and enum names change. |
| Q3 | Can existing Champions appoint or remove other Champions? | **No.** Only space admins and global admins. | Prevents unilateral leadership changes and self-promotion. Space admins become the gatekeepers. There are only 3 space admins today, so this adds a little admin load. |
| Q4 | Should Insiders/Supporters see the Starter Packet (moving it from `STEWARDS` to `SUPPORTERS`)? | **Yes**, per the stated intent. Authoring stays with the Topic team. | Wider disclosure of packet links, and files are anonymously reachable once disclosed. This contradicts conceptual model §11.2, which needs updating. |
| Q5 | Should Topic-Space admins see and author Starter Packets? Today they are deliberately excluded. | **Yes** for viewing, as stewards. **No** for authoring unless they are also Topic team. | It is odd that the people who can assign roles cannot see what those roles unlock. Viewing-only keeps authoring with the people who run the Topic. |
| Q6 | Can Contributors edit Topic meeting notes? | **Yes**, for notes attached to that Topic. | Matches "attends working meetings." The single-active-editor lock already prevents clobbering. Widens edit access from roughly 7 people to all Contributors. |
| Q7 | Who sees the "Topic team" list (Staff, Champions, Contributors)? | **Contributors and above.** Not Insiders/Supporters, not the public. | Respects that Staff are "behind the scenes" and that Contributors may not have consented to public listing. Supporters won't know who to ask, but the "Ask the Topic team" action covers that (Q10). |
| Q8 | Should the 2 existing `SUPPORT` holders keep task alerts after becoming Staff? | **Yes** (migrate with `task_alerts = 1`). They can turn it off. | Avoids a silent behavior change. New Staff default to off. |
| Q9 | Can the Topic team manage Topic Resources and the Topic Orientation bundle? Today only space admins can, while the team already uploads through the Starter Packet. | **Yes.** | Removes an inconsistency and a reason to make Champions space admins. Increases who can change the public Topic page's media. |
| Q10 | How does someone request Insider/Supporter or Contributor? | **v1: an "Ask the Topic team" action** (email or comment to task-alert recipients). No self-service or approval queue. | Keeps scope small. Builds a request queue only if demand appears. |
| Q11 | Should the new "Topic contributors" meeting-communication group default on? | **On** for Call for topics, Proposed agenda, Final agenda and Reminder. **Off** for Notes available (followers already get it). | Contributors become the working-meeting audience without anyone hand-picking them. |
| Q12 | Should Dandelion continue to receive assignments for plain Followers? | **No change in this project.** Revisit separately. | Out of scope. Flagged only because "assignment" semantics may be expected to mean "working on." |
| Q13 | Should Insider/Supporter and Contributor require a registered account at assignment time? | **No.** Allow email-only and send an invite. Access works after the person signs in with that email. | Consistent with managed followers. Magic-link sign-in proves email ownership. |

---

## 7. Risks and acceptance criteria

### Risks

- **Role confusion.** There are five tiers, two of which are new. Mitigations: the strict ladder, one-line role descriptions everywhere a role is chosen, and retiring "Support."
- **False sense of confidentiality.** "Limited distribution" content is reachable by anyone who has the URL. Mitigations: the mandatory notice and the existing content-boundary policy, which keeps sensitive documents out of InteropHub.
- **Privilege creep.** Today any Champion or Support can promote anyone, including to Champion. The ladder rule and §2.3 close this gap, but any missed call site that still writes `status = CHAMPION` would reopen it. Grep must be part of review.
- **Partial migration.** Code that filters on `status IN (CHAMPION, SUPPORT)` silently stops matching after migration. Every call site in §1.3 must move to `TopicRoleService`.
- **Alert gaps.** Staff-only Topics with alerts off escalate to space admins. Space admins must know this will happen.
- **Private spaces.** A role on a Topic in a private space does not grant visibility. Assigning a role to a non-member must warn the assigner (and optionally offer to add space membership).
- **Duplicates.** The 2 existing duplicate rows need the new merge precedence before or during migration.

### Acceptance criteria

1. "Unsubscribe from all," the per-Topic unsubscribe and the attendance-page checkboxes never change `topic_role` or `task_alerts`. A Champion who unsubscribes remains the public Champion.
2. Every check in §1.3 goes through `TopicRoleService`. A grep for `SubscriptionStatus.CHAMPION`, `SubscriptionStatus.SUPPORT` and `isChampionEquivalentStatus` in `src/main` returns no authorization uses.
3. Assignment rules in §2.3 are enforced server-side, with tests:
   - Staff cannot assign Staff or Champion.
   - Nobody but an admin can assign above their own role.
   - Champion assignment follows Q3.
4. Every role change, including migration and self step-down, writes an `es_topic_role_change` row with actor and reason.
5. Task alerts:
   - Comment notifications, new-follower digest items and the overdue-action fallback reach only Topic team members with `task_alerts = 1`, then space admins, then admins.
   - Migrated Staff keep alerts (Q8); newly assigned Staff do not.
6. The Topic page shows only Champions as leaders. Staff, Contributors and Insiders/Supporters never appear publicly. The Topic team list follows Q7.
7. Starter Packet teaser, list, page and direct URLs return the denial page (no titles or teasers) for viewers below the configured audience, and the content for viewers at or above it. Mutations require `TEAM`.
8. Bundle audience levels in §2.6 work for any purpose, and existing `STEWARDS` bundles behave as `TEAM`.
9. Meeting-communication preview shows the "Topic team" and "Topic contributors" groups with correct counts and dedup priority.
10. The migration is idempotent on a fresh production restore (`interophub-dev-db-restore`). Post-migration counts: 5 Champions and 2 Staff with `task_alerts = 1`. Follower and unsubscribed counts are unchanged.
11. Existing tests pass. New tests cover `TopicRoleService`, the migration mapping, assignment rules, unsubscribe preservation and audience checks.
