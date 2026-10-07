package org.airahub.interophub.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import org.airahub.interophub.dao.EsMeetingAgendaAttachmentDao;
import org.airahub.interophub.dao.EsMeetingAgendaItemDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.StoredFile;
import org.airahub.interophub.model.User;

public class MeetingAttachmentService {
    private final EsMeetingDao meetings;
    private final EsMeetingAgendaItemDao items;
    private final EsMeetingAgendaAttachmentDao attachments;
    private final MeetingAuthorizationService authorization;
    private final TopicSpaceAccessService access;
    private final StoredFileService storage;

    public MeetingAttachmentService() {
        this(new EsMeetingDao(), new EsMeetingAgendaItemDao(), new EsMeetingAgendaAttachmentDao(),
                new MeetingAuthorizationService(), new TopicSpaceAccessService(), new StoredFileService());
    }

    MeetingAttachmentService(EsMeetingDao meetings, EsMeetingAgendaItemDao items,
            EsMeetingAgendaAttachmentDao attachments, MeetingAuthorizationService authorization,
            TopicSpaceAccessService access, StoredFileService storage) {
        this.meetings = meetings;
        this.items = items;
        this.attachments = attachments;
        this.authorization = authorization;
        this.access = access;
        this.storage = storage;
    }

    public StoredFileService storage() { return storage; }

    public StoredFile upload(User user, Long meetingId, Long itemId, InputStream content,
            String filename, String declaredType) throws IOException {
        requireWriteAccess(user, meetingId, itemId);
        String name = requireAllowedFilename(filename);
        return storage.upload(null, content, name, declaredType, user.getUserId(), true, file -> {
            requireWriteAccess(user, meetingId, itemId);
            return attachments.register(meetingId, itemId, user.getUserId(), file);
        });
    }

    public void detach(User user, Long meetingId, Long itemId, Long attachmentId) {
        requireWriteAccess(user, meetingId, itemId);
        attachments.detach(meetingId, itemId, attachmentId, user.getUserId());
    }

    private void requireWriteAccess(User user, Long meetingId, Long itemId) {
        EsMeeting meeting = meetingId == null ? null : meetings.findById(meetingId).orElse(null);
        if (user == null || meeting == null || !access.canViewMeeting(user, meeting)
                || !authorization.canControlMeeting(user.getUserId(), meeting)) {
            throw new SecurityException("You do not have permission to change this meeting's attachments.");
        }
        EsMeetingAgendaItem item = itemId == null ? null : items.findById(itemId).orElse(null);
        requireEditableTarget(meeting, item, meetingId, itemId);
    }

    public static void requireEditableTarget(EsMeeting meeting, EsMeetingAgendaItem item,
            Long meetingId, Long itemId) {
        if (meeting == null || item == null || meetingId == null || itemId == null
                || !meetingId.equals(meeting.getEsMeetingId())
                || !meetingId.equals(item.getEsMeetingId()) || !itemId.equals(item.getEsMeetingAgendaItemId())) {
            throw new IllegalArgumentException("Select an agenda item that belongs to this meeting.");
        }
        if (meeting.getStatus() == null || item.getStatus() == null
                || meeting.getStatus() == EsMeeting.MeetingStatus.CLOSED
                || meeting.getStatus() == EsMeeting.MeetingStatus.CANCELLED
                || item.getStatus() == EsMeetingAgendaItem.AgendaItemStatus.CANCELLED
                || item.getStatus() == EsMeetingAgendaItem.AgendaItemStatus.POSTPONED) {
            throw new IllegalStateException("Attachments cannot be changed for closed/cancelled meetings"
                    + " or cancelled/postponed agenda items.");
        }
    }

    public static String requireAllowedFilename(String filename) {
        String name = StoredFileValidation.filename(filename);
        if (!name.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|webp|gif|pdf|pptx?)$")) {
            throw new IllegalArgumentException("Meeting attachments allow raster images, PDF, and PPT/PPTX only.");
        }
        return name;
    }
}
