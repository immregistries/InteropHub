package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.EsTopicPathDefinition;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.EsTopicStageDefinition;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.TopicBoardService;
import org.immregistries.aira.web.AiraPage;

public class EsTopicBoardServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final TopicBoardService topicBoardService;
    private final EsTopicSpaceDao topicSpaceDao;

    public EsTopicBoardServlet() {
        this.authFlowService = new AuthFlowService();
        this.topicBoardService = new TopicBoardService();
        this.topicSpaceDao = new EsTopicSpaceDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html;charset=UTF-8");

        String boardCode = extractBoardCode(request.getPathInfo());
        if (boardCode == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Optional<User> viewer = authFlowService.findAuthenticatedUser(request);
        Optional<TopicBoardService.BoardView> boardView = topicBoardService.loadBoardByCodeForDisplay(boardCode,
                viewer.orElse(null));
        if (boardView.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        TopicBoardService.BoardView board = boardView.get();
        String contextPath = request.getContextPath();
        EsTopicSpace topicSpace = topicSpaceDao.findById(board.board().getEsTopicSpaceId()).orElse(null);
        String spaceName = topicSpace == null ? board.board().getBoardName() : topicSpace.getSpaceName();
        String spaceCode = topicSpace == null ? null : topicSpace.getSpaceCode();

        AiraPage page = InteropAiraPageFactory.base(request, board.board().getBoardName() + " - InteropHub")
                .applicationSubtitle("Topic Boards")
                .pageHeading(board.board().getBoardName())
                .pageIntro(board.board().getBoardDescription() == null ? "" : board.board().getBoardDescription())
                .mainClass("aira-main interophub-topic-board-main")
                .context(InteropAiraPageFactory.topicsMeetingsContext(spaceName, spaceCode, true, false))
                .addLocalStylesheet("/css/topic-board.css")
                .build();

        List<StageColumn> stageColumns = new ArrayList<>();
        for (EsTopicStageDefinition stage : board.displayedStages()) {
            stageColumns.add(new StageColumn(stage.getEsTopicStageDefinitionId(), safe(stage.getStageName()), false));
        }
        if (board.showUnassignedStage()) {
            stageColumns.add(new StageColumn(null, "Not assigned", true));
        }

        List<PathRow> pathRows = new ArrayList<>();
        for (EsTopicPathDefinition path : board.displayedPaths()) {
            pathRows.add(new PathRow(path.getEsTopicPathDefinitionId(), safe(path.getPathName()), false));
        }
        // Always present as the drop-to-remove row, regardless of showUnassignedPath.
        // Its row-header cell (not its data cells) is the trash drop target.
        pathRows.add(new PathRow(null, board.showUnassignedPath() ? "Not assigned" : "", true));

        Long firstPathId = board.displayedPaths().isEmpty() ? null
                : board.displayedPaths().get(0).getEsTopicPathDefinitionId();

        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);

            out.println("    <div class=\"aira-container--wide aira-stack aira-stack--compact\">");
            if (board.board().getCuratorTopicId() != null) {
                out.println(
                        "      <section class=\"aira-alert aira-alert--info\" role=\"status\" aria-live=\"polite\">");
                out.println("        <p class=\"aira-alert__title\">Curated Board</p>");
                out.println(
                        "        <p>This board shows topics curated by the configured curator topic. Drag and Add operations preserve board curation rules.</p>");
                out.println("      </section>");
            }

            out.println(
                    "      <section class=\"tb-shell\" data-board-code=\"" + escapeHtml(board.board().getBoardCode())
                            + "\" data-curated=\"" + board.isCurated() + "\">");
            out.println("        <div class=\"aira-matrix-table-wrap\">");
            out.println("          <table class=\"aira-matrix-table\">");
            out.println("            <thead>");
            out.println("              <tr>");
            out.println(
                    "                <th class=\"aira-matrix-table__corner\" scope=\"col\"><div class=\"aira-matrix-table__header-inner\"><span class=\"aira-matrix-table__label\">Advancement Path / Stage</span></div></th>");
            for (StageColumn stage : stageColumns) {
                out.println("                <th class=\"aira-matrix-table__col-header\" scope=\"col\" data-stage-id=\""
                        + idAttr(stage.stageId())
                        + "\">");
                out.println("                  <div class=\"aira-matrix-table__header-inner\">");
                out.println(
                        "                    <span class=\"aira-matrix-table__label\">" + escapeHtml(stage.stageName())
                                + "</span>");
                if (!stage.unassigned()) {
                    out.println("                    <button type=\"button\" class=\"tb-header-add\" data-stage-id=\""
                            + idAttr(stage.stageId())
                            + "\" data-stage-name=\"" + escapeHtml(stage.stageName())
                            + "\" title=\"Add a topic to " + escapeHtml(stage.stageName()) + "\">+ Add</button>");
                }
                out.println("                  </div>");
                out.println("                </th>");
            }
            out.println("              </tr>");
            out.println("            </thead>");
            out.println("            <tbody>");

            for (PathRow path : pathRows) {
                out.println("              <tr>");
                if (path.unassigned()) {
                    out.println(
                            "                <th class=\"aira-matrix-table__row-header tb-path--trash tb-drop-zone\" scope=\"row\" data-path-id=\"\">");
                    out.println("                  <div class=\"aira-matrix-table__header-inner\">");
                    if (!path.pathName().isEmpty()) {
                        out.println("                    <span class=\"aira-matrix-table__label\">"
                                + escapeHtml(path.pathName()) + "</span>");
                    }
                    out.println(
                            "                    <button type=\"button\" class=\"tb-trash-toggle aira-danger-text\" title=\"Drag a topic here to remove it from the board\" aria-label=\"Drop a topic here to remove it from the board\">&#x1F5D1;&#xFE0F;</button>");
                    out.println("                  </div>");
                    out.println("                </th>");
                } else {
                    out.println(
                            "                <th class=\"aira-matrix-table__row-header\" scope=\"row\" data-path-id=\""
                                    + idAttr(path.pathId()) + "\"><div class=\"aira-matrix-table__header-inner\"><span class=\"aira-matrix-table__label\">"
                                    + escapeHtml(path.pathName()) + "</span></div></th>");
                }

                boolean rowAcceptsPlacement = !path.unassigned() || board.showUnassignedPath();
                for (StageColumn stage : stageColumns) {
                    TopicBoardService.CellKey key = new TopicBoardService.CellKey(stage.stageId(), path.pathId());
                    List<TopicBoardService.TopicCard> cards = board.cardsByCell().getOrDefault(key, List.of());
                    String cellClass = rowAcceptsPlacement
                            ? "aira-matrix-table__cell tb-cell tb-drop-zone"
                            : "aira-matrix-table__cell aira-matrix-table__cell--disabled tb-cell";

                    out.println("                <td class=\"" + cellClass + "\" data-stage-id=\""
                            + idAttr(stage.stageId())
                            + "\" data-path-id=\"" + idAttr(path.pathId()) + "\">");
                    out.println("                  <div class=\"aira-stack aira-stack--compact tb-cards\">");

                    for (TopicBoardService.TopicCard card : cards) {
                        renderCard(out, contextPath, board, card);
                    }

                    out.println("                  </div>");
                    out.println("                </td>");
                }

                out.println("              </tr>");
            }

            out.println("            </tbody>");
            out.println("          </table>");
            out.println("        </div>");

            renderAddModal(out, firstPathId);

            out.println("      </section>");
            out.println("    </div>");

            out.println("    <script src=\"" + contextPath + "/js/topic-board.js\"></script>");

            page.writeEnd(out);
        }
    }

    private void renderCard(PrintWriter out, String contextPath, TopicBoardService.BoardView board,
            TopicBoardService.TopicCard card) {
        out.println("                    <article class=\"aira-entity-card tb-card\" draggable=\"true\" data-topic-id=\""
                + card.topicId() + "\">");
        out.println(
                "                      <button type=\"button\" class=\"aira-entity-card__handle tb-drag-handle\" draggable=\"true\" title=\"Move topic\" aria-label=\"Move topic\">&#x2630;</button>");
        out.println(
                "                      <a class=\"aira-entity-card__title tb-topic-link\" draggable=\"false\" href=\"" + contextPath
                        + "/es/topic/" + card.topicId()
                        + "\">" + escapeHtml(card.topicName()) + "</a>");
        if (board.isCurated()) {
            out.println(
                    "                      <button type=\"button\" class=\"aira-entity-card__action aira-danger-text tb-remove-btn\" title=\"Remove from board\" aria-label=\"Remove from board\">Remove from board</button>");
        }
        out.println("                    </article>");
    }

    private void renderAddModal(PrintWriter out, Long firstPathId) {
        out.println("        <dialog id=\"tb-add-modal\" data-first-path-id=\"" + idAttr(firstPathId) + "\">");
        out.println(
                "          <form method=\"dialog\" class=\"aira-stack\" style=\"min-width:22rem; max-width:32rem;\">");
        out.println("            <h2 id=\"tb-add-modal-title\">Add topic</h2>");
        out.println("            <label for=\"tb-add-modal-input\">Find topic</label>");
        out.println(
                "            <input type=\"text\" id=\"tb-add-modal-input\" class=\"tb-add-input\" placeholder=\"Search active topics\" autocomplete=\"off\" />");
        out.println("            <ul class=\"tb-search-results\" id=\"tb-add-modal-results\"></ul>");
        out.println("            <div class=\"aira-cluster\">");
        out.println(
                "              <button type=\"button\" class=\"aira-button aira-button--tertiary\" id=\"tb-add-modal-cancel\">Cancel</button>");
        out.println("            </div>");
        out.println("          </form>");
        out.println("        </dialog>");
    }

    private String extractBoardCode(String pathInfo) {
        if (pathInfo == null || pathInfo.isBlank()) {
            return null;
        }
        String normalized = pathInfo.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            return null;
        }
        int slash = normalized.indexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(0, slash);
        }
        String candidate = normalized.trim();
        return candidate.isEmpty() ? null : candidate;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String idAttr(Long id) {
        return id == null ? "" : String.valueOf(id);
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

    private record StageColumn(Long stageId, String stageName, boolean unassigned) {
    }

    private record PathRow(Long pathId, String pathName, boolean unassigned) {
    }
}
