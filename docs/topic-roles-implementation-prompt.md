# Implementation Prompt: Topic Roles (Staff, Contributor, Insider/Supporter)

> **For the product owner, before handing this off.** This prompt contains decision placeholders written as `⟦DECISION Qn: …⟧`. Fill in the **Decisions** table below first. A coding agent must **stop and ask** if any row is still blank. Background and rationale are in `docs/topic-roles-staff-contributor-evaluation.md`.

## Decisions (fill in before starting)

| # | Decision | Recommended | **Approved value** |
|---|---|---|---|
| Q1 | Label and enum name for the trusted, limited-distribution tier | `Insider` / `INSIDER` | ⟦ ⟧ |
| Q2 | Label and enum name for the behind-the-scenes tier | `Staff` / `STAFF` | ⟦ ⟧ |
| Q3 | Can Champions assign or remove Champions? | No (space admin and global admin only) | ⟦ ⟧ |
| Q4 | Starter Packet viewing audience | `SUPPORTERS` tier (Q1 role and above) | ⟦ ⟧ |
| Q5 | Topic-Space admins: view Starter Packets? author them? | View yes / author no | ⟦ ⟧ |
| Q6 | Contributors can edit Topic meeting notes? | Yes | ⟦ ⟧ |
| Q7 | Who sees the "Topic team" list? | Contributor and above | ⟦ ⟧ |
| Q8 | Migrated `SUPPORT` holders keep task alerts? | Yes | ⟦ ⟧ |
| Q9 | Topic team can manage Topic Resources and Orientation? | Yes | ⟦ ⟧ |
| Q10 | Access-request mechanism | "Ask the Topic team" action only | ⟦ ⟧ |
| Q11 | "Topic contributors" meeting-comm group default | On for CALL_FOR_TOPICS, PROPOSED_AGENDA, FINAL_AGENDA, REMINDER | ⟦ ⟧ |
| Q12 | Dandelion assignment semantics | Unchanged | ⟦ ⟧ |
| Q13 | Email-only holders allowed for the Q1 tier and Contributor? | Yes | ⟦ ⟧ |

Throughout this prompt, `<INSIDER>` means the Q1 enum name and `<STAFF>` means the Q2 enum name.

---

## Task

You are working in the InteropHub repository: a Java servlet app with Hibernate, MySQL, and server-rendered HTML using the `aira-*` CSS from `aira-web-components`. Replace InteropHub's single-column Topic role model with a five-tier role ladder. Separate the person's role from their follow (email) preference, and add a task-alert preference and a role audit trail.

**Read before changing anything:**
- `CLAUDE.md`
- `docs/database-release-practice.md`. You must read it before editing `db/unapplied_updates.sql`.
- `docs/aira-web/README.md`. You must read it before adding any CSS.
- `docs/topic-managed-followers.md`
- `docs/add-supporters.md`. "Supporter" already names *organizations*; do not touch that entity.
- `docs/communication-bundles/InteropHub_Communication_Bundles_Conceptual_Model.md`, §10–11.

Follow existing patterns: DAO per table, servlets render HTML, services hold rules, tests in `src/test/java` mirroring the packages. Do not introduce new frameworks.

## Target model

**Role ladder** (one per person per Topic, each role including all lower ones):

`FOLLOWER < <INSIDER> < CONTRIBUTOR < <STAFF> < CHAMPION`

"Topic team" = `<STAFF>` + `CHAMPION`.

**Independent per-row settings:**
- `status` (`SUBSCRIBED` / `UNSUBSCRIBED`) — whether they receive update emails.
- `task_alerts` (boolean) — whether they receive task alerts. It is only meaningful for team rows.

**Derived, not stored:** management capabilities, resource access and public display.

## Current state you are replacing (verify each point yourself)

- `es_subscription.status` is `enum('SUBSCRIBED','CHAMPION','SUPPORT','UNSUBSCRIBED')`. `CHAMPION` and `SUPPORT` are "champion-equivalent."
- **Champion-equivalent checks are duplicated** in:
  - `EsSubscriptionDao` (`CHAMPION_EQUIVALENT_STATUSES`, `findChampionsByTopicId`, `findActiveChampionsByTopicIds`, `findSupportsByTopicId`, `findChampionOnlyByTopicId`, `hasActiveChampionForTopicAndUserId` — the last is CHAMPION-only)
  - `TopicFollowerManagementService.isChampionOrSupportForTopic`
  - `StarterPacketService.canAccess`
  - `MeetingAuthorizationService.canEditTopicNote` / `canCreateAdHocTopicNote`
  - Private copies in `EsTopicDetailServlet`, `EsTopicEditServlet`, `EsTopicManageServlet`, `EsTopicRelationshipServlet`, `EsTopicCurationServlet`, `EsMeetingAttendanceServlet`
  - `DandelionSyncService.isActiveAssignment`
- **Unfollowing removes the role.** These all write `UNSUBSCRIBED` over `CHAMPION` and `SUPPORT`:
  - `EsSubscriptionDao.unsubscribeAllByEmailNormalized`
  - `EsUnsubscribeServlet`
  - The account-page subscription form
- **`EsTopicSubscriptionRoleServlet`** lets any champion-equivalent user set any status, including CHAMPION, with no audit.
- **Alerts:**
  - `TopicContactResolver` (`SUPPORT` + `CHAMPION`, then space admins, then global admins) is used by `EsTopicReviewService` (comment notifications) and `NewFollowersDigestSource`.
  - `MeetingResponsibilityResolver` falls back to champion-equivalent contacts for `StaffActionDigestSource`.
- **Meeting comms:**
  - `es_meeting_communication.include_topic_champions`
  - `RecipientGroup.TOPIC_CHAMPION`
  - `MeetingCommunicationRecipientResolver`
  - `EsMeetingCommunicationServlet` (label "Topic champions/support"; group defaults per type)
  - `EsMeetingCommunicationPreviewServlet`
- **Bundle audiences:** `EsCommunicationBundlePurpose.Audience { PUBLIC, PARTICIPANTS, STEWARDS }`, stored in `es_communication_bundle.audience_scope varchar(16)`. The Starter Packet is created with `STEWARDS`.
- **Topic Resources** use `TopicSpaceAccessService.canEditTopic`, which means space admin only.
- **Role labels and badges** appear in `AccountServlet`, `EsUnsubscribeServlet`, `EsMeetingAttendanceServlet`, `EsTopicManageServlet` (role dropdown) and `EsTopicDetailServlet` (`Champions` chip).

## Work plan

### Phase 1 — Schema and migration (`db/unapplied_updates.sql` only, per the release doc)

1. **Alter `es_subscription`:**
   - Add `topic_role ENUM('FOLLOWER','<INSIDER>','CONTRIBUTOR','<STAFF>','CHAMPION') NOT NULL DEFAULT 'FOLLOWER'`.
   - Add `task_alerts BIT(1) NOT NULL DEFAULT b'0'`.
   - Add `role_assigned_by_user_id BIGINT NULL` with an FK to `auth_user`, and `role_assigned_at DATETIME(6) NULL`.
   - Add an index on `(es_topic_id, topic_role)`.
2. **Backfill:**
   - `CHAMPION` → role `CHAMPION`, status `SUBSCRIBED`, `task_alerts = 1`.
   - `SUPPORT` → role `<STAFF>`, status `SUBSCRIBED`, `task_alerts` = ⟦DECISION Q8: 1 if yes, 0 if no⟧.
   - Everything else → `FOLLOWER`, with status unchanged.
   - The SQL must be idempotent when run on a fresh production restore.
3. **Keep the old enum values for one release.** Leave `CHAMPION` and `SUPPORT` in the `status` enum for this release and add a `-- TODO drop in next release` note. No code may write them after this change.
4. **Create `es_topic_role_change`:**
   - Columns: `id`, `es_subscription_id`, `es_topic_id`, `user_id NULL`, `email_normalized`, `old_role NULL`, `new_role`, `old_task_alerts NULL`, `new_task_alerts NULL`, `changed_by_user_id NULL` (NULL means system/migration), `change_reason TEXT NULL`, `changed_at DATETIME(6)`.
   - Insert one seed row per migrated Champion and Staff member with reason `'Migrated from legacy status'`.
5. **Alter `es_meeting_communication`:** add `include_topic_contributors BIT(1) NOT NULL DEFAULT b'0'`.
6. **Update `es_communication_bundle.audience_scope`:** `STEWARDS` → `TEAM`, and `PARTICIPANTS` → `SIGNED_IN`. Existing Starter Packets → ⟦DECISION Q4: `SUPPORTERS` or stay `TEAM`⟧.
7. **Verify on a fresh restore:**
   1. Run the `interophub-dev-db-restore` skill (restore → unapplied updates).
   2. Confirm the counts: Champions unchanged (5 at time of writing), Staff = former SUPPORT count, Follower and unsubscribed counts unchanged.
   3. Report the actual numbers.
8. Do **not** freeze a `vX.Y` release file or bump the version. The product owner does that.

### Phase 2 — Domain layer

1. **Add a `TopicRole` enum** with an ordinal ladder and helpers: `atLeast(TopicRole)`, `isTeam()`, `label()`, `description()`. Write one-line descriptions suitable for UI tooltips.
2. **Update the `EsSubscription` model:** add the new fields, and narrow `SubscriptionStatus` usage to follow state. Keep the legacy constants deprecated and unused.
3. **Add `TopicRoleService` as the only place role rules live:**
   - `TopicRole roleFor(User, EsTopic)`. Match on `user_id` or verified `email_normalized`, as today. With duplicate rows, pick the highest role.
   - `boolean meets(User, EsTopic, TopicRole min)`. Global admin is always true. Space admin counts as `<STAFF>`-equivalent for management, but **not** for Starter Packet access unless ⟦DECISION Q5⟧.
   - `boolean canManageTopic(User, EsTopic)`, which is team, space admin or admin.
   - `boolean canAssign(User actor, EsTopic, TopicRole from, TopicRole to)`, enforcing:
     - Follower ↔ Follower: self, team, space admin or admin.
     - `<INSIDER>` / `CONTRIBUTOR`: team, space admin or admin.
     - `<STAFF>`: Champion, space admin or admin.
     - `CHAMPION`: space admin or admin ⟦DECISION Q3: plus Champions if yes⟧.
     - A non-admin may never assign a role above their own.
     - Anyone may lower **their own** role to `FOLLOWER`.
   - `void changeRole(User actor, EsSubscription row, TopicRole to, String reason)`. It validates, updates `topic_role` and the assignment fields, sets `task_alerts` to the default for the new role (Champion → 1, `<STAFF>` → 0, others → 0), writes `es_topic_role_change`, and enqueues the Dandelion contact upsert as current code does.
   - `void setTaskAlerts(User actor, EsSubscription row, boolean)`. Allowed for the row's own user, or anyone who can assign that row's role. Only valid on team rows. Audited.
   - `boolean canSeeAudience(User, EsTopic, Audience)`.
   - `List<Contact> taskAlertRecipients(EsTopic)`: team rows with `task_alerts = 1`, then space admins, then global admins.
4. **Bundle audience:** replace `Audience` with `PUBLIC`, `SIGNED_IN`, `SUPPORTERS`, `CONTRIBUTORS`, `TEAM`.
   - Map each to a minimum `TopicRole` (`SUPPORTERS` → `<INSIDER>`, `CONTRIBUTORS` → `CONTRIBUTOR`, `TEAM` → `<STAFF>`).
   - Add a lenient parser that reads the legacy `STEWARDS` and `PARTICIPANTS` values.
5. **Move every call site listed under "Current state" onto `TopicRoleService`.**
   - Delete the private `isChampionEquivalentStatus` and `isChampionOf` copies.
   - `canEditTopicNote` and `canCreateAdHocTopicNote` use team ⟦DECISION Q6: or `CONTRIBUTOR` and above⟧. This also fixes the existing bug where Support was excluded.
   - `TopicSpaceAccessService.canEditTopic` for Topic Resources and Orientation: ⟦DECISION Q9: add the Topic team, or leave it space-admin only⟧.
   - `StarterPacketService.canAccess` splits into `canView` (the configured audience) and `canAuthor` (`TEAM`, plus space admin if ⟦DECISION Q5⟧).
6. **`EsSubscriptionDao`:**
   - Split the queries into **follow** queries (filter on `status = SUBSCRIBED`; used for email recipients and follower counts) and **role** queries (filter on `topic_role`; used for authorization and display).
   - Rewrite `unsubscribeAllByEmailNormalized` so it changes `status` only.
   - Update the duplicate-merge precedence to: highest `topic_role`, then `SUBSCRIBED`, then has a token, then oldest. OR the `task_alerts` values together.
7. **`EsInterestService` and the self-follow flows:**
   - Following, re-following, unfollowing and the attendance-page checkboxes change `status` only, and never `topic_role`.
   - Re-following an `UNSUBSCRIBED` row keeps its role.

### Phase 3 — Notifications

1. **`TopicContactResolver`:** delegate to `taskAlertRecipients`.
   - Add `ContactRole.STAFF` and `EmailReason.TOPIC_COMMENT_STAFF_NOTIFY`.
   - Keep `TOPIC_COMMENT_SUPPORT_NOTIFY` so historical `email_send_log` rows still resolve.
2. **`MeetingResponsibilityResolver`:** its champion fallback uses team rows with `task_alerts = 1`.
3. **Meeting communications:**
   - Rename `RecipientGroup.TOPIC_CHAMPION` → `TOPIC_TEAM`, keeping the DB column `include_topic_champions` and changing only its label to "Topic team."
   - Add `TOPIC_CONTRIBUTOR`, sourced from `include_topic_contributors` and covering `CONTRIBUTOR` and above.
   - Priority: presenter > team > contributor > subscriber > general.
   - Both role groups ignore `status` (role holders get working-meeting mail even if they muted updates) but still respect `hasGeneralUnsubscribed`.
   - Defaults per ⟦DECISION Q11⟧.
   - Update the preview servlet.
4. **New emails** (follow the `EmailReason`, `EmailTemplates` and `email_send_log` pattern):
   - `TOPIC_ROLE_ASSIGNED`: role, plain-language description, who assigned it, the reason, whether it's publicly shown, and for team roles the task-alert setting with a link to change it.
   - `TOPIC_ROLE_REMOVED`.
   - `TOPIC_NO_CHAMPION`: sent to space admins when the last Champion leaves.
   - For email-only holders, reuse the managed-follower invite or registration flow ⟦DECISION Q13⟧.

### Phase 4 — UI

1. **`/es/topic-manage/{id}/followers` → rename the page to "People"** and keep the old URL working.
   - Columns: name, organization, registration status, **Role** (dropdown limited to the targets allowed by `canAssign`), **Gets updates**, **Task alerts** (team rows only), and **History** (expandable list from `es_topic_role_change`).
   - The add form gains a role selector, defaulting to Follower.
   - Add a soft warning when assigning a third Champion, and when removing the last one.
   - Warn when assigning a role on a Topic in a PRIVATE space to someone who isn't a space member.
2. **Topic page (`EsTopicDetailServlet`):**
   - The `Champions` chip is unchanged: Champions only.
   - Add a "Topic team" panel listing `<STAFF>`, `CHAMPION` and `CONTRIBUTOR` names, visible per ⟦DECISION Q7⟧.
   - The Starter Packet teaser section uses `canView`.
   - Do **not** rename the organization `Supporters` chip unless ⟦DECISION Q1⟧ kept "Supporter" for people, in which case relabel it "Supported by."
3. **Starter Packet pages and direct URLs:**
   - Below the audience: show a denial page that reveals no titles or teasers, plus an access request per ⟦DECISION Q10⟧.
   - At or above the audience: show the content and a fixed notice: "Limited distribution — not confidential. Anyone with a file link can open it."
4. **Bundle authoring:** add an audience selector with plain-language descriptions of each level.
5. **Account, unsubscribe and attendance pages:**
   - Show role badges as text next to the topic, separate from the follow checkbox.
   - Unsubscribe confirmations list the roles kept.
   - Add a "Step down to Follower" action with confirmation.
   - The "Support" label disappears everywhere.
6. **Meeting-communication form:** add "Topic team" and "Topic contributors" checkboxes.
7. **CSS:** use existing `aira-*` classes. If something is missing, follow `docs/aira-web/README.md` instead of adding local general-purpose CSS.

### Phase 5 — Tests and docs

1. **Unit tests:**
   - `TopicRoleService`: the role ladder, `canAssign` across every actor and target pair, self step-down, audience checks, and task-alert recipient fallback.
   - The duplicate-merge precedence.
   - Unsubscribe-all preserving roles.
   - `StarterPacketService` view versus author access.
   - Recipient-group dedup and priority.
2. **Update the existing tests** that reference `CHAMPION` or `SUPPORT` statuses: `TopicFollowerManagementServiceTest`, `StarterPacketServiceTest`, `TopicManageNavRendererTest`, `EsTopicResourceServletTest` and others found by grep.
3. **Documentation:**
   - Update `docs/topic-managed-followers.md` and §10–11 of the Communication Bundles conceptual model to the new rules.
   - Add a short `docs/topic-roles.md` user-facing guide: what each role means and who can assign it.
4. **Final grep:** `src/main` must contain no authorization or display use of `SubscriptionStatus.CHAMPION`, `SubscriptionStatus.SUPPORT`, `isChampionEquivalentStatus` or `isChampionOrSupport`.

## Out of scope

- Organization `Supporter` entity changes, beyond the optional chip relabel in Phase 4.
- Meeting-control permissions. Chair, scribe and co-chair stay per meeting or series.
- Per-person or per-resource access lists.
- Self-service role requests or approval queues (unless Q10 says otherwise).
- File-level access control for stored files.
- Dandelion payload changes (unless Q12 says otherwise).
- Release freezing and version bumps.

## Done when

- All acceptance criteria in `docs/topic-roles-staff-contributor-evaluation.md` §7 pass.
- `mvn test` is green.
- The migration has been verified on a fresh `interophub-dev-db-restore`, with counts reported.
- You have produced a short summary listing each decision value used and each place the approved value changed behavior compared with today.
