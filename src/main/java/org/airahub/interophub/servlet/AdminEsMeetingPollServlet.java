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
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsTopicMeetingPollService;
import org.airahub.interophub.service.PublicUrlService;

public class AdminEsMeetingPollServlet extends HttpServlet {

    private static final DateTimeFormatter LOCAL_INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuthFlowService authFlowService;
    private final EsTopicMeetingPollService pollService;
    private final PublicUrlService publicUrlService;

    public AdminEsMeetingPollServlet() {
        this.authFlowService = new AuthFlowService();
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
            renderPage(response, contextPath, poll, options, results, message);
        } catch (Exception ex) {
            response.sendRedirect(contextPath + "/admin/es/meeting-polls?message=" + urlEncode(ex.getMessage()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
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

    private void renderPage(HttpServletResponse response, String contextPath,
            EsTopicMeetingPoll poll, List<EsTopicMeetingPollOption> options,
            EsTopicMeetingPollService.PollResultsData results, String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        String publicPath = "/es/meeting-poll?pollId=" + poll.getEsTopicMeetingPollId();
        String publicUrl = publicUrlService.resolveExternalUrl(publicPath);

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Meeting Poll - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Edit Meeting Poll</h2>");
                if (message != null) {
                    panelOut.println("        <p><strong>" + escapeHtml(message) + "</strong></p>");
                }
                panelOut.println("        <p><strong>Public Link:</strong> <a href=\"" + escapeHtml(publicUrl)
                        + "\">" + escapeHtml(publicUrl) + "</a></p>");

                panelOut.println("        <h3>Poll Settings</h3>");
                panelOut.println("        <form method=\"post\" action=\"" + contextPath + "/admin/es/meeting-poll\">");
                panelOut.println("          <input type=\"hidden\" name=\"action\" value=\"updatePoll\">\n");
                panelOut.println(
                        "          <input type=\"hidden\" name=\"pollId\" value=\"" + poll.getEsTopicMeetingPollId()
                                + "\">\n");
                panelOut.println(
                        "          <label>Poll Name<br><input type=\"text\" name=\"pollName\" maxlength=\"160\" value=\""
                                + escapeHtml(orEmpty(poll.getPollName())) + "\" required></label><br><br>");
                panelOut.println("          <label>Description<br><textarea name=\"pollDescription\" rows=\"3\">"
                        + escapeHtml(orEmpty(poll.getPollDescription())) + "</textarea></label><br><br>");
                panelOut.println("          <label>Default Timezone<br><select name=\"defaultTimezone\">");
                for (String timezone : EsTopicMeetingPollService.ALLOWED_TIMEZONES) {
                    String selected = timezone.equals(poll.getDefaultTimezone()) ? " selected" : "";
                    panelOut.println("            <option value=\"" + timezone + "\"" + selected + ">"
                            + timezone + "</option>");
                }
                panelOut.println("          </select></label><br><br>");
                panelOut.println("          <button type=\"submit\">Save Poll</button>");
                panelOut.println("        </form>");

                panelOut.println("        <h3>Proposed Times</h3>");
                panelOut.println("        <table class=\"data-table\">");
                panelOut.println(
                        "          <thead><tr><th>Order</th><th>Start</th><th>End</th><th>Actions</th></tr></thead>");
                panelOut.println("          <tbody>");
                for (EsTopicMeetingPollOption option : options) {
                    LocalDateTime startLocal = pollService.fromUtcToLocal(option.getStartsAtUtc(),
                            poll.getDefaultTimezone());
                    LocalDateTime endLocal = option.getEndsAtUtc() == null ? null
                            : pollService.fromUtcToLocal(option.getEndsAtUtc(), poll.getDefaultTimezone());
                    panelOut.println("            <tr>");
                    panelOut.println("              <td colspan=\"4\">");
                    panelOut.println("                <form method=\"post\" action=\"" + contextPath
                            + "/admin/es/meeting-poll\" style=\"display:flex;gap:0.5rem;flex-wrap:wrap;align-items:flex-end;\">");
                    panelOut.println(
                            "                  <input type=\"hidden\" name=\"action\" value=\"updateOption\">\n");
                    panelOut.println("                  <input type=\"hidden\" name=\"pollId\" value=\""
                            + poll.getEsTopicMeetingPollId()
                            + "\">\n");
                    panelOut.println("                  <input type=\"hidden\" name=\"optionId\" value=\""
                            + option.getEsTopicMeetingPollOptionId() + "\">\n");
                    panelOut.println(
                            "                  <label>Order<br><input type=\"number\" name=\"displayOrder\" value=\""
                                    + option.getDisplayOrder() + "\" required></label>");
                    panelOut.println(
                            "                  <label>Start<br><input type=\"datetime-local\" name=\"startsAtLocal\" value=\""
                                    + formatLocalForInput(startLocal) + "\" required></label>");
                    panelOut.println(
                            "                  <label>End<br><input type=\"datetime-local\" name=\"endsAtLocal\" value=\""
                                    + formatLocalForInput(endLocal) + "\"></label>");
                    panelOut.println("                  <input type=\"hidden\" name=\"inputTimezone\" value=\""
                            + escapeHtml(poll.getDefaultTimezone()) + "\">\n");
                    panelOut.println("                  <button type=\"submit\">Save Option</button>");
                    panelOut.println("                </form>");
                    panelOut.println("                <form method=\"post\" action=\"" + contextPath
                            + "/admin/es/meeting-poll\" style=\"margin-top:0.25rem;\">");
                    panelOut.println(
                            "                  <input type=\"hidden\" name=\"action\" value=\"deleteOption\">\n");
                    panelOut.println("                  <input type=\"hidden\" name=\"pollId\" value=\""
                            + poll.getEsTopicMeetingPollId()
                            + "\">\n");
                    panelOut.println("                  <input type=\"hidden\" name=\"optionId\" value=\""
                            + option.getEsTopicMeetingPollOptionId() + "\">\n");
                    panelOut.println("                  <button type=\"submit\">Delete Option</button>");
                    panelOut.println("                </form>");
                    panelOut.println("              </td>");
                    panelOut.println("            </tr>");
                }
                if (options.isEmpty()) {
                    panelOut.println("            <tr><td colspan=\"4\">No options yet.</td></tr>");
                }
                panelOut.println("          </tbody>");
                panelOut.println("        </table>");

                panelOut.println("        <h3>Add Option</h3>");
                panelOut.println("        <form method=\"post\" action=\"" + contextPath + "/admin/es/meeting-poll\">");
                panelOut.println("          <input type=\"hidden\" name=\"action\" value=\"addOption\">\n");
                panelOut.println(
                        "          <input type=\"hidden\" name=\"pollId\" value=\"" + poll.getEsTopicMeetingPollId()
                                + "\">\n");
                panelOut.println("          <input type=\"hidden\" name=\"inputTimezone\" value=\""
                        + escapeHtml(poll.getDefaultTimezone()) + "\">\n");
                panelOut.println(
                        "          <label>Start<br><input type=\"datetime-local\" name=\"startsAtLocal\" required></label><br><br>");
                panelOut.println(
                        "          <label>End<br><input type=\"datetime-local\" name=\"endsAtLocal\"></label><br><br>");
                panelOut.println("          <button type=\"submit\">Add Option</button>");
                panelOut.println("        </form>");

                panelOut.println("        <h3>Results</h3>");
                renderResultsTable(panelOut, results, poll.getDefaultTimezone());

                panelOut.println("        <p><a href=\"" + contextPath + "/admin/es/meeting-polls?esTopicMeetingId="
                        + poll.getEsTopicMeetingId() + "\">Back to Meeting Polls</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderResultsTable(PrintWriter out, EsTopicMeetingPollService.PollResultsData results,
            String defaultTimezone) {
        out.println("        <table class=\"data-table\">");
        out.println("          <thead><tr><th>Proposed Time (" + escapeHtml(defaultTimezone)
                + ")</th><th>YES</th><th>MAYBE</th><th>NO</th></tr></thead>");
        out.println("          <tbody>");
        for (EsTopicMeetingPollOption option : results.options()) {
            LocalDateTime localStart = pollService.fromUtcToLocal(option.getStartsAtUtc(), defaultTimezone);
            LocalDateTime localEnd = option.getEndsAtUtc() == null
                    ? null
                    : pollService.fromUtcToLocal(option.getEndsAtUtc(), defaultTimezone);

            Map<PollResponseValue, Integer> counts = results.countsByOption()
                    .get(option.getEsTopicMeetingPollOptionId());
            Map<PollResponseValue, List<String>> names = results.namesByOption()
                    .get(option.getEsTopicMeetingPollOptionId());

            out.println("            <tr>");
            out.println("              <td>" + escapeHtml(formatDisplayRange(localStart, localEnd)) + "</td>");
            out.println("              <td>" + renderCell(counts, names, PollResponseValue.YES) + "</td>");
            out.println("              <td>" + renderCell(counts, names, PollResponseValue.MAYBE) + "</td>");
            out.println("              <td>" + renderCell(counts, names, PollResponseValue.NO) + "</td>");
            out.println("            </tr>");
        }
        if (results.options().isEmpty()) {
            out.println("            <tr><td colspan=\"4\">No poll options found.</td></tr>");
        }
        out.println("          </tbody>");
        out.println("        </table>");
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
