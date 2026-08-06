package org.airahub.interophub.servlet;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.Optional;
import javax.imageio.ImageIO;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.PublicUrlService;

public class AdminQrCodeServlet extends HttpServlet {

    private final PublicUrlService publicUrlService;

    public AdminQrCodeServlet() {
        this.publicUrlService = new PublicUrlService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String target = trimToNull(request.getParameter("target"));
        String label = trimToNull(request.getParameter("label"));
        String back = trimToNull(request.getParameter("back"));
        boolean allowExternal = parseBooleanFlag(request.getParameter("allowExternal"));

        if (target == null) {
            renderForm(request, response, label, back, false);
            return;
        }

        try {
            String normalizedTarget = publicUrlService.normalizeInternalPath(target, allowExternal);
            String resolvedUrl = isAbsoluteUrl(normalizedTarget)
                    ? normalizedTarget
                    : publicUrlService.resolveExternalUrl(normalizedTarget);
            String qrCodeDataUrl = buildQrCodeDataUrl(resolvedUrl);
            renderPage(request, response, normalizedTarget, resolvedUrl, qrCodeDataUrl, label, back, allowExternal);
        } catch (IllegalArgumentException ex) {
            renderError(request, response, ex.getMessage(), back);
        }
    }

    private void renderForm(HttpServletRequest request, HttpServletResponse response, String label, String back,
            boolean allowExternal) throws IOException {
        String contextPath = request.getContextPath();
        String formAction = contextPath + "/admin/qr";

        AdminShellRenderer.render(request, response, "QR Generator - InteropHub", AdminSection.PLATFORM,
                "/admin/qr", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">QR Generator</h2>");
                    out.println("            <form class=\"aira-form\" method=\"get\" action=\""
                            + escapeHtml(formAction) + "\">");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"target\">Target URL or internal path</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"target\" name=\"target\" type=\"text\" placeholder=\"/admin/es or https://example.org\" required />");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"label\">Label (optional)</label>");
                    out.println("                <input class=\"aira-input\" id=\"label\" name=\"label\" type=\"text\" value=\""
                            + escapeHtml(orDefault(label, "")) + "\" />");
                    out.println("              </div>");
                    out.println("              <label class=\"aira-radio\"><input type=\"checkbox\" name=\"allowExternal\" value=\"true\""
                            + (allowExternal ? " checked" : "")
                            + " /> allow generating for external URL</label>");
                    if (back != null) {
                        out.println("              <input type=\"hidden\" name=\"back\" value=\"" + escapeHtml(back)
                                + "\" />");
                    }
                    out.println("              <div class=\"aira-action-group\">");
                    out.println(
                            "                <button class=\"aira-button aira-button--primary\" type=\"submit\">Generate QR</button>");
                    out.println("              </div>");
                    out.println("            </form>");
                    out.println("          </section>");
                });
    }

    private void renderPage(HttpServletRequest request, HttpServletResponse response, String targetPath,
            String resolvedUrl, String qrCodeDataUrl, String label, String back, boolean allowExternal)
            throws IOException {
        String contextPath = request.getContextPath();
        String effectiveLabel = label == null ? "QR Code" : label;
        String backHref = resolveBackHref(contextPath, back);

        AdminShellRenderer.render(request, response, "QR Code - InteropHub", AdminSection.PLATFORM,
                "/admin/qr", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">QR Code</h2>");
                    out.println("            <p><strong>Link:</strong> " + escapeHtml(effectiveLabel) + "</p>");
                    out.println("            <p><strong>Target:</strong> " + escapeHtml(targetPath) + "</p>");
                    out.println(
                            "            <p><strong>Resolved URL:</strong> <a class=\"aira-inline-link\" href=\""
                                    + escapeHtml(resolvedUrl) + "\">"
                                    + escapeHtml(resolvedUrl) + "</a></p>");
                    out.println("            <p><img src=\"" + qrCodeDataUrl + "\" alt=\"QR code for "
                            + escapeHtml(effectiveLabel) + "\" style=\"max-width:360px;width:100%;height:auto\" /></p>");
                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + escapeHtml(contextPath)
                            + "/admin/qr?allowExternal=" + (allowExternal ? "true" : "false")
                            + "\">Generate another</a>");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + escapeHtml(backHref)
                            + "\">Back</a>");
                    out.println("            </div>");
                    out.println("          </section>");
                });
    }

    private void renderError(HttpServletRequest request, HttpServletResponse response, String message, String back)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        String contextPath = request.getContextPath();
        String backHref = resolveBackHref(contextPath, back);

        AdminShellRenderer.render(request, response, "QR Code Error - InteropHub", AdminSection.PLATFORM,
                "/admin/qr", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">QR Code Error</h2>");
                    out.println("            <div class=\"aira-alert aira-alert--danger\"><p>"
                            + escapeHtml(orDefault(message, "The QR code request was invalid.")) + "</p></div>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + escapeHtml(backHref) + "\">Back</a></p>");
                    out.println("          </section>");
                });
    }

    private String buildQrCodeDataUrl(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 360, 360);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("Could not generate the QR code.", ex);
        }
    }

    private String resolveBackHref(String contextPath, String back) {
        try {
            if (back != null) {
                return contextPath + publicUrlService.normalizeInternalPath(back);
            }
        } catch (IllegalArgumentException ex) {
            // Fall back to the default admin landing page.
        }
        return contextPath + "/admin/es";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean parseBooleanFlag(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return "true".equals(normalized) || "1".equals(normalized) || "on".equals(normalized);
    }

    private boolean isAbsoluteUrl(String value) {
        try {
            return URI.create(value).isAbsolute();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String orDefault(String value, String defaultValue) {
        return value == null ? defaultValue : value;
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
