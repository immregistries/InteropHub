# Topic Relationship Review — Decisions Log

Append-only, reverse-chronological. One entry per round. Add new entries at
the top. Don't edit or delete past entries — if a later round reverses an
earlier call, log that as a new entry that says so, and note it in the
relevant topic's history rather than rewriting the old one.

See `README.md` for the process this log is part of, and `state.md` for the
current watermark. Full per-topic rationale for each round lives in that
round's file under `rounds/`.

---

## 2026-08-24 — Round 1 (initial baseline)

**Scope:** All 110 active topics in Topic Space #1.
**Round file:** `rounds/2026-08-24-round-1-initial-baseline.md`
**Result:** 107 relationships proposed and accepted, applied in
`db/unapplied_updates.sql` (pending release in InteropHub v0.5 as of this
writing).

**Notable decisions:**

- *Immunization CDS (ImmDS + HALO)* vs. *Immunization Decision Support
  (ImmDS)* — looked like possible duplicates. Nathan clarified ImmDS + HALO
  is the successor to ImmDS; ImmDS stays as its own topic because it's a
  published, implementable-today standard. Modeled as `SUPERSEDES`, not
  `DUPLICATE_OF` or a merge.
- *Data Export Format (DAR-Based)* vs. *Flat File Data Export* — also
  looked like possible duplicates (both propose a common flat-file export
  standard). Nathan's call: keep both, DAR-Based is a legitimate competing
  proposal worth tracking separately. Modeled as `OVERLAPS`.
- A handful of topics (*Machine Learning* beyond one link, *NITAG* beyond
  one link, *Space Health*, others) were read and considered but got no
  new relationship — the topic text didn't give a concrete-enough hook to
  a specific other topic. Treated as "reviewed, no link found," not a gap
  — see `state.md` trigger #3 for how future rounds should treat this.
- Chose to key the SQL insert by `topic_code` rather than raw
  `es_topic_id`, and wrapped it in a `WHERE NOT EXISTS` guard, so the block
  is safe to re-run and independent of ID drift between environments.

**Process decisions made in this round (apply going forward):**

- Review happens against the local dev database (refreshed daily from
  production) — no need for anything more real-time. A day of staleness
  doesn't matter for a review cadence measured in weeks/months.
- Cadence is trigger-based, not calendar-based: review when there's a
  meaningful batch of new/changed topics, not on a fixed schedule.
- Each round only needs to look at topics that are new, changed, or linked
  to something changed since the last round's watermark — not re-scan
  everything every time. (Round 1 was a necessary full sweep since nothing
  had a watermark yet.)
- Keep AI/LLM involvement entirely outside the running app — this is an
  offline, human-reviewed process. Proposals get reviewed and edited in
  conversation before anything becomes SQL.
- Keep every round's proposal file instead of deleting it after use, for
  the audit trail this log indexes.
