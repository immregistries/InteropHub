package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.airahub.interophub.dao.EsSearchDao;
import org.junit.jupiter.api.Test;

class SearchServiceTest {

    private static final Set<Long> SPACE_1 = Set.of(1L);
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 6, 12, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            FIXED_NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @Test
    void blankQueryReturnsEmptyResults() {
        SearchService service = new SearchService(new FakeDataSource(), FIXED_CLOCK);
        SearchService.Results results = service.search("  ", SPACE_1);
        assertTrue(results.topics().isEmpty());
        assertTrue(results.upcomingMeetings().isEmpty());
        assertTrue(results.previousMeetings().isEmpty());
    }

    @Test
    void noVisibleSpacesReturnsEmptyResultsEvenWithMatchingContent() {
        FakeDataSource fake = new FakeDataSource();
        fake.topics.add(new EsSearchDao.TopicCandidate(1L, "Certificate Management", "Summary", "Description",
                null, "Gathering Information", 1L, FIXED_NOW));
        SearchService service = new SearchService(fake, FIXED_CLOCK);

        SearchService.Results results = service.search("certificate", Set.of());
        assertTrue(results.topics().isEmpty());
    }

    @Test
    void topicRankingPrefersExactOverPrefixOverContainsOverKeywordOverSummaryOverDescription() {
        FakeDataSource fake = new FakeDataSource();
        fake.topics.add(new EsSearchDao.TopicCandidate(1L, "Certificate", "n/a", "n/a", null, null, 1L, FIXED_NOW));
        fake.topics.add(new EsSearchDao.TopicCandidate(2L, "Certificate Renewal", "n/a", "n/a", null, null, 1L,
                FIXED_NOW));
        fake.topics.add(new EsSearchDao.TopicCandidate(3L, "Renewal Certificate Process", "n/a", "n/a", null, null,
                1L, FIXED_NOW));
        fake.topics.add(new EsSearchDao.TopicCandidate(4L, "Vaccine Records", "n/a", "n/a", "certificate, cert-mgmt",
                null, 1L, FIXED_NOW));
        fake.topics.add(new EsSearchDao.TopicCandidate(5L, "Immunization Focus", "About certificate handling", "n/a",
                null, null, 1L, FIXED_NOW));
        fake.topics.add(new EsSearchDao.TopicCandidate(6L, "Data Quality", "n/a",
                "Long description mentioning certificate somewhere in the body", null, null, 1L, FIXED_NOW));
        fake.spaces.put(1L, new EsSearchDao.SpaceInfo(1L, "Emerging Standards", "emerging-standards"));

        SearchService service = new SearchService(fake, FIXED_CLOCK);
        List<SearchService.TopicResult> topics = service.search("certificate", SPACE_1).topics();

        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L), topics.stream().map(SearchService.TopicResult::esTopicId).toList());
        assertEquals("Emerging Standards", topics.get(0).spaceName());
    }

    @Test
    void meetingRankingPrefersTitleOverAgendaOverDescriptionOverOutcomeOverNote() {
        FakeDataSource fake = new FakeDataSource();
        fake.titleMatches.add(new EsSearchDao.TitleMatchCandidate(1L, "Certificate Sync", "Certificate Sync", 1L,
                FIXED_NOW.plusDays(1), "America/Denver"));
        fake.agendaTitleMatches.add(new EsSearchDao.ContentMatchCandidate(2L, "Focus Group", 1L,
                FIXED_NOW.plusDays(1), "America/Denver", "Certificate Management"));
        fake.descriptionMatches.add(new EsSearchDao.ContentMatchCandidate(3L, "Planning Session", 1L,
                FIXED_NOW.plusDays(1), "America/Denver", "Discusses certificate policy"));
        fake.outcomeMatches.add(new EsSearchDao.ContentMatchCandidate(4L, "Review Meeting", 1L,
                FIXED_NOW.plusDays(1), "America/Denver", "Certificate renewal approved"));
        fake.noteMatches.add(new EsSearchDao.ContentMatchCandidate(5L, "Retro", 1L, FIXED_NOW.plusDays(1),
                "America/Denver", "Some notes mention certificate handling"));

        SearchService service = new SearchService(fake, FIXED_CLOCK);
        List<SearchService.MeetingResult> upcoming = service.search("certificate", SPACE_1).upcomingMeetings();

        assertEquals(List.of(1L, 2L, 3L, 4L, 5L),
                upcoming.stream().map(SearchService.MeetingResult::esMeetingId).toList());
        assertEquals(null, upcoming.get(0).matchLabel(), "title match needs no explanation");
        assertEquals("Agenda match", upcoming.get(1).matchLabel());
        assertEquals("Description match", upcoming.get(2).matchLabel());
        assertEquals("Outcome match", upcoming.get(3).matchLabel());
        assertEquals("Notes match", upcoming.get(4).matchLabel());
    }

    @Test
    void exactMeetingTitleOutranksTitleThatOnlyContainsQuery() {
        FakeDataSource fake = new FakeDataSource();
        fake.titleMatches.add(new EsSearchDao.TitleMatchCandidate(1L, "Certificate Management Working Group",
                "Certificate Management Working Group", 1L, FIXED_NOW.plusDays(1), null));
        fake.titleMatches.add(new EsSearchDao.TitleMatchCandidate(2L, "Certificate Management", "Certificate Management",
                1L, FIXED_NOW.plusDays(2), null));

        SearchService service = new SearchService(fake, FIXED_CLOCK);
        List<SearchService.MeetingResult> upcoming = service.search("Certificate Management", SPACE_1)
                .upcomingMeetings();

        assertEquals(2L, upcoming.get(0).esMeetingId(), "exact title match should rank first despite later date");
        assertEquals(1, upcoming.get(0).rankTier());
        assertEquals(2, upcoming.get(1).rankTier());
    }

    @Test
    void aMeetingMatchingMultipleSourcesAppearsOnceWithStrongestMatchAndASummaryOfTheRest() {
        FakeDataSource fake = new FakeDataSource();
        fake.agendaTitleMatches.add(new EsSearchDao.ContentMatchCandidate(1L, "Focus Group", 1L,
                FIXED_NOW.plusDays(1), null, "Certificate Management"));
        fake.agendaTitleMatches.add(new EsSearchDao.ContentMatchCandidate(1L, "Focus Group", 1L,
                FIXED_NOW.plusDays(1), null, "Certificate Renewal"));
        fake.noteMatches.add(new EsSearchDao.ContentMatchCandidate(1L, "Focus Group", 1L, FIXED_NOW.plusDays(1),
                null, "Certificate renewal responsibilities"));
        fake.noteMatches.add(new EsSearchDao.ContentMatchCandidate(1L, "Focus Group", 1L, FIXED_NOW.plusDays(1),
                null, "More certificate notes"));
        fake.noteMatches.add(new EsSearchDao.ContentMatchCandidate(1L, "Focus Group", 1L, FIXED_NOW.plusDays(1),
                null, "Even more certificate notes"));

        SearchService service = new SearchService(fake, FIXED_CLOCK);
        List<SearchService.MeetingResult> upcoming = service.search("certificate", SPACE_1).upcomingMeetings();

        assertEquals(1, upcoming.size(), "one meeting, matched via 5 different rows, must appear once");
        assertEquals("Agenda match", upcoming.get(0).matchLabel());
        assertEquals("Matches 1 agenda item and 3 notes", upcoming.get(0).additionalMatchSummary());
    }

    @Test
    void splitsMeetingsIntoUpcomingAscendingAndPreviousDescendingByDate() {
        FakeDataSource fake = new FakeDataSource();
        fake.titleMatches.add(new EsSearchDao.TitleMatchCandidate(1L, "Certificate Sync", "Certificate Sync", 1L,
                FIXED_NOW.plusDays(5), null));
        fake.titleMatches.add(new EsSearchDao.TitleMatchCandidate(2L, "Certificate Sync", "Certificate Sync", 1L,
                FIXED_NOW.plusDays(1), null));
        fake.titleMatches.add(new EsSearchDao.TitleMatchCandidate(3L, "Certificate Sync", "Certificate Sync", 1L,
                FIXED_NOW.minusDays(1), null));
        fake.titleMatches.add(new EsSearchDao.TitleMatchCandidate(4L, "Certificate Sync", "Certificate Sync", 1L,
                FIXED_NOW.minusDays(5), null));

        SearchService service = new SearchService(fake, FIXED_CLOCK);
        SearchService.Results results = service.search("Certificate Sync", SPACE_1);

        assertEquals(List.of(2L, 1L), results.upcomingMeetings().stream()
                .map(SearchService.MeetingResult::esMeetingId).toList());
        assertEquals(List.of(3L, 4L), results.previousMeetings().stream()
                .map(SearchService.MeetingResult::esMeetingId).toList());
    }

    @Test
    void meetingTitleAndTopicSummaryAreHighlightedWithMarkTags() {
        FakeDataSource fake = new FakeDataSource();
        fake.topics.add(new EsSearchDao.TopicCandidate(1L, "Certificate Management", "About certificates", null,
                null, null, 1L, FIXED_NOW));

        SearchService service = new SearchService(fake, FIXED_CLOCK);
        SearchService.TopicResult topic = service.search("certificate", SPACE_1).topics().get(0);

        assertTrue(topic.titleHtml().contains("<mark>Certificate</mark>"));
        assertTrue(topic.summaryHtml().contains("<mark>"));
    }

    private static final class FakeDataSource implements SearchService.SearchDataSource {
        private final List<EsSearchDao.TopicCandidate> topics = new ArrayList<>();
        private final List<EsSearchDao.TitleMatchCandidate> titleMatches = new ArrayList<>();
        private final List<EsSearchDao.ContentMatchCandidate> descriptionMatches = new ArrayList<>();
        private final List<EsSearchDao.ContentMatchCandidate> agendaTitleMatches = new ArrayList<>();
        private final List<EsSearchDao.ContentMatchCandidate> agendaDescriptionMatches = new ArrayList<>();
        private final List<EsSearchDao.ContentMatchCandidate> outcomeMatches = new ArrayList<>();
        private final List<EsSearchDao.ContentMatchCandidate> noteMatches = new ArrayList<>();
        private final Map<Long, EsSearchDao.SpaceInfo> spaces = new java.util.HashMap<>();

        @Override
        public List<EsSearchDao.TopicCandidate> findTopicCandidates(Set<Long> visibleSpaceIds, String likeQuery,
                int limit) {
            return topics;
        }

        @Override
        public List<EsSearchDao.TitleMatchCandidate> findMeetingTitleCandidates(Set<Long> visibleSpaceIds,
                String likeQuery, int limit) {
            return titleMatches;
        }

        @Override
        public List<EsSearchDao.ContentMatchCandidate> findMeetingDescriptionCandidates(Set<Long> visibleSpaceIds,
                String likeQuery, int limit) {
            return descriptionMatches;
        }

        @Override
        public List<EsSearchDao.ContentMatchCandidate> findAgendaTitleCandidates(Set<Long> visibleSpaceIds,
                String likeQuery, int limit) {
            return agendaTitleMatches;
        }

        @Override
        public List<EsSearchDao.ContentMatchCandidate> findAgendaDescriptionCandidates(Set<Long> visibleSpaceIds,
                String likeQuery, int limit) {
            return agendaDescriptionMatches;
        }

        @Override
        public List<EsSearchDao.ContentMatchCandidate> findOutcomeCandidates(Set<Long> visibleSpaceIds,
                String likeQuery, int limit) {
            return outcomeMatches;
        }

        @Override
        public List<EsSearchDao.ContentMatchCandidate> findNoteCandidates(Set<Long> visibleSpaceIds,
                String likeQuery, int limit) {
            return noteMatches;
        }

        @Override
        public Map<Long, EsSearchDao.SpaceInfo> findSpaceInfoByIds(Set<Long> spaceIds) {
            return spaces;
        }
    }
}
