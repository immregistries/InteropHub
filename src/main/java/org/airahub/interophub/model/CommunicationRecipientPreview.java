package org.airahub.interophub.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a single resolved recipient for a meeting communication.
 * This is an in-memory (non-persistent) value object.
 */
public class CommunicationRecipientPreview {

    private final String email;
    private final String emailNormalized;
    private final Long userId;
    private final String displayName;
    /** Highest-priority group this recipient falls into. */
    private final RecipientGroup primaryGroup;
    /** All groups this recipient qualifies for (before deduplication priority). */
    private final Set<RecipientGroup> allGroups;
    /** Topic names relevant to this recipient (may be empty). */
    private final List<String> topicNames;
    /** Agenda item titles where this recipient is a presenter (may be empty). */
    private final List<String> agendaItemTitles;

    public CommunicationRecipientPreview(
            String email,
            String emailNormalized,
            Long userId,
            String displayName,
            RecipientGroup primaryGroup,
            Set<RecipientGroup> allGroups,
            List<String> topicNames,
            List<String> agendaItemTitles) {
        this.email = email;
        this.emailNormalized = emailNormalized;
        this.userId = userId;
        this.displayName = displayName;
        this.primaryGroup = primaryGroup;
        this.allGroups = Collections.unmodifiableSet(EnumSet.copyOf(allGroups));
        this.topicNames = Collections.unmodifiableList(topicNames);
        this.agendaItemTitles = Collections.unmodifiableList(agendaItemTitles);
    }

    public String getEmail() {
        return email;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public Long getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public RecipientGroup getPrimaryGroup() {
        return primaryGroup;
    }

    public Set<RecipientGroup> getAllGroups() {
        return allGroups;
    }

    public List<String> getTopicNames() {
        return topicNames;
    }

    public List<String> getAgendaItemTitles() {
        return agendaItemTitles;
    }
}
