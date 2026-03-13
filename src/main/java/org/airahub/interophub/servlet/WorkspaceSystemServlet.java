package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.ConnectWorkspaceDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.dao.WorkspaceEndpointDao;
import org.airahub.interophub.dao.WorkspaceEnrollmentDao;
import org.airahub.interophub.dao.WorkspaceSystemContactDao;
import org.airahub.interophub.dao.WorkspaceSystemDao;
import org.airahub.interophub.model.ConnectWorkspace;
import org.airahub.interophub.model.User;
import org.airahub.interophub.model.WorkspaceEndpoint;
import org.airahub.interophub.model.WorkspaceEnrollment;
import org.airahub.interophub.model.WorkspaceSystem;
import org.airahub.interophub.model.WorkspaceSystemContact;
import org.airahub.interophub.service.AuthFlowService;

/**
 * Handles add/edit/view for workspace systems.
 *
 * <p>
 * URL patterns:
 * <ul>
 * <li>GET /workspace/system?workspaceId=X – new system form</li>
 * <li>GET /workspace/system?systemId=X – view system details + endpoints</li>
 * <li>GET /workspace/system?systemId=X&mode=edit – edit system form</li>
 * <li>POST /workspace/system (action=save) – create or update system</li>
 * <li>POST /workspace/system (action=add-contact) – add a workspace
 * contact</li>
 * <li>POST /workspace/system (action=remove-contact) – remove a workspace
 * contact</li>
 * </ul>
 */
public class WorkspaceSystemServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final ConnectWorkspaceDao connectWorkspaceDao;
    private final WorkspaceSystemDao workspaceSystemDao;
    private final WorkspaceSystemContactDao workspaceSystemContactDao;
    private final WorkspaceEnrollmentDao workspaceEnrollmentDao;
    private final WorkspaceEndpointDao workspaceEndpointDao;
    private final UserDao userDao;

    public WorkspaceSystemServlet() {
        this.authFlowService = new AuthFlowService();
        this.connectWorkspaceDao = new ConnectWorkspaceDao();
        this.workspaceSystemDao = new WorkspaceSystemDao();
        this.workspaceSystemContactDao = new WorkspaceSystemContactDao();
        this.workspaceEnrollmentDao = new WorkspaceEnrollmentDao();
        this.workspaceEndpointDao = new WorkspaceEndpointDao();
        this.userDao = new UserDao();
    }

    // -------------------------------------------------------------------------
    // GET
    // -------------------------------------------------------------------------

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> maybeUser = requireAuthenticated(request, response);
        if (maybeUser.isEmpty())
            return;
        User currentUser = maybeUser.get();

        String contextPath = request.getContextPath();
        String systemIdRaw = trimToNull(request.getParameter("systemId"));
        String workspaceIdRaw = trimToNull(request.getParameter("workspaceId"));
        String mode = trimToNull(request.getParameter("mode"));
        String savedParam = trimToNull(request.getParameter("saved"));

        if (systemIdRaw != null) {
            Long systemId = parseId(systemIdRaw);
            if (systemId == null) {
                renderError(response, contextPath, "Invalid system identifier.", null);
                return;
            }

            WorkspaceSystem system = workspaceSystemDao.findById(systemId).orElse(null);
            if (system == null) {
                renderError(response, contextPath, "System not found.", null);
                return;
            }

            // Verify approved enrollment in the system's workspace.
            WorkspaceEnrollment enrollment = workspaceEnrollmentDao
                    .findByWorkspaceAndUser(system.getWorkspaceId(), currentUser.getUserId()).orElse(null);
            if (enrollment == null || enrollment.getState() != WorkspaceEnrollment.EnrollmentState.APPROVED) {
                renderForbidden(response, contextPath);
                return;
            }

            if ("edit".equalsIgnoreCase(mode)) {
                // Only contacts on this system may edit it.
                boolean isContact = workspaceSystemContactDao
                        .findBySystemAndUser(systemId, currentUser.getUserId()).isPresent();
                if (!isContact && !authFlowService.isAdminUser(currentUser)) {
                    renderForbidden(response, contextPath);
                    return;
                }
                String message = savedParam != null ? "Contact updated." : null;
                renderEditForm(response, contextPath, currentUser, system, message);
            } else {
                String message = savedParam != null ? "System saved successfully." : null;
                renderView(response, contextPath, currentUser, system, message);
            }
            return;
        }

        if (workspaceIdRaw != null) {
            Long workspaceId = parseId(workspaceIdRaw);
            if (workspaceId == null) {
                renderError(response, contextPath, "Invalid workspace identifier.", null);
                return;
            }
            ConnectWorkspace workspace = connectWorkspaceDao.findById(workspaceId).orElse(null);
            if (workspace == null) {
                renderError(response, contextPath, "Workspace not found.", null);
                return;
            }
            // Require approved enrollment to add a system.
            WorkspaceEnrollment enrollment = workspaceEnrollmentDao
                    .findByWorkspaceAndUser(workspaceId, currentUser.getUserId()).orElse(null);
            if (enrollment == null || enrollment.getState() != WorkspaceEnrollment.EnrollmentState.APPROVED) {
                renderForbidden(response, contextPath);
                return;
            }
            WorkspaceSystem newSystem = new WorkspaceSystem();
            newSystem.setWorkspaceId(workspaceId);
            newSystem.setCapability(WorkspaceSystem.Capability.BOTH);
            newSystem.setAvailability(WorkspaceSystem.Availability.UNKNOWN);
            newSystem.setManagedBy(WorkspaceSystem.ManagedBy.THIRD_PARTY);
            renderAddForm(response, contextPath, workspace, newSystem, null);
            return;
        }

        renderError(response, contextPath, "A systemId or workspaceId parameter is required.", null);
    }

    // -------------------------------------------------------------------------
    // POST
    // -------------------------------------------------------------------------

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> maybeUser = requireAuthenticated(request, response);
        if (maybeUser.isEmpty())
            return;
        User currentUser = maybeUser.get();

        String contextPath = request.getContextPath();
        String action = trimToNull(request.getParameter("action"));
        String systemIdRaw = trimToNull(request.getParameter("systemId"));
        String workspaceIdRaw = trimToNull(request.getParameter("workspaceId"));

        if ("remove-contact".equalsIgnoreCase(action)) {
            handleRemoveContact(request, response, contextPath, currentUser, systemIdRaw);
            return;
        }

        if ("add-contact".equalsIgnoreCase(action)) {
            handleAddContact(request, response, contextPath, currentUser, systemIdRaw);
            return;
        }

        // Default: save system fields.
        if (systemIdRaw != null) {
            handleUpdateSystem(request, response, contextPath, currentUser, systemIdRaw);
        } else if (workspaceIdRaw != null) {
            handleCreateSystem(request, response, contextPath, currentUser, workspaceIdRaw);
        } else {
            renderError(response, contextPath, "A systemId or workspaceId parameter is required.", null);
        }
    }

    // -------------------------------------------------------------------------
    // POST handlers
    // -------------------------------------------------------------------------

    private void handleCreateSystem(HttpServletRequest request, HttpServletResponse response,
            String contextPath, User currentUser, String workspaceIdRaw) throws IOException {

        Long workspaceId = parseId(workspaceIdRaw);
        if (workspaceId == null) {
            renderError(response, contextPath, "Invalid workspace identifier.", null);
            return;
        }
        ConnectWorkspace workspace = connectWorkspaceDao.findById(workspaceId).orElse(null);
        if (workspace == null) {
            renderError(response, contextPath, "Workspace not found.", null);
            return;
        }
        WorkspaceEnrollment enrollment = workspaceEnrollmentDao
                .findByWorkspaceAndUser(workspaceId, currentUser.getUserId()).orElse(null);
        if (enrollment == null || enrollment.getState() != WorkspaceEnrollment.EnrollmentState.APPROVED) {
            renderForbidden(response, contextPath);
            return;
        }

        WorkspaceSystem system = new WorkspaceSystem();
        system.setWorkspaceId(workspaceId);
        system.setCreatedByUserId(currentUser.getUserId());

        try {
            bindSystemFields(request, system);
            system = workspaceSystemDao.saveOrUpdate(system);

            // Automatically add the current user as a contact.
            WorkspaceSystemContact selfContact = new WorkspaceSystemContact();
            selfContact.setSystemId(system.getSystemId());
            selfContact.setUserId(currentUser.getUserId());
            selfContact.setContactRole("Primary Contact");
            workspaceSystemContactDao.save(selfContact);

            // Optionally add a second contact if selected.
            addOptionalContact(request, system.getSystemId(), currentUser.getUserId());

            response.sendRedirect(contextPath + "/workspace/system?systemId=" + system.getSystemId() + "&saved=1");
        } catch (Exception ex) {
            renderAddForm(response, contextPath, workspace, system, "Could not save: " + ex.getMessage());
        }
    }

    private void handleUpdateSystem(HttpServletRequest request, HttpServletResponse response,
            String contextPath, User currentUser, String systemIdRaw) throws IOException {

        Long systemId = parseId(systemIdRaw);
        if (systemId == null) {
            renderError(response, contextPath, "Invalid system identifier.", null);
            return;
        }
        WorkspaceSystem system = workspaceSystemDao.findById(systemId).orElse(null);
        if (system == null) {
            renderError(response, contextPath, "System not found.", null);
            return;
        }
        WorkspaceEnrollment enrollment = workspaceEnrollmentDao
                .findByWorkspaceAndUser(system.getWorkspaceId(), currentUser.getUserId()).orElse(null);
        if (enrollment == null || enrollment.getState() != WorkspaceEnrollment.EnrollmentState.APPROVED) {
            renderForbidden(response, contextPath);
            return;
        }
        boolean isContact = workspaceSystemContactDao
                .findBySystemAndUser(systemId, currentUser.getUserId()).isPresent();
        if (!isContact && !authFlowService.isAdminUser(currentUser)) {
            renderForbidden(response, contextPath);
            return;
        }

        try {
            bindSystemFields(request, system);
            system = workspaceSystemDao.saveOrUpdate(system);

            // Optionally add a new contact.
            addOptionalContact(request, system.getSystemId(), currentUser.getUserId());

            response.sendRedirect(contextPath + "/workspace/system?systemId=" + system.getSystemId() + "&saved=1");
        } catch (Exception ex) {
            renderEditForm(response, contextPath, currentUser, system, "Could not save: " + ex.getMessage());
        }
    }

    private void handleAddContact(HttpServletRequest request, HttpServletResponse response,
            String contextPath, User currentUser, String systemIdRaw) throws IOException {

        Long systemId = parseId(systemIdRaw);
        if (systemId == null) {
            renderError(response, contextPath, "Invalid system identifier.", null);
            return;
        }
        WorkspaceSystem system = workspaceSystemDao.findById(systemId).orElse(null);
        if (system == null) {
            renderError(response, contextPath, "System not found.", null);
            return;
        }
        boolean isContact = workspaceSystemContactDao
                .findBySystemAndUser(systemId, currentUser.getUserId()).isPresent();
        if (!isContact && !authFlowService.isAdminUser(currentUser)) {
            renderForbidden(response, contextPath);
            return;
        }

        Long addUserId = parseId(trimToNull(request.getParameter("addUserId")));
        if (addUserId != null && !addUserId.equals(currentUser.getUserId())) {
            boolean alreadyContact = workspaceSystemContactDao
                    .findBySystemAndUser(systemId, addUserId).isPresent();
            if (!alreadyContact) {
                WorkspaceSystemContact contact = new WorkspaceSystemContact();
                contact.setSystemId(systemId);
                contact.setUserId(addUserId);
                contact.setContactRole(trimToNull(request.getParameter("addRole")));
                workspaceSystemContactDao.save(contact);
            }
        }

        response.sendRedirect(contextPath + "/workspace/system?systemId=" + systemId + "&mode=edit&saved=1");
    }

    private void handleRemoveContact(HttpServletRequest request, HttpServletResponse response,
            String contextPath, User currentUser, String systemIdRaw) throws IOException {

        Long systemId = parseId(systemIdRaw);
        if (systemId == null) {
            renderError(response, contextPath, "Invalid system identifier.", null);
            return;
        }
        WorkspaceSystem system = workspaceSystemDao.findById(systemId).orElse(null);
        if (system == null) {
            renderError(response, contextPath, "System not found.", null);
            return;
        }
        boolean isContact = workspaceSystemContactDao
                .findBySystemAndUser(systemId, currentUser.getUserId()).isPresent();
        if (!isContact && !authFlowService.isAdminUser(currentUser)) {
            renderForbidden(response, contextPath);
            return;
        }

        Long contactId = parseId(trimToNull(request.getParameter("contactId")));
        if (contactId != null) {
            WorkspaceSystemContact target = workspaceSystemContactDao.findById(contactId).orElse(null);
            // Prevent removal of the current user's own contact record.
            if (target != null && target.getSystemId().equals(systemId)
                    && !target.getUserId().equals(currentUser.getUserId())) {
                workspaceSystemContactDao.deleteById(contactId);
            }
        }

        response.sendRedirect(contextPath + "/workspace/system?systemId=" + systemId + "&mode=edit&saved=1");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void bindSystemFields(HttpServletRequest request, WorkspaceSystem system) {
        system.setSystemName(required(trimToNull(request.getParameter("systemName")), "System name"));
        system.setCapability(parseEnum(WorkspaceSystem.Capability.class,
                required(trimToNull(request.getParameter("capability")), "Capability")));
        system.setManagedBy(parseEnum(WorkspaceSystem.ManagedBy.class,
                required(trimToNull(request.getParameter("managedBy")), "Managed by")));
        system.setAvailability(parseEnum(WorkspaceSystem.Availability.class,
                required(trimToNull(request.getParameter("availability")), "Availability")));
        system.setAvailabilityNote(trimToNull(request.getParameter("availabilityNote")));
        system.setDescription(trimToNull(request.getParameter("description")));
        system.setHowToUse(trimToNull(request.getParameter("howToUse")));
        system.setLimitations(trimToNull(request.getParameter("limitations")));
    }

    private void addOptionalContact(HttpServletRequest request, Long systemId, Long currentUserId) {
        Long addUserId = parseId(trimToNull(request.getParameter("addUserId")));
        if (addUserId != null && !addUserId.equals(currentUserId)) {
            boolean alreadyContact = workspaceSystemContactDao
                    .findBySystemAndUser(systemId, addUserId).isPresent();
            if (!alreadyContact) {
                WorkspaceSystemContact contact = new WorkspaceSystemContact();
                contact.setSystemId(systemId);
                contact.setUserId(addUserId);
                contact.setContactRole(trimToNull(request.getParameter("addRole")));
                workspaceSystemContactDao.save(contact);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Renderers
    // -------------------------------------------------------------------------

    private void renderAddForm(HttpServletResponse response, String contextPath,
            ConnectWorkspace workspace, WorkspaceSystem system, String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Add System - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Add New System</h1>");
            if (message != null) {
                out.println("    <p class=\"error\"><strong>" + escapeHtml(message) + "</strong></p>");
            }
            out.println("    <form action=\"" + contextPath + "/workspace/system\" method=\"post\">");
            out.println("      <input type=\"hidden\" name=\"workspaceId\" value=\""
                    + workspace.getWorkspaceId() + "\" />");
            renderSystemFields(out, system);
            out.println("      <p><button type=\"submit\">Save System</button></p>");
            out.println("    </form>");
            out.println("    <p><a href=\"" + contextPath + "/workspace?workspaceId="
                    + workspace.getWorkspaceId() + "\">Back to Workspace</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderEditForm(HttpServletResponse response, String contextPath,
            User currentUser, WorkspaceSystem system, String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        List<WorkspaceSystemContact> contacts = workspaceSystemContactDao.findBySystemId(system.getSystemId());
        Set<Long> contactUserIds = contacts.stream()
                .map(WorkspaceSystemContact::getUserId).collect(Collectors.toSet());
        Map<Long, User> userById = loadUsers(new ArrayList<>(contactUserIds));

        // Approved workspace members eligible to be added as contacts.
        List<WorkspaceEnrollment> approvedEnrollments = workspaceEnrollmentDao
                .findByWorkspaceId(system.getWorkspaceId()).stream()
                .filter(e -> e.getState() == WorkspaceEnrollment.EnrollmentState.APPROVED)
                .collect(Collectors.toList());
        Set<Long> approvedUserIds = approvedEnrollments.stream()
                .map(WorkspaceEnrollment::getUserId).collect(Collectors.toSet());
        // Remove those already contacts or the current user (already a contact).
        approvedUserIds.removeAll(contactUserIds);
        Map<Long, User> candidateUsers = loadUsers(new ArrayList<>(approvedUserIds));

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Edit System - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Edit System</h1>");
            if (message != null) {
                out.println("    <p><strong>" + escapeHtml(message) + "</strong></p>");
            }
            out.println("    <form action=\"" + contextPath + "/workspace/system\" method=\"post\">");
            out.println("      <input type=\"hidden\" name=\"systemId\" value=\"" + system.getSystemId() + "\" />");
            renderSystemFields(out, system);

            // Add one additional contact in the same submit.
            if (!candidateUsers.isEmpty()) {
                out.println("      <fieldset>");
                out.println("        <legend>Add a Contact</legend>");
                out.println("        <label>User:<br />");
                out.println("          <select name=\"addUserId\">");
                out.println("            <option value=\"\">(none)</option>");
                for (User u : candidateUsers.values().stream()
                        .sorted((a, b) -> userDisplayName(a).compareToIgnoreCase(userDisplayName(b)))
                        .collect(Collectors.toList())) {
                    out.println("            <option value=\"" + u.getUserId() + "\">"
                            + escapeHtml(userDisplayName(u)) + "</option>");
                }
                out.println("          </select>");
                out.println("        </label>&nbsp;");
                out.println("        <label>Role (optional):<br />");
                out.println("          <input type=\"text\" name=\"addRole\" maxlength=\"120\" />");
                out.println("        </label>");
                out.println("      </fieldset>");
            }

            out.println("      <p><button type=\"submit\">Save</button></p>");
            out.println("    </form>");

            // Current contacts list.
            out.println("    <h3>Current Contacts</h3>");
            if (contacts.isEmpty()) {
                out.println("    <p>No contacts listed.</p>");
            } else {
                out.println("    <table class=\"data-table\">");
                out.println("      <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Action</th></tr></thead>");
                out.println("      <tbody>");
                for (WorkspaceSystemContact c : contacts) {
                    User u = userById.get(c.getUserId());
                    boolean isSelf = c.getUserId().equals(currentUser.getUserId());
                    out.println("        <tr>");
                    out.println("          <td>" + escapeHtml(u == null ? "" : userDisplayName(u)) + "</td>");
                    out.println("          <td>" + escapeHtml(u == null ? "" : orEmpty(u.getEmail())) + "</td>");
                    out.println("          <td>" + escapeHtml(orEmpty(c.getContactRole())) + "</td>");
                    out.println("          <td>");
                    if (!isSelf) {
                        out.println("            <form style=\"display:inline\" method=\"post\" action=\""
                                + contextPath + "/workspace/system\">");
                        out.println("              <input type=\"hidden\" name=\"systemId\" value=\""
                                + system.getSystemId() + "\" />");
                        out.println("              <input type=\"hidden\" name=\"action\" value=\"remove-contact\" />");
                        out.println("              <input type=\"hidden\" name=\"contactId\" value=\""
                                + c.getSystemContactId() + "\" />");
                        out.println("              <button type=\"submit\">Remove</button>");
                        out.println("            </form>");
                    } else {
                        out.println("            (you)");
                    }
                    out.println("          </td>");
                    out.println("        </tr>");
                }
                out.println("      </tbody>");
                out.println("    </table>");
            }

            out.println("    <p><a href=\"" + contextPath + "/workspace/system?systemId="
                    + system.getSystemId() + "\">View System</a></p>");
            out.println("    <p><a href=\"" + contextPath + "/workspace?workspaceId="
                    + system.getWorkspaceId() + "\">Back to Workspace</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderView(HttpServletResponse response, String contextPath,
            User currentUser, WorkspaceSystem system, String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        List<WorkspaceSystemContact> contacts = workspaceSystemContactDao.findBySystemId(system.getSystemId());
        Set<Long> contactUserIds = contacts.stream()
                .map(WorkspaceSystemContact::getUserId).collect(Collectors.toSet());
        Map<Long, User> userById = loadUsers(new ArrayList<>(contactUserIds));

        List<WorkspaceEndpoint> endpoints = workspaceEndpointDao.findBySystemId(system.getSystemId());

        boolean isContact = contacts.stream().anyMatch(c -> c.getUserId().equals(currentUser.getUserId()));
        boolean canEdit = isContact || authFlowService.isAdminUser(currentUser);

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>" + escapeHtml(orEmpty(system.getSystemName())) + " - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>" + escapeHtml(orEmpty(system.getSystemName())) + "</h1>");
            if (message != null) {
                out.println("    <p><strong>" + escapeHtml(message) + "</strong></p>");
            }

            // System details panel.
            out.println("    <section class=\"panel\">");
            out.println("      <p><strong>Capability:</strong> "
                    + escapeHtml(system.getCapability() == null ? "" : system.getCapability().name()) + "</p>");
            out.println("      <p><strong>Managed By:</strong> "
                    + escapeHtml(system.getManagedBy() == null ? "" : system.getManagedBy().name()) + "</p>");
            out.println("      <p><strong>Availability:</strong> "
                    + escapeHtml(system.getAvailability() == null ? "" : system.getAvailability().name()) + "</p>");
            if (system.getAvailabilityNote() != null) {
                out.println("      <p><strong>Availability Note:</strong> "
                        + escapeHtml(system.getAvailabilityNote()) + "</p>");
            }
            if (system.getDescription() != null) {
                out.println("      <p><strong>Description:</strong> "
                        + escapeHtml(system.getDescription()) + "</p>");
            }
            if (system.getHowToUse() != null) {
                out.println("      <p><strong>How to Use:</strong> "
                        + escapeHtml(system.getHowToUse()) + "</p>");
            }
            if (system.getLimitations() != null) {
                out.println("      <p><strong>Limitations:</strong> "
                        + escapeHtml(system.getLimitations()) + "</p>");
            }
            out.println("    </section>");

            if (canEdit) {
                out.println("    <p><a href=\"" + contextPath + "/workspace/system?systemId="
                        + system.getSystemId() + "&amp;mode=edit\">Edit System</a></p>");
            }

            // Contacts.
            out.println("    <h3>Contacts</h3>");
            if (contacts.isEmpty()) {
                out.println("    <p>No contacts listed.</p>");
            } else {
                out.println("    <table class=\"data-table\">");
                out.println("      <thead><tr><th>Name</th><th>Email</th><th>Role</th></tr></thead>");
                out.println("      <tbody>");
                for (WorkspaceSystemContact c : contacts) {
                    User u = userById.get(c.getUserId());
                    out.println("        <tr>");
                    out.println("          <td>" + escapeHtml(u == null ? "" : userDisplayName(u)) + "</td>");
                    out.println("          <td>" + escapeHtml(u == null ? "" : orEmpty(u.getEmail())) + "</td>");
                    out.println("          <td>" + escapeHtml(orEmpty(c.getContactRole())) + "</td>");
                    out.println("        </tr>");
                }
                out.println("      </tbody>");
                out.println("    </table>");
            }

            // Endpoints.
            out.println("    <h3>Endpoints</h3>");
            if (endpoints.isEmpty()) {
                out.println("    <p>No endpoints defined.</p>");
            } else {
                out.println("    <table class=\"data-table\">");
                out.println("      <thead>");
                out.println("        <tr>");
                out.println("          <th>Type</th>");
                out.println("          <th>URL</th>");
                out.println("          <th>Auth Type</th>");
                out.println("          <th>Active</th>");
                if (canEdit) {
                    out.println("          <th>Actions</th>");
                }
                out.println("        </tr>");
                out.println("      </thead>");
                out.println("      <tbody>");
                for (WorkspaceEndpoint ep : endpoints) {
                    out.println("        <tr>");
                    out.println("          <td>"
                            + escapeHtml(ep.getEndpointType() == null ? "" : ep.getEndpointType().name()) + "</td>");
                    out.println("          <td>" + escapeHtml(orEmpty(ep.getUrl())) + "</td>");
                    out.println("          <td>" + escapeHtml(ep.getAuthType() == null ? "" : ep.getAuthType().name())
                            + "</td>");
                    out.println("          <td>" + (Boolean.TRUE.equals(ep.getActive()) ? "Yes" : "No") + "</td>");
                    if (canEdit) {
                        out.println("          <td>");
                        out.println("            <a href=\"" + contextPath + "/workspace/endpoint?endpointId="
                                + ep.getEndpointId() + "\">Edit</a>");
                        out.println("            &nbsp;|&nbsp;");
                        out.println("            <form style=\"display:inline\" method=\"post\" action=\""
                                + contextPath + "/workspace/endpoint\">");
                        out.println("              <input type=\"hidden\" name=\"endpointId\" value=\""
                                + ep.getEndpointId() + "\" />");
                        out.println("              <input type=\"hidden\" name=\"action\" value=\"delete\" />");
                        out.println(
                                "              <button type=\"submit\" onclick=\"return confirm('Delete this endpoint?');\">Delete</button>");
                        out.println("            </form>");
                        out.println("          </td>");
                    }
                    out.println("        </tr>");
                }
                out.println("      </tbody>");
                out.println("    </table>");
            }
            if (canEdit) {
                out.println("    <p><a href=\"" + contextPath + "/workspace/endpoint?systemId="
                        + system.getSystemId() + "\">+ Add Endpoint</a></p>");
            }

            out.println("    <p><a href=\"" + contextPath + "/workspace?workspaceId="
                    + system.getWorkspaceId() + "\">Back to Workspace</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderSystemFields(PrintWriter out, WorkspaceSystem system) {
        out.println("      <p>");
        out.println("        <label>System Name (required):<br />");
        out.println("          <input type=\"text\" name=\"systemName\" maxlength=\"160\" required");
        out.println("                 value=\"" + escapeHtml(orEmpty(system.getSystemName())) + "\" />");
        out.println("        </label>");
        out.println("      </p>");

        out.println("      <p>");
        out.println("        <label>Capability (required):<br />");
        out.println("          <select name=\"capability\" required>");
        for (WorkspaceSystem.Capability c : WorkspaceSystem.Capability.values()) {
            String sel = c == system.getCapability() ? " selected" : "";
            out.println("            <option value=\"" + c.name() + "\"" + sel + ">" + c.name() + "</option>");
        }
        out.println("          </select>");
        out.println("        </label>");
        out.println("      </p>");

        out.println("      <p>");
        out.println("        <label>Managed By:<br />");
        out.println("          <select name=\"managedBy\">");
        for (WorkspaceSystem.ManagedBy m : WorkspaceSystem.ManagedBy.values()) {
            String sel = m == system.getManagedBy() ? " selected" : "";
            out.println("            <option value=\"" + m.name() + "\"" + sel + ">" + m.name() + "</option>");
        }
        out.println("          </select>");
        out.println("        </label>");
        out.println("      </p>");

        out.println("      <p>");
        out.println("        <label>Availability:<br />");
        out.println("          <select name=\"availability\">");
        for (WorkspaceSystem.Availability a : WorkspaceSystem.Availability.values()) {
            String sel = a == system.getAvailability() ? " selected" : "";
            out.println("            <option value=\"" + a.name() + "\"" + sel + ">" + a.name() + "</option>");
        }
        out.println("          </select>");
        out.println("        </label>");
        out.println("      </p>");

        out.println("      <p>");
        out.println("        <label>Availability Note:<br />");
        out.println("          <input type=\"text\" name=\"availabilityNote\" maxlength=\"400\"");
        out.println("                 value=\"" + escapeHtml(orEmpty(system.getAvailabilityNote())) + "\" />");
        out.println("        </label>");
        out.println("      </p>");

        out.println("      <p>");
        out.println("        <label>Description:<br />");
        out.println("          <textarea name=\"description\" rows=\"4\" cols=\"60\">"
                + escapeHtml(orEmpty(system.getDescription())) + "</textarea>");
        out.println("        </label>");
        out.println("      </p>");

        out.println("      <p>");
        out.println("        <label>How to Use:<br />");
        out.println("          <textarea name=\"howToUse\" rows=\"4\" cols=\"60\">"
                + escapeHtml(orEmpty(system.getHowToUse())) + "</textarea>");
        out.println("        </label>");
        out.println("      </p>");

        out.println("      <p>");
        out.println("        <label>Limitations:<br />");
        out.println("          <textarea name=\"limitations\" rows=\"3\" cols=\"60\">"
                + escapeHtml(orEmpty(system.getLimitations())) + "</textarea>");
        out.println("        </label>");
        out.println("      </p>");
    }

    // -------------------------------------------------------------------------
    // Auth helpers
    // -------------------------------------------------------------------------

    private Optional<User> requireAuthenticated(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Optional<User> user = authFlowService.findAuthenticatedUser(request);
        if (user.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
        }
        return user;
    }

    private void renderForbidden(HttpServletResponse response, String contextPath) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\" />");
            out.println("  <title>Access Denied - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" /></head>");
            out.println("<body><main class=\"container\"><h1>Access Denied</h1>");
            out.println("  <p>You do not have permission to access this resource.</p>");
            out.println("  <p><a href=\"" + contextPath + "/welcome\">Back to Welcome</a></p>");
            out.println("</main>");
            PageFooterRenderer.render(out);
            out.println("</body></html>");
        }
    }

    private void renderError(HttpServletResponse response, String contextPath,
            String message, String backUrl) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\" />");
            out.println("  <title>Error - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" /></head>");
            out.println("<body><main class=\"container\"><h1>Error</h1>");
            out.println("  <p>" + escapeHtml(message) + "</p>");
            String href = backUrl != null ? backUrl : contextPath + "/welcome";
            out.println("  <p><a href=\"" + escapeHtml(href) + "\">Go back</a></p>");
            out.println("</main>");
            PageFooterRenderer.render(out);
            out.println("</body></html>");
        }
    }

    // -------------------------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------------------------

    private Map<Long, User> loadUsers(List<Long> ids) {
        Map<Long, User> map = new HashMap<>();
        for (User u : userDao.findByIds(ids)) {
            map.put(u.getUserId(), u);
        }
        return map;
    }

    private String userDisplayName(User u) {
        if (u == null)
            return "";
        String name = trimToNull(u.getDisplayName());
        return name != null ? name : orEmpty(u.getEmail());
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value) {
        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid value '" + value + "' for " + enumClass.getSimpleName());
        }
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null)
            return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value;
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String escapeHtml(String value) {
        if (value == null)
            return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
