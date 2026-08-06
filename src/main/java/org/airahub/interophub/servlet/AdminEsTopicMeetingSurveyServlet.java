package org.airahub.interophub.servlet;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.model.EsSurvey;
import org.airahub.interophub.model.EsSurvey.SurveyStatus;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicMeetingSurvey;
import org.airahub.interophub.model.EsTopicMeetingSurvey.AssignmentStatus;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.EsSurveyService;

public class AdminEsTopicMeetingSurveyServlet extends HttpServlet {

    private static final String ACTIVE_HREF = "/admin/es/meeting-survey";

    private final EsSurveyService surveyService;
    private final EsTopicMeetingDao topicMeetingDao;

    public AdminEsTopicMeetingSurveyServlet() {
        this.surveyService = new EsSurveyService();
        this.topicMeetingDao = new EsTopicMeetingDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String action = trimToNull(request.getParameter("action"));
        Long assignmentId = parseId(trimToNull(request.getParameter("assignmentId")));
        Long meetingId = parseId(trimToNull(request.getParameter("meetingId")));

        if ("new".equals(action)) {
            renderCreateForm(request, response, meetingId, null, null);
            return;
        }

        if (assignmentId != null) {
            EsTopicMeetingSurvey assignment = surveyService.getTopicMeetingSurvey(assignmentId).orElse(null);
            if (assignment == null) {
                renderList(request, response, "Assignment not found.");
                return;
            }
            String savedMsg = request.getParameter("saved") != null ? "Assignment saved." : null;
            renderEditForm(request, response, assignment, savedMsg, null);
            return;
        }

        renderList(request, response, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String action = trimToNull(request.getParameter("action"));

        if ("create".equals(action)) {
            Long esTopicMeetingId = parseId(trimToNull(request.getParameter("esTopicMeetingId")));
            Long esSurveyId = parseId(trimToNull(request.getParameter("esSurveyId")));
            String startRaw = trimToNull(request.getParameter("startDate"));
            String endRaw = trimToNull(request.getParameter("endDate"));

            if (esTopicMeetingId == null || esSurveyId == null || startRaw == null || endRaw == null) {
                renderCreateForm(request, response, esTopicMeetingId,
                        "All fields are required.", null);
                return;
            }
            try {
                LocalDate start = LocalDate.parse(startRaw);
                LocalDate end = LocalDate.parse(endRaw);
                EsTopicMeetingSurvey created = surveyService.createTopicMeetingSurvey(
                        esTopicMeetingId, esSurveyId, start, end, adminUser.get().getUserId());
                response.sendRedirect(contextPath + "/admin/es/meeting-survey?assignmentId="
                        + created.getEsTopicMeetingSurveyId() + "&saved=1");
            } catch (DateTimeParseException ex) {
                renderCreateForm(request, response, esTopicMeetingId,
                        "Invalid date format. Use YYYY-MM-DD.", null);
            } catch (Exception ex) {
                renderCreateForm(request, response, esTopicMeetingId, ex.getMessage(), null);
            }
            return;
        }

        Long assignmentId = parseId(trimToNull(request.getParameter("assignmentId")));
        if (assignmentId == null) {
            renderList(request, response, "Missing assignment ID.");
            return;
        }

        if ("update".equals(action)) {
            String startRaw = trimToNull(request.getParameter("startDate"));
            String endRaw = trimToNull(request.getParameter("endDate"));
            String statusRaw = trimToNull(request.getParameter("status"));
            EsTopicMeetingSurvey assignment = surveyService.getTopicMeetingSurvey(assignmentId).orElse(null);
            if (assignment == null) {
                renderList(request, response, "Assignment not found.");
                return;
            }
            if (startRaw == null || endRaw == null || statusRaw == null) {
                renderEditForm(request, response, assignment, null, "All fields are required.");
                return;
            }
            try {
                LocalDate start = LocalDate.parse(startRaw);
                LocalDate end = LocalDate.parse(endRaw);
                AssignmentStatus status = AssignmentStatus.valueOf(statusRaw.toUpperCase());
                surveyService.updateTopicMeetingSurvey(assignmentId, start, end, status);
                response.sendRedirect(contextPath + "/admin/es/meeting-survey?assignmentId="
                        + assignmentId + "&saved=1");
            } catch (DateTimeParseException ex) {
                renderEditForm(request, response, assignment, null,
                        "Invalid date format. Use YYYY-MM-DD.");
            } catch (Exception ex) {
                renderEditForm(request, response, assignment, null, ex.getMessage());
            }
            return;
        }

        renderList(request, response, "Unknown action.");
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    private void renderList(HttpServletRequest request, HttpServletResponse response,
            String message) throws IOException {
        String contextPath = request.getContextPath();
        List<EsTopicMeetingSurvey> assignments = surveyService.listTopicMeetingSurveys();
        AdminShellRenderer.render(request, response, "Meeting Survey Assignments - InteropHub",
                AdminSection.TOPIC_SPACES, ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Meeting Survey Assignments</h2>");
                    if (message != null) {
                        out.println("            <div class=\"aira-alert aira-alert--info\"><p>"
                                + escapeHtml(message) + "</p></div>");
                    }
                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--primary\" href=\"" + contextPath
                            + "/admin/es/meeting-survey?action=new\">+ New Assignment</a>");
                    out.println("            </div>");
                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println("              <thead><tr>"
                            + "<th>Survey</th><th>Meeting</th>"
                            + "<th>Start</th><th>Status</th><th>Actions</th>"
                            + "</tr></thead>");
                    out.println("              <tbody>");
                    for (EsTopicMeetingSurvey a : assignments) {
                        EsSurvey survey = surveyService.getSurvey(a.getEsSurveyId()).orElse(null);
                        EsTopicMeeting meeting = topicMeetingDao.findById(a.getEsTopicMeetingId()).orElse(null);
                        String surveyName = survey != null ? survey.getSurveyName() : "?";
                        String meetingName = meeting != null ? orEmpty(meeting.getMeetingName()) : "?";
                        String editUrl = contextPath + "/admin/es/meeting-survey?assignmentId="
                                + a.getEsTopicMeetingSurveyId();
                        String resultsUrl = contextPath + "/admin/es/survey-results?assignmentId="
                                + a.getEsTopicMeetingSurveyId();
                        out.println("                <tr>");
                        out.println("                  <td>" + escapeHtml(surveyName) + "</td>");
                        out.println("                  <td>" + escapeHtml(meetingName) + "</td>");
                        out.println("                  <td>" + escapeHtml(a.getStartDate().toString()) + "</td>");
                        out.println("                  <td>" + escapeHtml(a.getStatus() != null
                                ? a.getStatus().name()
                                : "") + "</td>");
                        out.println("                  <td><a class=\"aira-inline-link\" href=\"" + editUrl
                                + "\">Edit</a>"
                                + " | <a class=\"aira-inline-link\" href=\"" + resultsUrl + "\">Results</a></td>");
                        out.println("                </tr>");
                    }
                    if (assignments.isEmpty()) {
                        out.println("                <tr><td colspan=\"5\">No assignments found.</td></tr>");
                    }
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");
                    out.println("          </section>");
                });
    }

    private void renderCreateForm(HttpServletRequest request, HttpServletResponse response,
            Long preselectedMeetingId, String errorMessage, String successMessage) throws IOException {
        String contextPath = request.getContextPath();
        List<EsSurvey> readySurveys = surveyService.listSurveys().stream()
                .filter(s -> s.getStatus() == SurveyStatus.READY)
                .toList();
        List<EsTopicMeeting> allMeetings = topicMeetingDao.findAll();
        AdminShellRenderer.render(request, response, "New Assignment - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">New Meeting Survey Assignment</h2>");
                    if (errorMessage != null) {
                        out.println("            <div class=\"aira-alert aira-alert--danger\"><p>"
                                + escapeHtml(errorMessage) + "</p></div>");
                    }
                    if (successMessage != null) {
                        out.println("            <div class=\"aira-alert aira-alert--success\"><p>"
                                + escapeHtml(successMessage) + "</p></div>");
                    }
                    out.println("            <form class=\"aira-form\" method=\"post\" action=\""
                            + contextPath + "/admin/es/meeting-survey\">");
                    out.println("              <input type=\"hidden\" name=\"action\" value=\"create\">");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"esTopicMeetingId\">Topic Meeting</label>");
                    out.println("                <select class=\"aira-select\" id=\"esTopicMeetingId\" name=\"esTopicMeetingId\">");
                    for (EsTopicMeeting m : allMeetings) {
                        boolean selected = preselectedMeetingId != null
                                && preselectedMeetingId.equals(m.getEsTopicMeetingId());
                        out.println("                  <option value=\"" + m.getEsTopicMeetingId() + "\""
                                + (selected ? " selected" : "") + ">"
                                + escapeHtml(orEmpty(m.getMeetingName()))
                                + " (id=" + m.getEsTopicMeetingId() + ")</option>");
                    }
                    out.println("                </select>");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"esSurveyId\">Survey (READY only)</label>");
                    out.println("                <select class=\"aira-select\" id=\"esSurveyId\" name=\"esSurveyId\">");
                    for (EsSurvey s : readySurveys) {
                        out.println("                  <option value=\"" + s.getEsSurveyId() + "\">"
                                + escapeHtml(s.getSurveyName()) + "</option>");
                    }
                    out.println("                </select>");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"startDate\">Start Date (YYYY-MM-DD)</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"startDate\" type=\"date\" name=\"startDate\" required>");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"endDate\">End Date (YYYY-MM-DD)</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"endDate\" type=\"date\" name=\"endDate\" required>");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-action-group\">");
                    out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Create Assignment</button>");
                    out.println("                <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/meeting-survey\">Cancel</a>");
                    out.println("              </div>");
                    out.println("            </form>");
                    out.println("          </section>");
                });
    }

    private void renderEditForm(HttpServletRequest request, HttpServletResponse response,
            EsTopicMeetingSurvey assignment, String successMessage,
            String errorMessage) throws IOException {
        String contextPath = request.getContextPath();
        EsSurvey survey = surveyService.getSurvey(assignment.getEsSurveyId()).orElse(null);
        EsTopicMeeting meeting = topicMeetingDao.findById(assignment.getEsTopicMeetingId()).orElse(null);
        AdminShellRenderer.render(request, response, "Edit Assignment - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Meeting Survey Assignment #"
                            + assignment.getEsTopicMeetingSurveyId() + "</h2>");
                    out.println("            <p>Survey: <strong>"
                            + escapeHtml(survey != null ? survey.getSurveyName() : "?") + "</strong></p>");
                    out.println("            <p>Meeting: <strong>"
                            + escapeHtml(meeting != null ? orEmpty(meeting.getMeetingName()) : "?") + "</strong></p>");
                    if (successMessage != null) {
                        out.println("            <div class=\"aira-alert aira-alert--success\"><p>"
                                + escapeHtml(successMessage) + "</p></div>");
                    }
                    if (errorMessage != null) {
                        out.println("            <div class=\"aira-alert aira-alert--danger\"><p>"
                                + escapeHtml(errorMessage) + "</p></div>");
                    }
                    out.println("            <form class=\"aira-form\" method=\"post\" action=\""
                            + contextPath + "/admin/es/meeting-survey\">");
                    out.println("              <input type=\"hidden\" name=\"action\" value=\"update\">");
                    out.println("              <input type=\"hidden\" name=\"assignmentId\" value=\""
                            + assignment.getEsTopicMeetingSurveyId() + "\">");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"startDate\">Start Date</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"startDate\" type=\"date\" name=\"startDate\" value=\""
                                    + assignment.getStartDate().toString() + "\" required>");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"endDate\">End Date</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"endDate\" type=\"date\" name=\"endDate\" value=\""
                                    + assignment.getEndDate().toString() + "\" required>");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"status\">Status</label>");
                    out.println("                <select class=\"aira-select\" id=\"status\" name=\"status\">");
                    for (AssignmentStatus s : AssignmentStatus.values()) {
                        boolean selected = s == assignment.getStatus();
                        out.println("                  <option value=\"" + s.name() + "\""
                                + (selected ? " selected" : "") + ">" + s.name() + "</option>");
                    }
                    out.println("                </select>");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-action-group\">");
                    out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Save</button>");
                    out.println("              </div>");
                    out.println("            </form>");
                    String resultsUrl = contextPath + "/admin/es/survey-results?assignmentId="
                            + assignment.getEsTopicMeetingSurveyId();
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + resultsUrl
                            + "\">View Results</a>"
                            + " &nbsp;|&nbsp; <a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/meeting-survey\">Back to Assignments</a></p>");
                    out.println("          </section>");
                });
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
