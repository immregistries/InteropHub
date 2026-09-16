package org.airahub.interophub.service;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.dao.EmailSendLogDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EmailSendLog;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.User;

/**
 * Orchestrates the managed-follower workflow (docs/topic-managed-followers.md):
 * a champion/support/space-admin/app-admin adding a follower on someone's
 * behalf, and the follow-up registration/verification invitations for
 * contacts who aren't fully registered/verified yet.
 */
public class TopicFollowerManagementService {

    private static final Logger LOGGER = Logger.getLogger(TopicFollowerManagementService.class.getName());

    /** Resend cooldown window for registration/verification invitations. */
    public static final int RESEND_COOLDOWN_DAYS = 7;

    public enum FollowerStatus {
        REGISTERED,
        UNVERIFIED,
        NOT_REGISTERED
    }

    private final EsSubscriptionDao subscriptionDao;
    private final UserDao userDao;
    private final EsTopicDao topicDao;
    private final EmailService emailService;
    private final EmailSendLogDao emailSendLogDao;
    private final HubLinkService hubLinkService;
    private final AuthFlowService authFlowService;
    private final TopicSpaceAccessService topicSpaceAccessService;

    public TopicFollowerManagementService() {
        this.subscriptionDao = new EsSubscriptionDao();
        this.userDao = new UserDao();
        this.topicDao = new EsTopicDao();
        this.emailService = new EmailService();
        this.emailSendLogDao = new EmailSendLogDao();
        this.hubLinkService = new HubLinkService();
        this.authFlowService = new AuthFlowService();
        this.topicSpaceAccessService = new TopicSpaceAccessService();
    }

    // -------------------------------------------------------------------------
    // Pure / static helpers (unit tested directly — see
    // TopicFollowerManagementServiceTest)
    // -------------------------------------------------------------------------

    /**
     * Managed add/invite actions are available to app admins, Topic-Space
     * admins for the topic's space, and champion/support contacts for the
     * topic itself.
     */
    public static boolean canManageFollowers(boolean isAdmin, boolean isSpaceAdmin,
            boolean isChampionOrSupportForTopic) {
        return isAdmin || isSpaceAdmin || isChampionOrSupportForTopic;
    }

    /**
     * Name display precedence: registered user's full name, then the
     * manager-entered contact name, then the raw email.
     */
    public static String resolveDisplayName(User registeredUser, EsSubscription subscription) {
        if (registeredUser != null) {
            String fullName = registeredUser.getFullName();
            if (fullName != null && !fullName.isBlank()) {
                return fullName;
            }
        }
        if (subscription != null) {
            String first = trimToNull(subscription.getContactFirstName());
            String last = trimToNull(subscription.getContactLastName());
            if (first != null || last != null) {
                return ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
            }
        }
        return subscription != null ? orEmpty(subscription.getEmail()) : "";
    }

    /**
     * True when there is a real name to show (registered user's full name, or
     * a manager-entered contact first/last name) — as opposed to falling back
     * to the email address. Drives whether the Followers list shows a name or
     * an inline "add name" affordance.
     */
    public static boolean hasDisplayName(User registeredUser, EsSubscription subscription) {
        if (registeredUser != null) {
            String fullName = registeredUser.getFullName();
            if (fullName != null && !fullName.isBlank()) {
                return true;
            }
        }
        if (subscription != null) {
            return trimToNull(subscription.getContactFirstName()) != null
                    || trimToNull(subscription.getContactLastName()) != null;
        }
        return false;
    }

    /**
     * Organization display precedence: registered user's organization, then
     * the manager-entered contact organization.
     */
    public static String resolveDisplayOrganization(User registeredUser, EsSubscription subscription) {
        if (registeredUser != null) {
            String org = trimToNull(registeredUser.getOrganization());
            if (org != null) {
                return org;
            }
        }
        return subscription != null ? orEmpty(subscription.getContactOrganization()) : "";
    }

    /**
     * Registered when an active, non-deleted, verified user is linked;
     * unverified when the linked user hasn't verified their email;
     * not registered when no user account is linked.
     */
    public static FollowerStatus resolveFollowerStatus(User registeredUser) {
        if (registeredUser == null || registeredUser.getStatus() == User.UserStatus.DELETED) {
            return FollowerStatus.NOT_REGISTERED;
        }
        return Boolean.TRUE.equals(registeredUser.getEmailVerified())
                ? FollowerStatus.REGISTERED
                : FollowerStatus.UNVERIFIED;
    }

    /** True when {@code lastSentAt} falls within {@code cooldownDays} of {@code now}. */
    public static boolean isWithinCooldown(LocalDateTime lastSentAt, LocalDateTime now, int cooldownDays) {
        if (lastSentAt == null || now == null) {
            return false;
        }
        return lastSentAt.isAfter(now.minusDays(cooldownDays));
    }

    /**
     * True when the viewer holds an active CHAMPION or SUPPORT subscription
     * (matched by user id or email) among the given topic subscriptions.
     */
    public static boolean isChampionOrSupportForTopic(User viewer, java.util.List<EsSubscription> topicSubscriptions) {
        if (viewer == null || topicSubscriptions == null) {
            return false;
        }
        String viewerEmail = trimToNull(viewer.getEmailNormalized());
        return topicSubscriptions.stream().anyMatch(s ->
                (s.getStatus() == EsSubscription.SubscriptionStatus.CHAMPION
                        || s.getStatus() == EsSubscription.SubscriptionStatus.SUPPORT)
                        && ((s.getUserId() != null && s.getUserId().equals(viewer.getUserId()))
                                || (viewerEmail != null && viewerEmail.equals(s.getEmailNormalized()))));
    }

    // -------------------------------------------------------------------------
    // Orchestration
    // -------------------------------------------------------------------------

    /**
     * Shared permission check for every managed-follower action (add, invite,
     * edit contact info, and role/unfollow changes): app admins, Topic-Space
     * admins for the topic's space, and champion/support contacts for the
     * topic itself. This is not a high-security area — anyone who can manage
     * a topic's followers at all can also promote, demote, or remove one.
     */
    public boolean canManageFollowers(User viewer, EsTopic topic) {
        if (viewer == null || topic == null) {
            return false;
        }
        boolean isAdmin = authFlowService.isAdminUser(viewer);
        boolean isSpaceAdmin = topicSpaceAccessService.canAdministerSpace(viewer, topic.getEsTopicSpaceId());
        boolean isChampionOrSupport = isChampionOrSupportForTopic(
                viewer, subscriptionDao.findActiveByTopicId(topic.getEsTopicId()));
        return canManageFollowers(isAdmin, isSpaceAdmin, isChampionOrSupport);
    }

    /**
     * Updates the manager-entered contact name/organization for a follower.
     * Used by the "+ Add name" inline editor when no name is otherwise
     * available to display.
     */
    public Outcome<EsSubscription> updateContactInfo(Long esSubscriptionId, Long topicId, String firstName,
            String lastName, String organization) {
        Optional<EsSubscription> subOpt = subscriptionDao.findById(esSubscriptionId);
        if (subOpt.isEmpty() || !subOpt.get().getEsTopicId().equals(topicId)) {
            return Outcome.failure("Follower not found.");
        }
        EsSubscription sub = subOpt.get();
        sub.setContactFirstName(trimToNull(firstName));
        sub.setContactLastName(trimToNull(lastName));
        sub.setContactOrganization(trimToNull(organization));
        EsSubscription saved = subscriptionDao.saveOrUpdate(sub);
        return Outcome.success(saved);
    }

    /**
     * Adds a new managed follower, or reactivates/updates an existing one,
     * deduping by registered user or by email on this topic. Links
     * {@code userId} automatically when the email matches an active,
     * non-deleted registered user.
     */
    public Outcome<EsSubscription> addManagedFollower(Long topicId, String rawEmail, String firstName,
            String lastName, String organization, String reason, Long addedByUserId, boolean sendNotification) {
        String emailNormalized = EsNormalizer.normalizeEmail(rawEmail);
        if (topicId == null || emailNormalized == null) {
            return Outcome.failure("A valid email address is required.");
        }

        Optional<User> matchedUser = userDao.findByEmailNormalized(emailNormalized)
                .filter(u -> u.getStatus() != User.UserStatus.DELETED);

        EsSubscription sub = subscriptionDao
                .findByUserOrEmailAndTopic(matchedUser.map(User::getUserId).orElse(null), emailNormalized, topicId)
                .orElseGet(EsSubscription::new);

        sub.setEmail(rawEmail.trim());
        sub.setEmailNormalized(emailNormalized);
        sub.setEsTopicId(topicId);
        sub.setSubscriptionType(EsSubscription.SubscriptionType.TOPIC);
        if (sub.getStatus() == null || sub.getStatus() == EsSubscription.SubscriptionStatus.UNSUBSCRIBED) {
            sub.setStatus(EsSubscription.SubscriptionStatus.SUBSCRIBED);
            sub.setUnsubscribedAt(null);
        }
        if (matchedUser.isPresent() && sub.getUserId() == null) {
            sub.setUserId(matchedUser.get().getUserId());
        }
        sub.setContactFirstName(trimToNull(firstName));
        sub.setContactLastName(trimToNull(lastName));
        sub.setContactOrganization(trimToNull(organization));
        sub.setManagedAddedByUserId(addedByUserId);
        sub.setManagedAddedAt(LocalDateTime.now());
        sub.setManagedAddReason(trimToNull(reason));
        if (sub.getUnsubscribeTokenHash() == null) {
            sub.setUnsubscribeTokenHash(generateTokenHash());
        }

        EsSubscription saved = subscriptionDao.saveOrUpdate(sub);

        if (sendNotification) {
            sendFollowerAddedEmail(saved, matchedUser.orElse(null), addedByUserId);
        }

        return Outcome.success(saved);
    }

    /** Sends the "you were added as a follower" notification for an existing subscription row. */
    public void sendFollowerAddedEmail(EsSubscription subscription, User matchedUser, Long addedByUserId) {
        try {
            String emailNormalized = subscription.getEmailNormalized();
            if (subscriptionDao.hasGeneralUnsubscribed(emailNormalized)) {
                return;
            }
            String topicName = topicName(subscription.getEsTopicId());
            String addedByName = addedByUserId != null
                    ? userDao.findById(addedByUserId).map(User::getFullName).orElse(null)
                    : null;
            String recipientName = resolveDisplayName(matchedUser, subscription);
            String topicLink = hubLinkService.buildTopicLink(subscription.getEsTopicId());
            String manageLink = buildManageLink(subscription.getEmail(), emailNormalized);
            boolean includeRegistrationCta = matchedUser == null;
            String homeLink = includeRegistrationCta ? hubLinkService.buildLink("/home") : null;

            String subject = EmailTemplates.topicFollowerAddedSubject(topicName);
            String body = EmailTemplates.topicFollowerAddedBody(recipientName, topicName, addedByName,
                    subscription.getManagedAddReason(), topicLink, manageLink, homeLink, includeRegistrationCta);

            EmailService.SendResult result = emailService.send(subscription.getEmail(), subject, body);
            logSend(EmailReason.TOPIC_FOLLOWER_ADDED, subscription.getEmail(), emailNormalized,
                    matchedUser != null ? matchedUser.getUserId() : null, subject, body, result);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING,
                    "Failed to send topic-follower-added email to " + subscription.getEmail(), ex);
        }
    }

    /**
     * Sends the registration-invite email for a follower with no registered
     * user account yet.
     */
    public Outcome<Void> sendRegistrationInvite(Long esSubscriptionId) {
        Optional<EsSubscription> subOpt = subscriptionDao.findById(esSubscriptionId);
        if (subOpt.isEmpty()) {
            return Outcome.failure("Follower not found.");
        }
        EsSubscription sub = subOpt.get();
        if (subscriptionDao.hasGeneralUnsubscribed(sub.getEmailNormalized())) {
            return Outcome.failure("This address has unsubscribed from all InteropHub email.");
        }
        try {
            String topicName = topicName(sub.getEsTopicId());
            String homeLink = hubLinkService.buildLink("/home");
            String subject = EmailTemplates.topicFollowerRegistrationInviteSubject();
            String body = EmailTemplates.topicFollowerRegistrationInviteBody(topicName, homeLink);

            EmailService.SendResult result = emailService.send(sub.getEmail(), subject, body);
            logSend(EmailReason.TOPIC_FOLLOWER_REGISTRATION_INVITE, sub.getEmail(), sub.getEmailNormalized(),
                    null, subject, body, result);
            return Outcome.success(null);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING,
                    "Failed to send registration invite to " + sub.getEmail(), ex);
            return Outcome.failure("Failed to send registration invite: " + ex.getMessage());
        }
    }

    /**
     * Sends the verify-email invite (a magic link) for a follower whose linked
     * user account hasn't verified their email yet.
     */
    public Outcome<Void> sendVerifyEmailInvite(Long esSubscriptionId, HttpServletRequest request) {
        Optional<EsSubscription> subOpt = subscriptionDao.findById(esSubscriptionId);
        if (subOpt.isEmpty() || subOpt.get().getUserId() == null) {
            return Outcome.failure("This follower has no registered account to verify.");
        }
        EsSubscription sub = subOpt.get();
        Optional<User> userOpt = userDao.findById(sub.getUserId())
                .filter(u -> u.getStatus() != User.UserStatus.DELETED);
        if (userOpt.isEmpty()) {
            return Outcome.failure("This follower has no registered account to verify.");
        }
        User user = userOpt.get();
        if (subscriptionDao.hasGeneralUnsubscribed(sub.getEmailNormalized())) {
            return Outcome.failure("This address has unsubscribed from all InteropHub email.");
        }
        try {
            String topicName = topicName(sub.getEsTopicId());
            String magicLinkUrl = authFlowService.issueMagicLink(user, request);
            String subject = EmailTemplates.topicFollowerVerifyEmailSubject();
            String body = EmailTemplates.topicFollowerVerifyEmailBody(topicName, magicLinkUrl);

            EmailService.SendResult result = emailService.send(user.getEmail(), subject, body);
            logSend(EmailReason.TOPIC_FOLLOWER_VERIFY_EMAIL, user.getEmail(), user.getEmailNormalized(),
                    user.getUserId(), subject, body, result);
            return Outcome.success(null);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING,
                    "Failed to send verify-email invite to " + sub.getEmail(), ex);
            return Outcome.failure("Failed to send verification email: " + ex.getMessage());
        }
    }

    /** Most recent send timestamp for a given email + email reason, if any. */
    public Optional<LocalDateTime> lastSentAt(String emailNormalized, String emailReason) {
        return emailSendLogDao.findMostRecentByEmailAndReason(emailNormalized, emailReason)
                .map(EmailSendLog::getSentAt);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String topicName(Long topicId) {
        if (topicId == null) {
            return null;
        }
        EsTopic topic = topicDao.findById(topicId).orElse(null);
        return topic != null ? topic.getTopicName() : null;
    }

    private String buildManageLink(String emailRaw, String emailNormalized) {
        String email = emailRaw != null && !emailRaw.isBlank() ? emailRaw : emailNormalized;
        String encoded = URLEncoder.encode(email, StandardCharsets.UTF_8);
        return hubLinkService.buildLink("/es/unsubscribe?email=" + encoded);
    }

    private void logSend(String reason, String recipientEmail, String recipientEmailNormalized, Long userId,
            String subject, String body, EmailService.SendResult result) {
        EmailSendLog logEntry = new EmailSendLog();
        logEntry.setEmailReason(reason);
        logEntry.setRecipientEmail(recipientEmail);
        logEntry.setRecipientEmailNormalized(recipientEmailNormalized);
        logEntry.setUserId(userId);
        logEntry.setSubject(subject);
        logEntry.setBodyText(body);
        logEntry.setSmtpMessageId(result.getSmtpMessageId());
        logEntry.setSmtpProvider(result.getSmtpProvider());
        emailSendLogDao.log(logEntry);
    }

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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Simple success/failure result wrapper for orchestration methods. */
    public static final class Outcome<T> {
        private final boolean success;
        private final T value;
        private final String errorMessage;

        private Outcome(boolean success, T value, String errorMessage) {
            this.success = success;
            this.value = value;
            this.errorMessage = errorMessage;
        }

        public static <T> Outcome<T> success(T value) {
            return new Outcome<>(true, value, null);
        }

        public static <T> Outcome<T> failure(String errorMessage) {
            return new Outcome<>(false, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public T getValue() {
            return value;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
