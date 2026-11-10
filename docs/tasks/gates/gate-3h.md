# Gate 3H Report: Private Hosting (local part)

**Date:** 2026-11-10
**Run by:** Stream G (browser), with H5's curl checks
**Test plan:** `docs/tasks/topic-reference-private-hosting.md`, Section 6
**Build under test:** `feature/h5-private-hosting` at `6d7c459` (H5 `8b16f3b` plus H5.1), deployed to local Tomcat; dev setup from `db/scripts/topic_reference_dev_setup.sql` (topic 186 `GUIDE-PRIVATE-TEST` in a private space, document `guide-hosted-test` set to `HUB`)
**Guide under test:** templates fixture project built with templates `v0.14.0`, `"comments": {"mode": "interophub", "hub": "http://localhost:8080/hub", "document": "guide-hosted-test"}` (about 2.9 MB)
**Result:** Local part passes, except the script half of 6.1, which waits for T5's `publish_guide_to_hub.py`. The production part (with P5) follows the release.

## Summary

| # | Check | Result | Evidence |
|---|---|---|---|
| 6.1 | Upload with the script and with the form; re-upload replaces `latest`, earlier upload still listed | **Pass (form, API)**; script pending T5 | H5: `latest` 201, `main` 201, `latest` again 200, `v0.3.0` 201; list shows history. G: built guide to `main` by API (200, 2,962,805 bytes); built guide to `latest` through the upload form as the author → `?uploaded=replaced`, `latest` 2,962,819 bytes, "2 earlier uploads" listed, and the guide then served the form-uploaded copy |
| 6.2 | Signed-out browser sent to sign-in and back to the guide | **Pass** | G in Chrome: signed out, opened `…/latest/` → sign-in page; requested the reader's link, confirmed → landed on `…/guide/view/guide-hosted-test/latest/` showing the guide. H5 by curl: same on a `v0.3.0` label with a query, magic link opened in a fresh cookie jar |
| 6.3 | Outsider refused; reader reads and comments; second user sees the comment without reload | **Pass** | H5: outsider gets the not-found page, which does not name the document. G: the reader's "Sign in to comment" completed in place ("Signed in as Guide Reader"); posted comment #12; the author replied through the API with a personal token; the reply appeared on the reader's open page within about 15 seconds, with no reload (navigation type unchanged) |
| 6.4 | Reader gets `403` from the upload API and does not see the form | **Pass** | H5: reader with `topics:write` token → `403 forbidden`. G: reader opening `/es/topic-guide/186` is sent to the topic page; no file input |
| 6.5 | Headers; no `public_id` or Blob URL; general HTML upload still refused | **Pass** | Headers (H5 by curl, G from the browser): `Cache-Control: private, no-store`, `X-Robots-Tag: noindex`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`, `text/html;charset=utf-8`, no `Content-Disposition`, no CSP. Upload page HTML, versions API, and the guide's network requests contain no `blob.core` or `/files/` address. General HTML refusal: unit test `GuideHostingTest.generalUploadStillRefusesHtmlWhilePreparedStorageAcceptsIt` (`mvn test` passes) |

## Other checks

- `/guide/open?doc=guide-hosted-test&comment=12` lands on `…/latest/#comment-12` with the comment on the page (H5 task 8).
- The topic page panel links to `/guide/open?doc=guide-hosted-test`.
- Refresh fix (H3.4, templates `v0.14.0`) on a hosted guide: a reply draft on #12 kept its text and focus across the guide's background refreshes.

## Findings (none blocking)

| # | Finding | Suggested handling |
|---|---|---|
| 3H.1 | Upload times on the registration page are raw ISO UTC (`2026-11-10T10:50:27.396Z`) | Show local, readable time; small H5 follow-up or with H4 |
| 3H.2 | The sign-in page reached from a hosted guide gives no hint that the reader will return to the guide | Optional wording; not needed |
| 3H.3 | While a reply or dispose form is open on a thread, that thread does not show new replies until the form closes (by design of the H3.4 fix) | Accepted |

## Test data left in the local database

Comment #12 and its reply on `guide-hosted-test`; versions on `latest`, `main`, `v0.3.0`; a personal token for `author.guide-test@example.invalid` labelled "G Gate 3H" (30 days). All are on dev-only test users and are cleared by the next database refresh.
