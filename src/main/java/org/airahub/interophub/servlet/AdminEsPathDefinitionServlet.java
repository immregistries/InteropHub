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
import org.airahub.interophub.dao.EsTopicPathDefinitionDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.EsTopicPathDefinition;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

public class AdminEsPathDefinitionServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsTopicPathDefinitionDao pathDefinitionDao;
    private final EsTopicSpaceDao topicSpaceDao;

    public AdminEsPathDefinitionServlet() {
        this.authFlowService = new AuthFlowService();
        this.pathDefinitionDao = new EsTopicPathDefinitionDao();
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
        String definitionIdRaw = trimToNull(request.getParameter("esTopicPathDefinitionId"));
        Long selectedTopicSpaceId = parseId(trimToNull(request.getParameter("esTopicSpaceId")));

        if ("new".equalsIgnoreCase(mode)) {
            EsTopicPathDefinition definition = new EsTopicPathDefinition();
            definition.setEsTopicSpaceId(selectedTopicSpaceId);
            renderEditForm(response, contextPath, definition, null, true);
            return;
        }

        if (definitionIdRaw != null) {
            Long definitionId = parseId(definitionIdRaw);
            if (definitionId == null) {
                renderList(response, contextPath, "Invalid advancement path identifier.", null, selectedTopicSpaceId);
                return;
            }

            EsTopicPathDefinition definition = pathDefinitionDao.findById(definitionId).orElse(null);
            if (definition == null) {
                renderList(response, contextPath, "Advancement Path was not found.", null, selectedTopicSpaceId);
                return;
            }

            if ("edit".equalsIgnoreCase(mode)) {
                renderEditForm(response, contextPath, definition, null, false);
                return;
            }

            renderDetails(response, contextPath, definition);
            return;
        }

        String message = request.getParameter("saved") != null ? "Advancement Path saved." : null;
        if (request.getParameter("bulkSaved") != null) {
            message = "Advancement Path block imported.";
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

        String definitionIdRaw = trimToNull(request.getParameter("esTopicPathDefinitionId"));
        boolean creating = definitionIdRaw == null;

        EsTopicPathDefinition definition;
        if (creating) {
            definition = new EsTopicPathDefinition();
        } else {
            Long definitionId = parseId(definitionIdRaw);
            if (definitionId == null) {
                renderList(response, contextPath, "Invalid advancement path identifier.", null, null);
                return;
            }
            definition = pathDefinitionDao.findById(definitionId).orElse(null);
            if (definition == null) {
                renderList(response, contextPath, "Advancement Path was not found.", null, null);
                return;
            }
        }

        String pathCode = trimToNull(request.getParameter("pathCode"));
        String pathName = trimToNull(request.getParameter("pathName"));
        String pathDescription = trimToNull(request.getParameter("pathDescription"));
        Long topicSpaceId = parseId(trimToNull(request.getParameter("esTopicSpaceId")));
        String displayOrderRaw = trimToNull(request.getParameter("displayOrder"));
        boolean isActive = request.getParameter("isActive") != null;

        try {
            if (topicSpaceId == null) {
                throw new IllegalArgumentException("Topic Space is required.");
            }
            if (creating || !topicSpaceId.equals(definition.getEsTopicSpaceId())) {
                requireActiveTopicSpace(topicSpaceId, "Only active Topic Spaces may receive new Advancement Paths.");
            }
            ensureUniquePathNameInSpace(pathName, topicSpaceId, definition.getEsTopicPathDefinitionId());

            definition.setEsTopicSpaceId(topicSpaceId);
            definition.setPathCode(required(pathCode, "Advancement Path code"));
            definition.setPathName(required(pathName, "Advancement Path name"));
            definition.setPathDescription(pathDescription);
            definition.setDisplayOrder(parseRequiredInt(displayOrderRaw, "Display order"));
            definition.setIsActive(isActive);

            pathDefinitionDao.saveOrUpdate(definition);
            response.sendRedirect(contextPath + "/admin/es/paths?saved=1&esTopicSpaceId=" + topicSpaceId);
        } catch (Exception ex) {
            definition.setPathCode(pathCode);
            definition.setPathName(pathName);
            definition.setPathDescription(pathDescription);
            definition.setEsTopicSpaceId(topicSpaceId);
            definition.setDisplayOrder(parseIntOrNull(displayOrderRaw));
            definition.setIsActive(isActive);
            renderEditForm(response, contextPath, definition, ex.getMessage(), creating);
        }
    }

    private void handleBulkUpsert(HttpServletRequest request, HttpServletResponse response, String contextPath)
            throws IOException {
        String bulkPaths = request.getParameter("bulkPaths");
        Long topicSpaceId = parseId(trimToNull(request.getParameter("esTopicSpaceId")));
        String normalizedBlock = trimToNull(bulkPaths);
        if (normalizedBlock == null) {
            renderList(response, contextPath, "Paste at least one Advancement Path line to import.", bulkPaths,
                    topicSpaceId);
            return;
        }
        if (topicSpaceId == null) {
            renderList(response, contextPath, "Topic Space is required for bulk import.", bulkPaths, null);
            return;
        }
        try {
            requireActiveTopicSpace(topicSpaceId, "Only active Topic Spaces may receive new Advancement Paths.");
        } catch (IllegalArgumentException ex) {
            renderList(response, contextPath, ex.getMessage(), bulkPaths, topicSpaceId);
            return;
        }

        List<EsTopicPathDefinition> existingDefinitions = pathDefinitionDao.findAllOrderedBySpaceId(topicSpaceId);
        Set<String> usedCodes = new HashSet<>();
        int nextDisplayOrder = 0;
        for (EsTopicPathDefinition existing : existingDefinitions) {
            String code = trimToNull(existing.getPathCode());
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
                        "Each line must use 'Advancement Path: Description' format. Problem line: " + normalizedLine,
                        bulkPaths, topicSpaceId);
                return;
            }

            String pathName = trimToNull(normalizedLine.substring(0, colonIndex));
            String pathDescription = trimToNull(normalizedLine.substring(colonIndex + 1));
            if (pathName == null) {
                renderList(response, contextPath, "Advancement Path name is required on every line.", bulkPaths,
                        topicSpaceId);
                return;
            }

            EsTopicPathDefinition definition = pathDefinitionDao.findByNameInSpace(pathName, topicSpaceId)
                    .orElse(null);
            boolean creating = definition == null;
            if (creating) {
                definition = new EsTopicPathDefinition();
                definition.setPathName(pathName);
                definition.setPathCode(generateUniqueCode(pathName, usedCodes));
                definition.setEsTopicSpaceId(topicSpaceId);
                definition.setDisplayOrder(nextDisplayOrder++);
            }

            definition.setPathName(pathName);
            definition.setPathDescription(pathDescription);
            definition.setIsActive(Boolean.TRUE);
            if (definition.getPathCode() == null || definition.getPathCode().isBlank()) {
                definition.setPathCode(generateUniqueCode(pathName, usedCodes));
            }
            if (definition.getDisplayOrder() == null) {
                definition.setDisplayOrder(nextDisplayOrder++);
            }

            pathDefinitionDao.saveOrUpdate(definition);
            importedCount++;
        }

        response.sendRedirect(contextPath + "/admin/es/paths?bulkSaved=1&count=" + importedCount
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

    private void renderList(HttpServletResponse response, String contextPath, String message, String bulkPaths,
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
        List<EsTopicPathDefinition> definitions = selectedTopicSpaceId == null
                ? List.of()
                : pathDefinitionDao.findAllOrderedBySpaceId(selectedTopicSpaceId);
        Map<Long, Long> usageCounts = pathDefinitionDao.findTopicUsageCountsByDefinitionId();
        EsTopicSpace selectedTopicSpace = selectedTopicSpaceId == null
                ? null
                : spacesById.get(selectedTopicSpaceId);
        boolean topicSpaceSelected = selectedTopicSpace != null;

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Advancement Paths Admin - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Advancement Paths</h2>");
                panelOut.println("        <p>Manage Advancement Path options scoped to each Topic Space.</p>");
                if (message != null && !message.isBlank()) {
                    panelOut.println("        <p><strong>" + escapeHtml(message) + "</strong></p>");
                }

                panelOut.println("        <section class=\"panel\">");
                panelOut.println("          <h3>Topic Space</h3>");
                panelOut.println("          <form class=\"login-form\" action=\"" + contextPath
                        + "/admin/es/paths\" method=\"get\">");
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
                panelOut.println(
                        "            <noscript><button type=\"submit\">Load Advancement Paths</button></noscript>");
                panelOut.println("          </form>");
                panelOut.println("        </section>");

                if (!topicSpaceSelected) {
                    panelOut.println(
                            "        <p>Select a Topic Space to view, add, or bulk import Advancement Paths.</p>");
                    panelOut.println(
                            "        <p><a href=\"" + contextPath + "/admin/es\">Back to Emerging Standards</a></p>");
                    panelOut.println("      </section>");
                    return;
                }

                panelOut.println("        <h3>Current Advancement Paths for "
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
                for (EsTopicPathDefinition definition : definitions) {
                    panelOut.println("            <tr>");
                    panelOut.println("              <td><a href=\"" + contextPath
                            + "/admin/es/paths?esTopicPathDefinitionId="
                            + definition.getEsTopicPathDefinitionId()
                            + "&esTopicSpaceId=" + selectedTopicSpaceId
                            + "\">" + escapeHtml(orEmpty(definition.getPathName())) + "</a></td>");
                    panelOut.println("              <td>" + escapeHtml(orEmpty(definition.getPathCode())) + "</td>");
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
                    Long usageCount = usageCounts.getOrDefault(definition.getEsTopicPathDefinitionId(), 0L);
                    panelOut.println("              <td>" + escapeHtml(String.valueOf(usageCount)) + "</td>");
                    panelOut.println("            </tr>");
                }
                if (definitions.isEmpty()) {
                    panelOut.println("            <tr>");
                    panelOut.println(
                            "              <td colspan=\"6\">No Advancement Paths found for this Topic Space.</td>");
                    panelOut.println("            </tr>");
                }
                panelOut.println("          </tbody>");
                panelOut.println("        </table>");

                panelOut.println("        <p><a href=\"" + contextPath
                        + "/admin/es/paths?mode=new&esTopicSpaceId=" + selectedTopicSpaceId
                        + "\">Add Advancement Path</a></p>");

                panelOut.println("        <section class=\"panel\">");
                panelOut.println("          <h3>Bulk Load Advancement Path Descriptions</h3>");
                panelOut.println(
                        "          <p>Paste one path per line using <strong>Advancement Path: Description</strong>.</p>");
                panelOut.println("          <form class=\"login-form\" action=\"" + contextPath
                        + "/admin/es/paths\" method=\"post\">");
                panelOut.println("            <input type=\"hidden\" name=\"action\" value=\"bulkUpsert\" />");
                panelOut.println("            <input type=\"hidden\" name=\"esTopicSpaceId\" value=\""
                        + selectedTopicSpaceId + "\" />");
                panelOut.println("            <p><strong>Topic Space:</strong> "
                        + escapeHtml(orEmpty(selectedTopicSpace.getSpaceName())) + "</p>");
                panelOut.println("            <label for=\"bulkPaths\">Advancement Path block</label>");
                panelOut.println("            <textarea id=\"bulkPaths\" name=\"bulkPaths\" rows=\"8\""
                        + " placeholder=\"Draft: Description of draft path\">"
                        + escapeHtml(orEmpty(bulkPaths)) + "</textarea>");
                panelOut.println("            <button type=\"submit\">Import Advancement Path Block</button>");
                panelOut.println("          </form>");
                panelOut.println("        </section>");
                panelOut.println(
                        "        <p><a href=\"" + contextPath + "/admin/es\">Back to Emerging Standards</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderDetails(HttpServletResponse response, String contextPath, EsTopicPathDefinition definition)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        long usageCount = pathDefinitionDao.findTopicUsageCountsByDefinitionId()
                .getOrDefault(definition.getEsTopicPathDefinitionId(), 0L);
        EsTopicSpace topicSpace = definition.getEsTopicSpaceId() == null
                ? null
                : topicSpaceDao.findById(definition.getEsTopicSpaceId()).orElse(null);

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Advancement Path Details - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Advancement Path Details</h2>");
                panelOut.println("        <section class=\"panel\">");
                panelOut.println("          <p><strong>Name:</strong> "
                        + escapeHtml(orEmpty(definition.getPathName())) + "</p>");
                panelOut.println("          <p><strong>Code:</strong> "
                        + escapeHtml(orEmpty(definition.getPathCode())) + "</p>");
                panelOut.println("          <p><strong>Topic Space:</strong> "
                        + escapeHtml(topicSpace == null ? "" : orEmpty(topicSpace.getSpaceName())) + "</p>");
                panelOut.println("          <p><strong>Description:</strong> "
                        + escapeHtml(orEmpty(definition.getPathDescription())) + "</p>");
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
                panelOut.println("        <p><a href=\"" + contextPath + "/admin/es/paths?esTopicPathDefinitionId="
                        + definition.getEsTopicPathDefinitionId() + "&mode=edit&esTopicSpaceId="
                        + definition.getEsTopicSpaceId() + "\">Edit Advancement Path</a></p>");
                panelOut.println("        <p><a href=\"" + contextPath
                        + "/admin/es/paths?esTopicSpaceId=" + definition.getEsTopicSpaceId()
                        + "\">Back to Advancement Paths</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderEditForm(HttpServletResponse response, String contextPath, EsTopicPathDefinition definition,
            String errorMessage, boolean creating) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        List<EsTopicSpace> allSpaces = topicSpaceDao.findAllOrdered();
        Long selectedSpaceId = definition.getEsTopicSpaceId();

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, (creating ? "Create" : "Edit") + " Advancement Path - InteropHub",
                    contextPath,
                    panelOut -> {
                        panelOut.println("      <section class=\"panel\">");
                        panelOut.println("        <h2>" + (creating ? "Create" : "Edit") + " Advancement Path</h2>");

                        if (errorMessage != null && !errorMessage.isBlank()) {
                            panelOut.println("        <p><strong>Could not save:</strong> " + escapeHtml(errorMessage)
                                    + "</p>");
                        }

                        panelOut.println("        <form class=\"login-form\" action=\"" + contextPath
                                + "/admin/es/paths\" method=\"post\">");
                        if (!creating && definition.getEsTopicPathDefinitionId() != null) {
                            panelOut.println(
                                    "      <input type=\"hidden\" name=\"esTopicPathDefinitionId\" value=\""
                                            + definition.getEsTopicPathDefinitionId() + "\" />");
                        }

                        panelOut.println("      <label for=\"pathCode\">Advancement Path Code (required)</label>");
                        panelOut.println(
                                "      <input id=\"pathCode\" name=\"pathCode\" type=\"text\" required value=\""
                                        + escapeHtml(orEmpty(definition.getPathCode())) + "\" />");

                        panelOut.println("      <label for=\"pathName\">Advancement Path Name (required)</label>");
                        panelOut.println(
                                "      <input id=\"pathName\" name=\"pathName\" type=\"text\" required value=\""
                                        + escapeHtml(orEmpty(definition.getPathName())) + "\" />");

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

                        panelOut.println("      <label for=\"pathDescription\">Description</label>");
                        panelOut.println("      <textarea id=\"pathDescription\" name=\"pathDescription\" rows=\"5\">"
                                + escapeHtml(orEmpty(definition.getPathDescription())) + "</textarea>");

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
                                + "/admin/es/paths\">Back to Advancement Paths</a></p>");
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
                panelOut.println("        <p>You must be an InteropHub admin to access Advancement Path settings.</p>");
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

    private void ensureUniquePathNameInSpace(String pathName, Long topicSpaceId, Long excludeId) {
        String normalizedName = trimToNull(pathName);
        if (normalizedName == null || topicSpaceId == null) {
            return;
        }
        boolean duplicate = pathDefinitionDao
                .findByNameInSpaceExcludingId(normalizedName, topicSpaceId, excludeId)
                .isPresent();
        if (duplicate) {
            throw new IllegalArgumentException(
                    "Advancement Path name must be unique within the selected Topic Space.");
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
