# InteropHub Global Search Design

## Purpose

InteropHub includes a **Search topics or meetings** field in the header on every page. This is a global search across all Topic Spaces and content the current user is permitted to see.

The search is distinct from the topic and meeting searches available inside an individual Topic Space. Those local searches help users browse within a known space. The global search helps users find a topic or meeting without first knowing where it belongs.

The search should support two common needs:

1. **Quick navigation:** A user begins typing and immediately selects a strong matching topic or meeting.
2. **Broader discovery:** A user submits the query and reviews a complete, grouped set of results.

Although the visible result types are limited to topics and meetings, the search should also use agenda items, meeting notes, and recorded outcomes to find relevant meetings. This allows InteropHub's accumulated community knowledge to remain discoverable without introducing additional result types.

## Core Design Principles

- Topics are the durable centers of InteropHub and appear first.
- Meetings are events in the history and advancement of topics.
- Upcoming and previous meetings are presented separately because they answer different user needs.
- Search results must explain why an item matched when the connection is not evident from its title.
- Every result identifies its Topic Space.
- Search operates only over content the current user is authorized to see.
- Cancelled meetings are excluded completely.
- The interface should remain simple even though the underlying search is broad.

## Searchable Content

### Topics

Search the following topic content:

- Topic title
- Alternate names, abbreviations, and configured keywords
- Topic summary
- Topic description and other curated descriptive content

### Meetings

Search the following meeting content:

- Meeting title
- Meeting-series title
- Meeting description
- Agenda topic titles
- Agenda item descriptions
- Meeting notes
- Recorded outcomes

Notes, outcomes, and agenda items do not appear as independent result types. They make the related meeting discoverable. Selecting the result opens the existing meeting page, where the user can navigate its agenda and associated content.

### Content Excluded from the Initial Search

Do not initially search or return the following as independent content types:

- Applications
- People
- Documents and resources
- Interoperability Labs
- Cancelled meetings

The header label promises a search of topics and meetings. Additional result types can be considered later if the label and information model are intentionally expanded.

## Search-as-You-Type Experience

### General Behavior

When the user enters at least two characters, display a suggestion panel beneath the header search field. Use a short debounce before submitting the request so that the server is not queried after every individual keystroke.

The suggestion panel provides immediate navigation. Every displayed topic or meeting is selectable and opens its existing detail page.

Group suggestions in this order:

1. **Topics** — up to four results
2. **Upcoming meetings** — up to three results
3. **Previous meetings** — up to two results

Only display groups that contain results. At the bottom of the panel, display:

> View all results for “{query}”

Selecting this option, or pressing Enter when no suggestion is actively selected, opens the full results page.

### Keyboard and Pointer Behavior

- **Down Arrow / Up Arrow:** Move through selectable results and the View all option.
- **Enter:** Open the highlighted result. If no result is highlighted, open the full results page.
- **Escape:** Close the suggestion panel without clearing the query.
- **Mouse or pointer selection:** Open the selected result.
- **Click outside the search:** Close the suggestion panel.
- Use proper focus handling and ARIA combobox/listbox semantics so the interaction is accessible.

### Suggestion Presentation

A topic suggestion should show:

- Topic title
- Topic Space
- Current stage, when available and useful
- A short summary or matching excerpt when space permits

Example:

> **Certificate Management**  
> Emerging Standards · Gathering Information

An upcoming meeting suggestion should show:

- Meeting or meeting-series title
- Date and time in the user's configured time zone
- Topic Space
- Matching agenda topic or other match explanation when the meeting title does not explain the result

Example:

> **Immunization Focus Group**  
> August 14, 2026 · 9:25 AM MDT · Emerging Standards  
> Agenda match: Certificate Management

A previous meeting suggestion should show:

- Meeting or meeting-series title
- Meeting date
- Topic Space
- The strongest matching agenda item, note, or outcome

Example:

> **Immunization Focus Group**  
> July 17, 2026 · Emerging Standards  
> Notes match: Certificate renewal responsibilities

## Full Results Page

Use one results page rather than separate tabs or views. Group results into the following sections:

1. **Topics**
2. **Upcoming meetings**
3. **Previous meetings**

Do not combine topics and meetings into a single ranked list. They represent different kinds of answers and require different supporting information.

Each section initially displays up to 10 results. If additional results exist, provide an in-place expansion control such as:

- Show 14 more topics
- Show 8 more upcoming meetings
- Show 22 more previous meetings

Expanding one group should not affect the other groups or navigate away from the page. A group with no results should be omitted. If only one group has results, display that group without empty placeholders for the others.

The page heading should clearly show the submitted query, for example:

> Search results for “certificate management”

## Result Presentation

### Topic Results

Each topic result should include:

- Topic title linked to the topic page
- Topic Space
- Current stage, when available
- Short summary or relevant excerpt
- Highlighting of matched terms where it improves comprehension

### Meeting Results

Each meeting result should include:

- Meeting or meeting-series title linked to the meeting page
- Date and time for upcoming meetings
- Date for previous meetings
- Topic Space
- The strongest reason the meeting matched
- A short relevant excerpt when the match came from a description, agenda item, note, or outcome

Example:

> **Immunization Focus Group — July 17, 2026**  
> Emerging Standards  
> **Notes match:** Certificate renewal responsibilities  
> “...discussion focused on who owns renewal and how expiration should be monitored...”

A meeting must appear only once in a result group even when several agenda items, notes, or outcomes match. Show the strongest match and optionally summarize the additional matches:

> Matches 2 agenda items and 3 notes

Do not display several duplicate entries for the same meeting.

## Ranking Rules

Relevance is the primary ranking factor. Recency or meeting date should order results only when their relevance is comparable. A recent weak match must not outrank a strong title match.

### Topic Ranking

Rank topic matches in this general order:

1. Exact title match
2. Title begins with the query
3. Title contains the query
4. Alternate name, abbreviation, or keyword match
5. Topic summary match
6. Topic description or other curated-content match

When otherwise comparable, use recent meaningful activity as a secondary ranking signal. Do not allow popularity or activity to overwhelm textual relevance.

### Meeting Ranking

Rank meeting matches in this general order:

1. Exact meeting or meeting-series title match
2. Meeting title contains the query
3. Agenda topic title match
4. Agenda item description match
5. Meeting description match
6. Recorded outcome match
7. Meeting note match

Within similarly relevant results:

- Upcoming meetings are ordered from soonest to latest.
- Previous meetings are ordered from most recent to oldest.

### Match Quality

Search should be case-insensitive and should tolerate ordinary punctuation differences. It should match complete words and useful partial title terms without returning large numbers of unrelated substring matches.

Support for advanced fuzzy matching is optional for the initial implementation. Predictable title, phrase, and keyword matching is more important than approximate matching that produces surprising results.

## Topic Space Identification

Every result must display its Topic Space, including results found through notes or agenda content. This distinguishes similarly named content, gives the user context before navigation, and reinforces the organizing model of InteropHub.

Topic Space is display metadata, not a global-search filter in the initial implementation. The search already spans all spaces the user can access.

## Meeting Status Rules

- Include scheduled and completed meetings as appropriate.
- Divide meetings into upcoming and previous groups based on their scheduled date and time.
- Exclude cancelled meetings from suggestions, full results, counts, and excerpts.
- Do not provide an Include cancelled option.

Cancelled meetings remain visible in the meeting calendar, which is the appropriate place to understand schedule changes. They generally do not contain a durable participation record and would create misleading or duplicate-looking search results.

## Authorization and Visibility

The search must apply authorization before matching, ranking, counting, or generating excerpts.

- Anonymous users may search only public Topic Spaces and their public content.
- Signed-in users may additionally search private Topic Spaces they are authorized to access.
- Inaccessible content must not affect result counts or ranking.
- Do not reveal the title, Topic Space, excerpt, match count, or existence of inaccessible content.

The same authorization rules must be used by both search-as-you-type and the full results page.

## Empty, Loading, and Error States

### Before Searching

Do not display general content or popular searches when the field is empty. The field should retain its existing placeholder:

> Search topics or meetings

### Loading

Show a restrained loading state if the search response is not immediate. Do not allow results from an earlier query to replace results from a later query if responses arrive out of order.

### No Suggestions

If no suggestion matches, show:

> No matching topics or meetings

The user may still press Enter to open the full results page.

### No Full Results

If the submitted query has no results, show:

> No topics or meetings matched “{query}”. Try a different name, abbreviation, or phrase.

### Error

If search cannot be completed, show a concise error without disrupting the current page:

> Search is temporarily unavailable. Please try again.

## URL and Navigation Behavior

- The full results page should use a shareable URL containing the query, such as `/search?q=certificate+management`.
- Opening a topic result should use the existing topic route.
- Opening a meeting result should use the existing meeting route.
- Browser Back should return the user to the search results with the query and expanded groups preserved when practical.
- Search result links should behave like standard links so users can open them in a new tab.

## Performance and Implementation Guidance

- Debounce search-as-you-type requests.
- Cancel or disregard obsolete requests when the query changes.
- Apply visibility and cancelled-meeting restrictions in the query itself, not after returning records to the client.
- Return only the fields and excerpts required to render the suggestion panel.
- Deduplicate meeting results before applying display limits.
- Generate excerpts around the strongest matching text and keep them short.
- Escape all displayed database content and safely highlight matched terms without inserting untrusted HTML.
- Keep the search service shared between the suggestion endpoint and full results page so ranking and authorization remain consistent.

## Acceptance Criteria

The implementation is complete when all of the following are true:

1. The header search works from every page where it appears.
2. Typing at least two characters displays grouped, selectable suggestions.
3. A user can open a suggested topic or meeting with the mouse or keyboard.
4. Pressing Enter without selecting a suggestion opens the full results page.
5. The full results page uses one page with Topics, Upcoming meetings, and Previous meetings groups.
6. Each group can expand in place when it contains more than the initial result limit.
7. Topics can be found through their title, alternate terms, summary, and curated description.
8. Meetings can be found through their title, agenda content, description, notes, and outcomes.
9. A meeting matching several pieces of content appears only once within its group.
10. Results explain matches found through an agenda item, note, outcome, or other non-title content.
11. Every result displays its Topic Space.
12. Upcoming meetings are ordered by relevance and then ascending date.
13. Previous meetings are ordered by relevance and then descending date.
14. Cancelled meetings never appear or contribute to counts.
15. Anonymous and signed-in users receive only results they are authorized to see.
16. Inaccessible content is not revealed through titles, excerpts, counts, or timing differences that can reasonably be avoided.
17. Search-as-you-type and full-page results use consistent ranking and authorization rules.
18. Loading, empty, and error states are handled without breaking the current page.
19. The full results URL is shareable and preserves the query.
20. The interaction is usable with a keyboard and appropriate screen-reader semantics.

## Suggested Implementation Sequence

1. Create a shared search service that returns authorized, ranked, deduplicated topic and meeting matches.
2. Implement the full results page and verify ranking, authorization, excerpts, and grouping.
3. Add the compact search-as-you-type endpoint and header suggestion panel using the same search service.
4. Add keyboard navigation and accessibility semantics.
5. Test public, signed-in, private-space, cancelled-meeting, duplicate-match, empty-result, and out-of-order-request scenarios.

