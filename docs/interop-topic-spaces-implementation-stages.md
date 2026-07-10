# InteropHub Topic Spaces Upgrade — Implementation Stages

## 1. Purpose

This document divides the InteropHub Topic Spaces upgrade into focused development stages suitable for separate GitHub Copilot prompts.

All stages must follow the architectural rules defined in:

`interop-topic-spaces-upgrade-context.md`

That context document is authoritative. This stages document defines sequencing and expected outputs; it does not modify the settled architecture.

The stages are development checkpoints only. The finished work will be deployed as:

- One application upgrade
- One database conversion
- No intermediate production state
- No staged production rollout
- No dual-schema compatibility period

Each stage should be implemented, reviewed, compiled, and committed before moving to the next stage.

---

## 2. General Instructions for Every Stage

Every Copilot prompt should include or reference the following expectations:

1. Read `interop-topic-spaces-upgrade-context.md` before making changes.
2. Implement only the requested stage.
3. Do not anticipate later stages unless a small supporting change is required to compile.
4. Do not change any settled architectural rule.
5. Do not create a many-to-many topic-to-space table.
6. Do not add topic movement history or migration audit tables.
7. Do not introduce staged production compatibility.
8. Do not make Topic Space visibility editable after creation.
9. Do not allow public and private topics to mix in meetings.
10. Enforce security rules server-side, not only in the user interface.
11. Reuse existing InteropHub patterns where they remain compatible with the architecture.
12. Identify all files changed.
13. Compile or otherwise validate the application.
14. Report:
    - Assumptions made
    - Repository behavior that conflicts with the context
    - Deferred work
    - Validation performed

---

# Stage 1 — Record the Architectural Rules

## Goal

Add the approved Topic Spaces architecture to the repository so future implementation work has a stable reference.

## Scope

- Add `interop-topic-spaces-upgrade-context.md` to the appropriate repository documentation directory.
- Add this implementation stages document.
- Review existing Topic Spaces planning or assessment documents.
- Mark older conflicting recommendations as superseded where appropriate.
- Ensure repository documentation clearly states that the context document is authoritative.

## Required architectural points

The documentation must clearly preserve these rules:

- One topic belongs to one Topic Space.
- Topic Space is stored directly on the topic.
- Topic Space visibility is fixed at creation.
- Topic Spaces cannot be deleted.
- Neighborhoods belong to one Topic Space.
- Meetings belong to one host Topic Space.
- Public meetings contain only public topics.
- Private meetings contain only topics from their own private Topic Space.
- Emerging Standards is the legacy default.
- Production conversion occurs in one upgrade.

## Out of scope

- Database changes
- Java model changes
- UI changes
- Access-control implementation
- Conversion scripts

## Acceptance criteria

- The context document exists in the repository.
- This stages document exists in the repository.
- No repository documentation still presents many-to-many Topic Space assignment as the approved design.
- The application still compiles unchanged.

---

# Stage 2 — Add Topic Space Schema and Domain Models

## Goal

Add the database structures and Java domain models needed for Topic Spaces without yet changing all application behavior.

## Scope

Create the Topic Space catalog and membership structures.

Add Topic Space foreign keys to:

- Topics
- Neighborhoods
- Meetings

Create or update Hibernate entities and configuration.

## Required database model

### Topic Space

Create a table equivalent to:

```text
es_topic_space
  es_topic_space_id
  space_code
  space_name
  description
  visibility
  display_order
  is_active
  created_at
  updated_at
```

Requirements:

- `space_code` is unique and stable.
- `visibility` supports `PUBLIC` and `PRIVATE`.
- `is_active` supports retiring a space without deletion.
- No delete behavior should cascade through topics, neighborhoods, meetings, or members.

### Topic Space membership

Create a table equivalent to:

```text
es_topic_space_member
  es_topic_space_member_id
  es_topic_space_id
  user_id
  role
  created_at
  updated_at
```

Requirements:

- Membership is unique by Topic Space and user.
- Initial roles may be limited to `MEMBER` and `ADMIN`.
- System administrators do not require explicit membership rows.

### Existing tables

Add:

```text
es_topic.es_topic_space_id
es_neighborhood.es_topic_space_id
<meeting-table>.es_topic_space_id
```

These fields may be temporarily nullable within the migration script, but the final converted schema must require them.

## Java changes

Create or update:

- Topic Space entity
- Topic Space member entity
- Topic entity
- Neighborhood entity
- Meeting entity
- Hibernate configuration
- Basic DAO classes needed to load and persist Topic Spaces and memberships

## Important exclusions

- Do not create `es_topic_space_topic`.
- Do not implement the production conversion yet.
- Do not change routes or navigation yet.
- Do not implement full access filtering yet.
- Do not add editable meeting visibility.
- Do not remove existing topic fields.

## Acceptance criteria

- New tables and foreign-key fields are represented in migration SQL.
- Hibernate entities compile.
- Topic Space and member DAOs support basic retrieval and persistence.
- No topic can be assigned to multiple Topic Spaces.
- No existing application behavior is intentionally changed yet.
- The application compiles.

---

# Stage 3 — Create the One-Time Data Conversion

## Goal

Create the complete conversion that transforms a production copy from the current schema and data into the final Topic Spaces schema.

## Scope

Write one deterministic conversion migration.

The migration must create and seed:

1. Emerging Standards
   - Code: stable code chosen for the application
   - Visibility: `PUBLIC`

2. Building Bridges
   - Code: stable code chosen for the application
   - Visibility: `PUBLIC`

3. AIRA Opportunity Nursery
   - Code: stable code chosen for the application
   - Visibility: `PRIVATE`

## Required conversion behavior

The migration must:

1. Assign every existing topic to Emerging Standards.
2. Assign every existing neighborhood to Emerging Standards.
3. Find the existing neighborhood named `Country Interview`.
4. Identify every topic assigned to that neighborhood.
5. Move those topics to Building Bridges.
6. Remove all neighborhood assignments from those moved topics.
7. Delete the `Country Interview` neighborhood.
8. Assign every existing meeting to Emerging Standards.
9. Make Topic Space foreign keys required after conversion.
10. Preserve the total number of topics.

## Validation requirements

The migration must validate that:

- The three initial Topic Spaces exist.
- Every topic has one Topic Space.
- Every neighborhood has one Topic Space.
- Every meeting has one host Topic Space.
- No Building Bridges topic has neighborhood assignments.
- The `Country Interview` neighborhood no longer exists.
- The total number of topics is unchanged.
- No duplicate Topic Space codes exist.

The migration should fail clearly if:

- More than one `Country Interview` neighborhood exists unexpectedly.
- Required source data is inconsistent.
- Any topic remains without a Topic Space.
- Any neighborhood remains without a Topic Space.
- Any meeting remains without a Topic Space.

## Operational assumptions

- The migration is run while the application is disabled.
- The old application does not need to run after the migration.
- The new application does not need to run before the migration.
- Partial conversion recovery is not required.
- Repeated testing will occur against newly restored production copies.
- Idempotency is not required unless repository migration conventions require it.

## Out of scope

- Runtime access filtering
- Topic Space UI
- Membership UI
- Meeting selection rules
- New Building Bridges tags
- Audit history

## Acceptance criteria

- The migration succeeds on a current production copy.
- Validation queries pass.
- Topic count is unchanged.
- Country Interview topics are in Building Bridges with no neighborhood assignments.
- All other existing topics are in Emerging Standards.
- Existing meetings are in Emerging Standards.
- The converted schema contains no nullable required Topic Space foreign keys.

---

# Stage 4 — Centralize Topic Space Access Control

## Goal

Create one consistent access-control layer for Topic Spaces, topics, meetings, and related content.

## Scope

Implement a common service or policy layer that answers questions such as:

```text
canViewSpace(user, space)
canAdministerSpace(user, space)
canViewTopic(user, topic)
canEditTopic(user, topic)
canViewMeeting(user, meeting)
canAdministerMeeting(user, meeting)
getVisibleSpaceIds(user)
```

## Required access rules

### Public spaces

- Visible to anonymous and authenticated users.
- Public topics are visible without membership.
- Public meetings are visible without membership.

### Private spaces

- Visible only to:
  - Explicit members
  - Topic Space administrators
  - System administrators
- Private topics and meetings must be inaccessible to nonmembers.
- Direct URLs must enforce the same rules as navigation.

### System administrators

- May access and administer all Topic Spaces.
- Do not require membership rows.

## Required query review

Apply the access layer to all current paths that retrieve or expose topics and meetings, including:

- Topic list queries
- Topic detail queries
- Meeting list queries
- Meeting detail queries
- Campaign topic queries
- Relationship queries
- Curation queries
- Subscription and follow displays
- Search
- Exports
- Administrative reports
- Notification generation
- Dandelion synchronization
- Deep links

## Leakage prevention

Public responses must not reveal:

- Private Topic Space names
- Private topic titles
- Private topic IDs
- Private relationships
- Private meetings
- Private notes
- Private agenda membership
- Counts that disclose private content
- Reverse links from public content to private content

## Out of scope

- Final navigation design
- Topic Space admin UI
- Meeting topic-selection UI
- New membership-management UI

## Acceptance criteria

- Access rules are implemented centrally.
- Direct private-topic and private-meeting URLs are denied to nonmembers.
- Anonymous access to public content continues to work.
- System administrators can access all spaces.
- No DAO or service path known to expose topics or meetings bypasses the access rules.
- The application compiles.
- Manual tests cover anonymous, member, nonmember, and administrator access.

---

# Stage 5 — Add Topic Space Navigation and Preserve Legacy Routes

## Goal

Introduce Topic Space-aware browsing while preserving current Emerging Standards bookmarks and behavior.

## Scope

Add Topic Space-aware routes and navigation.

Suggested canonical routes:

```text
/spaces/{space-code}/topics
/spaces/{space-code}/topic/{topic-id}
/spaces/{space-code}/meetings
/spaces/{space-code}/meeting/{meeting-id}
```

Exact route naming may follow repository conventions.

## Legacy behavior

Preserve:

- `/es/topics`
- `/es/topic/{id}`
- Other existing Emerging Standards bookmarks where practical

Required behavior:

- `/es/topics` opens Emerging Standards.
- Existing topic detail links continue to resolve.
- If an old `/es/topic/{id}` URL refers to a topic moved to Building Bridges, redirect to the canonical Building Bridges topic URL.
- Access checks must occur before rendering or redirecting private content.

## Navigation behavior

- Show public Topic Spaces to all users.
- Show private Topic Spaces only to authorized users.
- Use Topic Space as the primary context for topic browsing.
- Show neighborhoods only from the current Topic Space.
- Preserve existing Emerging Standards behavior as the default legacy experience.

## Out of scope

- Topic Space creation and editing
- Membership-management UI
- Meeting topic-selection rules
- Per-space custom topic fields
- Broad visual redesign

## Acceptance criteria

- Users can browse topics by Topic Space.
- Unauthorized users cannot see private Topic Spaces in navigation.
- `/es/topics` still works.
- Old topic bookmarks still work.
- Moved Country Interview topic links redirect correctly.
- Public and private routes enforce centralized access rules.
- The application compiles.

---

# Stage 6 — Build Topic Space and Membership Administration

## Goal

Provide administration tools to create and maintain Topic Spaces and private-space membership.

## Scope

Create a Topic Space administration page.

Supported actions:

- List Topic Spaces
- Create a Topic Space
- Edit:
  - Label
  - Description
  - Display order
  - Active status
- View visibility
- View stable code
- Manage membership for private spaces

## Creation rules

When creating a Topic Space:

- Require a stable unique code.
- Require a display label.
- Require visibility selection.
- Visibility becomes immutable after creation.
- Code becomes immutable after creation.

## Editing rules

Allow editing:

- Display label
- Description
- Display order
- Active status

Do not allow:

- Deletion
- Code changes
- Visibility changes

## Membership behavior

For private spaces:

- Add members
- Remove members
- Assign basic space role
- Prevent duplicate membership
- Allow system administrators regardless of membership

For public spaces:

- Membership management may be hidden or displayed as unused.
- Public access must not depend on membership.

## Inactive spaces

An inactive Topic Space:

- Remains visible to authorized administrators.
- Retains its existing topics, meetings, neighborhoods, and memberships.
- Does not accept new topics.
- Does not accept new meetings.
- Should not appear as a normal creation destination.

## Out of scope

- Granular per-action permissions
- Invitation email workflow unless already supported and trivial to reuse
- Visibility conversion
- Topic Space deletion
- Bulk membership import

## Acceptance criteria

- Administrators can create a public or private Topic Space.
- Visibility and code cannot be changed after creation.
- Topic Spaces cannot be deleted.
- Private membership can be added and removed.
- Duplicate memberships are prevented.
- Inactive spaces cannot receive new topics or meetings.
- The application compiles.

---

# Stage 7 — Update Topic and Neighborhood Administration

## Goal

Make topic ownership and neighborhood tagging consistent with Topic Space rules.

## Scope

Update topic administration so each topic has exactly one Topic Space.

Update neighborhood administration so each neighborhood has exactly one Topic Space.

## Topic editing rules

- Topic Space is required.
- New topics default to Emerging Standards only where legacy behavior requires a default.
- Only active Topic Spaces may receive new topics.
- The neighborhood selector shows only neighborhoods from the selected Topic Space.
- Saving must reject cross-space neighborhood assignments.

## Topic movement rules

When moving a topic to another Topic Space:

- Clear all existing neighborhood assignments unless the administrator explicitly remaps them.
- Do not preserve invalid cross-space tags.
- Check meeting assignments before allowing the move.
- Reject the move if it would make an existing meeting agenda invalid.
- Tell the administrator which meeting assignments must be removed first.

Do not:

- Create movement history.
- Automatically move meetings.
- Silently delete agenda entries.

## Neighborhood rules

- Each neighborhood belongs to one Topic Space.
- Neighborhood names are unique within a Topic Space.
- Identical names may exist in different Topic Spaces.
- Only active Topic Spaces may receive new neighborhoods.
- A neighborhood cannot be assigned to a topic in another space.

## Import behavior

Update topic import to:

- Accept exactly one Topic Space.
- Resolve the Topic Space by stable code.
- Validate every neighborhood against that Topic Space.
- Reject unknown or cross-space neighborhoods.
- Do not accept multiple spaces.

## Dandelion behavior

For this stage:

- Continue deriving Dandelion project tags from neighborhood names.
- Do not replace neighborhood tags with Topic Space names.
- Do not redesign Dandelion taxonomy.

## Acceptance criteria

- Every newly saved topic has one Topic Space.
- Topic forms show only valid neighborhoods.
- Cross-space neighborhood assignment is rejected server-side.
- Moving a topic clears or remaps neighborhoods safely.
- Invalid meeting assignments block topic movement.
- Imports accept one Topic Space only.
- Building Bridges and Opportunity Nursery may operate with no neighborhoods.
- The application compiles.

---

# Stage 8 — Implement Meeting and Agenda Topic Space Rules

## Goal

Make meetings inherit Topic Space visibility and enforce strict public/private topic separation.

## Scope

Require every meeting to have one immutable host Topic Space.

## Meeting creation rules

- Host Topic Space is required.
- Only active Topic Spaces may host new meetings.
- The selected host Topic Space determines meeting visibility.
- Do not store independent meeting visibility.
- After creation, the host Topic Space cannot be changed.

## Topic-selection rules

### Public meeting

A meeting hosted by a public Topic Space may include:

- Topics from its own public Topic Space
- Topics from any other public Topic Space

It may not include:

- Topics from any private Topic Space

### Private meeting

A meeting hosted by a private Topic Space may include:

- Topics from that same private Topic Space only

It may not include:

- Public topics
- Topics from another private Topic Space

## Enforcement points

Enforce the rules in:

- Topic-selection lists
- Agenda editing UI
- Server-side create/save operations
- DAO or service validation
- Imports or API endpoints, if any
- Meeting duplication or copy behavior, if any

The server must reject invalid agenda combinations even if a request bypasses the UI.

## Notes and derived displays

- Public meeting notes are public.
- Private meeting notes require access to the host private Topic Space.
- Public topic pages may show public meetings only.
- Private topic pages may show accessible private meetings.
- Public topics must never display private meeting references or notes.
- Notifications must follow meeting access rules.

## Legacy meetings

- All converted existing meetings begin in Emerging Standards.
- Existing meeting URLs should continue to work where practical.
- Existing agenda assignments should remain valid after conversion because all existing topics begin in Emerging Standards except converted Building Bridges topics.
- Any converted meeting containing a moved Building Bridges topic remains valid because both spaces are public.

## Acceptance criteria

- Every meeting has one host Topic Space.
- Host Topic Space cannot be changed after creation.
- Public meetings can select topics from any public space.
- Public meetings cannot select private topics.
- Private meetings can select only topics from their own private space.
- Invalid combinations are rejected server-side.
- Private notes and meetings do not leak through public topic pages or notifications.
- The application compiles.

---

# Stage 9 — Audit and Correct Cross-Cutting Features

## Goal

Find every secondary path that exposes, modifies, synchronizes, or reports topics and meetings, then make it obey Topic Space rules.

## Scope

Perform a repository-wide review of:

- Follow and unfollow
- Unsubscribe
- Champion and support roles
- Meeting requests
- Campaigns
- Relationships
- Curations
- Search
- Topic imports
- Exports
- Reports
- Email queues
- Notifications
- Dandelion synchronization
- Meeting agenda summaries
- Public home pages
- Administrative dashboards
- Direct links
- Recommendation or discovery features
- Background jobs
- Any cached or precomputed topic lists

## Review questions

For every path, determine:

1. Can it surface a topic?
2. Can it surface a meeting?
3. Can it reveal a relationship to private content?
4. Can it send a notification about private content?
5. Can it create an invalid cross-space assignment?
6. Does it use the centralized access service?
7. Does it assume all topics are Emerging Standards topics?
8. Does it assume all neighborhoods are global?
9. Does it assume all meetings are public?
10. Does it generate legacy `/es/` URLs that should become canonical Topic Space URLs?

## Required behavior

- Filter inaccessible topics and meetings before rendering.
- Do not reveal private counts or placeholders.
- Do not reveal private reverse relationships.
- Do not email or notify unauthorized users.
- Validate all mutation paths server-side.
- Preserve Dandelion neighborhood-tag behavior for now.
- Keep legacy links functional where required.

## Deliverable

Produce a short repository audit report containing:

- Paths reviewed
- Problems found
- Files changed
- Remaining deferred issues
- Tests performed

## Acceptance criteria

- No known topic- or meeting-producing path bypasses access controls.
- No known mutation path permits invalid meeting or neighborhood assignments.
- Notifications and emails respect visibility.
- Public pages do not reveal private content indirectly.
- The application compiles.

---

# Stage 10 — Rehearse Conversion and Prepare Production Release

## Goal

Validate that the complete upgrade can be applied safely as one production conversion and one deployment.

## Scope

Run the full conversion repeatedly against fresh copies of production.

## Pre-conversion checks

Record:

- Total topics
- Total neighborhoods
- Total meetings
- Number of topics assigned to `Country Interview`
- Current agenda assignments
- Current subscriptions
- Current relationships
- Current curations
- Current campaigns

Confirm:

- Exactly one expected `Country Interview` neighborhood exists.
- Production data matches migration assumptions.
- A current database backup and restore process works.

## Conversion rehearsal

For each rehearsal:

1. Restore a fresh production copy.
2. Run the migration.
3. Deploy the new application build.
4. Run database validation queries.
5. Run automated tests.
6. Perform manual access and workflow tests.
7. Record failures.
8. Fix the application or migration.
9. Repeat from a fresh restore.

Do not continue testing from a partially repaired converted copy when validating the full release process.

## Required validation

### Data

- Topic count unchanged
- All topics assigned to one Topic Space
- All neighborhoods assigned to one Topic Space
- All meetings assigned to one Topic Space
- Country Interview neighborhood removed
- Country Interview topics moved to Building Bridges
- Moved topics have no neighborhoods
- Existing meetings assigned to Emerging Standards

### Public access

- Anonymous users see Emerging Standards
- Anonymous users see Building Bridges
- Anonymous users do not see Opportunity Nursery
- Public topic and meeting detail pages work
- Public meetings may include topics across public spaces

### Private access

- Nursery members can see Nursery topics and meetings
- Nursery nonmembers cannot see Nursery content
- Private direct links are denied
- Private meetings accept only Nursery topics
- Public topics cannot be added to private meetings

### Legacy compatibility

- `/es/topics` works
- Existing `/es/topic/{id}` links work
- Moved Building Bridges topics redirect correctly
- Existing meeting links remain usable where expected

### Leakage tests

- Public topics do not display private meetings
- Public pages do not display private relationships
- Notifications do not reveal private content
- Search does not return private content to unauthorized users
- Reports and exports obey visibility

## Production deployment procedure

1. Announce or begin maintenance window.
2. Disable application access.
3. Back up the production database.
4. Run the conversion migration.
5. Deploy the new application version.
6. Run validation queries.
7. Perform smoke tests.
8. Re-enable application access.

## Rollback

Rollback should consist of:

- Disable the application.
- Restore the pre-upgrade database backup.
- Redeploy the prior application version.
- Re-enable access.

Because no in-flight activity needs to be preserved, rollback does not need to merge post-upgrade data.

## Acceptance criteria

- The full process succeeds repeatedly from a fresh production restore.
- All validation checks pass.
- The deployment and rollback procedures are documented.
- The final application build is ready for one-step production release.

---

# 3. Recommended Commit Sequence

A practical commit sequence is:

1. `docs: add Topic Spaces architecture and implementation stages`
2. `schema: add Topic Space and membership models`
3. `migration: convert existing data to Topic Spaces`
4. `security: centralize Topic Space access control`
5. `routing: add Topic Space navigation and legacy redirects`
6. `admin: add Topic Space and membership administration`
7. `topics: scope topics and neighborhoods by Topic Space`
8. `meetings: enforce Topic Space meeting rules`
9. `security: audit cross-cutting topic and meeting exposure`
10. `release: add conversion validation and deployment procedure`

The exact commit structure may change if repository dependencies require combining closely related work, but each commit should remain focused and reviewable.

---

# 4. Definition of Completion

The Topic Spaces upgrade is complete when:

- The final schema is in place.
- The complete conversion succeeds against production data.
- Every topic, neighborhood, and meeting belongs to one Topic Space.
- Topic Space visibility and membership are enforced centrally.
- Public and private content never mix in a meeting.
- Public interfaces never disclose private content.
- Topic Space administration is operational.
- Private membership administration is operational.
- Legacy Emerging Standards links continue to work.
- Country Interview topics are correctly converted to Building Bridges.
- All cross-cutting topic and meeting paths have been reviewed.
- The upgrade can be deployed and rolled back through documented procedures.
