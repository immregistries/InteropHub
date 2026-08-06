package org.airahub.interophub.servlet;

import java.io.IOException;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.EsSurveyService;
import org.airahub.interophub.service.EsSurveyService.QuestionResult;
import org.airahub.interophub.service.EsSurveyService.SurveyResultsData;
import org.airahub.interophub.model.EsSurveyQuestion.QuestionType;

public class AdminEsSurveyResultsServlet extends HttpServlet {

    private final EsSurveyService surveyService;

    public AdminEsSurveyResultsServlet() {
        this.surveyService = new EsSurveyService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        Long assignmentId = parseId(trimToNull(request.getParameter("assignmentId")));
        if (assignmentId == null) {
            renderError(request, response, "Missing assignmentId parameter.");
            return;
        }

        boolean includeAdmin = "true".equals(request.getParameter("includeAdmin"));

        SurveyResultsData results;
        try {
            results = surveyService.getAggregateResults(assignmentId, includeAdmin);
        } catch (Exception ex) {
            renderError(request, response, "Could not load results: " + ex.getMessage());
            return;
        }

        renderResults(request, response, results, includeAdmin);
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    private void renderResults(HttpServletRequest request, HttpServletResponse response,
            SurveyResultsData data, boolean includeAdmin) throws IOException {
        String contextPath = request.getContextPath();
        AdminShellRenderer.render(request, response, "Survey Results - InteropHub", AdminSection.TOPIC_SPACES,
                "/admin/es/surveys", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Results: "
                            + escapeHtml(data.getSurvey().getSurveyName()) + "</h2>");
                    out.println("            <p class=\"aira-meta\">Assignment ID: "
                            + data.getAssignment().getEsTopicMeetingSurveyId()
                            + " | Topic Meeting ID: " + data.getAssignment().getEsTopicMeetingId()
                            + " | Window: " + data.getAssignment().getStartDate()
                            + " to " + data.getAssignment().getEndDate() + "</p>");
                    out.println("            <form class=\"aira-form\" method=\"get\" action=\"" + contextPath
                            + "/admin/es/survey-results\">");
                    out.println("              <input type=\"hidden\" name=\"assignmentId\" value=\""
                            + data.getAssignment().getEsTopicMeetingSurveyId() + "\" />");
                    out.println("              <label class=\"aira-radio\"><input type=\"checkbox\" name=\"includeAdmin\" value=\"true\""
                            + (includeAdmin ? " checked" : "") + " /> Include admin responses</label>");
                    out.println("              <div class=\"aira-action-group\">");
                    out.println(
                            "                <button class=\"aira-button aira-button--primary\" type=\"submit\">Submit</button>");
                    out.println("              </div>");
                    out.println("            </form>");
                    out.println("            <p class=\"aira-meta\"><strong>Total Responses: "
                            + data.getResponseCount() + "</strong></p>");
                    if (data.getExcludedAdminCount() > 0) {
                        if (includeAdmin) {
                            out.println("            <p class=\"aira-meta\">Showing all responses including "
                                    + data.getExcludedAdminCount() + " admin response(s).</p>");
                        } else {
                            out.println("            <p class=\"aira-meta\">Note: " + data.getExcludedAdminCount()
                                    + " admin response(s) excluded."
                                    + " Check &#8220;Include admin responses&#8221; to include them.</p>");
                        }
                    }

                    for (QuestionResult qr : data.getQuestionResults()) {
                        out.println("            <h3 class=\"aira-subsection-title\">Q"
                                + qr.getQuestion().getDisplayOrder() + ": "
                                + escapeHtml(qr.getQuestion().getQuestionText()) + "</h3>");
                        out.println("            <p class=\"aira-meta\">Type: "
                                + qr.getQuestion().getQuestionType().name()
                                + " | Responses: " + qr.getCount() + "</p>");

                        if (qr.getQuestion().getQuestionType() == QuestionType.LIKERT_1_5) {
                            out.println("            <p class=\"aira-meta\">Average: "
                                    + String.format("%.2f", qr.getAverage()) + "</p>");
                            out.println("            <div class=\"aira-table-wrap\">");
                            out.println("            <table class=\"aira-table\">");
                            out.println("              <thead><tr><th>Rating</th><th>Count</th></tr></thead>");
                            out.println("              <tbody>");
                            for (int i = 1; i <= 5; i++) {
                                int count = qr.getDistribution().getOrDefault(i, 0);
                                out.println("                <tr><td>" + i + "</td><td>" + count + "</td></tr>");
                            }
                            out.println("              </tbody>");
                            out.println("            </table>");
                            out.println("            </div>");
                        } else {
                            if (qr.getTextAnswers().isEmpty()) {
                                out.println("            <p class=\"aira-meta\">No text responses.</p>");
                            } else {
                                out.println("            <ul>");
                                for (String text : qr.getTextAnswers()) {
                                    out.println("              <li>" + escapeHtml(text) + "</li>");
                                }
                                out.println("            </ul>");
                            }
                        }
                    }

                    String backUrl = contextPath + "/admin/es/meeting-survey?assignmentId="
                            + data.getAssignment().getEsTopicMeetingSurveyId();
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + backUrl
                            + "\">Back to Assignment</a></p>");
                    out.println("          </section>");
                });
    }

    private void renderError(HttpServletRequest request, HttpServletResponse response,
            String message) throws IOException {
        String contextPath = request.getContextPath();
        AdminShellRenderer.render(request, response, "Survey Results - InteropHub", AdminSection.TOPIC_SPACES,
                "/admin/es/surveys", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Survey Results</h2>");
                    out.println("            <div class=\"aira-alert aira-alert--danger\"><p>"
                            + escapeHtml(message) + "</p></div>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
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
