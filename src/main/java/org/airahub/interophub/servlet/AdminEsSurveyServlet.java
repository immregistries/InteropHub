package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.EsSurvey;
import org.airahub.interophub.model.EsSurvey.SurveyStatus;
import org.airahub.interophub.model.EsSurveyQuestion;
import org.airahub.interophub.model.EsSurveyQuestion.QuestionType;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsSurveyService;

public class AdminEsSurveyServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsSurveyService surveyService;

    public AdminEsSurveyServlet() {
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
        String action = trimToNull(request.getParameter("action"));
        Long surveyId = parseId(trimToNull(request.getParameter("surveyId")));

        if ("new".equals(action)) {
            renderCreateForm(response, contextPath, null);
            return;
        }

        if (surveyId != null) {
            EsSurvey survey = surveyService.getSurvey(surveyId).orElse(null);
            if (survey == null) {
                renderList(response, contextPath, "Survey not found.");
                return;
            }
            List<EsSurveyQuestion> questions = surveyService.listQuestions(surveyId);
            String savedMsg = request.getParameter("saved") != null ? "Survey saved." : null;
            renderDetail(response, contextPath, survey, questions, savedMsg, null);
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
            String name = trimToNull(request.getParameter("surveyName"));
            String description = trimToNull(request.getParameter("surveyDescription"));
            if (name == null) {
                renderCreateForm(response, contextPath, "Survey name is required.");
                return;
            }
            try {
                EsSurvey created = surveyService.createSurvey(name, description,
                        adminUser.get().getUserId());
                response.sendRedirect(contextPath + "/admin/es/surveys?surveyId="
                        + created.getEsSurveyId() + "&saved=1");
            } catch (Exception ex) {
                renderCreateForm(response, contextPath, "Error creating survey: " + ex.getMessage());
            }
            return;
        }

        Long surveyId = parseId(trimToNull(request.getParameter("surveyId")));
        if (surveyId == null) {
            renderList(response, contextPath, "Missing survey ID.");
            return;
        }

        if ("update".equals(action)) {
            String name = trimToNull(request.getParameter("surveyName"));
            String description = trimToNull(request.getParameter("surveyDescription"));
            if (name == null) {
                EsSurvey survey = surveyService.getSurvey(surveyId).orElse(null);
                List<EsSurveyQuestion> questions = survey != null ? surveyService.listQuestions(surveyId) : List.of();
                renderDetail(response, contextPath, survey, questions, null, "Survey name is required.");
                return;
            }
            try {
                surveyService.updateDraftSurvey(surveyId, name, description);
                response.sendRedirect(contextPath + "/admin/es/surveys?surveyId=" + surveyId + "&saved=1");
            } catch (Exception ex) {
                EsSurvey survey = surveyService.getSurvey(surveyId).orElse(null);
                List<EsSurveyQuestion> questions = survey != null ? surveyService.listQuestions(surveyId) : List.of();
                renderDetail(response, contextPath, survey, questions, null, ex.getMessage());
            }
            return;
        }

        if ("addQuestion".equals(action)) {
            String questionText = trimToNull(request.getParameter("questionText"));
            String questionTypeRaw = trimToNull(request.getParameter("questionType"));
            boolean required = "true".equals(trimToNull(request.getParameter("required")));
            if (questionText == null || questionTypeRaw == null) {
                EsSurvey survey = surveyService.getSurvey(surveyId).orElse(null);
                List<EsSurveyQuestion> questions = survey != null ? surveyService.listQuestions(surveyId) : List.of();
                renderDetail(response, contextPath, survey, questions, null,
                        "Question text and type are required.");
                return;
            }
            try {
                QuestionType qType = QuestionType.valueOf(questionTypeRaw.toUpperCase());
                surveyService.addQuestion(surveyId, questionText, qType, required);
                response.sendRedirect(contextPath + "/admin/es/surveys?surveyId=" + surveyId + "&saved=1");
            } catch (Exception ex) {
                EsSurvey survey = surveyService.getSurvey(surveyId).orElse(null);
                List<EsSurveyQuestion> questions = survey != null ? surveyService.listQuestions(surveyId) : List.of();
                renderDetail(response, contextPath, survey, questions, null, ex.getMessage());
            }
            return;
        }

        if ("updateQuestion".equals(action)) {
            Long questionId = parseId(trimToNull(request.getParameter("questionId")));
            String questionText = trimToNull(request.getParameter("questionText"));
            boolean required = "true".equals(trimToNull(request.getParameter("required")));
            if (questionId == null || questionText == null) {
                response.sendRedirect(contextPath + "/admin/es/surveys?surveyId=" + surveyId);
                return;
            }
            try {
                surveyService.updateQuestion(questionId, questionText, required);
                response.sendRedirect(contextPath + "/admin/es/surveys?surveyId=" + surveyId + "&saved=1");
            } catch (Exception ex) {
                EsSurvey survey = surveyService.getSurvey(surveyId).orElse(null);
                List<EsSurveyQuestion> questions = survey != null ? surveyService.listQuestions(surveyId) : List.of();
                renderDetail(response, contextPath, survey, questions, null, ex.getMessage());
            }
            return;
        }

        if ("reorderQuestions".equals(action)) {
            String orderRaw = trimToNull(request.getParameter("questionOrder"));
            if (orderRaw != null) {
                try {
                    List<Long> ids = Arrays.stream(orderRaw.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(Long::parseLong)
                            .toList();
                    surveyService.reorderQuestions(surveyId, ids);
                } catch (Exception ex) {
                    // ignore reorder errors silently
                }
            }
            response.sendRedirect(contextPath + "/admin/es/surveys?surveyId=" + surveyId + "&saved=1");
            return;
        }

        if ("markReady".equals(action)) {
            try {
                surveyService.markReady(surveyId, adminUser.get().getUserId());
                response.sendRedirect(contextPath + "/admin/es/surveys?surveyId=" + surveyId + "&saved=1");
            } catch (Exception ex) {
                EsSurvey survey = surveyService.getSurvey(surveyId).orElse(null);
                List<EsSurveyQuestion> questions = survey != null ? surveyService.listQuestions(surveyId) : List.of();
                renderDetail(response, contextPath, survey, questions, null, ex.getMessage());
            }
            return;
        }

        if ("close".equals(action)) {
            try {
                surveyService.closeSurvey(surveyId);
                response.sendRedirect(contextPath + "/admin/es/surveys?surveyId=" + surveyId + "&saved=1");
            } catch (Exception ex) {
                EsSurvey survey = surveyService.getSurvey(surveyId).orElse(null);
                List<EsSurveyQuestion> questions = survey != null ? surveyService.listQuestions(surveyId) : List.of();
                renderDetail(response, contextPath, survey, questions, null, ex.getMessage());
            }
            return;
        }

        renderList(response, contextPath, "Unknown action.");
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    private void renderList(HttpServletResponse response, String contextPath, String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        List<EsSurvey> surveys = surveyService.listSurveys();
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Surveys Admin - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>ES Surveys</h2>");
                if (message != null) {
                    panelOut.println("        <p><strong>" + escapeHtml(message) + "</strong></p>");
                }
                panelOut.println("        <p><a href=\"" + contextPath + "/admin/es/surveys?action=new\""
                        + " class=\"button\">+ New Survey</a></p>");
                panelOut.println("        <table>");
                panelOut.println("          <thead><tr>"
                        + "<th>Name</th><th>Key</th><th>Status</th><th>Actions</th>"
                        + "</tr></thead>");
                panelOut.println("          <tbody>");
                for (EsSurvey s : surveys) {
                    String detailUrl = contextPath + "/admin/es/surveys?surveyId=" + s.getEsSurveyId();
                    panelOut.println("            <tr>");
                    panelOut.println("              <td><a href=\"" + detailUrl + "\">"
                            + escapeHtml(s.getSurveyName()) + "</a></td>");
                    panelOut.println("              <td>" + escapeHtml(s.getSurveyKey()) + "</td>");
                    panelOut.println("              <td>" + escapeHtml(s.getStatus() != null
                            ? s.getStatus().name()
                            : "") + "</td>");
                    panelOut.println("              <td><a href=\"" + detailUrl + "\">Edit</a></td>");
                    panelOut.println("            </tr>");
                }
                if (surveys.isEmpty()) {
                    panelOut.println("            <tr><td colspan=\"4\">No surveys found.</td></tr>");
                }
                panelOut.println("          </tbody>");
                panelOut.println("        </table>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderCreateForm(HttpServletResponse response, String contextPath,
            String errorMessage) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "New Survey - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>New Survey</h2>");
                if (errorMessage != null) {
                    panelOut.println("        <p class=\"error\"><strong>"
                            + escapeHtml(errorMessage) + "</strong></p>");
                }
                panelOut.println("        <form method=\"post\" action=\""
                        + contextPath + "/admin/es/surveys\">");
                panelOut.println("          <input type=\"hidden\" name=\"action\" value=\"create\">");
                panelOut.println("          <label>Survey Name<br>"
                        + "<input type=\"text\" name=\"surveyName\" required size=\"60\"></label><br><br>");
                panelOut.println("          <label>Description<br>"
                        + "<textarea name=\"surveyDescription\" rows=\"3\" cols=\"60\"></textarea>"
                        + "</label><br><br>");
                panelOut.println("          <button type=\"submit\">Create Survey</button>");
                panelOut.println("          &nbsp; <a href=\"" + contextPath
                        + "/admin/es/surveys\">Cancel</a>");
                panelOut.println("        </form>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderDetail(HttpServletResponse response, String contextPath,
            EsSurvey survey, List<EsSurveyQuestion> questions, String successMessage,
            String errorMessage) throws IOException {
        if (survey == null) {
            renderList(response, contextPath, "Survey not found.");
            return;
        }
        response.setContentType("text/html;charset=UTF-8");
        boolean isDraft = survey.getStatus() == SurveyStatus.DRAFT;
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Survey - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>" + escapeHtml(survey.getSurveyName()) + "</h2>");
                panelOut.println("        <p>Status: <strong>" + escapeHtml(survey.getStatus().name())
                        + "</strong></p>");
                if (successMessage != null) {
                    panelOut.println("        <p><strong>" + escapeHtml(successMessage) + "</strong></p>");
                }
                if (errorMessage != null) {
                    panelOut.println("        <p class=\"error\"><strong>"
                            + escapeHtml(errorMessage) + "</strong></p>");
                }

                if (isDraft) {
                    panelOut.println("        <form method=\"post\" action=\""
                            + contextPath + "/admin/es/surveys\">");
                    panelOut.println("          <input type=\"hidden\" name=\"action\" value=\"update\">");
                    panelOut.println("          <input type=\"hidden\" name=\"surveyId\" value=\""
                            + survey.getEsSurveyId() + "\">");
                    panelOut.println("          <label>Survey Name<br>"
                            + "<input type=\"text\" name=\"surveyName\" value=\""
                            + escapeHtml(survey.getSurveyName()) + "\" required size=\"60\"></label><br><br>");
                    panelOut.println("          <label>Description<br>"
                            + "<textarea name=\"surveyDescription\" rows=\"3\" cols=\"60\">"
                            + escapeHtml(orEmpty(survey.getSurveyDescription()))
                            + "</textarea></label><br><br>");
                    panelOut.println("          <button type=\"submit\">Save</button>");
                    panelOut.println("        </form>");
                } else {
                    panelOut.println("        <p>Description: "
                            + escapeHtml(orEmpty(survey.getSurveyDescription())) + "</p>");
                }

                panelOut.println("        <hr>");
                panelOut.println("        <h3>Questions</h3>");

                if (questions.isEmpty()) {
                    panelOut.println("        <p>No questions added yet.</p>");
                } else {
                    panelOut.println("        <table>");
                    panelOut.println("          <thead><tr><th>#</th><th>Text</th><th>Type</th>"
                            + "<th>Required</th>" + (isDraft ? "<th>Edit</th>" : "") + "</tr></thead>");
                    panelOut.println("          <tbody>");
                    for (EsSurveyQuestion q : questions) {
                        panelOut.println("            <tr>");
                        panelOut.println("              <td>" + q.getDisplayOrder() + "</td>");
                        panelOut.println("              <td>" + escapeHtml(q.getQuestionText()) + "</td>");
                        panelOut.println("              <td>" + escapeHtml(q.getQuestionType().name()) + "</td>");
                        panelOut.println("              <td>" + (q.isRequired() ? "Yes" : "No") + "</td>");
                        if (isDraft) {
                            panelOut.println("              <td>");
                            panelOut.println("                <form method=\"post\" action=\""
                                    + contextPath + "/admin/es/surveys\" style=\"display:inline\">");
                            panelOut.println("                  <input type=\"hidden\" name=\"action\""
                                    + " value=\"updateQuestion\">");
                            panelOut.println("                  <input type=\"hidden\" name=\"surveyId\""
                                    + " value=\"" + survey.getEsSurveyId() + "\">");
                            panelOut.println("                  <input type=\"hidden\" name=\"questionId\""
                                    + " value=\"" + q.getEsSurveyQuestionId() + "\">");
                            panelOut.println("                  <input type=\"text\" name=\"questionText\""
                                    + " value=\"" + escapeHtml(q.getQuestionText()) + "\" size=\"50\">");
                            panelOut.println("                  <select name=\"required\">"
                                    + "<option value=\"true\"" + (q.isRequired() ? " selected" : "")
                                    + ">Required</option>"
                                    + "<option value=\"false\"" + (!q.isRequired() ? " selected" : "")
                                    + ">Optional</option></select>");
                            panelOut.println("                  <button type=\"submit\">Save</button>");
                            panelOut.println("                </form>");
                            panelOut.println("              </td>");
                        }
                        panelOut.println("            </tr>");
                    }
                    panelOut.println("          </tbody>");
                    panelOut.println("        </table>");

                    if (isDraft && questions.size() > 1) {
                        panelOut.println("        <form method=\"post\" action=\""
                                + contextPath + "/admin/es/surveys\">");
                        panelOut.println("          <input type=\"hidden\" name=\"action\""
                                + " value=\"reorderQuestions\">");
                        panelOut.println("          <input type=\"hidden\" name=\"surveyId\" value=\""
                                + survey.getEsSurveyId() + "\">");
                        panelOut.println("          <label>Question order (comma-sep IDs):<br>"
                                + "<input type=\"text\" name=\"questionOrder\" size=\"60\">"
                                + "</label>");
                        panelOut.println("          <button type=\"submit\">Reorder</button>");
                        panelOut.println("        </form>");
                    }
                }

                if (isDraft) {
                    panelOut.println("        <hr>");
                    panelOut.println("        <h3>Add Question</h3>");
                    panelOut.println("        <form method=\"post\" action=\""
                            + contextPath + "/admin/es/surveys\">");
                    panelOut.println("          <input type=\"hidden\" name=\"action\" value=\"addQuestion\">");
                    panelOut.println("          <input type=\"hidden\" name=\"surveyId\" value=\""
                            + survey.getEsSurveyId() + "\">");
                    panelOut.println("          <label>Question Text<br>"
                            + "<input type=\"text\" name=\"questionText\" required size=\"80\"></label><br>");
                    panelOut.println("          <label>Type: <select name=\"questionType\">"
                            + "<option value=\"LIKERT_1_5\">Likert 1-5</option>"
                            + "<option value=\"TEXT\">Text</option>"
                            + "</select></label> &nbsp;");
                    panelOut.println("          <label>Required: <select name=\"required\">"
                            + "<option value=\"true\">Yes</option>"
                            + "<option value=\"false\">No</option>"
                            + "</select></label><br><br>");
                    panelOut.println("          <button type=\"submit\">Add Question</button>");
                    panelOut.println("        </form>");
                }

                panelOut.println("        <hr>");
                if (isDraft) {
                    panelOut.println("        <form method=\"post\" action=\""
                            + contextPath + "/admin/es/surveys\">");
                    panelOut.println("          <input type=\"hidden\" name=\"action\" value=\"markReady\">");
                    panelOut.println("          <input type=\"hidden\" name=\"surveyId\" value=\""
                            + survey.getEsSurveyId() + "\">");
                    panelOut.println("          <button type=\"submit\">Mark Ready (publish)</button>");
                    panelOut.println("        </form>");
                } else if (survey.getStatus() == SurveyStatus.READY) {
                    panelOut.println("        <form method=\"post\" action=\""
                            + contextPath + "/admin/es/surveys\">");
                    panelOut.println("          <input type=\"hidden\" name=\"action\" value=\"close\">");
                    panelOut.println("          <input type=\"hidden\" name=\"surveyId\" value=\""
                            + survey.getEsSurveyId() + "\">");
                    panelOut.println("          <button type=\"submit\">Close Survey</button>");
                    panelOut.println("        </form>");
                }

                panelOut.println("        <br><a href=\"" + contextPath
                        + "/admin/es/surveys\">Back to Surveys</a>");
                panelOut.println("      </section>");
            });
        }
    }

    // -------------------------------------------------------------------------
    // Admin auth (copied verbatim from AdminEsMeetingServlet pattern)
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
                panelOut.println("        <p>You must be an InteropHub admin to access survey management.</p>");
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
