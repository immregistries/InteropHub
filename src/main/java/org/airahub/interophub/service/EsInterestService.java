package org.airahub.interophub.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;
import org.airahub.interophub.dao.EsCommentDao;
import org.airahub.interophub.dao.EsInterestDao;
import org.airahub.interophub.dao.EsMeetingAttendanceDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicMeetingMemberDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsComment;
import org.airahub.interophub.model.EsInterest;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.User;

/**
 * Orchestrates ES interest, comment, and subscription operations.
 *
 * Table voting uses overwrite semantics per campaign+table+round+session and is
 * coordinated by the table vote flow.
 *
 * Subscription dedup: locate existing record by email_normalized + type (+
 * topicId for TOPIC),
 * reactivate if UNSUBSCRIBED, or create new if none found.
 */
public class EsInterestService {

    private static final Logger LOGGER = Logger.getLogger(EsInterestService.class.getName());

    private final EsInterestDao interestDao;
    private final EsCommentDao commentDao;
    private final EsSubscriptionDao subscriptionDao;
    private final EsTopicMeetingMemberDao topicMeetingMemberDao;
    private final EsMeetingAttendanceDao meetingAttendanceDao;
    private final UserDao userDao;

    public EsInterestService() {
        this.interestDao = new EsInterestDao();
        this.commentDao = new EsCommentDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.topicMeetingMemberDao = new EsTopicMeetingMemberDao();
        this.meetingAttendanceDao = new EsMeetingAttendanceDao();
        this.userDao = new UserDao();
    }

    // -------------------------------------------------------------------------
    // Interests (votes)
    // -------------------------------------------------------------------------

    /**
     * Persists a single interest vote record.
     *
     * @param interest populated EsInterest; esInterestId must be null (new record).
     * @return the persisted EsInterest with its generated ID.
     */
    public EsInterest recordInterest(EsInterest interest) {
        if (interest == null || interest.getEsCampaignId() == null || interest.getEsTopicId() == null
                || interest.getTableNo() == null || interest.getRoundNo() == null
                || interest.getTableNo() < 1 || interest.getRoundNo() < 1) {
            throw new IllegalArgumentException("Interest vote requires campaign, topic, table, and round.");
        }
        return interestDao.saveOrUpdate(interest);
    }

    // -------------------------------------------------------------------------
    // Comments
    // -------------------------------------------------------------------------

    /**
     * Records an append-only comment. Normalizes email before storing.
     * Caller is responsible for setting commentType and, for TOPIC comments,
     * for supplying a valid esTopicId.
     *
     * @param comment populated EsComment; esCommentId must be null (new record).
     * @return the persisted EsComment with its generated ID.
     */
    public EsComment recordComment(EsComment comment) {
        comment.setEmailNormalized(EsNormalizer.normalizeEmail(comment.getEmail()));
        return commentDao.save(comment);
    }

    // -------------------------------------------------------------------------
    // Subscriptions
    // -------------------------------------------------------------------------

    /**
     * Creates a new subscription or reactivates an existing one.
     * Deduplicates by email_normalized + subscriptionType + esTopicId (app-layer).
     * Always requires a non-blank email; throws IllegalArgumentException otherwise.
     * Generates a SHA-256 unsubscribe token on new subscriptions.
     *
     * Note on unsubscribe token: the raw token (needed for building email links)
     * is NOT retained in memory after this method returns. Wire the email-sending
     * code before or alongside this call to capture it, or add a dedicated
     * generateAndStoreUnsubscribeToken step when email sending is implemented.
     *
     * @param subscription populated EsSubscription; esSubscriptionId must be null.
     * @return the persisted EsSubscription.
     */
    public EsSubscription subscribeOrUpdate(EsSubscription subscription) {
        String emailNorm = EsNormalizer.normalizeEmail(subscription.getEmail());
        if (emailNorm == null) {
            throw new IllegalArgumentException("Subscription requires a valid email address.");
        }
        subscription.setEmailNormalized(emailNorm);

        Optional<EsSubscription> existing = findExistingSubscription(
                emailNorm,
                subscription.getSubscriptionType(),
                subscription.getEsTopicId());

        if (existing.isPresent()) {
            EsSubscription sub = existing.get();
            sub.setStatus(EsSubscription.SubscriptionStatus.SUBSCRIBED);
            sub.setUnsubscribedAt(null);
            if (subscription.getUserId() != null && sub.getUserId() == null) {
                sub.setUserId(subscription.getUserId());
            }
            if (sub.getUnsubscribeTokenHash() == null) {
                sub.setUnsubscribeTokenHash(generateTokenHash());
            }
            return subscriptionDao.saveOrUpdate(sub);
        }

        subscription.setUnsubscribeTokenHash(generateTokenHash());
        return subscriptionDao.saveOrUpdate(subscription);
    }

    /**
     * Marks a subscription as UNSUBSCRIBED using the SHA-256 hash of the
     * unsubscribe token (derived from the email link parameter).
     * Throws IllegalArgumentException if the token is not found.
     */
    public void unsubscribeByTokenHash(byte[] tokenHash) {
        EsSubscription sub = subscriptionDao.findByUnsubscribeTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Unsubscribe token not found."));
        sub.setStatus(EsSubscription.SubscriptionStatus.UNSUBSCRIBED);
        sub.setUnsubscribedAt(LocalDateTime.now());
        subscriptionDao.saveOrUpdate(sub);
    }

    // -------------------------------------------------------------------------
    // Email-to-account linking
    // -------------------------------------------------------------------------

    /**
     * Backfills user_id on any anonymous es_subscription and
     * es_topic_meeting_member rows whose email_normalized matches the given value
     * and whose user_id is currently NULL. Safe to call on every login or
     * welcome-page visit — the WHERE clause makes it fully idempotent.
     *
     * @param userId          the authenticated user's ID
     * @param emailNormalized the user's normalized email address
     */
    public void linkAnonymousRecordsByEmail(long userId, String emailNormalized) {
        if (emailNormalized == null || emailNormalized.isBlank()) {
            return;
        }
        subscriptionDao.updateUserIdWhereNullByEmailNormalized(emailNormalized, userId);
        topicMeetingMemberDao.updateUserIdWhereNullByEmailNormalized(emailNormalized, userId);
        commentDao.updateUserIdWhereNullByEmailNormalized(emailNormalized, userId);
        meetingAttendanceDao.updateUserIdWhereNullByEmailNormalized(emailNormalized, userId);
    }

    /**
     * Bulk backfill: iterates every non-deleted registered user and links their
     * anonymous records across all tracked tables, then removes duplicate
     * subscriptions that may have been created or exposed by the link pass.
     * Safe to run multiple times (fully idempotent).
     *
     * @return a {@link LinkResult} with the count of users processed and the
     *         count of duplicate subscription rows removed.
     */
    public LinkResult linkAllProspects() {
        List<User> users = userDao.findAllNonDeletedWithEmail();
        for (User user : users) {
            linkAnonymousRecordsByEmail(user.getUserId(), user.getEmailNormalized());
        }
        int deduped = mergeAllDuplicateSubscriptions();
        return new LinkResult(users.size(), deduped);
    }

    /**
     * Finds and removes duplicate {@code es_subscription} rows for linked users.
     * For each group of (user_id, subscription_type, es_topic_id) with more than
     * one row, the winning row is the one with the highest status
     * (CHAMPION/SUPPORT &gt; SUBSCRIBED &gt; UNSUBSCRIBED), then the one with an
     * unsubscribe token, then the oldest row. All other rows in the group are
     * deleted.
     *
     * @return the number of duplicate rows removed
     */
    public int mergeAllDuplicateSubscriptions() {
        List<Long> toDelete = subscriptionDao.findDuplicateIdsToDelete();
        if (toDelete.isEmpty()) {
            return 0;
        }
        return subscriptionDao.deleteDuplicatesByIdList(toDelete);
    }

    /**
     * Holds the outcome of a {@link #linkAllProspects()} run.
     */
    public static final class LinkResult {
        private final int usersProcessed;
        private final int duplicateSubscriptionsRemoved;

        public LinkResult(int usersProcessed, int duplicateSubscriptionsRemoved) {
            this.usersProcessed = usersProcessed;
            this.duplicateSubscriptionsRemoved = duplicateSubscriptionsRemoved;
        }

        public int getUsersProcessed() {
            return usersProcessed;
        }

        public int getDuplicateSubscriptionsRemoved() {
            return duplicateSubscriptionsRemoved;
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private Optional<EsSubscription> findExistingSubscription(
            String emailNorm, EsSubscription.SubscriptionType type, Long esTopicId) {
        if (type == EsSubscription.SubscriptionType.TOPIC && esTopicId != null) {
            return subscriptionDao.findByEmailNormalizedAndTopic(emailNorm, esTopicId);
        }
        return subscriptionDao.findGeneralByEmailNormalized(emailNorm);
    }

    /**
     * Generates a secure random 32-byte token and returns its SHA-256 hash.
     * The raw token is discarded; callers requiring it for email links must
     * extend this method or generate the token upstream before calling.
     */
    private byte[] generateTokenHash() {
        try {
            SecureRandom rng = new SecureRandom();
            byte[] raw = new byte[32];
            rng.nextBytes(raw);
            return MessageDigest.getInstance("SHA-256").digest(raw);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.log(Level.SEVERE, "SHA-256 not available", e);
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
