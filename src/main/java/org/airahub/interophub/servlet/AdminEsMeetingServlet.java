package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.AdminMeetingBrowseRow;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.dao.EsTopicMeetingMemberDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicMeetingMember;
import org.airahub.interophub.model.EsTopicMeetingMember.MembershipStatus;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.PublicUrlService;

public class AdminEsMeetingServlet extends HttpServlet {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuthFlowService authFlowService;
    private final EsTopicMeetingDao meetingDao;
    private final EsTopicMeetingMemberDao memberDao;
    private final EsTopicDao topicDao;
    private final UserDao userDao;
    private final PublicUrlService publicUrlService;

    public AdminEsMeetingServlet() {
        this.authFlowService = new AuthFlowService();
        this.meetingDao = new EsTopicMeetingDao();
        this.memberDao = new EsTopicMeetingMemberDao();
        this.topicDao = new EsTopicDao();
        this.userDao = new UserDao();
        this.publicUrlService = new PublicUrlService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String meetingIdRaw = trimToNull(request.getParameter("meetingId"));

        if (meetingIdRaw != null) {
            Long meetingId = parseId(meetingIdRaw);
            if (meetingId == null) {
                renderList(response, contextPath, "Invalid meeting identifier.");
                return;
            }

            EsTopicMeeting meeting = meetingDao.findById(meetingId).orElse(null);
            if (meeting == null) {
                renderList(response, contextPath, "Meeting not found.");
                return;
            }

            String savedMsg = request.getParameter("saved") != null ? "Membership status updated." : null;
            renderDetail(response, contextPath, meeting, savedMsg);
            return;
        }

        renderList(response, contextPath, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        Long memberId = parseId(trimToNull(request.getParameter("memberId")));
        Long meetingId = parseId(trimToNull(request.getParameter("meetingId")));
        String action = trimToNull(request.getParameter("action"));

        if (memberId == null || meetingId == null || action == null) {
            response.sendRedirect(contextPath + "/admin/es/meetings");
            return;
        }

        EsTopicMeetingMember member = memberDao.findById(memberId).orElse(null);
        if (member == null || !meetingId.equals(member.getEsTopicMeetingId())) {
            response.sendRedirect(contextPath + "/admin/es/meetings?meetingId=" + meetingId);
            return;
        }

        applyTransition(member, action, adminUser.get().getUserId());

        response.sendRedirect(contextPath + "/admin/es/meetings?meetingId=" + meetingId + "&saved=1");
    }

    private void applyTransition(EsTopicMeetingMember member, String action, Long adminUserId) {
        MembershipStatus current = member.getMembershipStatus();
        MembershipStatus newStatus = null;

        switch (action.toLowerCase()) {
            case "approve":
                if (current == MembershipStatus.REQUESTED
                        || current == MembershipStatus.DECLINED
                        || current == MembershipStatus.REMOVED) {
                    newStatus = MembershipStatus.APPROVED;
                }
                break;
            case "decline":
                if (current == MembershipStatus.REQUESTED) {
                    newStatus = MembershipStatus.DECLINED;
                }
                break;
            case "remove":
                if (current == MembershipStatus.APPROVED) {
                    newStatus = MembershipStatus.REMOVED;
                }
                break;
            default:
                break;
        }

        if (newStatus != null) {
            member.setMembershipStatus(newStatus);
            if (newStatus == MembershipStatus.APPROVED) {
                member.setApprovedByUserId(adminUserId);
                member.setApprovedAt(LocalDateTime.now());
            }
            memberDao.saveOrUpdate(member);
        }
    }

    private void renderList(HttpServletResponse response, String contextPath, String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        List<AdminMeetingBrowseRow> meetings = meetingDao.findAllActiveBrowseRows();

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "ES Meetings Admin - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>ES Meetings</h2>");
                panelOut.println("        <p>View and manage Emerging Standards topic meeting memberships.</p>");
                if (message != null && !message.isBlank()) {
                    panelOut.println("        <p><strong>" + escapeHtml(message) + "</strong></p>");
                }

                panelOut.println("        <table class=\"data-table\">");
                panelOut.println("          <thead>");
                panelOut.println("            <tr>");
                panelOut.println("              <th>Meeting Name</th>");
                panelOut.println("              <th>Approved</th>");
                panelOut.println("              <th>Requested</th>");
                panelOut.println("            </tr>");
                panelOut.println("          </thead>");
                panelOut.println("          <tbody>");
                for (AdminMeetingBrowseRow row : meetings) {
                    panelOut.println("            <tr>");
                    panelOut.println("              <td><a href=\"" + contextPath + "/admin/es/meetings?meetingId="
                            + row.getEsTopicMeetingId() + "\">"
                            + escapeHtml(orEmpty(row.getMeetingName())) + "</a></td>");
                    panelOut.println("              <td>" + row.getApprovedCount() + "</td>");
                    panelOut.println("              <td>" + row.getRequestedCount() + "</td>");
                    panelOut.println("            </tr>");
                }
                if (meetings.isEmpty()) {
                    panelOut.println("            <tr>");
                    panelOut.println("              <td colspan=\"3\">No active meetings found.</td>");
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

    private void renderDetail(HttpServletResponse response, String contextPath, EsTopicMeeting meeting,
            String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        List<EsTopicMeetingMember> requested = memberDao.findByMeetingIdAndStatus(
                meeting.getEsTopicMeetingId(), MembershipStatus.REQUESTED);
        List<EsTopicMeetingMember> approved = memberDao.findByMeetingIdAndStatus(
                meeting.getEsTopicMeetingId(), MembershipStatus.APPROVED);
        List<EsTopicMeetingMember> declined = memberDao.findByMeetingIdAndStatus(
                meeting.getEsTopicMeetingId(), MembershipStatus.DECLINED);
        List<EsTopicMeetingMember> removed = memberDao.findByMeetingIdAndStatus(
                meeting.getEsTopicMeetingId(), MembershipStatus.REMOVED);

        Map<Long, User> usersById = loadUsersByMembers(requested, approved, declined, removed);
        Long meetingId = meeting.getEsTopicMeetingId();

        EsTopic topic = meeting.getEsTopicId() != null
                ? topicDao.findById(meeting.getEsTopicId()).orElse(null)
                : null;
        String topicCode = topic != null ? topic.getTopicCode() : null;
        String backPath = "/admin/es/meetings?meetingId=" + meetingId;
        String attendancePath = topicCode != null ? "/attend/" + encodePathSegment(topicCode) : null;
        String attendanceAbsoluteUrl = attendancePath != null
                ? publicUrlService.resolveExternalUrl(attendancePath)
                : null;
        String attendanceQrUrl = attendancePath != null
                ? buildQrPageUrl(contextPath, attendancePath, "Meeting Attendance", backPath)
                : null;

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Meeting Members - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>" + escapeHtml(orEmpty(meeting.getMeetingName())) + "</h2>");

                if (message != null && !message.isBlank()) {
                    panelOut.println("        <p><strong>" + escapeHtml(message) + "</strong></p>");
                }

                if (attendanceAbsoluteUrl != null) {
                    panelOut.println("        <p><strong>Attendance URL:</strong> <a href=\""
                            + escapeHtml(attendanceAbsoluteUrl) + "\">" + escapeHtml(attendanceAbsoluteUrl)
                            + "</a> (<a href=\"" + escapeHtml(attendanceQrUrl) + "\">qr code</a>)</p>");
                }

                if (!requested.isEmpty()) {
                    panelOut.println("        <h3>Requested (" + requested.size() + ")</h3>");
                    renderMemberTable(panelOut, contextPath, meetingId, requested, usersById,
                            List.of("approve", "decline"));
                }

                if (!approved.isEmpty()) {
                    panelOut.println("        <h3>Approved (" + approved.size() + ")</h3>");
                    renderMemberTable(panelOut, contextPath, meetingId, approved, usersById,
                            List.of("remove"));
                }

                if (!declined.isEmpty()) {
                    panelOut.println("        <h3>Declined (" + declined.size() + ")</h3>");
                    renderMemberTable(panelOut, contextPath, meetingId, declined, usersById,
                            List.of("approve"));
                }

                if (!removed.isEmpty()) {
                    panelOut.println("        <h3>Removed (" + removed.size() + ")</h3>");
                    renderMemberTable(panelOut, contextPath, meetingId, removed, usersById,
                            List.of("approve"));
                    panelOut.println("        </section>");
                }

                if (requested.isEmpty() && approved.isEmpty() && declined.isEmpty() && removed.isEmpty()) {
                    panelOut.println("        <p>No members found for this meeting.</p>");
                }

                panelOut.println(
                        "        <p><a href=\"" + contextPath + "/admin/es/meetings\">Back to Meetings</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderMemberTable(PrintWriter out, String contextPath, Long meetingId,
            List<EsTopicMeetingMember> members, Map<Long, User> usersById, List<String> actions) {
        out.println("          <table class=\"data-table\">");
        out.println("            <thead>");
        out.println("              <tr>");
        out.println("                <th>Email</th>");
        out.println("                <th>Display Name</th>");
        out.println("                <th>Organization</th>");
        out.println("                <th>Joined</th>");
        out.println("                <th>Actions</th>");
        out.println("              </tr>");
        out.println("            </thead>");
        out.println("            <tbody>");
        for (EsTopicMeetingMember member : members) {
            User user = member.getUserId() != null ? usersById.get(member.getUserId()) : null;
            out.println("              <tr>");
            out.println("                <td>" + escapeHtml(orEmpty(member.getEmail())) + "</td>");
            out.println("                <td>"
                    + escapeHtml(user != null ? orEmpty(user.getFullName()) : "") + "</td>");
            out.println("                <td>"
                    + escapeHtml(user != null ? orEmpty(user.getOrganization()) : "") + "</td>");
            out.println("                <td>" + escapeHtml(formatDate(member.getCreatedAt())) + "</td>");
            out.println("                <td>");
            for (String action : actions) {
                out.println("                  <form method=\"post\" action=\"" + contextPath
                        + "/admin/es/meetings\" style=\"display:inline\">");
                out.println(
                        "                    <input type=\"hidden\" name=\"meetingId\" value=\"" + meetingId + "\">");
                out.println("                    <input type=\"hidden\" name=\"memberId\" value=\""
                        + member.getEsTopicMeetingMemberId() + "\">");
                out.println("                    <input type=\"hidden\" name=\"action\" value=\""
                        + escapeHtml(action) + "\">");
                out.println("                    <button type=\"submit\">"
                        + escapeHtml(capitalize(action)) + "</button>");
                out.println("                  </form>");
            }
            out.println("                </td>");
            out.println("              </tr>");
        }
        out.println("            </tbody>");
        out.println("          </table>");
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
                panelOut.println("        <p>You must be an InteropHub admin to access meeting management.</p>");
                panelOut.println("        <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    @SafeVarargs
    private Map<Long, User> loadUsersByMembers(List<EsTopicMeetingMember>... groups) {
        List<Long> userIds = new ArrayList<>();
        for (List<EsTopicMeetingMember> group : groups) {
            for (EsTopicMeetingMember m : group) {
                if (m.getUserId() != null) {
                    userIds.add(m.getUserId());
                }
            }
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinctIds = userIds.stream().distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        return userDao.findByIds(distinctIds).stream()
                .collect(Collectors.toMap(User::getUserId, u -> u, (a, b) -> a, LinkedHashMap::new));
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception ex) {
            return null;
        }
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

    private String formatDate(LocalDateTime dt) {
        return dt == null ? "" : DATE_FORMAT.format(dt);
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String buildQrPageUrl(String contextPath, String targetPath, String label, String backPath) {
        return contextPath + "/admin/qr?target=" + encodeQueryComponent(targetPath)
                + "&label=" + encodeQueryComponent(label)
                + "&back=" + encodeQueryComponent(backPath);
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String encodeQueryComponent(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
