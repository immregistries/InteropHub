package org.airahub.interophub.servlet;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.ConnectWorkspaceDao;
import org.airahub.interophub.model.ConnectWorkspace;
import org.airahub.interophub.dao.IgTopicDao;
import org.airahub.interophub.model.IgTopic;
import org.airahub.interophub.model.User;

public class AdminIgTopicServlet extends HttpServlet {
    private static final DateTimeFormatter CREATED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final IgTopicDao igTopicDao;
    private final ConnectWorkspaceDao connectWorkspaceDao;

    public AdminIgTopicServlet() {
        this.igTopicDao = new IgTopicDao();
        this.connectWorkspaceDao = new ConnectWorkspaceDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String mode = trimToNull(request.getParameter("mode"));
        String topicIdRaw = trimToNull(request.getParameter("topicId"));

        if ("new".equalsIgnoreCase(mode)) {
            IgTopic newTopic = new IgTopic();
            newTopic.setStatus(IgTopic.TopicStatus.ACTIVE);
            renderEditForm(request, response, newTopic, null, true);
            return;
        }

        if (topicIdRaw != null) {
            Long topicId = parseId(topicIdRaw);
            if (topicId == null) {
                renderList(request, response, "Invalid topic identifier.");
                return;
            }

            IgTopic topic = igTopicDao.findById(topicId).orElse(null);
            if (topic == null) {
                renderList(request, response, "Topic entry was not found.");
                return;
            }

            if ("edit".equalsIgnoreCase(mode)) {
                renderEditForm(request, response, topic, null, false);
                return;
            }

            renderDetails(request, response, topic);
            return;
        }

        String message = request.getParameter("saved") != null ? "Topic settings saved." : null;
        renderList(request, response, message);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String topicIdRaw = trimToNull(request.getParameter("topicId"));

        boolean createNew = topicIdRaw == null;
        IgTopic topic;
        if (createNew) {
            topic = new IgTopic();
            topic.setCreatedByUserId(adminUser.get().getUserId());
        } else {
            Long topicId = parseId(topicIdRaw);
            if (topicId == null) {
                renderList(request, response, "Invalid topic identifier.");
                return;
            }

            topic = igTopicDao.findById(topicId).orElse(null);
            if (topic == null) {
                renderList(request, response, "Topic entry was not found.");
                return;
            }
        }

        String topicCode = trimToNull(request.getParameter("topicCode"));
        String topicName = trimToNull(request.getParameter("topicName"));
        String description = trimToNull(request.getParameter("description"));
        String statusRaw = trimToNull(request.getParameter("status"));

        try {
            if (createNew) {
                String normalizedTopicCode = required(topicCode, "Topic code");
                if (igTopicDao.findByTopicCode(normalizedTopicCode).isPresent()) {
                    throw new IllegalArgumentException("Topic code must be unique.");
                }
                topic.setTopicCode(normalizedTopicCode);
            }

            topic.setTopicName(required(topicName, "Topic name"));
            topic.setDescription(description);
            topic.setStatus(parseStatus(required(statusRaw, "Status")));

            igTopicDao.saveOrUpdate(topic);
            response.sendRedirect(contextPath + "/admin/topics?saved=1");
        } catch (Exception ex) {
            if (createNew) {
                topic.setTopicCode(topicCode);
                if (topic.getCreatedByUserId() == null) {
                    topic.setCreatedByUserId(adminUser.get().getUserId());
                }
            }
            topic.setTopicName(topicName);
            topic.setDescription(description);
            if (statusRaw != null) {
                try {
                    topic.setStatus(parseStatus(statusRaw));
                } catch (Exception ignored) {
                    // Keep existing status if parse fails to avoid masking original validation
                    // error.
                }
            }
            renderEditForm(request, response, topic, ex.getMessage(), createNew);
        }
    }

    private void renderList(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        String contextPath = request.getContextPath();
        List<IgTopic> topics = igTopicDao.findAllOrdered();

        AdminShellRenderer.render(request, response, "IG Topics Admin - InteropHub", AdminSection.CONTENT,
                "/admin/topics", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">IG Topics</h2>");
                    out.println("            <p class=\"aira-meta\">View and manage Interop Guide topics.</p>");
                    if (message != null && !message.isBlank()) {
                        out.println("            <div class=\"aira-alert aira-alert--success\"><p>" + escapeHtml(message) + "</p></div>");
                    }

                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println("              <thead>");
                    out.println("                <tr>");
                    out.println("                  <th>Topic Code</th>");
                    out.println("                  <th>Topic Name</th>");
                    out.println("                  <th>Status</th>");
                    out.println("                </tr>");
                    out.println("              </thead>");
                    out.println("              <tbody>");
                    for (IgTopic topic : topics) {
                        out.println("                <tr>");
                        out.println("                  <td><a class=\"aira-inline-link\" href=\"" + contextPath + "/admin/topics?topicId=" + topic.getTopicId()
                                + "\">" + escapeHtml(orEmpty(topic.getTopicCode())) + "</a></td>");
                        out.println("                  <td><a class=\"aira-inline-link\" href=\"" + contextPath + "/admin/topics?topicId=" + topic.getTopicId()
                                + "\">" + escapeHtml(orEmpty(topic.getTopicName())) + "</a></td>");
                        out.println("                  <td>" + escapeHtml(topic.getStatus() == null ? "" : topic.getStatus().name())
                                + "</td>");
                        out.println("                </tr>");
                    }
                    if (topics.isEmpty()) {
                        out.println("                <tr>");
                        out.println("                  <td colspan=\"3\">No topic entries yet.</td>");
                        out.println("                </tr>");
                    }
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");

                    out.println("            <h3 class=\"aira-subsection-title\">Create New Topic</h3>");
                    out.println(
                            "            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--primary\" href=\"" + contextPath + "/admin/topics?mode=new\">Add New IG Topic</a>");
                    out.println("            </div>");

                    out.println("          </section>");
                });
    }

    private void renderDetails(HttpServletRequest request, HttpServletResponse response, IgTopic topic)
            throws IOException {
        String contextPath = request.getContextPath();
        List<ConnectWorkspace> workspaces = connectWorkspaceDao.findByTopicIdOrdered(topic.getTopicId());

        AdminShellRenderer.render(request, response, "Topic Details - InteropHub", AdminSection.CONTENT,
                "/admin/topics", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Topic Details</h2>");
                    out.println("            <p class=\"aira-meta\">This view is read-only. Use Edit to change this topic.</p>");

                    out.println(
                            "            <p><strong>Topic Code:</strong> " + escapeHtml(orEmpty(topic.getTopicCode())) + "</p>");
                    out.println(
                            "            <p><strong>Topic Name:</strong> " + escapeHtml(orEmpty(topic.getTopicName())) + "</p>");
                    out.println(
                            "            <p><strong>Description:</strong> " + escapeHtml(orEmpty(topic.getDescription()))
                                    + "</p>");
                    out.println("            <p><strong>Status:</strong> "
                            + escapeHtml(topic.getStatus() == null ? "" : topic.getStatus().name()) + "</p>");
                    out.println("            <p><strong>Created By User ID:</strong> "
                            + escapeHtml(
                                    topic.getCreatedByUserId() == null ? "" : String.valueOf(topic.getCreatedByUserId()))
                            + "</p>");
                    out.println("            <p><strong>Created At:</strong> " + escapeHtml(formatCreatedAt(topic)) + "</p>");

                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath + "/admin/topics?topicId=" + topic.getTopicId()
                            + "&mode=edit\">Edit Topic</a>");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath + "/admin/topics\">Back to Topics</a>");
                    out.println("            </div>");

                    out.println("            <h3 class=\"aira-subsection-title\">Workspaces</h3>");
                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println("              <thead>");
                    out.println("                <tr>");
                    out.println("                  <th>Workspace Name</th>");
                    out.println("                  <th>Start Date</th>");
                    out.println("                  <th>End Date</th>");
                    out.println("                  <th>Status</th>");
                    out.println("                  <th>Action</th>");
                    out.println("                </tr>");
                    out.println("              </thead>");
                    out.println("              <tbody>");
                    for (ConnectWorkspace workspace : workspaces) {
                        out.println("                <tr>");
                        out.println("                  <td>" + escapeHtml(orEmpty(workspace.getWorkspaceName())) + "</td>");
                        out.println("                  <td>" + escapeHtml(formatDate(workspace.getStartDate())) + "</td>");
                        out.println("                  <td>" + escapeHtml(formatDate(workspace.getEndDate())) + "</td>");
                        out.println("                  <td>"
                                + escapeHtml(workspace.getStatus() == null ? "" : workspace.getStatus().name()) + "</td>");
                        out.println("                  <td><a class=\"aira-inline-link\" href=\"" + contextPath + "/admin/workspaces?workspaceId="
                                + workspace.getWorkspaceId() + "\">View Workspace</a></td>");
                        out.println("                </tr>");
                    }
                    if (workspaces.isEmpty()) {
                        out.println("                <tr>");
                        out.println("                  <td colspan=\"5\">No workspaces associated with this topic yet.</td>");
                        out.println("                </tr>");
                    }
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");
                    out.println(
                            "            <p><a class=\"aira-inline-link\" href=\"" + contextPath + "/admin/workspaces?topicId=" + topic.getTopicId()
                                    + "&mode=new\">Add Workspace</a></p>");
                    out.println("          </section>");
                });
    }

    private void renderEditForm(HttpServletRequest request, HttpServletResponse response, IgTopic topic,
            String errorMessage, boolean createNew) throws IOException {
        String contextPath = request.getContextPath();

        AdminShellRenderer.render(request, response, (createNew ? "Create Topic" : "Edit Topic") + " - InteropHub",
                AdminSection.CONTENT, "/admin/topics", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">" + (createNew ? "Create IG Topic" : "Edit IG Topic") + "</h2>");

                    if (errorMessage != null && !errorMessage.isBlank()) {
                        out.println(
                                "            <div class=\"aira-alert aira-alert--danger\"><p><strong>Could not save:</strong> " + escapeHtml(errorMessage) + "</p></div>");
                    }

                    out.println("            <form class=\"aira-form\" action=\"" + contextPath
                            + "/admin/topics\" method=\"post\">");
                    if (!createNew && topic.getTopicId() != null) {
                        out.println("              <input type=\"hidden\" name=\"topicId\" value=\"" + topic.getTopicId()
                                + "\" />");
                    }

                    if (createNew) {
                        out.println("              <div class=\"aira-field\">");
                        out.println("                <label for=\"topicCode\">Topic Code (required)</label>");
                        out.println(
                                "                <input class=\"aira-input\" id=\"topicCode\" name=\"topicCode\" type=\"text\" required value=\""
                                        + escapeHtml(orEmpty(topic.getTopicCode())) + "\" />");
                        out.println("              </div>");
                    } else {
                        out.println(
                                "            <p><strong>Topic Code:</strong> " + escapeHtml(orEmpty(topic.getTopicCode()))
                                        + "</p>");
                    }

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"topicName\">Topic Name (required)</label>");
                    out.println("                <input class=\"aira-input\" id=\"topicName\" name=\"topicName\" type=\"text\" required value=\""
                            + escapeHtml(orEmpty(topic.getTopicName())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"description\">Description</label>");
                    out.println("                <textarea class=\"aira-textarea\" id=\"description\" name=\"description\" rows=\"5\">"
                            + escapeHtml(orEmpty(topic.getDescription())) + "</textarea>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"status\">Status (required)</label>");
                    out.println("                <select class=\"aira-select\" id=\"status\" name=\"status\" required>");
                    out.println(
                            "                  <option value=\"ACTIVE\"" + selectedStatus(topic, IgTopic.TopicStatus.ACTIVE)
                                    + ">ACTIVE</option>");
                    out.println("                  <option value=\"ARCHIVED\""
                            + selectedStatus(topic, IgTopic.TopicStatus.ARCHIVED)
                            + ">ARCHIVED</option>");
                    out.println("                </select>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-action-group\">");
                    out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Save</button>");
                    out.println("              </div>");
                    out.println("            </form>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath + "/admin/topics\">Back to Topics</a></p>");
                    out.println("          </section>");
                });
    }

    private String formatCreatedAt(IgTopic topic) {
        if (topic.getCreatedAt() == null) {
            return "";
        }
        return CREATED_AT_FORMATTER.format(topic.getCreatedAt());
    }

    private String formatDate(java.time.LocalDate value) {
        if (value == null) {
            return "";
        }
        return DATE_FORMATTER.format(value);
    }

    private String selectedStatus(IgTopic topic, IgTopic.TopicStatus status) {
        return topic.getStatus() == status ? " selected" : "";
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private IgTopic.TopicStatus parseStatus(String value) {
        try {
            return IgTopic.TopicStatus.valueOf(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Status must be either ACTIVE or ARCHIVED.");
        }
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
