package org.airahub.interophub.service;

import java.util.List;

/**
 * Short status next to a checklist item. A hint never changes the item's status.
 * A code InteropHub does not know shows nothing.
 */
public final class ChecklistHints {

    /**
     * Counts for the Small Group checks. Organization and primary-topic checks
     * stay on {@link Facts}; the caller scopes those counts before calling.
     */
    public record GroupSignals(boolean topicHasActiveSeries, boolean seriesSelected, int unregisteredParticipants,
            int participantsMissingFollow, boolean cycleDatesSet, int meetingsInCycle, int agendaItems,
            int agendaItemsLinked, int meetingsMissingNotes, boolean orientationBundle,
            boolean starterPacketDuringCycle) {
    }

    public record Facts(InitiativeRosterRules.Representation representation, int organizationsNeedingContact,
            boolean topicHasChampion, int joinedMembersMissingFollow, GroupSignals group) {

        public Facts(InitiativeRosterRules.Representation representation, int organizationsNeedingContact,
                boolean topicHasChampion, int joinedMembersMissingFollow) {
            this(representation, organizationsNeedingContact, topicHasChampion, joinedMembersMissingFollow, null);
        }
    }

    private ChecklistHints() {
    }

    public static String hint(String checkCode, Facts facts) {
        if (checkCode == null || facts == null) {
            return null;
        }
        return switch (checkCode) {
            case "org-representation" -> representation(facts.representation());
            case "orgs-need-contact" -> organizations(facts.organizationsNeedingContact());
            case "topic-champion" -> facts.topicHasChampion()
                    ? "The primary topic has a Champion."
                    : "The primary topic has no Champion.";
            case "members-follow-primary-topic" -> facts.joinedMembersMissingFollow() == 0
                    ? "Joined members follow the primary topic."
                    : facts.joinedMembersMissingFollow() + " joined "
                            + (facts.joinedMembersMissingFollow() == 1 ? "member doesn't" : "members don't")
                            + " follow the primary topic.";
            case "series-exists", "members-registered-for-series", "members-follow-topic", "meetings-scheduled",
                    "agenda-topics-linked", "notes-finalized", "orientation-bundle", "technical-bundle" ->
                    groupHint(checkCode, facts.group());
            default -> null;
        };
    }

    private static String groupHint(String checkCode, GroupSignals group) {
        if (group == null) {
            return null;
        }
        return switch (checkCode) {
            case "series-exists" -> group.topicHasActiveSeries()
                    ? "This topic has an active meeting series."
                    : "This topic has no active meeting series.";
            case "members-registered-for-series" -> registration(group);
            case "members-follow-topic" -> group.participantsMissingFollow() == 0
                    ? "Participants with an email follow the Small Group topic."
                    : group.participantsMissingFollow() + " "
                            + (group.participantsMissingFollow() == 1
                                    ? "participant with an email doesn't"
                                    : "participants with an email don't")
                            + " follow the Small Group topic.";
            case "meetings-scheduled" -> meetings(group);
            case "agenda-topics-linked" -> agendas(group);
            case "notes-finalized" -> notes(group);
            case "orientation-bundle" -> group.orientationBundle()
                    ? "The topic has a Topic Orientation bundle."
                    : "The topic has no Topic Orientation bundle.";
            case "technical-bundle" -> technical(group);
            default -> null;
        };
    }

    private static String registration(GroupSignals group) {
        if (!group.seriesSelected()) {
            return "No meeting series is selected on this Small Group.";
        }
        if (group.unregisteredParticipants() == 0) {
            return "Every participant is registered for the series.";
        }
        return group.unregisteredParticipants() + " "
                + (group.unregisteredParticipants() == 1 ? "participant is" : "participants are")
                + " not registered for the series.";
    }

    private static String meetings(GroupSignals group) {
        if (!group.seriesSelected()) {
            return "No meeting series is selected on this Small Group.";
        }
        if (!group.cycleDatesSet()) {
            return "Set the cycle start and closeout to check scheduled meetings.";
        }
        if (group.meetingsInCycle() == 0) {
            return "No meetings are scheduled between the cycle start and closeout.";
        }
        return group.meetingsInCycle() + " "
                + (group.meetingsInCycle() == 1 ? "meeting is" : "meetings are") + " scheduled in this cycle.";
    }

    private static String agendas(GroupSignals group) {
        if (!group.seriesSelected()) {
            return "No meeting series is selected on this Small Group.";
        }
        if (!group.cycleDatesSet()) {
            return "Set the cycle start and closeout to check agendas.";
        }
        if (group.agendaItems() == 0) {
            return "Cycle meetings have no agenda items.";
        }
        return group.agendaItemsLinked() + " of " + group.agendaItems() + " agenda "
                + (group.agendaItems() == 1 ? "item is" : "items are") + " linked to a topic.";
    }

    private static String notes(GroupSignals group) {
        if (!group.seriesSelected()) {
            return "No meeting series is selected on this Small Group.";
        }
        if (!group.cycleDatesSet()) {
            return "Set the cycle dates to check meeting notes.";
        }
        if (group.meetingsMissingNotes() == 0) {
            return "Cycle meetings more than 7 days old have published notes.";
        }
        return group.meetingsMissingNotes() + " cycle "
                + (group.meetingsMissingNotes() == 1 ? "meeting" : "meetings")
                + " more than 7 days old " + (group.meetingsMissingNotes() == 1 ? "has" : "have")
                + " no published notes.";
    }

    private static String technical(GroupSignals group) {
        if (!group.cycleDatesSet()) {
            return "Set the cycle dates to check the Starter Packet.";
        }
        return group.starterPacketDuringCycle()
                ? "A Starter Packet was published for this topic during the cycle."
                : "No Starter Packet was published for this topic during the cycle.";
    }

    private static String representation(InitiativeRosterRules.Representation representation) {
        if (representation == null) {
            return null;
        }
        int count = representation.committedOrganizations();
        return count + " committed " + (count == 1 ? "organization" : "organizations")
                + ". Jurisdiction: " + (representation.jurisdictionPresent() ? "yes" : "not yet")
                + ". At least 3: " + (representation.atLeastThreeOrganizations() ? "yes" : "not yet") + ".";
    }

    private static String organizations(int needingContact) {
        if (needingContact <= 0) {
            return "Interested and committed organizations have a joined contact.";
        }
        return needingContact + " interested or committed "
                + (needingContact == 1 ? "organization needs" : "organizations need") + " a contact.";
    }

    public static List<String> knownCodes() {
        return List.of("org-representation", "orgs-need-contact", "topic-champion", "members-follow-primary-topic",
                "series-exists", "members-registered-for-series", "members-follow-topic", "meetings-scheduled",
                "agenda-topics-linked", "notes-finalized", "orientation-bundle", "technical-bundle");
    }
}
