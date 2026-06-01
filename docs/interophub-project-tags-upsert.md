# InteropHub Project Tag Sync Guide

This guide describes how InteropHub should send project tags to Dandelion when calling:

- `POST /api/v1/sync/projects/upsert`

## Summary

Use `projectTags` on each project item when you want Dandelion to reconcile tags for that project.

- Omit `projectTags` to preserve existing tags.
- Send `projectTags: []` to remove all tags.
- Send `projectTags` with one or more labels to set the exact tag set.

## Payload Field

`projectTags` is an optional string array on each project item.

Example:

```json
{
  "items": [
    {
      "externalProjectId": "PRJ-1001",
      "projectName": "Annual Partner Plan",
      "projectHandle": "annual-partner-plan",
      "projectStatus": "Active",
      "projectTags": [
        "Quarterly Planning",
        "Customer Success"
      ]
    }
  ]
}
```

## Reconciliation Rules

When `projectTags` is present, Dandelion reconciles mappings to exactly the supplied set.

- Tags present in Dandelion but missing from the incoming `projectTags` array are removed from that project.
- Tags in `projectTags` not currently linked to the project are added.
- Re-sending the same list is idempotent.

## Omitted vs Empty

- `projectTags` omitted:
  - Existing project-tag mappings are preserved.
- `projectTags: []`:
  - All project-tag mappings are removed for that project.
- `projectTags: [ ... ]`:
  - Mappings are reconciled to exactly that list.

## Tag Matching and Creation

For each incoming label in `projectTags`:

1. Match existing tags by case-insensitive `tagName`.
2. If no name match, match by normalized handle.
3. If still not found, create a new tag and map it.
4. If a matched tag is inactive, reactivate it.

## Label Normalization Rules

Incoming labels are normalized before matching/creation:

- Trim leading/trailing whitespace.
- Ignore blank labels.
- Clip labels to max length 100.

## Handle Generation and Collisions

When Dandelion creates a new tag:

- A handle is derived from the label.
- If the handle already exists in the workspace, deterministic suffixes are appended.
- Example suffixing sequence: `tag`, `tag-2`, `tag-3`, ...

## Client Implementation Checklist

1. Add `projectTags` to your project upsert item model.
2. Send full desired tag list each time you intend to update tags.
3. Omit `projectTags` when you do not want to change tags.
4. Send `projectTags: []` when you need to clear all tags.
5. Keep retry behavior unchanged; endpoint semantics are replay-safe.
6. Keep `externalProjectId` stable for each project.

## Practical Examples

### Preserve existing tags (no change)

```json
{
  "items": [
    {
      "externalProjectId": "PRJ-1001",
      "projectName": "Annual Partner Plan",
      "projectHandle": "annual-partner-plan",
      "projectStatus": "Active"
    }
  ]
}
```

### Replace with exactly two tags

```json
{
  "items": [
    {
      "externalProjectId": "PRJ-1001",
      "projectName": "Annual Partner Plan",
      "projectHandle": "annual-partner-plan",
      "projectStatus": "Active",
      "projectTags": ["Planning", "Q3"]
    }
  ]
}
```

### Remove all tags

```json
{
  "items": [
    {
      "externalProjectId": "PRJ-1001",
      "projectName": "Annual Partner Plan",
      "projectHandle": "annual-partner-plan",
      "projectStatus": "Active",
      "projectTags": []
    }
  ]
}
```

## Notes

- Project core field validation rules remain unchanged.
- Tag updates are applied per project item inside the batch response model.
- Existing assignment and contact sync endpoints are unchanged by this feature.
