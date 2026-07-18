package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
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
import org.airahub.interophub.service.AuthFlowService;

public class AdminEsNeighborhoodServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsNeighborhoodDao esNeighborhoodDao;
    private final EsTopicNeighborhoodDao topicNeighborhoodDao;
    private final EsTopicSpaceDao topicSpaceDao;

    public AdminEsNeighborhoodServlet() {
        this.authFlowService = new AuthFlowService();
        this.esNeighborhoodDao = new EsNeighborhoodDao();
        this.topicNeighborhoodDao = new EsTopicNeighborhoodDao();
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
        String neighborhoodIdRaw = trimToNull(request.getParameter("esNeighborhoodId"));

        if ("new".equalsIgnoreCase(mode)) {
            renderEditForm(response, contextPath, new EsNeighborhood(), null, true);
            return;
        }

        if (neighborhoodIdRaw != null) {
            Long neighborhoodId = parseId(neighborhoodIdRaw);
            if (neighborhoodId == null) {
                renderList(response, contextPath, "Invalid neighborhood identifier.", null, null);
                return;
            }

            EsNeighborhood neighborhood = esNeighborhoodDao.findById(neighborhoodId).orElse(null);
            if (neighborhood == null) {
                renderList(response, contextPath, "Neighborhood was not found.", null, null);
                return;
            }

            if ("edit".equalsIgnoreCase(mode)) {
                renderEditForm(response, contextPath, neighborhood, null, false);
                return;
            }

            renderDetails(response, contextPath, neighborhood);
            return;
        }

        String message = request.getParameter("saved") != null ? "Neighborhood saved." : null;
        if (request.getParameter("bulkSaved") != null) {
            message = "Neighborhood block imported.";
        }
        renderList(response, contextPath, message, null, null);
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
                renderList(response, contextPath, "Invalid neighborhood identifier.", null, null);
                return;
            }
            neighborhood = esNeighborhoodDao.findById(neighborhoodId).orElse(null);
            if (neighborhood == null) {
                renderList(response, contextPath, "Neighborhood was not found.", null, null);
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
            response.sendRedirect(contextPath + "/admin/es/neighborhoods?saved=1");
        } catch (Exception ex) {
            neighborhood.setNeighborhoodCode(neighborhoodCode);
            neighborhood.setNeighborhoodName(neighborhoodName);
            neighborhood.setDescription(description);
            neighborhood.setEsTopicSpaceId(topicSpaceId);
            neighborhood.setDisplayOrder(parseIntOrNull(displayOrderRaw));
            neighborhood.setIsActive(isActive);
            renderEditForm(response, contextPath, neighborhood, ex.getMessage(), creating);
        }
    }

    private void handleBulkUpsert(HttpServletRequest request, HttpServletResponse response, String contextPath,
            User adminUser)
            throws IOException {
        String bulkNeighborhoods = request.getParameter("bulkNeighborhoods");
        Long topicSpaceId = parseId(trimToNull(request.getParameter("esTopicSpaceId")));
        String normalizedBlock = trimToNull(bulkNeighborhoods);
        if (normalizedBlock == null) {
            renderList(response, contextPath, "Paste at least one neighborhood line to import.", bulkNeighborhoods,
                    topicSpaceId);
            return;
        }
        if (topicSpaceId == null) {
            renderList(response, contextPath, "Topic Space is required for bulk import.", bulkNeighborhoods, null);
            return;
        }
        try {
            requireActiveTopicSpace(topicSpaceId, "Only active Topic Spaces may receive new neighborhoods.");
        } catch (IllegalArgumentException ex) {
            renderList(response, contextPath, ex.getMessage(), bulkNeighborhoods, topicSpaceId);
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
                renderList(response, contextPath,
                        "Each line must use 'Neighborhood: Description' format. Problem line: " + normalizedLine,
                    bulkNeighborhoods, topicSpaceId);
                return;
            }

            String neighborhoodName = trimToNull(normalizedLine.substring(0, colonIndex));
            String description = trimToNull(normalizedLine.substring(colonIndex + 1));
            if (neighborhoodName == null) {
                renderList(response, contextPath, "Neighborhood name is required on every line.", bulkNeighborhoods,
                        topicSpaceId);
                return;
            }

            EsNeighborhood neighborhood = esNeighborhoodDao.findByNameInSpace(neighborhoodName, topicSpaceId).orElse(null);
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

        response.sendRedirect(contextPath + "/admin/es/neighborhoods?bulkSaved=1&count=" + importedCount);
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

        private void renderList(HttpServletResponse response, String contextPath, String message, String bulkNeighborhoods,
            Long bulkSpaceId)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        List<EsNeighborhood> neighborhoods = esNeighborhoodDao.findAllOrdered();
        Map<Long, EsTopicSpace> spacesById = topicSpaceDao.findAllOrdered().stream()
            .collect(java.util.stream.Collectors.toMap(
                EsTopicSpace::getEsTopicSpaceId,
                s -> s,
                (left, right) -> left,
                java.util.LinkedHashMap::new));
        List<EsTopicSpace> activeSpaces = topicSpaceDao.findAllActiveOrdered();
        Map<Long, Long> usageCounts = topicNeighborhoodDao.findActiveTopicCountsByNeighborhoodId();

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Neighborhoods Admin - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Neighborhoods</h2>");
                panelOut.println("        <p>Manage Neighborhood options used by the public ES Topics page.</p>");
                if (message != null && !message.isBlank()) {
                    panelOut.println("        <p><strong>" + escapeHtml(message) + "</strong></p>");
                }

                panelOut.println("        <p><a href=\"" + contextPath
                        + "/admin/es/neighborhoods?mode=new\">Add Neighborhood</a></p>");

                panelOut.println("        <section class=\"panel\">");
                panelOut.println("          <h3>Bulk Load Descriptions</h3>");
                panelOut.println(
                        "          <p>Paste one neighborhood per line using <strong>Name: Description</strong>.</p>");
                panelOut.println("          <form class=\"login-form\" action=\"" + contextPath
                        + "/admin/es/neighborhoods\" method=\"post\">");
                panelOut.println("            <input type=\"hidden\" name=\"action\" value=\"bulkUpsert\" />");
                panelOut.println("            <label for=\"bulkSpaceId\">Topic Space (required)</label>");
                panelOut.println("            <select id=\"bulkSpaceId\" name=\"esTopicSpaceId\" required>");
                panelOut.println("              <option value=\"\">\u2014 Select \u2014</option>");
                for (EsTopicSpace space : activeSpaces) {
                    panelOut.println("              <option value=\"" + space.getEsTopicSpaceId() + "\""
                        + (space.getEsTopicSpaceId().equals(bulkSpaceId) ? " selected" : "")
                        + ">" + escapeHtml(orEmpty(space.getSpaceName())) + "</option>");
                }
                panelOut.println("            </select>");
                panelOut.println("            <label for=\"bulkNeighborhoods\">Neighborhood block</label>");
                panelOut.println("            <textarea id=\"bulkNeighborhoods\" name=\"bulkNeighborhoods\" rows=\"8\""
                        + " placeholder=\"Advanced Access: New technologies...\">"
                        + escapeHtml(orEmpty(bulkNeighborhoods)) + "</textarea>");
                panelOut.println("            <button type=\"submit\">Import Neighborhood Block</button>");
                panelOut.println("          </form>");
                panelOut.println("        </section>");

                panelOut.println("        <table class=\"data-table\">");
                panelOut.println("          <thead>");
                panelOut.println("            <tr>");
                panelOut.println("              <th>Name</th>");
                panelOut.println("              <th>Code</th>");
                panelOut.println("              <th>Topic Space</th>");
                panelOut.println("              <th>Display Order</th>");
                panelOut.println("              <th>Active</th>");
                panelOut.println("              <th>Active Topics</th>");
                panelOut.println("            </tr>");
                panelOut.println("          </thead>");
                panelOut.println("          <tbody>");
                for (EsNeighborhood neighborhood : neighborhoods) {
                    panelOut.println("            <tr>");
                    panelOut.println("              <td><a href=\"" + contextPath
                            + "/admin/es/neighborhoods?esNeighborhoodId=" + neighborhood.getEsNeighborhoodId()
                            + "\">" + escapeHtml(orEmpty(neighborhood.getNeighborhoodName())) + "</a></td>");
                    panelOut.println("              <td>" + escapeHtml(orEmpty(neighborhood.getNeighborhoodCode()))
                            + "</td>");
                        EsTopicSpace topicSpace = spacesById.get(neighborhood.getEsTopicSpaceId());
                        panelOut.println("              <td>" + escapeHtml(topicSpace == null ? "" : orEmpty(topicSpace.getSpaceName()))
                            + "</td>");
                    panelOut.println("              <td>"
                            + escapeHtml(String.valueOf(neighborhood.getDisplayOrder() == null
                                    ? 0
                                    : neighborhood.getDisplayOrder()))
                            + "</td>");
                    panelOut.println(
                            "              <td>" + (Boolean.TRUE.equals(neighborhood.getIsActive()) ? "Yes" : "No")
                                    + "</td>");
                    Long usageCount = usageCounts.getOrDefault(neighborhood.getEsNeighborhoodId(), 0L);
                    panelOut.println("              <td>" + escapeHtml(String.valueOf(usageCount)) + "</td>");
                    panelOut.println("            </tr>");
                }
                if (neighborhoods.isEmpty()) {
                    panelOut.println("            <tr>");
                    panelOut.println("              <td colspan=\"6\">No neighborhoods found.</td>");
                    panelOut.println("            </tr>");
                }
                panelOut.println("          </tbody>");
                panelOut.println("        </table>");
                panelOut.println(
                        "        <p><a href=\"" + contextPath + "/admin/es\">Back to Emerging Standards</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderDetails(HttpServletResponse response, String contextPath, EsNeighborhood neighborhood)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        long activeTopicCount = topicNeighborhoodDao.findActiveTopicCountsByNeighborhoodId()
                .getOrDefault(neighborhood.getEsNeighborhoodId(), 0L);
        EsTopicSpace topicSpace = neighborhood.getEsTopicSpaceId() == null
            ? null
            : topicSpaceDao.findById(neighborhood.getEsTopicSpaceId()).orElse(null);

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Neighborhood Details - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Neighborhood Details</h2>");
                panelOut.println("        <section class=\"panel\">");
                panelOut.println("          <p><strong>Name:</strong> "
                        + escapeHtml(orEmpty(neighborhood.getNeighborhoodName())) + "</p>");
                panelOut.println("          <p><strong>Code:</strong> "
                        + escapeHtml(orEmpty(neighborhood.getNeighborhoodCode())) + "</p>");
                panelOut.println("          <p><strong>Topic Space:</strong> "
                    + escapeHtml(topicSpace == null ? "" : orEmpty(topicSpace.getSpaceName())) + "</p>");
                panelOut.println("          <p><strong>Description:</strong> "
                        + escapeHtml(orEmpty(neighborhood.getDescription())) + "</p>");
                panelOut.println("          <p><strong>Display Order:</strong> "
                        + escapeHtml(String.valueOf(neighborhood.getDisplayOrder() == null
                                ? 0
                                : neighborhood.getDisplayOrder()))
                        + "</p>");
                panelOut.println("          <p><strong>Active:</strong> "
                        + (Boolean.TRUE.equals(neighborhood.getIsActive()) ? "Yes" : "No") + "</p>");
                panelOut.println("          <p><strong>Active Topic Usage:</strong> "
                        + escapeHtml(String.valueOf(activeTopicCount)) + "</p>");
                panelOut.println("        </section>");
                panelOut.println("        <p><a href=\"" + contextPath + "/admin/es/neighborhoods?esNeighborhoodId="
                        + neighborhood.getEsNeighborhoodId() + "&mode=edit\">Edit Neighborhood</a></p>");
                panelOut.println("        <p><a href=\"" + contextPath
                        + "/admin/es/neighborhoods\">Back to Neighborhoods</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderEditForm(HttpServletResponse response, String contextPath, EsNeighborhood neighborhood,
            String errorMessage, boolean creating) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        List<EsTopicSpace> allSpaces = topicSpaceDao.findAllOrdered();
        Long selectedSpaceId = neighborhood.getEsTopicSpaceId();

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, (creating ? "Create" : "Edit") + " Neighborhood - InteropHub", contextPath,
                    panelOut -> {
                        panelOut.println("      <section class=\"panel\">");
                        panelOut.println("        <h2>" + (creating ? "Create" : "Edit") + " Neighborhood</h2>");

                        if (errorMessage != null && !errorMessage.isBlank()) {
                            panelOut.println("        <p><strong>Could not save:</strong> " + escapeHtml(errorMessage)
                                    + "</p>");
                        }

                        panelOut.println("        <form class=\"login-form\" action=\"" + contextPath
                                + "/admin/es/neighborhoods\" method=\"post\">");
                        if (!creating && neighborhood.getEsNeighborhoodId() != null) {
                            panelOut.println("      <input type=\"hidden\" name=\"esNeighborhoodId\" value=\""
                                    + neighborhood.getEsNeighborhoodId() + "\" />");
                        }

                        panelOut.println("      <label for=\"neighborhoodCode\">Neighborhood Code (required)</label>");
                        panelOut.println(
                                "      <input id=\"neighborhoodCode\" name=\"neighborhoodCode\" type=\"text\" required value=\""
                                        + escapeHtml(orEmpty(neighborhood.getNeighborhoodCode())) + "\" />");

                        panelOut.println("      <label for=\"neighborhoodName\">Neighborhood Name (required)</label>");
                        panelOut.println(
                                "      <input id=\"neighborhoodName\" name=\"neighborhoodName\" type=\"text\" required value=\""
                                        + escapeHtml(orEmpty(neighborhood.getNeighborhoodName())) + "\" />");

                        panelOut.println("      <label for=\"esTopicSpaceId\">Topic Space (required)</label>");
                        panelOut.println("      <select id=\"esTopicSpaceId\" name=\"esTopicSpaceId\" required>");
                        panelOut.println("        <option value=\"\">\u2014 Select \u2014</option>");
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
                            panelOut.println("        <option value=\"" + topicSpace.getEsTopicSpaceId() + "\"" + flags + ">"
                                    + escapeHtml(orEmpty(topicSpace.getSpaceName()))
                                    + (isActive ? "" : " (inactive)")
                                    + "</option>");
                        }
                        panelOut.println("      </select>");

                        panelOut.println("      <label for=\"description\">Description</label>");
                        panelOut.println("      <textarea id=\"description\" name=\"description\" rows=\"5\">"
                                + escapeHtml(orEmpty(neighborhood.getDescription())) + "</textarea>");

                        panelOut.println("      <label for=\"displayOrder\">Display Order (required)</label>");
                        panelOut.println(
                                "      <input id=\"displayOrder\" name=\"displayOrder\" type=\"number\" required value=\""
                                        + escapeHtml(String.valueOf(
                                                neighborhood.getDisplayOrder() == null ? 0
                                                        : neighborhood.getDisplayOrder()))
                                        + "\" />");

                        panelOut.println("      <label><input type=\"checkbox\" name=\"isActive\""
                                + (Boolean.TRUE.equals(neighborhood.getIsActive()) || creating ? " checked" : "")
                                + " /> Active</label>");

                        panelOut.println("      <button type=\"submit\">Save</button>");
                        panelOut.println("    </form>");
                        panelOut.println("    <p><a href=\"" + contextPath
                                + "/admin/es/neighborhoods\">Back to Neighborhoods</a></p>");
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
                panelOut.println("        <p>You must be an InteropHub admin to access ES neighborhood settings.</p>");
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
