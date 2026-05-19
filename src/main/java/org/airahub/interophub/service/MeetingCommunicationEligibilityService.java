package org.airahub.interophub.service;

import org.airahub.interophub.model.CommunicationEligibilityResult;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingCommunication;
import org.airahub.interophub.model.EsMeetingCommunication.CommunicationStatus;

/**
 * Checks whether a communication is eligible to be scheduled or sent.
 *
 * Three checks (all must pass):
 * 1. Communication is not in a terminal state (SENT, CANCELLED, FAILED).
 * 2. Meeting is not cancelled.
 * 3. Meeting status matches the expected status for this communication type
 * (advisory warning — does not hard-block to allow flexibility).
 */
public class MeetingCommunicationEligibilityService {

    private final MeetingCommunicationRenderer renderer;

    public MeetingCommunicationEligibilityService() {
        this.renderer = new MeetingCommunicationRenderer();
    }

    public MeetingCommunicationEligibilityService(MeetingCommunicationRenderer renderer) {
        this.renderer = renderer;
    }

    /**
     * Returns an eligibility result for sending the given communication.
     * Call this before allowing the user to schedule or manually trigger a send.
     */
    public CommunicationEligibilityResult check(
            EsMeetingCommunication communication,
            EsMeeting meeting) {

        // Check 1: terminal communication status
        CommunicationStatus status = communication.getStatus();
        if (status == CommunicationStatus.SENT) {
            return CommunicationEligibilityResult.blocked(
                    "This communication has already been sent.");
        }
        if (status == CommunicationStatus.CANCELLED) {
            return CommunicationEligibilityResult.blocked(
                    "This communication has been cancelled.");
        }
        if (status == CommunicationStatus.FAILED) {
            return CommunicationEligibilityResult.blocked(
                    "This communication failed to send. Create a new communication to retry.");
        }
        if (status == CommunicationStatus.SENDING) {
            return CommunicationEligibilityResult.blocked(
                    "This communication is currently being sent.");
        }

        // Check 2: meeting not cancelled
        if (meeting.getStatus() == EsMeeting.MeetingStatus.CANCELLED) {
            return CommunicationEligibilityResult.blocked(
                    "The meeting has been cancelled. Cancel this communication or create a "
                            + "cancellation notice instead.");
        }

        // Check 3: advisory — expected meeting status mismatch
        EsMeeting.MeetingStatus expected = renderer.handlerFor(communication.getCommunicationType())
                .expectedMeetingStatus();
        if (meeting.getStatus() != expected) {
            return CommunicationEligibilityResult.blocked(
                    "This communication type is intended for meetings in status '"
                            + expected.name() + "', but the meeting is currently '"
                            + meeting.getStatus().name() + "'. "
                            + "Update the meeting status or choose a different communication type.");
        }

        return CommunicationEligibilityResult.ok();
    }
}
