package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.User;
import org.junit.jupiter.api.Test;

class TopicSpaceAccessServicePolicyTest {

    @Test
    void canEditTopicAllowsAdminsAndActiveChampionOrSupporterButNotReaders() {
        EsTopic topic = new EsTopic();
        topic.setEsTopicId(17L);
        User user = new User();
        user.setUserId(22L);

        assertTrue(access(true, false).canEditTopic(user, topic));
        assertTrue(access(false, true).canEditTopic(user, topic));
        assertFalse(access(false, false).canEditTopic(user, topic));
    }

    @Test
    void topicCreationRequiresAnActiveSpaceAndSpaceAdmin() {
        assertTrue(TopicSpaceAccessService.mayCreateTopic(true, true));
        assertFalse(TopicSpaceAccessService.mayCreateTopic(true, false));
        assertFalse(TopicSpaceAccessService.mayCreateTopic(false, true));
    }

    private TopicSpaceAccessService access(boolean isSpaceAdmin, boolean activeChampionOrSupport) {
        EsSubscriptionDao subscriptions = new EsSubscriptionDao() {
            @Override
            public boolean hasActiveChampionOrSupportForTopicAndUser(Long topicId, Long userId, String email) {
                return activeChampionOrSupport;
            }
        };
        return new TopicSpaceAccessService(new AuthFlowService(), subscriptions) {
            @Override
            public boolean canAdministerSpace(User user, Long spaceId) {
                return isSpaceAdmin;
            }
        };
    }
}
