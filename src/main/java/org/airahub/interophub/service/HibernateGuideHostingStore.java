package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.dao.EsGuideDocumentDao;
import org.airahub.interophub.dao.EsGuideHostedVersionDao;
import org.airahub.interophub.dao.EsTopicChangeDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsGuideDocument;
import org.airahub.interophub.model.EsGuideHostedVersion;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicChange;
import org.airahub.interophub.model.StoredFile;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.HostedVersion;
import org.airahub.interophub.service.GuideRows.TopicRow;
import org.hibernate.Session;
import org.hibernate.Transaction;

final class HibernateGuideHostingStore implements GuideHostingService.Store {
    private final EsGuideDocumentDao documentDao = new EsGuideDocumentDao();
    private final EsGuideHostedVersionDao versionDao = new EsGuideHostedVersionDao();
    private final EsTopicDao topicDao = new EsTopicDao();
    private final EsTopicChangeDao changeDao = new EsTopicChangeDao();
    private final UserDao userDao = new UserDao();
    private final TopicSpaceAccessService accessService = new TopicSpaceAccessService();
    private final PublicUrlService publicUrlService = new PublicUrlService();

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
    public boolean canView(User user, TopicRow topic) {
        return accessService.canViewTopic(user, topicEntity(topic));
    }

    @Override
    public boolean canEdit(User user, TopicRow topic) {
        return accessService.canEditTopic(user, topicEntity(topic));
    }

    @Override
    public String displayName(long userId) {
        User user = userDao.findById(userId).orElse(null);
        if (user == null) {
            return "";
        }
        String name = user.getFullName();
        return name == null ? "" : name;
    }

    @Override
    public String publicBaseUrl() {
        String base = publicUrlService.resolveExternalUrl("/");
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }

    @Override
    public List<HostedVersion> versions(long documentId) {
        try (Session session = open()) {
            List<EsGuideHostedVersion> rows = versionDao.findByDocumentId(session, documentId);
            Map<Long, String> names = names(rows);
            Map<Long, Long> sizes = new HashMap<>();
            for (EsGuideHostedVersion row : rows) {
                if (sizes.containsKey(row.getStoredFileId())) {
                    continue;
                }
                StoredFile file = session.get(StoredFile.class, row.getStoredFileId());
                sizes.put(row.getStoredFileId(), file == null ? 0L : file.getSizeBytes());
            }
            return rows.stream().map(row -> toVersion(row, names, sizes)).toList();
        }
    }

    @Override
    public void save(HostedVersion version, String changesJson, long topicId, long userId, Long tokenId, String source) {
        Transaction transaction = null;
        try (Session session = open()) {
            transaction = session.beginTransaction();
            EsGuideHostedVersion entity = new EsGuideHostedVersion();
            entity.setGuideDocumentId(version.documentId);
            entity.setLabel(version.label);
            entity.setStoredFileId(version.storedFileId);
            entity.setUploadedByUserId(version.uploadedByUserId);
            entity.setUploadedAt(version.uploadedAt == null ? LocalDateTime.now() : version.uploadedAt);
            session.persist(entity);
            session.flush();
            version.id = entity.getGuideHostedVersionId();
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

    private Map<Long, String> names(List<EsGuideHostedVersion> rows) {
        List<Long> ids = rows.stream().map(EsGuideHostedVersion::getUploadedByUserId).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        for (User user : userDao.findByIds(ids)) {
            String name = user.getFullName();
            names.put(user.getUserId(), name == null ? "" : name);
        }
        return names;
    }

    private EsTopic topicEntity(TopicRow topic) {
        return topic == null ? null : topicDao.findById(topic.id).orElse(null);
    }

    private static DocumentRow toDocument(EsGuideDocument entity) {
        DocumentRow row = new DocumentRow();
        row.id = entity.getGuideDocumentId();
        row.documentKey = entity.getDocumentKey();
        row.topicId = entity.getEsTopicId();
        row.title = entity.getTitle();
        row.active = Boolean.TRUE.equals(entity.getActive());
        row.hosting = entity.getHosting() == null || entity.getHosting().isBlank()
                ? GuideHtmlRules.EXTERNAL : entity.getHosting();
        return row;
    }

    private static TopicRow toTopic(EsTopic topic) {
        TopicRow row = new TopicRow();
        row.id = topic.getEsTopicId();
        row.code = topic.getTopicCode();
        row.name = topic.getTopicName();
        return row;
    }

    private static HostedVersion toVersion(EsGuideHostedVersion entity, Map<Long, String> names, Map<Long, Long> sizes) {
        HostedVersion version = new HostedVersion();
        version.id = entity.getGuideHostedVersionId();
        version.documentId = entity.getGuideDocumentId();
        version.label = entity.getLabel();
        version.storedFileId = entity.getStoredFileId();
        version.sizeBytes = sizes.getOrDefault(entity.getStoredFileId(), 0L);
        version.uploadedByUserId = entity.getUploadedByUserId();
        version.uploadedByName = names.getOrDefault(entity.getUploadedByUserId(), "");
        version.uploadedAt = entity.getUploadedAt();
        return version;
    }

    private static Session open() {
        return HibernateUtil.getSessionFactory().openSession();
    }
}
