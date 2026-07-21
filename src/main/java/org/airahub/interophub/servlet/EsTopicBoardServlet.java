package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.airahub.interophub.model.EsTopicPathDefinition;
import org.airahub.interophub.model.EsTopicStageDefinition;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.TopicBoardService;
import org.immregistries.aira.web.AiraDefaults;
import org.immregistries.aira.web.AiraLogo;
import org.immregistries.aira.web.AiraPage;

public class EsTopicBoardServlet extends HttpServlet {

    private static final String APPLICATION_NAME = "InteropHub";
    private static final String APPLICATION_VERSION = resolveVersion();

    private final AuthFlowService authFlowService;
    private final TopicBoardService topicBoardService;

    public EsTopicBoardServlet() {
        this.authFlowService = new AuthFlowService();
        this.topicBoardService = new TopicBoardService();
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

        AiraPage page = AiraPage.builder()
                .applicationName(APPLICATION_NAME)
                .applicationSubtitle("Topic Boards")
                .applicationVersion(APPLICATION_VERSION)
                .documentTitle(board.board().getBoardName() + " - InteropHub")
                .pageHeading(board.board().getBoardName())
                .pageIntro(board.board().getBoardDescription() == null ? "" : board.board().getBoardDescription())
                .mainClass("aira-main interophub-topic-board-main")
                .contextPath(contextPath)
                .identityHref("/home")
                .logo(new AiraLogo(AiraDefaults.DEFAULT_LOGO_PATH, AiraDefaults.DEFAULT_LOGO_ALT_TEXT))
                .addGlobalAction("Topics", "/es/topics", "secondary")
                .addGlobalAction("Welcome", "/welcome", "secondary")
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
        if (board.showUnassignedPath()) {
            pathRows.add(new PathRow(null, "Not assigned", true));
        }

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
            out.println("        <div class=\"tb-board-scroll\">");
            out.println("          <table class=\"tb-board\">");
            out.println("            <thead>");
            out.println("              <tr>");
            out.println("                <th class=\"tb-corner\">Advancement Path / Stage</th>");
            for (StageColumn stage : stageColumns) {
                out.println("                <th class=\"tb-stage\" data-stage-id=\"" + idAttr(stage.stageId()) + "\">"
                        + escapeHtml(stage.stageName()) + "</th>");
            }
            out.println("              </tr>");
            out.println("            </thead>");
            out.println("            <tbody>");

            for (PathRow path : pathRows) {
                out.println("              <tr>");
                out.println("                <th class=\"tb-path\" data-path-id=\"" + idAttr(path.pathId()) + "\">"
                        + escapeHtml(path.pathName()) + "</th>");

                for (StageColumn stage : stageColumns) {
                    TopicBoardService.CellKey key = new TopicBoardService.CellKey(stage.stageId(), path.pathId());
                    List<TopicBoardService.TopicCard> cards = board.cardsByCell().getOrDefault(key, List.of());

                    out.println("                <td class=\"tb-cell\" data-stage-id=\"" + idAttr(stage.stageId())
                            + "\" data-path-id=\"" + idAttr(path.pathId()) + "\">");
                    out.println("                  <div class=\"tb-cell-controls\">");
                    out.println(
                            "                    <button type=\"button\" class=\"aira-button aira-button--tertiary tb-add-toggle\">Add</button>");
                    out.println("                  </div>");
                    out.println("                  <div class=\"tb-add-panel\" hidden>");
                    out.println("                    <label class=\"tb-add-label\">Find topic</label>");
                    out.println(
                            "                    <input type=\"text\" class=\"tb-add-input\" placeholder=\"Search active topics\" autocomplete=\"off\" />");
                    out.println("                    <ul class=\"tb-search-results\"></ul>");
                    out.println("                  </div>");
                    out.println("                  <div class=\"tb-cards\">");

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
            out.println("      </section>");
            out.println("    </div>");

            out.println("    <script src=\"" + contextPath + "/js/topic-board.js\"></script>");

            page.writeEnd(out);
        }
    }

    private void renderCard(PrintWriter out, String contextPath, TopicBoardService.BoardView board,
            TopicBoardService.TopicCard card) {
        out.println("                    <article class=\"tb-card\" data-topic-id=\"" + card.topicId() + "\">");
        out.println(
                "                      <button type=\"button\" class=\"tb-drag-handle\" draggable=\"true\" title=\"Move topic\" aria-label=\"Move topic\">&#x2630;</button>");
        out.println(
                "                      <a class=\"tb-topic-link\" href=\"" + contextPath + "/es/topic/" + card.topicId()
                        + "\">" + escapeHtml(card.topicName()) + "</a>");
        if (board.isCurated()) {
            out.println(
                    "                      <button type=\"button\" class=\"tb-remove-btn\" title=\"Remove from board\" aria-label=\"Remove from board\">Remove from board</button>");
        }
        out.println("                    </article>");
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

    private static String resolveVersion() {
        String appVersion = readVersionFromProperties("/interophub-version.properties", "software.version");
        if (appVersion != null && !appVersion.startsWith("${")) {
            return appVersion;
        }

        String pomVersion = readVersionFromProperties("/META-INF/maven/org.airahub/interophub/pom.properties",
                "version");
        if (pomVersion != null) {
            return pomVersion;
        }

        return "development";
    }

    private static String readVersionFromProperties(String path, String key) {
        Properties properties = new Properties();
        try (InputStream in = AiraDemoServlet.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            properties.load(in);
            String value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        } catch (IOException ex) {
            return null;
        }
    }

    private record StageColumn(Long stageId, String stageName, boolean unassigned) {
    }

    private record PathRow(Long pathId, String pathName, boolean unassigned) {
    }
}
