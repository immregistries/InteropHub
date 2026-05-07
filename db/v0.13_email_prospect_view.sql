-- v0.13: Add v_email_prospect view for admin funnel reporting.
-- Surfaces every unique email_normalized that has interacted with the system
-- (campaign registration, comment, subscription, or meeting membership) but has
-- not yet completed full registration in auth_user.
-- Used by the admin "Registered Users" dashboard to show conversion funnel counts.

CREATE OR REPLACE VIEW v_email_prospect AS
SELECT
    t.email_normalized,
    MIN(t.created_at)                           AS first_contact_at,
    MAX(t.created_at)                           AS last_contact_at,
    SUM(t.src_campaign_reg)                     AS campaign_registration_count,
    SUM(t.src_comment)                          AS comment_count,
    SUM(t.src_subscription)                     AS subscription_count,
    SUM(t.src_meeting_member)                   AS meeting_member_count
FROM (
    SELECT email_normalized, created_at, 1, 0, 0, 0
      FROM es_campaign_registration
     WHERE email_normalized IS NOT NULL

    UNION ALL

    SELECT email_normalized, created_at, 0, 1, 0, 0
      FROM es_comment
     WHERE email_normalized IS NOT NULL
       AND user_id IS NULL

    UNION ALL

    SELECT email_normalized, created_at, 0, 0, 1, 0
      FROM es_subscription
     WHERE email_normalized IS NOT NULL
       AND user_id IS NULL

    UNION ALL

    SELECT email_normalized, created_at, 0, 0, 0, 1
      FROM es_topic_meeting_member
     WHERE email_normalized IS NOT NULL
       AND user_id IS NULL
) AS t (email_normalized, created_at, src_campaign_reg, src_comment, src_subscription, src_meeting_member)
WHERE t.email_normalized NOT IN (
    SELECT email_normalized
      FROM auth_user
     WHERE status <> 'DELETED'
)
GROUP BY t.email_normalized;
