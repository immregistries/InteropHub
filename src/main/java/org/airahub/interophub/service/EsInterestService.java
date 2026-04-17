package org.airahub.interophub.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.dao.EsCommentDao;
import org.airahub.interophub.dao.EsInterestDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.model.EsComment;
import org.airahub.interophub.model.EsInterest;
import org.airahub.interophub.model.EsSubscription;

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

    public EsInterestService() {
        this.interestDao = new EsInterestDao();
        this.commentDao = new EsCommentDao();
        this.subscriptionDao = new EsSubscriptionDao();
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
