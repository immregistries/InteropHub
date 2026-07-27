package org.airahub.interophub.service;

import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsTopicNote;
import org.airahub.interophub.model.EsLiveVote;
import org.airahub.interophub.model.TopicNoteStatus;
import org.airahub.interophub.model.LiveVoteStatus;

public class MeetingImmutabilityGuard {

    public void ensureMeetingMutable(EsMeeting meeting) {
        if (meeting != null && meeting.getStatus() == EsMeeting.MeetingStatus.CLOSED) {
            throw new IllegalStateException("Meeting is closed and immutable.");
        }
    }

    public void ensureNoteMutable(EsTopicNote note) {
        if (note != null && note.getStatus() == TopicNoteStatus.FINALIZED) {
            throw new IllegalStateException("Topic note is finalized and immutable.");
        }
    }

    public void ensureNoteAndMeetingMutable(EsTopicNote note, EsMeeting meeting) {
        ensureNoteMutable(note);
        ensureMeetingMutable(meeting);
        if (meeting != null && meeting.getStatus() == EsMeeting.MeetingStatus.CLOSED) {
            throw new IllegalStateException("Meeting is closed and immutable.");
        }
    }

    public void ensureVoteMutable(EsLiveVote vote, EsMeeting meeting, EsTopicNote note) {
        if (vote != null && vote.getStatus() == LiveVoteStatus.CLOSED) {
            throw new IllegalStateException("Live vote is closed and immutable.");
        }
        ensureMeetingMutable(meeting);
        ensureNoteMutable(note);
    }
}