package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.dao.EsGuideDocumentDao;
import org.airahub.interophub.dao.EsGuideDocumentUrlDao;
import org.airahub.interophub.dao.EsGuideUrlPrefixDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.model.EsGuideDocument;
import org.airahub.interophub.model.EsGuideUrlPrefix;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.User;
import org.hibernate.Session;
import org.hibernate.Transaction;

/** Global-admin upkeep for approved guide URL prefixes and the registration list. */
public class GuideAdminService {
    private final EsGuideUrlPrefixDao prefixDao = new EsGuideUrlPrefixDao();
    private final EsGuideDocumentDao documentDao = new EsGuideDocumentDao();
    private final EsGuideDocumentUrlDao urlDao = new EsGuideDocumentUrlDao();
    private final EsTopicDao topicDao = new EsTopicDao();

    public List<PrefixItem> prefixes() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return prefixDao.findAllOrdered(session).stream()
                    .map(prefix -> new PrefixItem(prefix.getGuideUrlPrefixId(), prefix.getUrlPrefix(),
                            Boolean.TRUE.equals(prefix.getEnabled())))
                    .toList();
        }
    }

    public void addPrefix(User user, String raw) {
        String prefix = GuideUrlRules.prefix(raw);
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            EsGuideUrlPrefix existing = prefixDao.findByPrefix(session, prefix).orElse(null);
            if (existing == null) {
                EsGuideUrlPrefix created = new EsGuideUrlPrefix();
                created.setUrlPrefix(prefix);
                created.setEnabled(true);
                created.setCreatedByUserId(user.getUserId());
                created.setCreatedAt(LocalDateTime.now());
                session.persist(created);
            } else if (Boolean.TRUE.equals(existing.getEnabled())) {
                throw GuideApiException.invalidField("urlPrefix", "That prefix is already approved.");
            } else {
                existing.setEnabled(true);
                existing.setDisabledAt(null);
                existing.setDisabledByUserId(null);
            }
            transaction.commit();
        } catch (GuideApiException ex) {
            rollback(transaction);
            throw ex;
        } catch (RuntimeException ex) {
            rollback(transaction);
            throw ex;
        }
    }

    public void disablePrefix(User user, long id) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            EsGuideUrlPrefix prefix = session.get(EsGuideUrlPrefix.class, id);
            if (prefix == null) {
                throw GuideApiException.notFound("That prefix was not found.");
            }
            prefix.setEnabled(false);
            prefix.setDisabledAt(LocalDateTime.now());
            prefix.setDisabledByUserId(user.getUserId());
            transaction.commit();
        } catch (RuntimeException ex) {
            rollback(transaction);
            throw ex;
        }
    }

    public List<RegistrationItem> registrations() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<RegistrationItem> items = new ArrayList<>();
            for (EsGuideDocument document : documentDao.findAllOrdered(session)) {
                EsTopic topic = topicDao.findById(document.getEsTopicId()).orElse(null);
                List<String> urls = urlDao.findByDocumentId(session, document.getGuideDocumentId()).stream()
                        .map(url -> url.getLabel() + " — " + url.getUrl())
                        .toList();
                items.add(new RegistrationItem(document.getDocumentKey(), document.getTitle(),
                        topic == null ? "" : topic.getTopicCode(),
                        topic == null ? "" : topic.getTopicName(),
                        Boolean.TRUE.equals(document.getActive()), urls));
            }
            return items;
        }
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

    public record PrefixItem(long id, String prefix, boolean enabled) {
    }

    public record RegistrationItem(String documentKey, String title, String topicCode, String topicName,
            boolean active, List<String> urls) {
    }
}
