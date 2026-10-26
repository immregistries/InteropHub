package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.airahub.interophub.model.EsSubscription;
import org.junit.jupiter.api.Test;

class SmallGroupRulesTest {

    @Test
    void addingSomeoneWhoHasNotJoinedMarksThemJoinedAndFollowsTheGroupTopic() {
        SmallGroupParticipantRules.AddPlan plan = SmallGroupParticipantRules.planAdd(
                "INVITED", "Packet group", 9L, "ada@example.org", "Ada", "Lovelace", "IIS", null);
        assertTrue(plan.markJoined());
        assertTrue(plan.topicFollow().isPresent());
        InitiativeFollowRules.FollowRequest follow = plan.topicFollow().get();
        assertEquals(9L, follow.topicId());
        assertEquals("ada@example.org", follow.email());
        assertEquals("Taking part in the Packet group small group.", follow.reason());
        assertFalse(follow.sendNotification());
    }

    @Test
    void someoneAlreadyJoinedStillFollowsTheSmallGroupTopic() {
        SmallGroupParticipantRules.AddPlan plan = SmallGroupParticipantRules.planAdd(
                "JOINED", "Packet group", 9L, "ada@example.org", "Ada", "Lovelace", "IIS", null);
        assertFalse(plan.markJoined());
        assertTrue(plan.topicFollow().isPresent());
    }

    @Test
    void anExistingFollowIsLeftAloneAndABlankEmailDoesNotFollow() {
        SmallGroupParticipantRules.AddPlan subscribed = SmallGroupParticipantRules.planAdd(
                "JOINED", "Packet group", 9L, "ada@example.org", "Ada", "Lovelace", "IIS",
                EsSubscription.SubscriptionStatus.SUBSCRIBED);
        assertTrue(subscribed.topicFollow().isEmpty());

        Optional<InitiativeFollowRules.FollowRequest> account = SmallGroupParticipantRules.topicFollow(
                "Packet group", 9L, "ada@example.org", "Ada", "Lovelace", "IIS", null);
        assertTrue(account.isPresent());

        SmallGroupParticipantRules.AddPlan noEmail = SmallGroupParticipantRules.planAdd(
                "PROSPECT", "Packet group", 9L, "  ", "Pat", "Noemail", null, null);
        assertTrue(noEmail.markJoined());
        assertTrue(noEmail.topicFollow().isEmpty());
    }

    @Test
    void removingAParticipantDoesNotUnfollow() {
        assertFalse(SmallGroupParticipantRules.unfollowOnRemove());
        SmallGroupParticipantRules.AddPlan plan = SmallGroupParticipantRules.planRemove();
        assertFalse(plan.markJoined());
        assertTrue(plan.topicFollow().isEmpty());
    }

    @Test
    void copyingSkipsPeopleAlreadyActiveOnTheNewGroup() {
        assertEquals(List.of(2L, 3L),
                SmallGroupParticipantRules.memberIdsToCopy(Set.of(1L), Arrays.asList(1L, 2L, 2L, 3L, null)));
    }

    @Test
    void seriesComparisonTreatsRequestedAndApprovedAsRegistered() {
        SmallGroupSeriesComparison.Person registered = new SmallGroupSeriesComparison.Person(1L, "ada@example.org", 10L);
        SmallGroupSeriesComparison.Person missing = new SmallGroupSeriesComparison.Person(2L, "bea@example.org", null);
        SmallGroupSeriesComparison.Person byAccount = new SmallGroupSeriesComparison.Person(3L, null, 30L);
        List<SmallGroupSeriesComparison.Registrant> registrants = List.of(
                new SmallGroupSeriesComparison.Registrant("ada@example.org", 10L, "REQUESTED", "ada@example.org", "Ada"),
                new SmallGroupSeriesComparison.Registrant("gone@example.org", null, "DECLINED", "gone@example.org", "Gone"),
                new SmallGroupSeriesComparison.Registrant(null, 30L, "APPROVED", "cam@example.org", "Cam"),
                new SmallGroupSeriesComparison.Registrant("dee@example.org", 40L, "APPROVED", "dee@example.org", "Dee"));
        SmallGroupSeriesComparison.Result result = SmallGroupSeriesComparison.compare(
                List.of(registered, missing, byAccount), registrants);
        assertEquals(List.of(missing), result.participantsNotRegistered());
        assertEquals(1, result.registrantsNotParticipants().size());
        assertEquals("dee@example.org", result.registrantsNotParticipants().get(0).email());
        assertEquals("/registerForMeeting/fall/packet%20group",
                SmallGroupSeriesComparison.registrationPath("fall", "packet group"));
        assertEquals("Bea\nhttp://localhost:8080/hub/registerForMeeting/fall/packet",
                SmallGroupSeriesComparison.copyText("Bea", "http://localhost:8080/hub/registerForMeeting/fall/packet"));
    }

    @Test
    void organizationHintsCountOnlyThisSmallGroupsParticipants() {
        List<SmallGroupParticipantRules.OrgRow> organizations = List.of(
                new SmallGroupParticipantRules.OrgRow(1L, "COMMITTED", "JURISDICTION"),
                new SmallGroupParticipantRules.OrgRow(2L, "COMMITTED", "VENDOR"),
                new SmallGroupParticipantRules.OrgRow(3L, "INTERESTED", "IIS"),
                new SmallGroupParticipantRules.OrgRow(4L, "PROSPECT", "VENDOR"));
        ChecklistHints.Facts facts = SmallGroupParticipantRules.organizationFacts(
                organizations, Set.of(1L), true, 2);
        assertEquals(1, facts.representation().committedOrganizations());
        assertTrue(facts.representation().jurisdictionPresent());
        assertFalse(facts.representation().atLeastThreeOrganizations());
        assertEquals(2, facts.organizationsNeedingContact());
        assertTrue(facts.topicHasChampion());
        assertEquals(2, facts.joinedMembersMissingFollow());
        assertEquals("The primary topic has a Champion.", ChecklistHints.hint("topic-champion", facts));
    }

    @Test
    void eachSmallGroupHintReadsTheCycleAndDoesNotMarkAnythingDone() {
        LocalDate today = LocalDate.of(2026, 10, 26);
        LocalDate cycleStart = LocalDate.of(2026, 9, 1);
        LocalDate closeout = LocalDate.of(2026, 11, 30);
        List<SmallGroupSignals.MeetingView> meetings = List.of(
                new SmallGroupSignals.MeetingView(LocalDate.of(2026, 9, 1), false, false, 2, 1),
                new SmallGroupSignals.MeetingView(LocalDate.of(2026, 10, 20), false, false, 1, 1),
                new SmallGroupSignals.MeetingView(LocalDate.of(2026, 8, 1), false, false, 4, 0),
                new SmallGroupSignals.MeetingView(LocalDate.of(2026, 9, 15), true, false, 3, 3));
        ChecklistHints.GroupSignals signals = SmallGroupSignals.summarize(true, true, 2, 1, cycleStart, closeout,
                meetings, true, false, today);
        ChecklistHints.Facts facts = new ChecklistHints.Facts(null, 0, false, 0, signals);
        assertEquals(2, signals.meetingsInCycle());
        assertEquals(3, signals.agendaItems());
        assertEquals(2, signals.agendaItemsLinked());
        assertEquals(1, signals.meetingsMissingNotes());
        assertEquals("This topic has an active meeting series.", ChecklistHints.hint("series-exists", facts));
        assertEquals("2 participants are not registered for the series.",
                ChecklistHints.hint("members-registered-for-series", facts));
        assertEquals("1 participant with an email doesn't follow the Small Group topic.",
                ChecklistHints.hint("members-follow-topic", facts));
        assertEquals("2 meetings are scheduled in this cycle.", ChecklistHints.hint("meetings-scheduled", facts));
        assertEquals("2 of 3 agenda items are linked to a topic.", ChecklistHints.hint("agenda-topics-linked", facts));
        assertEquals("1 cycle meeting more than 7 days old has no published notes.",
                ChecklistHints.hint("notes-finalized", facts));
        assertEquals("The topic has a Topic Orientation bundle.", ChecklistHints.hint("orientation-bundle", facts));
        assertEquals("No Starter Packet was published for this topic during the cycle.",
                ChecklistHints.hint("technical-bundle", facts));

        ChecklistHints.GroupSignals quiet = SmallGroupSignals.summarize(false, false, 0, 0, null, null, List.of(),
                false, false, today);
        ChecklistHints.Facts empty = new ChecklistHints.Facts(null, 0, false, 0, quiet);
        assertEquals("This topic has no active meeting series.", ChecklistHints.hint("series-exists", empty));
        assertEquals("No meeting series is selected on this Small Group.",
                ChecklistHints.hint("members-registered-for-series", empty));
        assertEquals("Participants with an email follow the Small Group topic.",
                ChecklistHints.hint("members-follow-topic", empty));
        assertEquals("No meeting series is selected on this Small Group.",
                ChecklistHints.hint("meetings-scheduled", empty));
        assertEquals("No meeting series is selected on this Small Group.",
                ChecklistHints.hint("agenda-topics-linked", empty));
        assertEquals("No meeting series is selected on this Small Group.",
                ChecklistHints.hint("notes-finalized", empty));
        assertEquals("The topic has no Topic Orientation bundle.", ChecklistHints.hint("orientation-bundle", empty));
        assertEquals("Set the cycle dates to check the Starter Packet.", ChecklistHints.hint("technical-bundle", empty));
        assertTrue(ChecklistHints.knownCodes().contains("series-exists"));
        assertTrue(ChecklistHints.knownCodes().contains("technical-bundle"));
    }
}
