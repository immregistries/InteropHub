package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChecklistHintsTest {

    @Test
    void eachInitiativeCheck() {
        ChecklistHints.Facts facts = new ChecklistHints.Facts(
                InitiativeRosterRules.summarize(List.of("CONSULTANT", "JURISDICTION", "EHR")),
                2, true, 1);
        assertEquals("3 committed organizations. Jurisdiction: yes. At least 3: yes.",
                ChecklistHints.hint("org-representation", facts));
        assertEquals("2 interested or committed organizations need a contact.",
                ChecklistHints.hint("orgs-need-contact", facts));
        assertEquals("The primary topic has a Champion.", ChecklistHints.hint("topic-champion", facts));
        assertEquals("1 joined member doesn't follow the primary topic.",
                ChecklistHints.hint("members-follow-primary-topic", facts));
    }

    @Test
    void clearChecksAndUnknownCodes() {
        ChecklistHints.Facts facts = new ChecklistHints.Facts(
                InitiativeRosterRules.summarize(List.of("CONSULTANT")), 0, false, 0);
        assertEquals("1 committed organization. Jurisdiction: not yet. At least 3: not yet.",
                ChecklistHints.hint("org-representation", facts));
        assertEquals("Interested and committed organizations have a joined contact.",
                ChecklistHints.hint("orgs-need-contact", facts));
        assertEquals("The primary topic has no Champion.", ChecklistHints.hint("topic-champion", facts));
        assertEquals("Joined members follow the primary topic.",
                ChecklistHints.hint("members-follow-primary-topic", facts));
        assertNull(ChecklistHints.hint("series-exists", facts));
        assertNull(ChecklistHints.hint("notes-finalized", facts));
        assertNull(ChecklistHints.hint("not-a-check", facts));
        assertNull(ChecklistHints.hint(null, facts));
    }
}
