# Managed Topic Followers

## Purpose

Topic champions and administrators sometimes need to add a follower after that person asks through email, a meeting, or another offline channel. Today, people can only follow a topic themselves. Add a managed follower workflow so authorized topic managers can add a person, record why they were added, and invite them to confirm their registration or email address.

## Scope

Add this capability to the topic followers management page:

`/es/topic-manage/{topicId}/followers`

The workflow should support:

- adding an existing registered user as a topic follower;
- adding a follower by email address when no user account exists;
- capturing an optional name for the contact;
- capturing an optional reason/context for why the person was added;
- sending an email explaining that they were added and how to unfollow;
- inviting unregistered or unverified contacts to finish registration or verify their email;
- showing registration/verification status and recent invitation activity on the followers list.

## Permissions

The managed add/invite actions should be available to:

- app administrators;
- topic-space administrators for the topic's space;
- champion-equivalent contacts for the topic, meaning `CHAMPION` or `SUPPORT`.

This is broader than the current follower role-change servlet, which is app-admin-only. Keep role promotion/demotion rules intentional; adding followers and sending invitations can be available to champions/support, but changing someone to `CHAMPION` or `SUPPORT` may remain more restricted if that is the existing policy.

## Data Model

Use `es_subscription` for the actual topic-following relationship. If a registered user exists, link the subscription to `user_id`. If no registered user exists, store the follower through the existing anonymous subscription pattern using `email` and `email_normalized`.

Add database changes to `db/unapplied_changes.sql`.

Recommended new fields on `es_subscription`:

- `contact_first_name varchar(100) null`
- `contact_last_name varchar(100) null`
- `contact_organization varchar(200) null`
- `managed_added_by_user_id bigint null`
- `managed_added_at datetime(6) null`
- `managed_add_reason text null`

These fields allow a champion/admin to supply a display name before the person is registered and preserve why the person was manually added.

Consider adding:

- `last_follower_added_email_sent_at datetime(6) null`
- `last_registration_invite_sent_at datetime(6) null`
- `last_verification_invite_sent_at datetime(6) null`

However, preferred implementation is to derive last-sent timestamps from `email_send_log` by `recipient_email_normalized` and `email_reason`, so invitation history remains centralized.

## Add Follower Flow

On the followers page, add a compact form:

- search/select registered user, or enter an email address;
- optional first name;
- optional last name;
- optional organization;
- optional reason/context;
- checkbox or submit option to send notification immediately, default on.

When submitted:

1. Normalize the email.
2. If the email matches an active/non-deleted `auth_user`, add or reactivate the topic subscription with `user_id`.
3. If no user exists, add or reactivate an anonymous topic subscription with the email.
4. Save any manager-entered name/organization/reason metadata.
5. Send the appropriate notification email unless sending was intentionally skipped.
6. Redirect back to the followers page with a success/error message.

If a subscription already exists but is `UNSUBSCRIBED`, reactivating it is acceptable because this workflow is meant to reflect an offline request. The email must clearly explain how to unfollow.

## Followers List Display

Show name using this precedence:

1. registered user's full name;
2. subscription contact first/last name;
3. email address.

Show organization using this precedence:

1. registered user's organization;
2. subscription contact organization.

Show status next to the email:

- `Registered` when `user_id` resolves to an active/non-deleted user and email is verified;
- `Unverified` when `user_id` resolves but `auth_user.email_verified` is false;
- `Not registered` when no user account is linked.

For `Unverified` and `Not registered`, provide an action to send the appropriate email. Show the most recent send timestamp for that email type, if available, so managers can avoid sending repeated reminders too often.

## Email Behavior

Add email reasons in `EmailReason`:

- `TOPIC_FOLLOWER_ADDED`
- `TOPIC_FOLLOWER_REGISTRATION_INVITE`
- `TOPIC_FOLLOWER_VERIFY_EMAIL`

Add corresponding templates in `EmailTemplates`.

The follower-added email should include:

- topic name;
- who added them, if available;
- optional reason/context entered by the manager;
- link to the topic;
- link to manage/unfollow subscriptions;
- registration/sign-in call to action when appropriate.

The registration invite should explain that they are currently following the topic by email and should finish registering to manage their profile and participation.

The verification email can use the existing magic-link flow if consuming the magic link marks `auth_user.email_verified = true`. It should be clear that using the link verifies the email and signs them in.

All sends should use the existing `EmailService`, `EmailTemplates`, `EmailReason`, and `email_send_log` pattern.

## Unsubscribe Link

Prefer a direct manage-preferences link that does not require sign-in, consistent with the existing unsubscribe page.

If token-specific unsubscribe links are desired, add a subscription service method that creates or rotates an unsubscribe token and returns the raw token for immediate email composition. The current `EsInterestService.subscribeOrUpdate()` stores only the token hash and discards the raw token.

## Anti-Spam Guard

When resending registration or verification invitations, warn if the same email reason was sent to the same address recently, for example in the last 7 days.

The warning can be lightweight:

- show "Last sent Sep 12, 2026";
- require an explicit "Send again" action if inside the cooldown window.

Do not block app admins from resending when needed.

## Tests

Add focused tests for:

- champion/support can access add-follower action for their topic;
- topic-space admin can add followers for topics in their space;
- unrelated user cannot add followers;
- adding an existing user creates/reactivates a topic subscription linked to `user_id`;
- adding an unknown email creates/reactivates an anonymous topic subscription;
- contact name/organization/reason are saved;
- follower-added email is logged and includes the reason;
- followers list shows Registered, Unverified, and Not registered status correctly;
- last sent timestamp appears for registration/verification invitations.

## Notes

This feature should preserve the existing self-service follow/unfollow behavior. Managed additions are an administrative convenience for offline requests, not a replacement for people managing their own subscriptions.
