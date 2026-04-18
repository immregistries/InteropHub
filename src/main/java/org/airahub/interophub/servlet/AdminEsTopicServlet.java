package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

public class AdminEsTopicServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsTopicDao esTopicDao;
    private final EsTopicMeetingDao esTopicMeetingDao;

    public AdminEsTopicServlet() {
        this.authFlowService = new AuthFlowService();
        this.esTopicDao = new EsTopicDao();
        this.esTopicMeetingDao = new EsTopicMeetingDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String mode = trimToNull(request.getParameter("mode"));
        String topicIdRaw = trimToNull(request.getParameter("esTopicId"));

        if (topicIdRaw != null) {
            Long topicId = parseId(topicIdRaw);
            if (topicId == null) {
                renderList(response, contextPath, "Invalid topic identifier.");
                return;
            }

            EsTopic topic = esTopicDao.findById(topicId).orElse(null);
            if (topic == null) {
                renderList(response, contextPath, "Topic entry was not found.");
                return;
            }

            EsTopicMeeting meeting = esTopicMeetingDao.findByTopicId(topicId).orElse(null);
            if ("edit".equalsIgnoreCase(mode)) {
                renderEditForm(response, contextPath, topic, meeting, null);
                return;
            }

            renderDetails(response, contextPath, topic, meeting);
            return;
        }

        String message = request.getParameter("saved") != null ? "Topic settings saved." : null;
        renderList(response, contextPath, message);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String topicIdRaw = trimToNull(request.getParameter("esTopicId"));
        Long topicId = parseId(topicIdRaw);
        if (topicId == null) {
            renderList(response, contextPath, "Invalid topic identifier.");
            return;
        }

        EsTopic topic = esTopicDao.findById(topicId).orElse(null);
        if (topic == null) {
            renderList(response, contextPath, "Topic entry was not found.");
            return;
        }

        EsTopicMeeting meeting = esTopicMeetingDao.findByTopicId(topicId).orElse(null);

        String topicName = trimToNull(request.getParameter("topicName"));
        String description = trimToNull(request.getParameter("description"));
        String neighborhood = trimToNull(request.getParameter("neighborhood"));
        String priorityIisRaw = trimToNull(request.getParameter("priorityIis"));
        String priorityEhrRaw = trimToNull(request.getParameter("priorityEhr"));
        String priorityCdcRaw = trimToNull(request.getParameter("priorityCdc"));
        String stage = trimToNull(request.getParameter("stage"));
        String policyStatus = trimToNull(request.getParameter("policyStatus"));
        String topicType = trimToNull(request.getParameter("topicType"));
        String statusRaw = trimToNull(request.getParameter("status"));

        boolean meetingEnabled = request.getParameter("meetingEnabled") != null;
        String meetingName = trimToNull(request.getParameter("meetingName"));
        String meetingDescription = trimToNull(request.getParameter("meetingDescription"));
        boolean meetingRequiresApproval = request.getParameter("meetingRequiresApproval") != null;

        try {
            topic.setTopicName(required(topicName, "Topic name"));
            topic.setDescription(description);
            topic.setNeighborhood(neighborhood);
            topic.setPriorityIis(parseRequiredInt(priorityIisRaw, "Priority IIS"));
            topic.setPriorityEhr(parseRequiredInt(priorityEhrRaw, "Priority EHR"));
            topic.setPriorityCdc(parseRequiredInt(priorityCdcRaw, "Priority CDC"));
            topic.setStage(stage);
            topic.setPolicyStatus(policyStatus);
            topic.setTopicType(topicType);
            topic.setStatus(parseStatus(required(statusRaw, "Status")));

            esTopicDao.saveOrUpdate(topic);

            if (meetingEnabled) {
                if (meeting == null) {
                    meeting = new EsTopicMeeting();
                    meeting.setEsTopicId(topic.getEsTopicId());
                }
                meeting.setMeetingName(required(meetingName, "Meeting name"));
                meeting.setMeetingDescription(meetingDescription);
                meeting.setJoinRequiresApproval(meetingRequiresApproval);
                meeting.setStatus(EsTopicMeeting.MeetingStatus.ACTIVE);
                meeting.setDisabledAt(null);
                meeting.setDisabledByUserId(null);
                esTopicMeetingDao.saveOrUpdate(meeting);
            } else if (meeting != null) {
                esTopicMeetingDao.disableMeeting(meeting, adminUser.get().getUserId());
            }

            response.sendRedirect(contextPath + "/admin/es/topics?saved=1");
        } catch (Exception ex) {
            topic.setTopicName(topicName);
            topic.setDescription(description);
            topic.setNeighborhood(neighborhood);
            topic.setStage(stage);
            topic.setPolicyStatus(policyStatus);
            topic.setTopicType(topicType);
            if (priorityIisRaw != null) {
                topic.setPriorityIis(parseIntOrNull(priorityIisRaw));
            }
            if (priorityEhrRaw != null) {
                topic.setPriorityEhr(parseIntOrNull(priorityEhrRaw));
            }
            if (priorityCdcRaw != null) {
                topic.setPriorityCdc(parseIntOrNull(priorityCdcRaw));
            }
            if (statusRaw != null) {
                try {
                    topic.setStatus(parseStatus(statusRaw));
                } catch (Exception ignored) {
                    // Keep existing status if parse fails to avoid masking original validation
                    // error.
                }
            }

            if (meetingEnabled) {
                if (meeting == null) {
                    meeting = new EsTopicMeeting();
                    meeting.setEsTopicId(topic.getEsTopicId());
                }
                meeting.setMeetingName(meetingName);
                meeting.setMeetingDescription(meetingDescription);
                meeting.setJoinRequiresApproval(meetingRequiresApproval);
                meeting.setStatus(EsTopicMeeting.MeetingStatus.ACTIVE);
            }

            renderEditForm(response, contextPath, topic, meetingEnabled ? meeting : null, ex.getMessage());
        }
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

    private void renderList(HttpServletResponse response, String contextPath, String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        List<EsTopic> topics = esTopicDao.findAllOrderByTopicName();

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>ES Topics Admin - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>ES Topics</h1>");
            out.println("    <p>View and manage Emerging Standards topics.</p>");
            if (message != null && !message.isBlank()) {
                out.println("    <p><strong>" + escapeHtml(message) + "</strong></p>");
            }

            out.println("    <table class=\"data-table\">");
            out.println("      <thead>");
            out.println("        <tr>");
            out.println("          <th>Topic Name</th>");
            out.println("          <th>Stage</th>");
            out.println("          <th>Status</th>");
            out.println("        </tr>");
            out.println("      </thead>");
            out.println("      <tbody>");
            for (EsTopic topic : topics) {
                out.println("        <tr>");
                out.println(
                        "          <td><a href=\"" + contextPath + "/admin/es/topics?esTopicId=" + topic.getEsTopicId()
                                + "\">" + escapeHtml(orEmpty(topic.getTopicName())) + "</a></td>");
                out.println("          <td>" + escapeHtml(orEmpty(topic.getStage())) + "</td>");
                out.println("          <td>" + escapeHtml(topic.getStatus() == null ? "" : topic.getStatus().name())
                        + "</td>");
                out.println("        </tr>");
            }
            if (topics.isEmpty()) {
                out.println("        <tr>");
                out.println("          <td colspan=\"3\">No ES topics found.</td>");
                out.println("        </tr>");
            }
            out.println("      </tbody>");
            out.println("    </table>");

            out.println("    <p><a href=\"" + contextPath + "/admin/es\">Back to Emerging Standards</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderDetails(HttpServletResponse response, String contextPath, EsTopic topic, EsTopicMeeting meeting)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        boolean meetingEnabled = meeting != null && meeting.getStatus() == EsTopicMeeting.MeetingStatus.ACTIVE;

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>ES Topic Details - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>ES Topic Details</h1>");
            out.println("    <p>This view is read-only. Use Edit to change this topic.</p>");

            out.println("    <section class=\"panel\">");
            out.println("      <p><strong>Topic Code:</strong> " + escapeHtml(orEmpty(topic.getTopicCode())) + "</p>");
            out.println("      <p><strong>Topic Name:</strong> " + escapeHtml(orEmpty(topic.getTopicName())) + "</p>");
            out.println(
                    "      <p><strong>Description:</strong> " + escapeHtml(orEmpty(topic.getDescription())) + "</p>");
            out.println(
                    "      <p><strong>Neighborhood:</strong> " + escapeHtml(orEmpty(topic.getNeighborhood())) + "</p>");
            out.println("      <p><strong>Priority IIS:</strong> " + escapeHtml(String.valueOf(topic.getPriorityIis()))
                    + "</p>");
            out.println("      <p><strong>Priority EHR:</strong> " + escapeHtml(String.valueOf(topic.getPriorityEhr()))
                    + "</p>");
            out.println("      <p><strong>Priority CDC:</strong> " + escapeHtml(String.valueOf(topic.getPriorityCdc()))
                    + "</p>");
            out.println("      <p><strong>Stage:</strong> " + escapeHtml(orEmpty(topic.getStage())) + "</p>");
            out.println("      <p><strong>Policy Status:</strong> " + escapeHtml(orEmpty(topic.getPolicyStatus()))
                    + "</p>");
            out.println("      <p><strong>Topic Type:</strong> " + escapeHtml(orEmpty(topic.getTopicType())) + "</p>");
            out.println("      <p><strong>Status:</strong> "
                    + escapeHtml(topic.getStatus() == null ? "" : topic.getStatus().name()) + "</p>");
            out.println("      <p><strong>Created By User ID:</strong> "
                    + escapeHtml(topic.getCreatedByUserId() == null ? "" : String.valueOf(topic.getCreatedByUserId()))
                    + "</p>");
            out.println("      <p><strong>Meeting Enabled:</strong> " + (meetingEnabled ? "Yes" : "No") + "</p>");
            if (meeting != null) {
                out.println("      <p><strong>Meeting Name:</strong> " + escapeHtml(orEmpty(meeting.getMeetingName()))
                        + "</p>");
                out.println("      <p><strong>Meeting Description:</strong> "
                        + escapeHtml(orEmpty(meeting.getMeetingDescription())) + "</p>");
                out.println("      <p><strong>Join Requires Approval:</strong> "
                        + (Boolean.TRUE.equals(meeting.getJoinRequiresApproval()) ? "Yes" : "No") + "</p>");
                out.println("      <p><strong>Meeting Status:</strong> "
                        + escapeHtml(meeting.getStatus() == null ? "" : meeting.getStatus().name()) + "</p>");
            }
            out.println("    </section>");

            out.println("    <p><a href=\"" + contextPath + "/admin/es/topics?esTopicId=" + topic.getEsTopicId()
                    + "&mode=edit\">Edit Topic</a></p>");
            out.println("    <p><a href=\"" + contextPath + "/admin/es/topics\">Back to Topics</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderEditForm(HttpServletResponse response, String contextPath, EsTopic topic, EsTopicMeeting meeting,
            String errorMessage) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        boolean meetingEnabled = meeting != null && meeting.getStatus() == EsTopicMeeting.MeetingStatus.ACTIVE;

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Edit ES Topic - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Edit ES Topic</h1>");

            if (errorMessage != null && !errorMessage.isBlank()) {
                out.println("    <p><strong>Could not save:</strong> " + escapeHtml(errorMessage) + "</p>");
            }

            out.println(
                    "    <form class=\"login-form\" action=\"" + contextPath + "/admin/es/topics\" method=\"post\">");
            out.println("      <input type=\"hidden\" name=\"esTopicId\" value=\"" + topic.getEsTopicId() + "\" />");

            out.println("      <p><strong>Topic Code:</strong> " + escapeHtml(orEmpty(topic.getTopicCode())) + "</p>");

            out.println("      <label for=\"topicName\">Topic Name (required)</label>");
            out.println("      <input id=\"topicName\" name=\"topicName\" type=\"text\" required value=\""
                    + escapeHtml(orEmpty(topic.getTopicName())) + "\" />");

            out.println("      <label for=\"description\">Description</label>");
            out.println("      <textarea id=\"description\" name=\"description\" rows=\"5\">"
                    + escapeHtml(orEmpty(topic.getDescription())) + "</textarea>");

            out.println("      <label for=\"neighborhood\">Neighborhood</label>");
            out.println("      <input id=\"neighborhood\" name=\"neighborhood\" type=\"text\" value=\""
                    + escapeHtml(orEmpty(topic.getNeighborhood())) + "\" />");

            out.println("      <label for=\"priorityIis\">Priority IIS (required)</label>");
            out.println("      <input id=\"priorityIis\" name=\"priorityIis\" type=\"number\" required value=\""
                    + escapeHtml(String.valueOf(topic.getPriorityIis() == null ? 0 : topic.getPriorityIis()))
                    + "\" />");

            out.println("      <label for=\"priorityEhr\">Priority EHR (required)</label>");
            out.println("      <input id=\"priorityEhr\" name=\"priorityEhr\" type=\"number\" required value=\""
                    + escapeHtml(String.valueOf(topic.getPriorityEhr() == null ? 0 : topic.getPriorityEhr()))
                    + "\" />");

            out.println("      <label for=\"priorityCdc\">Priority CDC (required)</label>");
            out.println("      <input id=\"priorityCdc\" name=\"priorityCdc\" type=\"number\" required value=\""
                    + escapeHtml(String.valueOf(topic.getPriorityCdc() == null ? 0 : topic.getPriorityCdc()))
                    + "\" />");

            out.println("      <label for=\"stage\">Stage</label>");
            out.println("      <input id=\"stage\" name=\"stage\" type=\"text\" value=\""
                    + escapeHtml(orEmpty(topic.getStage())) + "\" />");

            out.println("      <label for=\"policyStatus\">Policy Status</label>");
            out.println("      <input id=\"policyStatus\" name=\"policyStatus\" type=\"text\" value=\""
                    + escapeHtml(orEmpty(topic.getPolicyStatus())) + "\" />");

            out.println("      <label for=\"topicType\">Topic Type</label>");
            out.println("      <input id=\"topicType\" name=\"topicType\" type=\"text\" value=\""
                    + escapeHtml(orEmpty(topic.getTopicType())) + "\" />");

            out.println("      <label for=\"status\">Status (required)</label>");
            out.println("      <select id=\"status\" name=\"status\" required>");
            out.println("        <option value=\"ACTIVE\"" + selectedStatus(topic, EsTopic.EsTopicStatus.ACTIVE)
                    + ">ACTIVE</option>");
            out.println("        <option value=\"RETIRED\"" + selectedStatus(topic, EsTopic.EsTopicStatus.RETIRED)
                    + ">RETIRED</option>");
            out.println("        <option value=\"ARCHIVED\"" + selectedStatus(topic, EsTopic.EsTopicStatus.ARCHIVED)
                    + ">ARCHIVED</option>");
            out.println("      </select>");

            out.println("      <h2>Meeting Configuration</h2>");
            out.println("      <label><input type=\"checkbox\" name=\"meetingEnabled\""
                    + (meetingEnabled ? " checked" : "")
                    + " /> Enable Meeting Support</label>");

            out.println("      <label for=\"meetingName\">Meeting Name (required when enabled)</label>");
            out.println("      <input id=\"meetingName\" name=\"meetingName\" type=\"text\" value=\""
                    + escapeHtml(orEmpty(meeting == null ? null : meeting.getMeetingName())) + "\" />");

            out.println("      <label for=\"meetingDescription\">Meeting Description</label>");
            out.println("      <textarea id=\"meetingDescription\" name=\"meetingDescription\" rows=\"4\">"
                    + escapeHtml(orEmpty(meeting == null ? null : meeting.getMeetingDescription())) + "</textarea>");

            out.println("      <label><input type=\"checkbox\" name=\"meetingRequiresApproval\""
                    + (meeting != null && Boolean.TRUE.equals(meeting.getJoinRequiresApproval()) ? " checked" : "")
                    + " /> Join Requires Approval</label>");

            out.println("      <button type=\"submit\">Save</button>");
            out.println("    </form>");
            out.println("    <p><a href=\"" + contextPath + "/admin/es/topics\">Back to Topics</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderForbidden(HttpServletResponse response, String contextPath) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Access Denied - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Access Denied</h1>");
            out.println("    <p>You must be an InteropHub admin to access ES topic settings.</p>");
            out.println("    <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private String selectedStatus(EsTopic topic, EsTopic.EsTopicStatus status) {
        return topic.getStatus() == status ? " selected" : "";
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

    private EsTopic.EsTopicStatus parseStatus(String value) {
        try {
            return EsTopic.EsTopicStatus.valueOf(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Status must be ACTIVE, RETIRED, or ARCHIVED.");
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
