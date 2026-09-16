# Meeting Attendance Console Design

## Purpose

InteropHub currently lets participants sign themselves in for a meeting shortly before or during the meeting. This self sign-in is still the preferred attendance signal because it can collect useful follow-up information, topic interest, and survey responses.

In practice, not every attendee signs in. Meeting staff can often see additional participants in Zoom and should be able to record that those people attended without treating that staff-entered observation as a replacement for self sign-in.

This design expands attendance from a single participant sign-in flow into a staff-facing attendance console that can support:

- self-reported attendance;
- staff-observed attendance;
- incomplete or uncertain attendee identity;
- later cleanup and reconciliation;
- occurrence-level RSVP intent; and
- topic-aware facilitation during a live meeting.

## Conceptual Model

Attendance should distinguish between what a person says about their own participation and what meeting staff observed.

### Meeting Series Membership

Series membership answers:

> Is this person generally connected to this recurring meeting?

This is already represented by `es_topic_meeting_member`.

Series membership should continue to be used for recurring meeting interest, communications, and the general community around a meeting series. It should not be treated as proof that someone attended a specific occurrence.

### Meeting RSVP

Occurrence RSVP answers:

> Does this person expect to attend this specific meeting occurrence?

This is not currently represented directly. A future implementation should consider a new occurrence-level RSVP model, likely tied to `es_meeting_id`, with responses such as:

- `COMING`
- `MAYBE`
- `NOT_COMING`

RSVP is intent. It should help staff understand who may show up, but it should not count as attendance.

### Self-Reported Attendance

Self-reported attendance answers:

> Did this attendee personally sign attendance for this meeting?

This should remain the strongest attendance signal. It should continue to drive participant-facing flows such as topic-interest updates and attendance-triggered surveys.

If a participant self-signs after a staff member observed them, the self sign-in should update or supersede the observed state for that person.

### Observed Attendance

Observed attendance answers:

> Did meeting staff see this person attend?

Observed attendance is a staff-entered shadow record. It should appear in the complete attendance view, but it should not remove the expectation that the attendee signs in personally.

Observed attendance should support incomplete identity. Staff may only know a Zoom display name, a partial name, or an organization. That information is still useful and should be recorded.

## Identity Handling

Observed attendance should include a required `display_name` field. This field captures exactly what staff know or saw, including partial or cryptic Zoom names.

Optional identity fields may include:

- first name;
- last name;
- organization;
- email;
- normalized email;
- linked user id; and
- staff notes.

Email is the safest reconciliation key. If an observed record has an email address and the attendee later self-signs with that email, the system should update the same attendance record rather than create a duplicate.

Name-only observed records should not be automatically matched to later self-sign-ins. Names are too ambiguous for safe automatic merging. Staff should be able to edit the observed record later if they discover the attendee's email address.

Observed-only records may be removed by authorized staff when they were entered in error or replaced by a better record. Self-reported attendance should not be removable through this workflow.

## Suggested Attendance States

The UI can derive display states from a small set of fields rather than relying on a separate status column.

Useful states include:

- `Signed in` when the attendee self-reported attendance.
- `Observed` when staff observed the attendee but the attendee has not self-signed.
- `Observed, email missing` when staff entered a display name without an email.
- `Invite sent` when staff sent a sign-in or registration invitation.
- `Removed` for observed-only records that staff removed from the active view.

The exact database representation can vary, but likely fields on `es_meeting_attendance` include:

- `display_name`
- nullable `email` and `email_normalized`
- `self_signed_at`
- `observed_at`
- `observed_by_user_id`
- nullable `removed_at`
- nullable `removed_by_user_id`
- optional observation note

The existing attendance table should remain the primary record. A separate shadow attendance table is not recommended.

## Attendance Console

Create a dedicated staff-facing attendance console, likely linked from the Meeting Workspace:

`/es/meeting-attendance?meetingId={id}`

This page would support the person responsible for watching attendance during and after a meeting.

The console should be available to meeting staff during the same practical window as meeting note work:

- beginning 15 minutes before the scheduled start;
- during the meeting;
- after the meeting while notes remain editable; and
- until the meeting is closed or the note-editing window ends.

The console should be optimized for fast staff work. It should avoid making staff choose between preserving imperfect information and entering nothing.

## Suggested Console Views

The first version can be simple, but the design should allow the console to grow into a live facilitation tool.

### Attendance Summary

Show compact counts such as:

- signed in;
- observed;
- expected but not seen;
- missing email; and
- regrets.

### People List

Group or filter people by practical state:

- signed in;
- observed;
- expected, not yet seen;
- regrets;
- missing identity; and
- removed observed records.

Rows can show badges such as:

- `Series member`
- `RSVP yes`
- `Topic follower`
- `Presenter`
- `Observed`
- `Signed in`
- `Email missing`
- `Invite sent`

Useful row actions include:

- mark observed;
- add or edit email;
- send sign-in invitation;
- remove observed record;
- view attendance details.

### Add Observed Attendee

Provide a compact form with:

- display name;
- optional first name;
- optional last name;
- optional organization;
- optional email;
- optional note; and
- optional send-invite checkbox when an email is available.

### Topic-Aware View

A later version should help staff answer:

> We are discussing this topic. Are the interested people here?

For each agenda topic, the console can show:

- presenters;
- topic followers;
- people who RSVP'd for the meeting;
- people who self-signed attendance;
- people observed by staff; and
- interested or expected people not yet seen.

This would turn attendance tracking into a meeting facilitation tool, not only a recordkeeping tool.

## Invitations

When an observed attendee has an email address, staff should be able to invite them to use InteropHub and sign attendance.

This should follow the pattern used by managed topic followers:

- detect whether the email belongs to an existing user;
- distinguish registered, unverified, and not registered contacts;
- send the appropriate email;
- log sends in `email_send_log`;
- show the most recent send timestamp; and
- use a resend warning/cooldown for non-admin users.

Meeting-specific email reasons should be added rather than reusing topic-follower reasons. Candidate reasons:

- `MEETING_ATTENDANCE_OBSERVED_INVITE`
- `MEETING_ATTENDANCE_REGISTRATION_INVITE`
- `MEETING_ATTENDANCE_VERIFY_EMAIL`

Email copy should make clear that staff observed them in the meeting and is asking them to confirm/sign attendance themselves.

## Participant RSVP

Adding RSVP would improve the attendance console because staff could compare expected participants with actual participants.

A simple participant-facing RSVP action could appear on the agenda page before the meeting:

- `I plan to attend`
- `Maybe`
- `I cannot attend`

This is separate from attendance sign-in. A participant who RSVP'd still needs to sign attendance when they attend.

RSVP may also capture topic interest for the specific meeting occurrence. This could later support the topic-aware console view.

## Suggested Implementation Plan

### Phase 1: Observed Attendance Foundation

Extend `es_meeting_attendance` to support observed attendance and incomplete identity.

Likely work:

- add `display_name`;
- make email fields nullable if needed;
- add self-sign and observed timestamps;
- add observer user id;
- add observed-only removal fields;
- update the attendance model and DAO;
- update self-sign-in logic so self-signing sets `self_signed_at`;
- preserve existing topic-interest and survey behavior for self sign-in only;
- update agenda display so observed rows are visible in full attendance but do not satisfy the viewer's self sign-in check.

### Phase 2: Staff Attendance Console

Create the dedicated attendance console linked from Meeting Workspace.

Likely work:

- new servlet/page for meeting attendance management;
- permission check aligned with meeting staff access;
- add observed attendee form;
- attendance table with signed-in and observed states;
- edit incomplete observed attendee identity;
- remove observed-only records;
- counts for signed-in, observed, and missing-email attendees.

### Phase 3: Invitation Support

Add email invitations for observed attendees with known email addresses.

Likely work:

- meeting attendance invitation email reasons;
- email templates;
- service method to classify registered/unverified/not registered contacts;
- email logging;
- last-sent display;
- resend warning/cooldown similar to managed topic followers.

### Phase 4: Occurrence RSVP

Add meeting-occurrence RSVP separate from attendance.

Likely work:

- new RSVP table/model tied to `es_meeting_id`;
- participant-facing RSVP controls on the agenda page;
- staff-facing RSVP visibility in the attendance console;
- support for `COMING`, `MAYBE`, and `NOT_COMING`;
- optional RSVP note.

### Phase 5: Expected Attendee and Topic-Aware Views

Expand the console into a facilitation dashboard.

Likely work:

- combine series members, RSVP responses, presenters, topic followers, self-signed attendees, and observed attendees;
- show expected-but-not-seen people;
- add agenda-topic filters or groupings;
- show whether interested people are present for the current topic.

## Design Principles

- Self sign-in remains the preferred and strongest attendance signal.
- Staff observation supplements attendance but does not replace participant confirmation.
- Imperfect identity should be recordable.
- Email-based reconciliation should be automatic; name-based reconciliation should be manual.
- RSVP, attendance, and series membership should remain separate concepts.
- The staff workflow should be fast enough to use during a live Zoom meeting.
