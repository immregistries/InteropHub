package org.airahub.interophub.service;

import java.util.List;

/**
 * Short status next to a checklist item. A hint never changes the item's status.
 * A code this phase does not know, including the Small Group checks, shows nothing.
 */
public final class ChecklistHints {

    public record Facts(InitiativeRosterRules.Representation representation, int organizationsNeedingContact,
            boolean topicHasChampion, int joinedMembersMissingFollow) {
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
            default -> null;
        };
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
        return List.of("org-representation", "orgs-need-contact", "topic-champion", "members-follow-primary-topic");
    }
}
