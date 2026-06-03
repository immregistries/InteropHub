package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicMeetingPoll;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsTopicMeetingPollService;
import org.airahub.interophub.service.PublicUrlService;

public class AdminEsMeetingPollsServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsTopicMeetingDao topicMeetingDao;
    private final EsTopicMeetingPollService pollService;
    private final PublicUrlService publicUrlService;

    public AdminEsMeetingPollsServlet() {
        this.authFlowService = new AuthFlowService();
        this.topicMeetingDao = new EsTopicMeetingDao();
        this.pollService = new EsTopicMeetingPollService();
        this.publicUrlService = new PublicUrlService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        Long esTopicMeetingId = parseId(trimToNull(request.getParameter("esTopicMeetingId")));
        if (esTopicMeetingId == null) {
            renderMeetingSelector(response, contextPath, "Select a meeting to manage polls.");
            return;
        }

        EsTopicMeeting meeting = topicMeetingDao.findById(esTopicMeetingId).orElse(null);
        if (meeting == null) {
            renderMeetingSelector(response, contextPath, "Meeting not found.");
            return;
        }

        List<EsTopicMeetingPoll> polls = pollService.listPollsForMeeting(esTopicMeetingId);
        String message = trimToNull(request.getParameter("message"));
        renderMeetingPolls(response, contextPath, meeting, polls, message);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        Long esTopicMeetingId = parseId(trimToNull(request.getParameter("esTopicMeetingId")));
        if (esTopicMeetingId == null) {
            response.sendRedirect(contextPath + "/admin/es/meeting-polls");
            return;
        }

        String pollName = trimToNull(request.getParameter("pollName"));
        String pollDescription = trimToNull(request.getParameter("pollDescription"));
        String defaultTimezone = trimToNull(request.getParameter("defaultTimezone"));

        try {
            EsTopicMeetingPoll created = pollService.createPoll(esTopicMeetingId, pollName, pollDescription,
                    defaultTimezone);
            response.sendRedirect(contextPath + "/admin/es/meeting-poll?pollId=" + created.getEsTopicMeetingPollId()
                    + "&message=Poll+created");
        } catch (Exception ex) {
            response.sendRedirect(contextPath + "/admin/es/meeting-polls?esTopicMeetingId=" + esTopicMeetingId
                    + "&message=" + urlEncode(ex.getMessage()));
        }
    }

    private void renderMeetingSelector(HttpServletResponse response, String contextPath, String message)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        List<EsTopicMeeting> meetings = topicMeetingDao.findAll();
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Meeting Polls - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Meeting Polls</h2>");
                if (message != null) {
                    panelOut.println("        <p><strong>" + escapeHtml(message) + "</strong></p>");
                }
                panelOut.println("        <form method=\"get\" action=\"" + contextPath + "/admin/es/meeting-polls\">");
                panelOut.println("          <label>Meeting:<br><select name=\"esTopicMeetingId\">");
                for (EsTopicMeeting m : meetings) {
                    panelOut.println("            <option value=\"" + m.getEsTopicMeetingId() + "\">"
                            + escapeHtml(orEmpty(m.getMeetingName()))
                            + " (id=" + m.getEsTopicMeetingId() + ")</option>");
                }
                panelOut.println("          </select></label>");
                panelOut.println("          <button type=\"submit\">Open Polls</button>");
                panelOut.println("        </form>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderMeetingPolls(HttpServletResponse response, String contextPath,
            EsTopicMeeting meeting, List<EsTopicMeetingPoll> polls, String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Meeting Polls - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Meeting Polls</h2>");
                panelOut.println("        <p><strong>Meeting:</strong> " + escapeHtml(orEmpty(meeting.getMeetingName()))
                        + "</p>");
                if (message != null) {
                    panelOut.println("        <p><strong>" + escapeHtml(message) + "</strong></p>");
                }

                panelOut.println("        <table class=\"data-table\">");
                panelOut.println(
                        "          <thead><tr><th>Poll Name</th><th>Timezone</th><th>Public Link</th><th>Actions</th></tr></thead>");
                panelOut.println("          <tbody>");
                for (EsTopicMeetingPoll poll : polls) {
                    String pollPath = "/es/meeting-poll?pollId=" + poll.getEsTopicMeetingPollId();
                    String publicLink = publicUrlService.resolveExternalUrl(pollPath);
                    panelOut.println("            <tr>");
                    panelOut.println("              <td>" + escapeHtml(orEmpty(poll.getPollName())) + "</td>");
                    panelOut.println("              <td>" + escapeHtml(orEmpty(poll.getDefaultTimezone())) + "</td>");
                    panelOut.println("              <td><a href=\"" + escapeHtml(publicLink) + "\">View</a></td>");
                    panelOut.println("              <td><a href=\"" + contextPath + "/admin/es/meeting-poll?pollId="
                            + poll.getEsTopicMeetingPollId() + "\">Edit</a></td>");
                    panelOut.println("            </tr>");
                }
                if (polls.isEmpty()) {
                    panelOut.println("            <tr><td colspan=\"4\">No polls for this meeting yet.</td></tr>");
                }
                panelOut.println("          </tbody>");
                panelOut.println("        </table>");

                panelOut.println("        <h3>Create Poll</h3>");
                panelOut.println(
                        "        <form method=\"post\" action=\"" + contextPath + "/admin/es/meeting-polls\">");
                panelOut.println("          <input type=\"hidden\" name=\"esTopicMeetingId\" value=\""
                        + meeting.getEsTopicMeetingId() + "\">\n");
                panelOut.println(
                        "          <label>Poll Name<br><input type=\"text\" name=\"pollName\" maxlength=\"160\" required></label><br><br>");
                panelOut.println(
                        "          <label>Description<br><textarea name=\"pollDescription\" rows=\"3\"></textarea></label><br><br>");
                panelOut.println("          <label>Default Timezone<br><select name=\"defaultTimezone\">");
                for (String timezone : EsTopicMeetingPollService.ALLOWED_TIMEZONES) {
                    String selected = EsTopicMeetingPollService.DEFAULT_TIMEZONE.equals(timezone) ? " selected" : "";
                    panelOut.println("            <option value=\"" + timezone + "\"" + selected + ">"
                            + timezone + "</option>");
                }
                panelOut.println("          </select></label><br><br>");
                panelOut.println("          <button type=\"submit\">Create Poll</button>");
                panelOut.println("        </form>");

                panelOut.println("        <p><a href=\"" + contextPath + "/admin/es/meetings?meetingId="
                        + meeting.getEsTopicMeetingId() + "\">Back to Meeting</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private Optional<User> requireAdmin(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Optional<User> user = authFlowService.findAuthenticatedUser(request);
        if (user.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return Optional.empty();
        }
        if (!authFlowService.isAdminUser(user.get())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = response.getWriter()) {
                AdminShellRenderer.render(out, "Access Denied - InteropHub", request.getContextPath(), panelOut -> {
                    panelOut.println("      <section class=\"panel\">");
                    panelOut.println("        <h2>Access Denied</h2>");
                    panelOut.println("        <p>Admin access required.</p>");
                    panelOut.println("      </section>");
                });
            }
            return Optional.empty();
        }
        return user;
    }

    private Long parseId(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
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

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(orEmpty(value), java.nio.charset.StandardCharsets.UTF_8);
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
