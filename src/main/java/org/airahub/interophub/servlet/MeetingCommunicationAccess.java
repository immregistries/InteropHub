package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.MeetingAuthorizationService;
import org.immregistries.aira.web.AiraPage;

/**
 * Access check and page shell for a single meeting's communication pages
 * (/es/meeting-communication and /es/meeting-communication-preview).
 *
 * <p>Sending a meeting's notices is a workgroup function, not an admin-only
 * one: anyone who passes
 * {@link MeetingAuthorizationService#canManageMeetingCommunications} may use
 * these pages. Admins keep the admin shell they reach these pages from;
 * everyone else gets the ordinary Topics/Meetings shell so they aren't shown
 * an admin menu they can't use.
 */
final class MeetingCommunicationAccess {

    private static final AuthFlowService AUTH_FLOW_SERVICE = new AuthFlowService();
    private static final MeetingAuthorizationService AUTHORIZATION_SERVICE = new MeetingAuthorizationService();
    private static final EsTopicSpaceDao TOPIC_SPACE_DAO = new EsTopicSpaceDao();

    /** The signed-in user allowed to manage this meeting's communications, and whether they're a site admin. */
    record Access(User user, boolean admin) {
    }

    private MeetingCommunicationAccess() {
    }

    /**
     * Returns the caller's access, or empty after having already responded:
     * a redirect to sign in for anonymous visitors, or a 403 page for a
     * signed-in user who isn't one of this meeting's leads.
     */
    static Optional<Access> require(HttpServletRequest request, HttpServletResponse response, EsMeeting meeting)
            throws IOException {
        Optional<User> user = AUTH_FLOW_SERVICE.findAuthenticatedUser(request);
        if (user.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return Optional.empty();
        }
        if (!AUTHORIZATION_SERVICE.canManageMeetingCommunications(user.get().getUserId(), meeting)) {
            renderForbidden(request, response, meeting);
            return Optional.empty();
        }
        return Optional.of(new Access(user.get(), AUTH_FLOW_SERVICE.isAdminUser(user.get())));
    }

    static void render(HttpServletRequest request, HttpServletResponse response, Access access, EsMeeting meeting,
            String documentTitle, AdminShellRenderer.ContentRenderer content) throws IOException {
        if (access.admin()) {
            AdminShellRenderer.render(request, response, documentTitle, AdminSection.TOPIC_SPACES,
                    "/admin/es/meetings", content);
            return;
        }
        String contextPath = request.getContextPath();
        EsTopicSpace space = meeting.getEsTopicSpaceId() == null ? null
                : TOPIC_SPACE_DAO.findById(meeting.getEsTopicSpaceId()).orElse(null);
        AiraPage page = InteropAiraPageFactory.base(request, documentTitle)
                .applicationSubtitle("Communications")
                .mainClass("aira-main")
                .context(InteropAiraPageFactory.topicsMeetingsContext(
                        space != null ? space.getSpaceName() : "InteropHub",
                        space != null ? space.getSpaceCode() : null,
                        false,
                        true))
                .build();
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container--wide aira-stack\">");
            content.render(out);
            out.println("    </div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            page.writeEnd(out);
        }
    }

    private static void renderForbidden(HttpServletRequest request, HttpServletResponse response, EsMeeting meeting)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        String contextPath = request.getContextPath();
        AiraPage page = InteropAiraPageFactory.base(request, "Access Denied - InteropHub").build();
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container aira-stack\">");
            out.println("      <div class=\"aira-page-header\">");
            out.println("        <div>");
            out.println("          <h1 class=\"aira-page-title\">Access Denied</h1>");
            out.println("        </div>");
            out.println("      </div>");
            out.println("      <div class=\"aira-alert aira-alert--danger\"><p>Only this meeting's chair, scribe,"
                    + " series co-chairs, or Topic Space admins can manage its communications.</p></div>");
            out.println("      <p><a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                    + "/es/agenda?meetingId=" + meeting.getEsMeetingId() + "\">Back to Meeting</a></p>");
            out.println("    </div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            page.writeEnd(out);
        }
    }
}
