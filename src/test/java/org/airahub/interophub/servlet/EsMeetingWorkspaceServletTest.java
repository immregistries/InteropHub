package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.User;
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
                                false,
                                false,
                                "Start session is only available after finalization.",
                                "Ending the meeting will set close due date to 7 days from completion.",
                                null,
                                null,
                                null,
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
                                                true,
                                                true,
                                                false,
                                                1L,
                                                "csrf-token"));

                String html = renderWorkspaceHtml(view);
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
                assertTrue(html.indexOf("Close meeting</button>") < html.indexOf("data-meeting-attachments"));
                assertTrue(html.indexOf("data-meeting-attachments") < html.indexOf(">Roles</h4>"));
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
                                false,
                                false,
                                "Start session is only available after finalization.",
                                "Ending the meeting will set close due date to 7 days from completion.",
                                null,
                                null,
                                "Session started.",
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
                                                true,
                                                true,
                                                false,
                                                1L,
                                                "csrf-token"));

                String html = renderWorkspaceHtml(view);
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
                                false,
                                false,
                                "Start session is only available after finalization.",
                                "Ending the meeting will set close due date to 7 days from completion.",
                                null,
                                null,
                                "Meeting ended.",
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
                                                true,
                                                true,
                                                false,
                                                1L,
                                                "csrf-token"));

                String html = renderWorkspaceHtml(view);
                assertTrue(!html.contains("Meeting ended."));
                assertTrue(!html.contains("#20"));
        }

        @Test
        void publishNotesButtonAndSuggestBannerRenderForACompletedUnpublishedMeeting() {
                EsMeeting meeting = meeting();
                meeting.setStatus(EsMeeting.MeetingStatus.COMPLETED);
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
                                "Completed",
                                "aira-badge--info",
                                List.of(),
                                List.of(selectedItem),
                                selectedItem,
                                7,
                                1,
                                "Ada Lovelace",
                                false,
                                false,
                                true,
                                false,
                                "Start session is only available after finalization.",
                                "Meeting has already ended.",
                                "Publishing notifies the community that notes are ready for review.",
                                null,
                                "Notes published for review.",
                                null,
                                "<a href=\"/hub/es/meeting-communication?meetingId=99&suggestType=NOTES_AVAILABLE\">Send Notes Available communication</a>",
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
                                                true,
                                                true,
                                                false,
                                                1L,
                                                "csrf-token"));

                String html = renderWorkspaceHtml(view);
                assertTrue(html.contains("name=\"action\" value=\"publishNotes\""));
                int buttonIndex = html.indexOf("Publish notes for review");
                assertTrue(buttonIndex > 0);
                String buttonTag = html.substring(html.lastIndexOf("<button", buttonIndex), buttonIndex);
                assertTrue(!buttonTag.contains("disabled"));
                assertTrue(html.contains("Send Notes Available communication"));
                assertTrue(html.contains("meeting-communication?meetingId=99&suggestType=NOTES_AVAILABLE"));
        }

        @Test
        void publishNotesButtonIsDisabledBeforeMeetingCompletion() {
                EsMeeting meeting = meeting();
                meeting.setStatus(EsMeeting.MeetingStatus.IN_SESSION);
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
                                false,
                                false,
                                "Start session is only available after finalization.",
                                "Ending the meeting will set close due date to 7 days from completion.",
                                "Notes can be published once the meeting is completed.",
                                null,
                                null,
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
                                                true,
                                                true,
                                                false,
                                                1L,
                                                "csrf-token"));

                String html = renderWorkspaceHtml(view);
                int buttonIndex = html.indexOf("Publish notes for review");
                assertTrue(buttonIndex > 0);
                String buttonTag = html.substring(html.lastIndexOf("<button", buttonIndex), buttonIndex);
                assertTrue(buttonTag.contains("disabled"));
        }

        @Test
        void closeMeetingButtonIsEnabledOnlyForAForgottenOverdueMeeting() {
                EsMeeting meeting = meeting();
                meeting.setStatus(EsMeeting.MeetingStatus.FINALIZED);
                meeting.setScheduledEnd(LocalDateTime.of(2020, 1, 1, 0, 0)); // long past, regardless of "now"
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
                                "Finalized",
                                "aira-badge--info",
                                List.of(),
                                List.of(selectedItem),
                                selectedItem,
                                7,
                                1,
                                "Ada Lovelace",
                                false,
                                false,
                                false,
                                true,
                                "Start session is only available after finalization.",
                                "Meeting has already ended.",
                                "Notes can be published once the meeting is completed.",
                                "The scheduled end time has passed. Closing locks notes on the usual 7-day timer.",
                                null,
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
                                                true,
                                                true,
                                                false,
                                                1L,
                                                "csrf-token"));

                String html = renderWorkspaceHtml(view);
                assertTrue(html.contains("name=\"action\" value=\"closeMeeting\""));
                int buttonIndex = html.indexOf("Close meeting</button>");
                assertTrue(buttonIndex > 0);
                String buttonTag = html.substring(html.lastIndexOf("<button", buttonIndex), buttonIndex);
                assertTrue(!buttonTag.contains("disabled"));
        }

        @Test
        void chairAndScribeRowsGetARealAssignFormWhenAssignableUsersArePresent() {
                EsMeeting meeting = meeting();
                meeting.setStatus(EsMeeting.MeetingStatus.IN_SESSION);
                EsMeetingWorkspaceServlet.AgendaItemView selectedItem = new EsMeetingWorkspaceServlet.AgendaItemView(
                                22L, 20, "Standing agenda", "Operations", "Accepted", "aira-badge--success",
                                "Ada Lovelace", 15, "Read only notes", true, "Open note #44", "Open",
                                "Ada Lovelace is taking notes", "10:00 AM", "10:15 AM", "10:00 AM - 10:15 AM");
                List<EsMeetingWorkspaceServlet.RoleSummary> roles = List.of(
                                new EsMeetingWorkspaceServlet.RoleSummary("Chair", "Ada Lovelace", "User #1"),
                                new EsMeetingWorkspaceServlet.RoleSummary("Current chair", "Grace Hopper", "User #2"),
                                new EsMeetingWorkspaceServlet.RoleSummary("Scribe", "Linus Torvalds", "User #3"),
                                new EsMeetingWorkspaceServlet.RoleSummary("Created by", "Nathan Bunker", "User #4"));
                EsMeetingWorkspaceServlet.WorkspaceView view = new EsMeetingWorkspaceServlet.WorkspaceView(
                                meeting, null, "Emerging Standards", "Topic series description",
                                "Thursday, January 15, 2026 10:00 AM America/New_York", "In session",
                                "aira-badge--info", roles, List.of(selectedItem), selectedItem, 7, 1, "Ada Lovelace",
                                false, true, false, false,
                                "Start session is only available after finalization.",
                                "Ending the meeting will set close due date to 7 days from completion.",
                                "Notes can be published once the meeting is completed.", null,
                                null, null, null,
                                new EsMeetingWorkspaceServlet.NotePanelView(
                                                "Operations", "Standing agenda", true, 44L, 3L, 2L, "Open", "OPEN",
                                                "Thursday, January 15, 2026 10:05 AM", "2026-01-15T10:05:00Z", "{}",
                                                "{}", 1L, "Ada Lovelace", "You are taking notes for this topic.",
                                                "Take over notes", "Take over notes from Ada Lovelace", true, true,
                                                false, 1L, "csrf-token"));

                User candidate = new User();
                candidate.setUserId(9L);
                candidate.setFirstName("Margaret");
                candidate.setLastName("Hamilton");

                String html = renderWorkspaceHtml(view, List.of(candidate));

                // Chair and Current chair (and Scribe) all get a real select + assignRole form.
                assertEquals(3, countOccurrences(html, "name=\"action\" value=\"assignRole\""));
                assertTrue(html.contains("name=\"roleType\" value=\"CHAIR\""));
                assertTrue(html.contains("name=\"roleType\" value=\"SCRIBE\""));
                assertTrue(html.contains("Margaret Hamilton"));
                // Created by never gets a real control, even when assignable users exist.
                int createdByIndex = html.indexOf("Created by");
                String afterCreatedBy = html.substring(createdByIndex);
                assertTrue(afterCreatedBy.indexOf("disabled>Assign</button>") < afterCreatedBy.indexOf("</tbody>"));
        }

        @Test
        void chairAndScribeRowsStayDisabledWhenNoAssignableUsersAreOffered() {
                EsMeeting meeting = meeting();
                meeting.setStatus(EsMeeting.MeetingStatus.FINALIZED);
                EsMeetingWorkspaceServlet.AgendaItemView selectedItem = new EsMeetingWorkspaceServlet.AgendaItemView(
                                22L, 20, "Standing agenda", "Operations", "Accepted", "aira-badge--success",
                                "Ada Lovelace", 15, "Read only notes", true, "Open note #44", "Open",
                                "Ada Lovelace is taking notes", "10:00 AM", "10:15 AM", "10:00 AM - 10:15 AM");
                List<EsMeetingWorkspaceServlet.RoleSummary> roles = List.of(
                                new EsMeetingWorkspaceServlet.RoleSummary("Chair", "Ada Lovelace", "User #1"));
                EsMeetingWorkspaceServlet.WorkspaceView view = new EsMeetingWorkspaceServlet.WorkspaceView(
                                meeting, null, "Emerging Standards", "Topic series description",
                                "Thursday, January 15, 2026 10:00 AM America/New_York", "Finalized",
                                "aira-badge--info", roles, List.of(selectedItem), selectedItem, 7, 1, "Ada Lovelace",
                                false, false, false, false,
                                "Start session is only available after finalization.",
                                "Meeting has already ended.",
                                "Notes can be published once the meeting is completed.", null,
                                null, null, null,
                                new EsMeetingWorkspaceServlet.NotePanelView(
                                                "Operations", "Standing agenda", true, 44L, 3L, 2L, "Open", "OPEN",
                                                "Thursday, January 15, 2026 10:05 AM", "2026-01-15T10:05:00Z", "{}",
                                                "{}", 1L, "Ada Lovelace", "You are taking notes for this topic.",
                                                "Take over notes", "Take over notes from Ada Lovelace", true, true,
                                                false, 1L, "csrf-token"));

                // No 4th-arg assignableUsers supplied - matches an out-of-session or unauthorized viewer.
                String html = renderWorkspaceHtml(view);

                assertTrue(!html.contains("name=\"action\" value=\"assignRole\""));
                assertTrue(html.contains("disabled>Assign</button>"));
        }

        private static int countOccurrences(String haystack, String needle) {
                int count = 0;
                int index = 0;
                while ((index = haystack.indexOf(needle, index)) != -1) {
                        count++;
                        index += needle.length();
                }
                return count;
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
                                false,
                                false,
                                "Start session is only available after finalization.",
                                "Ending the meeting will set close due date to 7 days from completion.",
                                null,
                                null,
                                null,
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
                                                true,
                                                true,
                                                false,
                                                1L,
                                                "csrf-token"));

                String html = renderWorkspaceHtml(view);
                assertTrue(!html.contains("data-note-assume-editorship"));
                assertTrue(!html.contains("data-note-edit-toggle"));
        }

        @Test
        void meetingAdminCardShowsWorkspaceLinkAndGatesAdminLinksOnAdmin() {
                StringWriter editorBuffer = new StringWriter();
                EsAgendaServlet.renderMeetingAdminCard(new PrintWriter(editorBuffer), "/hub", 123L, 55L, false,
                                false, null);
                String editorHtml = editorBuffer.toString();
                assertTrue(editorHtml.contains("Open Meeting Workspace"));
                assertTrue(editorHtml.contains("/es/meeting-workspace?meetingId=123"));
                assertTrue(!editorHtml.contains("Confluence export"));
                assertTrue(!editorHtml.contains("Meeting Polls"));
                assertTrue(!editorHtml.contains("Meeting Surveys"));
                assertTrue(!editorHtml.contains("/admin/es/meetings?meetingId=55"));

                StringWriter adminBuffer = new StringWriter();
                EsAgendaServlet.renderMeetingAdminCard(new PrintWriter(adminBuffer), "/hub", 123L, 55L, true,
                                false, null);
                String adminHtml = adminBuffer.toString();
                assertTrue(adminHtml.contains("Open Meeting Workspace"));
                assertTrue(adminHtml.contains("Confluence export"));
                assertTrue(adminHtml.contains("/es/agenda/confluence?meetingId=123"));
                assertTrue(adminHtml.contains("/admin/es/meetings?meetingId=55"));
                assertTrue(adminHtml.contains("Meeting Polls"));
                assertTrue(adminHtml.contains("/admin/es/meeting-polls"));
                assertTrue(adminHtml.contains("Meeting Surveys"));
                assertTrue(adminHtml.contains("/admin/es/meeting-survey"));
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

        private static String renderWorkspaceHtml(EsMeetingWorkspaceServlet.WorkspaceView view) {
                return renderWorkspaceHtml(view, List.of());
        }

        private static String renderWorkspaceHtml(EsMeetingWorkspaceServlet.WorkspaceView view,
                        List<User> assignableUsers) {
                StringWriter buffer = new StringWriter();
                PrintWriter out = new PrintWriter(buffer);
                EsMeetingWorkspaceServlet.renderWorkspaceContent(out, "/hub", view, assignableUsers);
                out.flush();
                return buffer.toString();
        }
}