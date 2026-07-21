package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicBoardStage;

public class EsTopicBoardStageDao extends GenericDao<EsTopicBoardStage, Long> {

    public EsTopicBoardStageDao() {
        super(EsTopicBoardStage.class);
    }

    public List<EsTopicBoardStage> findByBoardDefinitionId(Long boardDefinitionId) {
        if (boardDefinitionId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicBoardStage s"
                            + " where s.esTopicBoardDefinitionId = :boardId"
                            + " order by s.displayOrder asc, s.esTopicBoardStageId asc",
                    EsTopicBoardStage.class)
                    .setParameter("boardId", boardDefinitionId)
                    .getResultList();
        }
    }
}
