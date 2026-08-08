# ES Agenda: Live Meeting Notes, Outcomes, and Confluence Export

## Purpose

Enhance the participant-facing `es/agenda` page so that it develops naturally from a planned agenda into a live meeting record and, ultimately, the final meeting record.

Meeting organizers already use `es/meeting-workspace` to:

- take structured notes;
- record outcomes;
- start and end the meeting; and
- advance through agenda items.

This change should expose the appropriate parts of that existing meeting state on `es/agenda`. It should also update the existing Confluence export so the exported final record includes notes and outcomes while retaining the familiar HL7 agenda format.

## Design Principle

Use one consistent representation throughout the meeting lifecycle:

- **Before the meeting:** planned agenda;
- **During the meeting:** planned agenda with notes and outcomes appearing as they are recorded;
- **After the meeting:** final agenda, notes, and outcomes; and
- **In HL7 Confluence:** the same final content in the familiar three-column table.

The page should not introduce a separate live-notes view or a separate minutes format. Participants should simply see the agenda becoming the meeting record.

## Existing Page Roles

### Organizer workspace

`es/meeting-workspace` remains the organizer and scribe interface. Its existing editing, automatic save, organizer synchronization, meeting-state management, and agenda-item advancement behavior should continue to be used.

### Participant agenda

`es/agenda` is the read-only participant view. It should display:

- the planned agenda;
- the agenda item currently being discussed;
- notes as the scribe records them;
- outcomes associated with each agenda item; and
- links from agenda topics to their InteropHub topic pages.

This change is targeted at `es/agenda`; it should work with the existing capabilities of `es/meeting-workspace` rather than recreate them.

## Participant Experience on `es/agenda`

### Preserve the three-column agenda

Continue to use the existing columns:

1. **Topic / Time**
2. **Agenda**
3. **Presenter(s)**

The middle **Agenda** column should contain the planned agenda followed by notes and outcomes. Do not add separate Notes or Outcomes columns. Additional narrow columns would make hierarchical notes difficult to read and would depart from the familiar HL7 format.

The Agenda column should receive most of the available width. A reasonable desktop allocation is approximately:

- Topic / Time: 22 percent;
- Agenda: 63 percent; and
- Presenter(s): 15 percent.

Exact widths may be adjusted to fit the existing InteropHub layout and AIRA web styles.

### Agenda-item content order

Within each Agenda cell, render content in this order:

1. The planned agenda text, without an additional **Agenda** label.
2. A **Notes** heading followed by a hierarchical bullet list.
3. An **Outcomes** heading followed by a bullet list, but only when outcomes exist.

Example of the content within one Agenda cell:

> Following the recent OAuth discussion, we will examine certificate renewal and management for direct HL7 v2 exchange and IZ Gateway connections, including automation, ownership, and the need for shared community guidance.
>
> **Notes**
>
> - Four concerns were identified:
>
>   - Identity assurance
>   - Transport security
>   - Credential lifecycle
>   - Operational simplicity
>
> - The DigiCert portal banner has caused confusion.
> - The change does not affect `phiz-root-ca`.
> - Participants discussed responsibility for renewal:
>
>   - Organizations need clear ownership.
>   - Automated renewal could reduce operational burden.
>
> **Outcomes**
>
> - Confirmed that `phiz-root-ca` is not affected.
> - Nathan will draft guidance covering certificate ownership and renewal.
> - Automated renewal will be considered as a future topic.

### Notes are structured lists

Notes must render as real, separate list items. They must not be combined into paragraphs or represented as lines containing decorative bullet characters.

Preserve the hierarchy created by the scribe:

- each note is an individual list item;
- child notes are nested beneath their parent note;
- indentation communicates the relationship between notes; and
- sentence fragments and complete sentences are both valid note content.

The note hierarchy is meaningful meeting information. Preserve it consistently through:

- editing in `es/meeting-workspace`;
- storage and retrieval;
- display on `es/agenda`;
- incremental participant updates; and
- Confluence export.

Any participant correction or comment capability associated with individual notes should continue to identify the specific note item, not merely the agenda item or an undifferentiated text block.

### Showing the current agenda item

InteropHub already knows when a meeting has started and which agenda item is current. Use this state to identify the row presently being discussed.

The current row should receive a clear but restrained treatment, such as:

- a light background highlight;
- a colored left border; and/or
- a small **Current topic** indicator beside the topic name.

The treatment should:

- make it immediately apparent where the meeting is;
- move to the next row when the organizer advances the meeting;
- not alter column widths or cause the page to shift; and
- not obscure the agenda, notes, topic link, time, or presenters.

When an agenda item becomes current, display its **Notes** heading even if the first note has not yet been entered. This makes the transition into note-taking natural. Other agenda items without notes should continue to display only their planned agenda content.

### Participant updates

While the meeting is underway, `es/agenda` should retrieve and display changed meeting content approximately every 5–10 seconds.

Updates should include, as applicable:

- newly added notes;
- edits to existing notes;
- changes in note indentation or ordering;
- deleted notes;
- newly added or revised outcomes;
- agenda or presenter changes; and
- movement of the current-agenda-item indicator.

Apply updates without a full-page refresh. Preserve the participant's scroll position and avoid redrawing unaffected agenda rows when possible.

Do not add participant-facing save messages, synchronization messages, update timestamps, or a **Live notes** label. Participants should see notes appear naturally within the agenda. The changing content and current-topic indicator provide sufficient context.

It is acceptable for the participant view to show a saved snapshot a few seconds behind the organizer workspace. The participant view does not need to reproduce each keystroke.

### Outcomes

Outcomes should appear immediately after the notes for the agenda item from which they arose.

- Display the **Outcomes** heading only when at least one outcome exists.
- Render outcomes as real bullet-list items.
- Preserve any internal relationship between an outcome and the note that supports it.
- Do not require the participant view or Confluence export to display that internal relationship explicitly.

The notes explain the discussion. The outcomes identify what the discussion produced. They should remain visually distinct even when an outcome repeats or summarizes an important note.

### Topic links

Continue linking agenda topic names to their corresponding InteropHub topic pages. These links are an important advantage of the InteropHub record and must remain present in both `es/agenda` and the Confluence export.

Where InteropHub already supports additional useful links from notes or outcomes to topics, meetings, resources, or other records, preserve those links in the participant view and export them when the Confluence representation supports them.

### Responsive behavior

On desktop screens, retain the three-column table.

On narrow screens, rows may reflow into stacked agenda-item sections if the existing responsive table treatment requires it. The stacked order should be:

1. topic and time;
2. presenters;
3. planned agenda;
4. Notes and their nested list; and
5. Outcomes, when present.

Nested note indentation must remain distinguishable on small screens without pushing note text into an unusably narrow column.

## Confluence Export

### Preserve the familiar HL7 table

Adjust the existing agenda-only Confluence export rather than introducing a second export format.

The exported page should retain the standard three-column structure:

| Topic / Time | Agenda | Presenter(s) |
| --- | --- | --- |
| Linked topic name and scheduled time | Planned agenda, followed by Notes and Outcomes | Presenter names |

The export must not add separate Notes or Outcomes columns. Notes and outcomes belong with the agenda item and should be placed in the existing Agenda cell.

### Agenda cell export rules

For each agenda item:

1. Export the planned agenda first, with no internal **Agenda** heading.
2. If notes exist, add a new block with the heading **Notes**.
3. Export notes as a properly nested bulleted list.
4. If outcomes exist, add a new block with the heading **Outcomes**.
5. Export outcomes as a bulleted list.

Spacing must make the planned agenda, Notes section, and Outcomes section visibly distinct. Do not flatten list items into paragraphs, line breaks, or bullet characters inside a single paragraph.

### Export stages

Continue using a manual copy workflow:

1. Copy the agenda-only export to Confluence when the planned agenda is ready for publication.
2. After the meeting notes and outcomes are finalized, generate the export again and replace or update the Confluence content with the complete record.

Automatic Confluence synchronization is not part of this change.

### Links in Confluence

Preserve the existing links from agenda topic names to InteropHub topic pages.

The final Confluence page should remain useful on its own, while the links allow readers to move from a specific meeting record to the durable topic record and related work in InteropHub.

### Confluence compatibility

Generate list and table markup that Confluence recognizes as native structure:

- real table cells;
- separate paragraphs or blocks within the Agenda cell;
- real unordered lists; and
- nested unordered lists for indented notes.

Verify that copying the generated output into HL7 Confluence preserves:

- table structure;
- line and block separation;
- nested indentation;
- topic links;
- emphasis on the Notes and Outcomes headings; and
- readable spacing in long agenda rows.

## Meeting Lifecycle Display

### Before the meeting

- Show the planned three-column agenda.
- Do not display empty Notes or Outcomes sections.
- Topic names remain linked to InteropHub topics.

### During the meeting

- Identify the current agenda item.
- Show the **Notes** heading for the current item.
- Display note changes within approximately 5–10 seconds.
- Preserve nested note indentation.
- Display Outcomes when they are recorded.
- Do not show participant-facing save or update-status messages.

### After the meeting

- Retain the same agenda layout.
- Display the completed notes and outcomes under each applicable agenda item.
- Remove the current-topic treatment when the meeting is no longer in progress.
- Use this view as the participant-facing meeting record.

## Scope Boundaries

This change does not require:

- a new scribe editor;
- changes to the existing organizer automatic-save message;
- a participant-facing save or synchronization indicator;
- agenda locking or agenda edit-history notices;
- a separate minutes page;
- a fourth Notes or Outcomes column;
- automatic synchronization with Confluence; or
- publication of every keystroke.

## Acceptance Criteria

The change is complete when:

- `es/agenda` continues to show the existing three-column agenda;
- the current agenda item is visibly identifiable while the meeting is underway;
- notes recorded in `es/meeting-workspace` appear on `es/agenda` within approximately 5–10 seconds;
- updates do not require a participant page refresh;
- participant scroll position remains stable during updates;
- each note remains a separate list item;
- nested note indentation is preserved accurately;
- outcomes appear below notes only when outcomes exist;
- no participant-facing save timestamp or live-update message is added;
- topic links continue to work;
- the Confluence export retains the three-column table;
- the Confluence Agenda cells contain planned agenda text followed by Notes and Outcomes;
- Confluence notes and outcomes are exported as real bullet lists;
- nested note indentation survives copying into Confluence; and
- the agenda-only and complete-record exports both remain readable and familiar to HL7 participants.
