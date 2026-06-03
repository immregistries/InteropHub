package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.LinkedHashMap;
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

public class EsMeetingPollServlet extends HttpServlet {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a",
            Locale.ENGLISH);

    private final AuthFlowService authFlowService;
    private final EsTopicMeetingPollService pollService;

    public EsMeetingPollServlet() {
        this.authFlowService = new AuthFlowService();
        this.pollService = new EsTopicMeetingPollService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        String contextPath = request.getContextPath();
        Long pollId = parseId(trimToNull(request.getParameter("pollId")));
        if (pollId == null) {
            renderError(response, contextPath, "No poll specified.");
            return;
        }

        try {
            User user = authenticatedUser.get();
            EsTopicMeetingPoll poll = pollService.getPollRequired(pollId);
            List<EsTopicMeetingPollOption> options = pollService.listOptionsOrdered(pollId);

            String selectedTimezone = trimToNull(request.getParameter("timezoneId"));
            String effectiveTimezone = selectedTimezone != null
                    ? selectedTimezone
                    : pollService.resolveEffectiveTimezone(user, poll);
            pollService.updateUserTimezone(user, effectiveTimezone);

            Map<Long, PollResponseValue> currentResponses = pollService.getCurrentUserResponses(pollId,
                    user.getUserId());
            EsTopicMeetingPollService.PollResultsData results = pollService.getResults(pollId);

            String message = trimToNull(request.getParameter("message"));
            renderPage(response, contextPath, poll, options, effectiveTimezone, currentResponses, results, message);
        } catch (Exception ex) {
            renderError(response, contextPath, ex.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        String contextPath = request.getContextPath();
        Long pollId = parseId(trimToNull(request.getParameter("pollId")));
        if (pollId == null) {
            renderError(response, contextPath, "No poll specified.");
            return;
        }

        User user = authenticatedUser.get();
        String action = trimToNull(request.getParameter("action"));
        try {
            if ("updateTimezone".equals(action)) {
                String timezoneId = trimToNull(request.getParameter("timezoneId"));
                pollService.updateUserTimezone(user, timezoneId);
                response.sendRedirect(contextPath + "/es/meeting-poll?pollId=" + pollId + "&timezoneId="
                        + urlEncode(timezoneId) + "&message=Timezone+updated");
                return;
            }

            List<EsTopicMeetingPollOption> options = pollService.listOptionsOrdered(pollId);
            Map<Long, PollResponseValue> responses = new LinkedHashMap<>();
            for (EsTopicMeetingPollOption option : options) {
                String rawValue = trimToNull(
                        request.getParameter("response_" + option.getEsTopicMeetingPollOptionId()));
                if (rawValue == null) {
                    continue;
                }
                PollResponseValue responseValue = parseResponseValue(rawValue);
                if (responseValue == null) {
                    throw new IllegalArgumentException("Response values must be YES, MAYBE, or NO.");
                }
                responses.put(option.getEsTopicMeetingPollOptionId(), responseValue);
            }
            pollService.saveUserResponses(pollId, user, responses);

            String timezoneId = trimToNull(request.getParameter("timezoneId"));
            if (timezoneId != null) {
                pollService.updateUserTimezone(user, timezoneId);
            }

            response.sendRedirect(contextPath + "/es/meeting-poll?pollId=" + pollId
                    + "&timezoneId=" + urlEncode(timezoneId)
                    + "&message=Responses+saved");
        } catch (Exception ex) {
            response.sendRedirect(contextPath + "/es/meeting-poll?pollId=" + pollId + "&message="
                    + urlEncode(ex.getMessage()));
        }
    }

    private void renderPage(HttpServletResponse response, String contextPath,
            EsTopicMeetingPoll poll, List<EsTopicMeetingPollOption> options,
            String effectiveTimezone, Map<Long, PollResponseValue> currentResponses,
            EsTopicMeetingPollService.PollResultsData results,
            String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head><meta charset=\"UTF-8\">");
            out.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
            out.println("<title>" + escapeHtml(orEmpty(poll.getPollName())) + " - InteropHub</title>");
            out.println("<link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\">");
            out.println("</head><body>");
            out.println("<main class=\"container\">");
            out.println("  <h1>" + escapeHtml(orEmpty(poll.getPollName())) + "</h1>");
            if (poll.getPollDescription() != null) {
                out.println("  <p>" + escapeHtml(poll.getPollDescription()) + "</p>");
            }
            if (message != null) {
                out.println("  <p><strong>" + escapeHtml(message) + "</strong></p>");
            }

            out.println("  <form method=\"post\" action=\"" + contextPath + "/es/meeting-poll\">");
            out.println("    <input type=\"hidden\" name=\"action\" value=\"updateTimezone\">");
            out.println("    <input type=\"hidden\" name=\"pollId\" value=\"" + poll.getEsTopicMeetingPollId() + "\">");
            out.println("    <label>Timezone:");
            out.println("      <select name=\"timezoneId\">");
            for (String timezone : EsTopicMeetingPollService.ALLOWED_TIMEZONES) {
                String selected = timezone.equals(effectiveTimezone) ? " selected" : "";
                out.println("        <option value=\"" + timezone + "\"" + selected + ">" + timezone + "</option>");
            }
            out.println("      </select>");
            out.println("    </label>");
            out.println("    <button type=\"submit\">Update Timezone</button>");
            out.println("  </form>");

            out.println("  <h2>Your Availability</h2>");
            out.println("  <form method=\"post\" action=\"" + contextPath + "/es/meeting-poll\">");
            out.println("    <input type=\"hidden\" name=\"pollId\" value=\"" + poll.getEsTopicMeetingPollId() + "\">");
            out.println(
                    "    <input type=\"hidden\" name=\"timezoneId\" value=\"" + escapeHtml(effectiveTimezone) + "\">");
            out.println("    <table class=\"data-table\">");
            out.println("      <thead><tr><th>Proposed Time (" + escapeHtml(effectiveTimezone)
                    + ")</th><th>YES</th><th>MAYBE</th><th>NO</th></tr></thead>");
            out.println("      <tbody>");
            for (EsTopicMeetingPollOption option : options) {
                LocalDateTime startLocal = pollService.fromUtcToLocal(option.getStartsAtUtc(), effectiveTimezone);
                LocalDateTime endLocal = option.getEndsAtUtc() == null
                        ? null
                        : pollService.fromUtcToLocal(option.getEndsAtUtc(), effectiveTimezone);
                PollResponseValue selectedResponse = currentResponses.get(option.getEsTopicMeetingPollOptionId());
                out.println("        <tr>");
                out.println("          <td>" + escapeHtml(formatRange(startLocal, endLocal)) + "</td>");
                out.println("          <td><label><input type=\"radio\" name=\"response_"
                        + option.getEsTopicMeetingPollOptionId() + "\" value=\"YES\""
                        + checked(selectedResponse, PollResponseValue.YES) + "> YES</label></td>");
                out.println("          <td><label><input type=\"radio\" name=\"response_"
                        + option.getEsTopicMeetingPollOptionId() + "\" value=\"MAYBE\""
                        + checked(selectedResponse, PollResponseValue.MAYBE) + "> MAYBE</label></td>");
                out.println("          <td><label><input type=\"radio\" name=\"response_"
                        + option.getEsTopicMeetingPollOptionId() + "\" value=\"NO\""
                        + checked(selectedResponse, PollResponseValue.NO) + "> NO</label></td>");
                out.println("        </tr>");
            }
            if (options.isEmpty()) {
                out.println("        <tr><td colspan=\"4\">No poll options available.</td></tr>");
            }
            out.println("      </tbody>");
            out.println("    </table>");
            out.println("    <button type=\"submit\">Save Responses</button>");
            out.println("  </form>");

            if (hasAnyResponses(results)) {
                out.println("  <h2>Results</h2>");
                renderResults(out, results, effectiveTimezone);
            } else {
                out.println("  <h2>Results</h2>");
                out.println("  <p>No responses yet. Submit your availability to see results.</p>");
            }
            out.println("  <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
            out.println("</main>");
            PageFooterRenderer.render(out);
            out.println("</body></html>");
        }
    }

    private void renderResults(PrintWriter out, EsTopicMeetingPollService.PollResultsData results, String timezoneId) {
        out.println("  <table class=\"data-table\">");
        out.println("    <thead><tr><th>Proposed Time (" + escapeHtml(timezoneId)
                + ")</th><th>YES</th><th>MAYBE</th><th>NO</th></tr></thead>");
        out.println("    <tbody>");
        for (EsTopicMeetingPollOption option : results.options()) {
            LocalDateTime startLocal = pollService.fromUtcToLocal(option.getStartsAtUtc(), timezoneId);
            LocalDateTime endLocal = option.getEndsAtUtc() == null
                    ? null
                    : pollService.fromUtcToLocal(option.getEndsAtUtc(), timezoneId);
            Map<PollResponseValue, Integer> counts = results.countsByOption()
                    .get(option.getEsTopicMeetingPollOptionId());
            Map<PollResponseValue, List<String>> names = results.namesByOption()
                    .get(option.getEsTopicMeetingPollOptionId());
            out.println("      <tr>");
            out.println("        <td>" + escapeHtml(formatRange(startLocal, endLocal)) + "</td>");
            out.println("        <td>" + renderCell(counts, names, PollResponseValue.YES) + "</td>");
            out.println("        <td>" + renderCell(counts, names, PollResponseValue.MAYBE) + "</td>");
            out.println("        <td>" + renderCell(counts, names, PollResponseValue.NO) + "</td>");
            out.println("      </tr>");
        }
        if (results.options().isEmpty()) {
            out.println("      <tr><td colspan=\"4\">No poll options available.</td></tr>");
        }
        out.println("    </tbody>");
        out.println("  </table>");
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

    private boolean hasAnyResponses(EsTopicMeetingPollService.PollResultsData results) {
        for (Map<PollResponseValue, Integer> countByResponse : results.countsByOption().values()) {
            if (countByResponse == null) {
                continue;
            }
            for (Integer count : countByResponse.values()) {
                if (count != null && count > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void renderError(HttpServletResponse response, String contextPath, String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\"><head><meta charset=\"UTF-8\">");
            out.println("<title>Meeting Poll - InteropHub</title>");
            out.println("<link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\">");
            out.println("</head><body><main class=\"container\">");
            out.println("  <h1>Meeting Poll</h1>");
            out.println("  <p>" + escapeHtml(message) + "</p>");
            out.println("  <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
            out.println("</main>");
            PageFooterRenderer.render(out);
            out.println("</body></html>");
        }
    }

    private PollResponseValue parseResponseValue(String value) {
        try {
            return PollResponseValue.valueOf(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String checked(PollResponseValue value, PollResponseValue target) {
        return value == target ? " checked" : "";
    }

    private String formatRange(LocalDateTime startLocal, LocalDateTime endLocal) {
        if (startLocal == null) {
            return "";
        }
        String start = DISPLAY_FORMAT.format(startLocal);
        if (endLocal == null) {
            return start;
        }
        return start + " to " + DISPLAY_FORMAT.format(endLocal);
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
