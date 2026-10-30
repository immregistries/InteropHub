# InteropHub Initiatives and Small Groups Design

**Status:** Design agreed in discussion with Nathan, October 2026. Phases 1 and 2 are implemented. Phase 3 uses a Small Group participant list; the meeting series is compared with that list and is not the list itself.

## Purpose

AIRA staff organize multi-month efforts, such as a year-long proof of concept, that recruit people from many organizations and run one or more SISC Small Groups along the way. Today that organizing work happens outside InteropHub. The first example is a spreadsheet Sabrina started for the Risk-Based Forecasting Proof of Concept. It has these columns:

- point of contact
- email
- organization
- organization type (EHR, Jurisdiction, IIS Vendor, CDS)
- free-text notes about where each conversation stands

InteropHub can't hold that list today:

- **Topic followers** (`es_subscription`) show interest, not recruitment or commitment.
- **Meeting series members** (`es_topic_meeting_member`) list who is in a recurring meeting, not who an effort is drawing from.
- The **Connectathon workspace tables** (`connect_workspace`, `workspace_*`) were never finished. They are empty and tied to the legacy `ig_topic` table.

A second example is the Indiana School Immunization Interoperability Project. Its Smartsheet project plan tracks partner organizations first (school information system vendors, testing partners, the state). Their contacts are found afterward and sometimes change. Its partner list also lives in a separate "Stakeholder Matrix" Smartsheet.

This design adds an **Initiative** module. An initiative is a named, managed effort that sits above topics. It keeps a roster of organizations and people over time, organizes one or more Small Groups, and gives its organizer checklists for setting up and closing each piece of work. The proposal is that the initiative roster **replaces the Stakeholder Matrix**, so partner organizations and contacts are kept in one place.

InteropHub is not a project-management tool. Contracts, travel and venue logistics, task dependencies, percent-complete tracking and internal staff meetings stay in Smartsheet. An initiative links to those tools instead of copying them.

The Small Group process itself is defined in the SISC *Small Group Roadmap* (`Shared AIRA Files/Committees/Standards & Interoperability Committee/8 - Small Groups/Small Group Roadmap`). This design does not restate that process. It decides how InteropHub supports it.

## Conceptual Model

```
Initiative ── primary topic (exactly one)
   │             ↑ every joined member follows it
   │
   ├── Managers: admin / editor / viewer
   ├── Organizations: prospect → contacted → interested → committed  (also declined, withdrawn)
   │             may be listed before anyone there is known
   ├── Members: prospect → invited / applied → joined  (also declined, not accepted, left)
   │             each usually linked to one of the initiative's organizations
   ├── Links: Smartsheet plan, Slack channel, document share, GitHub board, …
   ├── Checklist (from the Initiative template)
   │
   └── Small Groups (zero or more)
          ├── topic (exactly one; may or may not be the primary topic)
          ├── participant list, drawn from the initiative roster
          │     └── an email or a linked account follows this topic
          ├── the topic's meeting series, compared with the participant list
          │     └── meetings → agendas → other topics (unchanged)
          └── Checklist (from the Small Group template)
```

### Topics are public and permanent; initiatives organize on top of them

Topics, topic relationships, topic curation, meeting series, meetings, agendas, notes and recorded outcomes stay exactly as they are. Organizing topics, including one topic curating others or a series' agendas bringing in other topics, stays public and in the topic layer.

An initiative adds an organizing layer above that. It points at topics. Topics don't need to know about it, except that the primary topic advertises the initiative once it is visible. The Champion of a topic that only appears on an agenda may never hear of the initiative, and that's fine.

Access to topics doesn't change. Initiative roles grant no rights over topics. If an initiative organizer needs to schedule meetings on a topic, they are given that access through the existing topic mechanisms, separately.

### Initiative

A named effort, such as "Risk-Based Forecasting Proof of Concept." It has:

- a name, summary, description, and optional start and end dates;
- exactly one **primary topic**: the public place where the initiative is announced and where its members follow along;
- a **status**: `PLANNING`, `ACTIVE`, `CLOSED` or `ARCHIVED`;
- a **visibility**: `PRIVATE` (only managers can see it) or `LISTED` (shown under a top-level **Initiatives** menu and advertised on the primary topic);
- a **join policy**:
  - `INVITE_ONLY`: managers add and invite people;
  - `APPLY`: people can apply, and a manager accepts them;
  - `OPEN`: people can join directly.

Visibility and join policy are separate settings. A listed initiative can still be invite-only.

An initiative also has a short list of **links** (label + URL), such as the Smartsheet project plan, the Slack channel, the shared document folder or a GitHub board. These are where the project's detailed planning and technical work happen. InteropHub points to them and doesn't copy them.

### Initiative managers

Global admins create initiatives and can always manage them. Each initiative has its own list of managers:

| Role | Can do |
|---|---|
| `ADMIN` | Everything below, plus manage the manager list and initiative settings |
| `EDITOR` | Manage members, organizations, Small Groups and checklists; comment |
| `VIEWER` | See the roster, Small Groups and checklists |

Manager roles are about running the initiative. They are separate from member status, which is about a person's participation.

### Members (the roster)

The roster is the pool of people the initiative draws from, before, during and after any Small Group. A person can be listed before they have an InteropHub account. Like `es_subscription`, rows are keyed by email and `user_id` is filled in once the person registers. First and last name are stored separately. An email address is never stored in a name field, on the roster or on the topic follow that joining creates. When no name is known, the name fields stay empty.

A person is shown by account name, then roster name, then email. Linking an account copies the account's first and last name into any blank roster name, including when the link happens later at sign-in.

Managers find people through one search, used everywhere a person is added. Two or more characters match a name, email, or organization. Results are the initiative roster, then InteropHub accounts, then other people already known by email (topic-follow contact names and series registrants), then **Add new** (first name, last name, optional email, and the organization type-ahead). Choosing an account links `user_id` and copies the account name. Choosing a known email copies the contact names when there are any. The search never creates an account. Only initiative editors and global admins can use it, because it shows account emails. The spreadsheet import still has one Point of Contact column; that value is split into first and last name, and a cell that is only an email is stored as the email.

People reach `JOINED` from either direction:

- **Outbound** (staff recruiting): `PROSPECT` → `INVITED` → `JOINED`, or `DECLINED`
- **Inbound** (the person asks): `APPLIED` → `JOINED`, or `NOT_ACCEPTED`
- Anyone joined can later be `LEFT`.

| Status | Meaning |
|---|---|
| `PROSPECT` | Staff think this person should be involved; no commitment either way |
| `INVITED` | Staff have asked this person to join |
| `APPLIED` | The person asked to join (join policy `APPLY`) |
| `JOINED` | The person is a confirmed member of the initiative |
| `DECLINED` | The person said no, or didn't respond |
| `NOT_ACCEPTED` | A manager decided not to accept an application |
| `LEFT` | Was joined; no longer participating |

Status is not restrictive. Managers can move a member to any status. Every change can carry a note (see **Timeline**). Sabrina's spreadsheet notes are status changes with notes ("Emailed to express interest and ask questions").

Contact information and notes are private to managers but are not treated as especially sensitive. InteropHub does not track stipends or other financial details. Anything relevant goes in notes.

Each member records where they came from: added by staff, applied, imported from followers, imported from a spreadsheet, or added from a Small Group.

A member can also have an optional free-text **role in the initiative** (e.g. "product manager," "technical lead," "program manager"), so the roster can do the job of the stakeholder matrix.

### Organizations

Organizations are a shared list used across all initiatives:

- a name and a type;
- empty `external_system` and `external_id` columns, reserved for the planned Zoho integration;
- a type stored as a short code, so new types don't need a schema change. Starting types: `EHR`, `JURISDICTION`, `IIS_VENDOR`, `CDS`, `SCHOOL_INFORMATION_SYSTEM`, `TESTING_VENDOR`, `STATE_AGENCY`, `FEDERAL`, `HEALTH_SYSTEM`, `PHARMACY`, `NATIONAL_ORG`, `CONSULTANT`, `OTHER`.

Adding a new organization has to be quick. Wherever an organization is chosen, the field is a type-ahead with **+ Add new organization**, and there is no separate admin step.

A full person-to-organizations model is left to Zoho. Duplicates such as "HLN" and "HLN Consulting" are expected. A merge tool can wait until Zoho brings a more formal list.

### Participating organizations

Recruitment often starts with an organization, not a person. In the Indiana project, AIRA identified Skyward, PowerSchool and Harmony as needed partners, then looked for the right contact at each one. A contact sometimes changes partway through (at PowerSchool the original contact no longer managed the product, and a colleague took over). So an initiative tracks **organizations as well as people**, each with its own status:

| Status | Meaning |
|---|---|
| `PROSPECT` | This organization should be involved; no contact made yet |
| `CONTACTED` | Outreach has started |
| `INTERESTED` | The organization has said it is interested |
| `COMMITTED` | The organization has agreed to take part |
| `DECLINED` | The organization said no, or didn't respond |
| `WITHDRAWN` | Was committed; no longer taking part |

- An organization can be on the initiative with **no contact yet**. The roster shows "needs a contact."
- A member is usually linked to one of the initiative's organizations: the organization they represent here. Adding a member with a new organization adds that organization to the initiative too.
- An organization's contacts are its linked members. When a contact changes, the old member becomes `LEFT` and the new one is added, and both show up under the organization with their history.
- Organization status and member status are separate. An organization can be `COMMITTED` while a newly named contact is still `INVITED`.
- The representation summary counts **committed organizations** and their types.

Contract status stays in Smartsheet. A note like "contract signed" can go on the organization's history if it's useful.

The existing `supporter` table (organizations shown on topics) is a separate concept and is not changed. Linking supporters to organizations may be worth doing later.

### Following rules

Joining an initiative is a deliberate step beyond following a topic. That solves the problem that following alone says too little.

1. **Joining an initiative means following its primary topic.** When a member becomes `JOINED`, InteropHub creates or restores a follow of the primary topic, if one doesn't already exist. Prospects and invited people do *not* follow automatically, because they haven't agreed to anything.
2. **Taking part in a Small Group means joining the initiative and following the Small Group topic.** Staff add participants from the people search. Someone who is not on the roster is added as `JOINED`, with `member_source` `SMALL_GROUP` and a timeline entry, and then made a participant. No notification is sent, and the person does not need to sign in. An account is never created.
   - A roster member who is not yet `JOINED` becomes `JOINED`, with a timeline entry, because taking part means joining the initiative. That also follows the primary topic, under rule 1, and with the same notification behavior (the follow is recorded and the follower-added email is not sent).
   - A participant with an email, or a linked account, follows the Small Group's topic the same way. Prospects who are only on the roster do not follow that topic.
   - Removing a participant does not unfollow the topic. The follow stays until the person changes it.
   - Registering for the meeting series is separate. Self-registration through `EsRegisterForMeetingServlet` still follows the topic, as it does today. This phase does not add or remove series members.
3. **Explicitly unfollowing the primary topic means leaving the initiative.** If a joined member uses the follow toggle to unfollow the primary topic, InteropHub asks: "This will remove you from the *X* initiative. Continue?" If confirmed, the member becomes `LEFT`, with an automatic note.
4. **Email preference is separate from following.** The topic-roles redesign (`docs/topic-roles-implementation-prompt.md`) separates a person's relationship to a topic from whether they receive email. Once that lands, a member can turn off topic emails without leaving. Unsubscribe links in emails must keep working and must not silently remove anyone from an initiative.

**Until the email split lands:** some unsubscribe paths (email links, the account page, unsubscribe-all) will set a follow to `UNSUBSCRIBED` without going through the confirmation. Don't block those paths and don't remove the member automatically. Instead, flag the member on the roster ("joined, but no longer follows the primary topic") and let the organizer decide.

### Small Group

A Small Group is one cycle of the SISC Small Group process, run within an initiative. Because the existing topic and meeting structures do most of the work, the record itself is thin:

- the initiative it belongs to;
- **exactly one topic**. This may be the primary topic or a different one, and it may curate or bring in other topics through the usual mechanisms;
- the topic's **meeting series** (`es_topic_meeting`) used for its meetings;
- a name (e.g. "Cycle 1: Guidance and Tests");
- **status**: `PLANNING`, `RECRUITING`, `ACTIVE`, `HANDOFF`, `CLOSED` or `CANCELLED`;
- **cycle dates**: cycle start, working day, closeout, and a break note;
- primary product areas (guidance, tests, software);
- a link to the charter;
- a description.

The cycle dates follow the roadmap's language: four working weeks, with the working day as a fixed 25-hour window that contains about 6–8 hours of actual group work. Older plans that describe "a 6–8 hour working session" mean the same thing.

**Only public meetings are tracked.** Small Group meetings are public and run in the topic's meeting series. Project meetings with a state partner or funder, one-on-one partner meetings and internal staff meetings are not tracked in InteropHub.

**Each Small Group has its own participant list.** Some people will never sign in, register, or attend a meeting, so the meeting series cannot be the group list. Staff add and remove participants directly. Adding someone does not email them and does not require an account. The page opens with a summary (name, status, topic, series, cycle, working day, product areas, and charter when one is set). Break and description show only when they are filled in. **Edit details** holds the cycle fields. Change status stays a separate action.

- A participant is an initiative member with an optional free-text role or team, and a status of `ACTIVE` or `REMOVED`. The role is edited in place.
- Adding someone who is not on the roster creates a `JOINED` roster row with `member_source` `SMALL_GROUP`. Adding a member who is not yet `JOINED` marks them `JOINED`.
- A participant with an email, or a linked account, follows the Small Group topic. The follow stores a name only when one is known.
- Removing a participant leaves that follow in place.
- The participant table shows name, organization, email, role, whether they are registered, and whether they follow the topic. Registration is `APPROVED` only. Each person who is not registered has a copyable registration link, and so does the group.

**People to bring in** lists everyone who is not already an active participant: series registrants (`APPROVED`, with `REQUESTED` shown as requested and not counted as registered), followers of the Small Group topic, and joined initiative members. Filters are registered, follows the topic, and on the initiative. **Add** puts that person on the participant list in one step. The list is paged because a topic can have many followers.

**The series is compared with the participant list.** Only an `APPROVED` registration counts as registered. `REQUESTED` is shown and is not counted. This phase does not send email and does not change series membership.

Later cycles on the same topic reuse the series. A new Small Group can copy participants from an earlier one. Teardown still includes a step to review who should stay registered. Because AIRA won't run unrelated meetings in the same series while a Small Group is active, sharing the series across cycles does not conflict with other uses.

### Checklists

Both initiatives and Small Groups have checklists that help the organizer know what to do next. They don't enforce anything: no item blocks another, and items can be done in any order.

**Items.** Each checklist item has:

- a phase, title and optional guidance (which can link to the relevant roadmap section);
- **status**: `OPEN`, `DONE` or `NOT_APPLICABLE`. Any status can change to any other, including reopening a done item, and every change can carry a note;
- an optional **assignee**, defaulting to the initiative's managers;
- an optional **due date**, set relative to dates on its owner (see **Templates**) or entered directly;
- a timeline of comments and status changes.

**Editing should feel like a spreadsheet.** The template is only a starting point. Each checklist belongs to its initiative or Small Group, and the organizer can change it at any time without a separate edit mode:

- add an item inline, at the end of a phase or between two items;
- edit an item's text and guidance in place;
- remove an item with one click. Removed items are hidden but can be restored, and they keep their history;
- drag to reorder items, or move them between phases;
- add or rename phases.

None of this needs approval or affects any other checklist. It should feel as light as a row in Smartsheet or Excel.

**Hints.** For items InteropHub can check from its own data, it shows a hint next to the item. The person still checks the item off. Automatic checks tend to be subtly wrong, and a manual check-off is the record that someone looked.

| Check code | What InteropHub looks at |
|---|---|
| `org-representation` | Committed organizations and their types; whether an IIS/jurisdiction is present; at least 3 organizations. On a Small Group checklist, only committed organizations that have an active participant in that Small Group. |
| `orgs-need-contact` | Interested or committed organizations with no joined contact. On a Small Group checklist, interested or committed organizations with no active participant in that Small Group. |
| `topic-champion` | The initiative's primary topic has a `CHAMPION`. The same check on a Small Group checklist. |
| `members-follow-primary-topic` | Joined initiative members who don't follow the primary topic. The same check on a Small Group checklist. |
| `series-exists` | The Small Group topic has an active meeting series |
| `members-registered-for-series` | Small Group participants who do not have an `APPROVED` registration for the series. A `REQUESTED` registration is not counted. |
| `members-follow-topic` | Small Group participants with an email who don't follow the Small Group topic |
| `meetings-scheduled` | Meetings in the series, other than cancelled ones, between the cycle start and closeout dates |
| `agenda-topics-linked` | Agenda items in the cycle's meetings that are linked to a topic |
| `notes-finalized` | Cycle meetings more than 7 days old without published notes |
| `orientation-bundle` | The Small Group topic has a Topic Orientation bundle |
| `technical-bundle` | A published Starter Packet snapshot was created for the Small Group topic during the cycle |

Hints only report what they find. They never mark an item done. `topic-champion` and `members-follow-primary-topic` keep their initiative meaning on a Small Group checklist. The two organization checks above are the ones that narrow to the Small Group.

New checks can be added over time. A template item that names a check InteropHub doesn't know simply shows no hint.

**Action queue.** Later, overdue and due-soon checklist items could appear in the home-page action queue, alongside meeting-cadence actions such as "publish notes." That is deferred.

### Templates

Templates are deliberately loose. The first versions are drafts. Sabrina will try them, they'll be revised and uploaded again, and checklists already in use pick up the changes without losing their history.

**Format.** A template is a Markdown file, uploaded by a global admin. Only global admins can upload or change templates.

```markdown
# Small Group Checklist
kind: SMALL_GROUP
version: 2

## Qualify and charter
- [sisc-sponsor] Confirm the SISC sponsor and AIRA project support. (due: cycle-start-21d)
  Guidance lines are indented under the item. Links are allowed.
- [org-representation] Confirm participating organizations and an IIS perspective. (check: org-representation)
```

Rules:

- `kind:` is `INITIATIVE` or `SMALL_GROUP`. `version:` is a whole number.
- `##` headings are phases. Each `-` line is an item. Indented lines that follow an item are its guidance.
- `[key]` is the item's **stable identity**. If it is missing, a key is generated from the title, so a rough draft still uploads.
- `(due: anchor±Nd)` is optional:
  - initiative anchors: `start`, `end`;
  - Small Group anchors: `cycle-start`, `working-day`, `closeout`;
  - due dates appear once the anchor date is set, and the organizer can override them.
- `(check: code)` is optional. It is kept separate from the key so that rewording or rekeying an item doesn't break its hint.

The uploaded Markdown is stored as-is alongside the parsed items, so every version can be read and downloaded again.

**One rule about keys:** never reuse a key for something that means something different. Change the wording freely and keep the key. If an item's meaning really changes, give it a new key.

**Copying and upgrading.** Creating an initiative or Small Group copies the current template of its kind into its own checklist. A later template version is merged in by key, only when the organizer chooses:

| Case | Result |
|---|---|
| Key in both | Phase, title, guidance, order, due rule and check update. **Status, assignee and timeline are kept.** If the organizer edited the item's text, the organizer's wording is kept and the template's new wording is shown in the preview. |
| Key in both, but the organizer removed the item | Stays removed. Upgrades don't bring back items the organizer deliberately removed. |
| Key only in the new template | Added as `OPEN`. |
| Key only in the checklist | Kept, marked "no longer in template," with its history. The organizer may mark it not applicable or leave it. |
| Organizer-added item | Never touched. |

Active initiatives and Small Groups show "Template v*N* is available," a preview of what would change, and an apply button. Closed ones keep the version they ran with. Each upgrade adds an entry to the checklist's timeline ("Updated from template v2 to v3: 2 added, 1 no longer in template").

**Learning from real checklists.** Because organizers change their own checklists freely, those changes show how the template should improve. Global admins get a template-feedback view for each template kind. Across all checklists, it shows:

- items organizers added;
- template items they removed or marked not applicable;
- template items they reworded.

Any checklist can also be **downloaded as template Markdown**, using the same format and keeping keys. A well-tuned checklist from one Small Group can be edited lightly and uploaded as the next template version.

The first draft templates are in `docs/initiatives/templates/`.

### Timeline: status changes with notes

Comments and status changes are recorded the same way everywhere in this module. Every status change can carry an optional note. Comments and status changes appear together, in order, on:

- initiatives (status changes, settings changes);
- the initiative's organizations (status changes, comments, contact changes);
- members (status changes, organization changes, comments);
- Small Groups (status changes, comments);
- checklist items (status changes, reassignment, comments);
- checklists (template upgrades).

Each entry is one line. A comment's text is on that same line. The page shows the latest five entries, with **Show all** for the rest.

Example:

> Oct 23 · Sabrina · Prospect → Invited: "Emailed; she's checking with leadership about participating."
> Nov 2 · Sabrina · Leadership approved, waiting for her reply.
> Nov 4 · Sabrina · Invited → Joined.

Status-change entries can't be edited. Authors edit their own comments through an Edit link that opens a dialog. One shared timeline component serves every page.

## Suggested Data Model

Table names use the `es_` prefix, because initiatives sit in the same area as topics and meetings. The shared organization list does not, matching `supporter`. Add new tables to `db/unapplied_updates.sql`. Read `docs/database-release-practice.md` first.

**`organization`**
- `organization_id`, `organization_name`, `organization_type` (varchar code), `website_url`
- `external_system`, `external_id` (Zoho, later)
- `is_active`, `created_by_user_id`, `created_at`, `updated_at`

**`es_initiative`**
- `es_initiative_id`, `initiative_code` (unique, used in URLs), `initiative_name`, `summary`, `description`
- `primary_es_topic_id` (NOT NULL)
- `status`, `visibility`, `join_policy`
- `start_date`, `end_date`
- `created_by_user_id`, `created_at`, `updated_at`

**`es_initiative_manager`**
- `es_initiative_manager_id`, `es_initiative_id`, `user_id`, `manager_role` (`ADMIN`, `EDITOR`, `VIEWER`)
- `created_by_user_id`, `created_at`
- unique on (`es_initiative_id`, `user_id`)

**`es_initiative_link`**
- `es_initiative_link_id`, `es_initiative_id`, `link_label`, `link_url`, `display_order`
- `created_by_user_id`, `created_at`

**`es_initiative_organization`**
- `es_initiative_organization_id`, `es_initiative_id`, `organization_id`
- `participation_status` (`PROSPECT`, `CONTACTED`, `INTERESTED`, `COMMITTED`, `DECLINED`, `WITHDRAWN`)
- `status_changed_at`, `status_changed_by_user_id`
- `created_by_user_id`, `created_at`, `updated_at`
- unique on (`es_initiative_id`, `organization_id`)

**`es_initiative_member`**
- `es_initiative_member_id`, `es_initiative_id`, `user_id` (nullable)
- `email` (nullable; a name may be known before an email), `email_normalized`, `contact_first_name`, `contact_last_name`
- `es_initiative_organization_id` (nullable), `initiative_role` (free text, nullable)
- `member_status`, `member_source` (`STAFF_ADDED`, `APPLIED`, `FOLLOWER_IMPORT`, `SPREADSHEET_IMPORT`, `SMALL_GROUP`)
- `status_changed_at`, `status_changed_by_user_id`, `joined_at`
- `created_by_user_id`, `created_at`, `updated_at`
- unique on (`es_initiative_id`, `email_normalized`) where the email is present
- fill in `user_id` on sign-in, the same way as other email-keyed tables (see `EsInterestService`)

**`es_small_group`**
- `es_small_group_id`, `es_initiative_id`
- `es_topic_id` (NOT NULL), `es_topic_meeting_id` (nullable until a series is chosen)
- `small_group_name`, `description`, `status`
- `cycle_start_date`, `working_day_date`, `closeout_date`, `break_note`
- `primary_product_areas` (e.g. a comma list of `GUIDANCE`, `TESTS`, `SOFTWARE`), `charter_url`
- `created_by_user_id`, `created_at`, `updated_at`

**`es_small_group_participant`**
- `es_small_group_participant_id`, `es_small_group_id`, `es_initiative_member_id`
- `participant_role` (free text, nullable: a role or a team)
- `participant_status` (`ACTIVE`, `REMOVED`)
- `created_by_user_id`, `created_at`, `updated_at`
- unique on (`es_small_group_id`, `es_initiative_member_id`)

**`es_checklist_template`**
- `es_checklist_template_id`, `template_kind` (`INITIATIVE`, `SMALL_GROUP`), `version_no`, `title`
- `source_markdown`, `is_current`
- `uploaded_by_user_id`, `uploaded_at`
- unique on (`template_kind`, `version_no`)

**`es_checklist_template_item`**
- `es_checklist_template_item_id`, `es_checklist_template_id`
- `item_key`, `phase_label`, `title`, `guidance`
- `due_anchor`, `due_offset_days`, `check_code`, `display_order`
- unique on (`es_checklist_template_id`, `item_key`)

**`es_checklist`**
- `es_checklist_id`, `es_initiative_id`
- `es_small_group_id` (null for the initiative's own checklist)
- `template_kind`, `applied_es_checklist_template_id`, `created_at`, `updated_at`

**`es_checklist_item`**
- `es_checklist_item_id`, `es_checklist_id`
- `item_key`, `item_origin` (`TEMPLATE`, `CUSTOM`), `in_current_template`
- `is_removed`, `is_locally_edited`
- `phase_label`, `title`, `guidance`
- `due_anchor`, `due_offset_days`, `due_date_override`, `check_code`, `display_order`
- `item_status` (`OPEN`, `DONE`, `NOT_APPLICABLE`), `status_changed_at`, `status_changed_by_user_id`
- `assigned_user_id`, `created_at`, `updated_at`
- unique on (`es_checklist_id`, `item_key`)

**`es_initiative_timeline_entry`**
- `es_initiative_timeline_entry_id`
- `es_initiative_id` (every entry belongs to one initiative, which scopes access)
- `subject_type` (`INITIATIVE`, `ORGANIZATION`, `MEMBER`, `SMALL_GROUP`, `CHECKLIST`, `CHECKLIST_ITEM`), `subject_id`
- `entry_type` (`COMMENT`, `STATUS_CHANGE`, `FIELD_CHANGE`, `TEMPLATE_UPGRADE`, `SYSTEM`)
- `from_value`, `to_value`, `note_text`
- `created_by_user_id`, `created_at`, `edited_at`
- index on (`subject_type`, `subject_id`, `created_at`)

Status columns are stored as varchar codes, not MySQL enums, so the lists can change while the module is still settling.

## Pages

| Page | Who | Contents |
|---|---|---|
| **Initiatives** (top menu) | Everyone | Listed initiatives, with Join / Apply depending on join policy; "My initiatives" |
| **Initiative public page** | Everyone, if listed | Summary, primary topic, how to join |
| **Initiative workspace** | Managers | Overview, Roster, Small Groups, Checklist, Settings |
| **Roster: Organizations** | Managers | Each organization with its status, type, contacts and "needs a contact" flag; add organization; change status with a note; representation summary |
| **Roster: People** | Managers | Filter by status, organization and organization type; add person (organization type-ahead with add-new); change status with a note; import from xlsx/CSV; pull from primary-topic followers; "doesn't follow primary topic" flag |
| **Small Groups** | Managers | Compact list: name, topic, status, cycle dates, participant count. New Small Group defaults the topic to the primary topic and chooses a series from that topic. If the topic has no series, the page says so and links to the existing series admin. |
| **Small Group page** | Managers | Summary, Edit details, status change with a note, participant table, People to bring in, copyable registration links, checklist, one-line timeline |
| **Checklist** (initiative or Small Group) | Managers | Items grouped by phase, status, due dates, hints, timeline per item; spreadsheet-style inline add, edit, remove, restore and reorder; template-upgrade preview; download as template Markdown |
| **Checklist templates** | Global admins | Upload Markdown, see versions, download source, template-feedback view |
| **Primary topic page** | Everyone | Banner for a listed initiative: "Part of *X* — learn more / join" |

The roster import should accept Sabrina's current columns (contact, email, organization, organization type, notes). It should create missing organizations and save each row's notes as the first timeline entry. Rows with an organization but no contact add the organization only. The import should also accept the Stakeholder Matrix's columns, once they are confirmed.

## What Does Not Change

- Topics, topic spaces, topic relationships, topic curation and topic permissions.
- Meeting series, series membership and registration, meetings, agendas, notes, outcomes, RSVP and attendance.
- Topic roles. A Champion may never know about an initiative that uses their topic.
- Who may schedule meetings on a topic. An organizer who needs that is given topic access separately.
- InteropHub tracks only public meetings. Private project, partner, funder and internal meetings stay outside it.
- Project management stays in Smartsheet: contracts, travel and venue logistics, task dependencies and percent-complete tracking.

## Connectathon Workspace Tables

`connect_workspace`, `workspace_enrollment`, `workspace_system`, `workspace_endpoint`, `workspace_progress`, `workspace_step`, `workspace_app` and `workspace_system_contact` are empty and tied to the legacy `ig_topic` table. They are **not** reused for this module.

Their testing-grid idea (systems, endpoints, step-by-step progress between client and server) may later return as an optional technical workspace attached to a Small Group, re-pointed from `ig_topic`. That decision is deferred.

## Implementation Plan

### Phase 1: Initiatives, organizations and roster

- Tables: `organization`, `es_initiative`, `es_initiative_manager`, `es_initiative_link`, `es_initiative_organization`, `es_initiative_member`, `es_initiative_timeline_entry`.
- Admin creation of initiatives and manager access.
- Roster pages for organizations and people, with status changes and notes, the organization type-ahead, xlsx/CSV import, and the representation summary.
- Automatic follow of the primary topic on `JOINED`, and the "doesn't follow primary topic" flag.

This replaces Sabrina's spreadsheet and the Stakeholder Matrix, and doesn't touch any existing topic or meeting code.

### Phase 2: Checklists and templates

- Template upload and parsing, checklist copies for initiatives, status changes with notes, relative due dates.
- Spreadsheet-style inline editing: add, edit, remove, restore, reorder.
- Load the draft templates from `docs/initiatives/templates/`.
- Template upgrade with preview, download as template Markdown, and the template-feedback view.

### Phase 3: Small Groups

- `es_small_group` and `es_small_group_participant`. `es_checklist.es_small_group_id` links the Small Group checklist.
- Staff add participants through the shared people search, including someone who is not yet on the roster. That person is added as `JOINED` with `member_source` `SMALL_GROUP`. Adding someone who is not `JOINED` marks them `JOINED` and follows the Small Group topic when they have an email or a linked account. Names are first and last. An email is never stored as a name. Removing a participant does not unfollow the topic.
- The Small Group page is a summary plus a participant table and a People to bring in table. Only an `APPROVED` series registration counts as registered. Copyable registration links do not send email and do not change series membership.
- A new Small Group can copy participants from an earlier one. Later cycles reuse the topic's series.
- The Small Group checklist is copied from the current `SMALL_GROUP` template when the Small Group is created. Due dates use `cycle-start`, `working-day`, and `closeout`. Template upgrade and download work the same way as for an initiative.
- Checklist hints read the participant list, the series, meetings, agendas, notes, the Topic Orientation bundle, and Starter Packet snapshots. They never mark an item done.

### Phase 4: Public listing and joining

- Initiatives menu, public page, apply and join flows, primary-topic banner.
- Confirmation when unfollowing the primary topic, which leads to `LEFT`.

### Later

- Checklist items in the home-page action queue.
- Initiative and Small Group audiences in meeting communications, and inviting primary-topic followers to join. This is done by hand for now.
- Technical Bundle as its own bundle purpose, product records, SISC review and handoff.
- Zoho organization sync and organization merging.
- Optional technical workspace for Small Groups (see **Connectathon Workspace Tables**).

## Dependencies and Open Items

- **Topic-roles email split.** The full following rules depend on separating email preference from the topic relationship (`docs/topic-roles-implementation-prompt.md`). Phase 1 uses the interim flag described under **Following rules**.
- **Stakeholder Matrix.** Proposed: move it into InteropHub as the initiative roster. Get its current columns from Smartsheet to confirm the roster covers them and to shape the import.
- **Initiative checklist content.** The Small Group template comes from the roadmap's operating checklist. The initiative template draws on the Indiana project plan's generic steps and will be corrected by Sabrina after using it.
- **Series membership stays in the meeting tools.** Phase 3 reads registrants and does not add or remove them. Whether an admin-added series member follows the topic is unchanged.

## Design Principles

- Topics are public and permanent. Initiatives are an organizing layer that points at them.
- Use the existing topic, meeting and agenda structures; don't rebuild them.
- Following is easy. Joining is a deliberate commitment, and joining implies following.
- Help the organizer see what's next; don't enforce a workflow.
- Keep editing as light as a spreadsheet. Templates are a starting point, not a form to fill in.
- Link to project-management tools; don't become one.
- Every status change can explain itself, and history is kept, not overwritten.
- Templates evolve. Existing work is merged forward, never thrown away.
