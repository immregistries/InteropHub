package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsMeetingParticipantCount;

public class EsMeetingParticipantCountDao extends GenericDao<EsMeetingParticipantCount, Long> {

    public EsMeetingParticipantCountDao() {
        super(EsMeetingParticipantCount.class);
    }

    public List<EsMeetingParticipantCount> findByAgendaActivityIdOrdered(Long esMeetingAgendaActivityId) {
        if (esMeetingAgendaActivityId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingParticipantCount p where p.esMeetingAgendaActivityId = :agendaActivityId"
                            + " order by p.recordedAt asc, p.esMeetingParticipantCountId asc",
                    EsMeetingParticipantCount.class)
                    .setParameter("agendaActivityId", esMeetingAgendaActivityId)
                    .getResultList();
        }
    }

    public Optional<EsMeetingParticipantCount> findLatestByAgendaActivityId(Long esMeetingAgendaActivityId) {
        if (esMeetingAgendaActivityId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingParticipantCount p where p.esMeetingAgendaActivityId = :agendaActivityId"
                            + " order by p.recordedAt desc, p.esMeetingParticipantCountId desc",
                    EsMeetingParticipantCount.class)
                    .setParameter("agendaActivityId", esMeetingAgendaActivityId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }
}