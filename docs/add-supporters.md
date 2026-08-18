# Implement Supporters in InteropHub

Please implement a new **Supporters** concept in InteropHub.

Before making changes, review the repository documentation, `CLAUDE.md` files, database migration guidance, UI conventions, routing conventions, authorization patterns, and existing implementations of similar features. In particular, use the existing **Champions** functionality on topic pages as the primary UI/reference pattern where applicable.

Do not introduce new architectural patterns where an established InteropHub pattern already exists.

## Concept

A **Supporter** is an organization that has explicitly agreed to be publicly identified as supporting efforts related to one or more interoperability topics.

Support is deliberately broad. It might represent funding, staffing, technical expertise, implementation, promotion, coordination, or another form of assistance. InteropHub should **not** classify the kind or amount of support.

This is a relationship between an organization-like Supporter record and a Topic. It is **not a project relationship**.

Do not add:

- projects;
- grants;
- funding amounts;
- funding types;
- project dates;
- deliverables;
- support categories;
- levels of support;
- contractual information.

A Supporter is system-wide and should **not** be modeled as an Emerging Standards-specific entity. Emerging Standards is simply the first Topic Space using this functionality.

There is currently no general Organization entity in InteropHub. Do **not** create one or add an organization/account foreign key as part of this work. We may connect Supporters to a future Organization model later.

---

# 1. Database Model

Add a reusable `Supporter` entity/table using the repository's existing naming, ID, timestamp, and migration conventions.

Conceptually it needs:

- `id`
- `short_name`
- `full_name`
- `description`
- `website_url`
- `active`
- normal created/updated audit metadata used elsewhere in the application

### Field behavior

**Short name**
- Required.
- Compact public name.
- Used in chips and other space-constrained displays.
- Examples: `AIRA`, `Pfizer`, `SNOMED International`.

**Full name**
- Required.
- Formal/full organizational name.

**Description**
- Optional.
- Public-facing short description of the organization.

**Website URL**
- Optional.
- Public URL for the supporter.

**Active**
- Required.
- Default `true`.

An inactive Supporter:

- remains in the database;
- can still be edited by administrators;
- cannot be newly assigned to topics;
- does not appear on public Supporters pages;
- does not appear publicly on topic pages;
- retains its existing Topic relationships in the database.

If the Supporter is later made active again, its retained Topic relationships should once again be visible.

There should be **no delete operation for Supporters** in the application.

Use normal repository conventions for validation and reasonable field lengths. Do not create unnecessary fields.

## Topic-Supporter Relationship

Add a many-to-many relationship between Topics and Supporters.

Conceptually:

`Topic <-> TopicSupporter <-> Supporter`

Requirements:

- one Topic may have many Supporters;
- one Supporter may support many Topics;
- the same Supporter cannot be associated with the same Topic twice;
- enforce the Topic + Supporter uniqueness at the database level if consistent with existing migration practices;
- removing support deletes only this relationship;
- deleting/removing a relationship must never delete the Supporter;
- making a Supporter inactive must not delete its Topic relationships.

Follow existing FK behavior and table naming conventions. Topic deletion can clean up relationship rows according to the normal application convention, but Supporter records themselves should not be cascaded away.

Do not add a status or support-type field to the relationship. The relationship either exists or does not.

---

# 2. Public Emerging Standards Navigation

Add a new top-level menu item within Emerging Standards:

**Supporters**

It should appear alongside the existing:

**Topics | Meetings | Supporters**

Use the application's existing navigation conventions.

The expected public route is conceptually:

`/es/supporters`

If repository routing conventions indicate a slightly different implementation, follow those conventions while preserving the intended URL structure where practical.

The Supporters menu item should appear even when there are currently zero Supporters.

---

# 3. Public Emerging Standards Supporters Page

Create a public Supporters page for Emerging Standards.

This page is specific in its wording to the Emerging Standards Topic Space even though the underlying Supporter model is generic.

## Introductory explanation

Use wording approximately like:

> Emerging Standards tracks interoperability topics that may be important to the immunization community. Inclusion of a topic in Emerging Standards does not mean that AIRA, CDC, or another organization funds, endorses, or supports work related to that topic.
>
> Organizations listed here have explicitly agreed to be publicly identified as supporting efforts related to one or more topics. Support may take different forms, and InteropHub does not distinguish among funding, technical assistance, staffing, implementation, promotion, or other forms of support.

Keep this presentation concise and consistent with the rest of the application's visual design.

## When Supporters exist

List **active Supporters alphabetically by short name**.

For each Supporter:

- display the short name prominently;
- display the full name where useful if it differs from the short name;
- optionally display its description and website using normal application patterns;
- list all Topics it supports;
- sort those Topics alphabetically by topic title;
- link each Topic to its normal topic page.

The Supporters page is **not limited to topics currently displayed on the main Emerging Standards Topics board**. If a Supporter is associated with an Emerging Standards topic, it belongs on this page even if that topic is not currently visible in a particular board/status view.

Only active Supporters should appear publicly.

## Empty state

When no active Supporters exist, do not show an empty listing.

Instead show an intentional empty state approximately like:

> No organizations are currently listed as public supporters.
>
> Organizations interested in being publicly identified as supporting one or more Emerging Standards topics should contact AIRA.

Use whatever existing contact mechanism/pattern is already available in the application. Do not invent an email address if one is not already configured or established.

Be careful with wording. Do **not** say that there are "no supporters." The system only knows that no organizations are currently **listed as public supporters**.

---

# 4. Topic Page — Supporter Chip

On the existing Emerging Standards topic display (`/es/topic/...`), add Supporter information to the summary chips near the top.

Use the existing **Champions** chip implementation as the design and technical pattern.

Behavior:

- If the Topic has one or more **active** Supporters, show a `Supporters` chip.
- List all active Supporters using their **short names**.
- Follow the Champions behavior for multiple values, wrapping, formatting, responsive behavior, etc.
- Sort supporter names alphabetically.
- If there are no active Supporters, do not render a Supporters chip.
- Inactive Supporters must not appear publicly.

Do not add logos.

Do not modify the main Emerging Standards Topics board to show Supporter information.

---

# 5. Topic Page — Supporters Section

On `/es/topic/...`, add a **Supporters** section immediately after the existing **Overview** section.

Behavior:

- Render the section only when at least one active Supporter is associated with the Topic.
- Do not render an empty panel.
- Display the active Supporters alphabetically.
- Use their public names appropriately; the short name should be the primary compact identity.
- If useful within the existing UI conventions, show the full name, description, and/or website without making the section visually heavy.
- Do not show inactive Supporters.

Keep this simple. The purpose is to answer:

> Which organizations have publicly agreed to be identified as supporting efforts related to this topic?

Do not introduce project or funding information here.

---

# 6. Topic Administration — Manage Supporters

For administrators, under the existing **Manage This Topic** area on the topic page, add:

**Supporters**

Follow the same routing and management patterns used by the other topic-management options.

The Supporters management page should allow an administrator to manage Topic-Supporter relationships.

## Current Supporters

Show all Supporters currently associated with the Topic.

This administrative view should include relationships to inactive Supporters as well, because those relationships still exist.

If a related Supporter is inactive, clearly mark it as **Inactive**.

Sort the list alphabetically.

For each associated Supporter provide:

**Remove**

Removal behavior:

- remove the Topic-Supporter relationship immediately;
- do **not** show a confirmation dialog;
- do not delete or modify the Supporter record;
- refresh/update the UI immediately;
- optionally use the application's normal success notification/toast.

There is deliberately no confirmation because the relationship is easy to restore.

## Add Existing Supporter

Provide a way to add an existing active Supporter.

Use the application's normal searchable select/autocomplete pattern if one exists.

Requirements:

- only active Supporters should be available for new assignment;
- do not show Supporters already associated with the Topic as available choices;
- prevent duplicate relationships;
- after selection, create the relationship and update the page.

## Add New Supporter

From this same workflow, allow the administrator to create a Supporter that does not yet exist.

The form should collect:

- Short Name — required
- Full Name — required
- Description — optional
- Website URL — optional
- Active — default true

After successful creation:

1. create the Supporter;
2. associate it with the current Topic;
3. return/update the Topic Supporters management interface.

Use existing form validation, error display, and modal/page conventions.

Do not create a second Supporter if a validation or concurrency condition indicates the record already exists.

---

# 7. Emerging Standards Admin — Supporters

Under:

`/hub/admin/es`

add a new admin option/page called:

**Supporters**

This page manages the Supporter records themselves rather than their relationships to individual Topics.

Remember that Supporters are a **system-wide model** even though the first administration entry point is under Emerging Standards.

Do not add an `es_id`, Topic Space ID, or other Emerging Standards ownership field to the Supporter table.

## Supporter Administration Page

Display existing Supporters alphabetically.

Make it easy to distinguish:

- Active
- Inactive

An administrator should be able to:

- add a Supporter;
- edit an existing Supporter;
- change short name;
- change full name;
- change description;
- change website URL;
- make a Supporter inactive;
- reactivate an inactive Supporter.

Do **not** provide a Delete option.

When a Supporter is made inactive:

- retain all existing Topic relationships;
- immediately stop displaying it publicly;
- remove it from the choices for new Topic associations.

When it is reactivated:

- retained relationships become publicly visible again;
- it becomes available for new Topic associations.

Use the application's normal admin table/list and form patterns.

---

# 8. Public vs. Administrative Behavior

Keep these behaviors distinct.

### Public

Public pages should only consider **active Supporters**.

That includes:

- `/es/supporters`;
- topic Supporters chips;
- topic Supporters sections.

### Administrative

Administrators should still be able to see inactive Supporters.

That includes:

- `/hub/admin/es` Supporters administration;
- existing Topic-Supporter relationships involving an inactive Supporter.

Inactive Supporters should **not** be selectable when adding a new relationship.

---

# 9. Sorting

Use predictable alphabetical ordering throughout.

- Public Supporters page: Supporters alphabetically by `short_name`.
- Topics beneath each Supporter: alphabetically by topic title.
- Topic Supporter chip values: alphabetically by Supporter short name.
- Topic Supporters section: alphabetically.
- Topic Supporter admin relationships: alphabetically.
- Supporter admin page: alphabetically.

Use the application's established case-insensitive sorting behavior if one exists.

---

# 10. Authorization

Follow existing InteropHub security conventions.

Public users may:

- view the Emerging Standards Supporters page;
- view Supporter information associated with public topics.

Only appropriately authorized administrators may:

- create Supporters;
- edit Supporters;
- activate/inactivate Supporters;
- add Supporters to Topics;
- remove Supporters from Topics.

Reuse the authorization checks already used for the corresponding Topic and Emerging Standards administrative functions.

Do not create a new authorization model specifically for this feature unless required by the existing architecture.

---

# 11. UI Constraints

Keep this intentionally restrained.

Do not add:

- logos;
- supporter shading on the main Topics board;
- supporter filters on the main Topics board;
- project cards;
- funding badges;
- support-type badges;
- counts or rankings intended to compare Supporters.

The existing Topics board should remain visually unchanged.

Supporters should appear only where explicitly described above.

---

# 12. Testing

Add/update tests consistent with the existing project's testing strategy.

At minimum verify:

### Data

- Supporter can be created.
- Supporter can be edited.
- Supporter can be made inactive and active again.
- Supporter cannot be associated with the same Topic twice.
- Removing a Topic-Supporter relationship does not delete the Supporter.
- Inactivating a Supporter does not remove its Topic relationships.

### Public behavior

- Active Supporters appear on `/es/supporters`.
- Inactive Supporters do not.
- Supporters and Topics are alphabetically sorted.
- Correct empty state appears with zero active Supporters.
- Topic chip appears when active Supporters exist.
- Topic chip is absent when none exist.
- Topic Supporters section appears after Overview when appropriate.
- No empty Supporters section appears.
- Inactive Supporters disappear from topic public displays.

### Administration

- Existing active Supporter can be assigned to a Topic.
- New Supporter can be created and assigned from Topic management.
- Relationship removal is immediate and requires no confirmation.
- Inactive Supporters cannot be newly assigned.
- Existing relationship to an inactive Supporter remains visible administratively.
- Admin can reactivate the Supporter and the existing relationship becomes publicly visible again.
- Supporter records cannot be deleted through the admin UI.

---

# 13. Implementation Approach

Before coding:

1. Inspect repository instructions and architecture documentation.
2. Find the existing Topic entity and migration conventions.
3. Find the Champions implementation and reuse its Topic-page patterns.
4. Find the existing `/hub/admin/es` administration structure.
5. Find existing admin CRUD/list/form patterns.
6. Find the application's established public navigation and empty-state patterns.
7. Identify existing authorization checks to reuse.

Then implement the feature end-to-end:

- migration/schema;
- persistence/domain model;
- service/repository layer;
- public queries;
- administrative operations;
- routes/controllers;
- templates/components;
- navigation;
- tests.

Prefer small changes that fit the existing architecture over introducing abstractions intended for hypothetical future requirements.

The model should be generic enough for Supporters to be used in another Topic Space later, but **do not implement cross-Topic-Space supporter UI or administration now**.

After implementation, provide a concise summary of:

- database changes;
- routes/pages added;
- major UI changes;
- tests added;
- any design decisions where the existing repository architecture required deviating from these instructions.