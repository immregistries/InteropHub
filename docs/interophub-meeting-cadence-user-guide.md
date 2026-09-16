# Meeting Cadence Actions — User Guide

This describes the "action needed" functionality added to InteropHub: a
running checklist that tells the right person, at the right time, what
meeting-related task is due — instead of relying on someone to remember.
It's meant as source material for training; it describes what people will
see and do, not the internal implementation.

## The problem this solves

Every meeting InteropHub tracks goes through the same four checkpoints:
publish a proposed agenda, finalize that agenda, close the meeting out
afterward, and publish the notes. Nothing enforced any of that before — it
was easy for a step to quietly get skipped. This feature surfaces each
outstanding step to the person responsible for it, both inside the app and
by email, until it's done.

Nothing new is "stored" about whether a step is complete. The system always
looks at what's actually true (has the agenda been published, has the
meeting ended, have notes been published) and derives the checklist fresh
each time — so it can never say "still pending" about something that's
already been done through some other path.

## The four actions

| Action | Appears when | Due | How to complete it |
|---|---|---|---|
| **Publish proposed agenda** | Meeting hasn't reached a finalized agenda yet | 14 days before the meeting (or immediately, if the meeting was created inside that window) | Publish the agenda and send the proposed-agenda notice — or finalize the agenda directly, skipping this step. Do this before the meeting; it isn't meaningful once the meeting has already happened. |
| **Finalize agenda** | Meeting hasn't started yet | 3 days before the meeting (or immediately, if created inside that window) | Finalize the agenda and send the final-agenda notice. Like the step above, this has to happen before the meeting starts. |
| **Close meeting** | The meeting's scheduled end time has passed and it was never started, ended, or cancelled | The moment the scheduled end time passes | Close the meeting or mark it cancelled. |
| **Publish notes** | The meeting has been marked completed | 24 hours after the meeting completed | Publish the notes and send the notes-available notice — within 7 days of the meeting, the same window in which note edits are allowed. Notes can't be published for review once that window closes, so don't let this slip. |

"Close meeting" specifically targets meetings that were **forgotten** —
never started and never explicitly ended. A meeting that's actively running
past its scheduled end time uses **End meeting** instead (see below), not
this action.

## Where people see this in the app

**Header badge.** Every signed-in page shows an "Action needed" badge in
the top navigation whenever the signed-in user has at least one *overdue*
action. The number is a live count — it goes away as soon as the action is
resolved or snoozed. Clicking it goes to the dashboard.

**Dashboard (`/welcome`).** The signed-in home page lists every visible
action in three groups:

- **Needs attention** — overdue.
- **Upcoming** — due, but the deadline hasn't passed yet.
- **Snoozed** — temporarily hidden by the user (see below).

Each row shows the meeting name, the instruction, a due/overdue badge, a
button to jump straight to the right page (agenda or meeting workspace),
and controls to mark it read, snooze until tomorrow, or snooze until next
week. An unread action shows a small dot until it's opened or marked read.

Snoozing is personal — it only affects what that one person sees, and it
also suppresses that specific action from that person's daily digest email
until the snooze expires. It doesn't affect what anyone else sees, and it
doesn't stop the deadline itself from having passed.

**Who sees an action in the dashboard at all:** anyone who can already
manage that meeting — the chair, scribe, an active cochair, an accepted
presenter, a Topic-Space admin, or a site admin. This is deliberately broad
(same rule the rest of the meeting-management pages use) — an admin should
always be able to open and act on anything, even if they're not the one who
gets emailed about it (see below).

## Assigning chair and scribe

Every meeting can have a **Chair** and a **Scribe** assigned, and this is
what most of the notification targeting is based on — so this is the single
most important thing for a meeting organizer to set.

- On the meeting's **agenda page**, anyone who can edit the meeting sees a
  "Chair / Scribe" field. Clicking it opens two dropdowns to assign or clear
  either role from the list of registered users.
- Once a meeting is **in session**, the **meeting workspace** page also lets
  an eligible user assign or reassign Chair/Scribe live, in case a different
  person is actually running that particular occurrence (e.g., a co-chair
  filling in). A live "current chair"/"current scribe" shows separately if
  it differs from the pre-planned one.

A meeting with no chair and no scribe assigned will fall back to a wider
group getting notified (see the fallback chain below) — which is exactly
the situation this feature is meant to reduce.

## Buttons on the meeting workspace page

| Button | Enabled when |
|---|---|
| **Start session** | Agenda is finalized (or the meeting is already completed) and the scheduled start window is open |
| **End meeting** | Meeting is currently in session |
| **Publish notes for review** | Meeting is completed and notes haven't been published yet — publishing notifies the community that notes are ready for review |
| **Close meeting** | Scheduled end time has passed and the meeting was never started (the "forgotten meeting" case) |

Anyone who can manage the meeting can click these — the same broad rule as
dashboard visibility above. This is intentionally *not* narrowed to match
who gets emailed: the goal is only to control who's proactively notified,
not who's allowed to act.

## The daily digest email

Once a day (6:00 AM server time), anyone with at least one **overdue**
action across any meeting gets a single email — "InteropHub Daily Digest" —
listing every overdue item assigned to them, each with a direct link.

Unlike the in-app dashboard (which shows an action to *everyone* who could
manage that meeting), the digest is targeted: each overdue action goes only
to the specific person or people actually responsible for it, so nobody
gets an email about a task that isn't theirs. This is a deliberate,
per-action-type set of rules:

**Publish proposed agenda / Finalize agenda** — agenda upkeep is the
chair's job:
1. The chair (however currently assigned).
2. If there's no chair, the meeting's **topic champions**.
3. If there are none of those, the Topic-Space's **admin** members.
4. If there are none of those either, **all site administrators**.

**Publish notes** — publishing notes is the scribe's job, or the chair's if
there's no scribe:
1. The **scribe** (however currently assigned).
2. If there's no scribe, the **chair**.
3. Then Topic-Space admins, then site administrators, same as above.

**Close meeting** ("forgotten meeting") — kept intentionally broad, since
by definition nobody stepped in to run this meeting:
1. Anyone with a meeting-specific role: chair, scribe, an active cochair, an
   accepted presenter, or a topic champion.
2. Then Topic-Space admins, then site administrators, same as above.

In every case, **site administrators are the last resort** — they only get
an email about a meeting when nobody more specific (a meeting role, a
Topic-Space admin) exists at all. This is deliberate: admins can always
*see* and act on everything from the dashboard, but the goal of the digest
is targeted reminders, not an org-wide broadcast that trains people to
ignore it. If something genuinely isn't getting done, it eventually
surfaces to admins as visibility that the responsible person didn't act —
it just doesn't happen by default every day.

### What this means for setup

For the digest to actually reach the right person instead of falling back
to admins, each meeting should have:

- A **chair** assigned (covers agenda actions, and is the fallback for
  notes if there's no scribe).
- A **scribe** assigned, if you want notes-publishing to go to someone
  specific rather than falling back to the chair.

And each **Topic Space** should have at least one member with the **ADMIN**
role (`/admin/es/topic-spaces` → open a space → Members), so that a meeting
with no chair/scribe/champion still lands on someone space-specific rather
than skipping straight to every site administrator.

## Quick reference: what a meeting organizer should do

1. When creating or reviewing a meeting, set its **Chair** (and **Scribe**,
   if a different person will be taking notes) on the agenda page.
2. Watch the **"Action needed"** badge in the header — it only lights up
   when something you're responsible for is overdue.
3. Use the dashboard's **Needs attention / Upcoming / Snoozed** groups to
   work through what's due; snooze anything you've deliberately deferred so
   it stops nagging (in-app and by email) until the date you pick.
4. If you're not receiving digest emails you'd expect for a meeting you
   run, check that you're actually set as its chair or scribe — the digest
   only reaches specifically-responsible people, by design.
