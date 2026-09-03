package org.airahub.interophub.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicSpaceMemberDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicSpaceMember;
import org.airahub.interophub.model.User;

/**
 * Resolves who should be notified about activity on a topic: its support and
 * champion contacts (both); if neither exists, the ADMIN members of its
 * Topic Space; if there are none of those either, all site administrators.
 * Shared by any feature that alerts topic contacts (comment notifications,
 * the daily digest, etc.).
 */
public class TopicContactResolver {

    public enum ContactRole {
        SUPPORT,
        CHAMPION,
        ADMIN
    }

    public record Contact(String email, String emailNormalized, Long userId, ContactRole role) {
    }

    private final EsSubscriptionDao subscriptionDao;
    private final EsTopicSpaceMemberDao topicSpaceMemberDao;
    private final UserDao userDao;

    public TopicContactResolver() {
        this.subscriptionDao = new EsSubscriptionDao();
        this.topicSpaceMemberDao = new EsTopicSpaceMemberDao();
        this.userDao = new UserDao();
    }

    public List<Contact> resolveContactsForTopic(EsTopic topic) {
        List<Contact> topicContacts = collectTopicContacts(topic.getEsTopicId());
        if (!topicContacts.isEmpty()) {
            return topicContacts;
        }

        if (topic.getEsTopicSpaceId() != null) {
            List<Contact> spaceAdmins = collectSpaceAdminContacts(topic.getEsTopicSpaceId());
            if (!spaceAdmins.isEmpty()) {
                return spaceAdmins;
            }
        }

        return collectSiteAdminContacts();
    }

    private List<Contact> collectTopicContacts(Long esTopicId) {
        Map<String, Contact> contacts = new LinkedHashMap<>();
        for (EsSubscription sub : subscriptionDao.findSupportsByTopicId(esTopicId)) {
            addContact(contacts, sub.getEmail(), sub.getEmailNormalized(), sub.getUserId(), ContactRole.SUPPORT);
        }
        for (EsSubscription sub : subscriptionDao.findChampionOnlyByTopicId(esTopicId)) {
            addContact(contacts, sub.getEmail(), sub.getEmailNormalized(), sub.getUserId(), ContactRole.CHAMPION);
        }
        return List.copyOf(contacts.values());
    }

    private List<Contact> collectSpaceAdminContacts(Long esTopicSpaceId) {
        List<EsTopicSpaceMember> admins = topicSpaceMemberDao.findAdminsBySpaceId(esTopicSpaceId);
        if (admins.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = admins.stream().map(EsTopicSpaceMember::getUserId).distinct().toList();
        Map<Long, User> usersById = new LinkedHashMap<>();
        for (User user : userDao.findByIds(userIds)) {
            usersById.put(user.getUserId(), user);
        }

        Map<String, Contact> contacts = new LinkedHashMap<>();
        for (EsTopicSpaceMember admin : admins) {
            User user = usersById.get(admin.getUserId());
            if (user == null) {
                continue;
            }
            addContact(contacts, user.getEmail(), user.getEmailNormalized(), user.getUserId(), ContactRole.ADMIN);
        }
        return List.copyOf(contacts.values());
    }

    private List<Contact> collectSiteAdminContacts() {
        Map<String, Contact> contacts = new LinkedHashMap<>();
        for (User admin : userDao.findAllAdmins()) {
            addContact(contacts, admin.getEmail(), admin.getEmailNormalized(), admin.getUserId(), ContactRole.ADMIN);
        }
        return List.copyOf(contacts.values());
    }

    private void addContact(Map<String, Contact> contacts, String email, String emailNormalized, Long userId,
            ContactRole role) {
        if (emailNormalized == null || email == null || contacts.containsKey(emailNormalized)) {
            return;
        }
        contacts.put(emailNormalized, new Contact(email, emailNormalized, userId, role));
    }
}
