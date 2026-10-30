package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.airahub.interophub.model.EsSubscription;

/**
 * What happens when staff add or remove a Small Group participant.
 * Removing a participant does not unfollow the topic.
 */
public final class SmallGroupParticipantRules {

    public record AddPlan(boolean markJoined, Optional<InitiativeFollowRules.FollowRequest> topicFollow) {
    }

    public record OrgRow(long participationId, String status, String type) {
    }

    private SmallGroupParticipantRules() {
    }

    public static boolean needsJoined(String memberStatus) {
        return !"JOINED".equals(memberStatus);
    }

    /** Removal leaves topic follows as they are. */
    public static boolean unfollowOnRemove() {
        return false;
    }

    public static AddPlan planAdd(String memberStatus, String groupName, Long topicId, String email, String firstName,
            String lastName, String organization, EsSubscription.SubscriptionStatus existingFollow) {
        return new AddPlan(needsJoined(memberStatus),
                topicFollow(groupName, topicId, email, firstName, lastName, organization, existingFollow));
    }

    public static AddPlan planRemove() {
        return new AddPlan(false, Optional.empty());
    }

    public static Optional<InitiativeFollowRules.FollowRequest> topicFollow(String groupName, Long topicId,
            String email, String firstName, String lastName, String organization,
            EsSubscription.SubscriptionStatus existingFollow) {
        if (email == null || email.isBlank() || topicId == null) {
            return Optional.empty();
        }
        if (existingFollow == EsSubscription.SubscriptionStatus.SUBSCRIBED
                || existingFollow == EsSubscription.SubscriptionStatus.CHAMPION
                || existingFollow == EsSubscription.SubscriptionStatus.SUPPORT) {
            return Optional.empty();
        }
        String name = groupName == null || groupName.isBlank() ? "a small group" : groupName.trim();
        return Optional.of(new InitiativeFollowRules.FollowRequest(
                topicId,
                email.trim(),
                PersonNames.nameOrNull(firstName),
                PersonNames.nameOrNull(lastName),
                organization,
                "Taking part in the " + name + " small group.",
                false));
    }

    public static List<Long> memberIdsToCopy(Set<Long> alreadyActiveMemberIds, List<Long> sourceActiveMemberIds) {
        List<Long> copy = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        if (sourceActiveMemberIds == null) {
            return copy;
        }
        for (Long memberId : sourceActiveMemberIds) {
            if (memberId == null || seen.contains(memberId)) {
                continue;
            }
            seen.add(memberId);
            if (alreadyActiveMemberIds != null && alreadyActiveMemberIds.contains(memberId)) {
                continue;
            }
            copy.add(memberId);
        }
        return copy;
    }

    /**
     * Organization hints for a Small Group checklist. Representation counts
     * committed organizations that have an active participant. Organizations
     * that need a contact are interested or committed and have none.
     */
    public static ChecklistHints.Facts organizationFacts(List<OrgRow> organizations,
            Set<Long> representedParticipationIds, boolean topicHasChampion, int joinedMembersMissingFollow) {
        List<String> committedTypes = new ArrayList<>();
        int needingContact = 0;
        Set<Long> represented = representedParticipationIds == null ? Set.of() : representedParticipationIds;
        if (organizations != null) {
            for (OrgRow organization : organizations) {
                boolean inGroup = represented.contains(organization.participationId());
                boolean committed = "COMMITTED".equals(organization.status());
                boolean interested = "INTERESTED".equals(organization.status());
                if (committed && inGroup && organization.type() != null && !organization.type().isBlank()) {
                    committedTypes.add(organization.type());
                }
                if ((committed || interested) && !inGroup) {
                    needingContact++;
                }
            }
        }
        return new ChecklistHints.Facts(InitiativeRosterRules.summarize(committedTypes), needingContact,
                topicHasChampion, joinedMembersMissingFollow);
    }

    public static String productAreas(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        List<String> ordered = new ArrayList<>();
        for (String allowed : List.of("GUIDANCE", "TESTS", "SOFTWARE")) {
            for (String code : codes) {
                if (allowed.equals(code) && !ordered.contains(allowed)) {
                    ordered.add(allowed);
                }
            }
        }
        for (String code : codes) {
            if (code != null && !code.isBlank() && !List.of("GUIDANCE", "TESTS", "SOFTWARE").contains(code)) {
                throw new IllegalArgumentException("Product area " + code + " is not recognized.");
            }
        }
        return ordered.isEmpty() ? null : String.join(",", ordered);
    }

    public static List<String> splitProductAreas(String stored) {
        List<String> areas = new ArrayList<>();
        if (stored == null || stored.isBlank()) {
            return areas;
        }
        for (String part : stored.split(",")) {
            String code = part.trim();
            if (!code.isEmpty()) {
                areas.add(code);
            }
        }
        return areas;
    }
}
