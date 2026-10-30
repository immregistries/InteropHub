package org.airahub.interophub.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.dao.EsGuideCommentAuditDao;
import org.airahub.interophub.dao.EsGuideCommentDao;
import org.airahub.interophub.dao.EsGuideDispositionDao;
import org.airahub.interophub.dao.EsGuideDocumentDao;
import org.airahub.interophub.dao.EsGuideDocumentUrlDao;
import org.airahub.interophub.dao.EsGuideReviewFlagDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsGuideComment;
import org.airahub.interophub.model.EsGuideCommentAudit;
import org.airahub.interophub.model.EsGuideDisposition;
import org.airahub.interophub.model.EsGuideDocument;
import org.airahub.interophub.model.EsGuideReviewFlag;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.GuideRows.AuditRow;
import org.airahub.interophub.service.GuideRows.CommentRow;
import org.airahub.interophub.service.GuideRows.Disposition;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.FlagRow;
import org.airahub.interophub.service.GuideRows.Person;
import org.airahub.interophub.service.GuideRows.TopicRow;
import org.airahub.interophub.service.GuideRows.UrlBinding;
import org.hibernate.Session;
import org.hibernate.Transaction;

final class HibernateGuideCommentStore implements GuideCommentService.Store {
    private final EsGuideDocumentDao documentDao = new EsGuideDocumentDao();
    private final EsGuideDocumentUrlDao urlDao = new EsGuideDocumentUrlDao();
    private final EsGuideCommentDao commentDao = new EsGuideCommentDao();
    private final EsGuideCommentAuditDao auditDao = new EsGuideCommentAuditDao();
    private final EsGuideDispositionDao dispositionDao = new EsGuideDispositionDao();
    private final EsGuideReviewFlagDao flagDao = new EsGuideReviewFlagDao();
    private final EsTopicDao topicDao = new EsTopicDao();
    private final EsTopicSpaceDao topicSpaceDao = new EsTopicSpaceDao();
    private final UserDao userDao = new UserDao();
    private final TopicSpaceAccessService accessService = new TopicSpaceAccessService();
    private final PublicUrlService publicUrlService = new PublicUrlService();

    @Override
    public DocumentRow documentByKey(String key) {
        try (Session session = open()) {
            return documentDao.findByKey(session, key).map(entity -> toDocument(session, entity)).orElse(null);
        }
    }

    @Override
    public DocumentRow documentById(long id) {
        try (Session session = open()) {
            EsGuideDocument entity = session.get(EsGuideDocument.class, id);
            return entity == null ? null : toDocument(session, entity);
        }
    }

    @Override
    public TopicRow topic(long topicId) {
        EsTopic topic = topicDao.findById(topicId).orElse(null);
        return topic == null ? null : toTopic(topic);
    }

    @Override
    public boolean canView(User user, TopicRow topic) {
        EsTopic entity = topicDao.findById(topic.id).orElse(null);
        return accessService.canViewTopic(user, entity);
    }

    @Override
    public boolean canEdit(User user, TopicRow topic) {
        EsTopic entity = topicDao.findById(topic.id).orElse(null);
        return accessService.canEditTopic(user, entity);
    }

    @Override
    public Person personOf(User user) {
        return GuidePeople.of(user);
    }

    @Override
    public Map<Long, Person> people(Collection<Long> userIds) {
        Map<Long, Person> people = new LinkedHashMap<>();
        for (Long userId : userIds) {
            if (userId == null || people.containsKey(userId)) {
                continue;
            }
            userDao.findById(userId).map(GuidePeople::of).ifPresent(person -> people.put(userId, person));
        }
        return people;
    }

    @Override
    public List<Disposition> dispositions() {
        try (Session session = open()) {
            return dispositionDao.findAllOrdered(session).stream()
                    .map(HibernateGuideCommentStore::toDisposition)
                    .toList();
        }
    }

    @Override
    public List<CommentRow> comments(long documentId) {
        try (Session session = open()) {
            return commentDao.findByDocumentId(session, documentId).stream().map(HibernateGuideCommentStore::toComment).toList();
        }
    }

    @Override
    public List<FlagRow> activeFlags(long documentId) {
        try (Session session = open()) {
            return flagDao.findActiveByDocumentId(session, documentId).stream()
                    .map(HibernateGuideCommentStore::toFlag)
                    .toList();
        }
    }

    @Override
    public CommentRow comment(long id) {
        try (Session session = open()) {
            return commentDao.findById(session, id).map(HibernateGuideCommentStore::toComment).orElse(null);
        }
    }

    @Override
    public void insertComment(CommentRow row, CommentRow parent, List<AuditRow> audits) {
        Transaction transaction = null;
        try (Session session = open()) {
            transaction = session.beginTransaction();
            EsGuideComment entity = new EsGuideComment();
            entity.setGuideDocumentId(row.documentId);
            entity.setParentCommentId(row.parentId);
            entity.setUserId(row.userId);
            entity.setCreatedAt(row.createdAt);
            apply(entity, row);
            session.persist(entity);
            session.flush();
            row.id = entity.getGuideCommentId();
            touchParent(session, parent);
            writeAudits(session, row.id, audits);
            transaction.commit();
        } catch (RuntimeException ex) {
            rollback(transaction);
            throw ex;
        }
    }

    @Override
    public void updateComment(CommentRow row, CommentRow parent, List<AuditRow> audits) {
        Transaction transaction = null;
        try (Session session = open()) {
            transaction = session.beginTransaction();
            EsGuideComment entity = session.get(EsGuideComment.class, row.id);
            if (entity == null) {
                throw GuideApiException.notFound("That comment was not found.");
            }
            apply(entity, row);
            touchParent(session, parent);
            writeAudits(session, row.id, audits);
            transaction.commit();
        } catch (RuntimeException ex) {
            rollback(transaction);
            throw ex;
        }
    }

    @Override
    public String topicUrl(long topicId) {
        return publicUrlService.resolveExternalUrl("/es/topic/" + topicId);
    }

    private DocumentRow toDocument(Session session, EsGuideDocument entity) {
        DocumentRow row = new DocumentRow();
        row.id = entity.getGuideDocumentId();
        row.documentKey = entity.getDocumentKey();
        row.topicId = entity.getEsTopicId();
        row.title = entity.getTitle();
        row.repositoryUrl = entity.getRepositoryUrl();
        row.active = Boolean.TRUE.equals(entity.getActive());
        row.createdByUserId = entity.getCreatedByUserId();
        row.createdAt = entity.getCreatedAt();
        row.updatedAt = entity.getUpdatedAt();
        for (var url : urlDao.findByDocumentId(session, entity.getGuideDocumentId())) {
            UrlBinding binding = new UrlBinding(url.getUrl(), url.getLabel());
            binding.sortOrder = url.getSortOrder() == null ? 0 : url.getSortOrder();
            binding.allowId = url.getAllowId();
            binding.ownsAllowlist = Boolean.TRUE.equals(url.getOwnsAllowlist());
            row.urls.add(binding);
        }
        return row;
    }

    private TopicRow toTopic(EsTopic topic) {
        TopicRow row = new TopicRow();
        row.id = topic.getEsTopicId();
        row.code = topic.getTopicCode();
        row.name = topic.getTopicName();
        EsTopicSpace space = topic.getEsTopicSpaceId() == null
                ? null
                : topicSpaceDao.findById(topic.getEsTopicSpaceId()).orElse(null);
        row.visibility = space == null || space.getVisibility() == null ? "PUBLIC" : space.getVisibility().name();
        return row;
    }

    private static void apply(EsGuideComment entity, CommentRow row) {
        entity.setAnchorId(row.anchor);
        entity.setBlockIndex(row.blockIndex);
        entity.setBlockSnippet(row.snippet);
        entity.setCommentText(row.text);
        entity.setState(row.state);
        entity.setSourceNote(row.sourceNote);
        entity.setEditedAt(row.editedAt);
        entity.setUpdatedAt(row.updatedAt);
        entity.setWithdrawnAt(row.withdrawnAt);
    }

    private static void touchParent(Session session, CommentRow parent) {
        if (parent == null || parent.id == null) {
            return;
        }
        EsGuideComment managed = session.get(EsGuideComment.class, parent.id);
        if (managed != null) {
            managed.setUpdatedAt(parent.updatedAt);
        }
    }

    private void writeAudits(Session session, Long commentId, List<AuditRow> audits) {
        for (AuditRow audit : audits) {
            EsGuideCommentAudit entity = new EsGuideCommentAudit();
            entity.setGuideCommentId(commentId);
            entity.setChangedByUserId(audit.userId);
            entity.setChangedAt(audit.at);
            entity.setFieldName(audit.field);
            entity.setOldValue(audit.oldValue);
            entity.setNewValue(audit.newValue);
            entity.setAppUserTokenId(audit.tokenId);
            auditDao.save(session, entity);
        }
    }

    private static CommentRow toComment(EsGuideComment entity) {
        CommentRow row = new CommentRow();
        row.id = entity.getGuideCommentId();
        row.documentId = entity.getGuideDocumentId();
        row.parentId = entity.getParentCommentId();
        row.anchor = entity.getAnchorId();
        row.blockIndex = entity.getBlockIndex();
        row.snippet = entity.getBlockSnippet();
        row.text = entity.getCommentText();
        row.state = entity.getState();
        row.dispositionCode = entity.getDispositionCode();
        row.changeNote = entity.getChangeNote();
        row.userId = entity.getUserId();
        row.sourceNote = entity.getSourceNote();
        row.createdAt = entity.getCreatedAt();
        row.editedAt = entity.getEditedAt();
        row.updatedAt = entity.getUpdatedAt();
        row.deletedAt = entity.getDeletedAt();
        row.deletedByUserId = entity.getDeletedByUserId();
        row.withdrawnAt = entity.getWithdrawnAt();
        row.disposedAt = entity.getDisposedAt();
        row.disposedByUserId = entity.getDisposedByUserId();
        row.closedAt = entity.getClosedAt();
        row.closedByUserId = entity.getClosedByUserId();
        return row;
    }

    private static FlagRow toFlag(EsGuideReviewFlag entity) {
        FlagRow row = new FlagRow();
        row.id = entity.getReviewFlagId();
        row.anchor = entity.getAnchorId();
        row.note = entity.getNote();
        row.createdByUserId = entity.getCreatedByUserId();
        row.createdAt = entity.getCreatedAt();
        row.updatedAt = entity.getUpdatedAt();
        return row;
    }

    private static Disposition toDisposition(EsGuideDisposition entity) {
        return new Disposition(entity.getDispositionCode(), entity.getLabel());
    }

    private static Session open() {
        return HibernateUtil.getSessionFactory().openSession();
    }

    private static void rollback(Transaction transaction) {
        if (transaction != null) {
            try {
                transaction.rollback();
            } catch (RuntimeException ignored) {
                // The original failure is the one to report.
            }
        }
    }
}
