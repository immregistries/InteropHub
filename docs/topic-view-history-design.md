# Topic View History and Recently Viewed Topics

**Status:** Proposed design  
**Scope:** InteropHub topic pages  
**Primary purpose:** Support a persistent **Recently Viewed** experience for authenticated users and provide a low-volume heat map of topic interest.

## 1. Decision Summary

Topic viewing will be tracked separately from topic subscriptions.

For authenticated users, the application will maintain one durable record for each user-topic pair. The record will store:

- When the user first viewed the topic
- When the user most recently viewed the topic
- The number of distinct visits, using a 30-minute deduplication window
- When the visit count was most recently incremented

For anonymous users:

- No viewing data will be persisted.
- No anonymous analytics will be collected.
- Recently viewed topics may be maintained only in the current HTTP session.
- The anonymous recently viewed list may disappear when the session expires.

The resulting data is intended as an **interest signal**, not as detailed web analytics or click tracking.

## 2. Goals

This design supports two related capabilities:

### 2.1 Recently Viewed

Authenticated users can return to topics they recently opened, even after signing out, starting a new session, or using another device.

Anonymous users can receive a limited recently viewed experience during the current session without being assigned a durable identity.

### 2.2 Topic Interest Heat Map

Administrators or other authorized users can identify:

- Topics viewed by the most registered users
- Topics receiving repeated authenticated-user interest
- Topics viewed recently
- Topics that have received little or no authenticated-user attention

The data should be interpreted as directional evidence of interest. The number of regular users is relatively small, so raw counts should generally be visible alongside any color scale or ranking.

## 3. Non-Goals

This feature is not intended to provide:

- Raw click tracking
- A record of every page request
- Anonymous-user tracking
- Bot or crawler analytics
- Full website traffic analytics
- A chronological event log of every view
- Precise session replay or navigation-path analysis
- A replacement for topic subscriptions or following

A subscription represents an intentional relationship with a topic. A view only indicates that a user opened the topic.

## 4. Existing Schema Assessment

The current schema does not contain a suitable topic-view history table.

Related structures should not be reused:

- `es_subscription` represents explicit topic subscriptions, champions, and support relationships.
- `auth_session` represents authenticated login sessions.
- `auth_user.last_seen_at` represents general user activity.
- Campaign `session_key` fields are specific to campaign registration and participation.
- `usage_daily_agg` contains API-token usage metrics rather than user-interface activity.

Topic viewing should therefore be represented by a separate user-topic relationship.

## 5. Proposed Data Model

Add one row for each authenticated user and topic that the user has viewed.

### 5.1 Proposed Table

```sql
CREATE TABLE `es_topic_user_view` (
  `es_topic_user_view_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `es_topic_id` bigint NOT NULL,
  `first_viewed_at` datetime(6) NOT NULL,
  `last_viewed_at` datetime(6) NOT NULL,
  `visit_count` bigint unsigned NOT NULL DEFAULT 1,
  `last_counted_at` datetime(6) NOT NULL,

  PRIMARY KEY (`es_topic_user_view_id`),

  UNIQUE KEY `uq_es_topic_user_view_user_topic`
    (`user_id`, `es_topic_id`),

  KEY `ix_es_topic_user_view_user_recent`
    (`user_id`, `last_viewed_at`),

  KEY `ix_es_topic_user_view_topic_recent`
    (`es_topic_id`, `last_viewed_at`),

  CONSTRAINT `fk_es_topic_user_view_user`
    FOREIGN KEY (`user_id`)
    REFERENCES `auth_user` (`user_id`),

  CONSTRAINT `fk_es_topic_user_view_topic`
    FOREIGN KEY (`es_topic_id`)
    REFERENCES `es_topic` (`es_topic_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
```

### 5.2 Column Semantics

| Column | Meaning |
|---|---|
| `es_topic_user_view_id` | Surrogate primary key consistent with the existing schema style |
| `user_id` | Authenticated user who viewed the topic |
| `es_topic_id` | Topic that was viewed |
| `first_viewed_at` | First recorded view by this user |
| `last_viewed_at` | Most recent successful display of the topic |
| `visit_count` | Number of distinct visits after applying the 30-minute rule |
| `last_counted_at` | Time at which `visit_count` was most recently incremented |

The unique user-topic constraint ensures that the table remains a compact summary rather than becoming an event log.

## 6. Definition of a View

A view should be recorded only when all of the following are true:

1. The request is for the topic detail page.
2. The topic exists.
3. The authenticated user is authorized to view the topic and its topic space.
4. The application is successfully rendering the topic.

Do not record:

- Authorization failures
- Missing topics
- Redirects that do not display the topic
- Background API requests
- Browser prefetches, when they can be distinguished
- Topic cards merely appearing in a list
- Clicks that do not result in the topic being displayed

`last_viewed_at` represents the most recent successful display. It may be updated on repeated page loads even when `visit_count` is not incremented.

## 7. Thirty-Minute Visit Rule

The application will use a rolling 30-minute deduplication window.

### 7.1 New User-Topic Pair

When no row exists:

- Insert the row.
- Set `first_viewed_at`, `last_viewed_at`, and `last_counted_at` to the current time.
- Set `visit_count` to `1`.

### 7.2 Existing User-Topic Pair Within 30 Minutes

When the current time is less than 30 minutes after `last_counted_at`:

- Update `last_viewed_at`.
- Do not increment `visit_count`.
- Do not change `last_counted_at`.

### 7.3 Existing User-Topic Pair After 30 Minutes

When the current time is at least 30 minutes after `last_counted_at`:

- Update `last_viewed_at`.
- Increment `visit_count`.
- Set `last_counted_at` to the current time.

This prevents refreshes, back-button navigation, and repeated requests during the same reading period from being treated as separate interest events.

The operation should be implemented atomically, using either a database upsert or a transaction that is protected by the unique user-topic constraint.

## 8. Authenticated Recently Viewed Experience

The Recently Viewed section should retrieve the most recently viewed topics for the current authenticated user.

Recommended initial behavior:

- Display up to 10 topics.
- Order by `last_viewed_at` descending.
- Do not show duplicate topics.
- Show the topic name and, where useful, its topic space and last-viewed time.
- Link directly to the topic detail page.

Conceptual query:

```sql
SELECT
  t.es_topic_id,
  t.topic_code,
  t.topic_name,
  t.es_topic_space_id,
  v.last_viewed_at
FROM es_topic_user_view v
JOIN es_topic t
  ON t.es_topic_id = v.es_topic_id
WHERE v.user_id = ?
  AND t.status = 'ACTIVE'
ORDER BY v.last_viewed_at DESC
LIMIT 10;
```

The application must also apply the current topic-space access rules. A stored view record does not grant continuing access.

If a user previously viewed a private topic but later loses access, retain the database record for administrative interest data but omit the topic from that user's Recently Viewed section.

Archived and retired topics should normally be omitted from the user-facing list unless the application deliberately supports viewing them.

## 9. Anonymous Recently Viewed Experience

Anonymous users will not be written to `es_topic_user_view` or any other persistent view-tracking table.

The application may maintain a session attribute containing an ordered list of topic IDs.

Recommended behavior:

1. When an anonymous user successfully views a topic, remove that topic ID from its existing position in the session list.
2. Insert the topic ID at the beginning.
3. Truncate the list to 10 topics.
4. When displaying the list, recheck topic status and public accessibility.

Example session value:

```text
[104, 18, 72, 31]
```

This list is intentionally temporary. It may be lost when:

- The HTTP session expires
- The browser no longer sends the session cookie
- The application restarts without persistent session storage
- The user changes browsers or devices

Anonymous session data must not be incorporated into the administrative heat map.

## 10. Topic Interest Heat Map

The initial heat map should use authenticated-user data only.

Useful measures include:

- **Unique viewers:** Number of registered users with a row for the topic
- **Lifetime visits:** Sum of `visit_count`
- **Recent viewers:** Number of users whose `last_viewed_at` falls within a selected recent period
- **Most recent view:** Maximum `last_viewed_at`
- **Return interest:** Difference between lifetime visits and unique viewers, or average visits per viewer

Example aggregate query:

```sql
SELECT
  t.es_topic_id,
  t.topic_code,
  t.topic_name,
  COUNT(v.user_id) AS unique_viewers,
  COALESCE(SUM(v.visit_count), 0) AS lifetime_visits,
  SUM(
    CASE
      WHEN v.last_viewed_at >= CURRENT_TIMESTAMP(6) - INTERVAL 30 DAY
      THEN 1
      ELSE 0
    END
  ) AS viewers_in_last_30_days,
  MAX(v.last_viewed_at) AS most_recent_view
FROM es_topic t
LEFT JOIN es_topic_user_view v
  ON v.es_topic_id = t.es_topic_id
WHERE t.status = 'ACTIVE'
GROUP BY
  t.es_topic_id,
  t.topic_code,
  t.topic_name;
```

The heat map should not rely solely on `lifetime_visits`. A small number of repeat users could otherwise appear equivalent to broad community interest.

A reasonable default presentation is:

- Primary signal: unique authenticated viewers
- Recency signal: viewers whose last view occurred in the last 30 or 90 days
- Secondary signal: lifetime visits or average visits per viewer
- Always show the underlying counts

## 11. Important Analytical Limitation

This table stores a current summary for each user-topic pair. It does not store each distinct visit as a separate event.

It can answer questions such as:

- How many registered users have ever viewed this topic?
- Which topics were viewed by a user most recently?
- Which users have returned to a topic multiple times?
- Which topics have users viewed recently?
- Which topics have never been viewed by a registered user?

It cannot accurately reconstruct questions such as:

- How many total visits occurred in each previous month?
- On which dates did a user make each visit?
- Was a lifetime visit count concentrated last week or spread across several years?
- What navigation path led a user to the topic?

If historical trend reporting becomes necessary, add a separate authenticated-only daily aggregate or event structure at that time. Do not add it preemptively for the current feature.

## 12. Privacy and Access Boundaries

This design deliberately minimizes retained data:

- Only authenticated users are persistently represented.
- No IP address, user agent, session identifier, or referral source is stored with a topic view.
- No anonymous identity is created.
- No raw event stream is retained.
- One summary row is stored per authenticated user-topic pair.

Access to user-level viewing information should be restricted. Most heat-map interfaces should use aggregate counts rather than expose lists of individual viewers.

A user-level view may be appropriate for specifically authorized administrative purposes, but the application should not casually present named-user browsing history.

## 13. User and Topic Lifecycle

### 13.1 Disabled or Deleted Users

Rows may remain associated with users whose `auth_user.status` is no longer `ACTIVE`, unless a broader data-deletion process requires removal.

Heat-map queries should decide explicitly whether to include disabled or deleted accounts. The recommended default is:

- Include active users
- Exclude accounts marked `DELETED`
- Consider whether disabled accounts should remain in historical totals

### 13.2 Archived or Retired Topics

View records may remain after a topic becomes archived or retired.

- Omit inaccessible or inactive topics from Recently Viewed.
- Administrative reporting may retain their historical interest data.
- Physical topic deletion must account for the foreign-key relationship.

## 14. Implementation Placement

View recording should be called from the common code path that successfully renders a topic detail page.

Avoid placing the logic only in a navigation-link handler because topics may be reached through:

- Search results
- Boards
- Meeting pages
- Recently Viewed itself
- Direct URLs
- Other future navigation paths

A dedicated service method is recommended, for example:

```text
TopicViewService.recordAuthenticatedView(userId, topicId, viewedAt)
```

The service should own:

- Insert-versus-update behavior
- The 30-minute rule
- Concurrency handling
- Timestamp consistency

The servlet or controller should remain responsible for confirming that the topic was successfully authorized and loaded before calling the service.

## 15. Suggested Implementation Sequence

1. Add the `es_topic_user_view` migration.
2. Add the corresponding persistence entity and repository/DAO.
3. Add the topic-view service with the 30-minute rule.
4. Call the service from successful authenticated topic-page rendering.
5. Add anonymous session-only recent-topic maintenance.
6. Add the Recently Viewed user-interface section.
7. Add aggregate administrative queries for the topic interest heat map.
8. Add tests for deduplication, authorization, and inaccessible topics.

## 16. Acceptance Criteria

### Authenticated Users

- Opening a topic for the first time creates one row with `visit_count = 1`.
- Refreshing or reopening the topic within 30 minutes updates `last_viewed_at` but does not increment `visit_count`.
- Reopening the topic after 30 minutes increments `visit_count`.
- A user-topic pair never creates more than one row.
- Recently Viewed persists across authenticated sessions.
- Recently Viewed is ordered by the most recent successful display.
- Topics the user can no longer access are not displayed.

### Anonymous Users

- No persistent topic-view row is created.
- No anonymous viewing data appears in the heat map.
- Recently Viewed works during the active HTTP session.
- Anonymous recent-topic entries are deduplicated and limited to 10.
- Private or inactive topics are not shown.

### Administrative Interest Data

- Unique authenticated viewers can be calculated per topic.
- Lifetime distinct visits can be calculated per topic.
- Recently interested users can be approximated using `last_viewed_at`.
- Topics with no authenticated views remain visible with zero counts.
- Raw counts are available with any heat-map visualization.

## 17. Future Extensions

Potential future additions, only when justified, include:

- User control to clear their Recently Viewed list
- A configurable number of recently viewed topics
- Filters by topic space
- Authenticated-only daily aggregates for historical trend reporting
- A minimum-count privacy threshold before displaying heat-map values
- Exclusion of designated test or administrative accounts from analytics

These extensions should preserve the current boundary: no persistent anonymous tracking and no raw clickstream unless a separate, explicit decision is made.
