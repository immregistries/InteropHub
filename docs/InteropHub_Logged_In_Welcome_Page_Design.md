# InteropHub Logged-In Welcome Page Design

## Purpose

The logged-in welcome page should orient participants and give them clear starting points. It should not function primarily as a directory of links or as an authenticated copy of the sign-in page.

The page should communicate that:

- Topic Spaces are the primary way to discover and participate in InteropHub work;
- applications are supporting resources for demonstration and testing;
- InteropHub connects topics, meetings, people, resources, testing systems, and accumulated outcomes; and
- signing in provides a more continuous and personalized participation experience.

The page should use the open, explanatory style of the public sign-in page while recognizing that the person has already signed in.

## Information hierarchy

Use the following order:

1. Welcome and orientation
2. Topic Spaces
3. Applications
4. How InteropHub supports the work
5. Administration, when applicable

Topic Spaces and Applications should not appear as equivalent inventories. Topic Spaces are the primary organizing model for InteropHub. Applications are resources used in support of interoperability work.

---

## Proposed page content

### 1. Welcome and orientation

# Welcome, Nathan

**Discover, participate in, and advance immunization interoperability work.**

InteropHub connects topics, meetings, people, resources, and testing systems used by the immunization interoperability community. Start with a Topic Space to explore a related body of work, or open an application when you need a demonstration or testing system.

#### Implementation notes

- Substitute the signed-in user's preferred or first name for `Nathan`.
- Do not make the user's email address visually prominent. It may appear as a small secondary line beneath the welcome message, or account information may be left under **Account** in the header.
- Keep **Logout** under **Account** or present it as a visually subordinate action.
- Remove the large **Emerging Standard Topics** button. Topic Space destinations are already available in the main navigation.

---

### 2. Topic Spaces

## Explore work through Topic Spaces

Topic Spaces organize related topics, meetings, resources, participants, and outcomes. Select a Topic Space from the navigation above to explore the work available to you.

**Emerging Standards**  
Explore developing standards and interoperability topics.

**Building Bridges**  
Explore country-specific and international interoperability work.

**AIRA Opportunity Nursery**  
Develop and review internal interoperability opportunities. *Private—visible because you have access.*

#### Implementation notes

- Show only Topic Spaces the current user is authorized to access.
- Topic Space names may be links even though they also appear in the main navigation. The purpose of this section is orientation, not merely navigation.
- Present the spaces as short named descriptions rather than boxed cards.
- Distinguish private Topic Spaces with a restrained label rather than placing them in a large separate panel.
- The page should support additional Topic Spaces without becoming a grid of navigation boxes.

---

### 3. Applications

## Use demonstration and testing applications

InteropHub provides access to applications that support standards development, demonstration, and interoperability testing. Applications may relate to work found in one or more Topic Spaces.

### StepIntoCDSI

Explore and test clinical decision support for immunization services.

**Action:** Open application

#### Implementation notes

- Applications must appear after Topic Spaces.
- Each application should include its name, a one-sentence explanation, and a clear **Open application** link or button.
- Avoid placing an application link inside multiple nested panels.
- When more applications are added, use a simple vertical list or a lightly divided two-column layout rather than a dense grid of boxes.
- If an application has special authorization or setup requirements, state that near its launch action.

---

### 4. How InteropHub supports the work

## One place to follow interoperability work

InteropHub connects the activities that move interoperability work from initial discovery through participation, testing, and durable results.

### Discover topics

Find emerging issues, understand why they matter, and see how they relate to other work.

### Follow work

Stay connected to the topics and recurring meeting series that matter to you.

### Join meetings

Find upcoming discussions, review agendas, and remain connected to the topics discussed.

### Use testing systems

Access demonstration applications, technical resources, and organized interoperability activities.

### Preserve outcomes

Return to meeting notes, decisions, presentations, resources, and prior work without reconstructing the history from separate systems.

#### Implementation notes

- Present these five functions in the same clear visual language as the explanatory content on the public sign-in page.
- On a wide screen, the functions may appear in five short columns. They should stack cleanly on smaller screens.
- Use short headings and concise descriptions. Small, restrained icons are optional.
- Avoid heavy individual borders. Spacing, subtle dividers, or a light background band should provide sufficient structure.
- This section explains the complete InteropHub experience; it should not look like five additional navigation buttons.

---

### 5. Administration

Administrative tools are not part of the primary welcome-page experience. If they must remain on this page temporarily, place them after substantial visual separation beneath the heading:

## Administration

The section should be visible only to authorized users. It may retain a compact link-based layout because it serves as a utility directory rather than general orientation.

Longer term, administrative functions should move to a dedicated administration page linked from the account or administrative navigation.

---

## Layout and visual guidance

- Preserve the current header and Topic Space navigation.
- Use a main content width similar to the public sign-in page.
- Prefer open sections, whitespace, restrained background bands, and thin dividers over nested bordered panels.
- The welcome text should be visually prominent but should not consume most of the first screen.
- Topic Spaces should receive the strongest functional emphasis.
- Applications should be clearly secondary to Topic Spaces while remaining easy to find.
- Do not repeat navigation merely to fill the page. When a destination appears again, provide explanatory context that the header navigation cannot provide.
- Avoid dashboard language and metrics unless the page later gains genuinely useful personalized information.
- Maintain accessible heading order, visible keyboard focus, adequate color contrast, and responsive stacking.

## Suggested page flow

1. **Welcome, Nathan** — short orientation to InteropHub.
2. **Explore work through Topic Spaces** — descriptions of the spaces available to the user.
3. **Use demonstration and testing applications** — application descriptions and launch actions.
4. **One place to follow interoperability work** — Discover, Follow, Join, Use, and Preserve.
5. **Administration** — separated from the participant experience and shown only when applicable.

## Future personalization

The initial redesign does not require personalized activity features. If they become reliable and useful later, the page could add a compact **Continue your work** section containing items such as:

- followed topics with recent activity;
- upcoming meetings related to the user's interests;
- recently viewed topics; or
- applications recently accessed by the user.

Do not add empty or weakly populated dashboard widgets merely to make the page appear personalized. The proposed explanatory page is preferable until InteropHub has enough meaningful user-specific information to support those features.

## Central design principle

The logged-in welcome page should make the InteropHub model immediately understandable: **begin with a Topic Space, follow the work that matters, participate in meetings and testing, and return to the outcomes that the community has preserved.**
