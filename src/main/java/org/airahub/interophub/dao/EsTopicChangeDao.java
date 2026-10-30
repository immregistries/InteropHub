package org.airahub.interophub.dao;

import org.airahub.interophub.model.EsTopicChange;
import org.hibernate.Session;

public class EsTopicChangeDao extends GenericDao<EsTopicChange, Long> {

    public EsTopicChangeDao() {
        super(EsTopicChange.class);
    }

    public void save(Session session, EsTopicChange change) {
        session.persist(change);
    }
}
