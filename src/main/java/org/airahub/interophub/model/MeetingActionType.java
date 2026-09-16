package org.airahub.interophub.model;

/**
 * One phase of the fixed meeting cadence that can appear as a shared action in
 * a user's action queue. See docs/interophub-meeting-cadence-design.md.
 */
public enum MeetingActionType {
    PUBLISH_PROPOSED_AGENDA,
    FINALIZE_AGENDA,
    CLOSE_MEETING,
    PUBLISH_NOTES
}
