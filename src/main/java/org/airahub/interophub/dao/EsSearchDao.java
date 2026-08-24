package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsTopic;
import org.hibernate.Session;

/**
 * Read-only candidate queries backing the global header search
 * (see {@code docs/InteropHub_Global_Search_Design.md}). Each method applies
 * Topic Space visibility and the cancelled-meeting exclusion directly in the
 * query, and returns a broad, unranked candidate set; {@code SearchService}
 * is responsible for ranking, deduplication, and excerpt generation.
 */
public class EsSearchDao {

    /** Meeting statuses that may ever appear in search results. Cancelled and still-draft meetings never do. */
    private static final List<EsMeeting.MeetingStatus> SEARCHABLE_MEETING_STATUSES = List.of(
            EsMeeting.MeetingStatus.PROPOSED,
            EsMeeting.MeetingStatus.FINALIZED,
            EsMeeting.MeetingStatus.IN_SESSION,
            EsMeeting.MeetingStatus.COMPLETED,
            EsMeeting.MeetingStatus.CLOSED);

    public record TopicCandidate(Long esTopicId, String topicName, String topicSummary, String description,
            String searchKeywords, String stage, Long esTopicSpaceId, LocalDateTime updatedAt) {
    }

    public record TitleMatchCandidate(Long esMeetingId, String meetingName, String seriesName,
            Long esTopicSpaceId, LocalDateTime scheduledStart, String timezoneId) {
    }

    public record ContentMatchCandidate(Long esMeetingId, String meetingName, Long esTopicSpaceId,
            LocalDateTime scheduledStart, String timezoneId, String matchText) {
    }

    public record SpaceInfo(Long esTopicSpaceId, String spaceName, String spaceCode) {
    }

    public List<TopicCandidate> findTopicCandidates(Set<Long> visibleSpaceIds, String likeQuery, int limit) {
        if (visibleSpaceIds == null || visibleSpaceIds.isEmpty() || likeQuery == null || likeQuery.isBlank()) {
            return List.of();
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select new org.airahub.interophub.dao.EsSearchDao$TopicCandidate("
                            + " t.esTopicId, t.topicName, t.topicSummary, t.description, t.searchKeywords,"
                            + " d.stageName, t.esTopicSpaceId, t.updatedAt)"
                            + " from EsTopic t left join EsTopicStageDefinition d"
                            + " on d.esTopicStageDefinitionId = t.esTopicStageDefinitionId"
                            + " where t.esTopicSpaceId in (:spaceIds)"
                            + " and t.status = :status"
                            + " and (lower(t.topicName) like :like"
                            + " or lower(coalesce(t.searchKeywords, '')) like :like"
                            + " or lower(coalesce(t.topicSummary, '')) like :like"
                            + " or lower(coalesce(t.description, '')) like :like)"
                            + " order by t.esTopicId asc",
                    TopicCandidate.class)
                    .setParameterList("spaceIds", visibleSpaceIds)
                    .setParameter("status", EsTopic.EsTopicStatus.ACTIVE)
                    .setParameter("like", likeQuery)
                    .setMaxResults(limit)
                    .getResultList();
        }
    }

    public List<TitleMatchCandidate> findMeetingTitleCandidates(Set<Long> visibleSpaceIds, String likeQuery,
            int limit) {
        if (visibleSpaceIds == null || visibleSpaceIds.isEmpty() || likeQuery == null || likeQuery.isBlank()) {
            return List.of();
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select new org.airahub.interophub.dao.EsSearchDao$TitleMatchCandidate("
                            + " m.esMeetingId, m.meetingName, s.meetingName, m.esTopicSpaceId, m.scheduledStart,"
                            + " m.timezoneId)"
                            + " from EsMeeting m, EsTopicMeeting s"
                            + " where m.esTopicMeetingId = s.esTopicMeetingId"
                            + " and m.esTopicSpaceId in (:spaceIds)"
                            + " and m.status in (:statuses)"
                            + " and (lower(m.meetingName) like :like or lower(s.meetingName) like :like)"
                            + " order by m.esMeetingId asc",
                    TitleMatchCandidate.class)
                    .setParameterList("spaceIds", visibleSpaceIds)
                    .setParameterList("statuses", SEARCHABLE_MEETING_STATUSES)
                    .setParameter("like", likeQuery)
                    .setMaxResults(limit)
                    .getResultList();
        }
    }

    public List<ContentMatchCandidate> findMeetingDescriptionCandidates(Set<Long> visibleSpaceIds, String likeQuery,
            int limit) {
        return findMeetingContentCandidates(visibleSpaceIds, likeQuery, limit,
                "select new org.airahub.interophub.dao.EsSearchDao$ContentMatchCandidate("
                        + " m.esMeetingId, m.meetingName, m.esTopicSpaceId, m.scheduledStart, m.timezoneId,"
                        + " m.meetingDescription)"
                        + " from EsMeeting m"
                        + " where m.esTopicSpaceId in (:spaceIds)"
                        + " and m.status in (:statuses)"
                        + " and lower(coalesce(m.meetingDescription, '')) like :like"
                        + " order by m.esMeetingId asc");
    }

    public List<ContentMatchCandidate> findAgendaTitleCandidates(Set<Long> visibleSpaceIds, String likeQuery,
            int limit) {
        return findMeetingContentCandidates(visibleSpaceIds, likeQuery, limit,
                "select new org.airahub.interophub.dao.EsSearchDao$ContentMatchCandidate("
                        + " m.esMeetingId, m.meetingName, m.esTopicSpaceId, m.scheduledStart, m.timezoneId, a.title)"
                        + " from EsMeetingAgendaItem a, EsMeeting m"
                        + " where a.esMeetingId = m.esMeetingId"
                        + " and m.esTopicSpaceId in (:spaceIds)"
                        + " and m.status in (:statuses)"
                        + " and lower(a.title) like :like"
                        + " order by m.esMeetingId asc");
    }

    public List<ContentMatchCandidate> findAgendaDescriptionCandidates(Set<Long> visibleSpaceIds, String likeQuery,
            int limit) {
        return findMeetingContentCandidates(visibleSpaceIds, likeQuery, limit,
                "select new org.airahub.interophub.dao.EsSearchDao$ContentMatchCandidate("
                        + " m.esMeetingId, m.meetingName, m.esTopicSpaceId, m.scheduledStart, m.timezoneId,"
                        + " a.agendaMarkdown)"
                        + " from EsMeetingAgendaItem a, EsMeeting m"
                        + " where a.esMeetingId = m.esMeetingId"
                        + " and m.esTopicSpaceId in (:spaceIds)"
                        + " and m.status in (:statuses)"
                        + " and lower(coalesce(a.agendaMarkdown, '')) like :like"
                        + " order by m.esMeetingId asc");
    }

    public List<ContentMatchCandidate> findOutcomeCandidates(Set<Long> visibleSpaceIds, String likeQuery,
            int limit) {
        return findMeetingContentCandidates(visibleSpaceIds, likeQuery, limit,
                "select new org.airahub.interophub.dao.EsSearchDao$ContentMatchCandidate("
                        + " m.esMeetingId, m.meetingName, m.esTopicSpaceId, m.scheduledStart, m.timezoneId,"
                        + " coalesce(o.shortTitle, o.outcomeText))"
                        + " from EsRecordedOutcome o, EsTopicNote n, EsMeeting m"
                        + " where o.esTopicNoteId = n.esTopicNoteId"
                        + " and n.esMeetingId = m.esMeetingId"
                        + " and n.esMeetingId is not null"
                        + " and m.esTopicSpaceId in (:spaceIds)"
                        + " and m.status in (:statuses)"
                        + " and (lower(o.outcomeText) like :like or lower(coalesce(o.shortTitle, '')) like :like)"
                        + " order by m.esMeetingId asc");
    }

    public List<ContentMatchCandidate> findNoteCandidates(Set<Long> visibleSpaceIds, String likeQuery, int limit) {
        return findMeetingContentCandidates(visibleSpaceIds, likeQuery, limit,
                "select new org.airahub.interophub.dao.EsSearchDao$ContentMatchCandidate("
                        + " m.esMeetingId, m.meetingName, m.esTopicSpaceId, m.scheduledStart, m.timezoneId,"
                        + " n.documentText)"
                        + " from EsTopicNote n, EsMeeting m"
                        + " where n.esMeetingId = m.esMeetingId"
                        + " and n.esMeetingId is not null"
                        + " and m.esTopicSpaceId in (:spaceIds)"
                        + " and m.status in (:statuses)"
                        + " and lower(coalesce(n.documentText, '')) like :like"
                        + " order by m.esMeetingId asc");
    }

    private List<ContentMatchCandidate> findMeetingContentCandidates(Set<Long> visibleSpaceIds, String likeQuery,
            int limit, String hql) {
        if (visibleSpaceIds == null || visibleSpaceIds.isEmpty() || likeQuery == null || likeQuery.isBlank()) {
            return List.of();
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(hql, ContentMatchCandidate.class)
                    .setParameterList("spaceIds", visibleSpaceIds)
                    .setParameterList("statuses", SEARCHABLE_MEETING_STATUSES)
                    .setParameter("like", likeQuery)
                    .setMaxResults(limit)
                    .getResultList();
        }
    }

    public Map<Long, SpaceInfo> findSpaceInfoByIds(Set<Long> spaceIds) {
        if (spaceIds == null || spaceIds.isEmpty()) {
            return Map.of();
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<SpaceInfo> rows = session.createQuery(
                    "select new org.airahub.interophub.dao.EsSearchDao$SpaceInfo("
                            + " s.esTopicSpaceId, s.spaceName, s.spaceCode)"
                            + " from EsTopicSpace s where s.esTopicSpaceId in (:spaceIds)",
                    SpaceInfo.class)
                    .setParameterList("spaceIds", spaceIds)
                    .getResultList();
            Map<Long, SpaceInfo> result = new LinkedHashMap<>();
            for (SpaceInfo row : rows) {
                result.put(row.esTopicSpaceId(), row);
            }
            return result;
        }
    }
}
