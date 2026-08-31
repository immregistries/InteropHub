# Topic Relationship Review — Current State

Machine-and-human-readable watermark for the review process described in
`README.md`. A new round updates this file as its last step. Keep this file
small and current — history belongs in `decisions-log.md` and the `rounds/`
files, not here.

## Baseline

- **Topic Space #1** (`es_topic_space_id = 1`, code `emerging-standards`):
  all 110 active topics as of 2026-08-24 have been reviewed at least once
  (round 1 — see `rounds/2026-08-24-round-1-initial-baseline.md`). A topic
  that was reviewed and deliberately got no new link (e.g. *Machine
  Learning*, *Space Health*) counts as covered — it should not be re-flagged
  by a future round unless it falls into one of the triggers below.
- No other topic space has been reviewed under this process yet.

## Last round completed

- **Round 1** — 2026-08-24. Full baseline sweep, 107 relationships proposed
  and accepted, applied to `db/unapplied_updates.sql` (pending release as of
  this writing — check `db/v0.5_*.sql` once it exists to see if it has
  shipped).

## Watermark for the next round

Next round should treat a topic in Topic Space #1 as **needing review** if
any of these are true:

1. `es_topic.updated_at > '2026-08-24'` (topic content changed since the
   last round that covered it — bump this date each round).
2. `es_topic.created_at > '2026-08-24'` (new topic, never reviewed).
3. The topic has **zero** rows in `es_topic_relationship` (either
   direction) *and* was never explicitly logged as "reviewed, no link
   found" in a round file — i.e. don't re-flag topics round 1 already
   considered and passed on.
4. The topic is linked (either direction) to a topic that matches 1 or 2
   above — pull these in as **context**, not automatically as
   "needs a new link," so the reviewer can sanity-check whether an
   existing relationship is now stale because the topic on the other end
   changed.
5. `es_topic.status` changed to `RETIRED` or `ARCHIVED` since the last
   round — flag its existing relationships as candidates for removal
   (dead links), don't auto-delete them.

After a round completes, update the two dates above to the new round's
date and add an entry to `decisions-log.md`.
