package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.airahub.interophub.model.EsMeeting;
import org.junit.jupiter.api.Test;

class MeetingWindowRulesTest {

    @Test
    void startWindowOpensAtFifteenMinutesBeforeScheduledStart() {
        EsMeeting meeting = new EsMeeting();
        meeting.setScheduledStart(LocalDateTime.of(2026, 1, 15, 10, 0));
        meeting.setTimezoneId("America/New_York");

        Instant beforeWindow = LocalDateTime.of(2026, 1, 15, 9, 44)
                .atZone(ZoneId.of("America/New_York"))
                .toInstant();
        Instant openingTime = LocalDateTime.of(2026, 1, 15, 9, 45)
                .atZone(ZoneId.of("America/New_York"))
                .toInstant();

        assertFalse(MeetingWindowRules.isMeetingStartWindowOpen(meeting, beforeWindow));
        assertTrue(MeetingWindowRules.isMeetingStartWindowOpen(meeting, openingTime));
    }

    @Test
    void invalidTimezoneFallsBackWithoutThrowing() {
        EsMeeting meeting = new EsMeeting();
        meeting.setScheduledStart(LocalDateTime.of(2026, 1, 15, 10, 0));
        meeting.setTimezoneId("Not/A_Real_Timezone");

        assertTrue(MeetingWindowRules.isMeetingStartWindowOpen(meeting,
                Instant.parse("2026-01-15T10:00:00Z")));
    }
}
