package org.airahub.interophub.servlet;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.airahub.interophub.dao.EsArtifactDemoDao;
import org.airahub.interophub.model.*;
import org.airahub.interophub.service.*;

/** Temporary feature consumer of shared document/image storage. */
public class AdminEsArtifactTestServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(AdminEsArtifactTestServlet.class.getName());
    private static final String ACTIVE_HREF = "/admin/es/artifact-test";
    private final EsArtifactDemoDao artifacts = new EsArtifactDemoDao();
    private final StoredFileService storage = new StoredFileService();

    @Override
    public void init() {
        storage.local().setDeploymentPath(getServletContext().getRealPath("/"));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (AdminAccessGuard.requireAdmin(request, response).isEmpty()) {
            return;
        }
        String csrf = CsrfTokenSupport.getOrCreateToken(request);
        String error = request.getParameter("error");
        boolean saved = "1".equals(request.getParameter("saved"));
        List<EsArtifactDemo> demos = artifacts.findAll();
        AdminShellRenderer.render(request, response, "Artifact Storage Test - InteropHub",
                AdminSection.TOPIC_SPACES, ACTIVE_HREF, out -> {
                    out.println("<section class=\"aira-panel\"><h2 class=\"aira-section-title\">Artifact Storage Test</h2>");
                    out.println("<p>Upload a local Welcome image or a document. File URLs remain stable on replacement. "
                            + "Existing Blob content keeps its recorded backend.</p>");
                    if (saved) {
                        out.println("<div class=\"aira-alert aira-alert--success\">File uploaded.</div>");
                    }
                    if (error != null) {
                        out.println("<div class=\"aira-alert aira-alert--error\">" + escapeHtml(error) + "</div>");
                    }
                    out.println("</section>");
                    renderSlot(out, request.getContextPath(), csrf, EsArtifactDemo.SLOT_WELCOME_DEMO,
                            "Welcome image (local)", demos);
                    renderSlot(out, request.getContextPath(), csrf, EsArtifactDemo.SLOT_DOCUMENT_DEMO,
                            "Document demonstration (local)", demos);
                    if (demos.stream().anyMatch(d -> EsArtifactDemo.SLOT_LEGACY_BLOB.equals(d.getSlotKey()))) {
                        renderSlot(out, request.getContextPath(), csrf, EsArtifactDemo.SLOT_LEGACY_BLOB,
                                "Existing Blob Welcome image", demos);
                    }
                });
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> admin = AdminAccessGuard.requireAdmin(request, response);
        if (admin.isEmpty()) {
            return;
        }
        String redirect = request.getContextPath() + ACTIVE_HREF;
        Part part = null;
        try {
            if (!CsrfTokenSupport.isValid(request)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid request token. Reload and try again.");
                return;
            }
            String slot = request.getParameter("slotKey");
            if (!Set.of(EsArtifactDemo.SLOT_WELCOME_DEMO, EsArtifactDemo.SLOT_DOCUMENT_DEMO,
                    EsArtifactDemo.SLOT_LEGACY_BLOB).contains(slot == null ? "" : slot)) {
                throw new IllegalArgumentException("Choose a supported demonstration slot.");
            }
            StoredFile existing = artifacts.findBySlotKey(slot).map(EsArtifactDemo::getStoredFile).orElse(null);
            if (existing == null && EsArtifactDemo.SLOT_LEGACY_BLOB.equals(slot)) {
                throw new IllegalArgumentException("There is no existing Blob demonstration to replace.");
            }
            String problem = storage.writeProblem(existing);
            if (problem != null) {
                throw new IllegalStateException(problem);
            }
            part = request.getPart("artifactFile");
            if (part == null || part.getSize() == 0 || part.getSize() > StoredFileValidation.MAX_BYTES) {
                throw new IllegalArgumentException("Choose a non-empty file no larger than 25 MiB.");
            }
            String name = StoredFileValidation.filename(part.getSubmittedFileName());
            if (!EsArtifactDemo.SLOT_DOCUMENT_DEMO.equals(slot)
                    && !name.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|webp|gif)$")) {
                throw new IllegalArgumentException("The Welcome slot accepts raster images only.");
            }
            try (InputStream content = part.getInputStream()) {
                storage.upload(existing, content, name, part.getContentType(),
                        admin.get().getUserId(), file -> artifacts.register(slot, file));
            }
            response.sendRedirect(redirect + "?saved=1");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            LOGGER.log(Level.WARNING, "Artifact upload rejected", ex);
            response.sendRedirect(redirect + "?error=" + encode(ex.getMessage()));
        } catch (IOException | ServletException ex) {
            LOGGER.log(Level.SEVERE, "Artifact upload could not be read or stored", ex);
            response.sendRedirect(redirect + "?error=" + encode(
                    "Upload failed. Check the 25 MiB limit, file format, directory permissions and server log."));
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Artifact upload registration failed", ex);
            response.sendRedirect(redirect + "?error=" + encode("File registration failed. See the server log."));
        } finally {
            if (part != null) {
                try {
                    part.delete();
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "Failed to clean up multipart upload", ex);
                }
            }
        }
    }

    private void renderSlot(PrintWriter out, String context, String csrf, String slot, String title,
            List<EsArtifactDemo> demos) {
        StoredFile file = demos.stream().filter(d -> slot.equals(d.getSlotKey()))
                .findFirst().map(EsArtifactDemo::getStoredFile).orElse(null);
        out.println("<section class=\"aira-panel\"><h2 class=\"aira-subsection-title\">" + title + "</h2>");
        if (file != null) {
            String url = StoredFileService.readUrl(context, file);
            if (file.isImage()) {
                out.println("<p><img src=\"" + escapeHtml(url) + "\" alt=\"" + escapeHtml(file.getOriginalFilename())
                        + "\" style=\"max-width:100%;height:auto;\" /></p>");
            }
            out.println("<p><a class=\"aira-inline-link\" href=\"" + escapeHtml(url) + "\">"
                    + escapeHtml(file.getOriginalFilename()) + "</a></p>");
            out.println("<p class=\"aira-meta\">Backend: " + file.getBackend() + "; " + file.getSizeBytes()
                    + " bytes; uploaded " + file.getUploadedAt() + " by user " + file.getUploadedByUserId() + "</p>");
            out.println("<p class=\"aira-meta\">Stable file URL: " + escapeHtml(url) + "</p>");
        } else {
            out.println("<p class=\"aira-meta\">Nothing uploaded yet.</p>");
        }
        String problem = storage.writeProblem(file);
        if (problem != null) {
            out.println("<div class=\"aira-alert aira-alert--warning\">" + escapeHtml(problem) + "</div>");
        } else {
            out.println("<form class=\"aira-form\" method=\"post\" enctype=\"multipart/form-data\" action=\""
                    + escapeHtml(context + ACTIVE_HREF) + "\">");
            out.println("<input type=\"hidden\" name=\"csrfToken\" value=\"" + escapeHtml(csrf) + "\" />");
            out.println("<input type=\"hidden\" name=\"slotKey\" value=\"" + slot + "\" />");
            boolean documents = EsArtifactDemo.SLOT_DOCUMENT_DEMO.equals(slot);
            String accept = documents ? ".png,.jpg,.jpeg,.webp,.gif,.pdf,.txt,.doc,.docx,.ppt,.pptx"
                    : ".png,.jpg,.jpeg,.webp,.gif";
            out.println("<label for=\"file-" + slot + "\">"
                    + (documents ? "Document or image" : "Raster image") + " (25 MiB max)</label>");
            out.println("<input type=\"file\" id=\"file-" + slot + "\" name=\"artifactFile\" accept=\"" + accept
                    + "\" required />");
            out.println("<p class=\"aira-meta\">Not confidential storage: anyone with a file URL can read it. "
                    + "Keep authoritative copies elsewhere. Routine replacement does not preserve file history.</p>");
            out.println("<div class=\"aira-action-group\"><button type=\"submit\" "
                    + "class=\"aira-button aira-button--primary\">Upload or replace</button></div></form>");
        }
        out.println("</section>");
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String escapeHtml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
