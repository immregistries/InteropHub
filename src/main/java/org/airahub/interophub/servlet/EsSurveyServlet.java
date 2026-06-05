package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.EsMeetingAttendance;
import org.airahub.interophub.model.EsSurvey;
import org.airahub.interophub.model.EsSurveyQuestion;
import org.airahub.interophub.model.EsSurveyQuestion.QuestionType;
import org.airahub.interophub.model.EsTopicMeetingSurvey;
import org.airahub.interophub.model.EsTopicMeetingSurvey.AssignmentStatus;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsNormalizer;
import org.airahub.interophub.service.EsSurveyService;

public class EsSurveyServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsSurveyService surveyService;

    public EsSurveyServlet() {
        this.authFlowService = new AuthFlowService();
        this.surveyService = new EsSurveyService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String contextPath = request.getContextPath();

        Long assignmentId = parseId(trimToNull(request.getParameter("assignmentId")));
        if (assignmentId == null) {
            renderError(response, contextPath, "No survey specified.");
            return;
        }

        if ("1".equals(request.getParameter("submitted"))) {
            renderThankYou(response, contextPath, assignmentId);
            return;
        }

        EsTopicMeetingSurvey assignment = surveyService.getTopicMeetingSurvey(assignmentId).orElse(null);
        if (assignment == null || assignment.getStatus() != AssignmentStatus.ACTIVE) {
            renderError(response, contextPath, "This survey is not available.");
            return;
        }

        java.time.LocalDate today = java.time.LocalDate.now();
        if (today.isBefore(assignment.getStartDate()) || today.isAfter(assignment.getEndDate())) {
            renderError(response, contextPath, "This survey is not currently accepting responses.");
            return;
        }

        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        Long userId = authenticatedUser.map(User::getUserId).orElse(null);

        String emailNormalized = resolveEmailNormalized(request, authenticatedUser.orElse(null));
        if (surveyService.hasResponded(assignment, userId, emailNormalized)) {
            renderAlreadySubmitted(response, contextPath);
            return;
        }

        EsSurvey survey = surveyService.getSurvey(assignment.getEsSurveyId()).orElse(null);
        List<EsSurveyQuestion> questions = surveyService.listQuestions(assignment.getEsSurveyId());
        renderSurveyForm(response, contextPath, assignment, survey, questions, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String contextPath = request.getContextPath();

        Long assignmentId = parseId(trimToNull(request.getParameter("assignmentId")));
        if (assignmentId == null) {
            renderError(response, contextPath, "No survey specified.");
            return;
        }

        EsTopicMeetingSurvey assignment = surveyService.getTopicMeetingSurvey(assignmentId).orElse(null);
        if (assignment == null || assignment.getStatus() != AssignmentStatus.ACTIVE) {
            renderError(response, contextPath, "This survey is not available.");
            return;
        }

        java.time.LocalDate today = java.time.LocalDate.now();
        if (today.isBefore(assignment.getStartDate()) || today.isAfter(assignment.getEndDate())) {
            renderError(response, contextPath, "This survey is not currently accepting responses.");
            return;
        }

        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        User loggedInUser = authenticatedUser.orElse(null);

        // Build a minimal attendance-like object from session/form data
        String emailNormalized = resolveEmailNormalized(request, loggedInUser);
        if (emailNormalized == null) {
            EsSurvey survey = surveyService.getSurvey(assignment.getEsSurveyId()).orElse(null);
            List<EsSurveyQuestion> questions = surveyService.listQuestions(assignment.getEsSurveyId());
            renderSurveyForm(response, contextPath, assignment, survey, questions,
                    "Could not identify your email address. Please return to the attendance page.");
            return;
        }

        // Collect answers
        List<EsSurveyQuestion> questions = surveyService.listQuestions(assignment.getEsSurveyId());
        Map<Long, String> answers = new HashMap<>();
        for (EsSurveyQuestion q : questions) {
            String val = trimToNull(request.getParameter("answer_" + q.getEsSurveyQuestionId()));
            if (val != null) {
                answers.put(q.getEsSurveyQuestionId(), val);
            }
        }

        // Build synthetic attendance record for submission
        EsMeetingAttendance syntheticAttendance = buildSyntheticAttendance(
                request, loggedInUser, emailNormalized, assignment);

        try {
            surveyService.submitSurveyResponse(assignment, syntheticAttendance, loggedInUser, answers);
            response.sendRedirect(contextPath + "/es/survey?assignmentId=" + assignmentId + "&submitted=1");
        } catch (IllegalStateException ex) {
            // Already responded
            renderAlreadySubmitted(response, contextPath);
        } catch (IllegalArgumentException ex) {
            // Validation error
            EsSurvey survey = surveyService.getSurvey(assignment.getEsSurveyId()).orElse(null);
            renderSurveyForm(response, contextPath, assignment, survey, questions, ex.getMessage());
        } catch (Exception ex) {
            EsSurvey survey = surveyService.getSurvey(assignment.getEsSurveyId()).orElse(null);
            renderSurveyForm(response, contextPath, assignment, survey, questions,
                    "An error occurred while saving your response. Please try again.");
        }
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    private void renderSurveyForm(HttpServletResponse response, String contextPath,
            EsTopicMeetingSurvey assignment, EsSurvey survey, List<EsSurveyQuestion> questions,
            String errorMessage) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        String title = survey != null ? escapeHtml(survey.getSurveyName()) : "Survey";
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head><meta charset=\"UTF-8\">");
            out.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
            out.println("<title>" + title + " - InteropHub</title>");
            out.println("<link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\">");
            out.println("</head><body>");
            out.println("<main class=\"container\">");
            out.println("  <h1>" + title + "</h1>");
            if (survey != null && survey.getSurveyDescription() != null) {
                out.println("  <p>" + escapeHtml(survey.getSurveyDescription()) + "</p>");
            }
            if (errorMessage != null) {
                out.println("  <p class=\"error\"><strong>" + escapeHtml(errorMessage) + "</strong></p>");
            }
            out.println("  <form method=\"post\" action=\"" + contextPath + "/es/survey\">");
            out.println("    <input type=\"hidden\" name=\"assignmentId\" value=\""
                    + assignment.getEsTopicMeetingSurveyId() + "\">");

            for (EsSurveyQuestion q : questions) {
                out.println("  <div class=\"survey-question\">");
                out.println("    <p><strong>" + q.getDisplayOrder() + ". "
                        + escapeHtml(q.getQuestionText())
                        + (q.isRequired() ? " <span class=\"required\">*</span>" : "") + "</strong></p>");
                if (q.getQuestionType() == QuestionType.LIKERT_1_5) {
                    out.println("    <div class=\"likert-row\">");
                    out.println("      <span>Strongly Disagree</span>");
                    for (int i = 1; i <= 5; i++) {
                        out.println("      <label><input type=\"radio\" name=\"answer_"
                                + q.getEsSurveyQuestionId() + "\" value=\"" + i + "\""
                                + (q.isRequired() ? " required" : "") + "> " + i + "</label>");
                    }
                    out.println("      <span>Strongly Agree</span>");
                    out.println("    </div>");
                } else {
                    out.println("    <textarea name=\"answer_" + q.getEsSurveyQuestionId()
                            + "\" rows=\"3\" cols=\"60\"></textarea>");
                }
                out.println("  </div>");
            }

            out.println("    <br>");
            out.println("    <button type=\"submit\">Submit Survey</button>");
            out.println("  </form>");
            out.println("</main>");
            PageFooterRenderer.render(out);
            out.println("</body></html>");
        }
    }

    private void renderThankYou(HttpServletResponse response, String contextPath,
            Long assignmentId) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\"><head><meta charset=\"UTF-8\">");
            out.println("<title>Thank You - InteropHub</title>");
            out.println("<link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\">");
            out.println("</head><body><main class=\"container\">");
            out.println("  <h1>Thank You!</h1>");
            out.println("  <p>Your survey response has been submitted successfully.</p>");
            out.println("  <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
            out.println("</main>");
            PageFooterRenderer.render(out);
            out.println("</body></html>");
        }
    }

    private void renderAlreadySubmitted(HttpServletResponse response, String contextPath) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\"><head><meta charset=\"UTF-8\">");
            out.println("<title>Already Submitted - InteropHub</title>");
            out.println("<link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\">");
            out.println("</head><body><main class=\"container\">");
            out.println("  <h1>Already Submitted</h1>");
            out.println("  <p>You have already submitted a response for this survey. Thank you!</p>");
            out.println("  <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
            out.println("</main>");
            PageFooterRenderer.render(out);
            out.println("</body></html>");
        }
    }

    private void renderError(HttpServletResponse response, String contextPath,
            String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\"><head><meta charset=\"UTF-8\">");
            out.println("<title>Survey - InteropHub</title>");
            out.println("<link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\">");
            out.println("</head><body><main class=\"container\">");
            out.println("  <h1>Survey</h1>");
            out.println("  <p>" + escapeHtml(message) + "</p>");
            out.println("  <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
            out.println("</main>");
            PageFooterRenderer.render(out);
            out.println("</body></html>");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String resolveEmailNormalized(HttpServletRequest request, User loggedInUser) {
        if (loggedInUser != null && loggedInUser.getEmail() != null) {
            return EsNormalizer.normalizeEmail(loggedInUser.getEmail());
        }
        Object sessionEmail = request.getSession(false) != null
                ? request.getSession(false).getAttribute("interophub.lastAttendedEmail")
                : null;
        if (sessionEmail instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    private EsMeetingAttendance buildSyntheticAttendance(HttpServletRequest request,
            User loggedInUser, String emailNormalized, EsTopicMeetingSurvey assignment) {
        EsMeetingAttendance a = new EsMeetingAttendance();
        a.setEsTopicMeetingId(assignment.getEsTopicMeetingId());
        a.setEmailNormalized(emailNormalized);
        if (loggedInUser != null) {
            a.setUserId(loggedInUser.getUserId());
            a.setEmail(loggedInUser.getEmail());
            a.setFirstName(loggedInUser.getFirstName());
            a.setLastName(loggedInUser.getLastName());
        } else {
            // For anonymous users the email is stored normalized in the session
            a.setEmail(emailNormalized);
        }
        return a;
    }

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
