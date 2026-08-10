package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.LegalTermAcceptanceDao;
import org.airahub.interophub.dao.LegalTermDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.LegalTerm;
import org.airahub.interophub.model.LegalTermAcceptance;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.immregistries.aira.web.AiraPage;

/**
 * Signed-in user's personal account page. Route: /account
 *
 * <ul>
 * <li>GET /account &ndash; view/edit profile, accepted legal terms, and email
 * subscriptions</li>
 * <li>POST /account (action=update-profile) &ndash; update name, organization,
 * role title</li>
 * <li>POST /account (action=save-subscriptions) &ndash; remove unchecked
 * subscriptions</li>
 * <li>POST /account (action=unsubscribe-all) &ndash; unsubscribe from
 * everything</li>
 * </ul>
 *
 * Email is not editable here; it is the account's fixed identity.
 * Subscription changes always operate on the signed-in user's own email
 * address (never a client-supplied one), unlike the public
 * {@link EsUnsubscribeServlet} which is reached from email links.
 */
public class AccountServlet extends HttpServlet {
    private static final int MAX_FIRST_NAME_LENGTH = 60;
    private static final int MAX_LAST_NAME_LENGTH = 60;
    private static final int MAX_ORGANIZATION_LENGTH = 120;
    private static final int MAX_ROLE_TITLE_LENGTH = 120;

    private final AuthFlowService authFlowService;
    private final UserDao userDao;
    private final EsSubscriptionDao esSubscriptionDao;
    private final LegalTermAcceptanceDao legalTermAcceptanceDao;
    private final LegalTermDao legalTermDao;

    public AccountServlet() {
        this.authFlowService = new AuthFlowService();
        this.userDao = new UserDao();
        this.esSubscriptionDao = new EsSubscriptionDao();
        this.legalTermAcceptanceDao = new LegalTermAcceptanceDao();
        this.legalTermDao = new LegalTermDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        User user = authenticatedUser.get();
        renderAccountPage(request, response, user, null, Map.of(),
                user.getFirstName(), user.getLastName(), user.getOrganization(), user.getRoleTitle());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");

        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        User user = authenticatedUser.get();
        String action = trimToNull(request.getParameter("action"));

        if ("update-profile".equals(action)) {
            handleUpdateProfile(request, response, user);
        } else if ("save-subscriptions".equals(action)) {
            handleSaveSubscriptions(request, response, user);
        } else if ("unsubscribe-all".equals(action)) {
            esSubscriptionDao.unsubscribeAllByEmailNormalized(user.getEmailNormalized());
            renderAccountPage(request, response, user, "You have been unsubscribed from all topics.", Map.of(),
                    user.getFirstName(), user.getLastName(), user.getOrganization(), user.getRoleTitle());
        } else {
            renderAccountPage(request, response, user, null, Map.of(),
                    user.getFirstName(), user.getLastName(), user.getOrganization(), user.getRoleTitle());
        }
    }

    private void handleUpdateProfile(HttpServletRequest request, HttpServletResponse response, User user)
            throws IOException {
        String firstName = trimToNull(request.getParameter("firstName"));
        String lastName = trimToNull(request.getParameter("lastName"));
        String organization = trimToNull(request.getParameter("organization"));
        String roleTitle = trimToNull(request.getParameter("roleTitle"));

        Map<String, String> fieldErrors = validateProfileFields(firstName, lastName, organization, roleTitle);
        if (!fieldErrors.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            renderAccountPage(request, response, user, "Please correct the highlighted profile fields.", fieldErrors,
                    firstName, lastName, organization, roleTitle);
            return;
        }

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setOrganization(organization);
        user.setRoleTitle(roleTitle);
        userDao.saveOrUpdate(user);

        renderAccountPage(request, response, user, "Profile updated.", Map.of(),
                firstName, lastName, organization, roleTitle);
    }

    private void handleSaveSubscriptions(HttpServletRequest request, HttpServletResponse response, User user)
            throws IOException {
        String allIdsCsv = trimToNull(request.getParameter("all_ids"));
        Set<Long> allIds = parseLongSet(allIdsCsv);

        Set<Long> checkedIds = new HashSet<>();
        for (Long id : allIds) {
            if (request.getParameter("sub_" + id) != null) {
                checkedIds.add(id);
            }
        }

        List<EsSubscriptionDao.ActiveSubscriptionRow> current = esSubscriptionDao
                .findAllActiveByEmailNormalized(user.getEmailNormalized());
        Set<Long> currentIds = new HashSet<>();
        for (EsSubscriptionDao.ActiveSubscriptionRow row : current) {
            currentIds.add(row.getEsSubscriptionId());
        }
        for (Long id : allIds) {
            if (!checkedIds.contains(id) && currentIds.contains(id)) {
                esSubscriptionDao.removeById(id);
            }
        }

        renderAccountPage(request, response, user, "Subscription changes saved.", Map.of(),
                user.getFirstName(), user.getLastName(), user.getOrganization(), user.getRoleTitle());
    }

    private void renderAccountPage(HttpServletRequest request, HttpServletResponse response, User user,
            String message, Map<String, String> fieldErrors,
            String firstName, String lastName, String organization, String roleTitle) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        String contextPath = request.getContextPath();

        AiraPage page = InteropAiraPageFactory.base(request, "My Account - InteropHub")
                .applicationSubtitle("My Account")
                .mainClass("aira-main")
                .build();

        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);

            out.println("      <div class=\"aira-container--standard\">");
            out.println("        <div class=\"aira-page-header\">");
            out.println("          <div>");
            out.println("            <h1 class=\"aira-page-title\">My Account</h1>");
            out.println(
                    "            <p class=\"aira-page-intro\">Manage your profile, review the legal terms you have accepted, and control which topics you receive email updates for.</p>");
            out.println("          </div>");
            out.println("        </div>");

            if (message != null) {
                boolean isError = !fieldErrors.isEmpty();
                out.println("        <div class=\"aira-alert aira-alert--" + (isError ? "error" : "success") + "\">");
                out.println("          <p>" + escapeHtml(message) + "</p>");
                out.println("        </div>");
            }

            out.println("        <div class=\"aira-stack\">");
            renderProfileSection(out, contextPath, user, fieldErrors, firstName, lastName, organization, roleTitle);
            renderSubscriptionsSection(out, contextPath, user);
            renderLegalTermsSection(out, user);
            renderDeleteAccountSection(out);
            out.println("        </div>");
            out.println("      </div>");

            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            page.writeEnd(out);
        }
    }

    private void renderProfileSection(PrintWriter out, String contextPath, User user,
            Map<String, String> fieldErrors, String firstName, String lastName, String organization,
            String roleTitle) {
        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-section-title\">Your Profile</h2>");
        out.println("            <form class=\"aira-form\" method=\"post\" action=\"" + contextPath + "/account\">");
        out.println("              <input type=\"hidden\" name=\"action\" value=\"update-profile\" />");

        out.println("              <div class=\"aira-field\">");
        out.println("                <label for=\"email\">Email</label>");
        out.println("                <input class=\"aira-input\" id=\"email\" type=\"email\" value=\""
                + escapeHtml(orEmpty(user.getEmail())) + "\" disabled />");
        out.println(
                "                <p class=\"aira-field-help\">Your email address is your account identity and cannot be changed here.</p>");
        out.println("              </div>");

        renderTextField(out, fieldErrors, "firstName", "First Name", firstName, MAX_FIRST_NAME_LENGTH, true, null);
        renderTextField(out, fieldErrors, "lastName", "Last Name", lastName, MAX_LAST_NAME_LENGTH, false, null);
        renderTextField(out, fieldErrors, "organization", "Organization", organization, MAX_ORGANIZATION_LENGTH,
                true, "Full name of organization you are associated with");
        renderTextField(out, fieldErrors, "roleTitle", "Role Title", roleTitle, MAX_ROLE_TITLE_LENGTH, true, null);

        out.println("              <div class=\"aira-form-actions\">");
        out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Save Profile</button>");
        out.println("              </div>");
        out.println("            </form>");
        out.println("          </section>");
    }

    private void renderTextField(PrintWriter out, Map<String, String> fieldErrors, String fieldName, String label,
            String value, int maxLength, boolean required, String helpText) {
        String errorMessage = fieldErrors == null ? null : fieldErrors.get(fieldName);
        boolean hasError = errorMessage != null && !errorMessage.isBlank();
        String errorId = fieldName + "-error";

        out.println("              <div class=\"aira-field\">");
        out.println("                <label for=\"" + fieldName + "\">" + escapeHtml(label) + "</label>");
        if (helpText != null) {
            out.println("                <div class=\"aira-field-help\">" + escapeHtml(helpText) + "</div>");
        }
        if (hasError) {
            out.println("                <div class=\"aira-field-error\" id=\"" + errorId + "\">"
                    + escapeHtml(errorMessage) + "</div>");
        }
        out.println("                <input class=\"aira-input\" id=\"" + fieldName + "\" name=\"" + fieldName
                + "\" type=\"text\"" + (required ? " required" : "") + " maxlength=\"" + maxLength + "\" value=\""
                + escapeHtml(orEmpty(value)) + "\""
                + (hasError ? " aria-invalid=\"true\" aria-describedby=\"" + errorId + "\"" : "") + " />");
        out.println("              </div>");
    }

    private void renderSubscriptionsSection(PrintWriter out, String contextPath, User user) {
        List<EsSubscriptionDao.ActiveSubscriptionRow> subs = esSubscriptionDao
                .findAllActiveByEmailNormalized(user.getEmailNormalized());

        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-section-title\">Email Subscriptions</h2>");

        if (subs.isEmpty()) {
            out.println("            <p>You have no active email subscriptions.</p>");
            out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                    + "/es/topics\">Browse Emerging Standards Topics</a></p>");
        } else {
            String allIds = buildAllIds(subs);

            out.println("            <form class=\"aira-form\" method=\"post\" action=\"" + contextPath
                    + "/account\">");
            out.println("              <input type=\"hidden\" name=\"all_ids\" value=\"" + escapeHtml(allIds)
                    + "\" />");

            out.println("              <div class=\"aira-stack aira-stack--compact\">");
            for (EsSubscriptionDao.ActiveSubscriptionRow row : subs) {
                if (row.getSubscriptionType() == EsSubscription.SubscriptionType.GENERAL_ES) {
                    out.println("                <label class=\"aira-check\">");
                    out.println("                  <input type=\"checkbox\" name=\"sub_" + row.getEsSubscriptionId()
                            + "\" value=\"1\" checked />");
                    out.println("                  <span>General Emerging Standards updates</span>");
                    out.println("                </label>");
                }
            }
            boolean hasTopics = false;
            for (EsSubscriptionDao.ActiveSubscriptionRow row : subs) {
                if (row.getSubscriptionType() == EsSubscription.SubscriptionType.TOPIC) {
                    if (!hasTopics) {
                        out.println("                <p class=\"aira-field-help\">Topic subscriptions:</p>");
                        hasTopics = true;
                    }
                    String label = row.getTopicName() != null ? row.getTopicName() : "Topic #" + row.getEsTopicId();
                    String badge = "";
                    if (row.getStatus() == EsSubscription.SubscriptionStatus.CHAMPION) {
                        badge = " <span class=\"aira-badge aira-badge--info\">Champion</span>";
                    } else if (row.getStatus() == EsSubscription.SubscriptionStatus.SUPPORT) {
                        badge = " <span class=\"aira-badge aira-badge--info\">Support</span>";
                    }
                    out.println("                <label class=\"aira-check\">");
                    out.println("                  <input type=\"checkbox\" name=\"sub_" + row.getEsSubscriptionId()
                            + "\" value=\"1\" checked />");
                    out.println("                  <span>" + escapeHtml(label) + badge + "</span>");
                    out.println("                </label>");
                }
            }
            out.println("              </div>");

            out.println("              <div class=\"aira-form-actions\">");
            out.println(
                    "                <button class=\"aira-button aira-button--primary\" type=\"submit\" name=\"action\" value=\"save-subscriptions\">Save Changes</button>");
            out.println(
                    "                <button class=\"aira-button aira-button--secondary\" type=\"submit\" name=\"action\" value=\"unsubscribe-all\">Unsubscribe from Everything</button>");
            out.println("              </div>");
            out.println("            </form>");
        }

        out.println("          </section>");
    }

    private void renderLegalTermsSection(PrintWriter out, User user) {
        List<LegalTermAcceptance> acceptances = legalTermAcceptanceDao.findByUserId(user.getUserId());

        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-section-title\">Legal Terms Accepted</h2>");

        if (acceptances.isEmpty()) {
            out.println("            <p>You have not accepted any legal terms yet.</p>");
        } else {
            out.println("            <div class=\"aira-table-wrap\">");
            out.println("              <table class=\"aira-table\">");
            out.println("                <thead>");
            out.println("                  <tr>");
            out.println("                    <th scope=\"col\" class=\"aira-table__cell--primary\">Term</th>");
            out.println("                    <th scope=\"col\">Scope</th>");
            out.println("                    <th scope=\"col\" class=\"aira-table__cell--date\">Accepted</th>");
            out.println("                  </tr>");
            out.println("                </thead>");
            out.println("                <tbody>");
            for (LegalTermAcceptance acceptance : acceptances) {
                Optional<LegalTerm> term = legalTermDao.findById(acceptance.getTermId());
                String title = term.map(LegalTerm::getTitle).orElse("(Term #" + acceptance.getTermId() + ")");
                String scope = term.map(t -> t.getScopeType().name()).orElse("");
                String acceptedAt = acceptance.getAcceptedAt() == null ? "" : acceptance.getAcceptedAt().toLocalDate().toString();

                out.println("                  <tr>");
                out.println("                    <th scope=\"row\" class=\"aira-table__cell--primary\">"
                        + escapeHtml(title) + "</th>");
                out.println("                    <td>" + escapeHtml(scope) + "</td>");
                out.println("                    <td class=\"aira-table__cell--date\">" + escapeHtml(acceptedAt)
                        + "</td>");
                out.println("                  </tr>");
            }
            out.println("                </tbody>");
            out.println("              </table>");
            out.println("            </div>");
        }

        out.println("          </section>");
    }

    private void renderDeleteAccountSection(PrintWriter out) {
        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-section-title\">Delete Account</h2>");
        out.println(
                "            <p class=\"aira-field-help\">Account deletion is not yet available. Contact support if you need your account removed.</p>");
        out.println(
                "            <button class=\"aira-button aira-button--secondary\" type=\"button\" disabled>Delete Account</button>");
        out.println("          </section>");
    }

    private Map<String, String> validateProfileFields(String firstName, String lastName, String organization,
            String roleTitle) {
        Map<String, String> fieldErrors = new HashMap<>();

        if (!isValidName(firstName)) {
            fieldErrors.put("firstName",
                    "Enter your first name (2+ letters, max " + MAX_FIRST_NAME_LENGTH + ").");
        }
        if (lastName != null && !isValidName(lastName)) {
            fieldErrors.put("lastName",
                    "Last name must be 2+ letters if provided (max " + MAX_LAST_NAME_LENGTH + ").");
        }
        if (!isValidOrganization(organization)) {
            fieldErrors.put("organization",
                    "Use 3-" + MAX_ORGANIZATION_LENGTH
                            + " characters with letters, numbers, spaces, and common punctuation.");
        }
        if (!isValidRoleTitle(roleTitle)) {
            fieldErrors.put("roleTitle",
                    "Use 3-" + MAX_ROLE_TITLE_LENGTH
                            + " characters with letters, numbers, spaces, and common punctuation.");
        }

        return fieldErrors;
    }

    private boolean isValidName(String name) {
        if (name == null || name.length() < 2 || name.length() > MAX_FIRST_NAME_LENGTH) {
            return false;
        }
        return containsOnlySafeDisplayNameChars(name) && countLetters(name) >= 2;
    }

    private boolean isValidOrganization(String organization) {
        return organization != null
                && organization.length() >= 3
                && organization.length() <= MAX_ORGANIZATION_LENGTH
                && containsOnlySafeTextChars(organization);
    }

    private boolean isValidRoleTitle(String roleTitle) {
        return roleTitle != null
                && roleTitle.length() >= 3
                && roleTitle.length() <= MAX_ROLE_TITLE_LENGTH
                && containsOnlySafeTextChars(roleTitle);
    }

    private boolean containsOnlySafeTextChars(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isISOControl(ch)) {
                return false;
            }
            boolean allowed = Character.isLetterOrDigit(ch)
                    || Character.isWhitespace(ch)
                    || ch == '.'
                    || ch == ','
                    || ch == '\''
                    || ch == '-'
                    || ch == '&'
                    || ch == '/'
                    || ch == '(' || ch == ')';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    private boolean containsOnlySafeDisplayNameChars(String value) {
        boolean hasLetter = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isISOControl(ch)) {
                return false;
            }

            int type = Character.getType(ch);
            boolean isUnicodeNamePunctuation = type == Character.DASH_PUNCTUATION
                    || type == Character.CONNECTOR_PUNCTUATION
                    || type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK;

            if (Character.isLetter(ch)) {
                hasLetter = true;
            }

            boolean allowed = Character.isLetterOrDigit(ch)
                    || Character.isWhitespace(ch)
                    || ch == '\''
                    || ch == '’'
                    || ch == '.'
                    || isUnicodeNamePunctuation;
            if (!allowed) {
                return false;
            }
        }
        return hasLetter;
    }

    private int countLetters(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int letterCount = 0;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetter(value.charAt(i))) {
                letterCount++;
            }
        }
        return letterCount;
    }

    private static String buildAllIds(List<EsSubscriptionDao.ActiveSubscriptionRow> subs) {
        StringBuilder sb = new StringBuilder();
        for (EsSubscriptionDao.ActiveSubscriptionRow row : subs) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(row.getEsSubscriptionId());
        }
        return sb.toString();
    }

    private static Set<Long> parseLongSet(String csv) {
        Set<Long> result = new HashSet<>();
        if (csv == null || csv.isBlank()) {
            return result;
        }
        for (String part : csv.split(",")) {
            try {
                result.add(Long.valueOf(part.trim()));
            } catch (NumberFormatException ignored) {
                // Ignore malformed subscription id.
            }
        }
        return result;
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
