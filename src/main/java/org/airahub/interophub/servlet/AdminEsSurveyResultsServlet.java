package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsSurveyService;
import org.airahub.interophub.service.EsSurveyService.QuestionResult;
import org.airahub.interophub.service.EsSurveyService.SurveyResultsData;
import org.airahub.interophub.model.EsSurveyQuestion.QuestionType;

public class AdminEsSurveyResultsServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsSurveyService surveyService;

    public AdminEsSurveyResultsServlet() {
        this.authFlowService = new AuthFlowService();
        this.surveyService = new EsSurveyService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        Long assignmentId = parseId(trimToNull(request.getParameter("assignmentId")));
        if (assignmentId == null) {
            renderError(response, contextPath, "Missing assignmentId parameter.");
            return;
        }

        SurveyResultsData results;
        try {
            results = surveyService.getAggregateResults(assignmentId);
        } catch (Exception ex) {
            renderError(response, contextPath, "Could not load results: " + ex.getMessage());
            return;
        }

        renderResults(response, contextPath, results);
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    private void renderResults(HttpServletResponse response, String contextPath,
            SurveyResultsData data) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Survey Results - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Results: " + escapeHtml(data.getSurvey().getSurveyName()) + "</h2>");
                panelOut.println("        <p>Assignment ID: " + data.getAssignment().getEsTopicMeetingSurveyId()
                        + " | Topic Meeting ID: " + data.getAssignment().getEsTopicMeetingId()
                        + " | Window: " + data.getAssignment().getStartDate()
                        + " to " + data.getAssignment().getEndDate() + "</p>");
                panelOut.println("        <p><strong>Total Responses: " + data.getResponseCount()
                        + "</strong></p>");

                for (QuestionResult qr : data.getQuestionResults()) {
                    panelOut.println("        <hr>");
                    panelOut.println("        <h3>Q" + qr.getQuestion().getDisplayOrder() + ": "
                            + escapeHtml(qr.getQuestion().getQuestionText()) + "</h3>");
                    panelOut.println("        <p>Type: " + qr.getQuestion().getQuestionType().name()
                            + " | Responses: " + qr.getCount() + "</p>");

                    if (qr.getQuestion().getQuestionType() == QuestionType.LIKERT_1_5) {
                        panelOut.println("        <p>Average: "
                                + String.format("%.2f", qr.getAverage()) + "</p>");
                        panelOut.println("        <table>");
                        panelOut.println("          <thead><tr><th>Rating</th><th>Count</th></tr></thead>");
                        panelOut.println("          <tbody>");
                        for (int i = 1; i <= 5; i++) {
                            int count = qr.getDistribution().getOrDefault(i, 0);
                            panelOut.println("            <tr><td>" + i + "</td><td>" + count + "</td></tr>");
                        }
                        panelOut.println("          </tbody>");
                        panelOut.println("        </table>");
                    } else {
                        if (qr.getTextAnswers().isEmpty()) {
                            panelOut.println("        <p>No text responses.</p>");
                        } else {
                            panelOut.println("        <ul>");
                            for (String text : qr.getTextAnswers()) {
                                panelOut.println("          <li>" + escapeHtml(text) + "</li>");
                            }
                            panelOut.println("        </ul>");
                        }
                    }
                }

                String backUrl = contextPath + "/admin/es/meeting-survey?assignmentId="
                        + data.getAssignment().getEsTopicMeetingSurveyId();
                panelOut.println("        <hr>");
                panelOut.println("        <a href=\"" + backUrl + "\">Back to Assignment</a>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderError(HttpServletResponse response, String contextPath,
            String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Survey Results - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Survey Results</h2>");
                panelOut.println("        <p><strong>" + escapeHtml(message) + "</strong></p>");
                panelOut.println("        <a href=\"" + contextPath
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
