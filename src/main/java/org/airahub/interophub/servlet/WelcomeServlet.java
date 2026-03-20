package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.AppRegistryDao;
import org.airahub.interophub.dao.ConnectWorkspaceDao;
import org.airahub.interophub.dao.IgTopicDao;
import org.airahub.interophub.dao.WorkspaceEnrollmentDao;
import org.airahub.interophub.model.AppRegistry;
import org.airahub.interophub.model.ConnectWorkspace;
import org.airahub.interophub.model.IgTopic;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

public class WelcomeServlet extends HttpServlet {
    private final AuthFlowService authFlowService;
    private final AppRegistryDao appRegistryDao;
    private final ConnectWorkspaceDao connectWorkspaceDao;
    private final IgTopicDao igTopicDao;
    private final WorkspaceEnrollmentDao workspaceEnrollmentDao;

    public WelcomeServlet() {
        this.authFlowService = new AuthFlowService();
        this.appRegistryDao = new AppRegistryDao();
        this.connectWorkspaceDao = new ConnectWorkspaceDao();
        this.igTopicDao = new IgTopicDao();
        this.workspaceEnrollmentDao = new WorkspaceEnrollmentDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User user = authFlowService.findAuthenticatedUser(request)
                .orElse(null);

        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        response.setContentType("text/html;charset=UTF-8");
        String contextPath = request.getContextPath();
        boolean adminUser = authFlowService.isAdminUser(user);
        String name = user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getEmail()
                : user.getDisplayName();
        List<AppRegistry> availableApps = appRegistryDao.findAllOrdered().stream()
                .filter(app -> Boolean.TRUE.equals(app.getEnabled()))
                .filter(app -> !Boolean.TRUE.equals(app.getKillSwitch()))
                .filter(app -> app.getAppName() != null && !app.getAppName().isBlank())
                .filter(app -> app.getDefaultRedirectUrl() != null && !app.getDefaultRedirectUrl().isBlank())
                .toList();
        List<ConnectWorkspace> activeWorkspaces = connectWorkspaceDao.findActiveOrderedByStartDate();
        List<Long> activeWorkspaceIds = new ArrayList<>();
        for (ConnectWorkspace workspace : activeWorkspaces) {
            activeWorkspaceIds.add(workspace.getWorkspaceId());
        }
        long pendingRegistrationCount = adminUser
                ? workspaceEnrollmentDao.countPendingForWorkspaceIds(activeWorkspaceIds)
                : 0L;
        Map<Long, String> topicNameById = new HashMap<>();
        for (IgTopic topic : igTopicDao.findAllOrdered()) {
            topicNameById.put(topic.getTopicId(), topic.getTopicName());
        }

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Welcome - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <section class=\"panel\">");
            out.println("      <img class=\"banner\" src=\"" + contextPath
                    + "/image/Splashpage_connectathon.png\" alt=\"Developers collaborating on connectathon work\" />");
            out.println("    </section>");
            out.println("    <h1>Welcome " + escapeHtml(name) + "</h1>");
            out.println(
                    "    <p>You are signed in as <strong>" + escapeHtml(orEmpty(user.getEmail())) + "</strong>.</p>");

            if (adminUser && pendingRegistrationCount > 0) {
                out.println("    <section class=\"panel\">");
                out.println("      <h2>Pending Workspace Registrations</h2>");
                out.println("      <p>There are currently <strong>" + pendingRegistrationCount
                        + "</strong> pending registration request(s).</p>");
                out.println("      <p><a href=\"" + contextPath
                        + "/workspace?mode=admin-enrollments\">Review Pending Registrations</a></p>");
                out.println("    </section>");
            }

            out.println("    <h2>Applications</h2>");
            if (availableApps.isEmpty()) {
                out.println("    <p>No applications are currently available.</p>");
            } else {
                out.println("    <ul>");
                for (AppRegistry app : availableApps) {
                    out.println("      <li><a href=\"" + contextPath + "/app-access?appId=" + app.getAppId()
                            + "\">" + escapeHtml(app.getAppName()) + "</a></li>");
                }
                out.println("    </ul>");
            }

            out.println("    <h2>Workspaces</h2>");
            if (activeWorkspaces.isEmpty()) {
                out.println("    <p>No active workspaces are currently available.</p>");
            } else {
                out.println("    <ul>");
                for (ConnectWorkspace workspace : activeWorkspaces) {
                    String topicName = topicNameById.get(workspace.getTopicId());
                    if (topicName == null || topicName.isBlank()) {
                        topicName = "Unknown Topic";
                    }
                    String workspaceName = workspace.getWorkspaceName();
                    if (workspaceName == null || workspaceName.isBlank()) {
                        workspaceName = "(Unnamed Workspace)";
                    }
                    out.println("      <li><a href=\"" + contextPath + "/workspace?workspaceId="
                            + workspace.getWorkspaceId() + "\">"
                            + escapeHtml(topicName + ": " + workspaceName) + "</a></li>");
                }
                out.println("    </ul>");
            }

            if (adminUser) {
                out.println("      <h2>Admin Functions</h2>");
                out.println("      <p><a href=\"" + contextPath + "/settings\">Hub Settings</a></p>");
                out.println("      <p><a href=\"" + contextPath + "/admin/apps\">App Registry</a></p>");
                out.println("      <p><a href=\"" + contextPath + "/admin/topics\">IG Topics</a></p>");
            }

            out.println("    <form action=\"" + contextPath + "/logout\" method=\"post\">");
            out.println("      <button type=\"submit\">Logout</button>");
            out.println("    </form>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
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

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
