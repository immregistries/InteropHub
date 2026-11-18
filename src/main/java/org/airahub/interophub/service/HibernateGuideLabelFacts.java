package org.airahub.interophub.service;

import java.time.LocalDateTime;
import org.airahub.interophub.dao.EsGuideLabelDao;
import org.airahub.interophub.model.EsGuideLabel;
import org.airahub.interophub.service.GuideRows.LabelRow;
import org.hibernate.Session;

/** Reads {@link GuideLabels.Facts} for one guide in four small queries. */
final class HibernateGuideLabelFacts {
    private static final EsGuideLabelDao LABEL_DAO = new EsGuideLabelDao();

    private HibernateGuideLabelFacts() {
    }

    static GuideLabels.Facts load(Session session, long documentId) {
        GuideLabels.Facts facts = new GuideLabels.Facts();
        for (EsGuideLabel entity : LABEL_DAO.findByDocumentId(session, documentId)) {
            LabelRow row = toRow(entity);
            facts.settings.put(row.label, row);
        }
        for (Object[] row : session.createQuery("select v.label, max(v.uploadedAt) from EsGuideHostedVersion v"
                + " where v.guideDocumentId = :id group by v.label", Object[].class)
                .setParameter("id", documentId).getResultList()) {
            if (row[0] instanceof String label && row[1] instanceof LocalDateTime at) {
                facts.uploads.put(label, at);
            }
        }
        for (Object[] row : session.createQuery("select b.label, max(b.receivedAt) from EsGuideBuild b"
                + " where b.guideDocumentId = :id and b.label is not null group by b.label", Object[].class)
                .setParameter("id", documentId).getResultList()) {
            if (row[0] instanceof String label && row[1] instanceof LocalDateTime at) {
                facts.builds.put(label, at);
            }
        }
        for (String label : session.createQuery("select u.label from EsGuideDocumentUrl u"
                + " where u.guideDocumentId = :id order by u.sortOrder asc, u.guideDocumentUrlId asc", String.class)
                .setParameter("id", documentId).getResultList()) {
            if (label != null && !facts.urlLabels.contains(label)) {
                facts.urlLabels.add(label);
            }
        }
        return facts;
    }

    static LabelRow toRow(EsGuideLabel entity) {
        LabelRow row = new LabelRow();
        row.id = entity.getGuideLabelId();
        row.documentId = entity.getGuideDocumentId();
        row.label = entity.getLabel();
        row.display = entity.getDisplayState() == null ? GuideLabels.UNLISTED : entity.getDisplayState();
        row.commentMode = entity.getCommentMode() == null ? GuideLabels.OPEN : entity.getCommentMode();
        row.commentModeSetAt = entity.getCommentModeSetAt();
        row.updatedByUserId = entity.getUpdatedByUserId();
        row.updatedAt = entity.getUpdatedAt();
        return row;
    }
}
