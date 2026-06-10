package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
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
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsSurveyService;

public class AdminEsTopicMeetingSurveyServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsSurveyService surveyService;
    private final EsTopicMeetingDao topicMeetingDao;

    public AdminEsTopicMeetingSurveyServlet() {
        this.authFlowService = new AuthFlowService();
        this.surveyService = new EsSurveyService();
        this.topicMeetingDao = new EsTopicMeetingDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String action = trimToNull(request.getParameter("action"));
        Long assignmentId = parseId(trimToNull(request.getParameter("assignmentId")));
        Long meetingId = parseId(trimToNull(request.getParameter("meetingId")));

        if ("new".equals(action)) {
            renderCreateForm(response, contextPath, meetingId, null, null);
            return;
        }

        if (assignmentId != null) {
            EsTopicMeetingSurvey assignment = surveyService.getTopicMeetingSurvey(assignmentId).orElse(null);
            if (assignment == null) {
                renderList(response, contextPath, "Assignment not found.");
                return;
            }
            String savedMsg = request.getParameter("saved") != null ? "Assignment saved." : null;
            renderEditForm(response, contextPath, assignment, savedMsg, null);
            return;
        }

        renderList(response, contextPath, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
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
                renderCreateForm(response, contextPath, esTopicMeetingId,
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
                renderCreateForm(response, contextPath, esTopicMeetingId,
                        "Invalid date format. Use YYYY-MM-DD.", null);
            } catch (Exception ex) {
                renderCreateForm(response, contextPath, esTopicMeetingId, ex.getMessage(), null);
            }
            return;
        }

        Long assignmentId = parseId(trimToNull(request.getParameter("assignmentId")));
        if (assignmentId == null) {
            renderList(response, contextPath, "Missing assignment ID.");
            return;
        }

        if ("update".equals(action)) {
            String startRaw = trimToNull(request.getParameter("startDate"));
            String endRaw = trimToNull(request.getParameter("endDate"));
            String statusRaw = trimToNull(request.getParameter("status"));
            EsTopicMeetingSurvey assignment = surveyService.getTopicMeetingSurvey(assignmentId).orElse(null);
            if (assignment == null) {
                renderList(response, contextPath, "Assignment not found.");
                return;
            }
            if (startRaw == null || endRaw == null || statusRaw == null) {
                renderEditForm(response, contextPath, assignment, null, "All fields are required.");
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
                renderEditForm(response, contextPath, assignment, null,
                        "Invalid date format. Use YYYY-MM-DD.");
            } catch (Exception ex) {
                renderEditForm(response, contextPath, assignment, null, ex.getMessage());
            }
            return;
        }

        renderList(response, contextPath, "Unknown action.");
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    private void renderList(HttpServletResponse response, String contextPath,
            String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        List<EsTopicMeetingSurvey> assignments = surveyService.listTopicMeetingSurveys();
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Meeting Survey Assignments - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Meeting Survey Assignments</h2>");
                if (message != null) {
                    panelOut.println("        <p><strong>" + escapeHtml(message) + "</strong></p>");
                }
                panelOut.println("        <p><a href=\"" + contextPath
                        + "/admin/es/meeting-survey?action=new\" class=\"button\">+ New Assignment</a></p>");
                panelOut.println("        <table class=\"data-table\">");
                panelOut.println("          <thead><tr>"
                        + "<th>Survey</th><th>Meeting</th>"
                        + "<th>Start</th><th>Status</th><th>Actions</th>"
                        + "</tr></thead>");
                panelOut.println("          <tbody>");
                for (EsTopicMeetingSurvey a : assignments) {
                    EsSurvey survey = surveyService.getSurvey(a.getEsSurveyId()).orElse(null);
                    EsTopicMeeting meeting = topicMeetingDao.findById(a.getEsTopicMeetingId()).orElse(null);
                    String surveyName = survey != null ? survey.getSurveyName() : "?";
                    String meetingName = meeting != null ? orEmpty(meeting.getMeetingName()) : "?";
                    String editUrl = contextPath + "/admin/es/meeting-survey?assignmentId="
                            + a.getEsTopicMeetingSurveyId();
                    String resultsUrl = contextPath + "/admin/es/survey-results?assignmentId="
                            + a.getEsTopicMeetingSurveyId();
                    panelOut.println("            <tr>");
                    panelOut.println("              <td>" + escapeHtml(surveyName) + "</td>");
                    panelOut.println("              <td>" + escapeHtml(meetingName) + "</td>");
                    panelOut.println("              <td>" + escapeHtml(a.getStartDate().toString()) + "</td>");
                    panelOut.println("              <td>" + escapeHtml(a.getStatus() != null
                            ? a.getStatus().name()
                            : "") + "</td>");
                    panelOut.println("              <td><a href=\"" + editUrl + "\">Edit</a>"
                            + " | <a href=\"" + resultsUrl + "\">Results</a></td>");
                    panelOut.println("            </tr>");
                }
                if (assignments.isEmpty()) {
                    panelOut.println("            <tr><td colspan=\"5\">No assignments found.</td></tr>");
                }
                panelOut.println("          </tbody>");
                panelOut.println("        </table>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderCreateForm(HttpServletResponse response, String contextPath,
            Long preselectedMeetingId, String errorMessage, String successMessage) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        List<EsSurvey> readySurveys = surveyService.listSurveys().stream()
                .filter(s -> s.getStatus() == SurveyStatus.READY)
                .toList();
        List<EsTopicMeeting> allMeetings = topicMeetingDao.findAll();
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "New Assignment - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>New Meeting Survey Assignment</h2>");
                if (errorMessage != null) {
                    panelOut.println("        <p class=\"error\"><strong>"
                            + escapeHtml(errorMessage) + "</strong></p>");
                }
                if (successMessage != null) {
                    panelOut.println("        <p><strong>" + escapeHtml(successMessage) + "</strong></p>");
                }
                panelOut.println("        <form method=\"post\" action=\""
                        + contextPath + "/admin/es/meeting-survey\">");
                panelOut.println("          <input type=\"hidden\" name=\"action\" value=\"create\">");
                panelOut.println("          <label>Topic Meeting:<br><select name=\"esTopicMeetingId\">");
                for (EsTopicMeeting m : allMeetings) {
                    boolean selected = preselectedMeetingId != null
                            && preselectedMeetingId.equals(m.getEsTopicMeetingId());
                    panelOut.println("            <option value=\"" + m.getEsTopicMeetingId() + "\""
                            + (selected ? " selected" : "") + ">"
                            + escapeHtml(orEmpty(m.getMeetingName()))
                            + " (id=" + m.getEsTopicMeetingId() + ")</option>");
                }
                panelOut.println("          </select></label><br><br>");
                panelOut.println("          <label>Survey (READY only):<br>"
                        + "<select name=\"esSurveyId\">");
                for (EsSurvey s : readySurveys) {
                    panelOut.println("            <option value=\"" + s.getEsSurveyId() + "\">"
                            + escapeHtml(s.getSurveyName()) + "</option>");
                }
                panelOut.println("          </select></label><br><br>");
                panelOut.println("          <label>Start Date (YYYY-MM-DD):<br>"
                        + "<input type=\"date\" name=\"startDate\" required></label><br><br>");
                panelOut.println("          <label>End Date (YYYY-MM-DD):<br>"
                        + "<input type=\"date\" name=\"endDate\" required></label><br><br>");
                panelOut.println("          <button type=\"submit\">Create Assignment</button>");
                panelOut.println("          &nbsp; <a href=\"" + contextPath
                        + "/admin/es/meeting-survey\">Cancel</a>");
                panelOut.println("        </form>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderEditForm(HttpServletResponse response, String contextPath,
            EsTopicMeetingSurvey assignment, String successMessage,
            String errorMessage) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        EsSurvey survey = surveyService.getSurvey(assignment.getEsSurveyId()).orElse(null);
        EsTopicMeeting meeting = topicMeetingDao.findById(assignment.getEsTopicMeetingId()).orElse(null);
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Edit Assignment - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Meeting Survey Assignment #"
                        + assignment.getEsTopicMeetingSurveyId() + "</h2>");
                panelOut.println("        <p>Survey: <strong>"
                        + escapeHtml(survey != null ? survey.getSurveyName() : "?") + "</strong></p>");
                panelOut.println("        <p>Meeting: <strong>"
                        + escapeHtml(meeting != null ? orEmpty(meeting.getMeetingName()) : "?") + "</strong></p>");
                if (successMessage != null) {
                    panelOut.println("        <p><strong>" + escapeHtml(successMessage) + "</strong></p>");
                }
                if (errorMessage != null) {
                    panelOut.println("        <p class=\"error\"><strong>"
                            + escapeHtml(errorMessage) + "</strong></p>");
                }
                panelOut.println("        <form method=\"post\" action=\""
                        + contextPath + "/admin/es/meeting-survey\">");
                panelOut.println("          <input type=\"hidden\" name=\"action\" value=\"update\">");
                panelOut.println("          <input type=\"hidden\" name=\"assignmentId\" value=\""
                        + assignment.getEsTopicMeetingSurveyId() + "\">");
                panelOut.println("          <label>Start Date:<br>"
                        + "<input type=\"date\" name=\"startDate\" value=\""
                        + assignment.getStartDate().toString() + "\" required></label><br><br>");
                panelOut.println("          <label>End Date:<br>"
                        + "<input type=\"date\" name=\"endDate\" value=\""
                        + assignment.getEndDate().toString() + "\" required></label><br><br>");
                panelOut.println("          <label>Status:<br><select name=\"status\">");
                for (AssignmentStatus s : AssignmentStatus.values()) {
                    boolean selected = s == assignment.getStatus();
                    panelOut.println("            <option value=\"" + s.name() + "\""
                            + (selected ? " selected" : "") + ">" + s.name() + "</option>");
                }
                panelOut.println("          </select></label><br><br>");
                panelOut.println("          <button type=\"submit\">Save</button>");
                panelOut.println("        </form>");
                String resultsUrl = contextPath + "/admin/es/survey-results?assignmentId="
                        + assignment.getEsTopicMeetingSurveyId();
                panelOut.println("        <br><a href=\"" + resultsUrl + "\">View Results</a>");
                panelOut.println("        &nbsp;|&nbsp;<a href=\"" + contextPath
                        + "/admin/es/meeting-survey\">Back to Assignments</a>");
                panelOut.println("      </section>");
            });
        }
    }

    // -------------------------------------------------------------------------
    // Admin auth
    // -------------------------------------------------------------------------

    private Optional<User> requireAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return Optional.empty();
        }
        if (!authFlowService.isAdminUser(authenticatedUser.get())) {
            renderForbidden(response, request.getContextPath());
            return Optional.empty();
        }
        return authenticatedUser;
    }

    private void renderForbidden(HttpServletResponse response, String contextPath) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Access Denied - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Access Denied</h2>");
                panelOut.println("        <p>You must be an InteropHub admin.</p>");
                panelOut.println("        <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
                panelOut.println("      </section>");
            });
        }
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
