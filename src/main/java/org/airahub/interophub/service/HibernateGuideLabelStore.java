package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.dao.EsGuideCommentDao;
import org.airahub.interophub.dao.EsGuideDocumentDao;
import org.airahub.interophub.dao.EsTopicChangeDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.model.EsGuideLabel;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicChange;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.GuideRows.CommentRow;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.LabelRow;
import org.airahub.interophub.service.GuideRows.TopicRow;
import org.hibernate.Session;
import org.hibernate.Transaction;

final class HibernateGuideLabelStore implements GuideLabelService.Store {
    private final EsGuideDocumentDao documentDao = new EsGuideDocumentDao();
    private final EsGuideCommentDao commentDao = new EsGuideCommentDao();
    private final EsTopicDao topicDao = new EsTopicDao();
    private final EsTopicChangeDao changeDao = new EsTopicChangeDao();
    private final TopicSpaceAccessService accessService = new TopicSpaceAccessService();

    @Override
    public DocumentRow documentByKey(String key) {
        try (Session session = open()) {
            return documentDao.findByKey(session, key).map(HibernateGuideHostingStore::toDocument).orElse(null);
        }
    }

    @Override
    public TopicRow topic(long topicId) {
        return topicDao.findById(topicId).map(HibernateGuideHostingStore::toTopic).orElse(null);
    }

    @Override
    public boolean canView(User user, TopicRow topic) {
        return accessService.canViewTopic(user, entity(topic));
    }

    @Override
    public boolean canEdit(User user, TopicRow topic) {
        return accessService.canEditTopic(user, entity(topic));
    }

    @Override
    public GuideLabels.Facts facts(long documentId) {
        try (Session session = open()) {
            return HibernateGuideLabelFacts.load(session, documentId);
        }
    }

    @Override
    public List<CommentRow> comments(long documentId) {
        try (Session session = open()) {
            return commentDao.findByDocumentId(session, documentId).stream()
                    .map(HibernateGuideCommentStore::toComment).toList();
        }
    }

    @Override
    public void save(List<LabelRow> rows, String changesJson, long topicId, long userId, Long tokenId,
            String source) {
        Transaction transaction = null;
        try (Session session = open()) {
            transaction = session.beginTransaction();
            for (LabelRow row : rows) {
                EsGuideLabel entity = row.id == null ? null : session.get(EsGuideLabel.class, row.id);
                if (entity == null) {
                    entity = session.createQuery("from EsGuideLabel l where l.guideDocumentId = :id and l.label = :label",
                            EsGuideLabel.class)
                            .setParameter("id", row.documentId)
                            .setParameter("label", row.label)
                            .uniqueResultOptional().orElse(null);
                }
                boolean creating = entity == null;
                if (creating) {
                    entity = new EsGuideLabel();
                    entity.setGuideDocumentId(row.documentId);
                    entity.setLabel(row.label);
                }
                entity.setDisplayState(row.display);
                entity.setCommentMode(row.commentMode);
                entity.setCommentModeSetAt(row.commentModeSetAt);
                entity.setUpdatedByUserId(row.updatedByUserId == null ? userId : row.updatedByUserId);
                entity.setUpdatedAt(row.updatedAt == null ? ApiTime.now() : row.updatedAt);
                if (creating) {
                    session.persist(entity);
                    session.flush();
                }
                row.id = entity.getGuideLabelId();
            }
            if (changesJson != null) {
                EsTopicChange change = new EsTopicChange();
                change.setEsTopicId(topicId);
                change.setChangedByUserId(userId);
                change.setChangedAt(LocalDateTime.now());
                change.setSource(source);
                change.setChangeType("GUIDE");
                change.setChangesJson(changesJson);
                change.setAppUserTokenId(tokenId);
                changeDao.save(session, change);
            }
            transaction.commit();
        } catch (RuntimeException ex) {
            if (transaction != null) {
                try {
                    transaction.rollback();
                } catch (RuntimeException ignored) {
                    // The original failure is the one to report.
                }
            }
            throw ex;
        }
    }

    private EsTopic entity(TopicRow topic) {
        return topic == null ? null : topicDao.findById(topic.id).orElse(null);
    }

    private static Session open() {
        return HibernateUtil.getSessionFactory().openSession();
    }
}
