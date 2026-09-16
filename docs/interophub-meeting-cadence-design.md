# InteropHub Meeting Cadence and Shared Action Queue

## Purpose

This document describes a desired meeting cadence for InteropHub and a lightweight mechanism for prompting authorized users to complete the next required step.

It is intended as an analysis and implementation handoff. Before proposing code changes, inspect the current InteropHub application and reconcile these requirements with its existing meeting states, agenda workflow, notes workflow, roles, permissions, communications, scheduled jobs, and user interface.

Do not assume that the names or data structures in this document match the current code. Reuse existing concepts where they provide the required behavior. Avoid introducing a general-purpose task-management or workflow engine unless the existing architecture makes that clearly preferable.

## Strategic context

InteropHub is intended to support a repeatable operating process for community-driven standards work:

- Meetings are temporary containers for interaction.
- Topics are the durable centers of work and retain accumulated notes, outcomes, rationale, open issues, and history.
- The application should make responsibilities and the next process step obvious.
- More than one authorized person should be able to advance the work, so the process does not depend on one individual being available.
- The process should be opinionated and predictable. The cadence described here is application-wide rather than configurable for each meeting or Topic Space.

The action queue described below is operational support for this process. It is not intended to become a personal task manager.

## Fixed meeting cadence

### Step 0: Schedule the meeting

An authorized user schedules a meeting and creates its draft agenda.

- The meeting is scheduled.
- The agenda is in Draft status.
- No action or reminder is needed for this step because it initiates the lifecycle.

### Step 1: Publish the proposed agenda

The proposed agenda is due 14 calendar days before the meeting.

The responsible users should:

1. Review and develop the draft agenda.
2. Change the agenda to Proposed.
3. Send or schedule the proposed-agenda communication.

The communication goes to:

- Topic champions for topics represented on the agenda.
- People identified as sharing, presenting, or championing an agenda topic.
- Any other existing application role that is already intended to participate in agenda preparation.

The proposed-agenda phase is one action, not separate content and communication actions. It is complete only when the agenda has the appropriate status and its communication has been sent or scheduled.

If a meeting is created fewer than 14 days in advance, this action becomes due immediately. It should not be described as overdue for time before the meeting existed.

### Step 2: Finalize the agenda

The final agenda is due three calendar days before the meeting.

The responsible users should:

1. Complete any final agenda changes.
2. Change the agenda to Final.
3. Send or schedule the final-agenda communication.

The communication goes to the deduplicated audience of people following the relevant meeting topics, together with any other existing meeting-notification audience that the current application intentionally supports.

This phase is also one action. If the final-agenda communication has already been scheduled, the action should recognize that and should not continue prompting the user to schedule it.

If a meeting is created fewer than three days in advance, this action becomes due immediately, subject to the same created-date rule described above.

### Step 3: Hold and close the meeting

Participants use their calendars to attend the meeting. InteropHub supports the live meeting and note-taking process.

No ordinary reminder to attend or begin the meeting is required.

The application does, however, need a reliable indication that the meeting was held or cancelled so that the notes-publication phase can begin. Prefer the existing meeting-close behavior if one exists.

If the scheduled meeting end passes and the meeting is still Scheduled or In Progress, show a fallback action to close the meeting or mark it cancelled. This is an exception-recovery prompt, not a normal meeting reminder.

### Step 4: Publish notes for review

When the meeting is closed, the assigned note-taker or another authorized user reviews the notes and publishes them.

- The action appears when the meeting is closed or otherwise recorded as held.
- The action becomes overdue 24 hours after the meeting.
- Publishing late does not extend or restart the existing seven-day editing window.

The responsible user should:

1. Review and correct the meeting notes.
2. Confirm that the notes are suitable for community review.
3. Publish the notes.
4. Send or schedule the notes-available communication.

The communication goes to the same deduplicated follower audience used for the final agenda, adjusted to match the application's existing notification rules.

The communication should:

- State that the meeting was held.
- State that notes are available for review.
- Link directly to the meeting notes.
- Ask readers to provide corrections or fixes within the next three days.
- Make clear that the note-editing window is limited.

This is one action. It is complete only when the notes have been published and the related communication has been sent or scheduled.

### Step 5: Allow corrections and close editing

The note-taker may incorporate corrections received during the review period.

The existing application already enforces the seven-day editing rule when someone attempts to edit notes. Preserve that implementation unless analysis identifies a defect. This design does not require:

- A finalization action.
- A scheduled job that changes note status at seven days.
- A new notification when the editing window closes.
- Extension or restart of the editing window when notes are published late or edited.

After the existing deadline, edits remain blocked. Later corrections should use the application's established approach for adding new topic-level information rather than changing the immutable meeting record.

## Conceptual lifecycle

The desired behavior can be understood through three related concerns. These do not necessarily require three new database fields if the application already represents them differently.

| Concern | Conceptual states |
| --- | --- |
| Agenda | Draft, Proposed, Final |
| Meeting | Scheduled, In Progress, Held, Cancelled |
| Notes | Not Started, Open, Published for Review, Editing Closed |

Avoid forcing these into one combined status if separate existing fields or derived conditions are clearer.

## Shared actions

### Core principle

An action represents an unmet condition for one meeting. It is not a separately assigned task copied into every eligible user's inbox.

Examples:

- Publish proposed agenda.
- Finalize agenda.
- Close meeting or mark cancelled.
- Publish meeting notes.

All users with the appropriate rights may see and perform the action. When any authorized user performs the underlying operation, the condition becomes satisfied and the action disappears for everyone.

### Deliberate simplifications

The initial design does not include:

- Claiming actions.
- Exclusive ownership or locking.
- Manual completion independent of application state.
- Permanent dismissal of required actions.
- Following an action.
- Per-user copies of shared actions.
- Approval or sign-off queues.
- A general-purpose task-management system.

### Completion

Completion should be derived from authoritative application state. Examples:

| Action | Completion condition |
| --- | --- |
| Publish proposed agenda | Agenda is Proposed and the proposed-agenda communication is sent or scheduled |
| Finalize agenda | Agenda is Final and the final-agenda communication is sent or scheduled |
| Close meeting | Meeting is Held or Cancelled |
| Publish meeting notes | Notes are published and the notes-available communication is sent or scheduled |

Do not add a separate `completed` flag if it could disagree with these authoritative records.

### Stable identity

Each derived action needs a stable identity so the application can retain personal read and snooze state. A deterministic identity such as the following may be sufficient:

```text
(meeting_id, action_type)
```

Use the existing identifier conventions in the application.

### Personal action state

Personal state is limited to interaction with the shared prompt:

- Unread
- Read
- Snoozed until a selected time

A conceptual persistence model is:

```text
UserActionState
- meeting/action identity
- user identity
- read_at
- snoozed_until
```

This is illustrative, not a required schema. Determine whether an existing notification, preference, or user-state mechanism can support it.

Snoozing affects only the individual user. It does not alter the shared obligation or hide it from other authorized users. Once the underlying action is complete, any retained personal state may remain as harmless history or be cleaned up according to existing application conventions.

## Determining who sees an action

Reuse the application's existing authorization rules. An action should be visible only to a user who can perform the operation it describes.

Likely responsibility groups include:

- Meeting organizers, chairs, or cochairs.
- Topic champions relevant to the meeting.
- Assigned note-takers.
- Other users who already have equivalent agenda, meeting, notes, or communication permissions.

Do not give followers editing responsibility merely because they receive communications.

The analysis should determine whether eligibility is best derived dynamically from current roles or captured when the action first becomes relevant. Dynamic derivation is preferred unless role changes would produce surprising or unsafe behavior.

## Action queue user experience

The authenticated home page should provide a focused action queue. Required operational actions should remain visually distinct from informational notifications such as followed-topic updates.

Suggested sections:

1. **Needs attention** — actions due now or overdue.
2. **Upcoming** — incomplete actions with future deadlines.
3. **Snoozed** — actions hidden by this user until a selected time.

Each action should show:

- A concise instruction.
- Meeting name and date.
- Due date or overdue duration.
- Why the user is eligible to act.
- A direct link or button to the correct workflow screen.
- Read/unread state.
- A snooze control.

Example:

> **Publish meeting notes**  
> The September IFG meeting has ended. Review the notes and schedule the notes-for-review communication by September 19.  
> `Review notes`

Do not require the user to manually mark the action complete. Returning to the queue should reflect the current authoritative state.

## Daily staff reminder digest

Send no more than one staff action digest per user per day.

Send the digest only when the user has at least one due or overdue action that is not currently snoozed. Do not send an empty digest.

The digest should:

- Use the same eligibility and completion logic as the in-application queue.
- Deduplicate actions that the user can see through multiple roles.
- Group actions by meeting where useful.
- Distinguish due and overdue items.
- Link directly to the relevant workflow screen.
- Exclude upcoming actions that are not yet due.
- Exclude actions completed before the digest is generated or sent.

The queue is authoritative. Email is a prompt that brings the user back to the application.

The analysis should determine how this fits the application's current email scheduling, retry, failure logging, unsubscribe, and preference behavior. Operational reminders to authorized staff may require different preference handling from community subscription email; do not assume they are identical.

## Community communications

Community communications are process deliverables, not action-reminder emails.

The application needs to distinguish at least:

- Proposed-agenda communication.
- Final-agenda communication.
- Notes-available communication.
- Staff action digest.

For phase completion, a required community communication counts as handled when it has been successfully sent or validly scheduled. If a scheduled communication is cancelled or fails before sending, the action may need to become active again. Analyze the existing communication state model and recommend precise behavior.

Audience construction must preserve existing topic-centered rules:

- Following applies to topics rather than Topic Spaces.
- Meeting interest may imply following the associated topic where that is current behavior.
- Recipients must be deduplicated across topic following, meeting interest, agenda participation, and other applicable sources.
- Public viewing does not grant editing rights or automatically create subscriptions.

## Time calculations

The cadence uses fixed application-wide values:

- Proposed agenda: 14 calendar days before the meeting.
- Final agenda: three calendar days before the meeting.
- Notes publication: 24 hours after the meeting.
- Requested correction period: three days after notes publication.
- Editing closure: preserve the existing seven-day enforcement.

During analysis, identify:

- Which meeting timestamp and timezone the application treats as authoritative.
- Whether deadlines are stored or derived.
- How rescheduling currently affects scheduled communications.
- Whether the existing seven-day check uses scheduled end, actual close, or another timestamp.

Do not change the seven-day anchor merely to make it align with this document. Document the current behavior.

When a meeting is scheduled or rescheduled inside an action's normal lead time, make the action due immediately. Its overdue calculation should begin when the action could first reasonably have been completed, not at a historical deadline that predates creation or rescheduling.

## Rescheduling, cancellation, and failure cases

The proposed design must account for:

- A meeting scheduled fewer than 14 or three days in advance.
- A meeting rescheduled after agenda communications have been scheduled.
- A meeting rescheduled after an agenda communication has already been sent.
- A scheduled communication that is cancelled.
- A communication that fails to send.
- A meeting that is cancelled before it occurs.
- A meeting whose scheduled end passes without being closed.
- Notes published after the 24-hour deadline.
- Notes published so late that fewer than three days remain in the existing editing window.
- A note-taker who becomes unavailable.
- A user's permissions changing while an action is pending or snoozed.
- Duplicate recipient paths through several followed topics or roles.

Prefer straightforward behavior and visible warnings over a complex exception workflow.

## Audit history

Do not build a separate action-completion audit trail if the application already records the underlying events. The useful history is:

- Who changed the agenda status and when.
- Who scheduled, sent, cancelled, or retried a communication.
- Who started or closed the meeting.
- Who published or edited notes.
- When an edit was rejected by the seven-day rule, if such logging is appropriate under current conventions.

Read and snooze state generally does not need to appear in the meeting's community-facing history.

## Analysis required before implementation

Inspect the current repository and produce a written gap analysis before changing code.

### 1. Map the current implementation

Identify the relevant:

- Meeting, meeting-series, agenda, agenda-item, topic, notes, follower, role, and communication models.
- Status fields and state-transition logic.
- APIs, services, commands, or controllers that implement those transitions.
- Pages and components used for meeting creation, agenda preparation, live meetings, notes, and the authenticated home page.
- Authorization rules for organizers, chairs, cochairs, champions, presenters, note-takers, followers, and administrators.
- Email composition, audience building, deduplication, scheduling, delivery, retries, and failure tracking.
- Background-job or scheduled-task infrastructure.
- Existing notification, inbox, read-state, or snooze-like infrastructure.
- Existing seven-day note-editing check and its exact time anchor.
- Tests covering any of these behaviors.

Reference concrete filenames, classes, functions, database tables, routes, and components.

### 2. Compare current behavior with this design

For every lifecycle phase, report whether the application currently provides:

- The required state.
- The required transition.
- The required communication.
- The correct audience.
- A schedulable communication.
- A reliable completion condition.
- The needed authorization rule.
- The needed timestamp.
- Adequate automated tests.

Classify each item as:

- Already supported.
- Supported with a small adjustment.
- Missing.
- Conflicting with the proposed design.
- Unclear and requiring a product decision.

### 3. Recommend the smallest coherent implementation

Prefer a derived-action service over a generalized workflow engine. Determine whether the current architecture supports something equivalent to:

```text
getVisibleMeetingActions(user, now)
```

That service should derive incomplete actions, eligibility, deadlines, urgency, and destination links from authoritative state. The dashboard and daily digest should consume the same logic so they cannot disagree.

Recommend persistence only for information that cannot be derived, especially read and snooze state.

Identify any cases where a materialized shared action record is actually necessary. Explain why before proposing it.

### 4. Produce an implementation plan

The plan should include:

- Proposed changes by file or subsystem.
- Any database migration.
- Backend derivation and authorization logic.
- Queue API or server-rendered query.
- Dashboard changes.
- Snooze/read behavior.
- Daily digest scheduling and email template.
- Community communication additions or changes.
- Rescheduling and cancellation behavior.
- Unit, integration, authorization, and end-to-end tests.
- A safe rollout sequence.

Separate required first-release work from useful later refinements.

Do not implement the changes during the analysis pass unless separately instructed.

## Acceptance criteria

### Shared behavior

- Every eligible user sees the same unmet meeting action.
- Completion by any authorized user removes it for all users.
- There is no claim, assignment, manual-completion, or permanent-dismissal workflow.
- A user can read or snooze an action without affecting anyone else.
- Followers do not receive editing authority.

### Agenda cadence

- A scheduled meeting with an incomplete proposed agenda produces an action due 14 days before the meeting.
- A Proposed agenda with incomplete finalization produces an action due three days before the meeting.
- Each phase accounts for both content status and its required communication.
- A sent or validly scheduled communication satisfies the communication portion of the phase.

### Meeting closure

- Normal meeting operation does not add unnecessary attendance reminders.
- A meeting left open after its scheduled end produces a recovery action.
- Marking the meeting Held or Cancelled clears that action.

### Notes cadence

- Closing a held meeting exposes the publish-notes action.
- The action becomes overdue 24 hours after the meeting.
- Publishing notes and sending or scheduling the notes communication clears it.
- The communication requests corrections within three days.
- Late publication does not extend the existing seven-day editing window.
- No finalization action or finalization email is created.

### Queue and digest

- The queue distinguishes needs-attention, upcoming, and snoozed actions.
- A direct link takes the user to the place where the action can be performed.
- The daily digest is sent only when the recipient has at least one unsnoozed due or overdue action.
- No user receives more than one action digest per day.
- Queue and digest use the same derivation, eligibility, completion, and deduplication logic.

## Expected analysis output

Return a concise technical design report containing:

1. Current architecture map.
2. Current lifecycle diagram or table.
3. Gap analysis against this document.
4. Recommended target design.
5. Proposed data-model changes, if any.
6. Proposed UI changes.
7. Communication and scheduling design.
8. Edge-case decisions.
9. Test strategy.
10. Phased implementation plan with concrete file references.

Call out any recommendation that would materially change an existing InteropHub behavior or contradict this design. Do not silently reinterpret product requirements to fit the current implementation.
