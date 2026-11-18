package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.dao.EsGuideBuildDao;
import org.airahub.interophub.dao.EsGuideDocumentDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsGuideBuild;
import org.airahub.interophub.model.EsGuideDocument;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.GuideRows.BuildRow;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.TopicRow;
import org.hibernate.Session;
import org.hibernate.Transaction;

final class HibernateGuideBuildStore implements GuideBuildService.Store {
    /** Same "live open thread" rule as {@code EsGuideCommentDao.sectionCounts}. */
    private static final String OPEN_COMMENT = "c.parentCommentId is null and c.deletedAt is null"
            + " and c.withdrawnAt is null and c.state = 'OPEN'";

    private final EsGuideDocumentDao documentDao = new EsGuideDocumentDao();
    private final EsGuideBuildDao buildDao = new EsGuideBuildDao();
    private final EsTopicDao topicDao = new EsTopicDao();
    private final UserDao userDao = new UserDao();
    private final TopicSpaceAccessService accessService = new TopicSpaceAccessService();

    @Override
    public DocumentRow documentByKey(String key) {
        try (Session session = open()) {
            return documentDao.findByKey(session, key).map(HibernateGuideHostingStore::toDocument).orElse(null);
        }
    }

    @Override
    public TopicRow topicById(long id) {
        return topicDao.findById(id).map(HibernateGuideHostingStore::toTopic).orElse(null);
    }

    @Override
    public boolean canEdit(User user, TopicRow topic) {
        EsTopic entity = topic == null ? null : topicDao.findById(topic.id).orElse(null);
        return accessService.canEditTopic(user, entity);
    }

    @Override
    public void save(BuildRow row) {
        Transaction transaction = null;
        try (Session session = open()) {
            transaction = session.beginTransaction();
            persist(session, row);
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

    /** Persists inside the caller's transaction. Used by the hosted upload too. */
    static void persist(Session session, BuildRow row) {
        EsGuideBuild entity = new EsGuideBuild();
        entity.setGuideDocumentId(row.documentId);
        entity.setGuideHostedVersionId(row.hostedVersionId);
        entity.setSource(row.source);
        entity.setLabel(row.label);
        entity.setManifestSchema(row.manifestSchema);
        entity.setProcessVersion(row.processVersion);
        entity.setDocumentVersion(row.documentVersion);
        entity.setDocumentStatus(row.documentStatus);
        entity.setBuiltAt(row.builtAt);
        entity.setSourceCommit(row.sourceCommit);
        entity.setWarningCount(row.warningCount);
        entity.setManifestJson(row.manifestJson);
        entity.setManifestError(row.manifestError);
        entity.setReceivedByUserId(row.receivedByUserId);
        entity.setReceivedAt(row.receivedAt == null ? ApiTime.now() : row.receivedAt);
        session.persist(entity);
        session.flush();
        row.id = entity.getGuideBuildId();
        row.receivedAt = entity.getReceivedAt();
    }

    @Override
    public List<BuildRow> builds(long documentId) {
        try (Session session = open()) {
            List<BuildRow> rows = buildDao.findByDocumentId(session, documentId).stream()
                    .map(HibernateGuideBuildStore::toRow).toList();
            List<Long> ids = rows.stream().map(row -> row.receivedByUserId).distinct().toList();
            Map<Long, String> names = new HashMap<>();
            for (User user : userDao.findByIds(ids)) {
                names.put(user.getUserId(), user.getFullName() == null ? "" : user.getFullName());
            }
            for (BuildRow row : rows) {
                row.receivedByName = names.getOrDefault(row.receivedByUserId, "");
            }
            return rows;
        }
    }

    @Override
    public GuideBuildService.StatusInputs statusInputs() {
        GuideBuildService.StatusInputs inputs = new GuideBuildService.StatusInputs();
        try (Session session = open()) {
            List<EsGuideDocument> documents = documentDao.findAllOrdered(session);
            List<Long> topicIds = new ArrayList<>();
            for (EsGuideDocument document : documents) {
                DocumentRow row = HibernateGuideHostingStore.toDocument(document);
                inputs.documents.add(row);
                inputs.documentKeys.put(row.id, row.documentKey);
                if (!topicIds.contains(row.topicId)) {
                    topicIds.add(row.topicId);
                }
            }
            if (!topicIds.isEmpty()) {
                for (EsTopic topic : session.createQuery("from EsTopic t where t.esTopicId in :ids", EsTopic.class)
                        .setParameter("ids", topicIds).getResultList()) {
                    inputs.topics.put(topic.getEsTopicId(), HibernateGuideHostingStore.toTopic(topic));
                }
            }
            for (EsGuideBuild build : buildDao.findLatestPerDocument(session)) {
                inputs.latestBuilds.put(build.getGuideDocumentId(), toRow(build));
            }
            counts(session, "select c.guideDocumentId, count(c) from EsGuideComment c where " + OPEN_COMMENT
                    + " group by c.guideDocumentId", inputs.openComments);
            counts(session, "select f.guideDocumentId, count(f) from EsGuideReviewFlag f where f.clearedAt is null"
                    + " group by f.guideDocumentId", inputs.openFlags);
            for (Object[] row : session.createQuery("select v.guideDocumentId, max(v.uploadedAt)"
                    + " from EsGuideHostedVersion v group by v.guideDocumentId", Object[].class).getResultList()) {
                if (row[0] instanceof Long id && row[1] instanceof LocalDateTime at) {
                    inputs.lastHostedUploads.put(id, at);
                }
            }
            guideUrls(session, inputs);
        }
        return inputs;
    }

    /**
     * Where an admin can open each guide: the first registered URL of an
     * external guide, or the hosted viewer (context-relative) of a hub guide.
     * A hub guide links to its {@code latest} label when that exists, else to
     * the label uploaded most recently.
     */
    private static void guideUrls(Session session, GuideBuildService.StatusInputs inputs) {
        Map<Long, String> firstUrl = new HashMap<>();
        for (Object[] row : session.createQuery("select u.guideDocumentId, u.url from EsGuideDocumentUrl u"
                + " order by u.guideDocumentId, u.sortOrder asc, u.guideDocumentUrlId asc", Object[].class)
                .getResultList()) {
            if (row[0] instanceof Long id && row[1] instanceof String url) {
                firstUrl.putIfAbsent(id, url);
            }
        }
        Map<Long, String> hubLabel = new HashMap<>();
        for (Object[] row : session.createQuery("select v.guideDocumentId, v.label from EsGuideHostedVersion v"
                + " order by v.uploadedAt desc, v.guideHostedVersionId desc", Object[].class).getResultList()) {
            if (row[0] instanceof Long id && row[1] instanceof String label) {
                if ("latest".equals(label)) {
                    hubLabel.put(id, label);
                } else {
                    hubLabel.putIfAbsent(id, label);
                }
            }
        }
        for (DocumentRow document : inputs.documents) {
            if (GuideHtmlRules.HUB.equals(document.hosting)) {
                String label = hubLabel.get(document.id);
                if (label != null) {
                    inputs.guideUrls.put(document.id, "/guide/view/" + document.documentKey + "/" + label + "/");
                }
            } else if (firstUrl.containsKey(document.id)) {
                inputs.guideUrls.put(document.id, firstUrl.get(document.id));
            }
        }
    }

    private static void counts(Session session, String query, Map<Long, Long> into) {
        for (Object[] row : session.createQuery(query, Object[].class).getResultList()) {
            if (row[0] instanceof Long id && row[1] instanceof Number count) {
                into.put(id, count.longValue());
            }
        }
    }

    private static BuildRow toRow(EsGuideBuild entity) {
        BuildRow row = new BuildRow();
        row.id = entity.getGuideBuildId();
        row.documentId = entity.getGuideDocumentId();
        row.hostedVersionId = entity.getGuideHostedVersionId();
        row.source = entity.getSource();
        row.label = entity.getLabel();
        row.manifestSchema = entity.getManifestSchema();
        row.processVersion = entity.getProcessVersion();
        row.documentVersion = entity.getDocumentVersion();
        row.documentStatus = entity.getDocumentStatus();
        row.builtAt = entity.getBuiltAt();
        row.sourceCommit = entity.getSourceCommit();
        row.warningCount = entity.getWarningCount();
        row.manifestJson = entity.getManifestJson();
        row.manifestError = entity.getManifestError();
        row.receivedByUserId = entity.getReceivedByUserId() == null ? 0L : entity.getReceivedByUserId();
        row.receivedAt = entity.getReceivedAt();
        return row;
    }

    private static Session open() {
        return HibernateUtil.getSessionFactory().openSession();
    }
}
