package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.hibernate.Session;

public class EmailProspectDao {

    private static final String BASE_SELECT = "SELECT email_normalized, first_contact_at, last_contact_at, " +
            "campaign_registration_count, comment_count, subscription_count, meeting_member_count " +
            "FROM v_email_prospect ";

    public long countProspects() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Object result = session
                    .createNativeQuery("SELECT COUNT(*) FROM v_email_prospect")
                    .getSingleResult();
            return ((Number) result).longValue();
        }
    }

    public List<EmailProspectBrowseRow> findRecentProspects(int limit) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Object[]> rows = session
                    .createNativeQuery(BASE_SELECT + "ORDER BY last_contact_at DESC", Object[].class)
                    .setMaxResults(limit)
                    .getResultList();
            return mapRows(rows);
        }
    }

    public List<EmailProspectBrowseRow> searchProspects(String query) {
        String pattern = "%" + query.trim().toLowerCase() + "%";
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Object[]> rows = session
                    .createNativeQuery(BASE_SELECT + "WHERE email_normalized LIKE :p ORDER BY last_contact_at DESC",
                            Object[].class)
                    .setParameter("p", pattern)
                    .getResultList();
            return mapRows(rows);
        }
    }

    private List<EmailProspectBrowseRow> mapRows(List<Object[]> rows) {
        List<EmailProspectBrowseRow> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            result.add(new EmailProspectBrowseRow(
                    (String) row[0],
                    row[1] == null ? null : ((java.sql.Timestamp) row[1]).toLocalDateTime(),
                    row[2] == null ? null : ((java.sql.Timestamp) row[2]).toLocalDateTime(),
                    ((Number) row[3]).longValue(),
                    ((Number) row[4]).longValue(),
                    ((Number) row[5]).longValue(),
                    ((Number) row[6]).longValue()));
        }
        return result;
    }
}
