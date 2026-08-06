# AIRA Web Integration — Start Here

Read this before adding a new general-purpose visual component (table, card, button, layout primitive, badge, etc.) to InteropHub's local CSS.

InteropHub is currently the only application consuming `aira-web`. `aira-web` is expected to grow into the shared design system for multiple AIRA applications. That means: **anything reusable belongs upstream in `aira-web`, not in InteropHub's local stylesheets.** Local CSS in this project should only ever hold InteropHub-specific mechanics — JavaScript hooks, drag-and-drop state, a specialized editor surface — never a general-purpose visual component that another AIRA application would plausibly also want.

## Current state

- InteropHub consumes `org.immregistries:aira-web-components` / `aira-web-theme` version **0.1.8** (see `pom.xml`).
- [`reference/aira.css`](reference/aira.css) is a committed, read-only snapshot of the exact CSS this version ships. Grep or read it directly — don't unzip the theme jar to check what a class does.
- [`components-guide.md`](components-guide.md) and [`adoption-guide.md`](adoption-guide.md) are mirrors of the same-named docs from the `aira-web` project's own `docs/`, current as of 0.1.8.
- [`aira-css-proposed-changes.md`](aira-css-proposed-changes.md) is the active queue of gaps proposed upstream but not yet implemented. Its revision-history table at the top links every past round.
- `aira-css-changes-revision-N.md` files are handoff notes written by the `aira-web` agent describing exactly what shipped in each release.
- `Migrating Servlets to AIRA CSS.md` is the detailed guide for migrating a legacy servlet page onto the shared shell; section 14 has the full decision process referenced below.

## The rule, before writing local CSS

1. Check `reference/aira.css` and the two guide docs above — does a shared `aira-*` component already do this?
2. If not: would another AIRA Web application reasonably want this too? If yes, it belongs upstream, not in this project.
3. If it's genuinely InteropHub-specific (a JS selector hook, drag/drop state, a specialized editor), keep it local.
4. If it's a real gap and reusable, write it up in `aira-css-proposed-changes.md` using the template already in that file, instead of inventing a local component. A narrowly-scoped, clearly-marked *temporary* local workaround is fine while a proposal is pending.

This is exactly what happened with the topic board: instead of permanently forking a grid-table component, InteropHub proposed `aira-matrix-table` / `aira-entity-card` upstream (0.1.5) plus two follow-up fixes (0.1.6). `topic-board.css` now only holds drag-and-drop mechanics that `aira-web` deliberately doesn't provide.

## How the two projects communicate

1. A gap is found in InteropHub. Add or update an entry in `aira-css-proposed-changes.md`.
2. Take that file to the `aira-web` project. Its agent implements the change, bumps the Maven version, and writes `aira-css-changes-revision-N.md` — which gets placed back in this folder as the handoff artifact.
3. `mvn install` the new version in the `aira-web` project's working tree so it lands in the local Maven repo (`~/.m2`). This step is easy to forget — if `pom.xml` here references a version that fails to resolve, this is almost always why.
4. Back in InteropHub: bump the version in `pom.xml`, mark the resolved proposal(s) `Available in this project` with a short resolution note, add a row to the revision-history table, and refresh the three mirrored files (next section).
5. If the revision notes call for markup or servlet changes, make them. A pure CSS/token fix (like both 0.1.6 changes) needs nothing beyond the version bump.

## Refreshing the mirrors — do this on every version bump

- **`reference/aira.css`**: extract `META-INF/resources/aira/css/aira.css` from the new `aira-web-theme-<version>.jar` and overwrite this file, keeping the header comment's version note current:

  ```sh
  unzip -p ~/.m2/repository/org/immregistries/aira-web-theme/<version>/aira-web-theme-<version>.jar \
    META-INF/resources/aira/css/aira.css > docs/aira-web/reference/aira.css
  ```

  Committing this file is the point — every future upstream change becomes a normal `git diff` on this one file instead of a manual jar comparison.

- **`components-guide.md` / `adoption-guide.md`**: if the `aira-web` project is checked out locally (on this machine, a sibling directory at `../aira-web` relative to InteropHub), copy `docs/components-guide.md` and `docs/adoption-guide.md` from there. If it isn't available, ask the user for current copies rather than leaving stale ones in place. A stale mirror is worse than no mirror — after 0.1.5 shipped `aira-matrix-table`, this project's copy of `components-guide.md` sat without the new "Matrix Tables and Entity Cards" section for an entire release cycle, which would have told a future session the component didn't exist.

## History

See the revision-history table at the top of `aira-css-proposed-changes.md`.
