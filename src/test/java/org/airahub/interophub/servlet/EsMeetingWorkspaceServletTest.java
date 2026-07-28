package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.junit.jupiter.api.Test;

class EsMeetingWorkspaceServletTest {

        @Test
        void statusLabelsAndClassesAreStable() {
                assertEquals("Finalized",
                                EsMeetingWorkspaceServlet.meetingStatusLabel(EsMeeting.MeetingStatus.FINALIZED));
                assertEquals("aira-badge--info",
                                EsMeetingWorkspaceServlet.meetingStatusClass(EsMeeting.MeetingStatus.IN_SESSION));
                assertEquals("Needs revision",
                                EsMeetingWorkspaceServlet.agendaStatusLabel(
                                                EsMeetingAgendaItem.AgendaItemStatus.NEEDS_REVISION));
                assertEquals("aira-badge--warning",
                                EsMeetingWorkspaceServlet
                                                .agendaStatusClass(EsMeetingAgendaItem.AgendaItemStatus.POSTPONED));
                assertEquals("Open",
                                EsMeetingWorkspaceServlet.topicNoteStatusLabel(
                                                org.airahub.interophub.model.TopicNoteStatus.OPEN));
        }

        @Test
        void selectedAgendaItemPrefersRequestedThenCurrentThenFirst() {
                EsMeeting meeting = meeting();
                meeting.setCurrentAgendaItemId(20L);

                List<EsMeetingWorkspaceServlet.AgendaItemView> items = List.of(
                                agendaItem(10L, 1, "One"),
                                agendaItem(20L, 2, "Two"),
                                agendaItem(30L, 3, "Three"));

                assertEquals(30L, EsMeetingWorkspaceServlet.selectedAgendaItemId(meeting, items, 30L));
                assertEquals(20L, EsMeetingWorkspaceServlet.selectedAgendaItemId(meeting, items, null));

                meeting.setCurrentAgendaItemId(999L);
                assertEquals(10L, EsMeetingWorkspaceServlet.selectedAgendaItemId(meeting, items, null));
        }

        @Test
        void agendaItemsSortByDisplayOrderThenId() {
                List<EsMeetingWorkspaceServlet.AgendaItemView> sorted = EsMeetingWorkspaceServlet
                                .sortAgendaItems(List.of(
                                                agendaItem(30L, 3, "Three"),
                                                agendaItem(10L, 1, "One"),
                                                agendaItem(20L, 2, "Two")));

                assertEquals(10L, sorted.get(0).agendaItemId());
                assertEquals(20L, sorted.get(1).agendaItemId());
                assertEquals(30L, sorted.get(2).agendaItemId());
        }

        @Test
        void effectiveAgendaTitleFallsBackToTopicNameThenGenericLabel() {
                EsMeetingAgendaItem item = new EsMeetingAgendaItem();
                item.setTitle(null);
                assertEquals("Emergency response",
                                EsMeetingWorkspaceServlet.effectiveAgendaTitle(item, "Emergency response"));
                assertEquals("Agenda item", EsMeetingWorkspaceServlet.effectiveAgendaTitle(item, null));
        }

        @Test
        void completedMeetingsCanBeRestartedWhenStartWindowIsOpen() {
                EsMeeting meeting = meeting();
                meeting.setStatus(EsMeeting.MeetingStatus.COMPLETED);

                assertTrue(EsMeetingWorkspaceServlet.canStartSession(meeting, true));
                assertTrue(!EsMeetingWorkspaceServlet.canStartSession(meeting, false));
        }

        @Test
        void renderedWorkspaceIsReadOnlyAndShowsPlaceholderControls() {
                EsMeeting meeting = meeting();
                meeting.setMeetingName("Weekly Meeting");
                meeting.setStatus(EsMeeting.MeetingStatus.IN_SESSION);

                EsMeetingWorkspaceServlet.AgendaItemView selectedItem = new EsMeetingWorkspaceServlet.AgendaItemView(
                                22L, 20, "Standing agenda", "Operations", "Accepted", "aira-badge--success",
                                "Ada Lovelace", 15, "Read only notes", true, "Open note #44", "Open",
                                "Ada Lovelace is taking notes",
                                "10:00 AM", "10:15 AM", "10:00 AM - 10:15 AM");
                EsMeetingWorkspaceServlet.WorkspaceView view = new EsMeetingWorkspaceServlet.WorkspaceView(
                                meeting,
                                null,
                                "Emerging Standards",
                                "Topic series description",
                                "Thursday, January 15, 2026 10:00 AM America/New_York",
                                "In session",
                                "aira-badge--info",
                                List.of(new EsMeetingWorkspaceServlet.RoleSummary("Current chair", "Ada Lovelace",
                                                "User #1")),
                                List.of(selectedItem),
                                selectedItem,
                                7,
                                1,
                                "Ada Lovelace",
                                false,
                                true,
                                "Start session is only available after finalization.",
                                "Ending the meeting will set close due date to 7 days from completion.",
                                null,
                                null,
                                new EsMeetingWorkspaceServlet.NotePanelView(
                                                "Operations",
                                                "Standing agenda",
                                                true,
                                                44L,
                                                3L,
                                                2L,
                                                "Open",
                                                "OPEN",
                                                "Thursday, January 15, 2026 10:05 AM",
                                                "2026-01-15T10:05:00Z",
                                                "{\"type\":\"doc\",\"content\":[{\"type\":\"bulletList\",\"content\":[{\"type\":\"listItem\",\"attrs\":{\"nodeId\":\"11111111-1111-4111-8111-111111111111\"},\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Read only notes\"}]}]}]}]}",
                                                "{\"type\":\"doc\",\"content\":[{\"type\":\"bulletList\",\"content\":[{\"type\":\"listItem\",\"attrs\":{\"nodeId\":\"11111111-1111-4111-8111-111111111111\"},\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Read only notes\"}]}]}]}]}",
                                                1L,
                                                "Ada Lovelace",
                                                "You are taking notes for this topic.",
                                                "Take over notes",
                                                "Take over notes from Ada Lovelace",
                                                "Edit notes",
                                                true,
                                                true,
                                                false,
                                                true,
                                                1L,
                                                "csrf-token"));

                StringWriter buffer = new StringWriter();
                PrintWriter out = new PrintWriter(buffer);
                EsMeetingWorkspaceServlet.renderPage(out, "/hub", view);
                out.flush();

                String html = buffer.toString();
                assertTrue(html.contains("Meeting Workspace"));
                assertTrue(html.contains("Meeting Controls"));
                assertTrue(html.contains("View Agenda"));
                assertTrue(html.contains("Next topic"));
                assertTrue(html.contains("Topic Notes"));
                assertTrue(html.contains("Recorded Outcomes"));
                assertTrue(html.contains("data-note-config"));
                assertTrue(html.contains("data-outcome-list"));
                assertTrue(html.contains("createOutcomeUrl"));
                assertTrue(html.contains("method=\"post\""));
                assertTrue(html.contains("name=\"action\" value=\"startSession\""));
                assertTrue(html.contains("name=\"action\" value=\"endMeeting\""));
                assertTrue(html.contains("Read only notes"));
                assertTrue(html.contains("meeting-workspace-notes.js"));
                assertTrue(!html.contains("Start session is only available after finalization."));
                assertTrue(!html.contains("Ending the meeting will set close due date to 7 days from completion."));
                assertTrue(!html.contains("Lifecycle transitions are server-enforced and role-aware."));
                assertTrue(!html.contains("confirm('Start session now?')"));
                assertTrue(!html.contains("confirm('End meeting now?')"));
                assertTrue(!html.contains("Anchor outcomes to specific bullets"));
                assertTrue(!html.contains("Session can now be started."));
        }

        @Test
        void startedFeedbackAndUnassignedCurrentRolesAreHidden() {
                EsMeeting meeting = meeting();
                EsMeetingWorkspaceServlet.AgendaItemView selectedItem = new EsMeetingWorkspaceServlet.AgendaItemView(
                                22L, 20, "Standing agenda", "Operations", "Accepted", "aira-badge--success",
                                "Ada Lovelace", 15, "Read only notes", true, "Open note #44", "Open",
                                "Ada Lovelace is taking notes", "10:00 AM", "10:15 AM", "10:00 AM - 10:15 AM");
                EsMeetingWorkspaceServlet.WorkspaceView view = new EsMeetingWorkspaceServlet.WorkspaceView(
                                meeting,
                                null,
                                "Emerging Standards",
                                "Topic series description",
                                "Thursday, January 15, 2026 10:00 AM America/New_York",
                                "In session",
                                "aira-badge--info",
                                List.of(
                                                new EsMeetingWorkspaceServlet.RoleSummary("Current Chair", "Unassigned",
                                                                "Not assigned"),
                                                new EsMeetingWorkspaceServlet.RoleSummary("Current Scribe",
                                                                "Unassigned", "Not assigned"),
                                                new EsMeetingWorkspaceServlet.RoleSummary("Designated Chair",
                                                                "Ada Lovelace", "User #1"),
                                                new EsMeetingWorkspaceServlet.RoleSummary("Designated Scribe",
                                                                "Grace Hopper", "User #2"),
                                                new EsMeetingWorkspaceServlet.RoleSummary("Created By",
                                                                "Linus Torvalds", "User #3")),
                                List.of(selectedItem),
                                selectedItem,
                                7,
                                1,
                                "Ada Lovelace",
                                false,
                                true,
                                "Start session is only available after finalization.",
                                "Ending the meeting will set close due date to 7 days from completion.",
                                "Session started.",
                                null,
                                new EsMeetingWorkspaceServlet.NotePanelView(
                                                "Operations",
                                                "Standing agenda",
                                                true,
                                                44L,
                                                3L,
                                                2L,
                                                "Open",
                                                "OPEN",
                                                "Thursday, January 15, 2026 10:05 AM",
                                                "2026-01-15T10:05:00Z",
                                                "{}",
                                                "{}",
                                                1L,
                                                "Ada Lovelace",
                                                "You are taking notes for this topic.",
                                                "Take over notes",
                                                "Take over notes from Ada Lovelace",
                                                "Edit notes",
                                                true,
                                                true,
                                                false,
                                                true,
                                                1L,
                                                "csrf-token"));

                StringWriter buffer = new StringWriter();
                PrintWriter out = new PrintWriter(buffer);
                EsMeetingWorkspaceServlet.renderPage(out, "/hub", view);
                out.flush();

                String html = buffer.toString();
                assertTrue(!html.contains("Session started."));
                assertTrue(html.contains("Designated Chair"));
                assertTrue(html.contains("Created By"));
                assertTrue(!html.contains("Current Chair"));
                assertTrue(!html.contains("Current Scribe"));
        }

        @Test
        void endedMeetingFeedbackIsSuppressedAndAgendaNumbersAreHidden() {
                EsMeeting meeting = meeting();
                EsMeetingWorkspaceServlet.AgendaItemView selectedItem = new EsMeetingWorkspaceServlet.AgendaItemView(
                                22L, 20, "Standing agenda", "Operations", "Accepted", "aira-badge--success",
                                "Ada Lovelace", 15, "Read only notes", true, "Open note #44", "Open",
                                "Ada Lovelace is taking notes", "10:00 AM", "10:15 AM", "10:00 AM - 10:15 AM");
                EsMeetingWorkspaceServlet.WorkspaceView view = new EsMeetingWorkspaceServlet.WorkspaceView(
                                meeting,
                                null,
                                "Emerging Standards",
                                "Topic series description",
                                "Thursday, January 15, 2026 10:00 AM America/New_York",
                                "In session",
                                "aira-badge--info",
                                List.of(),
                                List.of(selectedItem),
                                selectedItem,
                                7,
                                1,
                                "Ada Lovelace",
                                false,
                                true,
                                "Start session is only available after finalization.",
                                "Ending the meeting will set close due date to 7 days from completion.",
                                "Meeting ended.",
                                null,
                                new EsMeetingWorkspaceServlet.NotePanelView(
                                                "Operations",
                                                "Standing agenda",
                                                true,
                                                44L,
                                                3L,
                                                2L,
                                                "Open",
                                                "OPEN",
                                                "Thursday, January 15, 2026 10:05 AM",
                                                "2026-01-15T10:05:00Z",
                                                "{}",
                                                "{}",
                                                1L,
                                                "Ada Lovelace",
                                                "You are taking notes for this topic.",
                                                "Take over notes",
                                                "Take over notes from Ada Lovelace",
                                                "Edit notes",
                                                true,
                                                true,
                                                false,
                                                true,
                                                1L,
                                                "csrf-token"));

                StringWriter buffer = new StringWriter();
                PrintWriter out = new PrintWriter(buffer);
                EsMeetingWorkspaceServlet.renderPage(out, "/hub", view);
                out.flush();

                String html = buffer.toString();
                assertTrue(!html.contains("Meeting ended."));
                assertTrue(!html.contains("#20"));
        }

        @Test
        void noteActionButtonsAreHiddenWhenTheUserIsAlreadyEditing() {
                EsMeeting meeting = meeting();
                EsMeetingWorkspaceServlet.AgendaItemView selectedItem = new EsMeetingWorkspaceServlet.AgendaItemView(
                                22L, 20, "Standing agenda", "Operations", "Accepted", "aira-badge--success",
                                "Ada Lovelace", 15, "Read only notes", true, "Open note #44", "Open",
                                "Ada Lovelace is taking notes", "10:00 AM", "10:15 AM", "10:00 AM - 10:15 AM");
                EsMeetingWorkspaceServlet.WorkspaceView view = new EsMeetingWorkspaceServlet.WorkspaceView(
                                meeting,
                                null,
                                "Emerging Standards",
                                "Topic series description",
                                "Thursday, January 15, 2026 10:00 AM America/New_York",
                                "In session",
                                "aira-badge--info",
                                List.of(),
                                List.of(selectedItem),
                                selectedItem,
                                7,
                                1,
                                "Ada Lovelace",
                                false,
                                true,
                                "Start session is only available after finalization.",
                                "Ending the meeting will set close due date to 7 days from completion.",
                                null,
                                null,
                                new EsMeetingWorkspaceServlet.NotePanelView(
                                                "Operations",
                                                "Standing agenda",
                                                true,
                                                44L,
                                                3L,
                                                2L,
                                                "Open",
                                                "OPEN",
                                                "Thursday, January 15, 2026 10:05 AM",
                                                "2026-01-15T10:05:00Z",
                                                "{}",
                                                "{}",
                                                1L,
                                                "Ada Lovelace",
                                                "You are taking notes for this topic.",
                                                "Take over notes",
                                                "Take over notes from Ada Lovelace",
                                                "Edit notes",
                                                true,
                                                true,
                                                false,
                                                true,
                                                1L,
                                                "csrf-token"));

                StringWriter buffer = new StringWriter();
                PrintWriter out = new PrintWriter(buffer);
                EsMeetingWorkspaceServlet.renderPage(out, "/hub", view);
                out.flush();

                String html = buffer.toString();
                assertTrue(!html.contains("data-note-assume-editorship"));
                assertTrue(!html.contains("data-note-edit-toggle"));
        }

        @Test
        void workspaceFooterLinkRespectsEditAccess() {
                StringWriter visibleBuffer = new StringWriter();
                EsAgendaServlet.renderWorkspaceLink(new PrintWriter(visibleBuffer), "/hub", 123L, true);
                assertTrue(visibleBuffer.toString().contains("Open Meeting Workspace"));
                assertTrue(visibleBuffer.toString().contains("/es/meeting-workspace?meetingId=123"));

                StringWriter hiddenBuffer = new StringWriter();
                EsAgendaServlet.renderWorkspaceLink(new PrintWriter(hiddenBuffer), "/hub", 123L, false);
                assertEquals("", hiddenBuffer.toString());
        }

        private static EsMeeting meeting() {
                EsMeeting meeting = new EsMeeting();
                meeting.setEsMeetingId(99L);
                meeting.setMeetingName("Demo meeting");
                meeting.setScheduledStart(LocalDateTime.of(2026, 1, 15, 10, 0));
                meeting.setScheduledEnd(LocalDateTime.of(2026, 1, 15, 11, 0));
                meeting.setTimezoneId("America/New_York");
                meeting.setStatus(EsMeeting.MeetingStatus.FINALIZED);
                meeting.setCreatedByUserId(1L);
                meeting.setDesignatedChairUserId(1L);
                meeting.setCurrentChairUserId(1L);
                meeting.setDesignatedScribeUserId(2L);
                meeting.setCurrentScribeUserId(2L);
                return meeting;
        }

        private static EsMeetingWorkspaceServlet.AgendaItemView agendaItem(Long id, Integer displayOrder,
                        String title) {
                return new EsMeetingWorkspaceServlet.AgendaItemView(
                                id,
                                displayOrder,
                                title,
                                null,
                                "Accepted",
                                "aira-badge--success",
                                null,
                                10,
                                null,
                                false,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null);
        }
}