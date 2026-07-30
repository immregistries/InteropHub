package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.airahub.interophub.model.CommunicationEligibilityResult;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingCommunication;
import org.junit.jupiter.api.Test;

class MeetingCommunicationEligibilityServiceTest {

    @Test
    void allowsCancellationNoticeWhenMeetingIsCancelled() {
        MeetingCommunicationEligibilityService service = new MeetingCommunicationEligibilityService();

        EsMeeting meeting = new EsMeeting();
        meeting.setStatus(EsMeeting.MeetingStatus.CANCELLED);

        EsMeetingCommunication communication = new EsMeetingCommunication();
        communication.setCommunicationType(EsMeetingCommunication.CommunicationType.CANCELLED);
        communication.setStatus(EsMeetingCommunication.CommunicationStatus.DRAFT);

        CommunicationEligibilityResult result = service.check(communication, meeting);

        assertTrue(result.isEligible());
    }

    @Test
    void blocksNonCancellationCommunicationWhenMeetingIsCancelled() {
        MeetingCommunicationEligibilityService service = new MeetingCommunicationEligibilityService();

        EsMeeting meeting = new EsMeeting();
        meeting.setStatus(EsMeeting.MeetingStatus.CANCELLED);

        EsMeetingCommunication communication = new EsMeetingCommunication();
        communication.setCommunicationType(EsMeetingCommunication.CommunicationType.FINAL_AGENDA);
        communication.setStatus(EsMeetingCommunication.CommunicationStatus.DRAFT);

        CommunicationEligibilityResult result = service.check(communication, meeting);

        assertFalse(result.isEligible());
    }
}
