package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

public class AdminEsSubscriptionServlet extends HttpServlet {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuthFlowService authFlowService;
    private final EsSubscriptionDao esSubscriptionDao;
    private final EsTopicDao esTopicDao;
    private final UserDao userDao;

    public AdminEsSubscriptionServlet() {
        this.authFlowService = new AuthFlowService();
        this.esSubscriptionDao = new EsSubscriptionDao();
        this.esTopicDao = new EsTopicDao();
        this.userDao = new UserDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> admin = requireAdmin(request, response);
        if (admin.isEmpty()) {
            return;
        }
        String contextPath = request.getContextPath();
        Long subscriptionId = parseId(trimToNull(request.getParameter("subscriptionId")));
        Long topicIdParam = parseId(trimToNull(request.getParameter("topicId")));
        boolean saved = "true".equals(request.getParameter("saved"));

        if (subscriptionId == null) {
            renderNotFound(response, contextPath, "No subscription ID provided.");
            return;
        }
        Optional<EsSubscription> subOpt = esSubscriptionDao.findById(subscriptionId);
        if (subOpt.isEmpty()) {
            renderNotFound(response, contextPath, "Subscription not found.");
            return;
        }
        EsSubscription sub = subOpt.get();
        EsTopic topic = sub.getEsTopicId() != null
                ? esTopicDao.findById(sub.getEsTopicId()).orElse(null)
                : null;
        User subUser = sub.getUserId() != null
                ? userDao.findById(sub.getUserId()).orElse(null)
                : null;

        renderDetail(response, contextPath, sub, topic, subUser, topicIdParam, saved);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> admin = requireAdmin(request, response);
        if (admin.isEmpty()) {
            return;
        }
        String contextPath = request.getContextPath();
        Long subscriptionId = parseId(trimToNull(request.getParameter("subscriptionId")));
        Long topicIdParam = parseId(trimToNull(request.getParameter("topicId")));
        String action = trimToNull(request.getParameter("action"));

        if (subscriptionId == null || action == null) {
            response.sendRedirect(contextPath + "/admin/es/topics");
            return;
        }

        Optional<EsSubscription> subOpt = esSubscriptionDao.findById(subscriptionId);
        if (subOpt.isEmpty()) {
            response.sendRedirect(contextPath + "/admin/es/topics");
            return;
        }

        EsSubscription current = subOpt.get();
        boolean isTopic = current.getSubscriptionType() == EsSubscription.SubscriptionType.TOPIC;
        EsSubscription.SubscriptionStatus targetStatus = parseActionStatus(action);
        int updated = 0;
        if (targetStatus != null
                && canTransitionTo(current.getStatus(), targetStatus, isTopic)) {
            updated = esSubscriptionDao.setTopicSubscriptionStatus(
                    subscriptionId,
                    targetStatus,
                    targetStatus == EsSubscription.SubscriptionStatus.UNSUBSCRIBED
                            ? LocalDateTime.now()
                            : null);
        }

        StringBuilder redirect = new StringBuilder(contextPath)
                .append("/admin/es/subscription?subscriptionId=").append(subscriptionId)
                .append("&saved=").append(updated > 0 ? "true" : "false");
        if (topicIdParam != null) {
            redirect.append("&topicId=").append(topicIdParam);
        }
        response.sendRedirect(redirect.toString());
    }

    private void renderDetail(HttpServletResponse response, String contextPath, EsSubscription sub,
            EsTopic topic, User subUser, Long topicIdParam, boolean saved) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        boolean isTopic = sub.getSubscriptionType() == EsSubscription.SubscriptionType.TOPIC;
        EsSubscription.SubscriptionStatus status = sub.getStatus();

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Subscription Detail - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Subscription Detail</h2>");

                if (saved) {
                    panelOut.println("        <p><strong>Status updated.</strong></p>");
                }

                panelOut.println("        <section>");
                panelOut.println("          <p><strong>Email:</strong> "
                        + escapeHtml(orEmpty(sub.getEmail())) + "</p>");
                String displayName = subUser != null ? orEmpty(subUser.getFullName()) : "";
                panelOut.println("          <p><strong>Display Name:</strong> "
                        + escapeHtml(displayName) + "</p>");
                String org = subUser != null ? orEmpty(subUser.getOrganization()) : "";
                panelOut.println("          <p><strong>Organization:</strong> "
                        + escapeHtml(org) + "</p>");
                String typeLabel = isTopic ? "Topic Following" : "General ES Updates";
                panelOut.println("          <p><strong>Type:</strong> "
                        + escapeHtml(typeLabel) + "</p>");
                if (topic != null) {
                    panelOut.println("          <p><strong>Topic:</strong> <a href=\"" + contextPath
                            + "/admin/es/topics?esTopicId=" + topic.getEsTopicId() + "\">"
                            + escapeHtml(orEmpty(topic.getTopicName())) + "</a></p>");
                }
                panelOut.println("          <p><strong>Current Status:</strong> "
                        + escapeHtml(statusLabel(status)) + "</p>");
                panelOut.println("          <p><strong>Subscribed On:</strong> "
                        + escapeHtml(formatDate(sub.getCreatedAt())) + "</p>");
                panelOut.println("        </section>");

                panelOut.println("        <section>");
                panelOut.println("          <h3>Change Status</h3>");
                panelOut.println("          <form method=\"post\" action=\"" + contextPath
                        + "/admin/es/subscription\">");
                panelOut.println("            <input type=\"hidden\" name=\"subscriptionId\" value=\""
                        + sub.getEsSubscriptionId() + "\" />");
                if (topicIdParam != null) {
                    panelOut.println("            <input type=\"hidden\" name=\"topicId\" value=\""
                            + topicIdParam + "\" />");
                }
                panelOut.println(
                        "            <div class=\"login-form\" style=\"display:flex;gap:0.5rem;flex-wrap:wrap;\">");
                String disabledSubscribed = !canTransitionTo(
                        status,
                        EsSubscription.SubscriptionStatus.SUBSCRIBED,
                        isTopic)
                                ? " disabled"
                                : "";
                String disabledChampion = !canTransitionTo(
                        status,
                        EsSubscription.SubscriptionStatus.CHAMPION,
                        isTopic)
                                ? " disabled"
                                : "";
                String disabledSupport = !canTransitionTo(
                        status,
                        EsSubscription.SubscriptionStatus.SUPPORT,
                        isTopic)
                                ? " disabled"
                                : "";
                String disabledUnsubscribed = !canTransitionTo(
                        status,
                        EsSubscription.SubscriptionStatus.UNSUBSCRIBED,
                        isTopic)
                                ? " disabled"
                                : "";
                panelOut.println(
                        "              <button type=\"submit\" name=\"action\" value=\"SUBSCRIBED\""
                                + disabledSubscribed + ">Set Subscribed</button>");
                if (isTopic) {
                    panelOut.println(
                            "              <button type=\"submit\" name=\"action\" value=\"CHAMPION\""
                                    + disabledChampion + ">Set Champion</button>");
                    panelOut.println(
                            "              <button type=\"submit\" name=\"action\" value=\"SUPPORT\""
                                    + disabledSupport + ">Set Support</button>");
                }
                panelOut.println(
                        "              <button type=\"submit\" name=\"action\" value=\"UNSUBSCRIBED\""
                                + disabledUnsubscribed + ">Unsubscribe</button>");
                panelOut.println("            </div>");
                panelOut.println("          </form>");
                panelOut.println("        </section>");

                if (topicIdParam != null) {
                    panelOut.println("        <p><a href=\"" + contextPath
                            + "/admin/es/topics?esTopicId=" + topicIdParam
                            + "\">\u2190 Back to Topic</a></p>");
                }
                panelOut.println("        <p><a href=\"" + contextPath
                        + "/admin/es/topics\">Back to Topics List</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderNotFound(HttpServletResponse response, String contextPath,
            String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Not Found - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Not Found</h2>");
                panelOut.println("        <p>" + escapeHtml(message) + "</p>");
                panelOut.println("        <p><a href=\"" + contextPath
                        + "/admin/es/topics\">Back to Topics</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private Optional<User> requireAdmin(HttpServletRequest request,
            HttpServletResponse response) throws IOException {
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
                panelOut.println(
                        "        <p>You must be an InteropHub admin to access subscription management.</p>");
                panelOut.println("        <p><a href=\"" + contextPath
                        + "/welcome\">Return to Welcome</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private String statusLabel(EsSubscription.SubscriptionStatus status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case SUBSCRIBED:
                return "Subscribed";
            case CHAMPION:
                return "Champion";
            case SUPPORT:
                return "Support";
            case UNSUBSCRIBED:
                return "Unsubscribed";
            default:
                return status.name();
        }
    }

    private EsSubscription.SubscriptionStatus parseActionStatus(String action) {
        if (action == null) {
            return null;
        }
        try {
            return EsSubscription.SubscriptionStatus.valueOf(action);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean canTransitionTo(EsSubscription.SubscriptionStatus from,
            EsSubscription.SubscriptionStatus to,
            boolean isTopic) {
        if (to == null || from == to) {
            return false;
        }
        if (!isTopic && (to == EsSubscription.SubscriptionStatus.CHAMPION
                || to == EsSubscription.SubscriptionStatus.SUPPORT)) {
            return false;
        }
        return true;
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
        if (dt == null) {
            return "";
        }
        return DATE_FORMAT.format(dt);
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
