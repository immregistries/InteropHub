package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.dao.EsCampaignMeetingBrowseRow;
import org.airahub.interophub.dao.EsCampaignTopicBrowseRow;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsNeighborhood;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicCuration;
import org.airahub.interophub.model.EsTopicRelationship;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.EsTopicSpaceMember;
import org.airahub.interophub.model.User;

public class TopicSpaceAccessService {

    private final AuthFlowService authFlowService;

    public TopicSpaceAccessService() {
        this.authFlowService = new AuthFlowService();
    }

    public boolean canViewSpace(User user, EsTopicSpace topicSpace) {
        return topicSpace != null && canViewSpaceId(user, topicSpace.getEsTopicSpaceId());
    }

    public boolean canViewSpaceId(User user, Long esTopicSpaceId) {
        if (authFlowService.isAdminUser(user)) {
            return esTopicSpaceId != null;
        }
        if (esTopicSpaceId == null) {
            return false;
        }
        return getVisibleSpaceIds(user).contains(esTopicSpaceId);
    }

    public boolean canAdministerSpace(User user, Long esTopicSpaceId) {
        if (authFlowService.isAdminUser(user)) {
            return esTopicSpaceId != null;
        }
        if (user == null || user.getUserId() == null || esTopicSpaceId == null) {
            return false;
        }
        return findAdminSpaceIdsByUserId(user.getUserId()).contains(esTopicSpaceId);
    }

    public boolean canViewTopic(User user, EsTopic topic) {
        return topic != null && canViewSpaceId(user, topic.getEsTopicSpaceId());
    }

    public boolean canViewTopicId(User user, Long topicId) {
        if (topicId == null) {
            return false;
        }
        Map<Long, Long> spaceIdsByTopicId = findTopicSpaceIdsByTopicIds(List.of(topicId));
        return canViewSpaceId(user, spaceIdsByTopicId.get(topicId));
    }

    public boolean canEditTopic(User user, EsTopic topic) {
        return topic != null && canAdministerSpace(user, topic.getEsTopicSpaceId());
    }

    public boolean canViewMeeting(User user, EsMeeting meeting) {
        return meeting != null && canViewSpaceId(user, meeting.getEsTopicSpaceId());
    }

    public boolean canViewMeetingId(User user, Long meetingId) {
        if (meetingId == null) {
            return false;
        }
        Map<Long, Long> spaceIdsByMeetingId = findMeetingSpaceIdsByMeetingIds(List.of(meetingId));
        return canViewSpaceId(user, spaceIdsByMeetingId.get(meetingId));
    }

    public boolean canAdministerMeeting(User user, EsMeeting meeting) {
        return meeting != null && canAdministerSpace(user, meeting.getEsTopicSpaceId());
    }

    public Set<Long> getVisibleSpaceIds(User user) {
        if (authFlowService.isAdminUser(user)) {
            return findAllSpaceIds();
        }

        Set<Long> visibleSpaceIds = new LinkedHashSet<>(findPublicSpaceIds());
        if (user != null && user.getUserId() != null) {
            visibleSpaceIds.addAll(findMemberSpaceIdsByUserId(user.getUserId()));
        }
        return visibleSpaceIds;
    }

    public List<EsCampaignTopicBrowseRow> filterVisibleTopicRows(User user, List<EsCampaignTopicBrowseRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<Long> visibleSpaceIds = getVisibleSpaceIds(user);
        List<Long> topicIds = rows.stream()
                .map(EsCampaignTopicBrowseRow::getEsTopicId)
                .toList();
        Map<Long, Long> spaceIdsByTopicId = findTopicSpaceIdsByTopicIds(topicIds);
        List<EsCampaignTopicBrowseRow> visibleRows = new ArrayList<>();
        for (EsCampaignTopicBrowseRow row : rows) {
            Long spaceId = spaceIdsByTopicId.get(row.getEsTopicId());
            if (spaceId != null && visibleSpaceIds.contains(spaceId)) {
                visibleRows.add(row);
            }
        }
        return visibleRows;
    }

    public List<EsCampaignMeetingBrowseRow> filterVisibleMeetingRows(User user, List<EsCampaignMeetingBrowseRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<Long> visibleSpaceIds = getVisibleSpaceIds(user);
        List<Long> topicIds = rows.stream()
                .map(EsCampaignMeetingBrowseRow::getEsTopicId)
                .toList();
        Map<Long, Long> spaceIdsByTopicId = findTopicSpaceIdsByTopicIds(topicIds);
        List<EsCampaignMeetingBrowseRow> visibleRows = new ArrayList<>();
        for (EsCampaignMeetingBrowseRow row : rows) {
            Long spaceId = spaceIdsByTopicId.get(row.getEsTopicId());
            if (spaceId != null && visibleSpaceIds.contains(spaceId)) {
                visibleRows.add(row);
            }
        }
        return visibleRows;
    }

    public List<EsMeeting> filterVisibleMeetings(User user, List<EsMeeting> meetings) {
        if (meetings == null || meetings.isEmpty()) {
            return List.of();
        }
        Set<Long> visibleSpaceIds = getVisibleSpaceIds(user);
        List<EsMeeting> visibleMeetings = new ArrayList<>();
        for (EsMeeting meeting : meetings) {
            if (meeting != null && meeting.getEsTopicSpaceId() != null
                    && visibleSpaceIds.contains(meeting.getEsTopicSpaceId())) {
                visibleMeetings.add(meeting);
            }
        }
        return visibleMeetings;
    }

    public List<EsTopic> filterVisibleTopics(User user, List<EsTopic> topics) {
        if (topics == null || topics.isEmpty()) {
            return List.of();
        }
        Set<Long> visibleSpaceIds = getVisibleSpaceIds(user);
        List<EsTopic> visibleTopics = new ArrayList<>();
        for (EsTopic topic : topics) {
            if (topic != null && topic.getEsTopicSpaceId() != null
                    && visibleSpaceIds.contains(topic.getEsTopicSpaceId())) {
                visibleTopics.add(topic);
            }
        }
        return visibleTopics;
    }

    public List<EsNeighborhood> filterVisibleNeighborhoods(User user, List<EsNeighborhood> neighborhoods) {
        if (neighborhoods == null || neighborhoods.isEmpty()) {
            return List.of();
        }
        Set<Long> visibleSpaceIds = getVisibleSpaceIds(user);
        List<EsNeighborhood> visibleNeighborhoods = new ArrayList<>();
        for (EsNeighborhood neighborhood : neighborhoods) {
            if (neighborhood != null && neighborhood.getEsTopicSpaceId() != null
                    && visibleSpaceIds.contains(neighborhood.getEsTopicSpaceId())) {
                visibleNeighborhoods.add(neighborhood);
            }
        }
        return visibleNeighborhoods;
    }

    public List<EsTopicRelationship> filterVisibleRelationships(User user, List<EsTopicRelationship> relationships) {
        if (relationships == null || relationships.isEmpty()) {
            return List.of();
        }
        Set<Long> topicIds = new LinkedHashSet<>();
        for (EsTopicRelationship relationship : relationships) {
            if (relationship != null) {
                topicIds.add(relationship.getFromTopicId());
                topicIds.add(relationship.getToTopicId());
            }
        }
        Set<Long> visibleTopicIds = findVisibleTopicIds(user, new ArrayList<>(topicIds));
        List<EsTopicRelationship> visibleRelationships = new ArrayList<>();
        for (EsTopicRelationship relationship : relationships) {
            if (relationship != null
                    && visibleTopicIds.contains(relationship.getFromTopicId())
                    && visibleTopicIds.contains(relationship.getToTopicId())) {
                visibleRelationships.add(relationship);
            }
        }
        return visibleRelationships;
    }

    public List<EsTopicCuration> filterVisibleCurations(User user, List<EsTopicCuration> curations) {
        if (curations == null || curations.isEmpty()) {
            return List.of();
        }
        Set<Long> topicIds = new LinkedHashSet<>();
        for (EsTopicCuration curation : curations) {
            if (curation != null) {
                topicIds.add(curation.getCuratorTopicId());
                topicIds.add(curation.getCuratedTopicId());
            }
        }
        Set<Long> visibleTopicIds = findVisibleTopicIds(user, new ArrayList<>(topicIds));
        List<EsTopicCuration> visibleCurations = new ArrayList<>();
        for (EsTopicCuration curation : curations) {
            if (curation != null
                    && visibleTopicIds.contains(curation.getCuratorTopicId())
                    && visibleTopicIds.contains(curation.getCuratedTopicId())) {
                visibleCurations.add(curation);
            }
        }
        return visibleCurations;
    }

    public List<EsTopicSpace> filterVisibleSpaces(User user, List<EsTopicSpace> topicSpaces) {
        if (topicSpaces == null || topicSpaces.isEmpty()) {
            return List.of();
        }
        Set<Long> visibleSpaceIds = getVisibleSpaceIds(user);
        List<EsTopicSpace> visibleSpaces = new ArrayList<>();
        for (EsTopicSpace topicSpace : topicSpaces) {
            if (topicSpace != null && topicSpace.getEsTopicSpaceId() != null
                    && visibleSpaceIds.contains(topicSpace.getEsTopicSpaceId())) {
                visibleSpaces.add(topicSpace);
            }
        }
        return visibleSpaces;
    }

    private Set<Long> findVisibleTopicIds(User user, List<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> visibleSpaceIds = getVisibleSpaceIds(user);
        Map<Long, Long> spaceIdsByTopicId = findTopicSpaceIdsByTopicIds(topicIds);
        Set<Long> visibleTopicIds = new LinkedHashSet<>();
        for (Map.Entry<Long, Long> entry : spaceIdsByTopicId.entrySet()) {
            if (entry.getValue() != null && visibleSpaceIds.contains(entry.getValue())) {
                visibleTopicIds.add(entry.getKey());
            }
        }
        return visibleTopicIds;
    }

    private Set<Long> findAllSpaceIds() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return new LinkedHashSet<>(session.createQuery(
                    "select s.esTopicSpaceId from EsTopicSpace s",
                    Long.class).getResultList());
        }
    }

    private Set<Long> findPublicSpaceIds() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return new LinkedHashSet<>(session.createQuery(
                    "select s.esTopicSpaceId from EsTopicSpace s where s.visibility = :visibility",
                    Long.class)
                    .setParameter("visibility", EsTopicSpace.Visibility.PUBLIC)
                    .getResultList());
        }
    }

    private Set<Long> findMemberSpaceIdsByUserId(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return new LinkedHashSet<>(session.createQuery(
                    "select distinct m.esTopicSpaceId from EsTopicSpaceMember m where m.userId = :userId",
                    Long.class)
                    .setParameter("userId", userId)
                    .getResultList());
        }
    }

    private Set<Long> findAdminSpaceIdsByUserId(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return new LinkedHashSet<>(session.createQuery(
                    "select distinct m.esTopicSpaceId from EsTopicSpaceMember m"
                            + " where m.userId = :userId and m.role = :role",
                    Long.class)
                    .setParameter("userId", userId)
                    .setParameter("role", EsTopicSpaceMember.MemberRole.ADMIN)
                    .getResultList());
        }
    }

    private Map<Long, Long> findTopicSpaceIdsByTopicIds(List<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return Map.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Object[]> rows = session.createQuery(
                    "select t.esTopicId, t.esTopicSpaceId from EsTopic t where t.esTopicId in (:topicIds)",
                    Object[].class)
                    .setParameterList("topicIds", topicIds)
                    .getResultList();
            Map<Long, Long> result = new LinkedHashMap<>();
            for (Object[] row : rows) {
                result.put((Long) row[0], (Long) row[1]);
            }
            return result;
        }
    }

    private Map<Long, Long> findMeetingSpaceIdsByMeetingIds(List<Long> meetingIds) {
        if (meetingIds == null || meetingIds.isEmpty()) {
            return Map.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Object[]> rows = session.createQuery(
                    "select m.esMeetingId, m.esTopicSpaceId from EsMeeting m where m.esMeetingId in (:meetingIds)",
                    Object[].class)
                    .setParameterList("meetingIds", meetingIds)
                    .getResultList();
            Map<Long, Long> result = new LinkedHashMap<>();
            for (Object[] row : rows) {
                result.put((Long) row[0], (Long) row[1]);
            }
            return result;
        }
    }
}