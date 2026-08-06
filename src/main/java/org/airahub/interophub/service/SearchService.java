package org.airahub.interophub.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.airahub.interophub.dao.EsSearchDao;

/**
 * Shared search service backing the InteropHub global header search (see
 * {@code docs/InteropHub_Global_Search_Design.md}). Used identically by the
 * search-as-you-type suggestion endpoint and the full results page so
 * ranking, deduplication, and authorization stay consistent between them.
 *
 * <p>Authorization is the caller's responsibility: pass only the Topic Space
 * ids the current viewer (anonymous or signed in) is permitted to see, as
 * resolved by {@code TopicSpaceAccessService.getVisibleSpaceIds(user)}. This
 * service never widens that set, and every underlying query filters on it
 * directly rather than post-filtering fetched rows.
 */
public class SearchService {

    /** Generous per-source candidate cap; ranking/dedup/display limits are applied after fetch. */
    private static final int CANDIDATE_FETCH_LIMIT = 300;
    private static final int EXCERPT_LENGTH = 160;

    private final SearchDataSource dataSource;
    private final Clock clock;

    public SearchService() {
        this(new DaoSearchDataSource(new EsSearchDao()), Clock.systemDefaultZone());
    }

    SearchService(SearchDataSource dataSource, Clock clock) {
        this.dataSource = dataSource;
        this.clock = clock;
    }

    /**
     * Runs the full search: topics ranked by relevance, meetings split into
     * upcoming (ascending by date) and previous (descending by date), each
     * bucket ranked by relevance first. Cancelled and draft meetings are
     * excluded by the underlying queries; deduplication keeps one entry per
     * meeting, using its single strongest match.
     *
     * @param rawQuery user-entered query text; blank returns empty results
     * @param visibleSpaceIds Topic Space ids the current viewer may see
     */
    public Results search(String rawQuery, Set<Long> visibleSpaceIds) {
        String query = rawQuery == null ? "" : rawQuery.strip();
        if (query.isEmpty() || visibleSpaceIds == null || visibleSpaceIds.isEmpty()) {
            return new Results(List.of(), List.of(), List.of());
        }
        String likeQuery = "%" + SearchTextUtil.normalize(query) + "%";

        List<TopicResult> topics = rankTopics(query, likeQuery, visibleSpaceIds);
        List<MeetingCandidateEntry> meetingEntries = mergeMeetingCandidates(query, likeQuery, visibleSpaceIds);

        Map<Long, EsSearchDao.SpaceInfo> spaceInfoById = loadSpaceInfo(topics, meetingEntries);

        List<TopicResult> resolvedTopics = topics.stream()
                .map(t -> t.withSpace(spaceInfoById.get(t.esTopicSpaceId())))
                .sorted(Comparator.comparingInt(TopicResult::rankTier)
                        .thenComparing((a, b) -> compareRecency(a.updatedAt(), b.updatedAt()))
                        .thenComparing(TopicResult::esTopicId))
                .toList();

        LocalDateTime now = LocalDateTime.now(clock);
        List<MeetingResult> upcoming = new ArrayList<>();
        List<MeetingResult> previous = new ArrayList<>();
        for (MeetingCandidateEntry entry : meetingEntries) {
            MeetingResult result = entry.toResult(query, spaceInfoById.get(entry.esTopicSpaceId), EXCERPT_LENGTH);
            if (entry.scheduledStart != null && !entry.scheduledStart.isBefore(now)) {
                upcoming.add(result);
            } else {
                previous.add(result);
            }
        }
        upcoming.sort(Comparator.comparingInt(MeetingResult::rankTier)
                .thenComparing(MeetingResult::scheduledStart, Comparator.nullsLast(Comparator.naturalOrder())));
        previous.sort(Comparator.comparingInt(MeetingResult::rankTier)
                .thenComparing(MeetingResult::scheduledStart, Comparator.nullsLast(Comparator.reverseOrder())));

        return new Results(resolvedTopics, upcoming, previous);
    }

    private int compareRecency(LocalDateTime a, LocalDateTime b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return b.compareTo(a);
    }

    private List<TopicResult> rankTopics(String query, String likeQuery, Set<Long> visibleSpaceIds) {
        String normQuery = SearchTextUtil.normalize(query);
        List<TopicResult> results = new ArrayList<>();
        for (EsSearchDao.TopicCandidate candidate : dataSource.findTopicCandidates(visibleSpaceIds, likeQuery,
                CANDIDATE_FETCH_LIMIT)) {
            String normTitle = SearchTextUtil.normalize(candidate.topicName());
            String normKeywords = SearchTextUtil.normalize(candidate.searchKeywords());
            String normSummary = SearchTextUtil.normalize(candidate.topicSummary());
            String normDescription = SearchTextUtil.normalize(candidate.description());

            int tier;
            String excerptSource;
            if (normTitle.equals(normQuery)) {
                tier = 1;
                excerptSource = candidate.topicSummary();
            } else if (normTitle.startsWith(normQuery)) {
                tier = 2;
                excerptSource = candidate.topicSummary();
            } else if (normTitle.contains(normQuery)) {
                tier = 3;
                excerptSource = candidate.topicSummary();
            } else if (normKeywords.contains(normQuery)) {
                tier = 4;
                excerptSource = candidate.topicSummary() != null ? candidate.topicSummary() : candidate.searchKeywords();
            } else if (normSummary.contains(normQuery)) {
                tier = 5;
                excerptSource = candidate.topicSummary();
            } else if (normDescription.contains(normQuery)) {
                tier = 6;
                excerptSource = candidate.description();
            } else {
                // Matched one of the LIKE'd fields but not any normalized check above
                // (e.g. punctuation difference); still surface it, ranked lowest.
                tier = 6;
                excerptSource = candidate.topicSummary() != null ? candidate.topicSummary() : candidate.description();
            }

            String summaryHtml = excerptSource == null || excerptSource.isBlank()
                    ? ""
                    : SearchTextUtil.excerptAndHighlight(excerptSource, query, EXCERPT_LENGTH);

            String titleHtml = SearchTextUtil.highlightFull(candidate.topicName(), query);
            results.add(new TopicResult(candidate.esTopicId(), candidate.topicName(), titleHtml, candidate.stage(),
                    candidate.esTopicSpaceId(), null, summaryHtml, tier, candidate.updatedAt()));
        }
        return results;
    }

    private List<MeetingCandidateEntry> mergeMeetingCandidates(String query, String likeQuery,
            Set<Long> visibleSpaceIds) {
        Map<Long, MeetingCandidateEntry> byMeetingId = new LinkedHashMap<>();

        for (EsSearchDao.TitleMatchCandidate c : dataSource.findMeetingTitleCandidates(visibleSpaceIds, likeQuery,
                CANDIDATE_FETCH_LIMIT)) {
            String normQuery = SearchTextUtil.normalize(query);
            boolean exact = SearchTextUtil.normalize(c.meetingName()).equals(normQuery)
                    || SearchTextUtil.normalize(c.seriesName()).equals(normQuery);
            int tier = exact ? 1 : 2;
            entry(byMeetingId, c.esMeetingId(), c.meetingName(), c.esTopicSpaceId(), c.scheduledStart(),
                    c.timezoneId())
                    .addMatch(tier, MatchSource.TITLE, null);
        }
        for (EsSearchDao.ContentMatchCandidate c : dataSource.findAgendaTitleCandidates(visibleSpaceIds, likeQuery,
                CANDIDATE_FETCH_LIMIT)) {
            entry(byMeetingId, c.esMeetingId(), c.meetingName(), c.esTopicSpaceId(), c.scheduledStart(),
                    c.timezoneId())
                    .addMatch(3, MatchSource.AGENDA, c.matchText());
        }
        for (EsSearchDao.ContentMatchCandidate c : dataSource.findAgendaDescriptionCandidates(visibleSpaceIds,
                likeQuery, CANDIDATE_FETCH_LIMIT)) {
            entry(byMeetingId, c.esMeetingId(), c.meetingName(), c.esTopicSpaceId(), c.scheduledStart(),
                    c.timezoneId())
                    .addMatch(4, MatchSource.AGENDA, c.matchText());
        }
        for (EsSearchDao.ContentMatchCandidate c : dataSource.findMeetingDescriptionCandidates(visibleSpaceIds,
                likeQuery, CANDIDATE_FETCH_LIMIT)) {
            entry(byMeetingId, c.esMeetingId(), c.meetingName(), c.esTopicSpaceId(), c.scheduledStart(),
                    c.timezoneId())
                    .addMatch(5, MatchSource.DESCRIPTION, c.matchText());
        }
        for (EsSearchDao.ContentMatchCandidate c : dataSource.findOutcomeCandidates(visibleSpaceIds, likeQuery,
                CANDIDATE_FETCH_LIMIT)) {
            entry(byMeetingId, c.esMeetingId(), c.meetingName(), c.esTopicSpaceId(), c.scheduledStart(),
                    c.timezoneId())
                    .addMatch(6, MatchSource.OUTCOME, c.matchText());
        }
        for (EsSearchDao.ContentMatchCandidate c : dataSource.findNoteCandidates(visibleSpaceIds, likeQuery,
                CANDIDATE_FETCH_LIMIT)) {
            entry(byMeetingId, c.esMeetingId(), c.meetingName(), c.esTopicSpaceId(), c.scheduledStart(),
                    c.timezoneId())
                    .addMatch(7, MatchSource.NOTE, c.matchText());
        }

        return new ArrayList<>(byMeetingId.values());
    }

    private MeetingCandidateEntry entry(Map<Long, MeetingCandidateEntry> byMeetingId, Long meetingId,
            String meetingName, Long esTopicSpaceId, LocalDateTime scheduledStart, String timezoneId) {
        return byMeetingId.computeIfAbsent(meetingId,
                id -> new MeetingCandidateEntry(id, meetingName, esTopicSpaceId, scheduledStart, timezoneId));
    }

    private Map<Long, EsSearchDao.SpaceInfo> loadSpaceInfo(List<TopicResult> topics,
            List<MeetingCandidateEntry> meetingEntries) {
        Set<Long> spaceIds = new java.util.LinkedHashSet<>();
        for (TopicResult topic : topics) {
            spaceIds.add(topic.esTopicSpaceId());
        }
        for (MeetingCandidateEntry entry : meetingEntries) {
            spaceIds.add(entry.esTopicSpaceId);
        }
        return dataSource.findSpaceInfoByIds(spaceIds);
    }

    private enum MatchSource {
        TITLE, AGENDA, DESCRIPTION, OUTCOME, NOTE
    }

    /** Accumulates every candidate match found for one meeting before the strongest is chosen. */
    private static final class MeetingCandidateEntry {
        private final Long esMeetingId;
        private final String meetingName;
        private final Long esTopicSpaceId;
        private final LocalDateTime scheduledStart;
        private final String timezoneId;
        private final List<Match> matches = new ArrayList<>();

        private record Match(int tier, MatchSource source, String text) {
        }

        MeetingCandidateEntry(Long esMeetingId, String meetingName, Long esTopicSpaceId,
                LocalDateTime scheduledStart, String timezoneId) {
            this.esMeetingId = esMeetingId;
            this.meetingName = meetingName;
            this.esTopicSpaceId = esTopicSpaceId;
            this.scheduledStart = scheduledStart;
            this.timezoneId = timezoneId;
        }

        void addMatch(int tier, MatchSource source, String text) {
            matches.add(new Match(tier, source, text));
        }

        MeetingResult toResult(String query, EsSearchDao.SpaceInfo spaceInfo, int excerptLength) {
            Match strongest = matches.stream().min(Comparator.comparingInt(Match::tier)).orElseThrow();

            String matchLabel = null;
            String matchDetailHtml = null;
            if (strongest.source() != MatchSource.TITLE) {
                matchLabel = switch (strongest.source()) {
                    case AGENDA -> "Agenda match";
                    case DESCRIPTION -> "Description match";
                    case OUTCOME -> "Outcome match";
                    case NOTE -> "Notes match";
                    case TITLE -> null;
                };
                matchDetailHtml = SearchTextUtil.excerptAndHighlight(strongest.text(), query, excerptLength);
            }

            Map<MatchSource, Long> countsBySource = matches.stream()
                    .collect(java.util.stream.Collectors.groupingBy(Match::source, java.util.stream.Collectors.counting()));
            countsBySource.merge(strongest.source(), -1L, Long::sum);
            String additionalMatchSummary = summarizeAdditionalMatches(countsBySource);

            String titleHtml = SearchTextUtil.highlightFull(meetingName, query);
            return new MeetingResult(esMeetingId, meetingName, titleHtml, esTopicSpaceId,
                    spaceInfo == null ? null : spaceInfo.spaceName(), scheduledStart, timezoneId, matchLabel,
                    matchDetailHtml, additionalMatchSummary, strongest.tier());
        }

        private String summarizeAdditionalMatches(Map<MatchSource, Long> countsBySource) {
            List<String> parts = new ArrayList<>();
            appendCount(parts, countsBySource.get(MatchSource.AGENDA), "agenda item", "agenda items");
            appendCount(parts, countsBySource.get(MatchSource.OUTCOME), "outcome", "outcomes");
            appendCount(parts, countsBySource.get(MatchSource.NOTE), "note", "notes");
            if (parts.isEmpty()) {
                return null;
            }
            return "Matches " + String.join(" and ", parts);
        }

        private void appendCount(List<String> parts, Long count, String singular, String plural) {
            if (count == null || count <= 0) {
                return;
            }
            parts.add(count + " " + (count == 1 ? singular : plural));
        }
    }

    public record Results(List<TopicResult> topics, List<MeetingResult> upcomingMeetings,
            List<MeetingResult> previousMeetings) {
    }

    public record TopicResult(Long esTopicId, String topicName, String titleHtml, String stage, Long esTopicSpaceId,
            String spaceName, String summaryHtml, int rankTier, LocalDateTime updatedAt) {

        TopicResult withSpace(EsSearchDao.SpaceInfo spaceInfo) {
            return new TopicResult(esTopicId, topicName, titleHtml, stage, esTopicSpaceId,
                    spaceInfo == null ? null : spaceInfo.spaceName(), summaryHtml, rankTier, updatedAt);
        }
    }

    public record MeetingResult(Long esMeetingId, String meetingName, String titleHtml, Long esTopicSpaceId,
            String spaceName, LocalDateTime scheduledStart, String timezoneId, String matchLabel,
            String matchDetailHtml, String additionalMatchSummary, int rankTier) {
    }

    interface SearchDataSource {
        List<EsSearchDao.TopicCandidate> findTopicCandidates(Set<Long> visibleSpaceIds, String likeQuery, int limit);

        List<EsSearchDao.TitleMatchCandidate> findMeetingTitleCandidates(Set<Long> visibleSpaceIds, String likeQuery,
                int limit);

        List<EsSearchDao.ContentMatchCandidate> findMeetingDescriptionCandidates(Set<Long> visibleSpaceIds,
                String likeQuery, int limit);

        List<EsSearchDao.ContentMatchCandidate> findAgendaTitleCandidates(Set<Long> visibleSpaceIds,
                String likeQuery, int limit);

        List<EsSearchDao.ContentMatchCandidate> findAgendaDescriptionCandidates(Set<Long> visibleSpaceIds,
                String likeQuery, int limit);

        List<EsSearchDao.ContentMatchCandidate> findOutcomeCandidates(Set<Long> visibleSpaceIds, String likeQuery,
                int limit);

        List<EsSearchDao.ContentMatchCandidate> findNoteCandidates(Set<Long> visibleSpaceIds, String likeQuery,
                int limit);

        Map<Long, EsSearchDao.SpaceInfo> findSpaceInfoByIds(Set<Long> spaceIds);
    }

    static final class DaoSearchDataSource implements SearchDataSource {
        private final EsSearchDao dao;

        DaoSearchDataSource(EsSearchDao dao) {
            this.dao = dao;
        }

        @Override
        public List<EsSearchDao.TopicCandidate> findTopicCandidates(Set<Long> visibleSpaceIds, String likeQuery,
                int limit) {
            return dao.findTopicCandidates(visibleSpaceIds, likeQuery, limit);
        }

        @Override
        public List<EsSearchDao.TitleMatchCandidate> findMeetingTitleCandidates(Set<Long> visibleSpaceIds,
                String likeQuery, int limit) {
            return dao.findMeetingTitleCandidates(visibleSpaceIds, likeQuery, limit);
        }

        @Override
        public List<EsSearchDao.ContentMatchCandidate> findMeetingDescriptionCandidates(Set<Long> visibleSpaceIds,
                String likeQuery, int limit) {
            return dao.findMeetingDescriptionCandidates(visibleSpaceIds, likeQuery, limit);
        }

        @Override
        public List<EsSearchDao.ContentMatchCandidate> findAgendaTitleCandidates(Set<Long> visibleSpaceIds,
                String likeQuery, int limit) {
            return dao.findAgendaTitleCandidates(visibleSpaceIds, likeQuery, limit);
        }

        @Override
        public List<EsSearchDao.ContentMatchCandidate> findAgendaDescriptionCandidates(Set<Long> visibleSpaceIds,
                String likeQuery, int limit) {
            return dao.findAgendaDescriptionCandidates(visibleSpaceIds, likeQuery, limit);
        }

        @Override
        public List<EsSearchDao.ContentMatchCandidate> findOutcomeCandidates(Set<Long> visibleSpaceIds,
                String likeQuery, int limit) {
            return dao.findOutcomeCandidates(visibleSpaceIds, likeQuery, limit);
        }

        @Override
        public List<EsSearchDao.ContentMatchCandidate> findNoteCandidates(Set<Long> visibleSpaceIds,
                String likeQuery, int limit) {
            return dao.findNoteCandidates(visibleSpaceIds, likeQuery, limit);
        }

        @Override
        public Map<Long, EsSearchDao.SpaceInfo> findSpaceInfoByIds(Set<Long> spaceIds) {
            return dao.findSpaceInfoByIds(spaceIds);
        }
    }
}
