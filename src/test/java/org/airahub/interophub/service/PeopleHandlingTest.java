package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import org.airahub.interophub.model.EsInitiativeMember;
import org.airahub.interophub.model.EsInitiativeTimelineEntry;
import org.junit.jupiter.api.Test;

class PeopleHandlingTest {

    @Test
    void displayUsesTheAccountNameThenTheRosterNameThenTheEmail() {
        assertEquals("Kevin Snow", PersonNames.display("Kevin Snow", null, null, "ksnow@envisiontechnology.com",
                null, "ksnow@envisiontechnology.com"));
        assertEquals("Kevin Snow", PersonNames.display(null, "Kevin", "Snow", null, null,
                "ksnow@envisiontechnology.com"));
        assertEquals("Ada Lovelace", PersonNames.display(null, null, null, "Ada", "Lovelace", "ada@example.org"));
        assertEquals("ada@example.org", PersonNames.display(null, null, null, "ada@example.org", null, "ada@example.org"));
        assertEquals("Unnamed contact", PersonNames.display(null, null, null, null, null, " "));
    }

    @Test
    void accountAndFollowNamesFillBlankRosterFieldsAndNeverKeepAnEmail() {
        PersonNames.Names account = PersonNames.accountNames("ksnow@envisiontechnology.com", "Kevin", "Snow");
        assertEquals("Kevin", account.firstName());
        assertEquals("Snow", account.lastName());
        PersonNames.Names fromDisplay = PersonNames.accountNames("Kevin Snow", null, null);
        assertEquals("Kevin", fromDisplay.firstName());
        assertEquals("Snow", fromDisplay.lastName());

        PersonNames.Names filled = PersonNames.fillBlanks("ada@example.org", null, "Ada", "Lovelace");
        assertEquals("Ada", filled.firstName());
        assertEquals("Lovelace", filled.lastName());
        PersonNames.Names cleared = PersonNames.fillBlanks("ada@example.org", "ada@example.org", null, null);
        assertEquals(null, cleared.firstName());
        assertEquals(null, cleared.lastName());
    }

    @Test
    void searchRanksRosterThenAccountsThenKnownEmailsAndPrefersARealName() {
        assertFalse(PeopleSearch.eligible("a"));
        assertTrue(PeopleSearch.eligible("ke"));
        List<PeopleSearch.Hit> hits = PeopleSearch.rank(List.of(
                candidate(PeopleSearch.Source.KNOWN, null, null, "ada@example.org", null, null, null, null, null, "HLN"),
                candidate(PeopleSearch.Source.ACCOUNT, null, 30L, "ksnow@envisiontechnology.com", null, "Kevin", "Snow",
                        "ksnow@envisiontechnology.com", null, "Envision"),
                candidate(PeopleSearch.Source.KNOWN, null, null, "ksnow@envisiontechnology.com", null, null, null,
                        "ksnow@envisiontechnology.com", null, null),
                candidate(PeopleSearch.Source.ROSTER, 4L, 8L, "grace@example.org", null, "Grace", "Hopper", null, null,
                        "HLN"),
                candidate(PeopleSearch.Source.KNOWN, null, null, "ada@example.org", null, null, null, "Ada", "Lovelace",
                        "HLN")));
        assertEquals(List.of(PeopleSearch.Source.ROSTER, PeopleSearch.Source.ACCOUNT, PeopleSearch.Source.KNOWN),
                hits.stream().map(PeopleSearch.Hit::source).toList());
        assertEquals("Grace Hopper", hits.get(0).name());
        assertEquals(30L, hits.get(1).userId());
        assertEquals("Kevin", hits.get(1).firstName());
        assertEquals("Snow", hits.get(1).lastName());
        assertEquals("Kevin Snow", hits.get(1).name());
        assertEquals("Ada Lovelace", hits.get(2).name());
        assertEquals("Known by email", hits.get(2).sourceLabel());
    }

    @Test
    void peopleToBringInMergesSourcesExcludesParticipantsAndFiltersApprovedOnly() {
        PeopleToBringIn.Person joined = person("bea@example.org", null, 2L, null, null, null, "Bea", "Ng",
                "bea@example.org", "IIS", "Joined", null, false);
        PeopleToBringIn.Person follower = person("ada@example.org", null, null, null, null, null, "Ada", "Lovelace",
                "ada@example.org", "HLN", null, null, true);
        PeopleToBringIn.Person requested = person("cam@example.org", 30L, null, "Cam", null, null, null, null,
                "cam@example.org", null, null, "REQUESTED", false);
        PeopleToBringIn.Person approved = person("dee@example.org", null, null, null, null, null, null, null,
                "dee@example.org", null, null, "APPROVED", false);
        PeopleToBringIn.Person already = person("bea@example.org", null, 2L, null, null, null, "Bea", "Ng",
                "bea@example.org", "IIS", null, null, false);
        List<PeopleToBringIn.Row> rows = PeopleToBringIn.assemble(List.of(joined), List.of(follower),
                List.of(requested, approved), List.of(already));
        assertEquals(List.of("Ada Lovelace", "Cam", "dee@example.org"), rows.stream().map(PeopleToBringIn.Row::name).toList());
        assertEquals("Requested", rows.get(1).registrationLabel());
        assertFalse(rows.get(1).registered());
        assertEquals("Approved", rows.get(2).registrationLabel());
        assertTrue(rows.get(2).registered());
        assertTrue(rows.get(0).follows());
        assertFalse(rows.get(0).onInitiative());

        PeopleToBringIn.Page registered = PeopleToBringIn.page(rows, "registered", 1);
        assertEquals(1, registered.filteredTotal());
        assertEquals(3, registered.total());
        assertEquals(1, registered.registeredCount());
        assertEquals(1, registered.followsCount());
        assertEquals("dee@example.org", registered.rows().get(0).name());
        assertEquals(1, PeopleToBringIn.page(rows, "follows", 1).filteredTotal());
        assertEquals(0, PeopleToBringIn.page(rows, "initiative", 1).filteredTotal());
    }

    @Test
    void addingSomeoneWhoIsNotOnTheRosterJoinsThemFromTheSmallGroup() {
        assertTrue(SmallGroupParticipantRules.needsJoined(null));
        assertEquals("Added from a Small Group", EsInitiativeMember.MemberSource.SMALL_GROUP.getLabel());
        SmallGroupParticipantRules.AddPlan plan = SmallGroupParticipantRules.planAdd(
                null, "Fun Group First", 9L, "pat@example.org", "pat@example.org", null, null, null);
        assertTrue(plan.markJoined());
        assertEquals(null, plan.topicFollow().orElseThrow().firstName());
    }

    @Test
    void theTimelineIsOneLineAndShowsTheLatestFive() {
        LocalDateTime at = LocalDateTime.of(2026, 10, 30, 9, 0);
        assertEquals("Oct 30 · Nathan Bunker · Planning → Recruiting: \"note\"",
                InitiativeTimelineText.line(at, "Nathan Bunker", EsInitiativeTimelineEntry.EntryType.STATUS_CHANGE,
                        "Planning", "Recruiting", "note", false));
        assertEquals("Oct 30 · Nathan Bunker · Ready for review (edited)",
                InitiativeTimelineText.line(at, "Nathan Bunker", EsInitiativeTimelineEntry.EntryType.COMMENT,
                        null, null, "Ready for review", true));
        assertEquals(0, InitiativeTimelineText.firstVisible(5, false));
        assertEquals(3, InitiativeTimelineText.firstVisible(8, false));
        assertEquals(0, InitiativeTimelineText.firstVisible(8, true));
    }

    private static PeopleSearch.Candidate candidate(PeopleSearch.Source source, Long memberId, Long userId,
            String email, String accountDisplay, String accountFirst, String accountLast, String first, String last,
            String organization) {
        return new PeopleSearch.Candidate(source, memberId, userId, email, accountDisplay, accountFirst, accountLast,
                first, last, organization);
    }

    private static PeopleToBringIn.Person person(String emailNormalized, Long userId, Long memberId,
            String accountDisplay, String accountFirst, String accountLast, String first, String last, String email,
            String organization, String initiativeStatus, String registrationStatus, boolean follows) {
        return new PeopleToBringIn.Person(emailNormalized, userId, memberId, accountDisplay, accountFirst, accountLast,
                first, last, email, organization, initiativeStatus, registrationStatus, follows);
    }
}
