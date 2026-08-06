package org.airahub.interophub.servlet;

import java.io.IOException;
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

public class AdminEsSubscriptionServlet extends HttpServlet {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EsSubscriptionDao esSubscriptionDao;
    private final EsTopicDao esTopicDao;
    private final UserDao userDao;

    public AdminEsSubscriptionServlet() {
        this.esSubscriptionDao = new EsSubscriptionDao();
        this.esTopicDao = new EsTopicDao();
        this.userDao = new UserDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> admin = AdminAccessGuard.requireAdmin(request, response);
        if (admin.isEmpty()) {
            return;
        }
        Long subscriptionId = parseId(trimToNull(request.getParameter("subscriptionId")));
        Long topicIdParam = parseId(trimToNull(request.getParameter("topicId")));
        boolean saved = "true".equals(request.getParameter("saved"));

        if (subscriptionId == null) {
            renderNotFound(request, response, "No subscription ID provided.");
            return;
        }
        Optional<EsSubscription> subOpt = esSubscriptionDao.findById(subscriptionId);
        if (subOpt.isEmpty()) {
            renderNotFound(request, response, "Subscription not found.");
            return;
        }
        EsSubscription sub = subOpt.get();
        EsTopic topic = sub.getEsTopicId() != null
                ? esTopicDao.findById(sub.getEsTopicId()).orElse(null)
                : null;
        User subUser = sub.getUserId() != null
                ? userDao.findById(sub.getUserId()).orElse(null)
                : null;

        renderDetail(request, response, sub, topic, subUser, topicIdParam, saved);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> admin = AdminAccessGuard.requireAdmin(request, response);
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

    private void renderDetail(HttpServletRequest request, HttpServletResponse response, EsSubscription sub,
            EsTopic topic, User subUser, Long topicIdParam, boolean saved) throws IOException {
        String contextPath = request.getContextPath();
        boolean isTopic = sub.getSubscriptionType() == EsSubscription.SubscriptionType.TOPIC;
        EsSubscription.SubscriptionStatus status = sub.getStatus();

        AdminShellRenderer.render(request, response, "Subscription Detail - InteropHub", AdminSection.TOPIC_SPACES,
                "/admin/es/topics", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Subscription Detail</h2>");

                    if (saved) {
                        out.println("            <div class=\"aira-alert aira-alert--success\"><p>Status updated.</p></div>");
                    }

                    out.println("            <p><strong>Email:</strong> " + escapeHtml(orEmpty(sub.getEmail()))
                            + "</p>");
                    String displayName = subUser != null ? orEmpty(subUser.getFullName()) : "";
                    out.println("            <p><strong>Display Name:</strong> " + escapeHtml(displayName)
                            + "</p>");
                    String org = subUser != null ? orEmpty(subUser.getOrganization()) : "";
                    out.println("            <p><strong>Organization:</strong> " + escapeHtml(org) + "</p>");
                    String typeLabel = isTopic ? "Topic Following" : "General ES Updates";
                    out.println("            <p><strong>Type:</strong> " + escapeHtml(typeLabel) + "</p>");
                    if (topic != null) {
                        out.println("            <p><strong>Topic:</strong> <a class=\"aira-inline-link\" href=\""
                                + contextPath + "/es/topic/" + topic.getEsTopicId() + "\">"
                                + escapeHtml(orEmpty(topic.getTopicName())) + "</a></p>");
                    }
                    out.println("            <p><strong>Current Status:</strong> " + escapeHtml(statusLabel(status))
                            + "</p>");
                    out.println("            <p><strong>Subscribed On:</strong> "
                            + escapeHtml(formatDate(sub.getCreatedAt())) + "</p>");

                    out.println("            <h3 class=\"aira-subsection-title\">Change Status</h3>");
                    out.println("            <form method=\"post\" action=\"" + contextPath
                            + "/admin/es/subscription\">");
                    out.println("              <input type=\"hidden\" name=\"subscriptionId\" value=\""
                            + sub.getEsSubscriptionId() + "\" />");
                    if (topicIdParam != null) {
                        out.println("              <input type=\"hidden\" name=\"topicId\" value=\""
                                + topicIdParam + "\" />");
                    }
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
                    out.println("              <div class=\"aira-action-group\">");
                    out.println(
                            "                <button class=\"aira-button aira-button--secondary\" type=\"submit\" name=\"action\" value=\"SUBSCRIBED\""
                                    + disabledSubscribed + ">Set Subscribed</button>");
                    if (isTopic) {
                        out.println(
                                "                <button class=\"aira-button aira-button--secondary\" type=\"submit\" name=\"action\" value=\"CHAMPION\""
                                        + disabledChampion + ">Set Champion</button>");
                        out.println(
                                "                <button class=\"aira-button aira-button--secondary\" type=\"submit\" name=\"action\" value=\"SUPPORT\""
                                        + disabledSupport + ">Set Support</button>");
                    }
                    out.println(
                            "                <button class=\"aira-button aira-button--danger\" type=\"submit\" name=\"action\" value=\"UNSUBSCRIBED\""
                                    + disabledUnsubscribed + ">Unsubscribe</button>");
                    out.println("              </div>");
                    out.println("            </form>");

                    if (topicIdParam != null) {
                        out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                                + "/es/topic/" + topicIdParam
                                + "\">← Back to Topic</a></p>");
                    }
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/topics\">Back to Topics List</a></p>");
                    out.println("          </section>");
                });
    }

    private void renderNotFound(HttpServletRequest request, HttpServletResponse response,
            String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        String contextPath = request.getContextPath();
        AdminShellRenderer.render(request, response, "Not Found - InteropHub", AdminSection.TOPIC_SPACES,
                "/admin/es/topics", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Not Found</h2>");
                    out.println("            <p>" + escapeHtml(message) + "</p>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/topics\">Back to Topics</a></p>");
                    out.println("          </section>");
                });
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
