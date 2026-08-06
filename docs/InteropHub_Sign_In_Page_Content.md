# InteropHub Sign-In Page Content

## Purpose of the page

The sign-in page should answer three questions before a visitor enters an email address:

1. What is InteropHub?
2. Why would I sign in?
3. What will happen after I submit my email address?

The page should make sign-in feel useful and predictable, not mandatory or mysterious. It should also preserve an important distinction: anyone may explore public InteropHub content without an account, while signing in supports continuing participation and access to interactive or testing capabilities.

This page should not reproduce the full application overview or the legal terms. It should provide a clear summary, disclose the major account-enabled functions, and link to fuller information where appropriate.

## Recommended page structure

Use progressive disclosure in three levels:

1. **Immediate orientation:** a short explanation beside the email field.
2. **What signing in enables:** a concise list of the main user-facing functions.
3. **Additional details:** expandable sections about how sign-in works, information visibility, and testing-system use.

The email form should remain visible near the top of the page. The supporting explanation should not push the primary action below a long introduction.

---

## Proposed page language

### Page title and introduction

# Immunization InteropHub

**Discover and participate in immunization interoperability work.**

InteropHub connects topics, meetings, people, resources, and testing systems used by the immunization interoperability community. Public topics and materials are available without signing in. Sign in when you want to follow work, participate in meetings, connect with other participants, or use demonstration and testing systems.

### Sign-in panel

## Sign in with your email

Enter your email address and we will send you a sign-in link. No password is required.

**Email address**  
`you@example.org`

**Button:** Send sign-in link

### What happens next

If you already have an account, we will email you a link that signs you in. If you are new to InteropHub, you will first provide a few account details and accept the applicable terms; we will then email your sign-in link.

After you sign in, InteropHub will keep you signed in for up to 30 days. Continued activity renews that period, so people who use InteropHub regularly should rarely need to sign in again. You may still be asked to sign in again for security or account-related reasons.

> **You can browse without an account.** Public Topic Spaces, topics, meeting information, notes, presentations, and other public resources remain available without signing in.

---

## What signing in lets you do

### Follow the work that matters to you

Follow individual topics or recurring meeting series and receive relevant communications when that work is discussed or advanced.

### Participate in meetings and community work

Indicate that you plan to attend, record attendance, contribute where participation is enabled, and remain connected to the topics discussed in a meeting.

### Find and connect with participants

See relevant participants in the same community activities and allow others to recognize your involvement. This helps people find collaborators without depending entirely on informal networks.

### Use demonstration and testing systems

Access AIRA demonstration applications and interoperability testing resources. Some systems may require additional authorization, terms, or setup.

### Access testing credentials and technical collaboration

Where available, obtain credentials or API access for non-production testing and participate in organized interoperability activities, including future Interoperability Labs.

---

## Recommended expandable details

These sections should appear beneath the main explanation as accordions or other expandable elements. Their headings should remain visible so visitors can quickly identify the available information.

### How passwordless sign-in works

InteropHub uses email links instead of passwords. Enter your email address, complete registration if this is your first visit, and use the link we send to sign in. Once signed in, your session can remain active for 30 days and is renewed as you continue to use InteropHub.

The email message should identify InteropHub and AIRA clearly, state that the link was requested for sign-in, and explain what to do if the recipient did not request it.

### What information other participants may see

Your name, organization, role, and participation in relevant community activities may be visible to other signed-in participants. Depending on the activity, this may include following a topic, attending a meeting, or participating in an interoperability collaboration. This visibility helps the community identify who is interested and involved.

Do not state that a user's information is shared only with people in the same “connectathon workspace.” InteropHub supports several types of participation, and the applicable audience depends on the topic, meeting, application, or collaboration involved.

### Testing use and activity information

InteropHub and connected demonstration systems are for interoperability development, experimentation, and testing—not production use. Users must not submit protected health information, production data, real credentials, confidential information, or other sensitive or regulated data.

AIRA records account, participation, and system-usage information needed to operate the service, assist users, improve systems, analyze activity, troubleshoot problems, prevent misuse, communicate with participants, and report aggregate community value. The registration page should continue to present the formal acknowledgments and links to the Terms of Use and Privacy Policy.

---

## Shorter implementation option

If the page needs to remain very compact, use the title, introduction, sign-in panel, “What happens next,” and the following four bullets. Put the remaining material behind a single **Learn more about InteropHub and your account** disclosure.

By signing in, you can:

- follow topics and receive relevant meeting communications;
- participate in meetings and connect with other community members;
- access demonstration applications, testing systems, and credentials; and
- return without repeatedly entering a password.

Public topics and materials remain available without an account.

---

## Changes from the current page

The current page should be revised in the following ways:

- Replace **Connect with other developers working on immunization interoperability** with language that includes standards participants, meeting attendees, topic followers, and implementers—not only developers.
- Replace **Enter your email to continue** with **Sign in with your email**. “Continue” does not explain the action or the passwordless flow.
- Replace **Send Email Link** with **Send sign-in link**.
- Explain the new-user branch before submission: new users complete registration, while existing users proceed directly to the emailed link.
- Explain the 30-day renewable sign-in period as a benefit of the account.
- State explicitly that public discovery does not require an account.
- Remove the statement that information is shared only with people in the same connectathon workspace. It is too narrow and may be inaccurate for topics, meetings, applications, and future Interoperability Labs.
- Replace **About this project** with **What signing in lets you do**. InteropHub is an operating environment for community work, not merely a single project.
- Replace **Participate in connectathon workspaces** with broader language about organized interoperability activities and Interoperability Labs.
- Include a restrained explanation of participant visibility and activity logging before registration. The formal consent and detailed restrictions should remain on the registration page.

## Relationship to the registration page

The sign-in page should establish expectations; the registration page should collect details and obtain formal agreement.

The registration page should retain:

- the confirmed email address;
- first and last name;
- organization and role;
- acceptance of AIRA's Terms of Use and Privacy Policy;
- acknowledgment that connected systems are for testing only and prohibit production or sensitive data; and
- acknowledgment of system limitations and activity logging.

Its opening language could be simplified to:

> **Create your InteropHub account**  
> InteropHub is free to use. Tell us who you are, then review and accept the terms for participating in community activities and using demonstration and testing systems. After registration, we will email you a sign-in link.

This makes the relationship between registration and sign-in explicit and avoids describing all InteropHub capabilities as “testing resources.”

## Design guidance

- Keep the email field and primary button above the fold on typical desktop and mobile screens.
- Put the short orientation text immediately above or beside the form, not in a separate promotional panel that may be skipped.
- Make **Browse public topics without signing in** a visible secondary action if the application has an appropriate public landing route.
- Use expandable sections for details, but do not hide the new-user flow, passwordless email step, or availability of public browsing.
- Avoid “register” as the primary action on the first page. The user is entering a single sign-in flow; InteropHub can determine whether registration is needed.
- Avoid claims such as “secure,” “private,” or “only shared with” unless they are supported precisely by the implemented controls and the Privacy Policy. “Secure sign-in link” is appropriate only if the emailed link is implemented as a time-limited, single-use authentication token.

## Final content principle

The page should communicate one central idea: **an InteropHub account does not unlock public information; it removes friction when a person chooses to participate.** The account connects the person's interests, meeting participation, community relationships, and testing-system access so that returning involvement becomes easy and continuous.
