package org.airahub.interophub.service;

public class MeetingMaintenanceService {

    private final TopicNoteService topicNoteService;
    private final MeetingLifecycleService meetingLifecycleService;

    public MeetingMaintenanceService() {
        this.topicNoteService = new TopicNoteService();
        this.meetingLifecycleService = new MeetingLifecycleService();
    }

    public int runDueMaintenance() {
        int finalized = topicNoteService.automaticallyFinalizeDueAdHocNotes();
        int closed = meetingLifecycleService.automaticallyCloseDueMeetings();
        return finalized + closed;
    }
}