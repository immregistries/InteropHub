package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.EsTopicSpaceMemberDao;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.EsTopicSpaceMember;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

public class AdminEsTopicSpaceServlet extends HttpServlet {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuthFlowService authFlowService;
    private final EsTopicSpaceDao topicSpaceDao;
    private final EsTopicSpaceMemberDao memberDao;
    private final EsTopicDao topicDao;
    private final EsMeetingDao meetingDao;
    private final UserDao userDao;

    public AdminEsTopicSpaceServlet() {
        this.authFlowService = new AuthFlowService();
        this.topicSpaceDao = new EsTopicSpaceDao();
        this.memberDao = new EsTopicSpaceMemberDao();
        this.topicDao = new EsTopicDao();
        this.meetingDao = new EsMeetingDao();
        this.userDao = new UserDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String mode = trimToNull(request.getParameter("mode"));
        String spaceIdRaw = trimToNull(request.getParameter("esTopicSpaceId"));

        if ("new".equalsIgnoreCase(mode)) {
            EsTopicSpace blank = new EsTopicSpace();
            blank.setDisplayOrder(0);
            blank.setIsActive(Boolean.TRUE);
            blank.setVisibility(EsTopicSpace.Visibility.PUBLIC);
            renderEditForm(response, contextPath, blank, true, null);
            return;
        }

        if (spaceIdRaw != null) {
            Long spaceId = parseId(spaceIdRaw);
            if (spaceId == null) {
                renderList(response, contextPath, "Invalid Topic Space identifier.");
                return;
            }
            EsTopicSpace topicSpace = topicSpaceDao.findById(spaceId).orElse(null);
            if (topicSpace == null) {
                renderList(response, contextPath, "Topic Space was not found.");
                return;
            }

            if ("edit".equalsIgnoreCase(mode)) {
                renderEditForm(response, contextPath, topicSpace, false, null);
                return;
            }

            if ("report".equalsIgnoreCase(mode)) {
                renderTopicNarrativeReport(response, contextPath, topicSpace);
                return;
            }

            String message = request.getParameter("saved") != null ? "Topic Space updated." : null;
            if (request.getParameter("memberSaved") != null) {
                message = "Membership updated.";
            }
            renderDetails(response, contextPath, topicSpace, message);
            return;
        }

        String message = request.getParameter("saved") != null ? "Topic Space created." : null;
        renderList(response, contextPath, message);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String action = trimToNull(request.getParameter("action"));
        if ("addMember".equalsIgnoreCase(action)) {
            handleAddMember(request, response, contextPath);
            return;
        }
        if ("removeMember".equalsIgnoreCase(action)) {
            handleRemoveMember(request, response, contextPath);
            return;
        }

        String spaceIdRaw = trimToNull(request.getParameter("esTopicSpaceId"));
        boolean creating = spaceIdRaw == null;

        EsTopicSpace topicSpace;
        if (creating) {
            topicSpace = new EsTopicSpace();
        } else {
            Long spaceId = parseId(spaceIdRaw);
            if (spaceId == null) {
                renderList(response, contextPath, "Invalid Topic Space identifier.");
                return;
            }
            topicSpace = topicSpaceDao.findById(spaceId).orElse(null);
            if (topicSpace == null) {
                renderList(response, contextPath, "Topic Space was not found.");
                return;
            }
        }

        String spaceCode = trimToNull(request.getParameter("spaceCode"));
        String spaceName = trimToNull(request.getParameter("spaceName"));
        String description = trimToNull(request.getParameter("description"));
        String displayOrderRaw = trimToNull(request.getParameter("displayOrder"));
        boolean isActive = request.getParameter("isActive") != null;
        String visibilityRaw = trimToNull(request.getParameter("visibility"));

        try {
            topicSpace.setSpaceName(required(spaceName, "Topic Space name"));
            topicSpace.setDescription(description);
            topicSpace.setDisplayOrder(parseRequiredInt(displayOrderRaw, "Display order"));
            topicSpace.setIsActive(isActive);

            if (creating) {
                String normalizedCode = normalizeCode(required(spaceCode, "Topic Space code"));
                if (topicSpaceDao.findBySpaceCode(normalizedCode).isPresent()) {
                    throw new IllegalArgumentException("Topic Space code is already in use.");
                }
                topicSpace.setSpaceCode(normalizedCode);
                topicSpace.setVisibility(parseVisibility(required(visibilityRaw, "Visibility")));
            }

            topicSpaceDao.saveOrUpdate(topicSpace);
            if (creating) {
                response.sendRedirect(contextPath + "/admin/es/topic-spaces?saved=1");
            } else {
                response.sendRedirect(contextPath + "/admin/es/topic-spaces?esTopicSpaceId="
                        + topicSpace.getEsTopicSpaceId() + "&saved=1");
            }
        } catch (Exception ex) {
            topicSpace.setSpaceCode(spaceCode);
            topicSpace.setSpaceName(spaceName);
            topicSpace.setDescription(description);
            topicSpace.setDisplayOrder(parseIntOrNull(displayOrderRaw));
            topicSpace.setIsActive(isActive);
            if (creating && visibilityRaw != null) {
                try {
                    topicSpace.setVisibility(parseVisibility(visibilityRaw));
                } catch (Exception ignored) {
                }
            }
            renderEditForm(response, contextPath, topicSpace, creating, ex.getMessage());
        }
    }

    private void handleAddMember(HttpServletRequest request, HttpServletResponse response, String contextPath)
            throws IOException {
        Long spaceId = parseId(trimToNull(request.getParameter("esTopicSpaceId")));
        if (spaceId == null) {
            renderList(response, contextPath, "Invalid Topic Space identifier.");
            return;
        }

        EsTopicSpace topicSpace = topicSpaceDao.findById(spaceId).orElse(null);
        if (topicSpace == null) {
            renderList(response, contextPath, "Topic Space was not found.");
            return;
        }

        if (topicSpace.getVisibility() != EsTopicSpace.Visibility.PRIVATE) {
            renderDetails(response, contextPath, topicSpace,
                    "Membership is managed only for private Topic Spaces.");
            return;
        }

        String email = trimToNull(request.getParameter("memberEmail"));
        String roleRaw = trimToNull(request.getParameter("memberRole"));
        try {
            String normalizedEmail = required(email, "Member email").toLowerCase(Locale.ROOT);
            User user = userDao.findByEmailNormalized(normalizedEmail).orElse(null);
            if (user == null) {
                throw new IllegalArgumentException("No user was found for that email address.");
            }

            if (memberDao.findBySpaceIdAndUserId(spaceId, user.getUserId()).isPresent()) {
                throw new IllegalArgumentException("That user is already a member of this Topic Space.");
            }

            EsTopicSpaceMember member = new EsTopicSpaceMember();
            member.setEsTopicSpaceId(spaceId);
            member.setUserId(user.getUserId());
            member.setRole(parseMemberRole(required(roleRaw, "Member role")));
            memberDao.saveOrUpdate(member);
            response.sendRedirect(contextPath + "/admin/es/topic-spaces?esTopicSpaceId=" + spaceId
                    + "&memberSaved=1");
        } catch (Exception ex) {
            renderDetails(response, contextPath, topicSpace, ex.getMessage());
        }
    }

    private void handleRemoveMember(HttpServletRequest request, HttpServletResponse response, String contextPath)
            throws IOException {
        Long spaceId = parseId(trimToNull(request.getParameter("esTopicSpaceId")));
        Long memberId = parseId(trimToNull(request.getParameter("esTopicSpaceMemberId")));
        if (spaceId == null || memberId == null) {
            renderList(response, contextPath, "Invalid membership identifier.");
            return;
        }

        EsTopicSpace topicSpace = topicSpaceDao.findById(spaceId).orElse(null);
        if (topicSpace == null) {
            renderList(response, contextPath, "Topic Space was not found.");
            return;
        }

        EsTopicSpaceMember member = memberDao.findById(memberId).orElse(null);
        if (member == null || !spaceId.equals(member.getEsTopicSpaceId())) {
            renderDetails(response, contextPath, topicSpace, "Membership record was not found.");
            return;
        }

        memberDao.deleteById(memberId);
        response.sendRedirect(contextPath + "/admin/es/topic-spaces?esTopicSpaceId=" + spaceId + "&memberSaved=1");
    }

    private void renderList(HttpServletResponse response, String contextPath, String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        List<EsTopicSpace> spaces = topicSpaceDao.findAllOrdered();

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Topic Spaces Admin - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Topic Spaces</h2>");
                panelOut.println("        <p>Create and maintain Topic Spaces and private-space membership.</p>");
                if (message != null && !message.isBlank()) {
                    panelOut.println("        <p><strong>" + escapeHtml(message) + "</strong></p>");
                }
                panelOut.println("        <p><a href=\"" + contextPath
                        + "/admin/es/topic-spaces?mode=new\">Add Topic Space</a></p>");

                panelOut.println("        <table class=\"data-table\">");
                panelOut.println("          <thead>");
                panelOut.println("            <tr>");
                panelOut.println("              <th>Name</th>");
                panelOut.println("              <th>Code</th>");
                panelOut.println("              <th>Visibility</th>");
                panelOut.println("              <th>Active</th>");
                panelOut.println("              <th>Topics</th>");
                panelOut.println("              <th>Meetings</th>");
                panelOut.println("              <th>Members</th>");
                panelOut.println("            </tr>");
                panelOut.println("          </thead>");
                panelOut.println("          <tbody>");
                for (EsTopicSpace space : spaces) {
                    long topicCount = topicDao.countBySpaceId(space.getEsTopicSpaceId());
                    long meetingCount = meetingDao.countBySpaceId(space.getEsTopicSpaceId());
                    long memberCount = memberDao.findAllBySpaceId(space.getEsTopicSpaceId()).size();
                    panelOut.println("            <tr>");
                    panelOut.println("              <td><a href=\"" + contextPath
                            + "/admin/es/topic-spaces?esTopicSpaceId=" + space.getEsTopicSpaceId()
                            + "\">" + escapeHtml(orEmpty(space.getSpaceName())) + "</a></td>");
                    panelOut.println("              <td>" + escapeHtml(orEmpty(space.getSpaceCode())) + "</td>");
                    panelOut.println("              <td>" + escapeHtml(space.getVisibility() == null
                            ? ""
                            : space.getVisibility().name()) + "</td>");
                    panelOut.println("              <td>" + (Boolean.TRUE.equals(space.getIsActive()) ? "Yes" : "No")
                            + "</td>");
                    panelOut.println("              <td>" + topicCount + "</td>");
                    panelOut.println("              <td>" + meetingCount + "</td>");
                    panelOut.println("              <td>" + memberCount + "</td>");
                    panelOut.println("            </tr>");
                }
                if (spaces.isEmpty()) {
                    panelOut.println("            <tr><td colspan=\"7\">No Topic Spaces found.</td></tr>");
                }
                panelOut.println("          </tbody>");
                panelOut.println("        </table>");
                panelOut.println(
                        "        <p><a href=\"" + contextPath + "/admin/es\">Back to Emerging Standards</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderDetails(HttpServletResponse response, String contextPath, EsTopicSpace topicSpace,
            String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        List<EsTopicSpaceMember> members = memberDao.findAllBySpaceId(topicSpace.getEsTopicSpaceId());
        Map<Long, User> usersById = loadUsersByMembers(members);

        long topicCount = topicDao.countBySpaceId(topicSpace.getEsTopicSpaceId());
        long meetingCount = meetingDao.countBySpaceId(topicSpace.getEsTopicSpaceId());

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Topic Space - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>" + escapeHtml(orEmpty(topicSpace.getSpaceName())) + "</h2>");
                if (message != null && !message.isBlank()) {
                    panelOut.println("        <p><strong>" + escapeHtml(message) + "</strong></p>");
                }
                panelOut.println("        <section class=\"panel\">");
                panelOut.println("          <p><strong>Code:</strong> " + escapeHtml(orEmpty(topicSpace.getSpaceCode()))
                        + "</p>");
                panelOut.println("          <p><strong>Visibility:</strong> "
                        + escapeHtml(topicSpace.getVisibility() == null ? "" : topicSpace.getVisibility().name())
                        + "</p>");
                panelOut.println(
                        "          <p><strong>Description:</strong> " + escapeHtml(orEmpty(topicSpace.getDescription()))
                                + "</p>");
                panelOut.println("          <p><strong>Display Order:</strong> "
                        + escapeHtml(String.valueOf(orZero(topicSpace.getDisplayOrder()))) + "</p>");
                panelOut.println("          <p><strong>Active:</strong> "
                        + (Boolean.TRUE.equals(topicSpace.getIsActive()) ? "Yes" : "No") + "</p>");
                panelOut.println(
                        "          <p><strong>Created:</strong> " + escapeHtml(formatDate(topicSpace.getCreatedAt()))
                                + "</p>");
                panelOut.println(
                        "          <p><strong>Updated:</strong> " + escapeHtml(formatDate(topicSpace.getUpdatedAt()))
                                + "</p>");
                panelOut.println("          <p><strong>Topics:</strong> " + topicCount + "</p>");
                panelOut.println("          <p><strong>Meetings:</strong> " + meetingCount + "</p>");
                panelOut.println("        </section>");

                panelOut.println("        <p><a href=\"" + contextPath + "/admin/es/topic-spaces?esTopicSpaceId="
                        + topicSpace.getEsTopicSpaceId() + "&mode=edit\">Edit Topic Space</a></p>");
                panelOut.println("        <p><a href=\"" + contextPath + "/admin/es/neighborhoods?esTopicSpaceId="
                        + topicSpace.getEsTopicSpaceId() + "\">Edit Neighborhoods</a></p>");
                panelOut.println("        <p><a href=\"" + contextPath + "/admin/es/stages?esTopicSpaceId="
                        + topicSpace.getEsTopicSpaceId() + "\">Edit Stages</a></p>");
                panelOut.println("        <p><a href=\"" + contextPath + "/admin/es/paths?esTopicSpaceId="
                        + topicSpace.getEsTopicSpaceId() + "\">Edit Advancement Paths</a></p>");
                panelOut.println("        <p><a href=\"" + buildTopicSpaceTopicsUrl(contextPath, topicSpace)
                        + "\">View Topic Space</a></p>");
                panelOut.println("        <p><a href=\"" + contextPath + "/admin/es/topics?space="
                        + topicSpace.getEsTopicSpaceId() + "\">Manage ES Topics for this Workspace</a></p>");
                panelOut.println("        <p><a href=\"" + contextPath
                        + "/admin/es/topic-spaces?esTopicSpaceId=" + topicSpace.getEsTopicSpaceId()
                        + "&mode=report\">Printable Topic Narrative Report</a></p>");

                if (topicSpace.getVisibility() == EsTopicSpace.Visibility.PRIVATE) {
                    panelOut.println("        <h3>Members</h3>");
                    panelOut.println("        <table class=\"data-table\">");
                    panelOut.println("          <thead><tr>");
                    panelOut.println(
                            "            <th>Email</th><th>Name</th><th>Role</th><th>Added</th><th>Action</th>");
                    panelOut.println("          </tr></thead>");
                    panelOut.println("          <tbody>");
                    for (EsTopicSpaceMember member : members) {
                        User user = usersById.get(member.getUserId());
                        panelOut.println("            <tr>");
                        panelOut.println("              <td>" + escapeHtml(user == null ? "" : orEmpty(user.getEmail()))
                                + "</td>");
                        panelOut.println(
                                "              <td>" + escapeHtml(user == null ? "" : orEmpty(user.getFullName()))
                                        + "</td>");
                        panelOut.println("              <td>" + escapeHtml(member.getRole() == null
                                ? ""
                                : member.getRole().name()) + "</td>");
                        panelOut.println(
                                "              <td>" + escapeHtml(formatDate(member.getCreatedAt())) + "</td>");
                        panelOut.println("              <td>");
                        panelOut.println("                <form method=\"post\" action=\"" + contextPath
                                + "/admin/es/topic-spaces\" style=\"display:inline;\">");
                        panelOut.println(
                                "                  <input type=\"hidden\" name=\"action\" value=\"removeMember\" />");
                        panelOut.println("                  <input type=\"hidden\" name=\"esTopicSpaceId\" value=\""
                                + topicSpace.getEsTopicSpaceId() + "\" />");
                        panelOut.println(
                                "                  <input type=\"hidden\" name=\"esTopicSpaceMemberId\" value=\""
                                        + member.getEsTopicSpaceMemberId() + "\" />");
                        panelOut.println("                  <button type=\"submit\">Remove</button>");
                        panelOut.println("                </form>");
                        panelOut.println("              </td>");
                        panelOut.println("            </tr>");
                    }
                    if (members.isEmpty()) {
                        panelOut.println("            <tr><td colspan=\"5\">No members added.</td></tr>");
                    }
                    panelOut.println("          </tbody>");
                    panelOut.println("        </table>");

                    panelOut.println("        <h3>Add Member</h3>");
                    panelOut.println("        <form class=\"login-form\" action=\"" + contextPath
                            + "/admin/es/topic-spaces\" method=\"post\">");
                    panelOut.println("          <input type=\"hidden\" name=\"action\" value=\"addMember\" />");
                    panelOut.println("          <input type=\"hidden\" name=\"esTopicSpaceId\" value=\""
                            + topicSpace.getEsTopicSpaceId() + "\" />");
                    panelOut.println("          <label for=\"memberEmail\">User Email (required)</label>");
                    panelOut.println(
                            "          <input id=\"memberEmail\" name=\"memberEmail\" type=\"email\" required />");
                    panelOut.println("          <label for=\"memberRole\">Role</label>");
                    panelOut.println("          <select id=\"memberRole\" name=\"memberRole\">");
                    panelOut.println("            <option value=\"MEMBER\">MEMBER</option>");
                    panelOut.println("            <option value=\"ADMIN\">ADMIN</option>");
                    panelOut.println("          </select>");
                    panelOut.println("          <button type=\"submit\">Add Member</button>");
                    panelOut.println("        </form>");
                } else {
                    panelOut.println("        <p>Membership is not used for public Topic Spaces.</p>");
                }

                panelOut.println("        <p><a href=\"" + contextPath
                        + "/admin/es/topic-spaces\">Back to Topic Spaces</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderEditForm(HttpServletResponse response, String contextPath, EsTopicSpace topicSpace,
            boolean creating, String errorMessage) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, (creating ? "Create" : "Edit") + " Topic Space - InteropHub", contextPath,
                    panelOut -> {
                        panelOut.println("      <section class=\"panel\">");
                        panelOut.println("        <h2>" + (creating ? "Create" : "Edit") + " Topic Space</h2>");
                        if (errorMessage != null && !errorMessage.isBlank()) {
                            panelOut.println("        <p><strong>Could not save:</strong> " + escapeHtml(errorMessage)
                                    + "</p>");
                        }

                        panelOut.println("        <form class=\"login-form\" action=\"" + contextPath
                                + "/admin/es/topic-spaces\" method=\"post\">");
                        if (!creating && topicSpace.getEsTopicSpaceId() != null) {
                            panelOut.println("          <input type=\"hidden\" name=\"esTopicSpaceId\" value=\""
                                    + topicSpace.getEsTopicSpaceId() + "\" />");
                        }

                        panelOut.println("          <label for=\"spaceCode\">Topic Space Code"
                                + (creating ? " (required)" : "") + "</label>");
                        if (creating) {
                            panelOut.println(
                                    "          <input id=\"spaceCode\" name=\"spaceCode\" type=\"text\" required"
                                            + " value=\"" + escapeHtml(orEmpty(topicSpace.getSpaceCode())) + "\" />");
                        } else {
                            panelOut.println("          <input id=\"spaceCode\" type=\"text\" disabled"
                                    + " value=\"" + escapeHtml(orEmpty(topicSpace.getSpaceCode())) + "\" />");
                        }

                        panelOut.println("          <label for=\"spaceName\">Topic Space Name (required)</label>");
                        panelOut.println("          <input id=\"spaceName\" name=\"spaceName\" type=\"text\" required"
                                + " value=\"" + escapeHtml(orEmpty(topicSpace.getSpaceName())) + "\" />");

                        panelOut.println("          <label for=\"description\">Description</label>");
                        panelOut.println("          <textarea id=\"description\" name=\"description\" rows=\"4\">"
                                + escapeHtml(orEmpty(topicSpace.getDescription())) + "</textarea>");

                        panelOut.println("          <label for=\"visibility\">Visibility"
                                + (creating ? " (required)" : "") + "</label>");
                        if (creating) {
                            panelOut.println("          <select id=\"visibility\" name=\"visibility\">");
                            panelOut.println("            <option value=\"PUBLIC\""
                                    + (topicSpace.getVisibility() == EsTopicSpace.Visibility.PUBLIC ? " selected" : "")
                                    + ">PUBLIC</option>");
                            panelOut.println("            <option value=\"PRIVATE\""
                                    + (topicSpace.getVisibility() == EsTopicSpace.Visibility.PRIVATE ? " selected" : "")
                                    + ">PRIVATE</option>");
                            panelOut.println("          </select>");
                        } else {
                            panelOut.println("          <input id=\"visibility\" type=\"text\" disabled value=\""
                                    + escapeHtml(topicSpace.getVisibility() == null
                                            ? ""
                                            : topicSpace.getVisibility().name())
                                    + "\" />");
                        }

                        panelOut.println("          <label for=\"displayOrder\">Display Order (required)</label>");
                        panelOut.println(
                                "          <input id=\"displayOrder\" name=\"displayOrder\" type=\"number\" required"
                                        + " value=\"" + escapeHtml(String.valueOf(orZero(topicSpace.getDisplayOrder())))
                                        + "\" />");

                        panelOut.println("          <label><input type=\"checkbox\" name=\"isActive\""
                                + (Boolean.TRUE.equals(topicSpace.getIsActive()) ? " checked" : "")
                                + " /> Active</label>");

                        panelOut.println("          <button type=\"submit\">Save</button>");
                        panelOut.println("        </form>");
                        panelOut.println("        <p><a href=\"" + contextPath + "/admin/es/topic-spaces"
                                + (creating ? "" : "?esTopicSpaceId=" + topicSpace.getEsTopicSpaceId())
                                + "\">Back</a></p>");
                        panelOut.println("      </section>");
                    });
        }
    }

    private void renderTopicNarrativeReport(HttpServletResponse response, String contextPath, EsTopicSpace topicSpace)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        List<EsTopic> topics = topicDao.findAllOrderByTopicName().stream()
                .filter(topic -> topicSpace.getEsTopicSpaceId() != null
                        && topicSpace.getEsTopicSpaceId().equals(topic.getEsTopicSpaceId()))
                .toList();

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Topic Narrative Report - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Topic Narrative Report</h2>");
                panelOut.println("        <p><strong>Topic Space:</strong> "
                        + escapeHtml(orEmpty(topicSpace.getSpaceName())) + "</p>");
                panelOut.println("        <p><strong>Generated:</strong> " + escapeHtml(formatDate(LocalDateTime.now()))
                        + "</p>");
                panelOut.println("        <p>This is an alphabetical, human-readable list of topics.</p>");
                panelOut.println(
                        "        <p><button type=\"button\" onclick=\"window.print()\">Print This Report</button></p>");

                if (topics.isEmpty()) {
                    panelOut.println("        <p>No topics were found for this Topic Space.</p>");
                } else {
                    for (EsTopic topic : topics) {
                        panelOut.println("        <section class=\"panel\">");
                        panelOut.println("          <h3>" + escapeHtml(orEmpty(topic.getTopicName())) + "</h3>");
                        if (topic.getStatus() != null && topic.getStatus() != EsTopic.EsTopicStatus.ACTIVE) {
                            panelOut.println("          <p><strong>Status:</strong> "
                                    + escapeHtml(topic.getStatus().name()) + "</p>");
                        }
                        panelOut.println("          <p>" + escapeHtml(orEmpty(topic.getDescription())) + "</p>");
                        panelOut.println("        </section>");
                    }
                }

                panelOut.println("        <p><a href=\"" + contextPath + "/admin/es/topic-spaces?esTopicSpaceId="
                        + topicSpace.getEsTopicSpaceId() + "\">Back to Topic Space</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private Map<Long, User> loadUsersByMembers(List<EsTopicSpaceMember> members) {
        List<Long> userIds = members.stream()
                .map(EsTopicSpaceMember::getUserId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> usersById = new LinkedHashMap<>();
        for (User user : userDao.findByIds(userIds)) {
            usersById.put(user.getUserId(), user);
        }
        return usersById;
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

    private void renderForbidden(HttpServletResponse response, String contextPath) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Access Denied - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Access Denied</h2>");
                panelOut.println("        <p>You must be an InteropHub admin to access Topic Space settings.</p>");
                panelOut.println("        <p><a href=\"" + contextPath + "/admin\">Return to Admin Home</a></p>");
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

    private int parseRequiredInt(String raw, String label) {
        Integer value = parseIntOrNull(raw);
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value;
    }

    private Integer parseIntOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value;
    }

    private String normalizeCode(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        String cleaned = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("Topic Space code must contain letters or numbers.");
        }
        return cleaned;
    }

    private EsTopicSpace.Visibility parseVisibility(String value) {
        try {
            return EsTopicSpace.Visibility.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Visibility must be PUBLIC or PRIVATE.");
        }
    }

    private EsTopicSpaceMember.MemberRole parseMemberRole(String value) {
        try {
            return EsTopicSpaceMember.MemberRole.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Member role must be MEMBER or ADMIN.");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "" : DATE_FORMAT.format(value);
    }

    private String buildTopicSpaceTopicsUrl(String contextPath, EsTopicSpace topicSpace) {
        String spaceCode = topicSpace == null ? null : trimToNull(topicSpace.getSpaceCode());
        return contextPath + "/spaces/"
                + URLEncoder.encode(spaceCode == null ? "" : spaceCode, StandardCharsets.UTF_8).replace("+", "%20")
                + "/topics";
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
