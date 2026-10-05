# ES Topic Import Format

Use the admin import page at `/hub/admin/es-topic-import` to paste one JSON object per line.

This import is used to upsert records in `es_topic` and to rebuild the imported topic's canonical neighborhood assignments in `es_topic_neighborhood`.

Choosing a campaign on the import page is optional. With no campaign, only topics (and their neighborhoods) are imported; `set` and `displayOrder` are ignored. With a DRAFT campaign, the campaign is first reset (its interests, comments, subscriptions and topic assignments are deleted) and assignments are rebuilt from `set` and `displayOrder`. With a non-draft campaign, topics are imported but the campaign's assignments are left unchanged.

## File format

- The payload is JSON Lines.
- Each line must be one complete JSON object.
- Do not wrap the full batch in `[` and `]`.
- Do not place commas between lines.
- UTF-8 text is expected.

## Required fields

- `topicCode`: unique short code for the topic.
- `topicName`: display name for the topic.

## Optional fields

- `topicSummary`: one-sentence summary of the topic, at most 300 characters.
- `description`: longer description shown for the topic.
- `searchKeywords`: alternate names, abbreviations, and keywords used by search.
- `topicEmoji`: emoji shown with the topic, at most 64 characters.
- `neighborhood`: one active neighborhood name, or a comma-separated list of active neighborhood names.
- `priorityIis`: integer, defaults to `0` for new topics.
- `priorityEhr`: integer, defaults to `0` for new topics.
- `priorityCdc`: integer, defaults to `0` for new topics.
- `stage`: stage name; must match a stage defined for the selected Topic Space.
- `path`: advancement path name; must match a path defined for the selected Topic Space.
- `status`: `ACTIVE`, `ARCHIVED`, or `RETIRED` (case-insensitive). New topics default to `ACTIVE`.
- `policyStatus`: free-text policy status.
- `topicType`: free-text topic type.
- `confluenceUrl`: full URL for supporting documentation.

Campaign-only fields (ignored when no campaign is selected, or when the campaign is not in DRAFT status):

- `displayOrder`: integer, defaults to `0` if omitted.
- `set`: integer set number used for campaign table assignment. Topics without a set are not assigned to a campaign table.

## Updating existing topics

Lines are matched to existing topics by `topicCode`. For an existing topic:

- A field **left out of the line** keeps its current value. This makes partial updates safe, for example a line with only `topicCode`, `topicName`, and `topicSummary`.
- A field **set to `null` or `""`** is cleared. Priorities clear to `0`. `status` cannot be cleared; omit it to leave it unchanged.
- `topicName` is always required and always overwritten.

## Neighborhood rules

- `neighborhood` must match the configured active neighborhood name exactly, ignoring case and extra surrounding spaces.
- For multiple neighborhoods, separate names with commas.
- Example multi-neighborhood value: `"FHIR, Support"`.
- If `neighborhood` is `null` or blank, the topic's neighborhood assignments are removed. If it is omitted, a new topic gets no neighborhood assignments and an existing topic keeps its current ones.
- The import does not create new neighborhoods. If a name is not already configured as an active neighborhood, the import stops on that line with an error.

## Example

```json
{"topicCode":"VX-FORECAST","topicName":"Forecast Recommendations","topicSummary":"Exchange forecast recommendations between IIS and EHR systems.","description":"Support exchange of forecast recommendation data.","searchKeywords":"forecast, recommendations, CDSi","topicEmoji":"🔮","neighborhood":"FHIR","priorityIis":3,"priorityEhr":2,"priorityCdc":1,"stage":"Monitor","policyStatus":"In Progress","topicType":"Implementation Guide","confluenceUrl":"https://example.org/wiki/forecast","displayOrder":10,"set":1}
{"topicCode":"SCHOOL-EXPORT","topicName":"School Record Export","description":"Export immunization records for school workflows.","neighborhood":"School, Support","priorityIis":2,"priorityEhr":1,"priorityCdc":0,"stage":"Gather","policyStatus":"Draft","topicType":"Use Case","displayOrder":20,"set":1}
```

Partial update of an existing topic (only the summary and status change):

```json
{"topicCode":"VX-FORECAST","topicName":"Forecast Recommendations","topicSummary":"Exchange forecast recommendations between IIS and EHR systems.","status":"ACTIVE"}
```

## If every topic belongs to the same neighborhood

Repeat the same `neighborhood` value on every line.

Example:

```json
{"topicCode":"TOPIC-001","topicName":"Topic 001","neighborhood":"FHIR"}
{"topicCode":"TOPIC-002","topicName":"Topic 002","neighborhood":"FHIR"}
{"topicCode":"TOPIC-003","topicName":"Topic 003","neighborhood":"FHIR"}
```

## Notes for the person generating the import

- Keep `topicCode` stable for existing topics so the import updates the correct record.
- Use integer values for `priorityIis`, `priorityEhr`, `priorityCdc`, `displayOrder`, and `set`.
- Leave out fields you don't intend to change; include them as `null` only when you mean to clear them.
- Escape quotes inside text values using normal JSON escaping.
- A malformed JSON line stops the import at that line.
- An unknown neighborhood, stage, or path name, an invalid `status`, or a `topicSummary` over 300 characters stops the import at that line. Lines before it have already been saved.