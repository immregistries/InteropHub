package org.airahub.interophub.servlet;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsNeighborhoodDao;
import org.airahub.interophub.dao.EsTopicNeighborhoodDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.EsNeighborhood;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.User;

public class AdminEsNeighborhoodServlet extends HttpServlet {

    private static final String ACTIVE_HREF = "/admin/es/neighborhoods";

    private final EsNeighborhoodDao esNeighborhoodDao;
    private final EsTopicNeighborhoodDao topicNeighborhoodDao;
    private final EsTopicSpaceDao topicSpaceDao;

    public AdminEsNeighborhoodServlet() {
        this.esNeighborhoodDao = new EsNeighborhoodDao();
        this.topicNeighborhoodDao = new EsTopicNeighborhoodDao();
        this.topicSpaceDao = new EsTopicSpaceDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String mode = trimToNull(request.getParameter("mode"));
        String neighborhoodIdRaw = trimToNull(request.getParameter("esNeighborhoodId"));
        Long selectedTopicSpaceId = parseId(trimToNull(request.getParameter("esTopicSpaceId")));

        if ("new".equalsIgnoreCase(mode)) {
            EsNeighborhood neighborhood = new EsNeighborhood();
            neighborhood.setEsTopicSpaceId(selectedTopicSpaceId);
            renderEditForm(request, response, neighborhood, null, true);
            return;
        }

        if (neighborhoodIdRaw != null) {
            Long neighborhoodId = parseId(neighborhoodIdRaw);
            if (neighborhoodId == null) {
                renderList(request, response, "Invalid neighborhood identifier.", null, selectedTopicSpaceId);
                return;
            }

            EsNeighborhood neighborhood = esNeighborhoodDao.findById(neighborhoodId).orElse(null);
            if (neighborhood == null) {
                renderList(request, response, "Neighborhood was not found.", null, selectedTopicSpaceId);
                return;
            }

            if ("edit".equalsIgnoreCase(mode)) {
                renderEditForm(request, response, neighborhood, null, false);
                return;
            }

            renderDetails(request, response, neighborhood);
            return;
        }

        String message = request.getParameter("saved") != null ? "Neighborhood saved." : null;
        if (request.getParameter("bulkSaved") != null) {
            message = "Neighborhood block imported.";
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
            handleBulkUpsert(request, response, contextPath, adminUser.get());
            return;
        }

        String neighborhoodIdRaw = trimToNull(request.getParameter("esNeighborhoodId"));
        boolean creating = neighborhoodIdRaw == null;

        EsNeighborhood neighborhood;
        if (creating) {
            neighborhood = new EsNeighborhood();
            neighborhood.setCreatedByUserId(adminUser.get().getUserId());
        } else {
            Long neighborhoodId = parseId(neighborhoodIdRaw);
            if (neighborhoodId == null) {
                renderList(request, response, "Invalid neighborhood identifier.", null, null);
                return;
            }
            neighborhood = esNeighborhoodDao.findById(neighborhoodId).orElse(null);
            if (neighborhood == null) {
                renderList(request, response, "Neighborhood was not found.", null, null);
                return;
            }
        }

        String neighborhoodCode = trimToNull(request.getParameter("neighborhoodCode"));
        String neighborhoodName = trimToNull(request.getParameter("neighborhoodName"));
        String description = trimToNull(request.getParameter("description"));
        Long topicSpaceId = parseId(trimToNull(request.getParameter("esTopicSpaceId")));
        String displayOrderRaw = trimToNull(request.getParameter("displayOrder"));
        boolean isActive = request.getParameter("isActive") != null;

        try {
            if (topicSpaceId == null) {
                throw new IllegalArgumentException("Topic Space is required.");
            }
            if (creating || !topicSpaceId.equals(neighborhood.getEsTopicSpaceId())) {
                requireActiveTopicSpace(topicSpaceId, "Only active Topic Spaces may receive new neighborhoods.");
            }
            ensureUniqueNeighborhoodNameInSpace(neighborhoodName, topicSpaceId, neighborhood.getEsNeighborhoodId());

            neighborhood.setEsTopicSpaceId(topicSpaceId);
            neighborhood.setNeighborhoodCode(required(neighborhoodCode, "Neighborhood code"));
            neighborhood.setNeighborhoodName(required(neighborhoodName, "Neighborhood name"));
            neighborhood.setDescription(description);
            neighborhood.setDisplayOrder(parseRequiredInt(displayOrderRaw, "Display order"));
            neighborhood.setIsActive(isActive);
            if (neighborhood.getCreatedByUserId() == null) {
                neighborhood.setCreatedByUserId(adminUser.get().getUserId());
            }

            esNeighborhoodDao.saveOrUpdate(neighborhood);
            response.sendRedirect(contextPath + "/admin/es/neighborhoods?saved=1&esTopicSpaceId=" + topicSpaceId);
        } catch (Exception ex) {
            neighborhood.setNeighborhoodCode(neighborhoodCode);
            neighborhood.setNeighborhoodName(neighborhoodName);
            neighborhood.setDescription(description);
            neighborhood.setEsTopicSpaceId(topicSpaceId);
            neighborhood.setDisplayOrder(parseIntOrNull(displayOrderRaw));
            neighborhood.setIsActive(isActive);
            renderEditForm(request, response, neighborhood, ex.getMessage(), creating);
        }
    }

    private void handleBulkUpsert(HttpServletRequest request, HttpServletResponse response, String contextPath,
            User adminUser)
            throws IOException {
        String bulkNeighborhoods = request.getParameter("bulkNeighborhoods");
        Long topicSpaceId = parseId(trimToNull(request.getParameter("esTopicSpaceId")));
        String normalizedBlock = trimToNull(bulkNeighborhoods);
        if (normalizedBlock == null) {
            renderList(request, response, "Paste at least one neighborhood line to import.", bulkNeighborhoods,
                    topicSpaceId);
            return;
        }
        if (topicSpaceId == null) {
            renderList(request, response, "Topic Space is required for bulk import.", bulkNeighborhoods, null);
            return;
        }
        try {
            requireActiveTopicSpace(topicSpaceId, "Only active Topic Spaces may receive new neighborhoods.");
        } catch (IllegalArgumentException ex) {
            renderList(request, response, ex.getMessage(), bulkNeighborhoods, topicSpaceId);
            return;
        }

        List<EsNeighborhood> existingNeighborhoods = esNeighborhoodDao.findAllOrderedBySpaceId(topicSpaceId);
        Set<String> usedCodes = new HashSet<>();
        int nextDisplayOrder = 0;
        for (EsNeighborhood existing : existingNeighborhoods) {
            String code = trimToNull(existing.getNeighborhoodCode());
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
                        "Each line must use 'Neighborhood: Description' format. Problem line: " + normalizedLine,
                        bulkNeighborhoods, topicSpaceId);
                return;
            }

            String neighborhoodName = trimToNull(normalizedLine.substring(0, colonIndex));
            String description = trimToNull(normalizedLine.substring(colonIndex + 1));
            if (neighborhoodName == null) {
                renderList(request, response, "Neighborhood name is required on every line.", bulkNeighborhoods,
                        topicSpaceId);
                return;
            }

            EsNeighborhood neighborhood = esNeighborhoodDao.findByNameInSpace(neighborhoodName, topicSpaceId)
                    .orElse(null);
            boolean creating = neighborhood == null;
            if (creating) {
                neighborhood = new EsNeighborhood();
                neighborhood.setNeighborhoodName(neighborhoodName);
                neighborhood.setNeighborhoodCode(generateUniqueCode(neighborhoodName, usedCodes));
                neighborhood.setEsTopicSpaceId(topicSpaceId);
                neighborhood.setCreatedByUserId(adminUser.getUserId());
                neighborhood.setDisplayOrder(nextDisplayOrder++);
            }

            neighborhood.setNeighborhoodName(neighborhoodName);
            neighborhood.setDescription(description);
            neighborhood.setIsActive(Boolean.TRUE);
            if (neighborhood.getNeighborhoodCode() == null || neighborhood.getNeighborhoodCode().isBlank()) {
                neighborhood.setNeighborhoodCode(generateUniqueCode(neighborhoodName, usedCodes));
            }
            if (neighborhood.getCreatedByUserId() == null) {
                neighborhood.setCreatedByUserId(adminUser.getUserId());
            }
            if (neighborhood.getDisplayOrder() == null) {
                neighborhood.setDisplayOrder(nextDisplayOrder++);
            }

            esNeighborhoodDao.saveOrUpdate(neighborhood);
            importedCount++;
        }

        response.sendRedirect(contextPath + "/admin/es/neighborhoods?bulkSaved=1&count=" + importedCount
                + "&esTopicSpaceId=" + topicSpaceId);
    }

    private void renderList(HttpServletRequest request, HttpServletResponse response, String message,
            String bulkNeighborhoods, Long selectedTopicSpaceId)
            throws IOException {
        String contextPath = request.getContextPath();
        List<EsTopicSpace> allSpaces = topicSpaceDao.findAllOrdered();
        Map<Long, EsTopicSpace> spacesById = allSpaces.stream()
                .collect(java.util.stream.Collectors.toMap(
                        EsTopicSpace::getEsTopicSpaceId,
                        s -> s,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new));
        List<EsNeighborhood> neighborhoods = selectedTopicSpaceId == null
                ? List.of()
                : esNeighborhoodDao.findAllOrderedBySpaceId(selectedTopicSpaceId);
        Map<Long, Long> usageCounts = topicNeighborhoodDao.findActiveTopicCountsByNeighborhoodId();
        EsTopicSpace selectedTopicSpace = selectedTopicSpaceId == null
                ? null
                : spacesById.get(selectedTopicSpaceId);
        boolean topicSpaceSelected = selectedTopicSpace != null;

        AdminShellRenderer.render(request, response, "Neighborhoods Admin - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Neighborhoods</h2>");
                    out.println(
                            "            <p class=\"aira-meta\">Manage Neighborhood options used by the public ES Topics page.</p>");
                    if (message != null && !message.isBlank()) {
                        out.println("            <div class=\"aira-alert aira-alert--success\"><p>"
                                + escapeHtml(message) + "</p></div>");
                    }

                    out.println("            <section class=\"aira-panel\">");
                    out.println("              <h3 class=\"aira-subsection-title\">Topic Space</h3>");
                    out.println("              <form class=\"aira-form\" action=\"" + contextPath
                            + "/admin/es/neighborhoods\" method=\"get\">");
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
                            "                <noscript><button class=\"aira-button aira-button--secondary\" type=\"submit\">Load Neighborhoods</button></noscript>");
                    out.println("              </form>");
                    out.println("            </section>");

                    if (!topicSpaceSelected) {
                        out.println(
                                "            <p class=\"aira-meta\">Select a Topic Space to view, add, or bulk import neighborhoods.</p>");
                        out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                                + "/admin/es\">Back to Emerging Standards</a></p>");
                        out.println("          </section>");
                        return;
                    }

                    out.println("            <h3 class=\"aira-subsection-title\">Current Neighborhoods for "
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
                    out.println("                  <th>Active Topics</th>");
                    out.println("                </tr>");
                    out.println("              </thead>");
                    out.println("              <tbody>");
                    for (EsNeighborhood neighborhood : neighborhoods) {
                        out.println("                <tr>");
                        out.println("                  <td><a class=\"aira-inline-link\" href=\"" + contextPath
                                + "/admin/es/neighborhoods?esNeighborhoodId=" + neighborhood.getEsNeighborhoodId()
                                + "&esTopicSpaceId=" + selectedTopicSpaceId
                                + "\">" + escapeHtml(orEmpty(neighborhood.getNeighborhoodName())) + "</a></td>");
                        out.println("                  <td>" + escapeHtml(orEmpty(neighborhood.getNeighborhoodCode()))
                                + "</td>");
                        EsTopicSpace topicSpace = spacesById.get(neighborhood.getEsTopicSpaceId());
                        out.println("                  <td>"
                                + escapeHtml(topicSpace == null ? "" : orEmpty(topicSpace.getSpaceName()))
                                + "</td>");
                        out.println("                  <td>"
                                + escapeHtml(String.valueOf(neighborhood.getDisplayOrder() == null
                                        ? 0
                                        : neighborhood.getDisplayOrder()))
                                + "</td>");
                        out.println("                  <td>" + activeBadge(Boolean.TRUE.equals(neighborhood.getIsActive()))
                                + "</td>");
                        Long usageCount = usageCounts.getOrDefault(neighborhood.getEsNeighborhoodId(), 0L);
                        out.println("                  <td>" + escapeHtml(String.valueOf(usageCount)) + "</td>");
                        out.println("                </tr>");
                    }
                    if (neighborhoods.isEmpty()) {
                        out.println("                <tr>");
                        out.println("                  <td colspan=\"6\">No neighborhoods found for this Topic Space.</td>");
                        out.println("                </tr>");
                    }
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");

                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--primary\" href=\"" + contextPath
                            + "/admin/es/neighborhoods?mode=new&esTopicSpaceId=" + selectedTopicSpaceId
                            + "\">Add Neighborhood</a>");
                    out.println("            </div>");

                    out.println("            <section class=\"aira-panel\">");
                    out.println("              <h3 class=\"aira-subsection-title\">Bulk Load Descriptions</h3>");
                    out.println(
                            "              <p class=\"aira-meta\">Paste one neighborhood per line using <strong>Name: Description</strong>.</p>");
                    out.println("              <form class=\"aira-form\" action=\"" + contextPath
                            + "/admin/es/neighborhoods\" method=\"post\">");
                    out.println("                <input type=\"hidden\" name=\"action\" value=\"bulkUpsert\" />");
                    out.println("                <input type=\"hidden\" name=\"esTopicSpaceId\" value=\""
                            + selectedTopicSpaceId + "\" />");
                    out.println("                <p><strong>Topic Space:</strong> "
                            + escapeHtml(orEmpty(selectedTopicSpace.getSpaceName())) + "</p>");
                    out.println("                <div class=\"aira-field\">");
                    out.println("                  <label for=\"bulkNeighborhoods\">Neighborhood block</label>");
                    out.println(
                            "                  <textarea class=\"aira-textarea\" id=\"bulkNeighborhoods\" name=\"bulkNeighborhoods\" rows=\"8\""
                                    + " placeholder=\"Advanced Access: New technologies...\">"
                                    + escapeHtml(orEmpty(bulkNeighborhoods)) + "</textarea>");
                    out.println("                </div>");
                    out.println("                <div class=\"aira-action-group\">");
                    out.println(
                            "                  <button class=\"aira-button aira-button--primary\" type=\"submit\">Import Neighborhood Block</button>");
                    out.println("                </div>");
                    out.println("              </form>");
                    out.println("            </section>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es\">Back to Emerging Standards</a></p>");
                    out.println("          </section>");
                });
    }

    private void renderDetails(HttpServletRequest request, HttpServletResponse response, EsNeighborhood neighborhood)
            throws IOException {
        String contextPath = request.getContextPath();
        long activeTopicCount = topicNeighborhoodDao.findActiveTopicCountsByNeighborhoodId()
                .getOrDefault(neighborhood.getEsNeighborhoodId(), 0L);
        EsTopicSpace topicSpace = neighborhood.getEsTopicSpaceId() == null
                ? null
                : topicSpaceDao.findById(neighborhood.getEsTopicSpaceId()).orElse(null);

        AdminShellRenderer.render(request, response, "Neighborhood Details - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Neighborhood Details</h2>");
                    out.println("            <section class=\"aira-panel\">");
                    out.println("              <p><strong>Name:</strong> "
                            + escapeHtml(orEmpty(neighborhood.getNeighborhoodName())) + "</p>");
                    out.println("              <p><strong>Code:</strong> "
                            + escapeHtml(orEmpty(neighborhood.getNeighborhoodCode())) + "</p>");
                    out.println("              <p><strong>Topic Space:</strong> "
                            + escapeHtml(topicSpace == null ? "" : orEmpty(topicSpace.getSpaceName())) + "</p>");
                    out.println("              <p><strong>Description:</strong> "
                            + escapeHtml(orEmpty(neighborhood.getDescription())) + "</p>");
                    out.println("              <p><strong>Display Order:</strong> "
                            + escapeHtml(String.valueOf(neighborhood.getDisplayOrder() == null
                                    ? 0
                                    : neighborhood.getDisplayOrder()))
                            + "</p>");
                    out.println("              <p><strong>Active:</strong> "
                            + activeBadge(Boolean.TRUE.equals(neighborhood.getIsActive())) + "</p>");
                    out.println("              <p><strong>Active Topic Usage:</strong> "
                            + escapeHtml(String.valueOf(activeTopicCount)) + "</p>");
                    out.println("            </section>");
                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/neighborhoods?esNeighborhoodId=" + neighborhood.getEsNeighborhoodId()
                            + "&mode=edit&esTopicSpaceId=" + neighborhood.getEsTopicSpaceId()
                            + "\">Edit Neighborhood</a>");
                    out.println("            </div>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/neighborhoods?esTopicSpaceId=" + neighborhood.getEsTopicSpaceId()
                            + "\">Back to Neighborhoods</a></p>");
                    out.println("          </section>");
                });
    }

    private void renderEditForm(HttpServletRequest request, HttpServletResponse response, EsNeighborhood neighborhood,
            String errorMessage, boolean creating) throws IOException {
        String contextPath = request.getContextPath();
        List<EsTopicSpace> allSpaces = topicSpaceDao.findAllOrdered();
        Long selectedSpaceId = neighborhood.getEsTopicSpaceId();

        AdminShellRenderer.render(request, response, (creating ? "Create" : "Edit") + " Neighborhood - InteropHub",
                AdminSection.TOPIC_SPACES, ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">" + (creating ? "Create" : "Edit")
                            + " Neighborhood</h2>");

                    if (errorMessage != null && !errorMessage.isBlank()) {
                        out.println(
                                "            <div class=\"aira-alert aira-alert--danger\"><p><strong>Could not save:</strong> "
                                        + escapeHtml(errorMessage) + "</p></div>");
                    }

                    out.println("            <form class=\"aira-form\" action=\"" + contextPath
                            + "/admin/es/neighborhoods\" method=\"post\">");
                    if (!creating && neighborhood.getEsNeighborhoodId() != null) {
                        out.println("              <input type=\"hidden\" name=\"esNeighborhoodId\" value=\""
                                + neighborhood.getEsNeighborhoodId() + "\" />");
                    }

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"neighborhoodCode\">Neighborhood Code (required)</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"neighborhoodCode\" name=\"neighborhoodCode\" type=\"text\" required value=\""
                                    + escapeHtml(orEmpty(neighborhood.getNeighborhoodCode())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"neighborhoodName\">Neighborhood Name (required)</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"neighborhoodName\" name=\"neighborhoodName\" type=\"text\" required value=\""
                                    + escapeHtml(orEmpty(neighborhood.getNeighborhoodName())) + "\" />");
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
                    out.println("                <label for=\"description\">Description</label>");
                    out.println(
                            "                <textarea class=\"aira-textarea\" id=\"description\" name=\"description\" rows=\"5\">"
                                    + escapeHtml(orEmpty(neighborhood.getDescription())) + "</textarea>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"displayOrder\">Display Order (required)</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"displayOrder\" name=\"displayOrder\" type=\"number\" required value=\""
                                    + escapeHtml(String.valueOf(
                                            neighborhood.getDisplayOrder() == null ? 0
                                                    : neighborhood.getDisplayOrder()))
                                    + "\" />");
                    out.println("              </div>");

                    out.println("              <label class=\"aira-radio\"><input type=\"checkbox\" name=\"isActive\""
                            + (Boolean.TRUE.equals(neighborhood.getIsActive()) || creating ? " checked" : "")
                            + " /> Active</label>");

                    out.println("              <div class=\"aira-action-group\">");
                    out.println(
                            "                <button class=\"aira-button aira-button--primary\" type=\"submit\">Save</button>");
                    out.println("              </div>");
                    out.println("            </form>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/neighborhoods\">Back to Neighborhoods</a></p>");
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

    private void ensureUniqueNeighborhoodNameInSpace(String neighborhoodName, Long topicSpaceId, Long excludeId) {
        String normalizedName = trimToNull(neighborhoodName);
        if (normalizedName == null || topicSpaceId == null) {
            return;
        }
        boolean duplicate = esNeighborhoodDao
                .findByNameInSpaceExcludingId(normalizedName, topicSpaceId, excludeId)
                .isPresent();
        if (duplicate) {
            throw new IllegalArgumentException(
                    "Neighborhood name must be unique within the selected Topic Space.");
        }
    }

    private String generateUniqueCode(String neighborhoodName, Set<String> usedCodes) {
        String base = neighborhoodName == null
                ? "neighborhood"
                : neighborhoodName.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("(^-+|-+$)", "");
        if (base.isBlank()) {
            base = "neighborhood";
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
