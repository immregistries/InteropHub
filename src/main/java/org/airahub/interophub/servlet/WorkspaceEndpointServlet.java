package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.WorkspaceEndpointDao;
import org.airahub.interophub.dao.WorkspaceEnrollmentDao;
import org.airahub.interophub.dao.WorkspaceSystemContactDao;
import org.airahub.interophub.dao.WorkspaceSystemDao;
import org.airahub.interophub.model.User;
import org.airahub.interophub.model.WorkspaceEndpoint;
import org.airahub.interophub.model.WorkspaceEnrollment;
import org.airahub.interophub.model.WorkspaceSystem;
import org.airahub.interophub.service.AuthFlowService;

/**
 * Handles add/edit/delete for workspace endpoints.
 *
 * <p>
 * URL patterns:
 * <ul>
 * <li>GET /workspace/endpoint?systemId=X – new endpoint form</li>
 * <li>GET /workspace/endpoint?endpointId=X – edit endpoint form</li>
 * <li>POST /workspace/endpoint – create or update endpoint</li>
 * <li>POST /workspace/endpoint (action=delete, endpointId=X) – delete
 * endpoint</li>
 * </ul>
 */
public class WorkspaceEndpointServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final WorkspaceSystemDao workspaceSystemDao;
    private final WorkspaceSystemContactDao workspaceSystemContactDao;
    private final WorkspaceEnrollmentDao workspaceEnrollmentDao;
    private final WorkspaceEndpointDao workspaceEndpointDao;

    public WorkspaceEndpointServlet() {
        this.authFlowService = new AuthFlowService();
        this.workspaceSystemDao = new WorkspaceSystemDao();
        this.workspaceSystemContactDao = new WorkspaceSystemContactDao();
        this.workspaceEnrollmentDao = new WorkspaceEnrollmentDao();
        this.workspaceEndpointDao = new WorkspaceEndpointDao();
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
        String endpointIdRaw = trimToNull(request.getParameter("endpointId"));
        String systemIdRaw = trimToNull(request.getParameter("systemId"));

        if (endpointIdRaw != null) {
            Long endpointId = parseId(endpointIdRaw);
            if (endpointId == null) {
                renderError(response, contextPath, "Invalid endpoint identifier.", null);
                return;
            }
            WorkspaceEndpoint endpoint = workspaceEndpointDao.findById(endpointId).orElse(null);
            if (endpoint == null) {
                renderError(response, contextPath, "Endpoint not found.", null);
                return;
            }
            WorkspaceSystem system = workspaceSystemDao.findById(endpoint.getSystemId()).orElse(null);
            if (system == null || !canEdit(currentUser, system)) {
                renderForbidden(response, contextPath);
                return;
            }
            renderForm(response, contextPath, system, endpoint, null);
            return;
        }

        if (systemIdRaw != null) {
            Long systemId = parseId(systemIdRaw);
            if (systemId == null) {
                renderError(response, contextPath, "Invalid system identifier.", null);
                return;
            }
            WorkspaceSystem system = workspaceSystemDao.findById(systemId).orElse(null);
            if (system == null || !canEdit(currentUser, system)) {
                renderForbidden(response, contextPath);
                return;
            }
            WorkspaceEndpoint newEndpoint = new WorkspaceEndpoint();
            newEndpoint.setSystemId(systemId);
            newEndpoint.setEndpointType(WorkspaceEndpoint.EndpointType.FHIR_BASE);
            newEndpoint.setAuthType(WorkspaceEndpoint.AuthType.BEARER_PAT);
            newEndpoint.setActive(Boolean.TRUE);
            renderForm(response, contextPath, system, newEndpoint, null);
            return;
        }

        renderError(response, contextPath, "An endpointId or systemId parameter is required.", null);
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
        String endpointIdRaw = trimToNull(request.getParameter("endpointId"));
        String systemIdRaw = trimToNull(request.getParameter("systemId"));

        if ("delete".equalsIgnoreCase(action)) {
            handleDelete(response, contextPath, currentUser, endpointIdRaw);
            return;
        }

        if (endpointIdRaw != null) {
            handleUpdate(request, response, contextPath, currentUser, endpointIdRaw);
        } else if (systemIdRaw != null) {
            handleCreate(request, response, contextPath, currentUser, systemIdRaw);
        } else {
            renderError(response, contextPath, "An endpointId or systemId parameter is required.", null);
        }
    }

    // -------------------------------------------------------------------------
    // POST handlers
    // -------------------------------------------------------------------------

    private void handleCreate(HttpServletRequest request, HttpServletResponse response,
            String contextPath, User currentUser, String systemIdRaw) throws IOException {
        Long systemId = parseId(systemIdRaw);
        if (systemId == null) {
            renderError(response, contextPath, "Invalid system identifier.", null);
            return;
        }
        WorkspaceSystem system = workspaceSystemDao.findById(systemId).orElse(null);
        if (system == null || !canEdit(currentUser, system)) {
            renderForbidden(response, contextPath);
            return;
        }

        WorkspaceEndpoint endpoint = new WorkspaceEndpoint();
        endpoint.setSystemId(systemId);
        try {
            bindEndpointFields(request, endpoint);
            workspaceEndpointDao.saveOrUpdate(endpoint);
            response.sendRedirect(contextPath + "/workspace/system?systemId=" + systemId + "&saved=1");
        } catch (Exception ex) {
            renderForm(response, contextPath, system, endpoint, "Could not save: " + ex.getMessage());
        }
    }

    private void handleUpdate(HttpServletRequest request, HttpServletResponse response,
            String contextPath, User currentUser, String endpointIdRaw) throws IOException {
        Long endpointId = parseId(endpointIdRaw);
        if (endpointId == null) {
            renderError(response, contextPath, "Invalid endpoint identifier.", null);
            return;
        }
        WorkspaceEndpoint endpoint = workspaceEndpointDao.findById(endpointId).orElse(null);
        if (endpoint == null) {
            renderError(response, contextPath, "Endpoint not found.", null);
            return;
        }
        WorkspaceSystem system = workspaceSystemDao.findById(endpoint.getSystemId()).orElse(null);
        if (system == null || !canEdit(currentUser, system)) {
            renderForbidden(response, contextPath);
            return;
        }

        try {
            bindEndpointFields(request, endpoint);
            workspaceEndpointDao.saveOrUpdate(endpoint);
            response.sendRedirect(contextPath + "/workspace/system?systemId=" + endpoint.getSystemId() + "&saved=1");
        } catch (Exception ex) {
            renderForm(response, contextPath, system, endpoint, "Could not save: " + ex.getMessage());
        }
    }

    private void handleDelete(HttpServletResponse response, String contextPath,
            User currentUser, String endpointIdRaw) throws IOException {
        Long endpointId = parseId(endpointIdRaw);
        if (endpointId == null) {
            renderError(response, contextPath, "Invalid endpoint identifier.", null);
            return;
        }
        WorkspaceEndpoint endpoint = workspaceEndpointDao.findById(endpointId).orElse(null);
        if (endpoint == null) {
            renderError(response, contextPath, "Endpoint not found.", null);
            return;
        }
        WorkspaceSystem system = workspaceSystemDao.findById(endpoint.getSystemId()).orElse(null);
        if (system == null || !canEdit(currentUser, system)) {
            renderForbidden(response, contextPath);
            return;
        }
        Long systemId = endpoint.getSystemId();
        workspaceEndpointDao.deleteById(endpointId);
        response.sendRedirect(contextPath + "/workspace/system?systemId=" + systemId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void bindEndpointFields(HttpServletRequest request, WorkspaceEndpoint endpoint) {
        endpoint.setEndpointType(parseEnum(WorkspaceEndpoint.EndpointType.class,
                required(trimToNull(request.getParameter("endpointType")), "Endpoint type")));
        endpoint.setUrl(trimToNull(request.getParameter("url")));
        endpoint.setAuthType(parseEnum(WorkspaceEndpoint.AuthType.class,
                required(trimToNull(request.getParameter("authType")), "Auth type")));
        endpoint.setAuthInstructions(trimToNull(request.getParameter("authInstructions")));
        String activeRaw = request.getParameter("active");
        endpoint.setActive("true".equalsIgnoreCase(activeRaw) || "on".equalsIgnoreCase(activeRaw));
    }

    private boolean canEdit(User user, WorkspaceSystem system) {
        if (authFlowService.isAdminUser(user))
            return true;
        // User must be approved in the workspace AND a contact on the system.
        WorkspaceEnrollment enrollment = workspaceEnrollmentDao
                .findByWorkspaceAndUser(system.getWorkspaceId(), user.getUserId()).orElse(null);
        if (enrollment == null || enrollment.getState() != WorkspaceEnrollment.EnrollmentState.APPROVED) {
            return false;
        }
        return workspaceSystemContactDao.findBySystemAndUser(system.getSystemId(), user.getUserId()).isPresent();
    }

    // -------------------------------------------------------------------------
    // Renderer
    // -------------------------------------------------------------------------

    private void renderForm(HttpServletResponse response, String contextPath,
            WorkspaceSystem system, WorkspaceEndpoint endpoint, String message) throws IOException {
        boolean isNew = endpoint.getEndpointId() == null;
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>" + (isNew ? "Add Endpoint" : "Edit Endpoint") + " - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>" + (isNew ? "Add Endpoint" : "Edit Endpoint") + "</h1>");
            out.println("    <p>System: <strong>" + escapeHtml(orEmpty(system.getSystemName())) + "</strong></p>");
            if (message != null) {
                out.println("    <p class=\"error\"><strong>" + escapeHtml(message) + "</strong></p>");
            }
            out.println("    <form action=\"" + contextPath + "/workspace/endpoint\" method=\"post\">");
            if (!isNew) {
                out.println("      <input type=\"hidden\" name=\"endpointId\" value=\""
                        + endpoint.getEndpointId() + "\" />");
            } else {
                out.println("      <input type=\"hidden\" name=\"systemId\" value=\""
                        + endpoint.getSystemId() + "\" />");
            }

            out.println("      <p>");
            out.println("        <label>Endpoint Type (required):<br />");
            out.println("          <select name=\"endpointType\" required>");
            for (WorkspaceEndpoint.EndpointType t : WorkspaceEndpoint.EndpointType.values()) {
                String sel = t == endpoint.getEndpointType() ? " selected" : "";
                out.println("            <option value=\"" + t.name() + "\"" + sel + ">" + t.name() + "</option>");
            }
            out.println("          </select>");
            out.println("        </label>");
            out.println("      </p>");

            out.println("      <p>");
            out.println("        <label>URL:<br />");
            out.println("          <input type=\"url\" name=\"url\" maxlength=\"500\" size=\"60\"");
            out.println("                 value=\"" + escapeHtml(orEmpty(endpoint.getUrl())) + "\" />");
            out.println("        </label>");
            out.println("      </p>");

            out.println("      <p>");
            out.println("        <label>Auth Type (required):<br />");
            out.println("          <select name=\"authType\" required>");
            for (WorkspaceEndpoint.AuthType at : WorkspaceEndpoint.AuthType.values()) {
                String sel = at == endpoint.getAuthType() ? " selected" : "";
                out.println("            <option value=\"" + at.name() + "\"" + sel + ">" + at.name() + "</option>");
            }
            out.println("          </select>");
            out.println("        </label>");
            out.println("      </p>");

            out.println("      <p>");
            out.println("        <label>Auth Instructions:<br />");
            out.println("          <textarea name=\"authInstructions\" rows=\"4\" cols=\"60\">"
                    + escapeHtml(orEmpty(endpoint.getAuthInstructions())) + "</textarea>");
            out.println("        </label>");
            out.println("      </p>");

            out.println("      <p>");
            out.println("        <label>");
            String checkedAttr = Boolean.TRUE.equals(endpoint.getActive()) ? " checked" : "";
            out.println("          <input type=\"checkbox\" name=\"active\" value=\"true\"" + checkedAttr + " />");
            out.println("          Active");
            out.println("        </label>");
            out.println("      </p>");

            out.println("      <p><button type=\"submit\">Save Endpoint</button></p>");
            out.println("    </form>");
            out.println("    <p><a href=\"" + contextPath + "/workspace/system?systemId="
                    + endpoint.getSystemId() + "\">Back to System</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
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
            out.println("  <p>You do not have permission to manage this endpoint.</p>");
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
