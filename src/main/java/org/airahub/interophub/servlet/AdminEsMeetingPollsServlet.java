package org.airahub.interophub.servlet;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicMeetingPoll;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.EsTopicMeetingPollService;
import org.airahub.interophub.service.PublicUrlService;

public class AdminEsMeetingPollsServlet extends HttpServlet {

    private static final String ACTIVE_HREF = "/admin/es/meeting-polls";

    private final EsTopicMeetingDao topicMeetingDao;
    private final EsTopicMeetingPollService pollService;
    private final PublicUrlService publicUrlService;

    public AdminEsMeetingPollsServlet() {
        this.topicMeetingDao = new EsTopicMeetingDao();
        this.pollService = new EsTopicMeetingPollService();
        this.publicUrlService = new PublicUrlService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        Long esTopicMeetingId = parseId(trimToNull(request.getParameter("esTopicMeetingId")));
        if (esTopicMeetingId == null) {
            renderMeetingSelector(request, response, "Select a meeting to manage polls.");
            return;
        }

        EsTopicMeeting meeting = topicMeetingDao.findById(esTopicMeetingId).orElse(null);
        if (meeting == null) {
            renderMeetingSelector(request, response, "Meeting not found.");
            return;
        }

        List<EsTopicMeetingPoll> polls = pollService.listPollsForMeeting(esTopicMeetingId);
        String message = trimToNull(request.getParameter("message"));
        renderMeetingPolls(request, response, meeting, polls, message);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
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

    private void renderMeetingSelector(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        String contextPath = request.getContextPath();
        List<EsTopicMeeting> meetings = topicMeetingDao.findAll();
        AdminShellRenderer.render(request, response, "Meeting Polls - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Meeting Polls</h2>");
                    if (message != null) {
                        out.println("            <div class=\"aira-alert aira-alert--info\"><p>"
                                + escapeHtml(message) + "</p></div>");
                    }
                    out.println("            <form class=\"aira-form\" method=\"get\" action=\"" + contextPath
                            + "/admin/es/meeting-polls\">");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"esTopicMeetingId\">Meeting</label>");
                    out.println("                <select class=\"aira-select\" id=\"esTopicMeetingId\" name=\"esTopicMeetingId\">");
                    for (EsTopicMeeting m : meetings) {
                        out.println("                  <option value=\"" + m.getEsTopicMeetingId() + "\">"
                                + escapeHtml(orEmpty(m.getMeetingName()))
                                + " (id=" + m.getEsTopicMeetingId() + ")</option>");
                    }
                    out.println("                </select>");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-action-group\">");
                    out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Open Polls</button>");
                    out.println("              </div>");
                    out.println("            </form>");
                    out.println("          </section>");
                });
    }

    private void renderMeetingPolls(HttpServletRequest request, HttpServletResponse response,
            EsTopicMeeting meeting, List<EsTopicMeetingPoll> polls, String message) throws IOException {
        String contextPath = request.getContextPath();
        AdminShellRenderer.render(request, response, "Meeting Polls - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Meeting Polls</h2>");
                    out.println("            <p><strong>Meeting:</strong> " + escapeHtml(orEmpty(meeting.getMeetingName()))
                            + "</p>");
                    if (message != null) {
                        out.println("            <div class=\"aira-alert aira-alert--info\"><p>"
                                + escapeHtml(message) + "</p></div>");
                    }

                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println(
                            "              <thead><tr><th>Poll Name</th><th>Timezone</th><th>Public Link</th><th>Actions</th></tr></thead>");
                    out.println("              <tbody>");
                    for (EsTopicMeetingPoll poll : polls) {
                        String pollPath = "/es/meeting-poll?pollId=" + poll.getEsTopicMeetingPollId();
                        String publicLink = publicUrlService.resolveExternalUrl(pollPath);
                        out.println("                <tr>");
                        out.println("                  <td>" + escapeHtml(orEmpty(poll.getPollName())) + "</td>");
                        out.println("                  <td>" + escapeHtml(orEmpty(poll.getDefaultTimezone())) + "</td>");
                        out.println("                  <td><a class=\"aira-inline-link\" href=\"" + escapeHtml(publicLink)
                                + "\">View</a></td>");
                        out.println("                  <td><a class=\"aira-inline-link\" href=\"" + contextPath
                                + "/admin/es/meeting-poll?pollId="
                                + poll.getEsTopicMeetingPollId() + "\">Edit</a></td>");
                        out.println("                </tr>");
                    }
                    if (polls.isEmpty()) {
                        out.println("                <tr><td colspan=\"4\">No polls for this meeting yet.</td></tr>");
                    }
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");

                    out.println("            <h3 class=\"aira-subsection-title\">Create Poll</h3>");
                    out.println(
                            "            <form class=\"aira-form\" method=\"post\" action=\"" + contextPath
                                    + "/admin/es/meeting-polls\">");
                    out.println("              <input type=\"hidden\" name=\"esTopicMeetingId\" value=\""
                            + meeting.getEsTopicMeetingId() + "\">");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"pollName\">Poll Name</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"pollName\" type=\"text\" name=\"pollName\" maxlength=\"160\" required>");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"pollDescription\">Description</label>");
                    out.println(
                            "                <textarea class=\"aira-textarea\" id=\"pollDescription\" name=\"pollDescription\" rows=\"3\"></textarea>");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"defaultTimezone\">Default Timezone</label>");
                    out.println("                <select class=\"aira-select\" id=\"defaultTimezone\" name=\"defaultTimezone\">");
                    for (String timezone : EsTopicMeetingPollService.ALLOWED_TIMEZONES) {
                        String selected = EsTopicMeetingPollService.DEFAULT_TIMEZONE.equals(timezone) ? " selected" : "";
                        out.println("                  <option value=\"" + timezone + "\"" + selected + ">"
                                + timezone + "</option>");
                    }
                    out.println("                </select>");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-action-group\">");
                    out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Create Poll</button>");
                    out.println("              </div>");
                    out.println("            </form>");

                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/meetings?meetingId="
                            + meeting.getEsTopicMeetingId() + "\">Back to Meeting</a></p>");
                    out.println("          </section>");
                });
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
