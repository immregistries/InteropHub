package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsMeetingRsvp;
import org.airahub.interophub.model.MeetingRsvpResponse;

public class EsMeetingRsvpDao extends GenericDao<EsMeetingRsvp, Long> {

    public EsMeetingRsvpDao() {
        super(EsMeetingRsvp.class);
    }

    public Optional<EsMeetingRsvp> findByMeetingIdAndUserId(Long esMeetingId, Long userId) {
        if (esMeetingId == null || userId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingRsvp r where r.esMeetingId = :meetingId and r.userId = :userId",
                    EsMeetingRsvp.class)
                    .setParameter("meetingId", esMeetingId)
                    .setParameter("userId", userId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<EsMeetingRsvp> findByMeetingId(Long esMeetingId) {
        if (esMeetingId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingRsvp r where r.esMeetingId = :meetingId order by r.updatedAt desc",
                    EsMeetingRsvp.class)
                    .setParameter("meetingId", esMeetingId)
                    .getResultList();
        }
    }

    /** Sets (creating or updating) a user's RSVP for a meeting occurrence. */
    public EsMeetingRsvp setRsvp(Long esMeetingId, Long userId, MeetingRsvpResponse response, String note) {
        EsMeetingRsvp rsvp = findByMeetingIdAndUserId(esMeetingId, userId).orElseGet(() -> {
            EsMeetingRsvp created = new EsMeetingRsvp();
            created.setEsMeetingId(esMeetingId);
            created.setUserId(userId);
            return created;
        });
        rsvp.setResponse(response);
        rsvp.setNote(note);
        return save(rsvp);
    }
}
