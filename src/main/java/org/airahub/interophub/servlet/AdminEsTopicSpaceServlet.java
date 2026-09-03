package org.airahub.interophub.servlet;

import java.io.IOException;
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

public class AdminEsTopicSpaceServlet extends HttpServlet {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String ACTIVE_HREF = "/admin/es/topic-spaces";

    private final EsTopicSpaceDao topicSpaceDao;
    private final EsTopicSpaceMemberDao memberDao;
    private final EsTopicDao topicDao;
    private final EsMeetingDao meetingDao;
    private final UserDao userDao;

    public AdminEsTopicSpaceServlet() {
        this.topicSpaceDao = new EsTopicSpaceDao();
        this.memberDao = new EsTopicSpaceMemberDao();
        this.topicDao = new EsTopicDao();
        this.meetingDao = new EsMeetingDao();
        this.userDao = new UserDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String mode = trimToNull(request.getParameter("mode"));
        String spaceIdRaw = trimToNull(request.getParameter("esTopicSpaceId"));

        if ("new".equalsIgnoreCase(mode)) {
            EsTopicSpace blank = new EsTopicSpace();
            blank.setDisplayOrder(0);
            blank.setIsActive(Boolean.TRUE);
            blank.setVisibility(EsTopicSpace.Visibility.PUBLIC);
            renderEditForm(request, response, blank, true, null);
            return;
        }

        if (spaceIdRaw != null) {
            Long spaceId = parseId(spaceIdRaw);
            if (spaceId == null) {
                renderList(request, response, "Invalid Topic Space identifier.");
                return;
            }
            EsTopicSpace topicSpace = topicSpaceDao.findById(spaceId).orElse(null);
            if (topicSpace == null) {
                renderList(request, response, "Topic Space was not found.");
                return;
            }

            if ("edit".equalsIgnoreCase(mode)) {
                renderEditForm(request, response, topicSpace, false, null);
                return;
            }

            if ("report".equalsIgnoreCase(mode)) {
                renderTopicNarrativeReport(request, response, topicSpace);
                return;
            }

            String message = request.getParameter("saved") != null ? "Topic Space updated." : null;
            if (request.getParameter("memberSaved") != null) {
                message = "Membership updated.";
            }
            renderDetails(request, response, topicSpace, message);
            return;
        }

        String message = request.getParameter("saved") != null ? "Topic Space created." : null;
        renderList(request, response, message);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
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
                renderList(request, response, "Invalid Topic Space identifier.");
                return;
            }
            topicSpace = topicSpaceDao.findById(spaceId).orElse(null);
            if (topicSpace == null) {
                renderList(request, response, "Topic Space was not found.");
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
            renderEditForm(request, response, topicSpace, creating, ex.getMessage());
        }
    }

    private void handleAddMember(HttpServletRequest request, HttpServletResponse response, String contextPath)
            throws IOException {
        Long spaceId = parseId(trimToNull(request.getParameter("esTopicSpaceId")));
        if (spaceId == null) {
            renderList(request, response, "Invalid Topic Space identifier.");
            return;
        }

        EsTopicSpace topicSpace = topicSpaceDao.findById(spaceId).orElse(null);
        if (topicSpace == null) {
            renderList(request, response, "Topic Space was not found.");
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
            renderDetails(request, response, topicSpace, ex.getMessage());
        }
    }

    private void handleRemoveMember(HttpServletRequest request, HttpServletResponse response, String contextPath)
            throws IOException {
        Long spaceId = parseId(trimToNull(request.getParameter("esTopicSpaceId")));
        Long memberId = parseId(trimToNull(request.getParameter("esTopicSpaceMemberId")));
        if (spaceId == null || memberId == null) {
            renderList(request, response, "Invalid membership identifier.");
            return;
        }

        EsTopicSpace topicSpace = topicSpaceDao.findById(spaceId).orElse(null);
        if (topicSpace == null) {
            renderList(request, response, "Topic Space was not found.");
            return;
        }

        EsTopicSpaceMember member = memberDao.findById(memberId).orElse(null);
        if (member == null || !spaceId.equals(member.getEsTopicSpaceId())) {
            renderDetails(request, response, topicSpace, "Membership record was not found.");
            return;
        }

        memberDao.deleteById(memberId);
        response.sendRedirect(contextPath + "/admin/es/topic-spaces?esTopicSpaceId=" + spaceId + "&memberSaved=1");
    }

    private void renderList(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        String contextPath = request.getContextPath();
        List<EsTopicSpace> spaces = topicSpaceDao.findAllOrdered();

        AdminShellRenderer.render(request, response, "Topic Spaces Admin - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Topic Spaces</h2>");
                    out.println(
                            "            <p class=\"aira-meta\">Create and maintain Topic Spaces and private-space membership.</p>");
                    if (message != null && !message.isBlank()) {
                        out.println("            <div class=\"aira-alert aira-alert--success\"><p>"
                                + escapeHtml(message) + "</p></div>");
                    }
                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--primary\" href=\"" + contextPath
                            + "/admin/es/topic-spaces?mode=new\">Add Topic Space</a>");
                    out.println("            </div>");

                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println("              <thead>");
                    out.println("                <tr>");
                    out.println("                  <th>Name</th>");
                    out.println("                  <th>Code</th>");
                    out.println("                  <th>Visibility</th>");
                    out.println("                  <th>Active</th>");
                    out.println("                  <th>Topics</th>");
                    out.println("                  <th>Meetings</th>");
                    out.println("                  <th>Members</th>");
                    out.println("                </tr>");
                    out.println("              </thead>");
                    out.println("              <tbody>");
                    for (EsTopicSpace space : spaces) {
                        long topicCount = topicDao.countBySpaceId(space.getEsTopicSpaceId());
                        long meetingCount = meetingDao.countBySpaceId(space.getEsTopicSpaceId());
                        long memberCount = memberDao.findAllBySpaceId(space.getEsTopicSpaceId()).size();
                        out.println("                <tr>");
                        out.println("                  <td><a class=\"aira-inline-link\" href=\"" + contextPath
                                + "/admin/es/topic-spaces?esTopicSpaceId=" + space.getEsTopicSpaceId()
                                + "\">" + escapeHtml(orEmpty(space.getSpaceName())) + "</a></td>");
                        out.println(
                                "                  <td>" + escapeHtml(orEmpty(space.getSpaceCode())) + "</td>");
                        out.println("                  <td>" + escapeHtml(space.getVisibility() == null
                                ? ""
                                : space.getVisibility().name()) + "</td>");
                        out.println("                  <td>" + activeBadge(Boolean.TRUE.equals(space.getIsActive()))
                                + "</td>");
                        out.println("                  <td>" + topicCount + "</td>");
                        out.println("                  <td>" + meetingCount + "</td>");
                        out.println("                  <td>" + memberCount + "</td>");
                        out.println("                </tr>");
                    }
                    if (spaces.isEmpty()) {
                        out.println("                <tr><td colspan=\"7\">No Topic Spaces found.</td></tr>");
                    }
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");
                    out.println("          </section>");
                });
    }

    private void renderDetails(HttpServletRequest request, HttpServletResponse response, EsTopicSpace topicSpace,
            String message) throws IOException {
        String contextPath = request.getContextPath();
        List<EsTopicSpaceMember> members = memberDao.findAllBySpaceId(topicSpace.getEsTopicSpaceId());
        Map<Long, User> usersById = loadUsersByMembers(members);

        long topicCount = topicDao.countBySpaceId(topicSpace.getEsTopicSpaceId());
        long meetingCount = meetingDao.countBySpaceId(topicSpace.getEsTopicSpaceId());

        AdminShellRenderer.render(request, response, "Topic Space - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">"
                            + escapeHtml(orEmpty(topicSpace.getSpaceName())) + "</h2>");
                    if (message != null && !message.isBlank()) {
                        out.println("            <div class=\"aira-alert aira-alert--success\"><p>"
                                + escapeHtml(message) + "</p></div>");
                    }
                    out.println("            <section class=\"aira-panel\">");
                    out.println("              <p><strong>Code:</strong> "
                            + escapeHtml(orEmpty(topicSpace.getSpaceCode())) + "</p>");
                    out.println("              <p><strong>Visibility:</strong> "
                            + escapeHtml(topicSpace.getVisibility() == null ? "" : topicSpace.getVisibility().name())
                            + "</p>");
                    out.println("              <p><strong>Description:</strong> "
                            + escapeHtml(orEmpty(topicSpace.getDescription())) + "</p>");
                    out.println("              <p><strong>Display Order:</strong> "
                            + escapeHtml(String.valueOf(orZero(topicSpace.getDisplayOrder()))) + "</p>");
                    out.println("              <p><strong>Active:</strong> "
                            + activeBadge(Boolean.TRUE.equals(topicSpace.getIsActive())) + "</p>");
                    out.println("              <p><strong>Created:</strong> "
                            + escapeHtml(formatDate(topicSpace.getCreatedAt())) + "</p>");
                    out.println("              <p><strong>Updated:</strong> "
                            + escapeHtml(formatDate(topicSpace.getUpdatedAt())) + "</p>");
                    out.println("              <p><strong>Topics:</strong> " + topicCount + "</p>");
                    out.println("              <p><strong>Meetings:</strong> " + meetingCount + "</p>");
                    out.println("            </section>");

                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/topic-spaces?esTopicSpaceId=" + topicSpace.getEsTopicSpaceId()
                            + "&mode=edit\">Edit Topic Space</a>");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/neighborhoods?esTopicSpaceId=" + topicSpace.getEsTopicSpaceId()
                            + "\">Edit Neighborhoods</a>");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/stages?esTopicSpaceId=" + topicSpace.getEsTopicSpaceId()
                            + "\">Edit Stages</a>");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/paths?esTopicSpaceId=" + topicSpace.getEsTopicSpaceId()
                            + "\">Edit Advancement Paths</a>");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/dandelion-sync?esTopicSpaceId=" + topicSpace.getEsTopicSpaceId()
                            + "\">Dandelion Sync</a>");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\""
                            + buildTopicSpaceTopicsUrl(contextPath, topicSpace) + "\">View Topic Space</a>");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/topics?space=" + topicSpace.getEsTopicSpaceId()
                            + "\">Manage ES Topics for this Workspace</a>");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/topic-spaces?esTopicSpaceId=" + topicSpace.getEsTopicSpaceId()
                            + "&mode=report\">Printable Topic Narrative Report</a>");
                    out.println("            </div>");

                    {
                        out.println("            <h3 class=\"aira-subsection-title\">Members</h3>");
                        out.println(
                                "            <p class=\"aira-meta\">MEMBER grants access to this Topic Space when it's private (no effect on public spaces)."
                                        + " ADMIN can manage this space's content and topics, and is notified by email about a topic's"
                                        + " activity when that topic itself has no dedicated champion/support contact.</p>");
                        out.println("            <div class=\"aira-table-wrap\">");
                        out.println("            <table class=\"aira-table\">");
                        out.println("              <thead><tr>");
                        out.println(
                                "                <th>Email</th><th>Name</th><th>Role</th><th>Added</th><th>Action</th>");
                        out.println("              </tr></thead>");
                        out.println("              <tbody>");
                        for (EsTopicSpaceMember member : members) {
                            User user = usersById.get(member.getUserId());
                            out.println("                <tr>");
                            out.println("                  <td>"
                                    + escapeHtml(user == null ? "" : orEmpty(user.getEmail())) + "</td>");
                            out.println("                  <td>"
                                    + escapeHtml(user == null ? "" : orEmpty(user.getFullName())) + "</td>");
                            out.println("                  <td>" + escapeHtml(member.getRole() == null
                                    ? ""
                                    : member.getRole().name()) + "</td>");
                            out.println(
                                    "                  <td>" + escapeHtml(formatDate(member.getCreatedAt())) + "</td>");
                            out.println("                  <td>");
                            out.println("                    <form method=\"post\" action=\"" + contextPath
                                    + "/admin/es/topic-spaces\">");
                            out.println(
                                    "                      <input type=\"hidden\" name=\"action\" value=\"removeMember\" />");
                            out.println("                      <input type=\"hidden\" name=\"esTopicSpaceId\" value=\""
                                    + topicSpace.getEsTopicSpaceId() + "\" />");
                            out.println(
                                    "                      <input type=\"hidden\" name=\"esTopicSpaceMemberId\" value=\""
                                            + member.getEsTopicSpaceMemberId() + "\" />");
                            out.println(
                                    "                      <button class=\"aira-button aira-button--danger aira-button--small\" type=\"submit\">Remove</button>");
                            out.println("                    </form>");
                            out.println("                  </td>");
                            out.println("                </tr>");
                        }
                        if (members.isEmpty()) {
                            out.println("                <tr><td colspan=\"5\">No members added.</td></tr>");
                        }
                        out.println("              </tbody>");
                        out.println("            </table>");
                        out.println("            </div>");

                        out.println("            <h3 class=\"aira-subsection-title\">Add Member</h3>");
                        out.println("            <form class=\"aira-form\" action=\"" + contextPath
                                + "/admin/es/topic-spaces\" method=\"post\">");
                        out.println("              <input type=\"hidden\" name=\"action\" value=\"addMember\" />");
                        out.println("              <input type=\"hidden\" name=\"esTopicSpaceId\" value=\""
                                + topicSpace.getEsTopicSpaceId() + "\" />");
                        out.println("              <div class=\"aira-field\">");
                        out.println("                <label for=\"memberEmail\">User Email (required)</label>");
                        out.println(
                                "                <input class=\"aira-input\" id=\"memberEmail\" name=\"memberEmail\" type=\"email\" required />");
                        out.println("              </div>");
                        out.println("              <div class=\"aira-field\">");
                        out.println("                <label for=\"memberRole\">Role</label>");
                        out.println(
                                "                <select class=\"aira-select\" id=\"memberRole\" name=\"memberRole\">");
                        out.println("                  <option value=\"MEMBER\">MEMBER</option>");
                        out.println("                  <option value=\"ADMIN\">ADMIN</option>");
                        out.println("                </select>");
                        out.println("              </div>");
                        out.println("              <div class=\"aira-action-group\">");
                        out.println(
                                "                <button class=\"aira-button aira-button--primary\" type=\"submit\">Add Member</button>");
                        out.println("              </div>");
                        out.println("            </form>");
                    }

                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/topic-spaces\">Back to Topic Spaces</a></p>");
                    out.println("          </section>");
                });
    }

    private void renderEditForm(HttpServletRequest request, HttpServletResponse response, EsTopicSpace topicSpace,
            boolean creating, String errorMessage) throws IOException {
        String contextPath = request.getContextPath();

        AdminShellRenderer.render(request, response, (creating ? "Create" : "Edit") + " Topic Space - InteropHub",
                AdminSection.TOPIC_SPACES, ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">" + (creating ? "Create" : "Edit")
                            + " Topic Space</h2>");
                    if (errorMessage != null && !errorMessage.isBlank()) {
                        out.println(
                                "            <div class=\"aira-alert aira-alert--danger\"><p><strong>Could not save:</strong> "
                                        + escapeHtml(errorMessage) + "</p></div>");
                    }

                    out.println("            <form class=\"aira-form\" action=\"" + contextPath
                            + "/admin/es/topic-spaces\" method=\"post\">");
                    if (!creating && topicSpace.getEsTopicSpaceId() != null) {
                        out.println("              <input type=\"hidden\" name=\"esTopicSpaceId\" value=\""
                                + topicSpace.getEsTopicSpaceId() + "\" />");
                    }

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"spaceCode\">Topic Space Code"
                            + (creating ? " (required)" : "") + "</label>");
                    if (creating) {
                        out.println(
                                "                <input class=\"aira-input\" id=\"spaceCode\" name=\"spaceCode\" type=\"text\" required"
                                        + " value=\"" + escapeHtml(orEmpty(topicSpace.getSpaceCode())) + "\" />");
                    } else {
                        out.println(
                                "                <input class=\"aira-input\" id=\"spaceCode\" type=\"text\" disabled"
                                        + " value=\"" + escapeHtml(orEmpty(topicSpace.getSpaceCode())) + "\" />");
                    }
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"spaceName\">Topic Space Name (required)</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"spaceName\" name=\"spaceName\" type=\"text\" required"
                                    + " value=\"" + escapeHtml(orEmpty(topicSpace.getSpaceName())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"description\">Description</label>");
                    out.println(
                            "                <textarea class=\"aira-textarea\" id=\"description\" name=\"description\" rows=\"4\">"
                                    + escapeHtml(orEmpty(topicSpace.getDescription())) + "</textarea>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"visibility\">Visibility"
                            + (creating ? " (required)" : "") + "</label>");
                    if (creating) {
                        out.println(
                                "                <select class=\"aira-select\" id=\"visibility\" name=\"visibility\">");
                        out.println("                  <option value=\"PUBLIC\""
                                + (topicSpace.getVisibility() == EsTopicSpace.Visibility.PUBLIC ? " selected" : "")
                                + ">PUBLIC</option>");
                        out.println("                  <option value=\"PRIVATE\""
                                + (topicSpace.getVisibility() == EsTopicSpace.Visibility.PRIVATE ? " selected" : "")
                                + ">PRIVATE</option>");
                        out.println("                </select>");
                    } else {
                        out.println(
                                "                <input class=\"aira-input\" id=\"visibility\" type=\"text\" disabled value=\""
                                        + escapeHtml(topicSpace.getVisibility() == null
                                                ? ""
                                                : topicSpace.getVisibility().name())
                                        + "\" />");
                    }
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"displayOrder\">Display Order (required)</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"displayOrder\" name=\"displayOrder\" type=\"number\" required"
                                    + " value=\"" + escapeHtml(String.valueOf(orZero(topicSpace.getDisplayOrder())))
                                    + "\" />");
                    out.println("              </div>");

                    out.println("              <label class=\"aira-radio\"><input type=\"checkbox\" name=\"isActive\""
                            + (Boolean.TRUE.equals(topicSpace.getIsActive()) ? " checked" : "")
                            + " /> Active</label>");

                    out.println("              <div class=\"aira-action-group\">");
                    out.println(
                            "                <button class=\"aira-button aira-button--primary\" type=\"submit\">Save</button>");
                    out.println("              </div>");
                    out.println("            </form>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/topic-spaces"
                            + (creating ? "" : "?esTopicSpaceId=" + topicSpace.getEsTopicSpaceId())
                            + "\">Back</a></p>");
                    out.println("          </section>");
                });
    }

    private void renderTopicNarrativeReport(HttpServletRequest request, HttpServletResponse response,
            EsTopicSpace topicSpace) throws IOException {
        String contextPath = request.getContextPath();
        List<EsTopic> topics = topicDao.findAllOrderByTopicName().stream()
                .filter(topic -> topicSpace.getEsTopicSpaceId() != null
                        && topicSpace.getEsTopicSpaceId().equals(topic.getEsTopicSpaceId()))
                .toList();

        AdminShellRenderer.render(request, response, "Topic Narrative Report - InteropHub",
                AdminSection.TOPIC_SPACES, ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Topic Narrative Report</h2>");
                    out.println("            <p><strong>Topic Space:</strong> "
                            + escapeHtml(orEmpty(topicSpace.getSpaceName())) + "</p>");
                    out.println("            <p><strong>Generated:</strong> "
                            + escapeHtml(formatDate(LocalDateTime.now())) + "</p>");
                    out.println(
                            "            <p class=\"aira-meta\">This is an alphabetical, human-readable list of topics.</p>");
                    out.println("            <div class=\"aira-action-group\">");
                    out.println(
                            "              <button class=\"aira-button aira-button--secondary\" type=\"button\" onclick=\"window.print()\">Print This Report</button>");
                    out.println("            </div>");

                    if (topics.isEmpty()) {
                        out.println(
                                "            <p class=\"aira-meta\">No topics were found for this Topic Space.</p>");
                    } else {
                        for (EsTopic topic : topics) {
                            out.println("            <section class=\"aira-panel\">");
                            out.println("              <h3 class=\"aira-subsection-title\">"
                                    + escapeHtml(orEmpty(topic.getTopicName())) + "</h3>");
                            if (topic.getStatus() != null && topic.getStatus() != EsTopic.EsTopicStatus.ACTIVE) {
                                out.println("              <p><strong>Status:</strong> "
                                        + escapeHtml(topic.getStatus().name()) + "</p>");
                            }
                            out.println("              <p>" + escapeHtml(orEmpty(topic.getDescription())) + "</p>");
                            out.println("            </section>");
                        }
                    }

                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/topic-spaces?esTopicSpaceId=" + topicSpace.getEsTopicSpaceId()
                            + "\">Back to Topic Space</a></p>");
                    out.println("          </section>");
                });
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
