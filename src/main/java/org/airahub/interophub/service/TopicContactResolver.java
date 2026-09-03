package org.airahub.interophub.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.User;

/**
 * Resolves who should be notified about activity on a topic: its support and
 * champion contacts (both), falling back to administrators when a topic has
 * neither. Shared by any feature that alerts topic contacts (comment
 * notifications, the daily digest, etc.).
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
    private final UserDao userDao;

    public TopicContactResolver() {
        this.subscriptionDao = new EsSubscriptionDao();
        this.userDao = new UserDao();
    }

    /**
     * Returns support + champion contacts for the topic (deduped by email);
     * if neither exists, returns all administrators instead.
     */
    public List<Contact> resolveSupportAndChampionOrAdmins(Long esTopicId) {
        Map<String, Contact> contacts = new LinkedHashMap<>();
        for (EsSubscription sub : subscriptionDao.findSupportsByTopicId(esTopicId)) {
            addContact(contacts, sub.getEmail(), sub.getEmailNormalized(), sub.getUserId(), ContactRole.SUPPORT);
        }
        for (EsSubscription sub : subscriptionDao.findChampionOnlyByTopicId(esTopicId)) {
            addContact(contacts, sub.getEmail(), sub.getEmailNormalized(), sub.getUserId(), ContactRole.CHAMPION);
        }
        if (contacts.isEmpty()) {
            for (User admin : userDao.findAllAdmins()) {
                addContact(contacts, admin.getEmail(), admin.getEmailNormalized(), admin.getUserId(),
                        ContactRole.ADMIN);
            }
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
