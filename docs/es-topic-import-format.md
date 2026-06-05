# ES Topic Import Format

Use the admin import page at `/hub/admin/es-topic-import` to paste one JSON object per line.

This import is used to upsert records in `es_topic` and to rebuild the imported topic's canonical neighborhood assignments in `es_topic_neighborhood`.

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

- `description`: longer description shown for the topic.
- `neighborhood`: one active neighborhood name, or a comma-separated list of active neighborhood names.
- `priorityIis`: integer, defaults to `0` if omitted.
- `priorityEhr`: integer, defaults to `0` if omitted.
- `priorityCdc`: integer, defaults to `0` if omitted.
- `stage`: free-text stage label.
- `policyStatus`: free-text policy status.
- `topicType`: free-text topic type.
- `confluenceUrl`: full URL for supporting documentation.
- `displayOrder`: integer, defaults to `0` if omitted.
- `set`: integer set number used for campaign table assignment.

## Neighborhood rules

- `neighborhood` must match the configured active neighborhood name exactly, ignoring case and extra surrounding spaces.
- For multiple neighborhoods, separate names with commas.
- Example multi-neighborhood value: `"FHIR, Support"`.
- If `neighborhood` is blank or omitted, the topic will be imported with no neighborhood assignments.
- The import does not create new neighborhoods. If a name is not already configured as an active neighborhood, the import stops on that line with an error.

## Example

```json
{"topicCode":"VX-FORECAST","topicName":"Forecast Recommendations","description":"Support exchange of forecast recommendation data.","neighborhood":"FHIR","priorityIis":3,"priorityEhr":2,"priorityCdc":1,"stage":"Monitor","policyStatus":"In Progress","topicType":"Implementation Guide","confluenceUrl":"https://example.org/wiki/forecast","displayOrder":10,"set":1}
{"topicCode":"SCHOOL-EXPORT","topicName":"School Record Export","description":"Export immunization records for school workflows.","neighborhood":"School, Support","priorityIis":2,"priorityEhr":1,"priorityCdc":0,"stage":"Gather","policyStatus":"Draft","topicType":"Use Case","displayOrder":20,"set":1}
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
- Escape quotes inside text values using normal JSON escaping.
- A malformed JSON line stops the import at that line.
- An unknown neighborhood name stops the import at that line.