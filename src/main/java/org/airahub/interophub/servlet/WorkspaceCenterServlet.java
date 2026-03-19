package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.ConnectWorkspaceDao;
import org.airahub.interophub.dao.IgTopicDao;
import org.airahub.interophub.dao.LegalTermAcceptanceDao;
import org.airahub.interophub.dao.LegalTermDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.dao.WorkspaceEnrollmentDao;
import org.airahub.interophub.dao.WorkspaceSystemDao;
import org.airahub.interophub.model.ConnectWorkspace;
import org.airahub.interophub.model.IgTopic;
import org.airahub.interophub.model.LegalTerm;
import org.airahub.interophub.model.LegalTermAcceptance;
import org.airahub.interophub.model.User;
import org.airahub.interophub.model.WorkspaceEnrollment;
import org.airahub.interophub.model.WorkspaceSystem;
import org.airahub.interophub.service.AuthFlowService;

public class WorkspaceCenterServlet extends HttpServlet {
    private static final int MAX_DISPLAY_NAME_LENGTH = 60;
    private static final int MAX_ORGANIZATION_LENGTH = 120;
    private static final int MAX_ROLE_TITLE_LENGTH = 120;

    private final AuthFlowService authFlowService;
    private final ConnectWorkspaceDao connectWorkspaceDao;
    private final IgTopicDao igTopicDao;
    private final WorkspaceEnrollmentDao workspaceEnrollmentDao;
    private final UserDao userDao;
    private final WorkspaceSystemDao workspaceSystemDao;
    private final LegalTermDao legalTermDao;
    private final LegalTermAcceptanceDao legalTermAcceptanceDao;

    public WorkspaceCenterServlet() {
        this.authFlowService = new AuthFlowService();
        this.connectWorkspaceDao = new ConnectWorkspaceDao();
        this.igTopicDao = new IgTopicDao();
        this.workspaceEnrollmentDao = new WorkspaceEnrollmentDao();
        this.userDao = new UserDao();
        this.workspaceSystemDao = new WorkspaceSystemDao();
        this.legalTermDao = new LegalTermDao();
        this.legalTermAcceptanceDao = new LegalTermAcceptanceDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        String mode = trimToNull(request.getParameter("mode"));
        if ("admin-enrollments".equalsIgnoreCase(mode)) {
            if (!authFlowService.isAdminUser(authenticatedUser.get())) {
                renderForbidden(response, request.getContextPath());
                return;
            }
            renderAdminEnrollmentPage(response, request.getContextPath(), null);
            return;
        }

        renderWorkspaceCenter(response, request.getContextPath(), authenticatedUser.get(),
            trimToNull(request.getParameter("workspaceId")), null, Set.of(), Map.of(),
            trimToNull(authenticatedUser.get().getDisplayName()),
            trimToNull(authenticatedUser.get().getOrganization()),
            trimToNull(authenticatedUser.get().getRoleTitle()));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");

        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        String mode = trimToNull(request.getParameter("mode"));
        if ("admin-enrollments".equalsIgnoreCase(mode)) {
            if (!authFlowService.isAdminUser(authenticatedUser.get())) {
                renderForbidden(response, request.getContextPath());
                return;
            }
            handleAdminEnrollmentAction(request, response, request.getContextPath(), authenticatedUser.get());
            return;
        }

        String workspaceIdRaw = trimToNull(request.getParameter("workspaceId"));
        String action = trimToNull(request.getParameter("action"));
        String message = null;
        Map<String, String> fieldErrors = Map.of();
        Set<Long> selectedWorkspaceTermIds = parseSelectedWorkspaceTermIds(request);
        String displayName = trimToNull(request.getParameter("displayName"));
        String organization = trimToNull(request.getParameter("organization"));
        String roleTitle = trimToNull(request.getParameter("roleTitle"));

        if (displayName == null) {
            displayName = trimToNull(authenticatedUser.get().getDisplayName());
        }
        if (organization == null) {
            organization = trimToNull(authenticatedUser.get().getOrganization());
        }
        if (roleTitle == null) {
            roleTitle = trimToNull(authenticatedUser.get().getRoleTitle());
        }

        Long workspaceId = parseId(workspaceIdRaw);
        if (workspaceId != null && "join".equalsIgnoreCase(action)) {
            WorkspaceEnrollment existing = workspaceEnrollmentDao
                    .findByWorkspaceAndUser(workspaceId, authenticatedUser.get().getUserId())
                    .orElse(null);
            if (existing == null) {
                fieldErrors = validateProfileFields(displayName, organization, roleTitle);
                List<LegalTerm> workspaceTerms = loadWorkspaceJoinTerms();
                selectedWorkspaceTermIds = applyPreviouslyAcceptedBothTerms(authenticatedUser.get(), workspaceTerms,
                        selectedWorkspaceTermIds);
                if (!fieldErrors.isEmpty()) {
                    message = "Please correct the highlighted profile fields.";
                } else if (!workspaceTerms.isEmpty() && !allRequiredTermsAccepted(workspaceTerms, selectedWorkspaceTermIds)) {
                    message = "Please accept all required workspace legal terms before requesting to join.";
                } else {
                    applyUserProfileUpdates(authenticatedUser.get(), displayName, organization, roleTitle);
                    WorkspaceEnrollment enrollment = new WorkspaceEnrollment();
                    enrollment.setWorkspaceId(workspaceId);
                    enrollment.setUserId(authenticatedUser.get().getUserId());
                    enrollment.setState(WorkspaceEnrollment.EnrollmentState.PENDING);
                    enrollment.setConsentAt(LocalDateTime.now());
                    workspaceEnrollmentDao.save(enrollment);
                    saveTermAcceptancesForWorkspace(authenticatedUser.get(), workspaceId, workspaceTerms, request);
                    message = "Your join request has been submitted and is pending approval.";
                }
            }
        }

        renderWorkspaceCenter(response, request.getContextPath(), authenticatedUser.get(), workspaceIdRaw, message,
            selectedWorkspaceTermIds, fieldErrors, displayName, organization, roleTitle);
    }

    private void handleAdminEnrollmentAction(HttpServletRequest request, HttpServletResponse response,
            String contextPath,
            User adminUser) throws IOException {
        String action = trimToNull(request.getParameter("action"));
        Long enrollmentId = parseId(trimToNull(request.getParameter("enrollmentId")));
        String message;

        if (enrollmentId == null) {
            renderAdminEnrollmentPage(response, contextPath, "A valid enrollmentId is required.");
            return;
        }

        WorkspaceEnrollment enrollment = workspaceEnrollmentDao.findById(enrollmentId).orElse(null);
        if (enrollment == null) {
            renderAdminEnrollmentPage(response, contextPath, "Enrollment record was not found.");
            return;
        }

        if ("approve".equalsIgnoreCase(action)) {
            if (enrollment.getState() == WorkspaceEnrollment.EnrollmentState.PENDING) {
                enrollment.setState(WorkspaceEnrollment.EnrollmentState.APPROVED);
                enrollment.setApprovedByUserId(adminUser.getUserId());
                enrollment.setApprovedAt(LocalDateTime.now());
                workspaceEnrollmentDao.saveOrUpdate(enrollment);
                message = "Enrollment approved.";
            } else {
                message = "Enrollment is no longer pending and was not changed.";
            }
        } else if ("reject".equalsIgnoreCase(action)) {
            if (enrollment.getState() == WorkspaceEnrollment.EnrollmentState.PENDING) {
                enrollment.setState(WorkspaceEnrollment.EnrollmentState.REJECTED);
                enrollment.setApprovedByUserId(adminUser.getUserId());
                enrollment.setApprovedAt(LocalDateTime.now());
                workspaceEnrollmentDao.saveOrUpdate(enrollment);
                message = "Enrollment rejected.";
            } else {
                message = "Enrollment is no longer pending and was not changed.";
            }
        } else {
            message = "Unsupported action.";
        }

        renderAdminEnrollmentPage(response, contextPath, message);
    }

    private void renderAdminEnrollmentPage(HttpServletResponse response, String contextPath, String message)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        List<ConnectWorkspace> activeWorkspaces = connectWorkspaceDao.findActiveOrderedByStartDate();
        Map<Long, String> topicNameById = new HashMap<>();
        for (IgTopic topic : igTopicDao.findAllOrdered()) {
            topicNameById.put(topic.getTopicId(), topic.getTopicName());
        }

        // Load all enrollments and related users in batches to avoid per-row queries.
        Map<Long, List<WorkspaceEnrollment>> enrollmentsByWorkspaceId = new HashMap<>();
        Set<Long> userIds = new HashSet<>();
        for (ConnectWorkspace workspace : activeWorkspaces) {
            List<WorkspaceEnrollment> enrollments = workspaceEnrollmentDao
                    .findByWorkspaceId(workspace.getWorkspaceId());
            enrollmentsByWorkspaceId.put(workspace.getWorkspaceId(), enrollments);
            for (WorkspaceEnrollment enrollment : enrollments) {
                if (enrollment.getUserId() != null) {
                    userIds.add(enrollment.getUserId());
                }
            }
        }

        Map<Long, User> userById = new HashMap<>();
        for (User user : userDao.findByIds(new ArrayList<>(userIds))) {
            userById.put(user.getUserId(), user);
        }

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Workspace Registrations - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Workspace Registrations</h1>");
            out.println("    <p>Approve or reject pending workspace registrations.</p>");
            if (message != null) {
                out.println("    <p><strong>" + escapeHtml(message) + "</strong></p>");
            }

            if (activeWorkspaces.isEmpty()) {
                out.println("    <p>No active workspaces are currently available.</p>");
            }

            for (ConnectWorkspace workspace : activeWorkspaces) {
                String topicName = topicNameById.get(workspace.getTopicId());
                if (topicName == null || topicName.isBlank()) {
                    topicName = "Unknown Topic";
                }
                String workspaceName = workspace.getWorkspaceName();
                if (workspaceName == null || workspaceName.isBlank()) {
                    workspaceName = "(Unnamed Workspace)";
                }

                out.println("    <h2>" + escapeHtml(topicName + ": " + workspaceName) + "</h2>");

                List<WorkspaceEnrollment> enrollments = new ArrayList<>(
                        enrollmentsByWorkspaceId.getOrDefault(workspace.getWorkspaceId(), List.of()));
                enrollments.sort(Comparator.comparing(enrollment -> toSortKey(userById.get(enrollment.getUserId()))));

                if (enrollments.isEmpty()) {
                    out.println("    <p>No registrations yet.</p>");
                    continue;
                }

                out.println("    <table class=\"data-table\">");
                out.println("      <thead>");
                out.println("        <tr>");
                out.println("          <th>Display Name</th>");
                out.println("          <th>Organization</th>");
                out.println("          <th>Email</th>");
                out.println("          <th>Action</th>");
                out.println("        </tr>");
                out.println("      </thead>");
                out.println("      <tbody>");
                for (WorkspaceEnrollment enrollment : enrollments) {
                    User user = userById.get(enrollment.getUserId());
                    String displayName = user == null ? "" : trimToNull(user.getDisplayName());
                    if (displayName == null) {
                        displayName = user == null ? "(Unknown User)" : orEmpty(user.getEmail());
                    }
                    String organization = user == null ? "" : orEmpty(user.getOrganization());
                    String email = user == null ? "" : orEmpty(user.getEmail());

                    out.println("        <tr>");
                    out.println("          <td>" + escapeHtml(displayName) + "</td>");
                    out.println("          <td>" + escapeHtml(organization) + "</td>");
                    out.println("          <td>" + escapeHtml(email) + "</td>");
                    out.println("          <td>");
                    if (enrollment.getState() == WorkspaceEnrollment.EnrollmentState.PENDING) {
                        out.println("            <form style=\"display:inline\" action=\"" + contextPath
                                + "/workspace\" method=\"post\">");
                        out.println(
                                "              <input type=\"hidden\" name=\"mode\" value=\"admin-enrollments\" />");
                        out.println("              <input type=\"hidden\" name=\"enrollmentId\" value=\""
                                + enrollment.getEnrollmentId() + "\" />");
                        out.println("              <input type=\"hidden\" name=\"action\" value=\"approve\" />");
                        out.println("              <button type=\"submit\">Approve</button>");
                        out.println("            </form>");

                        out.println("            <form style=\"display:inline\" action=\"" + contextPath
                                + "/workspace\" method=\"post\">");
                        out.println(
                                "              <input type=\"hidden\" name=\"mode\" value=\"admin-enrollments\" />");
                        out.println("              <input type=\"hidden\" name=\"enrollmentId\" value=\""
                                + enrollment.getEnrollmentId() + "\" />");
                        out.println("              <input type=\"hidden\" name=\"action\" value=\"reject\" />");
                        out.println("              <button type=\"submit\">Reject</button>");
                        out.println("            </form>");
                    } else {
                        out.println("            " + escapeHtml(enrollment.getState().name()));
                    }
                    out.println("          </td>");
                    out.println("        </tr>");
                }
                out.println("      </tbody>");
                out.println("    </table>");
            }

            out.println("    <p><a href=\"" + contextPath + "/welcome\">Back to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderWorkspaceCenter(HttpServletResponse response, String contextPath, User user,
            String workspaceIdRaw,
            String submittedMessage, Set<Long> selectedWorkspaceTermIds, Map<String, String> fieldErrors,
            String displayName, String organization, String roleTitle) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        Long workspaceId = parseId(workspaceIdRaw);
        if (workspaceId == null) {
            renderError(response, contextPath, "A valid workspaceId is required.");
            return;
        }

        ConnectWorkspace workspace = connectWorkspaceDao.findById(workspaceId).orElse(null);
        if (workspace == null) {
            renderError(response, contextPath, "Workspace entry was not found.");
            return;
        }

        IgTopic topic = igTopicDao.findById(workspace.getTopicId()).orElse(null);
        if (topic == null) {
            renderError(response, contextPath, "Associated topic entry was not found.");
            return;
        }

        WorkspaceEnrollment enrollment = workspaceEnrollmentDao
                .findByWorkspaceAndUser(workspaceId, user.getUserId())
                .orElse(null);

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Workspace Center - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Workspace Center</h1>");

            if (submittedMessage != null) {
                out.println("    <p><strong>" + escapeHtml(submittedMessage) + "</strong></p>");
            }

            if (enrollment == null) {
                out.println("    <p>You are not currently enrolled in this workspace.</p>");
                out.println("    <form class=\"login-form\" action=\"" + contextPath + "/workspace\" method=\"post\">");
                out.println("      <input type=\"hidden\" name=\"workspaceId\" value=\"" + workspace.getWorkspaceId()
                        + "\" />");
                out.println("      <input type=\"hidden\" name=\"action\" value=\"join\" />");
                List<LegalTerm> workspaceTerms = loadWorkspaceJoinTerms();

                Set<Long> resolvedSelectedWorkspaceTermIds = applyPreviouslyAcceptedBothTerms(user, workspaceTerms,
                    selectedWorkspaceTermIds);

                out.println("      <section>");
                out.println("        <h3>Your Profile</h3>");
                out.println("      </section>");

                out.println("      <label for=\"displayName\">Display Name" + renderFieldError(fieldErrors, "displayName")
                    + "</label>");
                out.println("      <div class=\"field-hint\">Your first and last name for others to see</div>");
                out.println("      <input id=\"displayName\" name=\"displayName\" type=\"text\" required maxlength=\""
                    + MAX_DISPLAY_NAME_LENGTH + "\" value=\"" + escapeHtml(orEmpty(displayName)) + "\" />");

                out.println("      <label for=\"organization\">Organization" + renderFieldError(fieldErrors, "organization")
                    + "</label>");
                out.println("      <div class=\"field-hint\">Full name of organization you are associated with</div>");
                out.println("      <input id=\"organization\" name=\"organization\" type=\"text\" required maxlength=\""
                    + MAX_ORGANIZATION_LENGTH + "\" value=\"" + escapeHtml(orEmpty(organization)) + "\" />");

                out.println("      <label for=\"roleTitle\">Role Title" + renderFieldError(fieldErrors, "roleTitle")
                    + "</label>");
                out.println("      <input id=\"roleTitle\" name=\"roleTitle\" type=\"text\" required maxlength=\""
                    + MAX_ROLE_TITLE_LENGTH + "\" value=\"" + escapeHtml(orEmpty(roleTitle)) + "\" />");

                if (!workspaceTerms.isEmpty()) {
                    out.println("      <section>");
                    out.println("        <h3>Request To Join Terms</h3>");
                    LegalTermsUiRenderer.renderTermsSection(out, workspaceTerms, resolvedSelectedWorkspaceTermIds,
                            "Workspace Terms", "workspaceLegalTerm_");
                    out.println("      </section>");
                }
                out.println("      <div class=\"form-actions\">");
                out.println("        <button type=\"submit\">Request To Join</button>");
                out.println("        <a class=\"button-link\" href=\"" + contextPath + "/welcome\">Cancel</a>");
                out.println("      </div>");
                out.println("    </form>");
                LegalTermsUiRenderer.renderTermsScript(out);
            } else if (enrollment.getState() == WorkspaceEnrollment.EnrollmentState.APPROVED) {
                out.println("    <h2>"
                        + escapeHtml(orEmpty(topic.getTopicName()) + ": " + orEmpty(workspace.getWorkspaceName()))
                        + "</h2>");
                out.println("    <p>" + escapeHtml(orEmpty(workspace.getDescription())) + "</p>");

                List<WorkspaceSystem> systems = workspaceSystemDao
                        .findByWorkspaceIdAndContactUserId(workspaceId, user.getUserId());

                out.println("    <h3>Your Registered Systems</h3>");
                if (systems.isEmpty()) {
                    out.println("    <p>You have no systems registered in this workspace yet.</p>");
                } else {
                    out.println("    <table class=\"data-table\">");
                    out.println("      <thead>");
                    out.println("        <tr>");
                    out.println("          <th>System Name</th>");
                    out.println("          <th>Capability</th>");
                    out.println("          <th>Availability</th>");
                    out.println("          <th>Actions</th>");
                    out.println("        </tr>");
                    out.println("      </thead>");
                    out.println("      <tbody>");
                    for (WorkspaceSystem sys : systems) {
                        String sysName = orEmpty(sys.getSystemName());
                        String cap = sys.getCapability() == null ? "" : sys.getCapability().name();
                        String avail = sys.getAvailability() == null ? "" : sys.getAvailability().name();
                        out.println("        <tr>");
                        out.println("          <td>" + escapeHtml(sysName) + "</td>");
                        out.println("          <td>" + escapeHtml(cap) + "</td>");
                        out.println("          <td>" + escapeHtml(avail) + "</td>");
                        out.println("          <td>");
                        out.println("            <a href=\"" + contextPath + "/workspace/system?systemId="
                                + sys.getSystemId() + "\">View</a>");
                        out.println("            &nbsp;|&nbsp;");
                        out.println("            <a href=\"" + contextPath + "/workspace/system?systemId="
                                + sys.getSystemId() + "&amp;mode=edit\">Edit</a>");
                        out.println("          </td>");
                        out.println("        </tr>");
                    }
                    out.println("      </tbody>");
                    out.println("    </table>");
                }
                out.println("    <p><a href=\"" + contextPath + "/workspace/system?workspaceId="
                        + workspaceId + "\">+ Add New System</a></p>");
            } else if (enrollment.getState() == WorkspaceEnrollment.EnrollmentState.PENDING) {
                out.println("    <p>Your enrollment status is <strong>PENDING</strong>. Please wait for approval.</p>");
            } else if (enrollment.getState() == WorkspaceEnrollment.EnrollmentState.REJECTED) {
                out.println("    <p>Your enrollment status is <strong>REJECTED</strong>.</p>");
            } else if (enrollment.getState() == WorkspaceEnrollment.EnrollmentState.SUSPENDED) {
                out.println("    <p>Your enrollment status is <strong>SUSPENDED</strong>.</p>");
            } else {
                out.println("    <p>Your enrollment status is <strong>" + escapeHtml(enrollment.getState().name())
                        + "</strong>.</p>");
            }

            out.println("    <p><a href=\"" + contextPath + "/welcome\">Back to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void applyUserProfileUpdates(User user, String displayName, String organization, String roleTitle) {
        if (user == null) {
            return;
        }
        user.setDisplayName(displayName);
        user.setOrganization(organization);
        user.setRoleTitle(roleTitle);
        userDao.saveOrUpdate(user);
    }

    private Map<String, String> validateProfileFields(String displayName, String organization, String roleTitle) {
        Map<String, String> fieldErrors = new HashMap<>();

        if (!isValidDisplayName(displayName)) {
            fieldErrors.put("displayName",
                    "Enter your full first and last name (2+ letters each, max " + MAX_DISPLAY_NAME_LENGTH + ").");
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

    private boolean isValidDisplayName(String displayName) {
        if (displayName == null || displayName.length() < 5 || displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            return false;
        }
        if (!containsOnlySafeDisplayNameChars(displayName)) {
            return false;
        }

        String[] nameParts = displayName.split("\\s+");
        if (nameParts.length < 2) {
            return false;
        }

        String firstName = nameParts[0];
        String lastName = nameParts[nameParts.length - 1];
        return countLetters(firstName) >= 2 && countLetters(lastName) >= 2;
    }

    private boolean isValidOrganization(String organization) {
        return organization != null
                && organization.length() >= 3
                && organization.length() <= MAX_ORGANIZATION_LENGTH
                && containsOnlySafeTextChars(organization, false);
    }

    private boolean isValidRoleTitle(String roleTitle) {
        return roleTitle != null
                && roleTitle.length() >= 3
                && roleTitle.length() <= MAX_ROLE_TITLE_LENGTH
                && containsOnlySafeTextChars(roleTitle, false);
    }

    private boolean containsOnlySafeTextChars(String value, boolean requireLetter) {
        boolean hasLetter = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isISOControl(ch)) {
                return false;
            }
            if (Character.isLetter(ch)) {
                hasLetter = true;
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
        return !requireLetter || hasLetter;
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

    private Set<Long> applyPreviouslyAcceptedBothTerms(User user, List<LegalTerm> terms, Set<Long> selectedIds) {
        Set<Long> resolved = new HashSet<>();
        if (selectedIds != null) {
            resolved.addAll(selectedIds);
        }
        if (user == null || user.getUserId() == null || terms == null || terms.isEmpty()) {
            return resolved;
        }

        for (LegalTerm term : terms) {
            if (term.getTermId() == null || term.getScopeType() != LegalTerm.ScopeType.BOTH) {
                continue;
            }

            boolean alreadyAcceptedGlobally = legalTermAcceptanceDao
                    .findByTermUserWorkspace(term.getTermId(), user.getUserId(), null)
                    .isPresent();
            if (alreadyAcceptedGlobally) {
                resolved.add(term.getTermId());
            }
        }

        return resolved;
    }

    private String renderFieldError(Map<String, String> fieldErrors, String fieldName) {
        if (fieldErrors == null) {
            return "";
        }
        String message = fieldErrors.get(fieldName);
        if (message == null || message.isBlank()) {
            return "";
        }
        return " <span class=\"field-error\">" + escapeHtml(message) + "</span>";
    }

    private void renderError(HttpServletResponse response, String contextPath, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Workspace Center - Error</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Workspace Request Error</h1>");
            out.println("    <p>" + escapeHtml(message) + "</p>");
            out.println("    <p><a href=\"" + contextPath + "/welcome\">Back to Welcome</a></p>");
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
            out.println("    <p>You must be an InteropHub admin to manage workspace registrations.</p>");
            out.println("    <p><a href=\"" + contextPath + "/welcome\">Back to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private String toSortKey(User user) {
        if (user == null) {
            return "";
        }
        String displayName = trimToNull(user.getDisplayName());
        if (displayName != null) {
            return displayName.toLowerCase();
        }
        return orEmpty(user.getEmail()).toLowerCase();
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<LegalTerm> loadWorkspaceJoinTerms() {
        try {
            return legalTermDao.findActiveForScope(LegalTerm.ScopeType.WORKSPACE);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Set<Long> parseSelectedWorkspaceTermIds(HttpServletRequest request) {
        Set<Long> selectedIds = new HashSet<>();
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            if (paramName == null || !paramName.startsWith("workspaceLegalTerm_")) {
                continue;
            }
            if (request.getParameter(paramName) == null) {
                continue;
            }
            String suffix = paramName.substring("workspaceLegalTerm_".length());
            try {
                selectedIds.add(Long.valueOf(suffix));
            } catch (Exception ignored) {
                // Ignore malformed term id suffixes.
            }
        }
        return selectedIds;
    }

    private boolean allRequiredTermsAccepted(List<LegalTerm> terms, Set<Long> selectedIds) {
        for (LegalTerm term : terms) {
            if (term.getTermId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(term.getRequired()) && !selectedIds.contains(term.getTermId())) {
                return false;
            }
        }
        return true;
    }

    private void saveTermAcceptancesForWorkspace(User user, Long workspaceId, List<LegalTerm> terms,
            HttpServletRequest request) {
        if (user == null || user.getUserId() == null || workspaceId == null || terms == null || terms.isEmpty()) {
            return;
        }
        String ipAddress = trimToNull(request.getRemoteAddr());
        String userAgent = trimToNull(request.getHeader("User-Agent"));
        for (LegalTerm term : terms) {
            if (term.getTermId() == null) {
                continue;
            }
            LegalTermAcceptance acceptance = legalTermAcceptanceDao
                    .findByTermUserWorkspace(term.getTermId(), user.getUserId(), workspaceId)
                    .orElseGet(LegalTermAcceptance::new);
            acceptance.setTermId(term.getTermId());
            acceptance.setUserId(user.getUserId());
            acceptance.setWorkspaceId(workspaceId);
            acceptance.setAcceptedValue(Boolean.TRUE);
            acceptance.setAcceptedAt(LocalDateTime.now());
            acceptance.setIpAddress(ipAddress);
            acceptance.setUserAgent(userAgent);
            legalTermAcceptanceDao.saveOrUpdate(acceptance);
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
