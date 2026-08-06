# InteropHub Meetings Page Design

## Purpose

The Meetings page is the meeting-centered view of a Topic Space. It should help a user answer three questions:

1. What meetings are scheduled in this Topic Space?
2. Which of these meetings am I interested in?
3. What meetings happened previously?

The page should follow the existing visual language of the Topics page: a large central workspace, a narrower right-side column, simple bordered panels, and compact navigation. It should not reproduce the Topics grid because meetings are organized primarily by date and time rather than by advancement stage or path.

The page is reached after the user enters a Topic Space and selects **Meetings** from the Topic Space navigation.

## Access and visibility

The page inherits the access rules of its Topic Space.

- A public Topic Space and its public meetings can be viewed without signing in.
- A private Topic Space is available only to signed-in users who are authorized to enter it.
- Once a user has access to a Topic Space, the calendar shows all meetings in that space that the user is permitted to see.
- Features based on saved user interests, including **My Meetings**, require the user to be signed in.

Authorization for the Topic Space is handled before this page is displayed and does not need to be reimplemented as part of the page layout.

## Overall layout

The page should retain the existing InteropHub page structure:

1. Application header and global search
2. Topic Space navigation, with **Meetings** selected
3. Main content area containing the calendar
4. Right-side column containing meeting search and personal navigation
5. Existing site footer

The principal visual elements are:

| Area | Elements |
| --- | --- |
| Topic Space navigation | Topic Space name, Topics tab, selected Meetings tab |
| Calendar controls | Previous month, current month label, next month, Today, time zone, week-start preference |
| Main calendar | Meetings for the selected month |
| Right column | Search, meeting view selection, recently viewed meetings, upcoming My Meetings |

## Calendar controls

Place a compact control row immediately above the calendar.

### Month navigation

Include:

- Previous-month control
- Selected month and year, such as **August 2026**
- Next-month control
- **Today** control

The page initially displays the current month. Moving backward or forward changes the displayed month. The **Today** control returns to the current month.

The calendar itself is the mechanism for navigating to older meetings. A separate archive of all past meetings is not required.

### Time zone

The currently active time zone must always be plainly visible near the calendar controls. Do not rely only on abbreviations such as `ET`, because abbreviations may be ambiguous and may not reflect daylight-saving time correctly. A label such as **Eastern Time (America/New_York)** is preferable.

All meeting dates and times on this page, including items in the right column, are rendered in the selected time zone.

Behavior for a signed-in user:

1. Initialize the control from the time zone saved in the user's profile.
2. When the user selects another time zone, immediately redisplay all meeting times in that zone.
3. Save the new selection to the user's profile automatically.
4. Temporarily display a clear confirmation such as **Time zone saved to your profile**.

The confirmation should disappear after a short interval, but the selected time zone must remain visible. The user is responsible for changing the preference back if the change was temporary.

Behavior for an anonymous user:

1. Default to Eastern Time using the `America/New_York` zone.
2. Allow the user to change the time zone for the current session.
3. Do not display a profile-save confirmation.
4. Do not persist the selection beyond the current session.

Use an IANA time-zone identifier for storage and conversion rather than a fixed UTC offset. This ensures that daylight-saving changes are handled correctly.

### Start of week

Allow the user to choose whether the calendar week begins on Sunday or Monday.

- Default: Sunday
- Signed-in user: initialize from the user profile and save changes automatically
- Anonymous user: retain a change only for the current session

If a signed-in user changes the setting, display a temporary confirmation such as **Week start saved to your profile**.

## Main calendar

Use a conventional month calendar with a stable seven-column layout. Do not remove weekdays that contain no meetings, because changing the meaning or position of columns makes the calendar difficult to scan.

The week-start preference determines whether the columns run Sunday–Saturday or Monday–Sunday.

### Calendar cells

Each day cell should contain:

- The calendar date
- Zero or more meeting cards ordered by start time
- A subtle visual indication for the current day

If a day contains more meetings than can be shown comfortably, show the first meetings and a link such as **+3 more**. Activating that link should reveal the remaining meetings for the day without changing the selected month.

### Meeting card content

Each meeting card should show, as space permits:

- Start time in the active time zone
- Meeting title
- Followed or interested indicator for signed-in users
- Session-specific focus or subtitle, when one exists and space permits
- Cancelled status when applicable

The meeting title represents the meeting or meeting series. A session-specific subject may appear as secondary text.

Clicking a card opens the existing agenda page for that specific meeting occurrence on that specific date. The Meetings page does not need to reproduce or replace the agenda view.

### Visual states

Use restrained styling consistent with the Topics page.

- **My meeting:** Emphasize with a filled star, stronger title, darker border, or light green background. The star alone should not be the only distinction if practical.
- **Other meeting:** Standard white meeting card.
- **Past meeting:** Slightly muted while remaining readable and clickable.
- **Cancelled meeting:** Keep visible in the calendar and clearly label it **Cancelled**. Muted or struck styling may supplement the label but should not replace it.
- **Current day:** Subtle green outline or background on the date cell.

Cancelled meetings are intentionally visible in the calendar so users can understand what happened to a previously scheduled event. They should not appear in general meeting lists or produce a separate cancelled-meetings view.

## Right-side column

The right-side column should use the same bordered-card treatment as the Topics page. Arrange the cards in the following order.

### Search

Provide a search field scoped to the current Topic Space.

Search should cover useful meeting text such as:

- Meeting title
- Session title or focus
- Meeting description
- Agenda text, if already indexed and appropriate for the existing search implementation

Search results should identify the meeting title and occurrence date. Selecting a result opens the existing agenda page for that occurrence.

The interface must make it clear that this search applies to the current Topic Space, even if the global InteropHub search remains visible in the application header.

### Explore

Provide three simple meeting views:

- **All Meetings**
- **My Meetings**
- **Past Meetings**

These are view selections, not a large filter system.

#### All Meetings

This is the default. It displays all visible meetings in the selected month. Meetings followed by the signed-in user remain visually emphasized.

#### My Meetings

For a signed-in user, this limits the displayed calendar to meetings the user follows or has marked as being of interest.

For an anonymous user, selecting **My Meetings** should present a concise sign-in invitation explaining that sign-in is required to follow meetings. Do not show an unexplained empty calendar.

#### Past Meetings

This supports finding prior meetings without creating a complete archive list in the sidebar. Selecting it should take the user to a practical chronological presentation of completed meetings, newest first, or otherwise place the calendar into a clearly past-focused state. The implementation should reuse the same meeting-occurrence links to the existing agenda pages.

The normal calendar remains available for navigating directly to any earlier month. Past Meetings is primarily a convenience for users looking for the most recent completed sessions.

Do not add filters for associated topic, meeting series, date range, materials, or cancelled status in the initial implementation. Associated meetings are already available from individual Topic pages, and the calendar supplies month-based navigation.

### Recently Viewed Meetings

Show recently opened meeting occurrences. Use the heading **Recently Viewed Meetings**.

Each item should include:

- Meeting title
- Occurrence date

The occurrence date is essential because the same meeting title may recur. Selecting an item opens the agenda page for that exact occurrence.

For a signed-in user, retain this information using the same general recently-viewed approach used for Topics. For an anonymous user, retain it only for the current session.

### My Upcoming Meetings

For a signed-in user, show the next several upcoming occurrences of meetings the user follows.

Each item should include:

- Meeting title
- Date
- Time in the active time zone

Include a **View all my meetings** link that selects the My Meetings view.

Do not include cancelled meetings in this list. A cancellation remains visible when the user looks at the applicable calendar date.

For an anonymous user, either omit this card or replace its contents with a short sign-in invitation. Avoid displaying an empty personalized panel.

## Following meetings

The page should distinguish between a meeting series and a meeting occurrence:

- A meeting series represents the recurring meeting the user follows.
- A meeting occurrence represents the meeting held on a specific date and is the item opened in the agenda view.

The user's interest should normally be attached to the meeting series so future occurrences automatically appear under My Meetings. Recently Viewed Meetings, calendar cards, and agenda links refer to individual occurrences.

If the existing data model follows individual occurrences instead, Claude should identify that difference before changing the schema rather than silently assuming series-level following.

## Empty states

Use explicit, useful empty-state messages.

| Situation | Suggested message |
| --- | --- |
| No meetings in selected month | No meetings are scheduled in this Topic Space for this month. |
| No followed meetings in selected month | None of your meetings occur this month. |
| User follows no meetings | You are not following any meetings in this Topic Space. |
| No past meetings | No past meetings are available in this Topic Space. |
| Search has no results | No meetings in this Topic Space match your search. |
| Anonymous user requests My Meetings | Sign in to follow meetings and see them here. |

## Responsive behavior

On a narrow screen:

- Move the right-side cards below the main calendar.
- Preserve the selected time zone where it remains visible above the calendar.
- Allow horizontal scrolling only as a fallback; a compact day or week presentation may be used if the existing application has an established responsive calendar pattern.
- Do not abbreviate meeting titles so aggressively that recurring meetings become indistinguishable.

## Accessibility and usability

- Do not communicate followed, past, or cancelled status by color alone.
- Give previous month, next month, Today, star, and view controls accessible labels.
- Ensure meeting cards are keyboard reachable and have visible focus treatment.
- Include the full local date, time, and active time zone in the accessible name or details for each meeting occurrence.
- Preserve predictable calendar column positions.
- Use actual text for **Cancelled**, not only strikethrough styling.

## Initial implementation scope

The initial page should implement:

1. Current-month calendar with previous, next, and Today controls
2. Visible and changeable time zone
3. Automatic profile persistence and temporary confirmation for signed-in users
4. Session-only time-zone behavior for anonymous users, defaulting to `America/New_York`
5. Sunday/Monday week-start preference with corresponding persistence behavior
6. All Meetings, My Meetings, and Past Meetings views
7. Visual emphasis for followed meetings
8. Cancelled meetings visible only in the calendar-oriented presentation
9. Topic Space-scoped search
10. Recently Viewed Meetings
11. My Upcoming Meetings for signed-in users
12. Links from every meeting occurrence to the existing agenda page

The initial implementation does not require:

- A new agenda view
- A general-purpose meeting archive
- Topic or meeting-series filters
- A list of cancelled meetings
- A materials-only filter
- A separate five-day calendar whose columns change according to meeting activity

## Acceptance criteria

- Opening Meetings displays the current month and all permitted meetings in the current Topic Space.
- The active time zone is always clearly visible.
- Every displayed date and time uses the active time zone.
- Changing the time zone updates the calendar and right-column meeting times immediately.
- For a signed-in user, changing time zone or week start saves the setting and briefly confirms that it was saved.
- For an anonymous user, time zone and week-start changes last only for the session; the time zone initially defaults to `America/New_York`.
- A signed-in user's followed meetings are visually distinguishable from other meetings.
- All Meetings, My Meetings, and Past Meetings behave as described.
- An anonymous user selecting My Meetings receives a sign-in explanation rather than an unexplained empty result.
- Cancelled occurrences remain visible and labeled on the calendar but are excluded from My Upcoming Meetings and general lists.
- Recently Viewed Meetings identifies both meeting title and occurrence date.
- Search is limited to the current Topic Space.
- Clicking a meeting or search result opens the existing agenda page for the correct occurrence.
- The page retains the established InteropHub visual language without introducing unnecessary styling or a large filter system.
