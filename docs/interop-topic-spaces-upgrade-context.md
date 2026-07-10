# InteropHub Topic Spaces Upgrade Context

## 1. Purpose

This document defines the architectural context for upgrading InteropHub from an Emerging Standards-focused application into a platform organized around **Topic Spaces**.

It is intended to be referenced by every implementation prompt and development stage. It establishes the decisions that are already settled, the assumptions behind the upgrade, and the boundaries of the current work.

This is not a detailed execution plan. Individual implementation prompts may address the work in stages, but all stages must remain consistent with this context.

---

## 2. Core Concept

InteropHub will become a platform containing multiple governed Topic Spaces.

A Topic Space establishes the context in which a topic exists, including:

- Purpose
- Audience
- Visibility
- Membership requirements
- Available neighborhood tags
- Meeting behavior
- Administrative boundaries

A topic belongs to exactly one Topic Space.

Topic Spaces are not tags, and topics do not belong to multiple Topic Spaces. Relationships between topics in different spaces are represented through topic links rather than multiple space assignments.

The initial Topic Spaces are:

1. **Emerging Standards**
   - Public
   - Default space for existing InteropHub behavior
   - Contains the current Emerging Standards topic catalog

2. **Building Bridges**
   - Public
   - Contains country and international organization topics
   - Initially populated from topics currently tagged `Country Interview`

3. **AIRA Opportunity Nursery**
   - Private
   - Invitation-only
   - Supports internal strategic opportunity review and leadership discussion

Additional Topic Spaces may be created later.

---

## 3. Architectural Invariants

The following rules are fixed for this upgrade.

### 3.1 Topic ownership

- Every topic belongs to exactly one Topic Space.
- Topic Space membership is stored directly on the topic.
- Do not create a many-to-many topic-to-space mapping table.
- A topic may be moved from one Topic Space to another by an administrator.
- No movement audit or prior-space history is required.

### 3.2 Topic Space visibility

- A Topic Space is either `PUBLIC` or `PRIVATE`.
- Visibility is selected when the Topic Space is created.
- Visibility cannot be changed after creation.
- A Topic Space cannot be deleted.
- A Topic Space may be marked inactive so it no longer accepts new topics or meetings.
- The stable Topic Space code cannot be changed after creation.
- The label, description, display order, and active status may be edited.

### 3.3 Membership

- Public Topic Spaces do not require membership records.
- Private Topic Spaces require membership for normal users to view and participate.
- The system does not need to prohibit membership records for public spaces, but public visibility must never depend on membership.
- System administrators may access all Topic Spaces.
- The initial membership model may remain simple, using roles such as `MEMBER` and `ADMIN`.

### 3.4 Neighborhood tags

- The existing neighborhood system remains the topic-tagging system.
- Neighborhoods become scoped to one Topic Space.
- A neighborhood may be assigned only to topics in the same Topic Space.
- A topic may have multiple neighborhoods within its Topic Space.
- Neighborhood names need only be unique within a Topic Space.
- Changing a topic's Topic Space requires clearing or explicitly remapping its neighborhoods.
- Topic Spaces and neighborhoods serve different purposes:
  - Topic Space establishes context, visibility, and governance.
  - Neighborhoods describe subject matter and support browsing and filtering.

### 3.5 Meetings

- Every meeting belongs to one host Topic Space.
- The meeting's visibility is inherited from its host Topic Space.
- Meeting visibility is not stored or edited independently.
- The host Topic Space cannot be changed after the meeting is created.

Meeting topic-selection rules are:

- A meeting hosted by a public Topic Space may include topics from any public Topic Space.
- A meeting hosted by a private Topic Space may include topics only from that same private Topic Space.
- Public and private topics may never be mixed in one meeting.
- A private meeting may not include a public topic.
- A private meeting may not include a topic from another private Topic Space.

Meeting notes inherit meeting visibility:

- Notes from public meetings are public.
- Notes from private meetings are visible only to authorized members of the host Topic Space.
- A public topic must never acquire private meeting notes or private meeting references.

### 3.6 Access control

Topic and meeting access must be centralized rather than implemented independently in each servlet or page.

The implementation should provide a common access layer that can answer questions such as:

- Can this user view this Topic Space?
- Can this user administer this Topic Space?
- Can this user view this topic?
- Can this user view this meeting?
- Which Topic Spaces are visible to this user?

All topic- and meeting-producing paths must use the same access rules, including:

- Topic lists
- Topic detail pages
- Search
- Meetings and agendas
- Relationships
- Curations
- Campaigns
- Follow and subscription displays
- Notifications
- Exports
- Administrative reports
- Dandelion synchronization
- Future recommendations

Public interfaces must never expose the existence, title, metadata, relationship, meeting, or notes of private content.

### 3.7 Legacy routing

Emerging Standards remains the default Topic Space for legacy behavior.

Existing bookmarked routes must continue to work:

- `/es/topics` should continue to open Emerging Standards.
- Existing `/es/topic/{id}` links must still resolve.
- If an old Emerging Standards URL references a topic moved to Building Bridges, the application should resolve the topic and redirect to its canonical Topic Space URL rather than fail.

New neutral or Topic Space-aware routes may be introduced, such as:

- `/spaces/{space-code}/topics`
- `/spaces/{space-code}/topic/{id}`

---

## 4. Data Conversion Assumptions

The upgrade will be deployed as one application upgrade with one conversion script.

The development work may be divided into multiple implementation stages, but there will be no staged production rollout and no intermediate production compatibility state.

The conversion script will:

1. Create the initial Topic Spaces:
   - Emerging Standards
   - Building Bridges
   - AIRA Opportunity Nursery

2. Assign every existing topic to Emerging Standards.

3. Assign every existing neighborhood to Emerging Standards.

4. Locate the existing `Country Interview` neighborhood in production data.

5. Identify every topic currently assigned to `Country Interview`.

6. Move those topics to Building Bridges.

7. Remove all neighborhood assignments from the moved topics.

8. Delete the `Country Interview` neighborhood.

9. Assign all existing meetings to Emerging Standards.

10. Enforce required foreign keys after the data has been converted.

After conversion:

- Every topic must have exactly one Topic Space.
- Every neighborhood must have exactly one Topic Space.
- Every meeting must have exactly one host Topic Space.
- No Building Bridges topic should have a neighborhood assignment.
- The `Country Interview` neighborhood should no longer exist.
- The number of topics must remain unchanged.

No migration audit or movement history is required.

---

## 5. Operational Assumptions

The following assumptions justify a direct conversion:

- InteropHub currently contains approximately 120 topics.
- Usage is low.
- The system can be temporarily disabled during deployment.
- Preserving requests in flight is not required.
- A copy of production data is available for repeated conversion rehearsals.
- The upgrade can be tested multiple times against a restored production copy.
- If the conversion encounters an unexpected state, it should fail clearly rather than attempt a partial repair.
- The application does not need to support both the old and new schemas at the same time.

The production deployment is expected to follow this pattern:

1. Disable application access.
2. Back up the production database.
3. Run the conversion.
4. Deploy the new application version.
5. Run validation checks.
6. Re-enable access.

---

## 6. In Scope

The following work is part of this upgrade.

### 6.1 Database and domain model

- Add the Topic Space catalog.
- Add one Topic Space foreign key to each topic.
- Add Topic Space membership.
- Add one Topic Space foreign key to each neighborhood.
- Add one host Topic Space foreign key to each meeting.
- Add required indexes, foreign keys, and uniqueness constraints.
- Update Hibernate mappings and domain models.

### 6.2 Conversion

- Create and test the one-time production conversion script.
- Perform the Country Interview to Building Bridges migration.
- Remove neighborhood assignments from moved Building Bridges topics.
- Delete the Country Interview neighborhood.
- Assign all existing meetings to Emerging Standards.
- Add validation queries and failure checks.

### 6.3 Access control

- Create a centralized Topic Space access service or equivalent query boundary.
- Apply access rules to all topic and meeting retrieval paths.
- Protect direct URLs as well as user-interface navigation.
- Prevent reverse-link and derived-display leakage of private content.

### 6.4 Topic Space administration

- Add an administration page for Topic Spaces.
- Create Topic Spaces.
- Edit label, description, display order, and active status.
- Prevent deletion.
- Prevent changes to stable code and visibility.
- Manage members of private Topic Spaces.

### 6.5 Topic administration

- Require exactly one Topic Space for every topic.
- Filter available neighborhoods by the selected Topic Space.
- Clear or require remapping of neighborhoods when a topic changes spaces.
- Prevent topic moves that would leave invalid meeting assignments.

### 6.6 Neighborhood administration

- Require each neighborhood to belong to one Topic Space.
- Filter neighborhood lists and topic browsing by Topic Space.
- Update imports to accept one Topic Space and validate neighborhoods against it.

### 6.7 Meeting and agenda behavior

- Require a host Topic Space for every meeting.
- Inherit meeting visibility from the host Topic Space.
- Prevent host Topic Space changes after meeting creation.
- Enforce topic-selection rules in both the user interface and server-side save logic.
- Protect private meeting pages, notes, agendas, and notifications.
- Prevent public topics from displaying private meeting references.

### 6.8 Navigation and compatibility

- Add Topic Space-aware topic navigation.
- Preserve legacy Emerging Standards routes.
- Redirect moved topics to their canonical Topic Space routes.
- Keep Emerging Standards as the default legacy context.

### 6.9 Cross-cutting regression review

Review and update all features that may surface topics or meetings, including:

- Follow and unsubscribe workflows
- Meeting requests
- Relationships
- Curations
- Campaigns
- Notifications and email queues
- Search
- Imports and exports
- Dandelion synchronization
- Administrative reporting
- Deep links

### 6.10 Testing

- Rehearse the full conversion against restored production copies.
- Validate anonymous public access.
- Validate private member access.
- Validate private nonmember denial.
- Validate public and private meeting topic selection.
- Validate legacy bookmarks.
- Validate that no private data leaks through relationships, meetings, notes, notifications, or derived lists.

---

## 7. Out of Scope

The following items are explicitly deferred.

### 7.1 Per-space topic schemas

Existing topic fields such as stage, policy status, topic type, and Confluence URL will remain on the common topic model even when they are not meaningful for every Topic Space.

This upgrade will not introduce separate extension tables, configurable field definitions, or per-space forms.

### 7.2 Richer Building Bridges or Nursery tags

Building Bridges and AIRA Opportunity Nursery may initially have no neighborhood tags.

Designing new tag catalogs for those spaces is deferred.

### 7.3 Workflow redesign

This upgrade will not create separate workflow engines, status systems, or lifecycle rules for each Topic Space.

Existing workflows remain in place unless a change is required for access control or Topic Space compatibility.

### 7.4 Repository or document management

InteropHub remains an index and engagement layer.

It will not become a document repository, file preview system, Dropbox replacement, or Confluence replacement.

### 7.5 Topic movement history

No audit trail, prior-space history, or migration history is required for topics moved between Topic Spaces.

### 7.6 Staged production rollout

There will be no dual-schema period, feature flag rollout, incremental production migration, or temporary many-to-many compatibility model.

### 7.7 Preservation of in-flight activity

The deployment does not need to preserve requests being processed during the upgrade window.

### 7.8 Public/private conversion

Changing an existing Topic Space from public to private or private to public is not supported.

If a different visibility model is needed later, a new Topic Space should be created and topics deliberately moved.

### 7.9 Cross-visibility meetings

The following are not supported:

- Private meetings containing public topics
- Public meetings containing private topics
- Private meetings containing topics from another private Topic Space
- Meetings with independently editable visibility

### 7.10 Advanced role and permission systems

The initial implementation does not require granular permissions beyond system administration, private-space membership, and basic private-space administration.

### 7.11 Recommendation redesign

Topic recommendation and discovery algorithms are not part of this upgrade, except that any existing recommendation behavior must obey Topic Space access rules.

### 7.12 Dandelion taxonomy redesign

Topic Spaces will not replace neighborhood names as Dandelion project tags during this upgrade.

Dandelion behavior may be reviewed separately after Topic Spaces are operational.

### 7.13 Broad user-interface redesign

The work may introduce Topic Space navigation and required administrative controls, but it is not intended as a complete visual redesign of InteropHub.

---

## 8. Expected End State

After the upgrade:

- InteropHub supports multiple governed Topic Spaces.
- Every topic belongs to one and only one Topic Space.
- Every neighborhood belongs to one Topic Space.
- Every meeting is hosted by one Topic Space.
- Emerging Standards and Building Bridges are public.
- AIRA Opportunity Nursery is private and membership-controlled.
- Public meetings may draw from public topics across public Topic Spaces.
- Private meetings remain fully contained within their private Topic Space.
- Public and private content never mix in a meeting.
- Legacy Emerging Standards links continue to work.
- Existing Country Interview topics have moved cleanly into Building Bridges.
- Access decisions are enforced consistently through a centralized mechanism.
- The system is ready for future per-space refinement without requiring that refinement in this upgrade.

---

## 9. Guidance for Implementation Prompts

Every Copilot prompt for this project should reference this document and include the following expectations:

- Implement only the requested development stage.
- Do not change the settled architectural rules.
- Do not create a many-to-many topic-to-space model.
- Do not add audit or migration-history structures.
- Do not introduce staged production compatibility.
- Do not make Topic Space visibility editable.
- Do not allow public and private topics to mix in meetings.
- Enforce access and meeting rules server-side, not only in the user interface.
- Identify every changed file.
- Compile or otherwise validate the work.
- Report assumptions, unresolved issues, and any repository behavior that conflicts with this context.
