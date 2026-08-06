package org.airahub.interophub.servlet;

import java.io.IOException;
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
import org.airahub.interophub.service.EsSurveyService;

public class AdminEsSurveyServlet extends HttpServlet {

    private final EsSurveyService surveyService;

    public AdminEsSurveyServlet() {
        this.surveyService = new EsSurveyService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String action = trimToNull(request.getParameter("action"));
        Long surveyId = parseId(trimToNull(request.getParameter("surveyId")));

        if ("new".equals(action)) {
            renderCreateForm(request, response, null);
            return;
        }

        if (surveyId != null) {
            EsSurvey survey = surveyService.getSurvey(surveyId).orElse(null);
            if (survey == null) {
                renderList(request, response, "Survey not found.");
                return;
            }
            List<EsSurveyQuestion> questions = surveyService.listQuestions(surveyId);
            String savedMsg = request.getParameter("saved") != null ? "Survey saved." : null;
            renderDetail(request, response, survey, questions, savedMsg, null);
            return;
        }

        renderList(request, response, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String action = trimToNull(request.getParameter("action"));

        if ("create".equals(action)) {
            String name = trimToNull(request.getParameter("surveyName"));
            String description = trimToNull(request.getParameter("surveyDescription"));
            if (name == null) {
                renderCreateForm(request, response, "Survey name is required.");
                return;
            }
            try {
                EsSurvey created = surveyService.createSurvey(name, description,
                        adminUser.get().getUserId());
                response.sendRedirect(contextPath + "/admin/es/surveys?surveyId="
                        + created.getEsSurveyId() + "&saved=1");
            } catch (Exception ex) {
                renderCreateForm(request, response, "Error creating survey: " + ex.getMessage());
            }
            return;
        }

        Long surveyId = parseId(trimToNull(request.getParameter("surveyId")));
        if (surveyId == null) {
            renderList(request, response, "Missing survey ID.");
            return;
        }

        if ("update".equals(action)) {
            String name = trimToNull(request.getParameter("surveyName"));
            String description = trimToNull(request.getParameter("surveyDescription"));
            if (name == null) {
                EsSurvey survey = surveyService.getSurvey(surveyId).orElse(null);
                List<EsSurveyQuestion> questions = survey != null ? surveyService.listQuestions(surveyId) : List.of();
                renderDetail(request, response, survey, questions, null, "Survey name is required.");
                return;
            }
            try {
                surveyService.updateDraftSurvey(surveyId, name, description);
                response.sendRedirect(contextPath + "/admin/es/surveys?surveyId=" + surveyId + "&saved=1");
            } catch (Exception ex) {
                EsSurvey survey = surveyService.getSurvey(surveyId).orElse(null);
                List<EsSurveyQuestion> questions = survey != null ? surveyService.listQuestions(surveyId) : List.of();
                renderDetail(request, response, survey, questions, null, ex.getMessage());
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
                renderDetail(request, response, survey, questions, null,
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
                renderDetail(request, response, survey, questions, null, ex.getMessage());
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
                renderDetail(request, response, survey, questions, null, ex.getMessage());
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
                renderDetail(request, response, survey, questions, null, ex.getMessage());
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
                renderDetail(request, response, survey, questions, null, ex.getMessage());
            }
            return;
        }

        renderList(request, response, "Unknown action.");
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    private void renderList(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        String contextPath = request.getContextPath();
        List<EsSurvey> surveys = surveyService.listSurveys();
        AdminShellRenderer.render(request, response, "Surveys Admin - InteropHub", AdminSection.TOPIC_SPACES,
                "/admin/es/surveys", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">ES Surveys</h2>");
                    if (message != null) {
                        out.println("            <div class=\"aira-alert aira-alert--info\"><p>"
                                + escapeHtml(message) + "</p></div>");
                    }
                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--primary\" href=\"" + contextPath
                            + "/admin/es/surveys?action=new\">+ New Survey</a>");
                    out.println("            </div>");
                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println("              <thead>");
                    out.println("                <tr>");
                    out.println("                  <th>Name</th>");
                    out.println("                  <th>Key</th>");
                    out.println("                  <th>Status</th>");
                    out.println("                  <th>Actions</th>");
                    out.println("                </tr>");
                    out.println("              </thead>");
                    out.println("              <tbody>");
                    for (EsSurvey s : surveys) {
                        String detailUrl = contextPath + "/admin/es/surveys?surveyId=" + s.getEsSurveyId();
                        out.println("                <tr>");
                        out.println("                  <td><a class=\"aira-inline-link\" href=\"" + detailUrl + "\">"
                                + escapeHtml(s.getSurveyName()) + "</a></td>");
                        out.println("                  <td>" + escapeHtml(s.getSurveyKey()) + "</td>");
                        out.println("                  <td>" + escapeHtml(s.getStatus() != null
                                ? s.getStatus().name()
                                : "") + "</td>");
                        out.println("                  <td><a class=\"aira-inline-link\" href=\"" + detailUrl
                                + "\">Edit</a></td>");
                        out.println("                </tr>");
                    }
                    if (surveys.isEmpty()) {
                        out.println("                <tr><td colspan=\"4\">No surveys found.</td></tr>");
                    }
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");
                    out.println("          </section>");
                });
    }

    private void renderCreateForm(HttpServletRequest request, HttpServletResponse response,
            String errorMessage) throws IOException {
        String contextPath = request.getContextPath();
        AdminShellRenderer.render(request, response, "New Survey - InteropHub", AdminSection.TOPIC_SPACES,
                "/admin/es/surveys", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">New Survey</h2>");
                    if (errorMessage != null) {
                        out.println("            <div class=\"aira-alert aira-alert--danger\"><p>"
                                + escapeHtml(errorMessage) + "</p></div>");
                    }
                    out.println("            <form class=\"aira-form\" method=\"post\" action=\""
                            + contextPath + "/admin/es/surveys\">");
                    out.println("              <input type=\"hidden\" name=\"action\" value=\"create\" />");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"surveyName\">Survey Name</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"surveyName\" name=\"surveyName\" type=\"text\" required />");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"surveyDescription\">Description</label>");
                    out.println(
                            "                <textarea class=\"aira-textarea\" id=\"surveyDescription\" name=\"surveyDescription\" rows=\"3\"></textarea>");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-action-group\">");
                    out.println(
                            "                <button class=\"aira-button aira-button--primary\" type=\"submit\">Create Survey</button>");
                    out.println("                <a class=\"aira-button aira-button--secondary\" href=\""
                            + contextPath + "/admin/es/surveys\">Cancel</a>");
                    out.println("              </div>");
                    out.println("            </form>");
                    out.println("          </section>");
                });
    }

    private void renderDetail(HttpServletRequest request, HttpServletResponse response,
            EsSurvey survey, List<EsSurveyQuestion> questions, String successMessage,
            String errorMessage) throws IOException {
        if (survey == null) {
            renderList(request, response, "Survey not found.");
            return;
        }
        String contextPath = request.getContextPath();
        boolean isDraft = survey.getStatus() == SurveyStatus.DRAFT;
        AdminShellRenderer.render(request, response, "Survey - InteropHub", AdminSection.TOPIC_SPACES,
                "/admin/es/surveys", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">" + escapeHtml(survey.getSurveyName())
                            + "</h2>");
                    out.println("            <p class=\"aira-meta\">Status: <strong>"
                            + escapeHtml(survey.getStatus().name()) + "</strong></p>");
                    if (successMessage != null) {
                        out.println("            <div class=\"aira-alert aira-alert--success\"><p>"
                                + escapeHtml(successMessage) + "</p></div>");
                    }
                    if (errorMessage != null) {
                        out.println("            <div class=\"aira-alert aira-alert--danger\"><p>"
                                + escapeHtml(errorMessage) + "</p></div>");
                    }

                    if (isDraft) {
                        out.println("            <form class=\"aira-form\" method=\"post\" action=\""
                                + contextPath + "/admin/es/surveys\">");
                        out.println("              <input type=\"hidden\" name=\"action\" value=\"update\" />");
                        out.println("              <input type=\"hidden\" name=\"surveyId\" value=\""
                                + survey.getEsSurveyId() + "\" />");
                        out.println("              <div class=\"aira-field\">");
                        out.println("                <label for=\"surveyName\">Survey Name</label>");
                        out.println(
                                "                <input class=\"aira-input\" id=\"surveyName\" name=\"surveyName\" type=\"text\" required value=\""
                                        + escapeHtml(survey.getSurveyName()) + "\" />");
                        out.println("              </div>");
                        out.println("              <div class=\"aira-field\">");
                        out.println("                <label for=\"surveyDescription\">Description</label>");
                        out.println(
                                "                <textarea class=\"aira-textarea\" id=\"surveyDescription\" name=\"surveyDescription\" rows=\"3\">"
                                        + escapeHtml(orEmpty(survey.getSurveyDescription())) + "</textarea>");
                        out.println("              </div>");
                        out.println("              <div class=\"aira-action-group\">");
                        out.println(
                                "                <button class=\"aira-button aira-button--primary\" type=\"submit\">Save</button>");
                        out.println("              </div>");
                        out.println("            </form>");
                    } else {
                        out.println("            <p class=\"aira-meta\">Description: "
                                + escapeHtml(orEmpty(survey.getSurveyDescription())) + "</p>");
                    }

                    out.println("            <h3 class=\"aira-subsection-title\">Questions</h3>");

                    if (questions.isEmpty()) {
                        out.println("            <p class=\"aira-meta\">No questions added yet.</p>");
                    } else {
                        out.println("            <div class=\"aira-table-wrap\">");
                        out.println("            <table class=\"aira-table\">");
                        out.println("              <thead>");
                        out.println("                <tr>");
                        out.println("                  <th>#</th>");
                        out.println("                  <th>Text</th>");
                        out.println("                  <th>Type</th>");
                        out.println("                  <th>Required</th>");
                        if (isDraft) {
                            out.println("                  <th>Edit</th>");
                        }
                        out.println("                </tr>");
                        out.println("              </thead>");
                        out.println("              <tbody>");
                        for (EsSurveyQuestion q : questions) {
                            out.println("                <tr>");
                            out.println("                  <td>" + q.getDisplayOrder() + "</td>");
                            out.println("                  <td>" + escapeHtml(q.getQuestionText()) + "</td>");
                            out.println("                  <td>" + escapeHtml(q.getQuestionType().name()) + "</td>");
                            out.println("                  <td>" + (q.isRequired() ? "Yes" : "No") + "</td>");
                            if (isDraft) {
                                out.println("                  <td>");
                                out.println(
                                        "                    <form class=\"aira-form\" method=\"post\" action=\""
                                                + contextPath + "/admin/es/surveys\">");
                                out.println(
                                        "                      <input type=\"hidden\" name=\"action\" value=\"updateQuestion\" />");
                                out.println("                      <input type=\"hidden\" name=\"surveyId\" value=\""
                                        + survey.getEsSurveyId() + "\" />");
                                out.println(
                                        "                      <input type=\"hidden\" name=\"questionId\" value=\""
                                                + q.getEsSurveyQuestionId() + "\" />");
                                out.println(
                                        "                      <input class=\"aira-input\" type=\"text\" name=\"questionText\" value=\""
                                                + escapeHtml(q.getQuestionText()) + "\" />");
                                out.println("                      <select class=\"aira-select\" name=\"required\">"
                                        + "<option value=\"true\"" + (q.isRequired() ? " selected" : "")
                                        + ">Required</option>"
                                        + "<option value=\"false\"" + (!q.isRequired() ? " selected" : "")
                                        + ">Optional</option></select>");
                                out.println(
                                        "                      <button class=\"aira-button aira-button--small\" type=\"submit\">Save</button>");
                                out.println("                    </form>");
                                out.println("                  </td>");
                            }
                            out.println("                </tr>");
                        }
                        out.println("              </tbody>");
                        out.println("            </table>");
                        out.println("            </div>");

                        if (isDraft && questions.size() > 1) {
                            out.println("            <form class=\"aira-form\" method=\"post\" action=\""
                                    + contextPath + "/admin/es/surveys\">");
                            out.println(
                                    "              <input type=\"hidden\" name=\"action\" value=\"reorderQuestions\" />");
                            out.println("              <input type=\"hidden\" name=\"surveyId\" value=\""
                                    + survey.getEsSurveyId() + "\" />");
                            out.println("              <div class=\"aira-field\">");
                            out.println(
                                    "                <label for=\"questionOrder\">Question order (comma-separated IDs)</label>");
                            out.println(
                                    "                <input class=\"aira-input\" id=\"questionOrder\" type=\"text\" name=\"questionOrder\" />");
                            out.println("              </div>");
                            out.println("              <div class=\"aira-action-group\">");
                            out.println(
                                    "                <button class=\"aira-button aira-button--secondary\" type=\"submit\">Reorder</button>");
                            out.println("              </div>");
                            out.println("            </form>");
                        }
                    }

                    if (isDraft) {
                        out.println("            <h3 class=\"aira-subsection-title\">Add Question</h3>");
                        out.println("            <form class=\"aira-form\" method=\"post\" action=\""
                                + contextPath + "/admin/es/surveys\">");
                        out.println("              <input type=\"hidden\" name=\"action\" value=\"addQuestion\" />");
                        out.println("              <input type=\"hidden\" name=\"surveyId\" value=\""
                                + survey.getEsSurveyId() + "\" />");
                        out.println("              <div class=\"aira-field\">");
                        out.println("                <label for=\"questionText\">Question Text</label>");
                        out.println(
                                "                <input class=\"aira-input\" id=\"questionText\" name=\"questionText\" type=\"text\" required />");
                        out.println("              </div>");
                        out.println("              <div class=\"aira-field\">");
                        out.println("                <label for=\"questionType\">Type</label>");
                        out.println(
                                "                <select class=\"aira-select\" id=\"questionType\" name=\"questionType\">"
                                        + "<option value=\"LIKERT_1_5\">Likert 1-5</option>"
                                        + "<option value=\"TEXT\">Text</option></select>");
                        out.println("              </div>");
                        out.println("              <div class=\"aira-field\">");
                        out.println("                <label for=\"required\">Required</label>");
                        out.println("                <select class=\"aira-select\" id=\"required\" name=\"required\">"
                                + "<option value=\"true\">Yes</option>"
                                + "<option value=\"false\">No</option></select>");
                        out.println("              </div>");
                        out.println("              <div class=\"aira-action-group\">");
                        out.println(
                                "                <button class=\"aira-button aira-button--primary\" type=\"submit\">Add Question</button>");
                        out.println("              </div>");
                        out.println("            </form>");
                    }

                    if (isDraft) {
                        out.println("            <form class=\"aira-form\" method=\"post\" action=\""
                                + contextPath + "/admin/es/surveys\">");
                        out.println("              <input type=\"hidden\" name=\"action\" value=\"markReady\" />");
                        out.println("              <input type=\"hidden\" name=\"surveyId\" value=\""
                                + survey.getEsSurveyId() + "\" />");
                        out.println("              <div class=\"aira-action-group\">");
                        out.println(
                                "                <button class=\"aira-button aira-button--primary\" type=\"submit\">Mark Ready (publish)</button>");
                        out.println("              </div>");
                        out.println("            </form>");
                    } else if (survey.getStatus() == SurveyStatus.READY) {
                        out.println("            <form class=\"aira-form\" method=\"post\" action=\""
                                + contextPath + "/admin/es/surveys\">");
                        out.println("              <input type=\"hidden\" name=\"action\" value=\"close\" />");
                        out.println("              <input type=\"hidden\" name=\"surveyId\" value=\""
                                + survey.getEsSurveyId() + "\" />");
                        out.println("              <div class=\"aira-action-group\">");
                        out.println(
                                "                <button class=\"aira-button aira-button--danger\" type=\"submit\">Close Survey</button>");
                        out.println("              </div>");
                        out.println("            </form>");
                    }

                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/surveys\">Back to Surveys</a></p>");
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
