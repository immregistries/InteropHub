# P1: Add Anchors to the Pilot (contextual-conditions)

You are working in `C:\dev\writing\contextual-conditions`.

## Read first

- `AGENTS.md` in this repository, and follow it. This repository is **public**.
- After the submodule update below: `process/guidance/Conventions.md` (Review anchors) and `process/guidance/TopicReference.md`, and the `v0.5.0` entry in `process/CHANGELOG.md`.

## Task

1. **Update the submodule.** Move `process/` to the released tag `v0.5.0` of `content-publishing-templates` (`git -C process fetch --tags` then check out `v0.5.0`). Do not use an untagged commit.
2. **Place anchors** in the Markdown files in `content/` following the Review anchors guidance:
   - `<!-- anchor: name -->` on a line of its own, between blocks.
   - **Sparingly**, at natural review points: where a distinct claim, rule, definition, or decision starts and a reviewer would want to point at it. Headings are already anchors, so add one only where a section is long enough that comments on it need a finer target. As a rough guide, expect a handful per file, none in short sections, and never one before every paragraph.
   - Short, stable, descriptive names (`omission-means-unknown`, not `para-3`). Names are unique across the whole document. Do not use `document-start` or names starting with `comment-` or `guide-`.
   - **Change no text.** The diff must show only added anchor lines. Do not reflow, reword, or fix anything you notice; list such things in your report instead.
3. **Agent instructions.** Add a short bullet list to `AGENTS.md`, in a section that fits: place anchors per `process/guidance/Conventions.md`; keep names stable when editing nearby text; when reorganizing, move an anchor with its text; when renaming or removing one, say so in the commit message, since comments attached to it will become orphaned.
4. **Build and check.** Run `validate.ps1` and `build.ps1`. Both must pass. Confirm that `CoreDocument.md` and the Word output contain no anchor text.
5. **Check in the browser.** Serve `output/guide/` from a local web server (for example `python -m http.server 8001 --directory output/guide`) and confirm:
   - `#<anchor>` links scroll to each of three anchors you added;
   - a local comment on a paragraph inside an anchor area shows its anchor in the rail, and `#comment-<id>` jumps to it;
   - after you change that paragraph's wording slightly in your working copy and rebuild, the comment stays on it (then revert the wording change).

## Out of scope

- The `comments` setting in `guide.json`, InteropHub registration, publishing (P3).
- Any content change other than anchor lines and the `AGENTS.md` bullets.

## Report back

The list of anchors added, as `file: name — what it marks`, so the product owner can review the choices; the results of steps 4 and 5; anything you noticed in the text but did not change. Do not commit or push; the product owner reviews the anchor choices first.
