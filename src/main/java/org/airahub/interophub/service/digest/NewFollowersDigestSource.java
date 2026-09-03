package org.airahub.interophub.service.digest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.HubLinkService;
import org.airahub.interophub.service.TopicContactResolver;

/**
 * Digest source: topics that gained new followers since the last run, sent
 * to each topic's support/champion contacts (or admins if neither exists).
 */
public class NewFollowersDigestSource implements DigestItemSource {

    public static final String SECTION_TITLE = "New Followers";

    private final EsSubscriptionDao subscriptionDao;
    private final EsTopicDao topicDao;
    private final UserDao userDao;
    private final TopicContactResolver topicContactResolver;
    private final HubLinkService hubLinkService;

    public NewFollowersDigestSource() {
        this.subscriptionDao = new EsSubscriptionDao();
        this.topicDao = new EsTopicDao();
        this.userDao = new UserDao();
        this.topicContactResolver = new TopicContactResolver();
        this.hubLinkService = new HubLinkService();
    }

    @Override
    public String key() {
        return "NEW_FOLLOWERS";
    }

    @Override
    public List<DigestNotice> collect(LocalDateTime since, LocalDateTime until) {
        List<EsSubscription> newFollowers = subscriptionDao.findNewTopicFollowersCreatedBetween(since, until);
        if (newFollowers.isEmpty()) {
            return List.of();
        }

        Map<Long, List<EsSubscription>> followersByTopicId = new LinkedHashMap<>();
        for (EsSubscription sub : newFollowers) {
            followersByTopicId.computeIfAbsent(sub.getEsTopicId(), k -> new ArrayList<>()).add(sub);
        }

        List<Long> followerUserIds = newFollowers.stream()
                .map(EsSubscription::getUserId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, User> usersById = new LinkedHashMap<>();
        for (User user : userDao.findByIds(followerUserIds)) {
            usersById.put(user.getUserId(), user);
        }

        List<DigestNotice> notices = new ArrayList<>();
        for (Map.Entry<Long, List<EsSubscription>> entry : followersByTopicId.entrySet()) {
            Long topicId = entry.getKey();
            EsTopic topic = topicDao.findById(topicId).orElse(null);
            if (topic == null) {
                continue;
            }
            String topicName = topic.getTopicName() != null ? topic.getTopicName() : "Untitled Topic";
            String topicLink = hubLinkService.buildTopicLink(topicId);

            StringBuilder body = new StringBuilder();
            body.append(topicName).append("\n");
            for (EsSubscription follower : entry.getValue()) {
                body.append("  - ").append(displayName(follower, usersById))
                        .append(" (").append(follower.getEmail()).append(")\n");
            }
            body.append("  ").append(topicLink).append("\n");
            String bodyText = body.toString();

            for (TopicContactResolver.Contact contact : topicContactResolver
                    .resolveContactsForTopic(topic)) {
                notices.add(new DigestNotice(contact.email(), contact.emailNormalized(), contact.userId(),
                        SECTION_TITLE, bodyText));
            }
        }
        return notices;
    }

    private String displayName(EsSubscription follower, Map<Long, User> usersById) {
        User user = follower.getUserId() != null ? usersById.get(follower.getUserId()) : null;
        String fullName = user != null ? trimToNull(user.getFullName()) : null;
        if (fullName != null) {
            return fullName;
        }
        return follower.getEmail() != null ? follower.getEmail() : "A new follower";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
