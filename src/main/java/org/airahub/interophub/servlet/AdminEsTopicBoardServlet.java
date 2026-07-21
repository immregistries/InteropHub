package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicBoardDefinition;
import org.airahub.interophub.model.EsTopicPathDefinition;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.EsTopicStageDefinition;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.TopicBoardRules;
import org.airahub.interophub.service.TopicBoardService;

public class AdminEsTopicBoardServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final TopicBoardService topicBoardService;

    public AdminEsTopicBoardServlet() {
        this.authFlowService = new AuthFlowService();
        this.topicBoardService = new TopicBoardService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String mode = trimToNull(request.getParameter("mode"));
        Long boardDefinitionId = parseLong(request.getParameter("esTopicBoardDefinitionId"));

        if ("new".equalsIgnoreCase(mode)) {
            renderEdit(response, contextPath, topicBoardService.loadBoardEditData(null), true, null);
            return;
        }

        if ("edit".equalsIgnoreCase(mode) && boardDefinitionId != null) {
            try {
                renderEdit(response, contextPath, topicBoardService.loadBoardEditData(boardDefinitionId), false, null);
            } catch (TopicBoardService.ValidationException ex) {
                renderList(response, contextPath, ex.getMessage());
            }
            return;
        }

        String message = request.getParameter("saved") != null ? "Topic Board saved." : null;
        renderList(response, contextPath, message);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String action = trimToNull(request.getParameter("action"));
        Long boardDefinitionId = parseLong(request.getParameter("esTopicBoardDefinitionId"));
        boolean creating = boardDefinitionId == null;

        if ("changeSpace".equalsIgnoreCase(action)) {
            renderEditForSpaceChange(response, contextPath, request, creating);
            return;
        }

        try {
            TopicBoardService.BoardSaveRequest saveRequest = buildSaveRequest(request, boardDefinitionId);
            EsTopicBoardDefinition saved = topicBoardService.saveBoard(saveRequest, creating);
            response.sendRedirect(contextPath + "/admin/es/topic-boards?saved=1&esTopicBoardDefinitionId="
                    + saved.getEsTopicBoardDefinitionId());
        } catch (TopicBoardService.ValidationException ex) {
            renderEditWithPostedValues(response, contextPath, request, creating, ex.getMessage());
        }
    }

    private void renderList(HttpServletResponse response, String contextPath, String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        List<TopicBoardService.AdminBoardRow> rows = topicBoardService.listBoardDefinitions();

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Topic Boards Admin - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Topic Boards</h2>");
                panelOut.println("        <p>Manage reusable topic board configurations by Topic Space.</p>");
                if (message != null && !message.isBlank()) {
                    panelOut.println("        <p><strong>" + escapeHtml(message) + "</strong></p>");
                }
                panelOut.println("        <p><a href=\"" + contextPath
                        + "/admin/es/topic-boards?mode=new\">Add Topic Board</a></p>");

                panelOut.println("        <table class=\"data-table\">");
                panelOut.println("          <thead>");
                panelOut.println("            <tr>");
                panelOut.println("              <th>Board Name</th>");
                panelOut.println("              <th>Board Code</th>");
                panelOut.println("              <th>Topic Space</th>");
                panelOut.println("              <th>Curator Topic</th>");
                panelOut.println("              <th>Active</th>");
                panelOut.println("              <th>View</th>");
                panelOut.println("              <th>Edit</th>");
                panelOut.println("            </tr>");
                panelOut.println("          </thead>");
                panelOut.println("          <tbody>");
                for (TopicBoardService.AdminBoardRow row : rows) {
                    panelOut.println("            <tr>");
                    panelOut.println("              <td>" + escapeHtml(row.boardName()) + "</td>");
                    panelOut.println("              <td>" + escapeHtml(row.boardCode()) + "</td>");
                    panelOut.println("              <td>" + escapeHtml(row.topicSpaceName()) + "</td>");
                    panelOut.println("              <td>" + escapeHtml(row.curatorTopicName()) + "</td>");
                    panelOut.println("              <td>" + (row.active() ? "Yes" : "No") + "</td>");
                    panelOut.println("              <td><a href=\"" + contextPath
                            + "/es/board/" + urlEncodePathSegment(row.boardCode())
                            + "\" target=\"_blank\" rel=\"noopener\">Open</a></td>");
                    panelOut.println("              <td><a href=\"" + contextPath
                            + "/admin/es/topic-boards?mode=edit&esTopicBoardDefinitionId="
                            + row.boardDefinitionId() + "\">Edit</a></td>");
                    panelOut.println("            </tr>");
                }
                if (rows.isEmpty()) {
                    panelOut.println("            <tr><td colspan=\"7\">No board definitions found.</td></tr>");
                }
                panelOut.println("          </tbody>");
                panelOut.println("        </table>");
                panelOut.println(
                        "        <p><a href=\"" + contextPath + "/admin/es\">Back to Emerging Standards</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderEdit(HttpServletResponse response, String contextPath, TopicBoardService.BoardEditData data,
            boolean creating, String errorMessage) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        EsTopicBoardDefinition board = data.board();
        Long selectedSpaceId = board.getEsTopicSpaceId();

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, (creating ? "Create" : "Edit") + " Topic Board - InteropHub", contextPath,
                    panelOut -> {
                        panelOut.println("      <section class=\"panel\">");
                        panelOut.println(
                                "        <h2>" + (creating ? "Create Topic Board" : "Edit Topic Board") + "</h2>");
                        panelOut.println(
                                "        <p>Configure displayed stages and paths for a reusable board URL.</p>");
                        if (errorMessage != null && !errorMessage.isBlank()) {
                            panelOut.println("        <p><strong>" + escapeHtml(errorMessage) + "</strong></p>");
                        }

                        panelOut.println("        <form class=\"login-form\" method=\"post\" action=\"" + contextPath
                                + "/admin/es/topic-boards\">");
                        if (!creating) {
                            panelOut.println(
                                    "          <input type=\"hidden\" name=\"esTopicBoardDefinitionId\" value=\""
                                            + board.getEsTopicBoardDefinitionId() + "\" />");
                        }

                        panelOut.println("          <label for=\"boardName\">Board name</label>");
                        panelOut.println("          <input id=\"boardName\" name=\"boardName\" required value=\""
                                + escapeHtml(orEmpty(board.getBoardName())) + "\" />");

                        panelOut.println("          <label for=\"boardCode\">Board code</label>");
                        if (creating) {
                            panelOut.println("          <input id=\"boardCode\" name=\"boardCode\" required value=\""
                                    + escapeHtml(orEmpty(board.getBoardCode())) + "\" />");
                        } else {
                            panelOut.println("          <input id=\"boardCode\" name=\"boardCode\" value=\""
                                    + escapeHtml(orEmpty(board.getBoardCode()))
                                    + "\" readonly aria-readonly=\"true\" />");
                            panelOut.println(
                                    "          <span class=\"field-hint\">Board code is stable and used in saved links.</span>");
                        }

                        panelOut.println("          <label for=\"boardDescription\">Description</label>");
                        panelOut.println(
                                "          <textarea id=\"boardDescription\" name=\"boardDescription\" rows=\"3\">"
                                        + escapeHtml(orEmpty(board.getBoardDescription())) + "</textarea>");

                        panelOut.println("          <label for=\"esTopicSpaceId\">Topic Space</label>");
                        panelOut.println("          <select id=\"esTopicSpaceId\" name=\"esTopicSpaceId\" required>");
                        panelOut.println("            <option value=\"\">- Select -</option>");
                        for (EsTopicSpace space : data.topicSpaces()) {
                            boolean selected = selectedSpaceId != null
                                    && selectedSpaceId.equals(space.getEsTopicSpaceId());
                            panelOut.println("            <option value=\"" + space.getEsTopicSpaceId() + "\""
                                    + (selected ? " selected" : "") + ">" + escapeHtml(orEmpty(space.getSpaceName()))
                                    + (Boolean.TRUE.equals(space.getIsActive()) ? "" : " (inactive)") + "</option>");
                        }
                        panelOut.println("          </select>");

                        panelOut.println("          <div class=\"form-actions\">");
                        panelOut.println(
                                "            <button type=\"submit\" name=\"action\" value=\"changeSpace\">Reload stage/path options</button>");
                        panelOut.println("          </div>");

                        panelOut.println("          <label for=\"curatorTopicId\">Curator topic (optional)</label>");
                        panelOut.println("          <select id=\"curatorTopicId\" name=\"curatorTopicId\">");
                        panelOut.println("            <option value=\"\">- None -</option>");
                        for (EsTopic topic : data.curatorCandidates()) {
                            if (topic.getEsTopicId() == null) {
                                continue;
                            }
                            boolean selected = board.getCuratorTopicId() != null
                                    && board.getCuratorTopicId().equals(topic.getEsTopicId());
                            panelOut.println("            <option value=\"" + topic.getEsTopicId() + "\""
                                    + (selected ? " selected" : "") + ">" + escapeHtml(orEmpty(topic.getTopicName()))
                                    + "</option>");
                        }
                        panelOut.println("          </select>");

                        panelOut.println("          <label><input type=\"checkbox\" name=\"showUnassignedStage\""
                                + (Boolean.TRUE.equals(board.getShowUnassignedStage()) ? " checked" : "")
                                + " /> Show Not assigned stage</label>");

                        panelOut.println("          <label><input type=\"checkbox\" name=\"showUnassignedPath\""
                                + (Boolean.TRUE.equals(board.getShowUnassignedPath()) ? " checked" : "")
                                + " /> Show Not assigned path</label>");

                        panelOut.println("          <label><input type=\"checkbox\" name=\"isActive\""
                                + (Boolean.TRUE.equals(board.getIsActive()) ? " checked" : "")
                                + " /> Active</label>");

                        panelOut.println("          <section class=\"panel\">");
                        panelOut.println("            <h3>Included stages</h3>");
                        renderStageOptions(panelOut, data.options().activeStages(), data.selectedStageOrder());
                        panelOut.println("          </section>");

                        panelOut.println("          <section class=\"panel\">");
                        panelOut.println("            <h3>Included paths</h3>");
                        renderPathOptions(panelOut, data.options().activePaths(), data.selectedPathOrder());
                        panelOut.println("          </section>");

                        panelOut.println("          <div class=\"form-actions\">");
                        panelOut.println("            <button type=\"submit\">Save Board</button>");
                        panelOut.println("            <a class=\"button-link\" href=\"" + contextPath
                                + "/admin/es/topic-boards\">Cancel</a>");
                        panelOut.println("          </div>");

                        panelOut.println("        </form>");
                        panelOut.println("      </section>");
                    });
        }
    }

    private void renderStageOptions(PrintWriter out, List<EsTopicStageDefinition> stageOptions,
            Map<Long, Integer> selectedOrder) {
        if (stageOptions.isEmpty()) {
            out.println("            <p>No active stages are available for the selected Topic Space.</p>");
            return;
        }
        out.println("            <table class=\"data-table\">");
        out.println("              <thead><tr><th>Include</th><th>Stage</th><th>Order</th></tr></thead>");
        out.println("              <tbody>");
        for (EsTopicStageDefinition stage : stageOptions) {
            Long stageId = stage.getEsTopicStageDefinitionId();
            boolean selected = selectedOrder.containsKey(stageId);
            int order = selectedOrder.getOrDefault(stageId,
                    stage.getDisplayOrder() == null ? 0 : stage.getDisplayOrder());
            out.println("                <tr>");
            out.println("                  <td><input type=\"checkbox\" name=\"stageId\" value=\"" + stageId + "\""
                    + (selected ? " checked" : "") + " /></td>");
            out.println("                  <td>" + escapeHtml(orEmpty(stage.getStageName())) + "</td>");
            out.println(
                    "                  <td><input type=\"number\" name=\"stageOrder_" + stageId + "\" value=\"" + order
                            + "\" /></td>");
            out.println("                </tr>");
        }
        out.println("              </tbody>");
        out.println("            </table>");
    }

    private void renderPathOptions(PrintWriter out, List<EsTopicPathDefinition> pathOptions,
            Map<Long, Integer> selectedOrder) {
        if (pathOptions.isEmpty()) {
            out.println("            <p>No active paths are available for the selected Topic Space.</p>");
            return;
        }
        out.println("            <table class=\"data-table\">");
        out.println("              <thead><tr><th>Include</th><th>Path</th><th>Order</th></tr></thead>");
        out.println("              <tbody>");
        for (EsTopicPathDefinition path : pathOptions) {
            Long pathId = path.getEsTopicPathDefinitionId();
            boolean selected = selectedOrder.containsKey(pathId);
            int order = selectedOrder.getOrDefault(pathId, path.getDisplayOrder() == null ? 0 : path.getDisplayOrder());
            out.println("                <tr>");
            out.println("                  <td><input type=\"checkbox\" name=\"pathId\" value=\"" + pathId + "\""
                    + (selected ? " checked" : "") + " /></td>");
            out.println("                  <td>" + escapeHtml(orEmpty(path.getPathName())) + "</td>");
            out.println(
                    "                  <td><input type=\"number\" name=\"pathOrder_" + pathId + "\" value=\"" + order
                            + "\" /></td>");
            out.println("                </tr>");
        }
        out.println("              </tbody>");
        out.println("            </table>");
    }

    private void renderEditForSpaceChange(HttpServletResponse response, String contextPath, HttpServletRequest request,
            boolean creating) throws IOException {
        Long boardDefinitionId = parseLong(request.getParameter("esTopicBoardDefinitionId"));
        Long topicSpaceId = parseLong(request.getParameter("esTopicSpaceId"));

        TopicBoardService.BoardEditData data;
        if (creating) {
            data = topicBoardService.loadBoardEditData(null);
        } else {
            data = topicBoardService.loadBoardEditData(boardDefinitionId);
        }

        EsTopicBoardDefinition board = data.board();
        board.setBoardName(trimToNull(request.getParameter("boardName")));
        board.setBoardDescription(trimToNull(request.getParameter("boardDescription")));
        if (creating) {
            board.setBoardCode(trimToNull(request.getParameter("boardCode")));
        }
        board.setEsTopicSpaceId(topicSpaceId);
        board.setCuratorTopicId(parseLongOrNullIfBlank(request.getParameter("curatorTopicId")));
        board.setShowUnassignedStage(request.getParameter("showUnassignedStage") != null);
        board.setShowUnassignedPath(request.getParameter("showUnassignedPath") != null);
        board.setIsActive(request.getParameter("isActive") != null);

        TopicBoardService.OptionsBundle options = topicBoardService.loadOptions(topicSpaceId);

        Set<Long> selectedStageIds = parseIdSet(request.getParameterValues("stageId"));
        Set<Long> selectedPathIds = parseIdSet(request.getParameterValues("pathId"));

        selectedStageIds = TopicBoardRules.filterCompatibleDefinitionIds(selectedStageIds, options.activeStageIds());
        selectedPathIds = TopicBoardRules.filterCompatibleDefinitionIds(selectedPathIds, options.activePathIds());

        Map<Long, Integer> stageOrder = parseOrderedMap(selectedStageIds, "stageOrder_", request);
        Map<Long, Integer> pathOrder = parseOrderedMap(selectedPathIds, "pathOrder_", request);

        TopicBoardService.BoardEditData changed = new TopicBoardService.BoardEditData(
                board,
                data.topicSpaces(),
                data.curatorCandidates(),
                options,
                stageOrder,
                pathOrder);

        renderEdit(response, contextPath, changed, creating,
                "Topic Space changed. Incompatible stage/path selections were cleared.");
    }

    private void renderEditWithPostedValues(HttpServletResponse response, String contextPath,
            HttpServletRequest request,
            boolean creating, String errorMessage) throws IOException {
        Long boardDefinitionId = parseLong(request.getParameter("esTopicBoardDefinitionId"));
        Long topicSpaceId = parseLong(request.getParameter("esTopicSpaceId"));

        TopicBoardService.BoardEditData data = creating
                ? topicBoardService.loadBoardEditData(null)
                : topicBoardService.loadBoardEditData(boardDefinitionId);

        EsTopicBoardDefinition board = data.board();
        board.setBoardName(trimToNull(request.getParameter("boardName")));
        board.setBoardDescription(trimToNull(request.getParameter("boardDescription")));
        if (creating) {
            board.setBoardCode(trimToNull(request.getParameter("boardCode")));
        }
        board.setEsTopicSpaceId(topicSpaceId);
        board.setCuratorTopicId(parseLongOrNullIfBlank(request.getParameter("curatorTopicId")));
        board.setShowUnassignedStage(request.getParameter("showUnassignedStage") != null);
        board.setShowUnassignedPath(request.getParameter("showUnassignedPath") != null);
        board.setIsActive(request.getParameter("isActive") != null);

        TopicBoardService.OptionsBundle options = topicBoardService.loadOptions(topicSpaceId);

        Set<Long> selectedStageIds = parseIdSet(request.getParameterValues("stageId"));
        Set<Long> selectedPathIds = parseIdSet(request.getParameterValues("pathId"));
        selectedStageIds = TopicBoardRules.filterCompatibleDefinitionIds(selectedStageIds, options.activeStageIds());
        selectedPathIds = TopicBoardRules.filterCompatibleDefinitionIds(selectedPathIds, options.activePathIds());

        Map<Long, Integer> stageOrder = parseOrderedMap(selectedStageIds, "stageOrder_", request);
        Map<Long, Integer> pathOrder = parseOrderedMap(selectedPathIds, "pathOrder_", request);

        TopicBoardService.BoardEditData changed = new TopicBoardService.BoardEditData(
                board,
                data.topicSpaces(),
                data.curatorCandidates(),
                options,
                stageOrder,
                pathOrder);

        renderEdit(response, contextPath, changed, creating, errorMessage);
    }

    private TopicBoardService.BoardSaveRequest buildSaveRequest(HttpServletRequest request, Long boardDefinitionId) {
        Long topicSpaceId = parseLong(request.getParameter("esTopicSpaceId"));

        TopicBoardService.OptionsBundle options = topicBoardService.loadOptions(topicSpaceId);

        Set<Long> selectedStageIds = parseIdSet(request.getParameterValues("stageId"));
        Set<Long> selectedPathIds = parseIdSet(request.getParameterValues("pathId"));

        selectedStageIds = TopicBoardRules.filterCompatibleDefinitionIds(selectedStageIds, options.activeStageIds());
        selectedPathIds = TopicBoardRules.filterCompatibleDefinitionIds(selectedPathIds, options.activePathIds());

        Map<Long, Integer> stageOrder = parseOrderedMap(selectedStageIds, "stageOrder_", request);
        Map<Long, Integer> pathOrder = parseOrderedMap(selectedPathIds, "pathOrder_", request);

        return new TopicBoardService.BoardSaveRequest(
                boardDefinitionId,
                trimToNull(request.getParameter("boardCode")),
                trimToNull(request.getParameter("boardName")),
                trimToNull(request.getParameter("boardDescription")),
                topicSpaceId,
                parseLongOrNullIfBlank(request.getParameter("curatorTopicId")),
                request.getParameter("showUnassignedStage") != null,
                request.getParameter("showUnassignedPath") != null,
                request.getParameter("isActive") != null,
                stageOrder,
                pathOrder);
    }

    private Map<Long, Integer> parseOrderedMap(Set<Long> selectedIds, String prefix, HttpServletRequest request) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (Long id : selectedIds) {
            int order = parseIntOrDefault(request.getParameter(prefix + id), 0);
            result.put(id, order);
        }
        return result;
    }

    private Set<Long> parseIdSet(String[] values) {
        if (values == null || values.length == 0) {
            return Set.of();
        }
        return java.util.Arrays.stream(values)
                .map(this::parseLong)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

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
                panelOut.println("        <p>You must be an InteropHub admin to access this page.</p>");
                panelOut.println("        <p><a href=\"" + contextPath + "/admin\">Return to Admin Home</a></p>");
                panelOut.println("      </section>");
            });
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

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long parseLongOrNullIfBlank(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return parseLong(normalized);
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        try {
            if (value == null) {
                return defaultValue;
            }
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
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

    private String urlEncodePathSegment(String value) {
        if (value == null) {
            return "";
        }
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
}
