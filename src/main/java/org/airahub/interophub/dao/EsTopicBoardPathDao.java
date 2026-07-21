package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicBoardPath;

public class EsTopicBoardPathDao extends GenericDao<EsTopicBoardPath, Long> {

    public EsTopicBoardPathDao() {
        super(EsTopicBoardPath.class);
    }

    public List<EsTopicBoardPath> findByBoardDefinitionId(Long boardDefinitionId) {
        if (boardDefinitionId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicBoardPath p"
                            + " where p.esTopicBoardDefinitionId = :boardId"
                            + " order by p.displayOrder asc, p.esTopicBoardPathId asc",
                    EsTopicBoardPath.class)
                    .setParameter("boardId", boardDefinitionId)
                    .getResultList();
        }
    }
}
