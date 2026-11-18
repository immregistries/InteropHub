package org.airahub.interophub.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.dao.EsGuideDocumentDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.UserTokenDao;
import org.airahub.interophub.model.EsGuideDocument;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.User;
import org.airahub.interophub.model.UserToken;
import org.hibernate.Session;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * {@code GET /api/token}: the calling token describes itself, so an agent's first call can confirm
 * who it acts as, what it may do, and when the token expires. Also the list of guides an account
 * can publish to, which the account page shows after a token is created.
 *
 * <p>The response never carries the user's email, the token's hash, or the token itself.
 */
public class GuideTokenInfoService {
    private final Store store;

    public GuideTokenInfoService() {
        this(new HibernateStore());
    }

    public GuideTokenInfoService(Store store) {
        this.store = store;
    }

    /** The response body for the token the bearer filter accepted. */
    public JSONObject describe(User user, Long tokenId, String kind, Set<String> scopes, LocalDateTime now) {
        TokenFacts token = tokenId == null ? null : store.token(tokenId);
        boolean canPublish = GuideScopes.satisfied(scopes, GuideScopes.TOPICS_WRITE);

        JSONObject json = new JSONObject();
        JSONObject userJson = new JSONObject();
        userJson.put("userId", user.getUserId());
        userJson.put("displayName", GuidePeople.of(user).displayName);
        json.put("user", userJson);
        json.put("kind", kind == null ? JSONObject.NULL : kind);
        json.put("label", token == null || token.label() == null ? JSONObject.NULL : token.label());
        json.put("scopes", new JSONArray(orderedScopes(scopes)));
        LocalDateTime expiresAt = token == null ? null : token.expiresAt();
        json.put("expiresAt", expiresAt == null ? JSONObject.NULL : ApiTime.format(expiresAt));
        json.put("expiresInDays", expiresAt == null ? JSONObject.NULL : expiresInDays(expiresAt, now));
        JSONArray guides = new JSONArray();
        for (GuideEntry guide : guides(user, canPublish)) {
            guides.put(guideJson(guide));
        }
        json.put("guides", guides);
        json.put("help", GuideHelp.url(GuideHelp.IF_YOU_ARE_AN_AGENT));
        return json;
    }

    /**
     * Every active registered guide on a topic the user may edit ({@code canEditTopic}), ordered by
     * document key. Empty for a reader.
     */
    public List<GuideEntry> guides(User user, boolean canPublish) {
        if (user == null || user.getUserId() == null) {
            return List.of();
        }
        Map<Long, Boolean> editable = new HashMap<>();
        List<GuideEntry> result = new ArrayList<>();
        for (DocumentFacts document : store.documents()) {
            if (!document.active()) {
                continue;
            }
            TopicFacts topic = store.topic(document.topicId());
            if (topic == null) {
                continue;
            }
            boolean canEdit = editable.computeIfAbsent(topic.topicId(), id -> store.canEdit(user, topic.topicId()));
            if (!canEdit) {
                continue;
            }
            result.add(new GuideEntry(document.documentKey(), document.title(), topic.topicId(), topic.topicName(),
                    document.hosting() == null ? GuideHtmlRules.EXTERNAL : document.hosting(), true, canPublish));
        }
        result.sort((a, b) -> a.documentKey().compareTo(b.documentKey()));
        return result;
    }

    /** Whole days left, rounded up, so a token created for 90 days reports 90. Zero once expired. */
    static long expiresInDays(LocalDateTime expiresAt, LocalDateTime now) {
        long seconds = Duration.between(now, expiresAt).getSeconds();
        if (seconds <= 0) {
            return 0;
        }
        return (seconds + 86_399) / 86_400;
    }

    private static List<String> orderedScopes(Collection<String> scopes) {
        List<String> ordered = new ArrayList<>();
        if (scopes == null) {
            return ordered;
        }
        for (String scope : GuideScopes.PERSONAL_CHOICES) {
            if (scopes.contains(scope)) {
                ordered.add(scope);
            }
        }
        for (String scope : scopes) {
            if (!ordered.contains(scope)) {
                ordered.add(scope);
            }
        }
        return ordered;
    }

    private static JSONObject guideJson(GuideEntry guide) {
        JSONObject json = new JSONObject();
        json.put("documentKey", guide.documentKey());
        json.put("title", guide.title() == null ? JSONObject.NULL : guide.title());
        json.put("topicId", guide.topicId());
        json.put("topicName", guide.topicName() == null ? JSONObject.NULL : guide.topicName());
        json.put("hosting", guide.hosting());
        json.put("active", guide.active());
        json.put("canPublish", guide.canPublish());
        return json;
    }

    public record GuideEntry(String documentKey, String title, long topicId, String topicName, String hosting,
            boolean active, boolean canPublish) {
    }

    public record TokenFacts(String label, LocalDateTime expiresAt) {
    }

    public record DocumentFacts(String documentKey, String title, long topicId, String hosting, boolean active) {
    }

    public record TopicFacts(long topicId, String topicName) {
    }

    public interface Store {
        TokenFacts token(long tokenId);

        /** Every registered guide; the service keeps the active ones. */
        List<DocumentFacts> documents();

        TopicFacts topic(long topicId);

        boolean canEdit(User user, long topicId);
    }

    static final class HibernateStore implements Store {
        private final UserTokenDao tokenDao = new UserTokenDao();
        private final EsGuideDocumentDao documentDao = new EsGuideDocumentDao();
        private final EsTopicDao topicDao = new EsTopicDao();
        private final TopicSpaceAccessService accessService = new TopicSpaceAccessService();

        @Override
        public TokenFacts token(long tokenId) {
            UserToken token = tokenDao.findById(tokenId).orElse(null);
            return token == null ? null : new TokenFacts(token.getLabel(), token.getExpiresAt());
        }

        @Override
        public List<DocumentFacts> documents() {
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                List<DocumentFacts> rows = new ArrayList<>();
                for (EsGuideDocument document : documentDao.findAllOrdered(session)) {
                    if (document.getEsTopicId() == null) {
                        continue;
                    }
                    rows.add(new DocumentFacts(document.getDocumentKey(), document.getTitle(),
                            document.getEsTopicId(), document.getHosting(), Boolean.TRUE.equals(document.getActive())));
                }
                return rows;
            }
        }

        @Override
        public TopicFacts topic(long topicId) {
            EsTopic topic = entity(topicId);
            return topic == null ? null : new TopicFacts(topicId, topic.getTopicName());
        }

        @Override
        public boolean canEdit(User user, long topicId) {
            EsTopic topic = entity(topicId);
            return topic != null && accessService.canEditTopic(user, topic);
        }

        private EsTopic entity(long topicId) {
            return topicDao.findById(topicId).orElse(null);
        }
    }
}
