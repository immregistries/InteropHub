package org.airahub.interophub.dao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicMeetingPollResponse;
import org.airahub.interophub.model.EsTopicMeetingPollResponse.PollResponseValue;
import org.hibernate.Transaction;

public class EsTopicMeetingPollResponseDao extends GenericDao<EsTopicMeetingPollResponse, Long> {

    public EsTopicMeetingPollResponseDao() {
        super(EsTopicMeetingPollResponse.class);
    }

    public Map<Long, PollResponseValue> findByPollIdAndUserId(Long pollId, Long userId) {
        if (pollId == null || userId == null) {
            return Map.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Object[]> rows = session.createQuery(
                    "select r.esTopicMeetingPollOptionId, r.response"
                            + " from EsTopicMeetingPollResponse r, EsTopicMeetingPollOption o"
                            + " where r.esTopicMeetingPollOptionId = o.esTopicMeetingPollOptionId"
                            + " and o.esTopicMeetingPollId = :pollId"
                            + " and r.userId = :userId",
                    Object[].class)
                    .setParameter("pollId", pollId)
                    .setParameter("userId", userId)
                    .getResultList();
            Map<Long, PollResponseValue> result = new LinkedHashMap<>();
            for (Object[] row : rows) {
                result.put((Long) row[0], (PollResponseValue) row[1]);
            }
            return result;
        }
    }

    public void saveOrUpdateResponses(Long userId, Map<Long, PollResponseValue> responsesByOptionId) {
        if (userId == null || responsesByOptionId == null || responsesByOptionId.isEmpty()) {
            return;
        }
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            for (Map.Entry<Long, PollResponseValue> entry : responsesByOptionId.entrySet()) {
                Long optionId = entry.getKey();
                PollResponseValue responseValue = entry.getValue();
                EsTopicMeetingPollResponse existing = session.createQuery(
                        "from EsTopicMeetingPollResponse r"
                                + " where r.userId = :userId"
                                + " and r.esTopicMeetingPollOptionId = :optionId",
                        EsTopicMeetingPollResponse.class)
                        .setParameter("userId", userId)
                        .setParameter("optionId", optionId)
                        .setMaxResults(1)
                        .uniqueResult();
                if (existing == null) {
                    EsTopicMeetingPollResponse created = new EsTopicMeetingPollResponse();
                    created.setUserId(userId);
                    created.setEsTopicMeetingPollOptionId(optionId);
                    created.setResponse(responseValue);
                    session.persist(created);
                } else {
                    existing.setResponse(responseValue);
                    session.merge(existing);
                }
            }
            tx.commit();
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public List<OptionAggregateRow> aggregateByPollId(Long pollId) {
        if (pollId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Object[]> rows = session.createQuery(
                    "select r.esTopicMeetingPollOptionId, r.response, count(r.esTopicMeetingPollResponseId)"
                            + " from EsTopicMeetingPollResponse r, EsTopicMeetingPollOption o"
                            + " where r.esTopicMeetingPollOptionId = o.esTopicMeetingPollOptionId"
                            + " and o.esTopicMeetingPollId = :pollId"
                            + " group by r.esTopicMeetingPollOptionId, r.response",
                    Object[].class)
                    .setParameter("pollId", pollId)
                    .getResultList();
            List<OptionAggregateRow> result = new ArrayList<>(rows.size());
            for (Object[] row : rows) {
                result.add(new OptionAggregateRow(
                        (Long) row[0],
                        (PollResponseValue) row[1],
                        ((Long) row[2]).intValue()));
            }
            return result;
        }
    }

    public List<OptionResponderNameRow> findResponderNamesByPollId(Long pollId) {
        if (pollId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Object[]> rows = session.createQuery(
                    "select r.esTopicMeetingPollOptionId, r.response, u.displayName, u.firstName, u.lastName, u.userId"
                            + " from EsTopicMeetingPollResponse r, EsTopicMeetingPollOption o, User u"
                            + " where r.esTopicMeetingPollOptionId = o.esTopicMeetingPollOptionId"
                            + " and r.userId = u.userId"
                            + " and o.esTopicMeetingPollId = :pollId"
                            + " order by o.displayOrder asc, o.startsAtUtc asc, r.response asc,"
                            + " lower(coalesce(u.displayName, concat(coalesce(u.firstName, ''), ' ', coalesce(u.lastName, '')))) asc",
                    Object[].class)
                    .setParameter("pollId", pollId)
                    .getResultList();
            List<OptionResponderNameRow> result = new ArrayList<>(rows.size());
            for (Object[] row : rows) {
                String fullName = resolveFullName((String) row[2], (String) row[3], (String) row[4], (Long) row[5]);
                result.add(new OptionResponderNameRow(
                        (Long) row[0],
                        (PollResponseValue) row[1],
                        fullName));
            }
            return result;
        }
    }

    private String resolveFullName(String displayName, String firstName, String lastName, Long userId) {
        String d = trimToNull(displayName);
        if (d != null) {
            return d;
        }
        String first = trimToNull(firstName);
        String last = trimToNull(lastName);
        if (first != null && last != null) {
            return first + " " + last;
        }
        if (first != null) {
            return first;
        }
        if (last != null) {
            return last;
        }
        return "User #" + userId;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class OptionAggregateRow {
        private final Long optionId;
        private final PollResponseValue response;
        private final int count;

        public OptionAggregateRow(Long optionId, PollResponseValue response, int count) {
            this.optionId = optionId;
            this.response = response;
            this.count = count;
        }

        public Long getOptionId() {
            return optionId;
        }

        public PollResponseValue getResponse() {
            return response;
        }

        public int getCount() {
            return count;
        }
    }

    public static final class OptionResponderNameRow {
        private final Long optionId;
        private final PollResponseValue response;
        private final String fullName;

        public OptionResponderNameRow(Long optionId, PollResponseValue response, String fullName) {
            this.optionId = optionId;
            this.response = response;
            this.fullName = fullName;
        }

        public Long getOptionId() {
            return optionId;
        }

        public PollResponseValue getResponse() {
            return response;
        }

        public String getFullName() {
            return fullName;
        }
    }
}
