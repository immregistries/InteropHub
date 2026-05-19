package org.airahub.interophub.model;

/**
 * Identifies which recipient group a person belongs to in the context of a
 * meeting communication. Priority (highest to lowest) is used when a person
 * qualifies for multiple groups: AGENDA_PRESENTER > TOPIC_CHAMPION >
 * TOPIC_SUBSCRIBER > GENERAL_MEETING_MEMBER.
 */
public enum RecipientGroup {
    GENERAL_MEETING_MEMBER,
    TOPIC_SUBSCRIBER,
    TOPIC_CHAMPION,
    AGENDA_PRESENTER
}
