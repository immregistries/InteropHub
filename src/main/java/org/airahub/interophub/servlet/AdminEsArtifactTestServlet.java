package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.airahub.interophub.config.ArtifactStorageConfig;
import org.airahub.interophub.dao.EsArtifactDemoDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsArtifactDemo;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.ArtifactBlobStorageService;

/**
 * TEMPORARY - proves Azure Blob storage works end to end before Communication
 * Bundles depend on it
 * (docs/communication-bundles/artifact-storage-deployment-handoff.md). Uploads
 * one demo image, shows it from its direct Blob URL, and lets it be replaced at
 * the same URL. The same image appears on /welcome so anonymous read can be
 * checked signed out.
 */
public class AdminEsArtifactTestServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(AdminEsArtifactTestServlet.class.getName());

    private static final String ACTIVE_HREF = "/admin/es/artifact-test";
    private static final long MAX_UPLOAD_BYTES = 10L * 1024L * 1024L;
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/png", "image/jpeg", "image/webp", "image/gif");

    private final EsArtifactDemoDao artifactDao;
    private final UserDao userDao;
    private final ArtifactBlobStorageService blobStorageService;

    public AdminEsArtifactTestServlet() {
        this.artifactDao = new EsArtifactDemoDao();
        this.userDao = new UserDao();
        this.blobStorageService = new ArtifactBlobStorageService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String error = trimToNull(request.getParameter("error"));
        boolean saved = "1".equals(request.getParameter("saved"));
        EsArtifactDemo artifact = artifactDao.findBySlotKey(EsArtifactDemo.SLOT_WELCOME_DEMO).orElse(null);

        AdminShellRenderer.render(request, response, "Artifact Storage Test - InteropHub",
                AdminSection.TOPIC_SPACES, ACTIVE_HREF,
                out -> renderPage(out, request.getContextPath(), artifact, saved, error));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }
        String redirectBase = request.getContextPath() + ACTIVE_HREF;

        // Enforced here as well as hidden in the UI, so a direct POST cannot bypass it.
        if (!blobStorageService.isWriteEnabled()) {
            response.sendRedirect(
                    redirectBase + "?error=" + encode("Uploads are disabled: no write SAS is configured."));
            return;
        }

        Part filePart;
        try {
            filePart = request.getPart("artifactFile");
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Artifact upload could not be read", ex);
            response.sendRedirect(redirectBase + "?error="
                    + encode("Upload could not be read. The file may exceed the 10 MB limit."));
            return;
        }

        if (filePart == null || filePart.getSize() <= 0) {
            response.sendRedirect(redirectBase + "?error=" + encode("Choose a file to upload."));
            return;
        }
        if (filePart.getSize() > MAX_UPLOAD_BYTES) {
            response.sendRedirect(redirectBase + "?error=" + encode("File exceeds the 10 MB limit."));
            return;
        }

        String contentType = normalizeContentType(filePart.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            response.sendRedirect(redirectBase + "?error="
                    + encode("Only PNG, JPEG, WebP, and GIF images are accepted."));
            return;
        }

        String filename = sanitizeFilename(filePart.getSubmittedFileName());

        EsArtifactDemo artifact = artifactDao.findBySlotKey(EsArtifactDemo.SLOT_WELCOME_DEMO)
                .orElseGet(EsArtifactDemo::new);
        if (artifact.getObjectKey() == null) {
            // Minted once per slot and reused on replacement, so the public URL stays
            // stable.
            artifact.setObjectKey(UUID.randomUUID().toString());
        }

        try (InputStream content = filePart.getInputStream()) {
            blobStorageService.store(artifact.getObjectKey(), content, contentType, filename);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Artifact upload to Blob storage failed", ex);
            response.sendRedirect(redirectBase + "?error="
                    + encode("Upload to Blob storage failed. See the server log for details."));
            return;
        }

        artifact.setSlotKey(EsArtifactDemo.SLOT_WELCOME_DEMO);
        artifact.setOriginalFilename(filename);
        artifact.setContentType(contentType);
        artifact.setSizeBytes(filePart.getSize());
        artifact.setUploadedByUserId(adminUser.get().getUserId());
        artifact.setUploadedAt(LocalDateTime.now());
        artifactDao.save(artifact);

        response.sendRedirect(redirectBase + "?saved=1");
    }

    private void renderPage(PrintWriter out, String contextPath, EsArtifactDemo artifact, boolean saved,
            String error) {
        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-section-title\">Artifact Storage Test</h2>");
        out.println("            <p class=\"aira-meta\">Temporary page proving Azure Blob storage works end to "
                + "end. Upload an image here, then confirm it appears on the Welcome page both signed in and "
                + "signed out. Replacing the image reuses the same object key, so the public URL never "
                + "changes.</p>");

        if (saved) {
            out.println("            <div class=\"aira-alert aira-alert--success\">Image uploaded.</div>");
        }
        if (error != null) {
            out.println("            <div class=\"aira-alert aira-alert--error\">" + escapeHtml(error)
                    + "</div>");
        }
        out.println("          </section>");

        renderCurrentArtifact(out, artifact);
        renderUploadForm(out, contextPath);
    }

    private void renderCurrentArtifact(PrintWriter out, EsArtifactDemo artifact) {
        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-subsection-title\">Current image</h2>");
        if (artifact == null) {
            out.println("            <p class=\"aira-meta\">Nothing uploaded yet.</p>");
            out.println("          </section>");
            return;
        }

        String readUrl = ArtifactStorageConfig.getReadUrl(artifact.getObjectKey());
        out.println("            <p><img src=\"" + escapeHtml(readUrl) + "\" alt=\""
                + escapeHtml(artifact.getOriginalFilename()) + "\" style=\"max-width:100%;height:auto;\" /></p>");
        out.println("            <div class=\"aira-stack aira-stack--compact\">");
        renderMeta(out, "File name", artifact.getOriginalFilename());
        renderMeta(out, "Content type", artifact.getContentType());
        renderMeta(out, "Size", artifact.getSizeBytes() + " bytes");
        renderMeta(out, "Object key", artifact.getObjectKey());
        renderMeta(out, "Uploaded", String.valueOf(artifact.getUploadedAt()));
        renderMeta(out, "Uploaded by", describeUploader(artifact.getUploadedByUserId()));
        out.println("            </div>");
        out.println("            <p class=\"aira-meta\">Direct Blob URL: <a class=\"aira-inline-link\" href=\""
                + escapeHtml(readUrl) + "\" rel=\"noopener noreferrer\" target=\"_blank\">" + escapeHtml(readUrl)
                + "</a></p>");
        out.println("          </section>");
    }

    private void renderUploadForm(PrintWriter out, String contextPath) {
        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-subsection-title\">Upload or replace</h2>");

        if (!blobStorageService.isWriteEnabled()) {
            out.println("            <div class=\"aira-alert aira-alert--warning\">Uploads are disabled because "
                    + "no write SAS is configured (<code>HUB_ARTIFACTS_SAS_TOKEN</code>). This is expected on a "
                    + "local machine - the image above is still read straight from Blob storage. In production "
                    + "this means the environment variable is missing.</div>");
            out.println("          </section>");
            return;
        }

        out.println("            <form class=\"aira-form\" method=\"post\" enctype=\"multipart/form-data\" "
                + "action=\"" + contextPath + ACTIVE_HREF + "\">");
        out.println("              <label for=\"artifactFile\">Image (PNG, JPEG, WebP, or GIF; 10 MB max)</label>");
        out.println("              <input type=\"file\" id=\"artifactFile\" name=\"artifactFile\" "
                + "accept=\"image/png,image/jpeg,image/webp,image/gif\" required />");
        out.println("              <div class=\"aira-action-group\">");
        out.println("                <button type=\"submit\" class=\"aira-button aira-button--primary\">Upload"
                + "</button>");
        out.println("              </div>");
        out.println("            </form>");
        out.println("          </section>");
    }

    private void renderMeta(PrintWriter out, String label, String value) {
        out.println("              <div class=\"aira-meta-chip\"><span class=\"aira-meta-chip__label\">"
                + escapeHtml(label) + "</span><span class=\"aira-meta-chip__value\">" + escapeHtml(value)
                + "</span></div>");
    }

    private String describeUploader(Long userId) {
        if (userId == null) {
            return "Unknown";
        }
        return userDao.findById(userId)
                .map(user -> user.getFullName() == null || user.getFullName().isBlank()
                        ? user.getEmail()
                        : user.getFullName())
                .orElse("User " + userId);
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        String value = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String sanitizeFilename(String submitted) {
        if (submitted == null || submitted.isBlank()) {
            return "upload";
        }
        String name = submitted.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.trim();
        if (name.isEmpty()) {
            return "upload";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String escapeHtml(String value) {
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
