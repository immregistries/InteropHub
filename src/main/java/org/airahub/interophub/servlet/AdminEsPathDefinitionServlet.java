package org.airahub.interophub.servlet;

import java.io.IOException;
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

public class AdminEsPathDefinitionServlet extends HttpServlet {

    private static final String ACTIVE_HREF = "/admin/es/paths";

    private final EsTopicPathDefinitionDao pathDefinitionDao;
    private final EsTopicSpaceDao topicSpaceDao;

    public AdminEsPathDefinitionServlet() {
        this.pathDefinitionDao = new EsTopicPathDefinitionDao();
        this.topicSpaceDao = new EsTopicSpaceDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String mode = trimToNull(request.getParameter("mode"));
        String definitionIdRaw = trimToNull(request.getParameter("esTopicPathDefinitionId"));
        Long selectedTopicSpaceId = parseId(trimToNull(request.getParameter("esTopicSpaceId")));

        if ("new".equalsIgnoreCase(mode)) {
            EsTopicPathDefinition definition = new EsTopicPathDefinition();
            definition.setEsTopicSpaceId(selectedTopicSpaceId);
            renderEditForm(request, response, definition, null, true);
            return;
        }

        if (definitionIdRaw != null) {
            Long definitionId = parseId(definitionIdRaw);
            if (definitionId == null) {
                renderList(request, response, "Invalid advancement path identifier.", null, selectedTopicSpaceId);
                return;
            }

            EsTopicPathDefinition definition = pathDefinitionDao.findById(definitionId).orElse(null);
            if (definition == null) {
                renderList(request, response, "Advancement Path was not found.", null, selectedTopicSpaceId);
                return;
            }

            if ("edit".equalsIgnoreCase(mode)) {
                renderEditForm(request, response, definition, null, false);
                return;
            }

            renderDetails(request, response, definition);
            return;
        }

        String message = request.getParameter("saved") != null ? "Advancement Path saved." : null;
        if (request.getParameter("bulkSaved") != null) {
            message = "Advancement Path block imported.";
        }
        if (request.getParameter("conceptSaved") != null) {
            message = "Advancement Path meaning saved.";
        }
        renderList(request, response, message, null, selectedTopicSpaceId);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String action = trimToNull(request.getParameter("action"));
        if ("bulkUpsert".equalsIgnoreCase(action)) {
            handleBulkUpsert(request, response, contextPath);
            return;
        }
        if ("saveConceptDescription".equalsIgnoreCase(action)) {
            handleSaveConceptDescription(request, response, contextPath);
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
                renderList(request, response, "Invalid advancement path identifier.", null, null);
                return;
            }
            definition = pathDefinitionDao.findById(definitionId).orElse(null);
            if (definition == null) {
                renderList(request, response, "Advancement Path was not found.", null, null);
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
            renderEditForm(request, response, definition, ex.getMessage(), creating);
        }
    }

    private void handleBulkUpsert(HttpServletRequest request, HttpServletResponse response, String contextPath)
            throws IOException {
        String bulkPaths = request.getParameter("bulkPaths");
        Long topicSpaceId = parseId(trimToNull(request.getParameter("esTopicSpaceId")));
        String normalizedBlock = trimToNull(bulkPaths);
        if (normalizedBlock == null) {
            renderList(request, response, "Paste at least one Advancement Path line to import.", bulkPaths,
                    topicSpaceId);
            return;
        }
        if (topicSpaceId == null) {
            renderList(request, response, "Topic Space is required for bulk import.", bulkPaths, null);
            return;
        }
        try {
            requireActiveTopicSpace(topicSpaceId, "Only active Topic Spaces may receive new Advancement Paths.");
        } catch (IllegalArgumentException ex) {
            renderList(request, response, ex.getMessage(), bulkPaths, topicSpaceId);
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
                renderList(request, response,
                        "Each line must use 'Advancement Path: Description' format. Problem line: " + normalizedLine,
                        bulkPaths, topicSpaceId);
                return;
            }

            String pathName = trimToNull(normalizedLine.substring(0, colonIndex));
            String pathDescription = trimToNull(normalizedLine.substring(colonIndex + 1));
            if (pathName == null) {
                renderList(request, response, "Advancement Path name is required on every line.", bulkPaths,
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

    private void handleSaveConceptDescription(HttpServletRequest request, HttpServletResponse response,
            String contextPath) throws IOException {
        Long topicSpaceId = parseId(trimToNull(request.getParameter("esTopicSpaceId")));
        if (topicSpaceId == null) {
            renderList(request, response, "Topic Space is required.", null, null);
            return;
        }
        EsTopicSpace topicSpace = topicSpaceDao.findById(topicSpaceId).orElse(null);
        if (topicSpace == null) {
            renderList(request, response, "Topic Space was not found.", null, null);
            return;
        }

        topicSpace.setPathConceptDescription(trimToNull(request.getParameter("pathConceptDescription")));
        topicSpaceDao.saveOrUpdate(topicSpace);
        response.sendRedirect(
                contextPath + "/admin/es/paths?conceptSaved=1&esTopicSpaceId=" + topicSpaceId);
    }

    private void renderList(HttpServletRequest request, HttpServletResponse response, String message,
            String bulkPaths, Long selectedTopicSpaceId)
            throws IOException {
        String contextPath = request.getContextPath();
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

        AdminShellRenderer.render(request, response, "Advancement Paths Admin - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Advancement Paths</h2>");
                    out.println(
                            "            <p class=\"aira-meta\">Manage Advancement Path options scoped to each Topic Space.</p>");
                    if (message != null && !message.isBlank()) {
                        out.println("            <div class=\"aira-alert aira-alert--success\"><p>"
                                + escapeHtml(message) + "</p></div>");
                    }

                    out.println("            <section class=\"aira-panel\">");
                    out.println("              <h3 class=\"aira-subsection-title\">Topic Space</h3>");
                    out.println("              <form class=\"aira-form\" action=\"" + contextPath
                            + "/admin/es/paths\" method=\"get\">");
                    out.println("                <div class=\"aira-field\">");
                    out.println("                  <label for=\"spaceFilterId\">Topic Space (required)</label>");
                    out.println(
                            "                  <select class=\"aira-select\" id=\"spaceFilterId\" name=\"esTopicSpaceId\" required onchange=\"this.form.submit()\">");
                    out.println("                    <option value=\"\">— Select —</option>");
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
                        out.println("                    <option value=\"" + space.getEsTopicSpaceId() + "\"" + flags
                                + ">" + escapeHtml(orEmpty(space.getSpaceName())) + (isActive ? "" : " (inactive)")
                                + "</option>");
                    }
                    out.println("                  </select>");
                    out.println("                </div>");
                    out.println(
                            "                <noscript><button class=\"aira-button aira-button--secondary\" type=\"submit\">Load Advancement Paths</button></noscript>");
                    out.println("              </form>");
                    out.println("            </section>");

                    if (!topicSpaceSelected) {
                        out.println(
                                "            <p class=\"aira-meta\">Select a Topic Space to view, add, or bulk import Advancement Paths.</p>");
                        out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                                + "/admin/es\">Back to Emerging Standards</a></p>");
                        out.println("          </section>");
                        return;
                    }

                    out.println("            <section class=\"aira-panel\">");
                    out.println("              <h3 class=\"aira-subsection-title\">What \"Advancement Path\" Means for "
                            + escapeHtml(orEmpty(selectedTopicSpace.getSpaceName())) + "</h3>");
                    String pathConceptDescription = trimToNull(selectedTopicSpace.getPathConceptDescription());
                    out.println("              <p class=\"aira-meta\">" + (pathConceptDescription == null
                            ? "No custom meaning set. The standard description below is shown instead."
                            : "Custom meaning set for this Topic Space.") + "</p>");
                    out.println("              <form class=\"aira-form\" action=\"" + contextPath
                            + "/admin/es/paths\" method=\"post\">");
                    out.println("                <input type=\"hidden\" name=\"action\" value=\"saveConceptDescription\" />");
                    out.println("                <input type=\"hidden\" name=\"esTopicSpaceId\" value=\""
                            + selectedTopicSpaceId + "\" />");
                    out.println("                <div class=\"aira-field\">");
                    out.println(
                            "                  <label for=\"pathConceptDescription\">Advancement Path Meaning (leave blank to use the standard description)</label>");
                    out.println(
                            "                  <textarea class=\"aira-textarea\" id=\"pathConceptDescription\" name=\"pathConceptDescription\" rows=\"3\""
                                    + " placeholder=\"" + escapeHtml(EsTopicSpace.DEFAULT_PATH_CONCEPT_DESCRIPTION)
                                    + "\">" + escapeHtml(orEmpty(pathConceptDescription)) + "</textarea>");
                    out.println("                </div>");
                    out.println("                <div class=\"aira-action-group\">");
                    out.println(
                            "                  <button class=\"aira-button aira-button--primary\" type=\"submit\">Save Advancement Path Meaning</button>");
                    out.println("                </div>");
                    out.println("              </form>");
                    out.println("            </section>");

                    out.println("            <h3 class=\"aira-subsection-title\">Current Advancement Paths for "
                            + escapeHtml(orEmpty(selectedTopicSpace.getSpaceName())) + "</h3>");

                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println("              <thead>");
                    out.println("                <tr>");
                    out.println("                  <th>Name</th>");
                    out.println("                  <th>Code</th>");
                    out.println("                  <th>Topic Space</th>");
                    out.println("                  <th>Display Order</th>");
                    out.println("                  <th>Active</th>");
                    out.println("                  <th>Topics</th>");
                    out.println("                </tr>");
                    out.println("              </thead>");
                    out.println("              <tbody>");
                    for (EsTopicPathDefinition definition : definitions) {
                        out.println("                <tr>");
                        out.println("                  <td><a class=\"aira-inline-link\" href=\"" + contextPath
                                + "/admin/es/paths?esTopicPathDefinitionId="
                                + definition.getEsTopicPathDefinitionId()
                                + "&esTopicSpaceId=" + selectedTopicSpaceId
                                + "\">" + escapeHtml(orEmpty(definition.getPathName())) + "</a></td>");
                        out.println(
                                "                  <td>" + escapeHtml(orEmpty(definition.getPathCode())) + "</td>");
                        EsTopicSpace topicSpace = spacesById.get(definition.getEsTopicSpaceId());
                        out.println("                  <td>"
                                + escapeHtml(topicSpace == null ? "" : orEmpty(topicSpace.getSpaceName()))
                                + "</td>");
                        out.println("                  <td>"
                                + escapeHtml(String.valueOf(definition.getDisplayOrder() == null
                                        ? 0
                                        : definition.getDisplayOrder()))
                                + "</td>");
                        out.println("                  <td>"
                                + activeBadge(Boolean.TRUE.equals(definition.getIsActive())) + "</td>");
                        Long usageCount = usageCounts.getOrDefault(definition.getEsTopicPathDefinitionId(), 0L);
                        out.println("                  <td>" + escapeHtml(String.valueOf(usageCount)) + "</td>");
                        out.println("                </tr>");
                    }
                    if (definitions.isEmpty()) {
                        out.println("                <tr>");
                        out.println(
                                "                  <td colspan=\"6\">No Advancement Paths found for this Topic Space.</td>");
                        out.println("                </tr>");
                    }
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");

                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--primary\" href=\"" + contextPath
                            + "/admin/es/paths?mode=new&esTopicSpaceId=" + selectedTopicSpaceId
                            + "\">Add Advancement Path</a>");
                    out.println("            </div>");

                    out.println("            <section class=\"aira-panel\">");
                    out.println("              <h3 class=\"aira-subsection-title\">Bulk Load Advancement Path Descriptions</h3>");
                    out.println(
                            "              <p class=\"aira-meta\">Paste one path per line using <strong>Advancement Path: Description</strong>.</p>");
                    out.println("              <form class=\"aira-form\" action=\"" + contextPath
                            + "/admin/es/paths\" method=\"post\">");
                    out.println("                <input type=\"hidden\" name=\"action\" value=\"bulkUpsert\" />");
                    out.println("                <input type=\"hidden\" name=\"esTopicSpaceId\" value=\""
                            + selectedTopicSpaceId + "\" />");
                    out.println("                <p><strong>Topic Space:</strong> "
                            + escapeHtml(orEmpty(selectedTopicSpace.getSpaceName())) + "</p>");
                    out.println("                <div class=\"aira-field\">");
                    out.println("                  <label for=\"bulkPaths\">Advancement Path block</label>");
                    out.println("                  <textarea class=\"aira-textarea\" id=\"bulkPaths\" name=\"bulkPaths\" rows=\"8\""
                            + " placeholder=\"Draft: Description of draft path\">"
                            + escapeHtml(orEmpty(bulkPaths)) + "</textarea>");
                    out.println("                </div>");
                    out.println("                <div class=\"aira-action-group\">");
                    out.println(
                            "                  <button class=\"aira-button aira-button--primary\" type=\"submit\">Import Advancement Path Block</button>");
                    out.println("                </div>");
                    out.println("              </form>");
                    out.println("            </section>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es\">Back to Emerging Standards</a></p>");
                    out.println("          </section>");
                });
    }

    private void renderDetails(HttpServletRequest request, HttpServletResponse response,
            EsTopicPathDefinition definition) throws IOException {
        String contextPath = request.getContextPath();
        long usageCount = pathDefinitionDao.findTopicUsageCountsByDefinitionId()
                .getOrDefault(definition.getEsTopicPathDefinitionId(), 0L);
        EsTopicSpace topicSpace = definition.getEsTopicSpaceId() == null
                ? null
                : topicSpaceDao.findById(definition.getEsTopicSpaceId()).orElse(null);

        AdminShellRenderer.render(request, response, "Advancement Path Details - InteropHub",
                AdminSection.TOPIC_SPACES, ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Advancement Path Details</h2>");
                    out.println("            <section class=\"aira-panel\">");
                    out.println("              <p><strong>Name:</strong> "
                            + escapeHtml(orEmpty(definition.getPathName())) + "</p>");
                    out.println("              <p><strong>Code:</strong> "
                            + escapeHtml(orEmpty(definition.getPathCode())) + "</p>");
                    out.println("              <p><strong>Topic Space:</strong> "
                            + escapeHtml(topicSpace == null ? "" : orEmpty(topicSpace.getSpaceName())) + "</p>");
                    out.println("              <p><strong>Description:</strong> "
                            + escapeHtml(orEmpty(definition.getPathDescription())) + "</p>");
                    out.println("              <p><strong>Display Order:</strong> "
                            + escapeHtml(String.valueOf(definition.getDisplayOrder() == null
                                    ? 0
                                    : definition.getDisplayOrder()))
                            + "</p>");
                    out.println("              <p><strong>Active:</strong> "
                            + activeBadge(Boolean.TRUE.equals(definition.getIsActive())) + "</p>");
                    out.println("              <p><strong>Topic Usage:</strong> "
                            + escapeHtml(String.valueOf(usageCount)) + "</p>");
                    out.println("            </section>");
                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/paths?esTopicPathDefinitionId="
                            + definition.getEsTopicPathDefinitionId() + "&mode=edit&esTopicSpaceId="
                            + definition.getEsTopicSpaceId() + "\">Edit Advancement Path</a>");
                    out.println("            </div>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/paths?esTopicSpaceId=" + definition.getEsTopicSpaceId()
                            + "\">Back to Advancement Paths</a></p>");
                    out.println("          </section>");
                });
    }

    private void renderEditForm(HttpServletRequest request, HttpServletResponse response,
            EsTopicPathDefinition definition, String errorMessage, boolean creating) throws IOException {
        String contextPath = request.getContextPath();
        List<EsTopicSpace> allSpaces = topicSpaceDao.findAllOrdered();
        Long selectedSpaceId = definition.getEsTopicSpaceId();

        AdminShellRenderer.render(request, response, (creating ? "Create" : "Edit") + " Advancement Path - InteropHub",
                AdminSection.TOPIC_SPACES, ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">" + (creating ? "Create" : "Edit")
                            + " Advancement Path</h2>");

                    if (errorMessage != null && !errorMessage.isBlank()) {
                        out.println(
                                "            <div class=\"aira-alert aira-alert--danger\"><p><strong>Could not save:</strong> "
                                        + escapeHtml(errorMessage) + "</p></div>");
                    }

                    out.println("            <form class=\"aira-form\" action=\"" + contextPath
                            + "/admin/es/paths\" method=\"post\">");
                    if (!creating && definition.getEsTopicPathDefinitionId() != null) {
                        out.println("              <input type=\"hidden\" name=\"esTopicPathDefinitionId\" value=\""
                                + definition.getEsTopicPathDefinitionId() + "\" />");
                    }

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"pathCode\">Advancement Path Code (required)</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"pathCode\" name=\"pathCode\" type=\"text\" required value=\""
                                    + escapeHtml(orEmpty(definition.getPathCode())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"pathName\">Advancement Path Name (required)</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"pathName\" name=\"pathName\" type=\"text\" required value=\""
                                    + escapeHtml(orEmpty(definition.getPathName())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"esTopicSpaceId\">Topic Space (required)</label>");
                    out.println(
                            "                <select class=\"aira-select\" id=\"esTopicSpaceId\" name=\"esTopicSpaceId\" required>");
                    out.println("                  <option value=\"\">— Select —</option>");
                    for (EsTopicSpace topicSpace : allSpaces) {
                        if (topicSpace.getEsTopicSpaceId() == null || trimToNull(topicSpace.getSpaceCode()) == null) {
                            continue;
                        }
                        boolean isCurrent = topicSpace.getEsTopicSpaceId().equals(selectedSpaceId);
                        boolean isActive = Boolean.TRUE.equals(topicSpace.getIsActive());
                        String flags = isCurrent ? " selected" : "";
                        if (!isActive && !isCurrent) {
                            flags += " disabled";
                        }
                        out.println("                  <option value=\"" + topicSpace.getEsTopicSpaceId() + "\""
                                + flags + ">" + escapeHtml(orEmpty(topicSpace.getSpaceName()))
                                + (isActive ? "" : " (inactive)") + "</option>");
                    }
                    out.println("                </select>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"pathDescription\">Description</label>");
                    out.println(
                            "                <textarea class=\"aira-textarea\" id=\"pathDescription\" name=\"pathDescription\" rows=\"5\">"
                                    + escapeHtml(orEmpty(definition.getPathDescription())) + "</textarea>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"displayOrder\">Display Order (required)</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"displayOrder\" name=\"displayOrder\" type=\"number\" required value=\""
                                    + escapeHtml(String.valueOf(
                                            definition.getDisplayOrder() == null ? 0
                                                    : definition.getDisplayOrder()))
                                    + "\" />");
                    out.println("              </div>");

                    out.println("              <label class=\"aira-radio\"><input type=\"checkbox\" name=\"isActive\""
                            + (Boolean.TRUE.equals(definition.getIsActive()) || creating ? " checked" : "")
                            + " /> Active</label>");

                    out.println("              <div class=\"aira-action-group\">");
                    out.println(
                            "                <button class=\"aira-button aira-button--primary\" type=\"submit\">Save</button>");
                    out.println("              </div>");
                    out.println("            </form>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/paths\">Back to Advancement Paths</a></p>");
                    out.println("          </section>");
                });
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
}
