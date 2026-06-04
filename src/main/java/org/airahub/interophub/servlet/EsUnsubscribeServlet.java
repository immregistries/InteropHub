package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EmailSendLogDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.model.EmailSendLog;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.service.EsNormalizer;

/**
 * Public unsubscribe page — no authentication required.
 *
 * GET /es/unsubscribe?email=...&log_id=...
 * Shows the manage-preferences page or confirmation page.
 *
 * POST /es/unsubscribe
 * action=unsubscribe_all — bulk unsubscribe all active subscriptions
 * action=save — remove unchecked subscriptions
 * action=resubscribe_general — re-subscribe to general ES updates
 */
public class EsUnsubscribeServlet extends HttpServlet {

    private final EsSubscriptionDao esSubscriptionDao;
    private final EmailSendLogDao emailSendLogDao;

    public EsUnsubscribeServlet() {
        this.esSubscriptionDao = new EsSubscriptionDao();
        this.emailSendLogDao = new EmailSendLogDao();
    }

    // -------------------------------------------------------------------------
    // GET
    // -------------------------------------------------------------------------

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String emailRaw = trimToNull(request.getParameter("email"));
        String emailNormalized = EsNormalizer.normalizeEmail(emailRaw);
        Long logId = parseLongOrNull(request.getParameter("log_id"));
        boolean confirmed = "true".equals(request.getParameter("confirmed"));
        boolean resubscribed = "true".equals(request.getParameter("resubscribed"));

        if (emailNormalized == null) {
            renderError(response, request.getContextPath(), "No email address specified.");
            return;
        }

        // Verify log_id matches email if provided
        if (logId != null) {
            Optional<EmailSendLog> logOpt = emailSendLogDao.findById(logId);
            if (logOpt.isEmpty()
                    || !logOpt.get().getRecipientEmailNormalized().equals(emailNormalized)) {
                renderError(response, request.getContextPath(),
                        "This link may have expired or is not valid for this email address.");
                return;
            }
        }

        if (confirmed) {
            renderConfirmationPage(response, request.getContextPath(), emailRaw, emailNormalized, logId);
            return;
        }

        if (resubscribed) {
            renderResubscribedPage(response, request.getContextPath(), emailRaw, emailNormalized, logId);
            return;
        }

        renderManagePage(response, request.getContextPath(), emailRaw, emailNormalized, logId);
    }

    // -------------------------------------------------------------------------
    // POST
    // -------------------------------------------------------------------------

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");

        String emailRaw = trimToNull(request.getParameter("email"));
        String emailNormalized = EsNormalizer.normalizeEmail(emailRaw);
        Long logId = parseLongOrNull(request.getParameter("log_id"));
        String action = trimToNull(request.getParameter("action"));

        if (emailNormalized == null) {
            renderError(response, request.getContextPath(), "No email address specified.");
            return;
        }

        String encodedEmail = URLEncoder.encode(emailRaw != null ? emailRaw : emailNormalized,
                StandardCharsets.UTF_8);

        if ("unsubscribe_all".equals(action)) {
            esSubscriptionDao.unsubscribeAllByEmailNormalized(emailNormalized);
            String redirect = request.getContextPath() + "/es/unsubscribe?email=" + encodedEmail
                    + "&confirmed=true";
            if (logId != null) {
                redirect += "&log_id=" + logId;
            }
            response.sendRedirect(redirect);
            return;
        }

        if ("save".equals(action)) {
            // all_ids contains comma-separated IDs of every subscription shown on the page
            String allIdsCsv = trimToNull(request.getParameter("all_ids"));
            Set<Long> allIds = parseLongSet(allIdsCsv);

            // Determine which IDs are checked (checked checkboxes are submitted as
            // sub_{id})
            Set<Long> checkedIds = new HashSet<>();
            for (Long id : allIds) {
                if (request.getParameter("sub_" + id) != null) {
                    checkedIds.add(id);
                }
            }

            // Delete unchecked subscriptions — re-verify ownership by re-loading from DB
            List<EsSubscriptionDao.ActiveSubscriptionRow> current = esSubscriptionDao
                    .findAllActiveByEmailNormalized(emailNormalized);
            Set<Long> currentIds = new HashSet<>();
            for (EsSubscriptionDao.ActiveSubscriptionRow row : current) {
                currentIds.add(row.getEsSubscriptionId());
            }
            for (Long id : allIds) {
                if (!checkedIds.contains(id) && currentIds.contains(id)) {
                    esSubscriptionDao.removeById(id);
                }
            }

            String redirect = request.getContextPath() + "/es/unsubscribe?email=" + encodedEmail;
            if (logId != null) {
                redirect += "&log_id=" + logId;
            }
            response.sendRedirect(redirect);
            return;
        }

        if ("resubscribe_general".equals(action)) {
            Optional<EsSubscription> existing = esSubscriptionDao.findGeneralByEmailNormalized(emailNormalized);
            if (existing.isPresent()) {
                EsSubscription sub = existing.get();
                if (sub.getStatus() == EsSubscription.SubscriptionStatus.UNSUBSCRIBED) {
                    esSubscriptionDao.setTopicSubscriptionStatus(
                            sub.getEsSubscriptionId(),
                            EsSubscription.SubscriptionStatus.SUBSCRIBED,
                            null);
                }
            } else {
                EsSubscription newSub = new EsSubscription();
                newSub.setEmail(emailRaw != null ? emailRaw : emailNormalized);
                newSub.setEmailNormalized(emailNormalized);
                newSub.setSubscriptionType(EsSubscription.SubscriptionType.GENERAL_ES);
                newSub.setStatus(EsSubscription.SubscriptionStatus.SUBSCRIBED);
                esSubscriptionDao.saveOrUpdate(newSub);
            }
            String redirect = request.getContextPath() + "/es/unsubscribe?email=" + encodedEmail
                    + "&resubscribed=true";
            if (logId != null) {
                redirect += "&log_id=" + logId;
            }
            response.sendRedirect(redirect);
            return;
        }

        // Unknown action — redirect back to manage page
        String redirect = request.getContextPath() + "/es/unsubscribe?email=" + encodedEmail;
        if (logId != null) {
            redirect += "&log_id=" + logId;
        }
        response.sendRedirect(redirect);
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    private void renderManagePage(HttpServletResponse response, String contextPath,
            String emailRaw, String emailNormalized, Long logId) throws IOException {
        List<EsSubscriptionDao.ActiveSubscriptionRow> subs = esSubscriptionDao
                .findAllActiveByEmailNormalized(emailNormalized);

        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            writePageHeader(out, contextPath, "Manage Email Preferences");
            out.println("  <main class=\"es-unsubscribe-page\">");
            out.println("    <section class=\"es-unsubscribe-card\">");
            out.println("      <h1>Manage Email Preferences</h1>");
            out.println("      <p class=\"es-unsubscribe-email\">Managing preferences for: <strong>"
                    + escapeHtml(emailNormalized) + "</strong></p>");

            if (subs.isEmpty()) {
                out.println("      <p class=\"es-unsubscribe-none\">You have no active email subscriptions.</p>");
                out.println("      <p class=\"es-unsubscribe-none\">You can subscribe to specific topics at: "
                        + "<a href=\"" + contextPath + "/es/topics\">Emerging Standards Topics</a></p>");
            } else {
                String allIds = buildAllIds(subs);

                out.println("      <form method=\"post\" action=\"" + contextPath + "/es/unsubscribe\">");
                out.println("        <input type=\"hidden\" name=\"email\" value=\""
                        + escapeHtml(emailRaw != null ? emailRaw : emailNormalized) + "\" />");
                if (logId != null) {
                    out.println("        <input type=\"hidden\" name=\"log_id\" value=\"" + logId + "\" />");
                }
                out.println("        <input type=\"hidden\" name=\"all_ids\" value=\"" + escapeHtml(allIds) + "\" />");

                out.println("        <div class=\"es-unsubscribe-list\">");

                // GENERAL_ES subscription first
                for (EsSubscriptionDao.ActiveSubscriptionRow row : subs) {
                    if (row.getSubscriptionType() == EsSubscription.SubscriptionType.GENERAL_ES) {
                        out.println("          <label class=\"es-unsubscribe-row\">");
                        out.println("            <input type=\"checkbox\" name=\"sub_" + row.getEsSubscriptionId()
                                + "\" value=\"1\" checked />");
                        out.println("            <span>General Emerging Standards updates</span>");
                        out.println("          </label>");
                    }
                }

                // TOPIC subscriptions
                boolean hasTopics = false;
                for (EsSubscriptionDao.ActiveSubscriptionRow row : subs) {
                    if (row.getSubscriptionType() == EsSubscription.SubscriptionType.TOPIC) {
                        if (!hasTopics) {
                            out.println("          <p class=\"es-unsubscribe-section-label\">Topic subscriptions:</p>");
                            hasTopics = true;
                        }
                        String label = row.getTopicName() != null ? row.getTopicName()
                                : "Topic #" + row.getEsTopicId();
                        String badge = "";
                        if (row.getStatus() == EsSubscription.SubscriptionStatus.CHAMPION) {
                            badge = " <span class=\"es-champion-badge\">Champion</span>";
                        } else if (row.getStatus() == EsSubscription.SubscriptionStatus.SUPPORT) {
                            badge = " <span class=\"es-champion-badge\">Support</span>";
                        }
                        out.println("          <label class=\"es-unsubscribe-row\">");
                        out.println("            <input type=\"checkbox\" name=\"sub_" + row.getEsSubscriptionId()
                                + "\" value=\"1\" checked />");
                        out.println("            <span>" + escapeHtml(label) + badge + "</span>");
                        out.println("          </label>");
                    }
                }

                out.println("        </div>");

                out.println("        <div class=\"es-unsubscribe-actions\">");
                out.println("          <button type=\"submit\" name=\"action\" value=\"unsubscribe_all\""
                        + " class=\"es-unsubscribe-all-btn\">Unsubscribe from Everything</button>");
                out.println("          <button type=\"submit\" name=\"action\" value=\"save\""
                        + " class=\"es-secondary-button\">Save Changes</button>");
                out.println("        </div>");
                out.println("      </form>");
            }

            out.println("    </section>");
            out.println("  </main>");
            writePageFooter(out);
        }
    }

    private void renderConfirmationPage(HttpServletResponse response, String contextPath,
            String emailRaw, String emailNormalized, Long logId) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            writePageHeader(out, contextPath, "Unsubscribed — InteropHub");
            out.println("  <main class=\"es-unsubscribe-page\">");
            out.println("    <section class=\"es-unsubscribe-card\">");
            out.println("      <h1>You've been unsubscribed</h1>");
            out.println("      <p>You have been unsubscribed from all InteropHub emails for: <strong>"
                    + escapeHtml(emailNormalized) + "</strong></p>");
            out.println("      <p class=\"es-muted\">We're sorry to see you go. You can re-subscribe below "
                    + "or manage specific topic subscriptions anytime.</p>");

            out.println("      <form method=\"post\" action=\"" + contextPath + "/es/unsubscribe\""
                    + " class=\"es-resubscribe-form\">");
            out.println("        <input type=\"hidden\" name=\"email\" value=\""
                    + escapeHtml(emailRaw != null ? emailRaw : emailNormalized) + "\" />");
            if (logId != null) {
                out.println("        <input type=\"hidden\" name=\"log_id\" value=\"" + logId + "\" />");
            }
            out.println("        <button type=\"submit\" name=\"action\" value=\"resubscribe_general\""
                    + " class=\"es-secondary-button\">Re-subscribe to general updates</button>");
            out.println("      </form>");

            out.println("      <p class=\"es-unsubscribe-topics-link\">Browse and subscribe to specific topics: "
                    + "<a href=\"" + contextPath + "/es/topics\">Emerging Standards Topics</a></p>");
            out.println("    </section>");
            out.println("  </main>");
            writePageFooter(out);
        }
    }

    private void renderResubscribedPage(HttpServletResponse response, String contextPath,
            String emailRaw, String emailNormalized, Long logId) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            writePageHeader(out, contextPath, "Re-subscribed — InteropHub");
            out.println("  <main class=\"es-unsubscribe-page\">");
            out.println("    <section class=\"es-unsubscribe-card\">");
            out.println("      <h1>You've been re-subscribed</h1>");
            out.println("      <p>You are now subscribed to general Emerging Standards updates for: <strong>"
                    + escapeHtml(emailNormalized) + "</strong></p>");
            out.println("      <p><a href=\"" + contextPath + "/es/unsubscribe?email="
                    + URLEncoder.encode(emailRaw != null ? emailRaw : emailNormalized, StandardCharsets.UTF_8)
                    + (logId != null ? "&log_id=" + logId : "")
                    + "\">Manage preferences</a></p>");
            out.println("    </section>");
            out.println("  </main>");
            writePageFooter(out);
        }
    }

    private void renderError(HttpServletResponse response, String contextPath, String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        try (PrintWriter out = response.getWriter()) {
            writePageHeader(out, contextPath, "Error — InteropHub");
            out.println("  <main class=\"es-unsubscribe-page\">");
            out.println("    <section class=\"es-unsubscribe-card\">");
            out.println("      <h1>Unable to process request</h1>");
            out.println("      <p class=\"es-muted\">" + escapeHtml(message) + "</p>");
            out.println("    </section>");
            out.println("  </main>");
            writePageFooter(out);
        }
    }

    private void writePageHeader(PrintWriter out, String contextPath, String title) {
        out.println("<!DOCTYPE html>");
        out.println("<html lang=\"en\">");
        out.println("<head>");
        out.println("  <meta charset=\"UTF-8\" />");
        out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
        out.println("  <title>" + escapeHtml(title) + "</title>");
        out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
        out.println("  <style>");
        out.println("    .es-unsubscribe-page { display: flex; justify-content: center; padding: 2rem 1rem; }");
        out.println("    .es-unsubscribe-card { background: var(--panel); border: 1px solid var(--border);"
                + " border-radius: 8px; padding: 2rem; max-width: 560px; width: 100%; }");
        out.println("    .es-unsubscribe-card h1 { margin-top: 0; font-size: 1.4rem; }");
        out.println("    .es-unsubscribe-email { color: var(--muted); font-size: 0.9rem; margin-bottom: 1.5rem; }");
        out.println("    .es-unsubscribe-list { display: flex; flex-direction: column; gap: 0.5rem;"
                + " margin-bottom: 1.5rem; }");
        out.println("    .es-unsubscribe-row { display: flex; align-items: center; gap: 0.6rem;"
                + " font-size: 0.95rem; cursor: pointer; padding: 0.3rem 0; }");
        out.println("    .es-unsubscribe-row input[type=checkbox] { width: 1.1rem; height: 1.1rem;"
                + " flex-shrink: 0; }");
        out.println("    .es-unsubscribe-section-label { font-size: 0.8rem; font-weight: 600;"
                + " color: var(--muted); text-transform: uppercase; letter-spacing: 0.05em;"
                + " margin: 0.75rem 0 0.25rem; }");
        out.println("    .es-unsubscribe-actions { display: flex; flex-wrap: wrap; gap: 0.75rem;"
                + " align-items: center; }");
        out.println("    .es-unsubscribe-all-btn { background: var(--accent); color: #fff;"
                + " border: none; border-radius: 6px; padding: 0.6rem 1.2rem; font-size: 0.95rem;"
                + " cursor: pointer; }");
        out.println("    .es-unsubscribe-all-btn:hover { opacity: 0.88; }");
        out.println("    .es-secondary-button { background: transparent; border: 1px solid var(--border);"
                + " border-radius: 6px; padding: 0.6rem 1.2rem; font-size: 0.95rem; cursor: pointer;"
                + " color: var(--text); }");
        out.println("    .es-secondary-button:hover { background: var(--border); }");
        out.println("    .es-resubscribe-form { margin: 1.25rem 0; }");
        out.println("    .es-unsubscribe-topics-link { font-size: 0.9rem; color: var(--muted); margin-top: 1rem; }");
        out.println("    .es-unsubscribe-none { color: var(--muted); }");
        out.println("    .es-muted { color: var(--muted); }");
        out.println("    .es-champion-badge { font-size: 0.75rem; background: var(--accent); color: #fff;"
                + " border-radius: 4px; padding: 0 0.4rem; margin-left: 0.3rem; }");
        out.println("  </style>");
        out.println("</head>");
        out.println("<body>");
        LocalEnvBannerRenderer.renderIfLocalhost(out);
    }

    private void writePageFooter(PrintWriter out) {
        out.println("</body>");
        out.println("</html>");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String buildAllIds(List<EsSubscriptionDao.ActiveSubscriptionRow> subs) {
        StringBuilder sb = new StringBuilder();
        for (EsSubscriptionDao.ActiveSubscriptionRow row : subs) {
            if (sb.length() > 0)
                sb.append(',');
            sb.append(row.getEsSubscriptionId());
        }
        return sb.toString();
    }

    private static Set<Long> parseLongSet(String csv) {
        Set<Long> result = new HashSet<>();
        if (csv == null || csv.isBlank())
            return result;
        for (String part : csv.split(",")) {
            Long val = parseLongOrNull(part.trim());
            if (val != null)
                result.add(val);
        }
        return result;
    }

    private static Long parseLongOrNull(String s) {
        if (s == null || s.isBlank())
            return null;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trimToNull(String s) {
        if (s == null)
            return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String escapeHtml(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
