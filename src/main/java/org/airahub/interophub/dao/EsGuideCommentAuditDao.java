package org.airahub.interophub.dao;

import org.airahub.interophub.model.EsGuideCommentAudit;
import org.hibernate.Session;

public class EsGuideCommentAuditDao extends GenericDao<EsGuideCommentAudit, Long> {
    public EsGuideCommentAuditDao() {
        super(EsGuideCommentAudit.class);
    }

    public void save(Session session, EsGuideCommentAudit audit) {
        session.persist(audit);
    }
}
