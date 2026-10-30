package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.model.EsGuideUrlPrefix;
import org.hibernate.Session;

public class EsGuideUrlPrefixDao extends GenericDao<EsGuideUrlPrefix, Long> {
    public EsGuideUrlPrefixDao() {
        super(EsGuideUrlPrefix.class);
    }

    public List<EsGuideUrlPrefix> findAllOrdered(Session session) {
        return session.createQuery(
                "from EsGuideUrlPrefix p order by p.enabled desc, p.urlPrefix asc", EsGuideUrlPrefix.class)
                .getResultList();
    }

    public List<EsGuideUrlPrefix> findEnabled(Session session) {
        return session.createQuery(
                "from EsGuideUrlPrefix p where p.enabled = true order by p.urlPrefix asc", EsGuideUrlPrefix.class)
                .getResultList();
    }

    public Optional<EsGuideUrlPrefix> findByPrefix(Session session, String prefix) {
        return session.createQuery(
                "from EsGuideUrlPrefix p where p.urlPrefix = :prefix", EsGuideUrlPrefix.class)
                .setParameter("prefix", prefix)
                .setMaxResults(1)
                .uniqueResultOptional();
    }
}
