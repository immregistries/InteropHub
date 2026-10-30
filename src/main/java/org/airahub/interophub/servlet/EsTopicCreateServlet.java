package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsNeighborhoodDao;
import org.airahub.interophub.dao.EsTopicPathDefinitionDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.EsTopicStageDefinitionDao;
import org.airahub.interophub.model.EsNeighborhood;
import org.airahub.interophub.model.EsTopicPathDefinition;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.EsTopicStageDefinition;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.TopicEditService;
import org.airahub.interophub.service.TopicSpaceAccessService;
import org.immregistries.aira.web.AiraPage;

public class EsTopicCreateServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(EsTopicCreateServlet.class.getName());
    private final AuthFlowService authFlowService = new AuthFlowService();
    private final TopicSpaceAccessService accessService = new TopicSpaceAccessService();
    private final TopicEditService topicEditService = new TopicEditService();
    private final EsTopicSpaceDao topicSpaceDao = new EsTopicSpaceDao();
    private final EsNeighborhoodDao neighborhoodDao = new EsNeighborhoodDao();
    private final EsTopicStageDefinitionDao stageDao = new EsTopicStageDefinitionDao();
    private final EsTopicPathDefinitionDao pathDao = new EsTopicPathDefinitionDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> user = authFlowService.findAuthenticatedUser(request);
        EsTopicSpace space = selectedSpace(request);
        if (user.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }
        if (space == null || !accessService.canCreateTopicInSpace(user.get(), space.getEsTopicSpaceId())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        renderForm(request, response, space, Map.of(), List.of(), null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> user = authFlowService.findAuthenticatedUser(request);
        EsTopicSpace space = selectedSpace(request);
        if (user.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }
        if (space == null || !accessService.canCreateTopicInSpace(user.get(), space.getEsTopicSpaceId())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        Map<String, String> values = formValues(request);
        String[] selectedNeighborhoods = request.getParameterValues("neighborhoods");
        List<String> neighborhoods = selectedNeighborhoods == null ? List.of() : List.of(selectedNeighborhoods);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("topicCode", values.get("topicCode"));
        fields.put("topicName", values.get("topicName"));
        fields.put("topicSummary", values.get("topicSummary"));
        fields.put("description", values.get("description"));
        fields.put("searchKeywords", values.get("searchKeywords"));
        fields.put("topicEmoji", values.get("topicEmoji"));
        fields.put("neighborhoods", neighborhoods);
        fields.put("stage", values.get("stage"));
        fields.put("path", values.get("path"));
        fields.put("status", values.get("status"));
        fields.put("policyStatus", values.get("policyStatus"));
        fields.put("topicType", values.get("topicType"));
        fields.put("confluenceUrl", values.get("confluenceUrl"));
        try {
            Long topicId = topicEditService.create(user.get(), space, fields, "WEB").topic().getEsTopicId();
            response.sendRedirect(request.getContextPath() + "/es/topic/" + topicId);
        } catch (TopicEditService.ValidationException ex) {
            renderForm(request, response, space, values, neighborhoods, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            renderForm(request, response, space, values, neighborhoods, ex.getMessage());
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Unable to create topic in Topic Space " + space.getSpaceCode(), ex);
            renderForm(request, response, space, values, neighborhoods, "Unable to save the topic. Please try again.");
        }
    }

    private EsTopicSpace selectedSpace(HttpServletRequest request) {
        String code = trimToNull(request.getParameter("space"));
        return code == null ? null : topicSpaceDao.findBySpaceCode(code).orElse(null);
    }

    private Map<String, String> formValues(HttpServletRequest request) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String field : new String[] {
                "topicCode", "topicName", "topicSummary", "description", "searchKeywords", "topicEmoji",
                "stage", "path", "status", "policyStatus", "topicType", "confluenceUrl"
        }) {
            values.put(field, request.getParameter(field));
        }
        return values;
    }

    private void renderForm(HttpServletRequest request, HttpServletResponse response, EsTopicSpace space,
            Map<String, String> values, List<String> selectedNeighborhoods, String error) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        String contextPath = request.getContextPath();
        String action = contextPath + "/es/topics/new?space="
                + URLEncoder.encode(space.getSpaceCode(), StandardCharsets.UTF_8);
        AiraPage page = InteropAiraPageFactory.base(request, "Add Topic - InteropHub")
                .applicationSubtitle("Topics")
                .mainClass("aira-main")
                .context(InteropAiraPageFactory.topicsMeetingsContext(space.getSpaceName(), space.getSpaceCode(),
                        true, false))
                .build();
        List<EsNeighborhood> neighborhoods = neighborhoodDao.findAllActiveBySpaceId(space.getEsTopicSpaceId());
        List<EsTopicStageDefinition> stages = stageDao.findAllOrderedBySpaceId(space.getEsTopicSpaceId()).stream()
                .filter(stage -> Boolean.TRUE.equals(stage.getIsActive()))
                .toList();
        List<EsTopicPathDefinition> paths = pathDao.findAllOrderedBySpaceId(space.getEsTopicSpaceId()).stream()
                .filter(path -> Boolean.TRUE.equals(path.getIsActive()))
                .toList();

        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container aira-stack\">");
            out.println("      <div class=\"aira-page-header\"><div><h1 class=\"aira-page-title\">Add Topic</h1>");
            out.println("        <p class=\"aira-page-intro\">Create a topic in "
                    + escapeHtml(space.getSpaceName()) + ".</p></div></div>");
            out.println("      <section class=\"aira-panel\">");
            if (error != null) {
                out.println("        <div class=\"aira-alert aira-alert--danger\"><p><strong>Could not save:</strong> "
                        + escapeHtml(error) + "</p></div>");
            }
            out.println("        <form class=\"aira-form\" action=\"" + escapeHtml(action) + "\" method=\"post\">");
            out.println("          <p><strong>Topic Space:</strong> " + escapeHtml(space.getSpaceName()) + "</p>");
            textField(out, "topicCode", "Topic Code (required)", values, true, 80);
            textField(out, "topicName", "Topic Name (required)", values, true, 140);
            textField(out, "topicSummary", "Topic Summary", values, false, 300);
            textarea(out, "description", "Description", values.get("description"));
            textField(out, "searchKeywords", "Search Keywords", values, false, 0);
            textField(out, "topicEmoji", "Topic Emoji", values, false, 64);

            out.println("          <div class=\"aira-field\"><label>Neighborhoods</label>");
            if (neighborhoods.isEmpty()) {
                out.println("            <p class=\"aira-meta\"><em>No active neighborhoods defined.</em></p>");
            }
            for (EsNeighborhood neighborhood : neighborhoods) {
                String name = neighborhood.getNeighborhoodName();
                boolean checked = selectedNeighborhoods.contains(name);
                out.println("            <label><input type=\"checkbox\" name=\"neighborhoods\" value=\""
                        + escapeHtml(name) + "\"" + (checked ? " checked" : "") + " /> "
                        + escapeHtml(name) + "</label>");
            }
            out.println("          </div>");

            selectField(out, "stage", "Stage", stages.stream()
                    .map(stage -> new Choice(stage.getStageName(), stage.getStageName())).toList(), values.get("stage"));
            selectField(out, "path", "Advancement Path", paths.stream()
                    .map(path -> new Choice(path.getPathName(), path.getPathName())).toList(), values.get("path"));
            selectField(out, "status", "Status",
                    List.of(new Choice("ACTIVE", "Active"), new Choice("ARCHIVED", "Archived"),
                            new Choice("RETIRED", "Retired")),
                    values.getOrDefault("status", "ACTIVE"));
            textField(out, "policyStatus", "Policy Status", values, false, 120);
            textField(out, "topicType", "Topic Type", values, false, 120);
            textField(out, "confluenceUrl", "Confluence URL", values, false, 500);
            out.println("          <div class=\"aira-form-actions\">");
            out.println("            <button class=\"aira-button aira-button--primary\" type=\"submit\">Create topic</button>");
            out.println("            <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                    + "/es/topics?space=" + URLEncoder.encode(space.getSpaceCode(), StandardCharsets.UTF_8)
                    + "\">Cancel</a>");
            out.println("          </div>");
            out.println("        </form></section></div>");
            page.writeEnd(out);
        }
    }

    private void textField(PrintWriter out, String name, String label, Map<String, String> values,
            boolean required, int maxLength) {
        out.println("          <div class=\"aira-field\"><label for=\"" + name + "\">" + escapeHtml(label) + "</label>");
        out.print("            <input class=\"aira-input\" id=\"" + name + "\" name=\"" + name + "\" type=\"text\"");
        if (required) {
            out.print(" required");
        }
        if (maxLength > 0) {
            out.print(" maxlength=\"" + maxLength + "\"");
        }
        out.println(" value=\"" + escapeHtml(values.get(name)) + "\" /></div>");
    }

    private void textarea(PrintWriter out, String name, String label, String value) {
        out.println("          <div class=\"aira-field\"><label for=\"" + name + "\">" + escapeHtml(label) + "</label>");
        out.println("            <textarea class=\"aira-textarea\" id=\"" + name + "\" name=\"" + name
                + "\" rows=\"5\">" + escapeHtml(value) + "</textarea></div>");
    }

    private void selectField(PrintWriter out, String name, String label, List<Choice> choices, String selected) {
        out.println("          <div class=\"aira-field\"><label for=\"" + name + "\">" + escapeHtml(label) + "</label>");
        out.println("            <select class=\"aira-select\" id=\"" + name + "\" name=\"" + name + "\">");
        out.println("              <option value=\"\">— Select —</option>");
        for (Choice choice : choices) {
            out.println("              <option value=\"" + escapeHtml(choice.value()) + "\""
                    + (choice.value().equals(selected) ? " selected" : "") + ">"
                    + escapeHtml(choice.label()) + "</option>");
        }
        out.println("            </select></div>");
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private record Choice(String value, String label) {
    }
}
