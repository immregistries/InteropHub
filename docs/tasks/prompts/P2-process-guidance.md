# P2: Process Guidance for Topic Reference Comments (content-publishing-process)

You are working in `C:\dev\writing\content-publishing-process`, AIRA's internal process repository. It is itself a content project: `content/` is the canonical core document, `guidance/` holds internal checklists, and `process/` is the public `content-publishing-templates` submodule at `v0.9.0`.

Work on `main` in that repository only. Do not commit, push, or tag; leave your changes uncommitted for review by G and the product owner.

## Read first

- This repository's `AGENTS.md`, `README.md`, `guidance/TopicReferenceSetup.md`, `content/03-quality-and-review.md`, `content/05-interophub.md`, and `review/README.md`.
- `C:\dev\immregistries\InteropHub\docs\topic-reference-comments-design.md`, Sections 3 (review rounds and the two records), 5.3 (anchor placement rules), 6.3 (states and dispositions), 7 (review flags), 9.3 and 9.4 (MCP tools and registration), 12 (what this repository should say), 13 (steps for a content project), and 15.2 (open questions).
- `C:\dev\writing\content-publishing-templates\guidance\TopicReference.md` (Feedback section) and `guidance\Conventions.md` (Review anchors). These are the public guidance. Point to them; do not repeat them, and do not edit them.
- `C:\dev\writing\contextual-conditions`, the pilot project: its `guide.json` shows a finished InteropHub `comments` setting.

## What is live now, and what is not

- **Live on production (2026-11-02):** readers sign in from a published guide and comment, reply, edit within 14 days, and withdraw. Authors (anyone who can edit the InteropHub topic: global admins, admins of the topic's space, and the topic's active champions and support subscribers) see controls in the guide to dispose with a change note, close, delete, re-attach orphans, and flag areas for review. The pilot `contextual-conditions` is registered and live.
- **Registration today:** through the "Topic Reference" registration page linked from the topic's manage page in InteropHub. Published URLs must start with an approved prefix (`https://immregistries.github.io/`).
- **Not built yet:** the author actions on the server side (step H3, in progress), the InteropHub topic page panel and comment management page (H3), and the MCP server (H4). Write the review workflow as it will work once H3 ships, since the guide already presents it. For the MCP server, write a short section describing how an author's agent will work through a round, using the tool names in design 9.3 and 9.4, and mark it plainly as "available once the InteropHub MCP server ships." It will be revised after H4; keep it brief and do not invent setup steps, URLs, or token details.

## Task

Write the design's Section 12 into this repository, in its own words and in its existing style:

1. **Review-round workflow** (design 3.1): publish and invite comments for about a week; participants sign in and comment; in meetings, the facilitator or an author enters comments raised there under their own name, without attributing them to the person who raised them; authors revise, dispose each comment with a change note written for the community, and reply where useful; commit with general reasons; add a change log summary; flag areas for another look; publish; close finished comments; the group reviews flagged areas. Most comments are resolved and closed between meetings.
2. **Two records of feedback** (design 3.2): InteropHub is the accountability record of every comment and its commenter. The repository carries the text, commit messages with general reasons, and a readable change log, with duplicate themes combined and no commenter names. The author owns the accuracy of the summary; disputes are settled in meetings and conversations, not by editing the source or its history.
3. **Dispositions and change notes:** the seven dispositions (provisional HL7 ballot language, design 6.3) and what a good change note looks like ("Typo fixed", "Language updated to indicate need for secure transactions"). Note that their relationship to HL7 balloting is still open (design 15.2, question 1).
4. **Anchors in practice:** where to place them, keeping names stable, and checking for orphaned comments (and re-attaching them) after reorganizing or renaming. Link to the public Conventions guidance for the syntax and naming rules rather than restating them.
5. **Agent instructions** (in `AGENTS.md` or a guidance file it points to): how an agent helps an author through a round: find each comment's place by its anchor and then its snippet, report comments it cannot place rather than guessing, draft change notes for the community and commit messages and change log entries with no commenter names, and propose review flags for the author to approve. The MCP-specific part is marked as pending (see above).
6. **Setup checklist:** update `guidance/TopicReferenceSetup.md` step 4 ("Connect it") with registering the guide on the topic's registration page in InteropHub (the `/latest/` and `/main/` URLs), adding the `comments` setting to `guide.json` (mode `interophub`, hub `https://informatics.immregistries.org/hub`, and the document key), rebuilding, publishing, and checking sign-in and a test comment on the published page. Mention the optional import of comments previously downloaded in local mode.
7. **Core document:** update `content/05-interophub.md` (and `03-quality-and-review.md` only where it now contradicts the live workflow) so the core describes review through signed-in guide comments. Keep it at the level of the rest of the core; the operating detail belongs in `guidance/`. Rebuild per `AGENTS.md`, and run `process/tools/check_products.py` to list any derived products (decks) that now need review. List them; do not regenerate them.

## Rules

- Internal material stays in this repository. Do not copy any of it to `content-publishing-templates`, and do not edit `process/`.
- Write plainly and briefly, in the existing voice. Prefer linking to the public guidance over duplicating it.
- Do not decide the open questions in design 15.2 (balloting, the review lifecycle beyond manual closing, how revision history appears in the published guide). Mention them where a reader would otherwise wonder.
- Run `build.ps1` and `validate.ps1`; both must pass.

## Out of scope

- Changes to `content-publishing-templates` (T4 covers its setup tooling and template `AGENTS.md`).
- Moving other projects to InteropHub mode (P4).
- InteropHub code.

## Report back

Files changed with a one-line summary of each; where each item of design Section 12 now lives; the products `check_products.py` lists; anything in the design you found unclear or had to interpret. Leave the changes uncommitted.
