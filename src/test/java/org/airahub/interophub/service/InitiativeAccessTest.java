package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.airahub.interophub.model.EsInitiativeManager;
import org.airahub.interophub.model.EsInitiativeTimelineEntry;
import org.junit.jupiter.api.Test;

class InitiativeAccessTest {

    @Test
    void globalAdminAlwaysHasFullAccess() {
        assertEquals(InitiativeAccess.Level.ADMIN,
                InitiativeAccess.level(true, null));
        assertEquals(InitiativeAccess.Level.ADMIN,
                InitiativeAccess.level(true, EsInitiativeManager.ManagerRole.VIEWER));
        assertTrue(InitiativeAccess.canManageSettings(InitiativeAccess.level(true, null)));
        assertTrue(InitiativeAccess.canEditRoster(InitiativeAccess.level(true, null)));
    }

    @Test
    void managerRolesGrantOnlyWhatTheDesignAllows() {
        InitiativeAccess.Level admin = InitiativeAccess.level(false, EsInitiativeManager.ManagerRole.ADMIN);
        InitiativeAccess.Level editor = InitiativeAccess.level(false, EsInitiativeManager.ManagerRole.EDITOR);
        InitiativeAccess.Level viewer = InitiativeAccess.level(false, EsInitiativeManager.ManagerRole.VIEWER);

        assertTrue(InitiativeAccess.canManageSettings(admin));
        assertTrue(InitiativeAccess.canEditRoster(admin));

        assertFalse(InitiativeAccess.canManageSettings(editor));
        assertTrue(InitiativeAccess.canEditRoster(editor));
        assertTrue(InitiativeAccess.canComment(editor));

        assertTrue(InitiativeAccess.canEditChecklist(admin));
        assertTrue(InitiativeAccess.canEditChecklist(editor));
        assertTrue(InitiativeAccess.canView(viewer));
        assertFalse(InitiativeAccess.canEditRoster(viewer));
        assertFalse(InitiativeAccess.canEditChecklist(viewer));
        assertFalse(InitiativeAccess.canEditSmallGroup(viewer));
        assertFalse(InitiativeAccess.canComment(viewer));
        assertFalse(InitiativeAccess.canManageSettings(viewer));
        assertTrue(InitiativeAccess.canEditSmallGroup(admin));
        assertTrue(InitiativeAccess.canEditSmallGroup(editor));
        assertTrue(InitiativeAccess.canEditSmallGroup(InitiativeAccess.level(true, null)));
        assertTrue(InitiativeAccess.canManageTemplates(true));
        assertFalse(InitiativeAccess.canManageTemplates(false));
    }

    @Test
    void someoneWithoutARoleCannotSeeTheInitiative() {
        InitiativeAccess.Level none = InitiativeAccess.level(false, null);
        assertEquals(InitiativeAccess.Level.NONE, none);
        assertFalse(InitiativeAccess.canView(none));
    }
}

class InitiativeRosterRulesTest {

    @Test
    void statusChangeKeepsTheNoteAndSameStatusBecomesAComment() {
        InitiativeRosterRules.PlannedEntry change = InitiativeRosterRules.planStatusUpdate(
                "PROSPECT", "INVITED", " Emailed to ask about participating. ");
        assertEquals(InitiativeRosterRules.PlannedKind.STATUS_CHANGE, change.kind());
        assertEquals("PROSPECT", change.fromValue());
        assertEquals("INVITED", change.toValue());
        assertEquals("Emailed to ask about participating.", change.note());

        InitiativeRosterRules.PlannedEntry comment = InitiativeRosterRules.planStatusUpdate(
                "INVITED", "INVITED", "Waiting on leadership.");
        assertEquals(InitiativeRosterRules.PlannedKind.COMMENT, comment.kind());
        assertNull(comment.fromValue());
        assertEquals("Waiting on leadership.", comment.note());

        assertNull(InitiativeRosterRules.planStatusUpdate("JOINED", " JOINED ", "  "));
    }

    @Test
    void authorsCanEditTheirOwnCommentsAndNobodyEditsAStatusChange() {
        assertFalse(InitiativeRosterRules.canEditEntry(
                EsInitiativeTimelineEntry.EntryType.STATUS_CHANGE, 5L, 5L));
        assertFalse(InitiativeRosterRules.canEditEntry(
                EsInitiativeTimelineEntry.EntryType.FIELD_CHANGE, 5L, 5L));
        assertTrue(InitiativeRosterRules.canEditEntry(
                EsInitiativeTimelineEntry.EntryType.COMMENT, 5L, 5L));
        assertFalse(InitiativeRosterRules.canEditEntry(
                EsInitiativeTimelineEntry.EntryType.COMMENT, 5L, 9L));
        assertFalse(InitiativeRosterRules.canEditEntry(
                EsInitiativeTimelineEntry.EntryType.COMMENT, null, 5L));
    }

    @Test
    void needsAContactUntilSomeoneCurrentIsNamed() {
        assertTrue(InitiativeRosterRules.needsContact(java.util.List.of()));
        assertTrue(InitiativeRosterRules.needsContact(java.util.List.of("LEFT", "DECLINED")));
        assertFalse(InitiativeRosterRules.needsContact(java.util.List.of("LEFT", "PROSPECT")));
        assertFalse(InitiativeRosterRules.needsContact(java.util.List.of("JOINED")));
    }

    @Test
    void representationCountsCommittedOrganizationsByType() {
        InitiativeRosterRules.Representation small = InitiativeRosterRules.summarize(
                java.util.List.of("EHR", "CDS"));
        assertEquals(2, small.committedOrganizations());
        assertFalse(small.jurisdictionPresent());
        assertFalse(small.atLeastThreeOrganizations());
        assertEquals("EHR", small.committedByType().get(0).code());

        InitiativeRosterRules.Representation ready = InitiativeRosterRules.summarize(
                java.util.List.of("CDS", "JURISDICTION", "EHR", "JURISDICTION"));
        assertEquals(4, ready.committedOrganizations());
        assertTrue(ready.jurisdictionPresent());
        assertTrue(ready.atLeastThreeOrganizations());
        assertEquals(2, ready.committedByType().get(1).count());
        assertEquals("Jurisdiction", ready.committedByType().get(1).label());
    }

    @Test
    void peopleFilterByStatusOrganizationAndType() {
        assertTrue(InitiativeRosterRules.memberMatches("JOINED", 4L, "EHR", "JOINED", 4L, "EHR"));
        assertFalse(InitiativeRosterRules.memberMatches("INVITED", 4L, "EHR", "JOINED", null, null));
        assertFalse(InitiativeRosterRules.memberMatches("JOINED", 4L, "EHR", null, 9L, null));
        assertFalse(InitiativeRosterRules.memberMatches("JOINED", null, null, null, null, "JURISDICTION"));
    }

    @Test
    void contactNameSplitsOnTheLastSpace() {
        InitiativeRosterRules.ContactName name = InitiativeRosterRules.splitContactName("  Mary Ann Smith ");
        assertEquals("Mary Ann", name.firstName());
        assertEquals("Smith", name.lastName());
        assertNull(InitiativeRosterRules.splitContactName("   ").lastName());
    }
}
