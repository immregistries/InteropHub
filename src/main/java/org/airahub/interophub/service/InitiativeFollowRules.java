package org.airahub.interophub.service;

import java.util.Optional;
import org.airahub.interophub.model.EsSubscription;

/**
 * Decides whether becoming {@code JOINED} should create or restore a follow of
 * the initiative's primary topic. The follow is recorded through the managed
 * follower path and does not send the follower-added email.
 */
public final class InitiativeFollowRules {

    public record FollowRequest(Long topicId, String email, String firstName, String lastName, String organization,
            String reason, boolean sendNotification) {
    }

    private InitiativeFollowRules() {
    }

    /**
     * @param previousStatus status before this change; null when the member is new
     * @param existingFollow current topic subscription status, or null when there is none
     */
    public static Optional<FollowRequest> plan(String initiativeName, Long topicId, String previousStatus,
            String newStatus, String email, String firstName, String lastName, String organization,
            EsSubscription.SubscriptionStatus existingFollow) {
        if (!"JOINED".equals(newStatus) || "JOINED".equals(previousStatus)) {
            return Optional.empty();
        }
        if (email == null || email.isBlank() || topicId == null) {
            return Optional.empty();
        }
        if (existingFollow == EsSubscription.SubscriptionStatus.SUBSCRIBED
                || existingFollow == EsSubscription.SubscriptionStatus.CHAMPION
                || existingFollow == EsSubscription.SubscriptionStatus.SUPPORT) {
            return Optional.empty();
        }
        String name = initiativeName == null || initiativeName.isBlank() ? "an initiative" : initiativeName.trim();
        return Optional.of(new FollowRequest(
                topicId,
                email.trim(),
                PersonNames.nameOrNull(firstName),
                PersonNames.nameOrNull(lastName),
                organization,
                "Joined the " + name + " initiative.",
                false));
    }
}
