package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.dao.AppRedirectAllowlistDao;
import org.airahub.interophub.dao.AppRegistryDao;
import org.airahub.interophub.dao.EsGuideDispositionDao;
import org.airahub.interophub.dao.EsGuideDocumentDao;
import org.airahub.interophub.dao.EsGuideDocumentUrlDao;
import org.airahub.interophub.dao.EsGuideUrlPrefixDao;
import org.airahub.interophub.dao.EsTopicChangeDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.AppRedirectAllowlist;
import org.airahub.interophub.model.AppRegistry;
import org.airahub.interophub.model.EsGuideDisposition;
import org.airahub.interophub.model.EsGuideDocument;
import org.airahub.interophub.model.EsGuideDocumentUrl;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicChange;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.GuideRows.AllowInsert;
import org.airahub.interophub.service.GuideRows.AllowRow;
import org.airahub.interophub.service.GuideRows.AllowToggle;
import org.airahub.interophub.service.GuideRows.Disposition;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.Person;
import org.airahub.interophub.service.GuideRows.RegistrationPlan;
import org.airahub.interophub.service.GuideRows.TopicRow;
import org.airahub.interophub.service.GuideRows.UrlBinding;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.exception.ConstraintViolationException;

final class HibernateGuideRegistrationStore implements GuideRegistrationService.Store {
    private final EsGuideDocumentDao documentDao = new EsGuideDocumentDao();
    private final EsGuideDocumentUrlDao urlDao = new EsGuideDocumentUrlDao();
    private final EsGuideUrlPrefixDao prefixDao = new EsGuideUrlPrefixDao();
    private final EsGuideDispositionDao dispositionDao = new EsGuideDispositionDao();
    private final EsTopicDao topicDao = new EsTopicDao();
    private final EsTopicSpaceDao topicSpaceDao = new EsTopicSpaceDao();
    private final EsTopicChangeDao changeDao = new EsTopicChangeDao();
    private final AppRegistryDao appRegistryDao = new AppRegistryDao();
    private final AppRedirectAllowlistDao allowlistDao = new AppRedirectAllowlistDao();
    private final TopicSpaceAccessService accessService = new TopicSpaceAccessService();
    private final AuthFlowService authFlowService = new AuthFlowService();
    private final PublicUrlService publicUrlService = new PublicUrlService();

    @Override
    public TopicRow topicByCode(String code) {
        return topicDao.findByTopicCode(code).map(this::toTopic).orElse(null);
    }

    @Override
    public TopicRow topicById(long id) {
        return topicDao.findById(id).map(this::toTopic).orElse(null);
    }

    @Override
    public DocumentRow documentByKey(String key) {
        try (Session session = open()) {
            return documentDao.findByKey(session, key).map(entity -> toDocument(session, entity)).orElse(null);
        }
    }

    @Override
    public List<DocumentRow> documentsByTopic(long topicId) {
        try (Session session = open()) {
            return documentDao.findByTopicId(session, topicId).stream()
                    .map(entity -> toDocument(session, entity))
                    .toList();
        }
    }

    @Override
    public Long documentIdForUrl(String url) {
        try (Session session = open()) {
            return urlDao.findByUrl(session, url).map(EsGuideDocumentUrl::getGuideDocumentId).orElse(null);
        }
    }

    @Override
    public List<String> enabledPrefixes() {
        try (Session session = open()) {
            return prefixDao.findEnabled(session).stream().map(prefix -> prefix.getUrlPrefix()).toList();
        }
    }

    @Override
    public List<AllowRow> allowlist() {
        AppRegistry app = topicReferenceApp();
        if (app == null) {
            return List.of();
        }
        return allowlistDao.findByAppId(app.getAppId()).stream().map(HibernateGuideRegistrationStore::toAllow).toList();
    }

    @Override
    public boolean canEdit(User user, TopicRow topic) {
        return accessService.canEditTopic(user, topicEntity(topic));
    }

    @Override
    public boolean canView(User user, TopicRow topic) {
        return accessService.canViewTopic(user, topicEntity(topic));
    }

    @Override
    public boolean globalAdmin(User user) {
        return authFlowService.isAdminUser(user);
    }

    @Override
    public boolean localhostMode() {
        return publicUrlService.isLocalhostMode();
    }

    @Override
    public Person personOf(User user) {
        return GuidePeople.of(user);
    }

    @Override
    public List<Disposition> dispositions() {
        try (Session session = open()) {
            return dispositionDao.findAllOrdered(session).stream()
                    .map(HibernateGuideRegistrationStore::toDisposition)
                    .toList();
        }
    }

    @Override
    public String topicUrl(long topicId) {
        return publicUrlService.resolveExternalUrl("/es/topic/" + topicId);
    }

    @Override
    public void persist(RegistrationPlan plan) {
        Transaction transaction = null;
        try (Session session = open()) {
            transaction = session.beginTransaction();
            EsGuideDocument document = plan.creating ? new EsGuideDocument() : session.get(EsGuideDocument.class, plan.document.id);
            if (document == null) {
                throw GuideApiException.notFound("That guide was not found.");
            }
            document.setDocumentKey(plan.document.documentKey);
            document.setEsTopicId(plan.document.topicId);
            document.setTitle(plan.document.title);
            document.setRepositoryUrl(plan.document.repositoryUrl);
            document.setActive(plan.document.active);
            document.setCreatedByUserId(plan.document.createdByUserId);
            document.setCreatedAt(plan.document.createdAt);
            document.setUpdatedAt(plan.document.updatedAt == null ? LocalDateTime.now() : plan.document.updatedAt);
            if (plan.creating) {
                session.persist(document);
                session.flush();
                plan.document.id = document.getGuideDocumentId();
            }
            AppRegistry app = topicReferenceApp();
            if (app == null) {
                throw new IllegalStateException("topic-reference app is not registered.");
            }
            for (AllowInsert insert : plan.inserts) {
                AppRedirectAllowlist allow = new AppRedirectAllowlist();
                allow.setAppId(app.getAppId());
                allow.setBaseUrl(insert.url);
                allow.setEnabled(insert.enabled);
                allow.setCreatedAt(LocalDateTime.now());
                session.persist(allow);
                session.flush();
                for (UrlBinding binding : plan.urls) {
                    if (binding.insertNew && insert.url.equals(binding.url)) {
                        binding.allowId = allow.getAllowId();
                    }
                }
            }
            for (AllowToggle toggle : plan.toggles) {
                AppRedirectAllowlist allow = session.get(AppRedirectAllowlist.class, toggle.allowId);
                if (allow != null) {
                    allow.setEnabled(toggle.enabled);
                }
            }
            if (!plan.creating) {
                urlDao.deleteByDocumentId(session, document.getGuideDocumentId());
                session.flush();
            }
            for (UrlBinding binding : plan.urls) {
                EsGuideDocumentUrl url = new EsGuideDocumentUrl();
                url.setGuideDocumentId(document.getGuideDocumentId());
                url.setUrl(binding.url);
                url.setLabel(binding.label);
                url.setSortOrder(binding.sortOrder);
                url.setAllowId(binding.allowId);
                url.setOwnsAllowlist(binding.ownsAllowlist);
                session.persist(url);
            }
            if (plan.changesJson != null) {
                EsTopicChange change = new EsTopicChange();
                change.setEsTopicId(plan.topicId);
                change.setChangedByUserId(plan.userId);
                change.setChangedAt(LocalDateTime.now());
                change.setSource(plan.source);
                change.setChangeType("GUIDE");
                change.setChangesJson(plan.changesJson);
                change.setAppUserTokenId(plan.tokenId);
                changeDao.save(session, change);
            }
            transaction.commit();
        } catch (ConstraintViolationException ex) {
            rollback(transaction);
            throw GuideApiException.duplicate("urls", "That URL or document key is already registered.");
        } catch (RuntimeException ex) {
            if (causedByConstraint(ex)) {
                rollback(transaction);
                throw GuideApiException.duplicate("urls", "That URL or document key is already registered.");
            }
            rollback(transaction);
            throw ex;
        }
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
        for (EsGuideDocumentUrl url : urlDao.findByDocumentId(session, entity.getGuideDocumentId())) {
            UrlBinding binding = new UrlBinding(url.getUrl(), url.getLabel());
            binding.sortOrder = url.getSortOrder() == null ? 0 : url.getSortOrder();
            binding.allowId = url.getAllowId();
            binding.ownsAllowlist = Boolean.TRUE.equals(url.getOwnsAllowlist());
            row.urls.add(binding);
        }
        return row;
    }

    private EsTopic topicEntity(TopicRow topic) {
        return topic == null ? null : topicDao.findById(topic.id).orElse(null);
    }

    private AppRegistry topicReferenceApp() {
        return appRegistryDao.findByAppCode(GuideApps.TOPIC_REFERENCE).orElse(null);
    }

    private static AllowRow toAllow(AppRedirectAllowlist entity) {
        AllowRow row = new AllowRow();
        row.allowId = entity.getAllowId();
        row.baseUrl = entity.getBaseUrl();
        row.enabled = Boolean.TRUE.equals(entity.getEnabled());
        return row;
    }

    private static Disposition toDisposition(EsGuideDisposition entity) {
        return new Disposition(entity.getDispositionCode(), entity.getLabel());
    }

    private static boolean causedByConstraint(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ConstraintViolationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
