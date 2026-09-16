package org.airahub.interophub.model;

/**
 * A participant's stated intent to attend a specific meeting occurrence -
 * separate from actual attendance (see docs/meeting-attendance-console-design.md,
 * "Meeting RSVP"). RSVP is intent, not a record of who showed up.
 */
public enum MeetingRsvpResponse {
    COMING,
    MAYBE,
    NOT_COMING
}
