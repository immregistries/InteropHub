package org.airahub.interophub.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.airahub.interophub.dao.EsCampaignTopicDao;
import org.airahub.interophub.dao.EsCommentDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicReviewDao;
import org.airahub.interophub.model.EsCampaignTopic;
import org.airahub.interophub.model.EsComment;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicReview;
import org.airahub.interophub.model.User;

public class EsTopicReviewService {

    public static final String CDC_POLICY_STATUS_NOT_SUPPORTED = "Not currently supported by CDC policy";

    private final EsTopicReviewDao reviewDao;
    private final EsCampaignTopicDao campaignTopicDao;
    private final EsTopicDao topicDao;
    private final EsCommentDao commentDao;

    public EsTopicReviewService() {
        this.reviewDao = new EsTopicReviewDao();
        this.campaignTopicDao = new EsCampaignTopicDao();
        this.topicDao = new EsTopicDao();
        this.commentDao = new EsCommentDao();
    }

    public List<EsTopicReview> findUserReviews(Long campaignId, Long userId) {
        return reviewDao.findByCampaignIdAndUserId(campaignId, userId);
    }

    public Map<Long, Integer> findUserScoresByTopicId(Long campaignId, Long userId) {
        Map<Long, Integer> scoreByTopicId = new HashMap<>();
        for (EsTopicReview review : findUserReviews(campaignId, userId)) {
            if (review.getEsTopicId() != null && review.getCommunityValueScore() != null) {
                scoreByTopicId.put(review.getEsTopicId(), review.getCommunityValueScore());
            }
        }
        return scoreByTopicId;
    }

    public SaveResult saveScore(Long campaignId, Long topicId, Long userId, Integer score) {
        if (campaignId == null || campaignId <= 0L) {
            throw new IllegalArgumentException("Campaign is required.");
        }
        if (topicId == null || topicId <= 0L) {
            throw new IllegalArgumentException("Topic is required.");
        }
        if (userId == null || userId <= 0L) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }
        if (score == null || score < 0 || score > 5) {
            throw new IllegalArgumentException("Score must be between 0 and 5.");
        }

        EsTopic topic = requireActiveTopic(topicId);
        Optional<EsTopicReview> existing = reviewDao.findByCampaignIdAndTopicIdAndUserId(campaignId, topicId, userId);

        EsTopicReview review = existing.orElseGet(EsTopicReview::new);
        review.setEsCampaignId(campaignId);
        review.setEsTopicId(topic.getEsTopicId());
        review.setUserId(userId);
        review.setCommunityValueScore(score);

        EsTopicReview saved = reviewDao.saveOrUpdate(review);
        long reviewedCount = reviewDao.countReviewedTopicsByCampaignIdAndUserId(campaignId, userId);
        return new SaveResult(saved, reviewedCount);
    }

    public SaveResult saveCdcSignal(Long campaignId, Long topicId, Long userId, Integer score) {
        if (campaignId == null || campaignId <= 0L) {
            throw new IllegalArgumentException("Campaign is required.");
        }
        if (topicId == null || topicId <= 0L) {
            throw new IllegalArgumentException("Topic is required.");
        }
        if (userId == null || userId <= 0L) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }
        if (!isAllowedCdcSignalScore(score)) {
            throw new IllegalArgumentException("Signal must be one of 1, 3, or 4.");
        }

        EsTopic topic = requireCampaignActiveTopic(campaignId, topicId);
        if (isCdcPolicyBlocked(topic)) {
            throw new IllegalArgumentException(
                    "Feedback is not requested because this topic is marked: " + CDC_POLICY_STATUS_NOT_SUPPORTED
                            + ".");
        }

        Optional<EsTopicReview> existing = reviewDao.findByCampaignIdAndTopicIdAndUserId(campaignId, topicId, userId);

        EsTopicReview review = existing.orElseGet(EsTopicReview::new);
        review.setEsCampaignId(campaignId);
        review.setEsTopicId(topic.getEsTopicId());
        review.setUserId(userId);
        review.setCommunityValueScore(score);

        EsTopicReview saved = reviewDao.saveOrUpdate(review);
        long reviewedCount = reviewDao.countReviewedTopicsByCampaignIdAndUserId(campaignId, userId);
        return new SaveResult(saved, reviewedCount);
    }

    public EsComment addTopicComment(Long campaignId, Long topicId, User user, String commentText) {
        if (campaignId == null || campaignId <= 0L) {
            throw new IllegalArgumentException("Campaign is required.");
        }
        if (topicId == null || topicId <= 0L) {
            throw new IllegalArgumentException("Topic is required.");
        }
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }
        String normalizedComment = trimToNull(commentText);
        if (normalizedComment == null) {
            throw new IllegalArgumentException("Comment text is required.");
        }

        EsTopic topic = requireActiveTopic(topicId);
        NameParts nameParts = deriveNameParts(user);

        EsComment comment = new EsComment();
        comment.setEsCampaignId(campaignId);
        comment.setEsTopicId(topic.getEsTopicId());
        comment.setUserId(user.getUserId());
        comment.setSessionKey(null);
        comment.setFirstName(nameParts.firstName());
        comment.setLastName(nameParts.lastName());
        comment.setEmail(trimToNull(user.getEmail()));
        comment.setEmailNormalized(EsNormalizer.normalizeEmail(user.getEmail()));
        comment.setCommentType(EsComment.CommentType.TOPIC);
        comment.setCommentText(normalizedComment);
        return commentDao.save(comment);
    }

    public EsComment addCdcTopicComment(Long campaignId, Long topicId, User user, String commentText) {
        if (campaignId == null || campaignId <= 0L) {
            throw new IllegalArgumentException("Campaign is required.");
        }
        if (topicId == null || topicId <= 0L) {
            throw new IllegalArgumentException("Topic is required.");
        }
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }
        String normalizedComment = trimToNull(commentText);
        if (normalizedComment == null) {
            throw new IllegalArgumentException("Comment text is required.");
        }

        EsTopic topic = requireCampaignActiveTopic(campaignId, topicId);
        if (isCdcPolicyBlocked(topic)) {
            throw new IllegalArgumentException(
                    "Feedback is not requested because this topic is marked: " + CDC_POLICY_STATUS_NOT_SUPPORTED
                            + ".");
        }

        NameParts nameParts = deriveNameParts(user);

        EsComment comment = new EsComment();
        comment.setEsCampaignId(campaignId);
        comment.setEsTopicId(topic.getEsTopicId());
        comment.setUserId(user.getUserId());
        comment.setSessionKey(null);
        comment.setFirstName(nameParts.firstName());
        comment.setLastName(nameParts.lastName());
        comment.setEmail(trimToNull(user.getEmail()));
        comment.setEmailNormalized(EsNormalizer.normalizeEmail(user.getEmail()));
        comment.setCommentType(EsComment.CommentType.TOPIC);
        comment.setCommentText(normalizedComment);
        return commentDao.save(comment);
    }

    public EsTopic updateCdcPolicyStatus(Long campaignId, Long topicId, String action) {
        if (campaignId == null || campaignId <= 0L) {
            throw new IllegalArgumentException("Campaign is required.");
        }
        if (topicId == null || topicId <= 0L) {
            throw new IllegalArgumentException("Topic is required.");
        }
        String normalizedAction = trimToNull(action);
        if (normalizedAction == null) {
            throw new IllegalArgumentException("policyStatusAction is required.");
        }

        EsTopic topic = requireCampaignActiveTopic(campaignId, topicId);
        if ("set".equalsIgnoreCase(normalizedAction)) {
            topic.setPolicyStatus(CDC_POLICY_STATUS_NOT_SUPPORTED);
        } else if ("clear".equalsIgnoreCase(normalizedAction)) {
            topic.setPolicyStatus(null);
        } else {
            throw new IllegalArgumentException("policyStatusAction must be set or clear.");
        }
        return topicDao.saveOrUpdate(topic);
    }

    public List<EsTopicReviewDao.ResponderRow> findResponders(Long campaignId) {
        return reviewDao.findRespondersByCampaignId(campaignId);
    }

    public long countResponders(Long campaignId) {
        return reviewDao.countDistinctRespondersByCampaignId(campaignId);
    }

    public List<EsTopicReviewDao.TopicSummaryRow> findTopicSummary(Long campaignId) {
        return reviewDao.findTopicSummaryByCampaignIdAcrossActiveTopics(campaignId);
    }

    private EsTopic requireActiveTopic(Long topicId) {
        EsTopic topic = topicDao.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic was not found."));
        if (topic.getStatus() != EsTopic.EsTopicStatus.ACTIVE) {
            throw new IllegalArgumentException("Topic is not active.");
        }
        return topic;
    }

    private EsTopic requireCampaignActiveTopic(Long campaignId, Long topicId) {
        EsCampaignTopic campaignTopic = campaignTopicDao.findByCampaignIdAndTopicId(campaignId, topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic is not included in this campaign."));
        return requireActiveTopic(campaignTopic.getEsTopicId());
    }

    private boolean isAllowedCdcSignalScore(Integer score) {
        return Integer.valueOf(1).equals(score)
                || Integer.valueOf(3).equals(score)
                || Integer.valueOf(4).equals(score);
    }

    public boolean isCdcPolicyBlocked(EsTopic topic) {
        return topic != null && CDC_POLICY_STATUS_NOT_SUPPORTED.equals(trimToNull(topic.getPolicyStatus()));
    }

    private NameParts deriveNameParts(User user) {
        String displayName = trimToNull(user.getFullName());
        if (displayName != null) {
            String[] parts = displayName.split("\\s+");
            String first = trimToNull(parts.length > 0 ? parts[0] : null);
            String last = trimToNull(
                    parts.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length)) : null);
            if (first != null) {
                return new NameParts(first, last);
            }
        }

        String email = trimToNull(user.getEmail());
        if (email == null) {
            email = trimToNull(user.getEmailNormalized());
        }
        if (email != null) {
            String first = email;
            int at = email.indexOf('@');
            if (at > 0) {
                first = email.substring(0, at);
            }
            return new NameParts(first, null);
        }
        return new NameParts("User", null);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record SaveResult(EsTopicReview review, long reviewedCount) {
    }

    private record NameParts(String firstName, String lastName) {
    }
}
