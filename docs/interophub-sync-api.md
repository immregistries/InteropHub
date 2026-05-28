# InteropHub Sync API (Write-Only)

## Base URL
- Relative base: `/api`
- Version prefix: `/v1`

Example root: `/api/v1/sync`

## Authentication
- Header: `X-Api-Key: <api-key>`
- API key scope is the target workspace. InteropHub does not send workspace id.

## Source Ownership
- Source key is derived from the API key identity (agent name, or internal client id fallback).
- External identity keys are unique per `(workspace, source, external_id)`.
- InteropHub should keep external ids stable forever.

## Endpoint 1: Upsert Projects
`POST /api/v1/sync/projects/upsert`

### Request Body
```json
{
  "items": [
    {
      "externalProjectId": "PRJ-1001",
      "projectName": "Annual Partner Plan",
      "description": "",
      "projectHandle": "annual-partner-plan",
      "projectStatus": "Active"
    }
  ]
}
```

### Field Rules
- `externalProjectId`: required, immutable identity key.
- `projectName`: required, cannot be empty.
- `projectStatus`: required, must be one of: `Active`, `Paused`, `Complete`, `Closed`.
- `projectHandle`: required for non-closed projects; may be empty only when status is `Closed`.
- `description`: optional.

### Omit vs Empty
- Omitted field: preserve existing value.
- Present with `""` or `null`: clear value when field is clearable.
- Non-clearable fields (such as `projectName`, `projectStatus`) reject empty values.

## Endpoint 2: Upsert Contacts
`POST /api/v1/sync/contacts/upsert`

### Request Body
```json
{
  "items": [
    {
      "externalContactId": "CNT-208",
      "nameLast": "Rivera",
      "nameFirst": "Alex",
      "nameTitle": "Director",
      "organizationName": "Interop Hub",
      "emailAddress": "alex.rivera@interophub.example",
      "timeZone": "America/Denver",
      "contactStatus": "ACTIVE"
    }
  ]
}
```

### Field Rules
- `externalContactId`: required, immutable identity key.
- `nameFirst`: required, cannot be empty.
- `nameLast`: required, cannot be empty.
- `contactStatus`: required, must be `ACTIVE` or `INACTIVE`.
- `nameTitle`, `organizationName`, `emailAddress`, `timeZone`: optional and clearable.

### Omit vs Empty
- Omitted field: preserve existing value.
- Present with `""` or `null`: clear value when field is clearable.
- Non-clearable fields (`nameFirst`, `nameLast`, `contactStatus`) reject empty values.

## Endpoint 3: Apply Project-Contact Assignments
`POST /api/v1/sync/assignments/apply`

### Request Body
```json
{
  "items": [
    {
      "externalProjectId": "PRJ-1001",
      "externalContactId": "CNT-208",
      "operation": "add"
    },
    {
      "externalProjectId": "PRJ-1001",
      "externalContactId": "CNT-209",
      "operation": "remove"
    }
  ]
}
```

### Rules
- `operation` must be `add` or `remove`.
- Assignment table has no external id column; linkage is resolved through project/contact external ids.
- `add` is idempotent.
- `remove` is idempotent.

## Response Shape (All 3 Endpoints)
```json
{
  "total": 2,
  "successCount": 1,
  "errorCount": 1,
  "results": [
    {
      "key": "PRJ-1001",
      "operation": "project_upsert",
      "status": "updated",
      "message": "Project updated.",
      "projectId": 5012,
      "contactId": null
    },
    {
      "key": "PRJ-1002",
      "operation": "project_upsert",
      "status": "error",
      "message": "projectStatus is required and must be one of: Active, Paused, Complete, Closed.",
      "projectId": null,
      "contactId": null
    }
  ]
}
```

## Error Codes
- `400 bad_request`: malformed request body or empty `items`.
- `401 unauthorized`: missing/invalid API key.
- `403 forbidden`: workspace scope missing or invalid.

## Replay and Reliability
- Requests are designed for safe replay.
- Upsert identity is deterministic by external ids.
- Repeating assignment `add`/`remove` returns stable no-op outcomes when already in desired state.

## Operational Notes
- InteropHub is responsible for sending valid Dandelion status codes.
- External ids should never be changed after first create.
- Dandelion stores last sync timestamp on externally managed project/contact records.
