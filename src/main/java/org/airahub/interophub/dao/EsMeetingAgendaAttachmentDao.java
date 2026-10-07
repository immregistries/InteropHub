package org.airahub.interophub.dao;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaAttachment;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.StoredFile;
import org.airahub.interophub.service.MeetingAttachmentService;
import org.hibernate.Session;
import org.hibernate.Transaction;

public class EsMeetingAgendaAttachmentDao {
    private static final Logger LOGGER = Logger.getLogger(EsMeetingAgendaAttachmentDao.class.getName());

    public Map<Long, List<EsMeetingAgendaAttachment>> findActiveByMeetingId(Long meetingId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select a from EsMeetingAgendaAttachment a join fetch a.storedFile"
                            + " where a.removedAt is null and a.agendaItemId in"
                            + " (select i.esMeetingAgendaItemId from EsMeetingAgendaItem i where i.esMeetingId = :meeting)"
                            + " order by a.attachedAt, a.attachmentId", EsMeetingAgendaAttachment.class)
                    .setParameter("meeting", meetingId).getResultList().stream()
                    .collect(Collectors.groupingBy(EsMeetingAgendaAttachment::getAgendaItemId,
                            LinkedHashMap::new, Collectors.toList()));
        }
    }

    public StoredFile register(Long meetingId, Long itemId, Long userId, StoredFile candidate) {
        return mutate(meetingId, itemId, session -> {
            if (candidate.getStoredFileId() != null) {
                throw new IllegalArgumentException("Meeting attachments must be new files, not replacements.");
            }
            session.persist(candidate);
            EsMeetingAgendaAttachment attachment = new EsMeetingAgendaAttachment();
            attachment.setAgendaItemId(itemId);
            attachment.setStoredFile(candidate);
            attachment.setAttachedByUserId(userId);
            attachment.setAttachedAt(LocalDateTime.now());
            session.persist(attachment);
            return candidate;
        });
    }

    public void detach(Long meetingId, Long itemId, Long attachmentId, Long userId) {
        mutate(meetingId, itemId, session -> {
            EsMeetingAgendaAttachment attachment = attachmentId == null ? null
                    : session.find(EsMeetingAgendaAttachment.class, attachmentId, LockModeType.PESSIMISTIC_WRITE);
            if (attachment == null || !itemId.equals(attachment.getAgendaItemId())
                    || attachment.getRemovedAt() != null) {
                throw new IllegalArgumentException("Active attachment does not belong to the selected agenda item.");
            }
            attachment.setRemovedByUserId(userId);
            attachment.setRemovedAt(LocalDateTime.now());
            return null;
        });
    }

    private <T> T mutate(Long meetingId, Long itemId, Function<Session, T> mutation) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction transaction = session.beginTransaction();
            try {
                EsMeeting meeting = meetingId == null ? null
                        : session.find(EsMeeting.class, meetingId, LockModeType.PESSIMISTIC_WRITE);
                EsMeetingAgendaItem item = itemId == null ? null
                        : session.find(EsMeetingAgendaItem.class, itemId, LockModeType.PESSIMISTIC_WRITE);
                // Recheck lifecycle under locks after a potentially slow file upload.
                MeetingAttachmentService.requireEditableTarget(meeting, item, meetingId, itemId);
                T result = mutation.apply(session);
                transaction.commit();
                return result;
            } catch (RuntimeException ex) {
                try {
                    transaction.rollback();
                } catch (RuntimeException rollback) {
                    ex.addSuppressed(rollback);
                }
                LOGGER.log(Level.WARNING, "Meeting attachment transaction failed", ex);
                throw ex;
            }
        }
    }
}
