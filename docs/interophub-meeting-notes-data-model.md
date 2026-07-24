# InteropHub Meeting Notes and Outcomes Data Model

## Purpose

This document describes the data model used to support InteropHub meeting mechanics, topic notes, Recorded Outcomes, and live meeting voting.

It focuses on:

- The role of each table
- How the tables relate to one another
- The lifecycle of meetings and notes
- How the application is expected to use the model
- Important invariants and design boundaries

It is not intended to be a field-by-field schema reference. The SQL schema and Hibernate entities remain the authoritative source for individual columns and constraints.

---

## Core Design Principles

### Topics are the long-lived organizing structure

All notes belong to a topic. Meetings and agenda items provide context for a discussion, but the topic is the durable subject whose history continues across meetings.

### Meetings coordinate activity but do not own topic history

A meeting provides:

- An agenda
- A designated chair and scribe
- A current agenda item
- A sequence of chair, scribe, and topic changes
- Participant-count observations
- A cleanup period after the live meeting

Notes created during a meeting still roll up to their related topics.

### Responsibility is visible, not heavily policed

InteropHub records who is currently chairing, scribing, presenting, or editing notes. Qualified users may take over these responsibilities without a request-and-approval workflow.

The model assumes good intent and records what happened rather than attempting to resolve social conflicts.

### Historical records become immutable

Meetings and topic notes have a limited cleanup period. After finalization or closure, they cannot be reopened or edited. Corrections and later developments are recorded in new topic notes.

### Outcomes are part of the note record

Recorded Outcomes are structured annotations attached to a specific note bullet. They remain editable while the parent note is open and become immutable with the parent note.

### Live voting assists the meeting

The voting model helps display motions, gather optional electronic responses, perform arithmetic, and record the final tally. It does not replace the authority of the chair or scribe and does not create a permanent roll-call record.

---

## Model Overview

```text
auth_user
   │
   ├── recurring meeting responsibility
   │      └── es_topic_meeting_cochair
   │
   ├── individual meeting roles
   │      └── es_meeting_role_assignment
   │
   ├── note editor responsibility
   │      └── es_topic_note_editor_history
   │
   └── audit fields throughout the model

es_topic
   │
   ├── es_topic_meeting
   │      └── es_meeting
   │             ├── es_meeting_status_history
   │             ├── es_meeting_role_assignment
   │             ├── es_meeting_agenda_activity
   │             │      └── es_meeting_participant_count
   │             └── es_meeting_agenda_item
   │                    └── es_topic_note
   │
   └── es_topic_note
          ├── es_topic_note_revision
          ├── es_topic_note_editor_history
          └── es_recorded_outcome
                 └── es_live_vote
                        └── es_live_vote_response
```

---

# Existing Foundation Tables

## `auth_user`

`auth_user` supplies the registered identity used throughout the meeting and note-taking model.

A user account is required to:

- Serve as chair or scribe
- Be a recurring meeting cochair
- Operate meeting controls
- Take over note editing
- Create or edit topic notes
- Submit an electronic meeting vote
- Be recorded as an actor in audit history

People may view public meetings and notes without logging in, but active participation in meeting mechanics requires an authenticated user.

## `es_topic`

`es_topic` is the durable parent of all notes and outcomes.

A topic may accumulate:

- Notes from multiple meetings
- Multiple agenda items from the same meeting
- Notes entered directly by topic champions
- Recorded Outcomes from any of those notes
- Formal motions and live vote results

The topic page can use this model to present a chronological history across meetings and work performed between meetings.

The topic table itself does not need to contain note or outcome state.

## `es_topic_meeting`

`es_topic_meeting` represents a recurring meeting series or working group.

It provides:

- The recurring meeting identity
- The meeting series name and description
- The parent topic used when an agenda item has no more specific topic
- The context for recurring cochairs and members
- The parent for individual `es_meeting` instances

A recurring meeting does not have one permanent chair. It may have multiple cochairs.

## `es_topic_meeting_member`

This table represents ordinary membership in a recurring meeting series.

It remains separate from operational responsibility:

- Membership means a person is associated with the group.
- Cochair responsibility is stored separately.
- Membership may exist for an email address that is not linked to an account.
- Meeting control requires a linked `auth_user`.

This table should not be expanded into a general role table.

## `es_meeting_agenda_item`

This table represents one item scheduled on a specific meeting agenda.

An agenda item may:

- Reference a specific topic
- Have no specific topic and inherit the recurring meeting's parent topic
- Have one or more presenters
- Have one topic-note session
- Become the current discussion item one or more times during the meeting

The agenda item is meeting structure. It is not itself the note record.

## `es_agenda_item_presenter`

This table records people associated with presenting or facilitating an agenda item.

Presenters are important to the meeting mechanics because an authenticated, accepted presenter may:

- Advance the current agenda item
- Operate meeting controls
- Take over note editing
- Help complete the meeting record

A presenter without a linked `auth_user` may still be displayed as a presenter but cannot operate authenticated controls.

## `es_subscription`

`es_subscription` identifies followers and topic champions.

The distinction matters:

- A follower is interested in the topic.
- A champion is designated as responsible for the topic.

A champion with a linked user account may create and edit topic notes outside meetings. Ordinary followers do not receive those editing rights merely by following a topic.

## `es_topic_space_member`

Topic-space administrators receive broad operational authority within their topic space.

This existing model can be used when determining who may:

- Control meetings
- Create or edit notes
- Correct operational problems
- Manage topic-level records

This authority does not create an unlock mechanism for finalized notes or closed meetings.

## `es_meeting_attendance`

This table records identified people associated with meeting attendance.

Attendance is distinct from the observed number of people currently on the call:

- Attendance may be incomplete.
- Some people may join by telephone.
- Some people may not sign attendance.
- Attendance is person-oriented.
- Participant count is a time-based observation.

The live voting model must not assume that attendance rows equal the voting population.

## `es_meeting_communication`

This table manages communications associated with a meeting.

It is expected to remain aligned with the expanded meeting status model. Future work may add communication types for note-cleanup reminders or finalization notices.

Communication records are operational records and may still be created after a meeting is closed. They do not alter the immutable meeting content.

---

# Recurring Meeting Responsibility

## `es_topic_meeting_cochair`

This table records the users who share standing responsibility for a recurring meeting series.

The role is always **cochair**, not chair.

A cochair may:

- Help prepare individual meetings
- Be designated as the chair for a specific meeting
- Be designated as the scribe for a specific meeting
- Start or control a meeting
- Change the current agenda item
- Take over note editing
- Complete or close a meeting

A recurring meeting may have multiple active cochairs.

This table should preserve the assignment over time through active and inactive status rather than deleting historical responsibility.

---

# Individual Meeting Model

## `es_meeting`

`es_meeting` is the central record for one occurrence of a recurring meeting.

It stores the meeting's current operational state, including:

- Its lifecycle status
- Scheduled and actual timing
- Designated chair and scribe
- Current chair and scribe
- Current agenda item
- The fixed closure deadline
- Manual or automatic closure information

### Meeting lifecycle

```text
DRAFT
  ↓
PROPOSED
  ↓
FINALIZED
  ↓
IN_SESSION
  ↓
COMPLETED
  ↓
CLOSED
```

`CANCELLED` is a terminal alternative.

### Status meanings

- **DRAFT** — The agenda is being assembled.
- **PROPOSED** — The proposed agenda is available for review and preparation.
- **FINALIZED** — The agenda is final and the meeting is ready to occur.
- **IN_SESSION** — The meeting is actively underway.
- **COMPLETED** — The live meeting has ended; notes may still be cleaned up.
- **CLOSED** — The permanent meeting record is visible but immutable.
- **CANCELLED** — The meeting will not occur.

### Designated and current roles

The designated chair and scribe are selected while preparing the agenda.

When the meeting begins, those users normally become the initial current chair and scribe. During the meeting, either role may be changed.

The designated values preserve what was planned. The current values support the live meeting display.

### Closure

When the meeting is completed, it receives a fixed closure deadline seven days later.

The meeting may be closed sooner by an authorized user. Otherwise it closes automatically when the deadline is reached.

Closure finalizes all remaining meeting notes and prevents further editing of meeting-scoped content.

There is no reopen or unlock operation.

## `es_meeting_status_history`

This append-only table records every meeting lifecycle transition.

It provides an audit history showing:

- The previous and new statuses
- When the transition occurred
- Who initiated it
- Whether it was initiated by a user or automatic processing

The current status on `es_meeting` remains authoritative. This table explains how the meeting reached that state.

## `es_meeting_role_assignment`

This table records the timeline of active chair and scribe assignments during one meeting.

It answers:

- Who chaired at a given time?
- Who was serving as scribe?
- When did a handoff occur?
- Who made the change?

Only two role types are used:

- `CHAIR`
- `SCRIBE`

A role change is not a request-and-approval process. The prior assignment ends, the new assignment begins, and the current meeting pointer is updated.

The role history is also important to voting because the presiding chair cannot vote.

## `es_meeting_agenda_activity`

This table records which agenda item the group was discussing over time.

Each activation records:

- The agenda item
- The effective topic
- Start and end times
- The user who started or ended the activity

An agenda item may become active more than once if the meeting returns to it.

The effective topic is stored at the time of activation:

1. Use the agenda item's topic when present.
2. Otherwise use the recurring meeting's parent topic.

This table supports:

- The live “Now discussing” display
- Topic-switch history
- Time spent on each agenda item
- Participant counts associated with a discussion
- Future meeting analytics

The current agenda item is also stored directly on `es_meeting` for efficient live display.

## `es_meeting_participant_count`

This table stores observed counts of people currently on the call.

Counts are associated with an agenda-activity segment rather than only with the whole meeting. This allows participation to be understood by topic and over time.

Each adjustment creates a new observation. Previous counts are not overwritten.

Expected uses include:

- Helping the scribe calculate vote totals
- Showing the current number of people on the call
- Tracking participation as people join or leave
- Estimating interest in topics
- Supporting later meeting analysis

These counts are informative, not authoritative attendance or quorum records.

---

# Topic Note Model

## `es_topic_note`

`es_topic_note` is the authoritative note-session record.

It supports two contexts:

### Meeting note

A meeting note is attached to:

- One topic
- One meeting
- One agenda item

Only one topic-note session exists for each agenda item.

### Ad hoc topic note

A topic champion or other authorized user may create a note directly under a topic without a meeting or agenda item.

Examples include:

- An informal two-person discussion
- Information received by email
- Research or analysis completed between meetings
- Context added by a topic champion
- A clarification related to earlier notes

### Note document

The note content is stored as a Tiptap JSON document.

The document:

- Appears to users as a continuous bulleted outline
- Contains stable node IDs for individual bullets
- Is saved as one structured document
- Is not normalized into one database row per bullet
- Has a generated plain-text representation for searching

### Note lifecycle

Notes have only two states:

```text
OPEN
FINALIZED
```

While open:

- The content may be edited.
- Outcomes may be added or changed.
- A qualified user may take over the editor designation.
- Revision snapshots may be recorded.

When finalized:

- Notes become immutable.
- Outcomes and vote records attached to the note become immutable.
- There is no unlock function.
- Corrections must be recorded in a new note.

### Seven-day rule

Ad hoc topic notes finalize seven days after creation.

Meeting notes share the parent meeting's fixed closure deadline and are finalized no later than meeting closure.

Editing does not restart or extend the deadline.

### Empty notes

An empty note should not become a durable historical record. The application should avoid creating or retaining a note until meaningful content exists.

## `es_topic_note_revision`

This table stores recoverable snapshots of a topic note.

It is used for:

- Recovery from accidental edits
- Debugging save or synchronization problems
- Preserving checkpoints before takeover or finalization
- Understanding how a note changed during its open period

It is not:

- A branching document model
- A review workflow
- An unlock mechanism
- A way to publish multiple active versions

The current document on `es_topic_note` remains authoritative.

The application does not need to store a revision for every keystroke. Snapshots should be created at meaningful checkpoints.

## `es_topic_note_editor_history`

This table records visible changes in note-taking responsibility.

It records:

- The prior editor
- The new editor
- When the takeover occurred
- Who initiated it

Only one editor is designated as responsible for a specific note at a time.

The designation is a coordination mechanism, not a database lock. A qualified user may take over immediately without approval.

A takeover updates the note's active-editor version so that an earlier editor cannot later submit stale changes.

---

# Recorded Outcomes

## `es_recorded_outcome`

This table stores structured outcomes identified within a topic note.

Outcome types include:

- Working consensus
- Formal motion
- Direction
- Open issue
- Action
- Rationale

Each outcome references exactly one stable bullet node in the Tiptap document.

That bullet acts as a pointer into the surrounding notes. Supporting information may appear:

- In nested bullets
- Immediately before the source bullet
- Immediately after the source bullet

The outcome also contains concise text suitable for display on the topic timeline.

### Outcome lifecycle

Recorded Outcomes do not have an independent status or approval lifecycle.

Their editability is determined by the parent note:

- Open note: outcome may be edited.
- Finalized note: outcome is immutable.
- Closed meeting: all meeting outcomes are immutable.

If a later discussion corrects or changes an outcome, the new information is recorded in a new note and, when appropriate, a new outcome.

---

# Live Meeting Voting

## `es_live_vote`

This table stores the live vote associated with one Formal Motion outcome.

It is designed to assist a meeting, not to function as a general electronic-election system.

A vote may include:

- Motion text
- Mover and seconder
- Presiding chair
- Observed call participant count
- Expected voter count
- Electronic response totals
- Manually entered verbal or chat totals
- Final official tally
- Result

### Voting workflow

```text
PREPARED
   ↓
OPEN
   ↓
CLOSED
```

### Presiding chair

The current chair is captured when the vote opens.

That user is excluded from electronic voting for that vote. A later chair change does not rewrite the historical vote.

### Suggested and final totals

The application may suggest totals by combining:

- Electronic responses
- Manual counts
- Participant-count observations

The scribe retains final control over the official tally.

The database must not require the final numbers to match:

- The participant count
- The expected voter count
- Electronic plus manual arithmetic

Those mismatches may be shown as warnings, but they do not invalidate the record.

### Scope boundary

This table is for live meeting voting only.

It does not support:

- Surveys
- Asynchronous feedback
- Votes left open after the meeting
- Roll-call voting
- Follow-up collection from absent members

Those capabilities belong in a separate future model.

## `es_live_vote_response`

This temporary table stores electronic responses while a live vote is open.

A logged-in participant may choose:

- For
- Against
- Abstain

A participant may change the response while voting remains open.

The presiding chair may not submit a response.

When the vote closes:

1. Responses are aggregated.
2. Aggregate electronic totals are stored on `es_live_vote`.
3. Individual response rows are deleted.

This deliberately avoids creating a durable roll-call record.

Responses received verbally, by telephone, or through meeting chat are entered only as aggregate manual counts.

---

# How the Tables Work Together

## Preparing a meeting

1. An `es_meeting` is created under an `es_topic_meeting`.
2. Agenda items are added through `es_meeting_agenda_item`.
3. Presenters are added through `es_agenda_item_presenter`.
4. The designated chair and scribe are selected from `auth_user`.
5. Recurring cochairs are available through `es_topic_meeting_cochair`.
6. The agenda moves through `DRAFT`, `PROPOSED`, and `FINALIZED`.

## Starting a meeting

1. An authorized user moves the meeting to `IN_SESSION`.
2. A row is added to `es_meeting_status_history`.
3. The designated chair and scribe become current.
4. Initial rows are added to `es_meeting_role_assignment`.
5. An authorized user activates the first agenda item.
6. A row is added to `es_meeting_agenda_activity`.
7. The related topic note becomes available for live editing.

## Switching topics

1. The current agenda activity is ended.
2. A new `es_meeting_agenda_activity` row is created.
3. `es_meeting.current_agenda_item_id` is updated.
4. The effective topic is displayed to everyone.
5. The current scribe or another qualified user may take over the new note.
6. The prior note may remain open while its earlier editor finishes cleanup.

Different note sessions may therefore have different active editors at the same time.

## Recording participant counts

1. The scribe observes the number shown in the meeting platform.
2. A new `es_meeting_participant_count` row is added.
3. Later changes create additional observations.
4. The latest count for the active agenda item is displayed.
5. A vote may snapshot one of these observations.

## Taking notes

1. The application presents a Tiptap bulleted editor.
2. The current editor is shown to all viewers.
3. The editor saves the structured document to `es_topic_note`.
4. The server increments the note revision.
5. Periodic snapshots are written to `es_topic_note_revision`.
6. A takeover updates the current editor and adds an `es_topic_note_editor_history` row.
7. Viewers receive live updates but do not edit unless they take over.

## Recording an outcome

1. The note-taker selects one bullet.
2. The bullet's stable node ID is stored in `es_recorded_outcome`.
3. The outcome receives a type and concise outcome text.
4. It appears both in the note and in topic-level outcome views.
5. It remains editable until the parent note is finalized.

## Recording a formal motion and vote

1. A note bullet becomes a Formal Motion outcome.
2. An `es_live_vote` record is prepared.
3. The current chair is captured when voting opens.
4. Logged-in participants may submit temporary `es_live_vote_response` rows.
5. The scribe may enter additional aggregate verbal or chat counts.
6. The application suggests a total.
7. The scribe records the official final tally and result.
8. Electronic responses are aggregated and deleted.
9. The final motion and tally remain attached to the outcome.

## Completing and closing a meeting

1. An authorized user marks the meeting `COMPLETED`.
2. The fixed closure deadline is set to seven days later.
3. Open meeting notes receive the same deadline.
4. Notes remain editable during the cleanup period.
5. The scribe or another authorized user may close the meeting early.
6. Otherwise automatic processing closes it at the deadline.
7. Remaining notes are finalized.
8. Remaining vote responses are aggregated and deleted.
9. Open agenda activities and role assignments are ended.
10. The meeting becomes `CLOSED`.
11. No meeting-scoped content may be edited afterward.

The meeting closes in whatever state the record is in. The good, the bad, and the ugly meetings all close on time.

## Creating notes outside a meeting

1. A topic champion or another authorized user creates an ad hoc `es_topic_note`.
2. The note has a topic but no meeting or agenda item.
3. Outcomes may be recorded in the same way as meeting notes.
4. The note may be finalized immediately.
5. Otherwise it finalizes automatically after seven days.
6. It then becomes part of the topic's permanent history.

---

# Authorization Expectations

Authorization is primarily enforced in application services rather than through a complex database permission model.

## Meeting control

A logged-in user may normally control a meeting when they are:

- An active recurring cochair
- The designated or current chair
- The designated or current scribe
- An authenticated accepted presenter
- The meeting creator
- A topic-space administrator
- A global administrator

Meeting controls include:

- Starting the meeting
- Changing chair or scribe
- Activating an agenda item
- Updating participant count
- Taking over note editing
- Opening or closing a vote
- Completing or closing the meeting

## Topic-note participation

A logged-in user may normally create or edit notes when they are:

- A topic champion
- A recurring meeting cochair
- The chair or scribe
- A presenter for the agenda item
- A topic-space administrator
- A global administrator

Followers and unauthenticated viewers may view public content but may not operate meeting controls or edit notes.

These rules are intended to encourage participation. They should not be implemented as a burdensome approval system.

---

# Important Invariants

The application and database should preserve the following invariants:

1. Every topic note belongs to one topic.
2. A meeting note belongs to one meeting and one agenda item.
3. An ad hoc note belongs to neither a meeting nor an agenda item.
4. Only one note session exists for an agenda item.
5. Only one chair assignment and one scribe assignment are current for a meeting.
6. Only one agenda activity is current for a meeting.
7. Only one editor is designated for a note at a time.
8. Editor designation does not create a database lock.
9. A stale editor cannot save after another user takes over.
10. A Recorded Outcome references exactly one note node.
11. Only a Formal Motion outcome may have a live vote.
12. A presiding chair cannot submit an electronic vote.
13. Individual electronic vote responses are deleted after aggregation.
14. Editing never extends the seven-day deadline.
15. Finalized notes cannot be reopened.
16. Closed meetings cannot be reopened.
17. There is no administrative unlock function.
18. Later corrections are recorded in new notes.

---

# Deliberate Non-Goals

This model does not attempt to provide:

- A meeting chat system
- A meeting transcript
- Simultaneous multi-author wordsmithing in one note
- Formal note approval or sign-off
- Reviewer assignment workflows
- Permanent document branching
- Reopening or superseding finalized notes
- Administrative unlocking
- Roll-call voting
- Asynchronous voting
- Surveys or feedback outside meetings
- Quorum adjudication
- Enforcement of meeting etiquette
- Resolution of disputes between participants

The software supports meeting mechanics and preserves history. It does not replace the social processes that make meetings work.

---

# Summary

The data model uses topics as the durable center of discussion history.

Recurring meeting tables establish ongoing responsibility. Individual meeting tables coordinate the live session and preserve its operational history. Topic notes store the group-authored summary of what occurred. Recorded Outcomes identify the most important points within those notes. Live vote tables help the chair and scribe gather and record formal-motion tallies without turning InteropHub into a roll-call or asynchronous election system.

The model intentionally favors:

- Visible responsibility
- Easy participation
- Minimal approval machinery
- Short cleanup windows
- Immutable history
- New notes rather than rewritten history
