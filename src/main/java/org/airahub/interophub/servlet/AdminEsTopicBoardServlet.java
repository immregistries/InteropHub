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
import org.airahub.interophub.service.TopicBoardRules;
import org.airahub.interophub.service.TopicBoardService;

public class AdminEsTopicBoardServlet extends HttpServlet {

    private static final String ACTIVE_HREF = "/admin/es/topic-boards";

    private final TopicBoardService topicBoardService;

    public AdminEsTopicBoardServlet() {
        this.topicBoardService = new TopicBoardService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String mode = trimToNull(request.getParameter("mode"));
        Long boardDefinitionId = parseLong(request.getParameter("esTopicBoardDefinitionId"));

        if ("new".equalsIgnoreCase(mode)) {
            renderEdit(request, response, topicBoardService.loadBoardEditData(null), true, null);
            return;
        }

        if ("edit".equalsIgnoreCase(mode) && boardDefinitionId != null) {
            try {
                renderEdit(request, response, topicBoardService.loadBoardEditData(boardDefinitionId), false, null);
            } catch (TopicBoardService.ValidationException ex) {
                renderList(request, response, ex.getMessage());
            }
            return;
        }

        String message = request.getParameter("saved") != null ? "Topic Board saved." : null;
        renderList(request, response, message);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String action = trimToNull(request.getParameter("action"));
        Long boardDefinitionId = parseLong(request.getParameter("esTopicBoardDefinitionId"));
        boolean creating = boardDefinitionId == null;

        if ("changeSpace".equalsIgnoreCase(action)) {
            renderEditForSpaceChange(request, response, creating);
            return;
        }

        try {
            TopicBoardService.BoardSaveRequest saveRequest = buildSaveRequest(request, boardDefinitionId);
            EsTopicBoardDefinition saved = topicBoardService.saveBoard(saveRequest, creating);
            response.sendRedirect(contextPath + "/admin/es/topic-boards?saved=1&esTopicBoardDefinitionId="
                    + saved.getEsTopicBoardDefinitionId());
        } catch (TopicBoardService.ValidationException ex) {
            renderEditWithPostedValues(request, response, creating, ex.getMessage());
        }
    }

    private void renderList(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        String contextPath = request.getContextPath();
        List<TopicBoardService.AdminBoardRow> rows = topicBoardService.listBoardDefinitions();

        AdminShellRenderer.render(request, response, "Topic Boards Admin - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Topic Boards</h2>");
                    out.println(
                            "            <p class=\"aira-meta\">Manage reusable topic board configurations by Topic Space.</p>");
                    if (message != null && !message.isBlank()) {
                        out.println("            <div class=\"aira-alert aira-alert--success\"><p>"
                                + escapeHtml(message) + "</p></div>");
                    }
                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--primary\" href=\"" + contextPath
                            + "/admin/es/topic-boards?mode=new\">Add Topic Board</a>");
                    out.println("            </div>");

                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println("              <thead>");
                    out.println("                <tr>");
                    out.println("                  <th>Board Name</th>");
                    out.println("                  <th>Board Code</th>");
                    out.println("                  <th>Topic Space</th>");
                    out.println("                  <th>Curator Topic</th>");
                    out.println("                  <th>Active</th>");
                    out.println("                  <th>View</th>");
                    out.println("                  <th>Edit</th>");
                    out.println("                </tr>");
                    out.println("              </thead>");
                    out.println("              <tbody>");
                    for (TopicBoardService.AdminBoardRow row : rows) {
                        out.println("                <tr>");
                        out.println("                  <td>" + escapeHtml(row.boardName()) + "</td>");
                        out.println("                  <td>" + escapeHtml(row.boardCode()) + "</td>");
                        out.println("                  <td>" + escapeHtml(row.topicSpaceName()) + "</td>");
                        out.println("                  <td>" + escapeHtml(row.curatorTopicName()) + "</td>");
                        out.println("                  <td>" + activeBadge(row.active()) + "</td>");
                        out.println("                  <td><a class=\"aira-inline-link\" href=\"" + contextPath
                                + "/es/board/" + urlEncodePathSegment(row.boardCode())
                                + "\" target=\"_blank\" rel=\"noopener\">Open</a></td>");
                        out.println("                  <td><a class=\"aira-inline-link\" href=\"" + contextPath
                                + "/admin/es/topic-boards?mode=edit&esTopicBoardDefinitionId="
                                + row.boardDefinitionId() + "\">Edit</a></td>");
                        out.println("                </tr>");
                    }
                    if (rows.isEmpty()) {
                        out.println("                <tr><td colspan=\"7\">No board definitions found.</td></tr>");
                    }
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es\">Back to Emerging Standards</a></p>");
                    out.println("          </section>");
                });
    }

    private void renderEdit(HttpServletRequest request, HttpServletResponse response,
            TopicBoardService.BoardEditData data, boolean creating, String errorMessage) throws IOException {
        String contextPath = request.getContextPath();
        EsTopicBoardDefinition board = data.board();
        Long selectedSpaceId = board.getEsTopicSpaceId();

        AdminShellRenderer.render(request, response, (creating ? "Create" : "Edit") + " Topic Board - InteropHub",
                AdminSection.TOPIC_SPACES, ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println(
                            "            <h2 class=\"aira-section-title\">"
                                    + (creating ? "Create Topic Board" : "Edit Topic Board") + "</h2>");
                    out.println(
                            "            <p class=\"aira-meta\">Configure displayed stages and paths for a reusable board URL.</p>");
                    if (errorMessage != null && !errorMessage.isBlank()) {
                        out.println("            <div class=\"aira-alert aira-alert--danger\"><p>"
                                + escapeHtml(errorMessage) + "</p></div>");
                    }

                    out.println("            <form class=\"aira-form\" method=\"post\" action=\"" + contextPath
                            + "/admin/es/topic-boards\">");
                    if (!creating) {
                        out.println("              <input type=\"hidden\" name=\"esTopicBoardDefinitionId\" value=\""
                                + board.getEsTopicBoardDefinitionId() + "\" />");
                    }

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"boardName\">Board name</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"boardName\" name=\"boardName\" required value=\""
                                    + escapeHtml(orEmpty(board.getBoardName())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"boardCode\">Board code</label>");
                    if (creating) {
                        out.println(
                                "                <input class=\"aira-input\" id=\"boardCode\" name=\"boardCode\" required value=\""
                                        + escapeHtml(orEmpty(board.getBoardCode())) + "\" />");
                    } else {
                        out.println(
                                "                <input class=\"aira-input\" id=\"boardCode\" name=\"boardCode\" value=\""
                                        + escapeHtml(orEmpty(board.getBoardCode()))
                                        + "\" readonly aria-readonly=\"true\" />");
                        out.println(
                                "                <p class=\"aira-field-help\">Board code is stable and used in saved links.</p>");
                    }
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"boardDescription\">Description</label>");
                    out.println(
                            "                <textarea class=\"aira-textarea\" id=\"boardDescription\" name=\"boardDescription\" rows=\"3\">"
                                    + escapeHtml(orEmpty(board.getBoardDescription())) + "</textarea>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"esTopicSpaceId\">Topic Space</label>");
                    out.println(
                            "                <select class=\"aira-select\" id=\"esTopicSpaceId\" name=\"esTopicSpaceId\" required>");
                    out.println("                  <option value=\"\">- Select -</option>");
                    for (EsTopicSpace space : data.topicSpaces()) {
                        boolean selected = selectedSpaceId != null
                                && selectedSpaceId.equals(space.getEsTopicSpaceId());
                        out.println("                  <option value=\"" + space.getEsTopicSpaceId() + "\""
                                + (selected ? " selected" : "") + ">" + escapeHtml(orEmpty(space.getSpaceName()))
                                + (Boolean.TRUE.equals(space.getIsActive()) ? "" : " (inactive)") + "</option>");
                    }
                    out.println("                </select>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-action-group\">");
                    out.println(
                            "                <button class=\"aira-button aira-button--secondary\" type=\"submit\" name=\"action\" value=\"changeSpace\">Reload stage/path options</button>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"curatorTopicId\">Curator topic (optional)</label>");
                    out.println(
                            "                <select class=\"aira-select\" id=\"curatorTopicId\" name=\"curatorTopicId\">");
                    out.println("                  <option value=\"\">- None -</option>");
                    for (EsTopic topic : data.curatorCandidates()) {
                        if (topic.getEsTopicId() == null) {
                            continue;
                        }
                        boolean selected = board.getCuratorTopicId() != null
                                && board.getCuratorTopicId().equals(topic.getEsTopicId());
                        out.println("                  <option value=\"" + topic.getEsTopicId() + "\""
                                + (selected ? " selected" : "") + ">" + escapeHtml(orEmpty(topic.getTopicName()))
                                + "</option>");
                    }
                    out.println("                </select>");
                    out.println("              </div>");

                    out.println("              <label class=\"aira-radio\"><input type=\"checkbox\" name=\"showUnassignedStage\""
                            + (Boolean.TRUE.equals(board.getShowUnassignedStage()) ? " checked" : "")
                            + " /> Show Not assigned stage</label>");

                    out.println("              <label class=\"aira-radio\"><input type=\"checkbox\" name=\"showUnassignedPath\""
                            + (Boolean.TRUE.equals(board.getShowUnassignedPath()) ? " checked" : "")
                            + " /> Show Not assigned path</label>");

                    out.println("              <label class=\"aira-radio\"><input type=\"checkbox\" name=\"isActive\""
                            + (Boolean.TRUE.equals(board.getIsActive()) ? " checked" : "")
                            + " /> Active</label>");

                    out.println("              <section class=\"aira-panel\">");
                    out.println("                <h3 class=\"aira-subsection-title\">Included stages</h3>");
                    renderStageOptions(out, data.options().activeStages(), data.selectedStageOrder());
                    out.println("              </section>");

                    out.println("              <section class=\"aira-panel\">");
                    out.println("                <h3 class=\"aira-subsection-title\">Included paths</h3>");
                    renderPathOptions(out, data.options().activePaths(), data.selectedPathOrder());
                    out.println("              </section>");

                    out.println("              <div class=\"aira-action-group\">");
                    out.println(
                            "                <button class=\"aira-button aira-button--primary\" type=\"submit\">Save Board</button>");
                    out.println("                <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/topic-boards\">Cancel</a>");
                    out.println("              </div>");

                    out.println("            </form>");
                    out.println("          </section>");
                });
    }

    private void renderStageOptions(PrintWriter out, List<EsTopicStageDefinition> stageOptions,
            Map<Long, Integer> selectedOrder) {
        if (stageOptions.isEmpty()) {
            out.println("                <p class=\"aira-meta\">No active stages are available for the selected Topic Space.</p>");
            return;
        }
        out.println("                <div class=\"aira-table-wrap\">");
        out.println("                <table class=\"aira-table\">");
        out.println("                  <thead><tr><th>Include</th><th>Stage</th><th>Order</th></tr></thead>");
        out.println("                  <tbody>");
        for (EsTopicStageDefinition stage : stageOptions) {
            Long stageId = stage.getEsTopicStageDefinitionId();
            boolean selected = selectedOrder.containsKey(stageId);
            int order = selectedOrder.getOrDefault(stageId,
                    stage.getDisplayOrder() == null ? 0 : stage.getDisplayOrder());
            out.println("                    <tr>");
            out.println("                      <td><input type=\"checkbox\" name=\"stageId\" value=\"" + stageId
                    + "\"" + (selected ? " checked" : "") + " /></td>");
            out.println("                      <td>" + escapeHtml(orEmpty(stage.getStageName())) + "</td>");
            out.println("                      <td><input class=\"aira-input\" type=\"number\" name=\"stageOrder_"
                    + stageId + "\" value=\"" + order + "\" /></td>");
            out.println("                    </tr>");
        }
        out.println("                  </tbody>");
        out.println("                </table>");
        out.println("                </div>");
    }

    private void renderPathOptions(PrintWriter out, List<EsTopicPathDefinition> pathOptions,
            Map<Long, Integer> selectedOrder) {
        if (pathOptions.isEmpty()) {
            out.println("                <p class=\"aira-meta\">No active paths are available for the selected Topic Space.</p>");
            return;
        }
        out.println("                <div class=\"aira-table-wrap\">");
        out.println("                <table class=\"aira-table\">");
        out.println("                  <thead><tr><th>Include</th><th>Path</th><th>Order</th></tr></thead>");
        out.println("                  <tbody>");
        for (EsTopicPathDefinition path : pathOptions) {
            Long pathId = path.getEsTopicPathDefinitionId();
            boolean selected = selectedOrder.containsKey(pathId);
            int order = selectedOrder.getOrDefault(pathId, path.getDisplayOrder() == null ? 0 : path.getDisplayOrder());
            out.println("                    <tr>");
            out.println("                      <td><input type=\"checkbox\" name=\"pathId\" value=\"" + pathId
                    + "\"" + (selected ? " checked" : "") + " /></td>");
            out.println("                      <td>" + escapeHtml(orEmpty(path.getPathName())) + "</td>");
            out.println("                      <td><input class=\"aira-input\" type=\"number\" name=\"pathOrder_"
                    + pathId + "\" value=\"" + order + "\" /></td>");
            out.println("                    </tr>");
        }
        out.println("                  </tbody>");
        out.println("                </table>");
        out.println("                </div>");
    }

    private void renderEditForSpaceChange(HttpServletRequest request, HttpServletResponse response,
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

        renderEdit(request, response, changed, creating,
                "Topic Space changed. Incompatible stage/path selections were cleared.");
    }

    private void renderEditWithPostedValues(HttpServletRequest request, HttpServletResponse response,
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

        renderEdit(request, response, changed, creating, errorMessage);
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

    private String activeBadge(boolean active) {
        if (active) {
            return "<span class=\"aira-badge aira-badge--success\">Yes</span>";
        }
        return "<span class=\"aira-badge aira-badge--subtle\">No</span>";
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
