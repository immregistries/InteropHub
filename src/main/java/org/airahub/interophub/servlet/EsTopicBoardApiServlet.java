package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.TopicBoardService;

public class EsTopicBoardApiServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final TopicBoardService topicBoardService;

    public EsTopicBoardApiServlet() {
        this.authFlowService = new AuthFlowService();
        this.topicBoardService = new TopicBoardService();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        String action = trimToNull(request.getParameter("action"));
        if (action == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "Action is required.");
            return;
        }

        try {
            switch (action) {
                case "search":
                    handleSearch(request, response);
                    return;
                case "place":
                    handlePlace(request, response);
                    return;
                case "remove":
                    handleRemove(request, response);
                    return;
                default:
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    writeError(response, "Unsupported action.");
            }
        } catch (TopicBoardService.ValidationException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, ex.getMessage());
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writeError(response, "Unexpected board operation error.");
        }
    }

    private void handleSearch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String boardCode = trimToNull(request.getParameter("boardCode"));
        String query = trimToNull(request.getParameter("q"));
        int maxResults = parseIntOrDefault(request.getParameter("max"), 20);

        Optional<User> viewer = authFlowService.findAuthenticatedUser(request);
        List<TopicBoardService.SearchTopicResult> results = topicBoardService.searchActiveTopics(boardCode, query,
                viewer.orElse(null), maxResults);

        try (PrintWriter out = response.getWriter()) {
            out.print("{\"ok\":true,\"topics\":[");
            for (int i = 0; i < results.size(); i++) {
                TopicBoardService.SearchTopicResult result = results.get(i);
                if (i > 0) {
                    out.print(',');
                }
                out.print("{\"topicId\":");
                out.print(result.topicId());
                out.print(",\"topicName\":\"");
                out.print(escapeJson(result.topicName()));
                out.print("\",\"currentStage\":\"");
                out.print(escapeJson(result.currentStageName()));
                out.print("\",\"currentPath\":\"");
                out.print(escapeJson(result.currentPathName()));
                out.print("\"}");
            }
            out.print("]}");
        }
    }

    private void handlePlace(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> user = authFlowService.findAuthenticatedUser(request);
        if (user.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeError(response, "Authentication is required.");
            return;
        }

        String boardCode = trimToNull(request.getParameter("boardCode"));
        Long topicId = parseLong(request.getParameter("topicId"));
        Long stageDefinitionId = parseLongOrNullIfBlank(request.getParameter("stageDefinitionId"));
        Long pathDefinitionId = parseLongOrNullIfBlank(request.getParameter("pathDefinitionId"));

        TopicBoardService.PlacementResult placement = topicBoardService.placeTopic(
                boardCode,
                topicId,
                stageDefinitionId,
                pathDefinitionId,
                user.get());

        try (PrintWriter out = response.getWriter()) {
            out.print("{\"ok\":true,\"topic\":{");
            out.print("\"topicId\":");
            out.print(placement.topicId());
            out.print(",\"topicName\":\"");
            out.print(escapeJson(placement.topicName()));
            out.print("\",\"topicUrl\":\"");
            out.print(escapeJson(request.getContextPath() + "/es/topic/" + placement.topicId()));
            out.print("\"},\"stageDefinitionId\":");
            if (placement.stageDefinitionId() == null) {
                out.print("null");
            } else {
                out.print(placement.stageDefinitionId());
            }
            out.print(",\"pathDefinitionId\":");
            if (placement.pathDefinitionId() == null) {
                out.print("null");
            } else {
                out.print(placement.pathDefinitionId());
            }
            out.print(",\"curatedBoard\":");
            out.print(placement.curatedBoard());
            out.print("}");
        }
    }

    private void handleRemove(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> user = authFlowService.findAuthenticatedUser(request);
        if (user.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeError(response, "Authentication is required.");
            return;
        }

        String boardCode = trimToNull(request.getParameter("boardCode"));
        Long topicId = parseLong(request.getParameter("topicId"));

        topicBoardService.removeFromCuratedBoard(boardCode, topicId, user.get());

        try (PrintWriter out = response.getWriter()) {
            out.print("{\"ok\":true}");
        }
    }

    private void writeError(HttpServletResponse response, String message) throws IOException {
        try (PrintWriter out = response.getWriter()) {
            out.print("{\"ok\":false,\"error\":\"");
            out.print(escapeJson(message));
            out.print("\"}");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
