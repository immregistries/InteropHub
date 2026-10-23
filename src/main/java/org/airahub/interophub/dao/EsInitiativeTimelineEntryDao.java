package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsInitiativeTimelineEntry;

public class EsInitiativeTimelineEntryDao extends GenericDao<EsInitiativeTimelineEntry, Long> {

    public EsInitiativeTimelineEntryDao() {
        super(EsInitiativeTimelineEntry.class);
    }

    public List<EsInitiativeTimelineEntry> findBySubject(EsInitiativeTimelineEntry.SubjectType subjectType,
            Long subjectId) {
        if (subjectType == null || subjectId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsInitiativeTimelineEntry e where e.subjectType = :type and e.subjectId = :subjectId"
                            + " order by e.createdAt, e.esInitiativeTimelineEntryId",
                    EsInitiativeTimelineEntry.class)
                    .setParameter("type", subjectType)
                    .setParameter("subjectId", subjectId)
                    .getResultList();
        }
    }

    public List<EsInitiativeTimelineEntry> findByInitiativeAndSubjectType(Long esInitiativeId,
            EsInitiativeTimelineEntry.SubjectType subjectType) {
        if (esInitiativeId == null || subjectType == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsInitiativeTimelineEntry e where e.esInitiativeId = :initiativeId"
                            + " and e.subjectType = :type"
                            + " order by e.createdAt, e.esInitiativeTimelineEntryId",
                    EsInitiativeTimelineEntry.class)
                    .setParameter("initiativeId", esInitiativeId)
                    .setParameter("type", subjectType)
                    .getResultList();
        }
    }
}
