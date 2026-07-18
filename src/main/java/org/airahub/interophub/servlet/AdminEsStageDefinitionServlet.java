package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.EsTopicStageDefinitionDao;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.EsTopicStageDefinition;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

public class AdminEsStageDefinitionServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsTopicStageDefinitionDao stageDefinitionDao;
    private final EsTopicSpaceDao topicSpaceDao;

    public AdminEsStageDefinitionServlet() {
        this.authFlowService = new AuthFlowService();
        this.stageDefinitionDao = new EsTopicStageDefinitionDao();
        this.topicSpaceDao = new EsTopicSpaceDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String mode = trimToNull(request.getParameter("mode"));
        String definitionIdRaw = trimToNull(request.getParameter("esTopicStageDefinitionId"));
        Long selectedTopicSpaceId = parseId(trimToNull(request.getParameter("esTopicSpaceId")));

        if ("new".equalsIgnoreCase(mode)) {
            EsTopicStageDefinition definition = new EsTopicStageDefinition();
            definition.setEsTopicSpaceId(selectedTopicSpaceId);
            renderEditForm(response, contextPath, definition, null, true);
            return;
        }

        if (definitionIdRaw != null) {
            Long definitionId = parseId(definitionIdRaw);
            if (definitionId == null) {
                renderList(response, contextPath, "Invalid stage identifier.", null, selectedTopicSpaceId);
                return;
            }

            EsTopicStageDefinition definition = stageDefinitionDao.findById(definitionId).orElse(null);
            if (definition == null) {
                renderList(response, contextPath, "Stage was not found.", null, selectedTopicSpaceId);
                return;
            }

            if ("edit".equalsIgnoreCase(mode)) {
                renderEditForm(response, contextPath, definition, null, false);
                return;
            }

            renderDetails(response, contextPath, definition);
            return;
        }

        String message = request.getParameter("saved") != null ? "Stage saved." : null;
        if (request.getParameter("bulkSaved") != null) {
            message = "Stage block imported.";
        }
        renderList(response, contextPath, message, null, selectedTopicSpaceId);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String action = trimToNull(request.getParameter("action"));
        if ("bulkUpsert".equalsIgnoreCase(action)) {
            handleBulkUpsert(request, response, contextPath);
            return;
        }

        String definitionIdRaw = trimToNull(request.getParameter("esTopicStageDefinitionId"));
        boolean creating = definitionIdRaw == null;

        EsTopicStageDefinition definition;
        if (creating) {
            definition = new EsTopicStageDefinition();
        } else {
            Long definitionId = parseId(definitionIdRaw);
            if (definitionId == null) {
                renderList(response, contextPath, "Invalid stage identifier.", null, null);
                return;
            }
            definition = stageDefinitionDao.findById(definitionId).orElse(null);
            if (definition == null) {
                renderList(response, contextPath, "Stage was not found.", null, null);
                return;
            }
        }

        String stageCode = trimToNull(request.getParameter("stageCode"));
        String stageName = trimToNull(request.getParameter("stageName"));
        String stageDescription = trimToNull(request.getParameter("stageDescription"));
        Long topicSpaceId = parseId(trimToNull(request.getParameter("esTopicSpaceId")));
        String displayOrderRaw = trimToNull(request.getParameter("displayOrder"));
        boolean isActive = request.getParameter("isActive") != null;

        try {
            if (topicSpaceId == null) {
                throw new IllegalArgumentException("Topic Space is required.");
            }
            if (creating || !topicSpaceId.equals(definition.getEsTopicSpaceId())) {
                requireActiveTopicSpace(topicSpaceId, "Only active Topic Spaces may receive new stages.");
            }
            ensureUniqueStageNameInSpace(stageName, topicSpaceId, definition.getEsTopicStageDefinitionId());

            definition.setEsTopicSpaceId(topicSpaceId);
            definition.setStageCode(required(stageCode, "Stage code"));
            definition.setStageName(required(stageName, "Stage name"));
            definition.setStageDescription(stageDescription);
            definition.setDisplayOrder(parseRequiredInt(displayOrderRaw, "Display order"));
            definition.setIsActive(isActive);

            stageDefinitionDao.saveOrUpdate(definition);
            response.sendRedirect(contextPath + "/admin/es/stages?saved=1&esTopicSpaceId=" + topicSpaceId);
        } catch (Exception ex) {
            definition.setStageCode(stageCode);
            definition.setStageName(stageName);
            definition.setStageDescription(stageDescription);
            definition.setEsTopicSpaceId(topicSpaceId);
            definition.setDisplayOrder(parseIntOrNull(displayOrderRaw));
            definition.setIsActive(isActive);
            renderEditForm(response, contextPath, definition, ex.getMessage(), creating);
        }
    }

    private void handleBulkUpsert(HttpServletRequest request, HttpServletResponse response, String contextPath)
            throws IOException {
        String bulkStages = request.getParameter("bulkStages");
        Long topicSpaceId = parseId(trimToNull(request.getParameter("esTopicSpaceId")));
        String normalizedBlock = trimToNull(bulkStages);
        if (normalizedBlock == null) {
            renderList(response, contextPath, "Paste at least one stage line to import.", bulkStages,
                    topicSpaceId);
            return;
        }
        if (topicSpaceId == null) {
            renderList(response, contextPath, "Topic Space is required for bulk import.", bulkStages, null);
            return;
        }
        try {
            requireActiveTopicSpace(topicSpaceId, "Only active Topic Spaces may receive new stages.");
        } catch (IllegalArgumentException ex) {
            renderList(response, contextPath, ex.getMessage(), bulkStages, topicSpaceId);
            return;
        }

        List<EsTopicStageDefinition> existingDefinitions = stageDefinitionDao.findAllOrderedBySpaceId(topicSpaceId);
        Set<String> usedCodes = new HashSet<>();
        int nextDisplayOrder = 0;
        for (EsTopicStageDefinition existing : existingDefinitions) {
            String code = trimToNull(existing.getStageCode());
            if (code != null) {
                usedCodes.add(code.toLowerCase(Locale.ROOT));
            }
            Integer displayOrder = existing.getDisplayOrder();
            if (displayOrder != null && displayOrder >= nextDisplayOrder) {
                nextDisplayOrder = displayOrder + 1;
            }
        }

        String[] lines = normalizedBlock.split("\\r?\\n");
        int importedCount = 0;
        for (String line : lines) {
            String normalizedLine = trimToNull(line);
            if (normalizedLine == null) {
                continue;
            }

            int colonIndex = normalizedLine.indexOf(':');
            if (colonIndex <= 0) {
                renderList(response, contextPath,
                        "Each line must use 'Stage: Description' format. Problem line: " + normalizedLine,
                        bulkStages, topicSpaceId);
                return;
            }

            String stageName = trimToNull(normalizedLine.substring(0, colonIndex));
            String stageDescription = trimToNull(normalizedLine.substring(colonIndex + 1));
            if (stageName == null) {
                renderList(response, contextPath, "Stage name is required on every line.", bulkStages,
                        topicSpaceId);
                return;
            }

            EsTopicStageDefinition definition = stageDefinitionDao.findByNameInSpace(stageName, topicSpaceId)
                    .orElse(null);
            boolean creating = definition == null;
            if (creating) {
                definition = new EsTopicStageDefinition();
                definition.setStageName(stageName);
                definition.setStageCode(generateUniqueCode(stageName, usedCodes));
                definition.setEsTopicSpaceId(topicSpaceId);
                definition.setDisplayOrder(nextDisplayOrder++);
            }

            definition.setStageName(stageName);
            definition.setStageDescription(stageDescription);
            definition.setIsActive(Boolean.TRUE);
            if (definition.getStageCode() == null || definition.getStageCode().isBlank()) {
                definition.setStageCode(generateUniqueCode(stageName, usedCodes));
            }
            if (definition.getDisplayOrder() == null) {
                definition.setDisplayOrder(nextDisplayOrder++);
            }

            stageDefinitionDao.saveOrUpdate(definition);
            importedCount++;
        }

        response.sendRedirect(contextPath + "/admin/es/stages?bulkSaved=1&count=" + importedCount
                + "&esTopicSpaceId=" + topicSpaceId);
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

    private void renderList(HttpServletResponse response, String contextPath, String message, String bulkStages,
            Long selectedTopicSpaceId)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        List<EsTopicSpace> allSpaces = topicSpaceDao.findAllOrdered();
        Map<Long, EsTopicSpace> spacesById = allSpaces.stream()
                .collect(java.util.stream.Collectors.toMap(
                        EsTopicSpace::getEsTopicSpaceId,
                        s -> s,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new));
        List<EsTopicStageDefinition> definitions = selectedTopicSpaceId == null
                ? List.of()
                : stageDefinitionDao.findAllOrderedBySpaceId(selectedTopicSpaceId);
        Map<Long, Long> usageCounts = stageDefinitionDao.findTopicUsageCountsByDefinitionId();
        EsTopicSpace selectedTopicSpace = selectedTopicSpaceId == null
                ? null
                : spacesById.get(selectedTopicSpaceId);
        boolean topicSpaceSelected = selectedTopicSpace != null;

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Stages Admin - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Stages</h2>");
                panelOut.println("        <p>Manage Stage options scoped to each Topic Space.</p>");
                if (message != null && !message.isBlank()) {
                    panelOut.println("        <p><strong>" + escapeHtml(message) + "</strong></p>");
                }

                panelOut.println("        <section class=\"panel\">");
                panelOut.println("          <h3>Topic Space</h3>");
                panelOut.println("          <form class=\"login-form\" action=\"" + contextPath
                        + "/admin/es/stages\" method=\"get\">");
                panelOut.println("            <label for=\"spaceFilterId\">Topic Space (required)</label>");
                panelOut.println(
                        "            <select id=\"spaceFilterId\" name=\"esTopicSpaceId\" required onchange=\"this.form.submit()\">");
                panelOut.println("              <option value=\"\">— Select —</option>");
                for (EsTopicSpace space : allSpaces) {
                    if (space.getEsTopicSpaceId() == null || trimToNull(space.getSpaceCode()) == null) {
                        continue;
                    }
                    boolean isCurrent = space.getEsTopicSpaceId().equals(selectedTopicSpaceId);
                    boolean isActive = Boolean.TRUE.equals(space.getIsActive());
                    String flags = isCurrent ? " selected" : "";
                    if (!isActive && !isCurrent) {
                        flags += " disabled";
                    }
                    panelOut.println("              <option value=\"" + space.getEsTopicSpaceId() + "\"" + flags
                            + ">" + escapeHtml(orEmpty(space.getSpaceName())) + (isActive ? "" : " (inactive)")
                            + "</option>");
                }
                panelOut.println("            </select>");
                panelOut.println("            <noscript><button type=\"submit\">Load Stages</button></noscript>");
                panelOut.println("          </form>");
                panelOut.println("        </section>");

                if (!topicSpaceSelected) {
                    panelOut.println("        <p>Select a Topic Space to view, add, or bulk import stages.</p>");
                    panelOut.println(
                            "        <p><a href=\"" + contextPath + "/admin/es\">Back to Emerging Standards</a></p>");
                    panelOut.println("      </section>");
                    return;
                }

                panelOut.println("        <h3>Current Stages for "
                        + escapeHtml(orEmpty(selectedTopicSpace.getSpaceName())) + "</h3>");

                panelOut.println("        <table class=\"data-table\">");
                panelOut.println("          <thead>");
                panelOut.println("            <tr>");
                panelOut.println("              <th>Name</th>");
                panelOut.println("              <th>Code</th>");
                panelOut.println("              <th>Topic Space</th>");
                panelOut.println("              <th>Display Order</th>");
                panelOut.println("              <th>Active</th>");
                panelOut.println("              <th>Topics</th>");
                panelOut.println("            </tr>");
                panelOut.println("          </thead>");
                panelOut.println("          <tbody>");
                for (EsTopicStageDefinition definition : definitions) {
                    panelOut.println("            <tr>");
                    panelOut.println("              <td><a href=\"" + contextPath
                            + "/admin/es/stages?esTopicStageDefinitionId="
                            + definition.getEsTopicStageDefinitionId()
                            + "&esTopicSpaceId=" + selectedTopicSpaceId
                            + "\">" + escapeHtml(orEmpty(definition.getStageName())) + "</a></td>");
                    panelOut.println("              <td>" + escapeHtml(orEmpty(definition.getStageCode())) + "</td>");
                    EsTopicSpace topicSpace = spacesById.get(definition.getEsTopicSpaceId());
                    panelOut.println("              <td>"
                            + escapeHtml(topicSpace == null ? "" : orEmpty(topicSpace.getSpaceName()))
                            + "</td>");
                    panelOut.println("              <td>"
                            + escapeHtml(String.valueOf(definition.getDisplayOrder() == null
                                    ? 0
                                    : definition.getDisplayOrder()))
                            + "</td>");
                    panelOut.println("              <td>"
                            + (Boolean.TRUE.equals(definition.getIsActive()) ? "Yes" : "No")
                            + "</td>");
                    Long usageCount = usageCounts.getOrDefault(definition.getEsTopicStageDefinitionId(), 0L);
                    panelOut.println("              <td>" + escapeHtml(String.valueOf(usageCount)) + "</td>");
                    panelOut.println("            </tr>");
                }
                if (definitions.isEmpty()) {
                    panelOut.println("            <tr>");
                    panelOut.println("              <td colspan=\"6\">No stages found for this Topic Space.</td>");
                    panelOut.println("            </tr>");
                }
                panelOut.println("          </tbody>");
                panelOut.println("        </table>");

                panelOut.println("        <p><a href=\"" + contextPath
                        + "/admin/es/stages?mode=new&esTopicSpaceId=" + selectedTopicSpaceId
                        + "\">Add Stage</a></p>");

                panelOut.println("        <section class=\"panel\">");
                panelOut.println("          <h3>Bulk Load Stage Descriptions</h3>");
                panelOut.println(
                        "          <p>Paste one stage per line using <strong>Stage: Description</strong>.</p>");
                panelOut.println("          <form class=\"login-form\" action=\"" + contextPath
                        + "/admin/es/stages\" method=\"post\">");
                panelOut.println("            <input type=\"hidden\" name=\"action\" value=\"bulkUpsert\" />");
                panelOut.println("            <input type=\"hidden\" name=\"esTopicSpaceId\" value=\""
                        + selectedTopicSpaceId + "\" />");
                panelOut.println("            <p><strong>Topic Space:</strong> "
                        + escapeHtml(orEmpty(selectedTopicSpace.getSpaceName())) + "</p>");
                panelOut.println("            <label for=\"bulkStages\">Stage block</label>");
                panelOut.println("            <textarea id=\"bulkStages\" name=\"bulkStages\" rows=\"8\""
                        + " placeholder=\"Draft: Description of draft stage\">"
                        + escapeHtml(orEmpty(bulkStages)) + "</textarea>");
                panelOut.println("            <button type=\"submit\">Import Stage Block</button>");
                panelOut.println("          </form>");
                panelOut.println("        </section>");
                panelOut.println(
                        "        <p><a href=\"" + contextPath + "/admin/es\">Back to Emerging Standards</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderDetails(HttpServletResponse response, String contextPath, EsTopicStageDefinition definition)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        long usageCount = stageDefinitionDao.findTopicUsageCountsByDefinitionId()
                .getOrDefault(definition.getEsTopicStageDefinitionId(), 0L);
        EsTopicSpace topicSpace = definition.getEsTopicSpaceId() == null
                ? null
                : topicSpaceDao.findById(definition.getEsTopicSpaceId()).orElse(null);

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Stage Details - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Stage Details</h2>");
                panelOut.println("        <section class=\"panel\">");
                panelOut.println("          <p><strong>Name:</strong> "
                        + escapeHtml(orEmpty(definition.getStageName())) + "</p>");
                panelOut.println("          <p><strong>Code:</strong> "
                        + escapeHtml(orEmpty(definition.getStageCode())) + "</p>");
                panelOut.println("          <p><strong>Topic Space:</strong> "
                        + escapeHtml(topicSpace == null ? "" : orEmpty(topicSpace.getSpaceName())) + "</p>");
                panelOut.println("          <p><strong>Description:</strong> "
                        + escapeHtml(orEmpty(definition.getStageDescription())) + "</p>");
                panelOut.println("          <p><strong>Display Order:</strong> "
                        + escapeHtml(String.valueOf(definition.getDisplayOrder() == null
                                ? 0
                                : definition.getDisplayOrder()))
                        + "</p>");
                panelOut.println("          <p><strong>Active:</strong> "
                        + (Boolean.TRUE.equals(definition.getIsActive()) ? "Yes" : "No") + "</p>");
                panelOut.println("          <p><strong>Topic Usage:</strong> "
                        + escapeHtml(String.valueOf(usageCount)) + "</p>");
                panelOut.println("        </section>");
                panelOut.println("        <p><a href=\"" + contextPath + "/admin/es/stages?esTopicStageDefinitionId="
                        + definition.getEsTopicStageDefinitionId() + "&mode=edit&esTopicSpaceId="
                        + definition.getEsTopicSpaceId() + "\">Edit Stage</a></p>");
                panelOut.println("        <p><a href=\"" + contextPath
                        + "/admin/es/stages?esTopicSpaceId=" + definition.getEsTopicSpaceId()
                        + "\">Back to Stages</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderEditForm(HttpServletResponse response, String contextPath, EsTopicStageDefinition definition,
            String errorMessage, boolean creating) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        List<EsTopicSpace> allSpaces = topicSpaceDao.findAllOrdered();
        Long selectedSpaceId = definition.getEsTopicSpaceId();

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, (creating ? "Create" : "Edit") + " Stage - InteropHub", contextPath,
                    panelOut -> {
                        panelOut.println("      <section class=\"panel\">");
                        panelOut.println("        <h2>" + (creating ? "Create" : "Edit") + " Stage</h2>");

                        if (errorMessage != null && !errorMessage.isBlank()) {
                            panelOut.println("        <p><strong>Could not save:</strong> " + escapeHtml(errorMessage)
                                    + "</p>");
                        }

                        panelOut.println("        <form class=\"login-form\" action=\"" + contextPath
                                + "/admin/es/stages\" method=\"post\">");
                        if (!creating && definition.getEsTopicStageDefinitionId() != null) {
                            panelOut.println(
                                    "      <input type=\"hidden\" name=\"esTopicStageDefinitionId\" value=\""
                                            + definition.getEsTopicStageDefinitionId() + "\" />");
                        }

                        panelOut.println("      <label for=\"stageCode\">Stage Code (required)</label>");
                        panelOut.println(
                                "      <input id=\"stageCode\" name=\"stageCode\" type=\"text\" required value=\""
                                        + escapeHtml(orEmpty(definition.getStageCode())) + "\" />");

                        panelOut.println("      <label for=\"stageName\">Stage Name (required)</label>");
                        panelOut.println(
                                "      <input id=\"stageName\" name=\"stageName\" type=\"text\" required value=\""
                                        + escapeHtml(orEmpty(definition.getStageName())) + "\" />");

                        panelOut.println("      <label for=\"esTopicSpaceId\">Topic Space (required)</label>");
                        panelOut.println("      <select id=\"esTopicSpaceId\" name=\"esTopicSpaceId\" required>");
                        panelOut.println("        <option value=\"\">— Select —</option>");
                        for (EsTopicSpace topicSpace : allSpaces) {
                            if (topicSpace.getEsTopicSpaceId() == null
                                    || trimToNull(topicSpace.getSpaceCode()) == null) {
                                continue;
                            }
                            boolean isCurrent = topicSpace.getEsTopicSpaceId().equals(selectedSpaceId);
                            boolean isActive = Boolean.TRUE.equals(topicSpace.getIsActive());
                            String flags = isCurrent ? " selected" : "";
                            if (!isActive && !isCurrent) {
                                flags += " disabled";
                            }
                            panelOut.println(
                                    "        <option value=\"" + topicSpace.getEsTopicSpaceId() + "\"" + flags + ">"
                                            + escapeHtml(orEmpty(topicSpace.getSpaceName()))
                                            + (isActive ? "" : " (inactive)")
                                            + "</option>");
                        }
                        panelOut.println("      </select>");

                        panelOut.println("      <label for=\"stageDescription\">Description</label>");
                        panelOut.println("      <textarea id=\"stageDescription\" name=\"stageDescription\" rows=\"5\">"
                                + escapeHtml(orEmpty(definition.getStageDescription())) + "</textarea>");

                        panelOut.println("      <label for=\"displayOrder\">Display Order (required)</label>");
                        panelOut.println(
                                "      <input id=\"displayOrder\" name=\"displayOrder\" type=\"number\" required value=\""
                                        + escapeHtml(String.valueOf(
                                                definition.getDisplayOrder() == null ? 0
                                                        : definition.getDisplayOrder()))
                                        + "\" />");

                        panelOut.println("      <label><input type=\"checkbox\" name=\"isActive\""
                                + (Boolean.TRUE.equals(definition.getIsActive()) || creating ? " checked" : "")
                                + " /> Active</label>");

                        panelOut.println("      <button type=\"submit\">Save</button>");
                        panelOut.println("    </form>");
                        panelOut.println("    <p><a href=\"" + contextPath
                                + "/admin/es/stages\">Back to Stages</a></p>");
                        panelOut.println("      </section>");
                    });
        }
    }

    private void renderForbidden(HttpServletResponse response, String contextPath) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Access Denied - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Access Denied</h2>");
                panelOut.println("        <p>You must be an InteropHub admin to access stage settings.</p>");
                panelOut.println("        <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer parseRequiredInt(String value, String label) {
        String normalized = required(value, label);
        try {
            return Integer.valueOf(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be a valid number.");
        }
    }

    private Integer parseIntOrNull(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private EsTopicSpace requireActiveTopicSpace(Long topicSpaceId, String errorMessage) {
        EsTopicSpace topicSpace = topicSpaceDao.findById(topicSpaceId)
                .orElseThrow(() -> new IllegalArgumentException("Topic Space is invalid."));
        if (!Boolean.TRUE.equals(topicSpace.getIsActive())) {
            throw new IllegalArgumentException(errorMessage);
        }
        return topicSpace;
    }

    private void ensureUniqueStageNameInSpace(String stageName, Long topicSpaceId, Long excludeId) {
        String normalizedName = trimToNull(stageName);
        if (normalizedName == null || topicSpaceId == null) {
            return;
        }
        boolean duplicate = stageDefinitionDao
                .findByNameInSpaceExcludingId(normalizedName, topicSpaceId, excludeId)
                .isPresent();
        if (duplicate) {
            throw new IllegalArgumentException(
                    "Stage name must be unique within the selected Topic Space.");
        }
    }

    private String generateUniqueCode(String label, Set<String> usedCodes) {
        String base = label == null
                ? "value"
                : label.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("(^-+|-+$)", "");
        if (base.isBlank()) {
            base = "value";
        }
        if (base.length() > 70) {
            base = base.substring(0, 70);
        }

        String candidate = base;
        int suffix = 2;
        while (usedCodes.contains(candidate.toLowerCase(Locale.ROOT))) {
            candidate = base;
            String suffixText = "-" + suffix;
            if (candidate.length() + suffixText.length() > 80) {
                candidate = candidate.substring(0, 80 - suffixText.length());
            }
            candidate = candidate + suffixText;
            suffix++;
        }
        usedCodes.add(candidate.toLowerCase(Locale.ROOT));
        return candidate;
    }

    private String required(String value, String label) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return normalized;
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
