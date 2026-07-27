package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsMeetingStatusHistory;

public class EsMeetingStatusHistoryDao extends GenericDao<EsMeetingStatusHistory, Long> {

    public EsMeetingStatusHistoryDao() {
        super(EsMeetingStatusHistory.class);
    }

    public List<EsMeetingStatusHistory> findByMeetingIdOrdered(Long esMeetingId) {
        if (esMeetingId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingStatusHistory h where h.esMeetingId = :meetingId"
                            + " order by h.changedAt asc, h.esMeetingStatusHistoryId asc",
                    EsMeetingStatusHistory.class)
                    .setParameter("meetingId", esMeetingId)
                    .getResultList();
        }
    }
}