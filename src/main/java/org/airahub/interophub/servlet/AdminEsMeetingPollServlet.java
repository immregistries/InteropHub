package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.EsTopicMeetingPoll;
import org.airahub.interophub.model.EsTopicMeetingPollOption;
import org.airahub.interophub.model.EsTopicMeetingPollResponse.PollResponseValue;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.EsTopicMeetingPollService;
import org.airahub.interophub.service.PublicUrlService;

public class AdminEsMeetingPollServlet extends HttpServlet {

    private static final DateTimeFormatter LOCAL_INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String ACTIVE_HREF = "/admin/es/meeting-polls";

    private final EsTopicMeetingPollService pollService;
    private final PublicUrlService publicUrlService;

    public AdminEsMeetingPollServlet() {
        this.pollService = new EsTopicMeetingPollService();
        this.publicUrlService = new PublicUrlService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        Long pollId = parseId(trimToNull(request.getParameter("pollId")));
        if (pollId == null) {
            response.sendRedirect(contextPath + "/admin/es/meeting-polls");
            return;
        }

        try {
            EsTopicMeetingPoll poll = pollService.getPollRequired(pollId);
            List<EsTopicMeetingPollOption> options = pollService.listOptionsOrdered(pollId);
            EsTopicMeetingPollService.PollResultsData results = pollService.getResults(pollId);
            String message = trimToNull(request.getParameter("message"));
            renderPage(request, response, poll, options, results, message);
        } catch (Exception ex) {
            response.sendRedirect(contextPath + "/admin/es/meeting-polls?message=" + urlEncode(ex.getMessage()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        Long pollId = parseId(trimToNull(request.getParameter("pollId")));
        if (pollId == null) {
            response.sendRedirect(contextPath + "/admin/es/meeting-polls");
            return;
        }

        String action = trimToNull(request.getParameter("action"));
        if (action == null) {
            response.sendRedirect(contextPath + "/admin/es/meeting-poll?pollId=" + pollId);
            return;
        }

        try {
            if ("updatePoll".equals(action)) {
                pollService.updatePoll(
                        pollId,
                        trimToNull(request.getParameter("pollName")),
                        trimToNull(request.getParameter("pollDescription")),
                        trimToNull(request.getParameter("defaultTimezone")));
            } else if ("addOption".equals(action)) {
                EsTopicMeetingPoll poll = pollService.getPollRequired(pollId);
                String timezone = trimToNull(request.getParameter("inputTimezone"));
                if (timezone == null) {
                    timezone = poll.getDefaultTimezone();
                }
                LocalDateTime startLocal = parseLocalDateTime(trimToNull(request.getParameter("startsAtLocal")));
                LocalDateTime endLocal = parseLocalDateTime(trimToNull(request.getParameter("endsAtLocal")));
                pollService.addOption(pollId, startLocal, endLocal, timezone);
            } else if ("updateOption".equals(action)) {
                Long optionId = parseId(trimToNull(request.getParameter("optionId")));
                Integer displayOrder = parseInt(trimToNull(request.getParameter("displayOrder")));
                LocalDateTime startLocal = parseLocalDateTime(trimToNull(request.getParameter("startsAtLocal")));
                LocalDateTime endLocal = parseLocalDateTime(trimToNull(request.getParameter("endsAtLocal")));
                String timezone = trimToNull(request.getParameter("inputTimezone"));
                pollService.updateOption(pollId, optionId, displayOrder, startLocal, endLocal, timezone);
            } else if ("deleteOption".equals(action)) {
                Long optionId = parseId(trimToNull(request.getParameter("optionId")));
                pollService.deleteOption(pollId, optionId);
            }
            response.sendRedirect(contextPath + "/admin/es/meeting-poll?pollId=" + pollId + "&message=Saved");
        } catch (Exception ex) {
            response.sendRedirect(contextPath + "/admin/es/meeting-poll?pollId=" + pollId + "&message="
                    + urlEncode(ex.getMessage()));
        }
    }

    private void renderPage(HttpServletRequest request, HttpServletResponse response,
            EsTopicMeetingPoll poll, List<EsTopicMeetingPollOption> options,
            EsTopicMeetingPollService.PollResultsData results, String message) throws IOException {
        String contextPath = request.getContextPath();
        String publicPath = "/es/meeting-poll?pollId=" + poll.getEsTopicMeetingPollId();
        String publicUrl = publicUrlService.resolveExternalUrl(publicPath);

        AdminShellRenderer.render(request, response, "Meeting Poll - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Edit Meeting Poll</h2>");
                    if (message != null) {
                        out.println("            <div class=\"aira-alert aira-alert--info\"><p>"
                                + escapeHtml(message) + "</p></div>");
                    }
                    out.println("            <p><strong>Public Link:</strong> <a class=\"aira-inline-link\" href=\""
                            + escapeHtml(publicUrl)
                            + "\">" + escapeHtml(publicUrl) + "</a></p>");

                    out.println("            <h3 class=\"aira-subsection-title\">Poll Settings</h3>");
                    out.println("            <form class=\"aira-form\" method=\"post\" action=\"" + contextPath
                            + "/admin/es/meeting-poll\">");
                    out.println("              <input type=\"hidden\" name=\"action\" value=\"updatePoll\">");
                    out.println(
                            "              <input type=\"hidden\" name=\"pollId\" value=\"" + poll.getEsTopicMeetingPollId()
                                    + "\">");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"pollName\">Poll Name</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"pollName\" type=\"text\" name=\"pollName\" maxlength=\"160\" value=\""
                                    + escapeHtml(orEmpty(poll.getPollName())) + "\" required>");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"pollDescription\">Description</label>");
                    out.println("                <textarea class=\"aira-textarea\" id=\"pollDescription\" name=\"pollDescription\" rows=\"3\">"
                            + escapeHtml(orEmpty(poll.getPollDescription())) + "</textarea>");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"defaultTimezone\">Default Timezone</label>");
                    out.println("                <select class=\"aira-select\" id=\"defaultTimezone\" name=\"defaultTimezone\">");
                    for (String timezone : EsTopicMeetingPollService.ALLOWED_TIMEZONES) {
                        String selected = timezone.equals(poll.getDefaultTimezone()) ? " selected" : "";
                        out.println("                  <option value=\"" + timezone + "\"" + selected + ">"
                                + timezone + "</option>");
                    }
                    out.println("                </select>");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-action-group\">");
                    out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Save Poll</button>");
                    out.println("              </div>");
                    out.println("            </form>");

                    out.println("            <h3 class=\"aira-subsection-title\">Proposed Times</h3>");
                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println(
                            "              <thead><tr><th>Order</th><th>Start</th><th>End</th><th>Actions</th></tr></thead>");
                    out.println("              <tbody>");
                    for (EsTopicMeetingPollOption option : options) {
                        LocalDateTime startLocal = pollService.fromUtcToLocal(option.getStartsAtUtc(),
                                poll.getDefaultTimezone());
                        LocalDateTime endLocal = option.getEndsAtUtc() == null ? null
                                : pollService.fromUtcToLocal(option.getEndsAtUtc(), poll.getDefaultTimezone());
                        out.println("                <tr>");
                        out.println("                  <td colspan=\"4\">");
                        out.println("                    <form class=\"aira-form\" method=\"post\" action=\""
                                + contextPath + "/admin/es/meeting-poll\">");
                        out.println(
                                "                      <input type=\"hidden\" name=\"action\" value=\"updateOption\">");
                        out.println("                      <input type=\"hidden\" name=\"pollId\" value=\""
                                + poll.getEsTopicMeetingPollId()
                                + "\">");
                        out.println("                      <input type=\"hidden\" name=\"optionId\" value=\""
                                + option.getEsTopicMeetingPollOptionId() + "\">");
                        out.println("                      <div class=\"aira-field-row\">");
                        out.println("                        <div class=\"aira-field\">");
                        out.println("                          <label>Order</label>");
                        out.println(
                                "                          <input class=\"aira-input\" type=\"number\" name=\"displayOrder\" value=\""
                                        + option.getDisplayOrder() + "\" required>");
                        out.println("                        </div>");
                        out.println("                        <div class=\"aira-field\">");
                        out.println("                          <label>Start</label>");
                        out.println(
                                "                          <input class=\"aira-input\" type=\"datetime-local\" name=\"startsAtLocal\" value=\""
                                        + formatLocalForInput(startLocal) + "\" required>");
                        out.println("                        </div>");
                        out.println("                        <div class=\"aira-field\">");
                        out.println("                          <label>End</label>");
                        out.println(
                                "                          <input class=\"aira-input\" type=\"datetime-local\" name=\"endsAtLocal\" value=\""
                                        + formatLocalForInput(endLocal) + "\">");
                        out.println("                        </div>");
                        out.println("                      </div>");
                        out.println("                      <input type=\"hidden\" name=\"inputTimezone\" value=\""
                                + escapeHtml(poll.getDefaultTimezone()) + "\">");
                        out.println("                      <div class=\"aira-action-group\">");
                        out.println("                        <button class=\"aira-button aira-button--secondary aira-button--small\" type=\"submit\">Save Option</button>");
                        out.println("                      </div>");
                        out.println("                    </form>");
                        out.println("                    <form method=\"post\" action=\"" + contextPath
                                + "/admin/es/meeting-poll\">");
                        out.println(
                                "                      <input type=\"hidden\" name=\"action\" value=\"deleteOption\">");
                        out.println("                      <input type=\"hidden\" name=\"pollId\" value=\""
                                + poll.getEsTopicMeetingPollId()
                                + "\">");
                        out.println("                      <input type=\"hidden\" name=\"optionId\" value=\""
                                + option.getEsTopicMeetingPollOptionId() + "\">");
                        out.println("                      <div class=\"aira-action-group\">");
                        out.println("                        <button class=\"aira-button aira-button--danger aira-button--small\" type=\"submit\">Delete Option</button>");
                        out.println("                      </div>");
                        out.println("                    </form>");
                        out.println("                  </td>");
                        out.println("                </tr>");
                    }
                    if (options.isEmpty()) {
                        out.println("                <tr><td colspan=\"4\">No options yet.</td></tr>");
                    }
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");

                    out.println("            <h3 class=\"aira-subsection-title\">Add Option</h3>");
                    out.println("            <form class=\"aira-form\" method=\"post\" action=\"" + contextPath
                            + "/admin/es/meeting-poll\">");
                    out.println("              <input type=\"hidden\" name=\"action\" value=\"addOption\">");
                    out.println(
                            "              <input type=\"hidden\" name=\"pollId\" value=\"" + poll.getEsTopicMeetingPollId()
                                    + "\">");
                    out.println("              <input type=\"hidden\" name=\"inputTimezone\" value=\""
                            + escapeHtml(poll.getDefaultTimezone()) + "\">");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"startsAtLocal\">Start</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"startsAtLocal\" type=\"datetime-local\" name=\"startsAtLocal\" required>");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"endsAtLocal\">End</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"endsAtLocal\" type=\"datetime-local\" name=\"endsAtLocal\">");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-action-group\">");
                    out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Add Option</button>");
                    out.println("              </div>");
                    out.println("            </form>");

                    out.println("            <h3 class=\"aira-subsection-title\">Results</h3>");
                    renderResultsTable(out, results, poll.getDefaultTimezone());

                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/meeting-polls?esTopicMeetingId="
                            + poll.getEsTopicMeetingId() + "\">Back to Meeting Polls</a></p>");
                    out.println("          </section>");
                });
    }

    private void renderResultsTable(PrintWriter out, EsTopicMeetingPollService.PollResultsData results,
            String defaultTimezone) {
        out.println("            <div class=\"aira-table-wrap\">");
        out.println("            <table class=\"aira-table\">");
        out.println("              <thead><tr><th>Proposed Time (" + escapeHtml(defaultTimezone)
                + ")</th><th>YES</th><th>MAYBE</th><th>NO</th></tr></thead>");
        out.println("              <tbody>");
        for (EsTopicMeetingPollOption option : results.options()) {
            LocalDateTime localStart = pollService.fromUtcToLocal(option.getStartsAtUtc(), defaultTimezone);
            LocalDateTime localEnd = option.getEndsAtUtc() == null
                    ? null
                    : pollService.fromUtcToLocal(option.getEndsAtUtc(), defaultTimezone);

            Map<PollResponseValue, Integer> counts = results.countsByOption()
                    .get(option.getEsTopicMeetingPollOptionId());
            Map<PollResponseValue, List<String>> names = results.namesByOption()
                    .get(option.getEsTopicMeetingPollOptionId());

            out.println("                <tr>");
            out.println("                  <td>" + escapeHtml(formatDisplayRange(localStart, localEnd)) + "</td>");
            out.println("                  <td>" + renderCell(counts, names, PollResponseValue.YES) + "</td>");
            out.println("                  <td>" + renderCell(counts, names, PollResponseValue.MAYBE) + "</td>");
            out.println("                  <td>" + renderCell(counts, names, PollResponseValue.NO) + "</td>");
            out.println("                </tr>");
        }
        if (results.options().isEmpty()) {
            out.println("                <tr><td colspan=\"4\">No poll options found.</td></tr>");
        }
        out.println("              </tbody>");
        out.println("            </table>");
        out.println("            </div>");
    }

    private String renderCell(Map<PollResponseValue, Integer> counts,
            Map<PollResponseValue, List<String>> names,
            PollResponseValue key) {
        int count = counts != null && counts.get(key) != null ? counts.get(key) : 0;
        String namesText = "";
        if (names != null && names.get(key) != null && !names.get(key).isEmpty()) {
            namesText = "<br><small>" + escapeHtml(String.join(", ", names.get(key))) + "</small>";
        }
        return count + namesText;
    }

    private String formatDisplayRange(LocalDateTime localStart, LocalDateTime localEnd) {
        if (localStart == null) {
            return "";
        }
        String start = DISPLAY_FORMAT.format(localStart);
        if (localEnd == null) {
            return start;
        }
        return start + " to " + DISPLAY_FORMAT.format(localEnd);
    }

    private String formatLocalForInput(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return LOCAL_INPUT_FORMAT.format(value);
    }

    private LocalDateTime parseLocalDateTime(String value) {
        if (value == null) {
            return null;
        }
        return LocalDateTime.parse(value, LOCAL_INPUT_FORMAT);
    }

    private Long parseId(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer parseInt(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
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
