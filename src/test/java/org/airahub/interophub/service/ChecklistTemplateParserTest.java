package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChecklistTemplateParserTest {

    @Test
    void draftTemplatesParse() throws Exception {
        ChecklistTemplateParser.Parsed initiative = parseFile("initiative-checklist-v1.md");
        assertTrue(initiative.ok(), initiative.errorText());
        assertEquals("INITIATIVE", initiative.kind());
        assertEquals(1, initiative.version());
        assertEquals("Initiative Checklist", initiative.title());
        assertEquals(29, initiative.items().size());
        ChecklistTemplateParser.Item purpose = initiative.items().get(0);
        assertEquals("initiative-purpose", purpose.key());
        assertEquals("Set up", purpose.phase());
        assertEquals("start", purpose.dueAnchor());
        assertEquals(0, purpose.dueOffsetDays());
        assertNull(purpose.checkCode());

        ChecklistTemplateParser.Item champion = find(initiative, "primary-topic");
        assertEquals("topic-champion", champion.checkCode());

        ChecklistTemplateParser.Item slack = find(initiative, "slack-channel");
        assertEquals(7, slack.dueOffsetDays());

        ChecklistTemplateParser.Item close = find(initiative, "close-initiative");
        assertEquals("end", close.dueAnchor());
        assertEquals(30, close.dueOffsetDays());

        ChecklistTemplateParser.Parsed smallGroup = parseFile("small-group-checklist-v1.md");
        assertTrue(smallGroup.ok(), smallGroup.errorText());
        assertEquals("SMALL_GROUP", smallGroup.kind());
        assertEquals(46, smallGroup.items().size());
        ChecklistTemplateParser.Item areas = find(smallGroup, "product-areas");
        assertTrue(areas.guidance().startsWith("If only one area is primary"));
        ChecklistTemplateParser.Item bundle = find(smallGroup, "technical-bundle");
        assertEquals("cycle-start", bundle.dueAnchor());
        assertEquals(-7, bundle.dueOffsetDays());
        assertEquals("technical-bundle", bundle.checkCode());
        ChecklistTemplateParser.Item question = find(smallGroup, "technical-question");
        assertEquals(-42, question.dueOffsetDays());
    }

    @Test
    void missingKeyIsGeneratedAndGuidanceIsKept() {
        ChecklistTemplateParser.Parsed parsed = ChecklistTemplateParser.parse("""
                # Example
                kind: INITIATIVE
                version: 3

                ## Set up
                - Write the purpose.
                  Keep it short.
                  A second line.
                """);
        assertTrue(parsed.ok(), parsed.errorText());
        assertEquals("write-the-purpose", parsed.items().get(0).key());
        assertEquals("Keep it short.\nA second line.", parsed.items().get(0).guidance());
    }

    @Test
    void reportsEveryProblemWithItsLine() {
        ChecklistTemplateParser.Parsed parsed = ChecklistTemplateParser.parse("""
                # Example
                kind: PROJECT
                version: two

                - [start] Before a phase.
                ## Set up
                - [same] First
                - [same] Second
                - [other] Date (due: launch+3d)
                """);
        assertFalse(parsed.ok());
        assertTrue(parsed.errorText().contains("Line 2: Unknown kind"));
        assertTrue(parsed.errorText().contains("Line 3: version must be a whole number."));
        assertTrue(parsed.errorText().contains("Line 5: An item has to be under a phase heading."));
        assertTrue(parsed.errorText().contains("Line 8: Duplicate key \"same\" is already used on line 7."));
        assertTrue(parsed.errorText().contains("Line 9: Unknown due anchor"));
        assertEquals(5, parsed.errors().size());
    }

    @Test
    void htmlCommentsAreIgnored() {
        ChecklistTemplateParser.Parsed parsed = ChecklistTemplateParser.parse("""
                # Example
                kind: INITIATIVE
                version: 1
                <!--
                kind: SMALL_GROUP
                - [hidden] Not an item
                -->
                ## Set up
                - [visible] Shown
                """);
        assertTrue(parsed.ok(), parsed.errorText());
        assertEquals("INITIATIVE", parsed.kind());
        assertEquals(1, parsed.items().size());
        assertEquals("visible", parsed.items().get(0).key());
    }

    @Test
    void downloadRoundTripKeepsItemsAndLeavesVersionUnset() {
        ChecklistTemplateParser.Parsed parsed = ChecklistTemplateParser.parse("""
                # Initiative Checklist
                kind: INITIATIVE
                version: 4

                ## Set up
                - [purpose] Write the purpose. (due: start+7d) (check: topic-champion)
                  A note.
                - [custom] Added by the organizer.
                ## Close
                - [lookback] Write the lookback. (due: end)
                """);
        String downloaded = ChecklistMarkdown.write(parsed.title(), parsed.kind(), parsed.items());
        assertFalse(downloaded.contains("version:"));
        ChecklistTemplateParser.Parsed again = ChecklistTemplateParser.parse(downloaded.replace(
                "kind: INITIATIVE\n", "kind: INITIATIVE\nversion: 8\n"));
        assertTrue(again.ok(), again.errorText());
        assertEquals(8, again.version());
        assertEquals(parsed.items().size(), again.items().size());
        for (int index = 0; index < parsed.items().size(); index++) {
            ChecklistTemplateParser.Item left = parsed.items().get(index);
            ChecklistTemplateParser.Item right = again.items().get(index);
            assertEquals(left.key(), right.key());
            assertEquals(left.phase(), right.phase());
            assertEquals(left.title(), right.title());
            assertEquals(left.guidance(), right.guidance());
            assertEquals(left.dueAnchor(), right.dueAnchor());
            assertEquals(left.dueOffsetDays(), right.dueOffsetDays());
            assertEquals(left.checkCode(), right.checkCode());
            assertEquals(left.displayOrder(), right.displayOrder());
        }
    }

    private static ChecklistTemplateParser.Parsed parseFile(String name) throws Exception {
        return ChecklistTemplateParser.parse(Files.readString(Path.of("docs/initiatives/templates", name)));
    }

    private static ChecklistTemplateParser.Item find(ChecklistTemplateParser.Parsed parsed, String key) {
        for (ChecklistTemplateParser.Item item : parsed.items()) {
            if (key.equals(item.key())) {
                return item;
            }
        }
        throw new AssertionError(key);
    }
}
