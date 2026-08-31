# Topic Relationship Review Process

How InteropHub's `es_topic_relationship` links get curated over time: an
offline, periodic, human-in-the-loop review — not a live feature of the
running app.

## Why this exists

Topics in a Topic Space (see `es_topic_space`, currently mainly Topic Space
#1, "Emerging Standards") can be linked to each other via typed, directed
relationships (`RELATED_TO`, `DEPENDS_ON`, `SUPERSEDES`, etc. — see
`EsTopicRelationship.RelationshipType` in the codebase for the authoritative
list and label semantics). These links help readers discover related work
instead of relying on them noticing it themselves. Deciding what should link
to what requires judgment a human should make — Nathan reviews and edits
every round before anything ships — but the first-pass proposal and the
mechanical "does this SQL actually work" step are cheap to hand to an LLM
session working against the local dev database.

Deliberately **not** built into the app itself: no AI assistance lives in
InteropHub's runtime for this. Everything below happens outside the app,
against the local database, with the outcome landing in
`db/unapplied_updates.sql` like any other schema/data change (see
`docs/database-release-practice.md`).

## Files in this folder

| File | Role |
|---|---|
| `README.md` | This file — process overview. |
| `state.md` | Current watermark: what's been reviewed, what triggers a topic for re-review next round. Updated at the end of every round. |
| `decisions-log.md` | Append-only, reverse-chronological log of what happened each round and why — the audit trail. |
| `rounds/YYYY-MM-DD-*.md` | One archived file per round: the full proposal table (`From → Type → To` + rationale) as it was presented for review. Never edited after the fact — a later round that changes a call adds a new entry to `decisions-log.md`, it doesn't rewrite an old round file. |

## The skill

The actual step-by-step procedure (queries to run, how to compute the diff
against the last round, output format, how to write and test the SQL) is
captured as a personal Claude Code skill, **`interophub-topic-relationships`**,
at `~/.claude/skills/interophub-topic-relationships/SKILL.md` on Nathan's
machine — the same place the other InteropHub dev-environment skills
(`interophub-dev-database`, `interophub-dev-db-restore`,
`interophub-dev-signin`) live, and for the same reason: it's a personal
automation layer over local dev tooling, not something that makes sense to
ship in the repo for other developers who don't have this local setup.

That said, nothing in this process is hard-blocked on the skill existing —
this README plus `state.md` is enough for a human, or a fresh LLM session
without the skill loaded, to reconstruct the procedure. The skill just saves
re-deriving it from scratch every round.

## Running a round, in brief

1. Read `state.md` for the current watermark and triggers.
2. Query `es_topic` (scoped to the relevant topic space) for topics matching
   a trigger — new, changed, or linked to something changed. Pull in their
   existing `es_topic_relationship` rows as context.
3. Read the changed/new topics' descriptions, propose relationships (new
   links, and flag any existing link that looks stale given what changed),
   write it up as a new `rounds/YYYY-MM-DD-*.md` file in the same format as
   round 1.
4. Review with Nathan in conversation — accept, reject, retype, or add rows.
5. Convert the accepted rows into a guarded `INSERT ... SELECT` (add/delete
   as needed) keyed by `topic_code`, appended to `db/unapplied_updates.sql`.
   Test it against the local database before calling it done.
6. Update `state.md`'s watermark date and add an entry to
   `decisions-log.md` summarizing the round and any notable calls.

The SQL then rides along with the normal `unapplied_updates.sql` →
`vX.Y_*.sql` release cycle — no separate deployment step.

## Scope

Currently Topic Space #1 only, since that's the space with real relationship
data so far. The process generalizes to other topic spaces if/when they
accumulate enough topics to be worth linking — `state.md` tracks baseline
coverage per topic space so this can extend without redoing round 1's work.
