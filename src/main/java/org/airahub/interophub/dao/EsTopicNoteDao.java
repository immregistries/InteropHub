package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicNote;
import org.airahub.interophub.model.TopicNoteStatus;

public class EsTopicNoteDao extends GenericDao<EsTopicNote, Long> {

    public EsTopicNoteDao() {
        super(EsTopicNote.class);
    }

    public Optional<EsTopicNote> findByAgendaItemId(Long esMeetingAgendaItemId) {
        if (esMeetingAgendaItemId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicNote n where n.esMeetingAgendaItemId = :agendaItemId"
                            + " order by n.createdAt desc, n.esTopicNoteId desc",
                    EsTopicNote.class)
                    .setParameter("agendaItemId", esMeetingAgendaItemId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<EsTopicNote> findChronologicalByTopicId(Long esTopicId) {
        if (esTopicId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicNote n where n.esTopicId = :topicId order by n.createdAt asc, n.esTopicNoteId asc",
                    EsTopicNote.class)
                    .setParameter("topicId", esTopicId)
                    .getResultList();
        }
    }

    public List<EsTopicNote> findDueForFinalization(LocalDateTime dueAt) {
        if (dueAt == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicNote n where n.status = :status and n.finalizeAt is not null"
                            + " and n.finalizeAt <= :dueAt order by n.finalizeAt asc, n.esTopicNoteId asc",
                    EsTopicNote.class)
                    .setParameter("status", TopicNoteStatus.OPEN)
                    .setParameter("dueAt", dueAt)
                    .getResultList();
        }
    }

    public List<EsTopicNote> findOpenByMeetingId(Long esMeetingId) {
        if (esMeetingId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicNote n where n.esMeetingId = :meetingId and n.status = :status"
                            + " order by n.createdAt asc, n.esTopicNoteId asc",
                    EsTopicNote.class)
                    .setParameter("meetingId", esMeetingId)
                    .setParameter("status", TopicNoteStatus.OPEN)
                    .getResultList();
        }
    }

    public Optional<EsTopicNote> findOpenByMeetingAgendaItemId(Long esMeetingAgendaItemId) {
        if (esMeetingAgendaItemId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicNote n where n.esMeetingAgendaItemId = :agendaItemId"
                            + " and n.status = :status order by n.createdAt desc, n.esTopicNoteId desc",
                    EsTopicNote.class)
                    .setParameter("agendaItemId", esMeetingAgendaItemId)
                    .setParameter("status", TopicNoteStatus.OPEN)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }
}