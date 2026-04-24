package org.airahub.interophub.servlet;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.util.Base64;
import java.util.Optional;
import javax.imageio.ImageIO;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.PublicUrlService;

public class AdminQrCodeServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final PublicUrlService publicUrlService;

    public AdminQrCodeServlet() {
        this.authFlowService = new AuthFlowService();
        this.publicUrlService = new PublicUrlService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String target = trimToNull(request.getParameter("target"));
        String label = trimToNull(request.getParameter("label"));
        String back = trimToNull(request.getParameter("back"));
        boolean allowExternal = parseBooleanFlag(request.getParameter("allowExternal"));

        if (target == null) {
            renderForm(response, contextPath, label, back, false);
            return;
        }

        try {
            String normalizedTarget = publicUrlService.normalizeInternalPath(target, allowExternal);
            String resolvedUrl = isAbsoluteUrl(normalizedTarget)
                    ? normalizedTarget
                    : publicUrlService.resolveExternalUrl(normalizedTarget);
            String qrCodeDataUrl = buildQrCodeDataUrl(resolvedUrl);
            renderPage(response, contextPath, normalizedTarget, resolvedUrl, qrCodeDataUrl, label, back, allowExternal);
        } catch (IllegalArgumentException ex) {
            renderError(response, contextPath, ex.getMessage(), back);
        }
    }

    private void renderForm(HttpServletResponse response, String contextPath, String label, String back,
            boolean allowExternal) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        String formAction = contextPath + "/admin/qr";

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "QR Generator - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>QR Generator</h2>");
                panelOut.println("        <form method=\"get\" action=\"" + escapeHtml(formAction) + "\">");
                panelOut.println("          <label for=\"target\">Target URL or internal path</label>");
                panelOut.println(
                        "          <input id=\"target\" name=\"target\" type=\"text\" placeholder=\"/admin/es or https://example.org\" required />");
                panelOut.println("          <label for=\"label\">Label (optional)</label>");
                panelOut.println("          <input id=\"label\" name=\"label\" type=\"text\" value=\""
                        + escapeHtml(orDefault(label, "")) + "\" />");
                panelOut.println("          <label><input type=\"checkbox\" name=\"allowExternal\" value=\"true\""
                        + (allowExternal ? " checked" : "")
                        + " /> allow generating for external URL</label>");
                if (back != null) {
                    panelOut.println("          <input type=\"hidden\" name=\"back\" value=\"" + escapeHtml(back)
                            + "\" />");
                }
                panelOut.println("          <p><button type=\"submit\">Generate QR</button></p>");
                panelOut.println("        </form>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderPage(HttpServletResponse response, String contextPath, String targetPath, String resolvedUrl,
            String qrCodeDataUrl, String label, String back, boolean allowExternal) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        String effectiveLabel = label == null ? "QR Code" : label;
        String backHref = resolveBackHref(contextPath, back);

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "QR Code - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>QR Code</h2>");
                panelOut.println("        <p><strong>Link:</strong> " + escapeHtml(effectiveLabel) + "</p>");
                panelOut.println("        <p><strong>Target:</strong> " + escapeHtml(targetPath) + "</p>");
                panelOut.println(
                        "        <p><strong>Resolved URL:</strong> <a href=\"" + escapeHtml(resolvedUrl) + "\">"
                                + escapeHtml(resolvedUrl) + "</a></p>");
                panelOut.println("        <p><img src=\"" + qrCodeDataUrl + "\" alt=\"QR code for "
                        + escapeHtml(effectiveLabel) + "\" style=\"max-width:360px;width:100%;height:auto\" /></p>");
                panelOut.println("        <p><a href=\"" + escapeHtml(contextPath)
                        + "/admin/qr?allowExternal=" + (allowExternal ? "true" : "false")
                        + "\">Generate another</a></p>");
                panelOut.println("        <p><a href=\"" + escapeHtml(backHref) + "\">Back</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderError(HttpServletResponse response, String contextPath, String message, String back)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("text/html;charset=UTF-8");
        String backHref = resolveBackHref(contextPath, back);

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "QR Code Error - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>QR Code Error</h2>");
                panelOut.println(
                        "        <p>" + escapeHtml(orDefault(message, "The QR code request was invalid.")) + "</p>");
                panelOut.println("        <p><a href=\"" + escapeHtml(backHref) + "\">Back</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private Optional<User> requireAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return Optional.empty();
        }
        if (!authFlowService.isAdminUser(authenticatedUser.get())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = response.getWriter()) {
                out.println("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\" />");
                out.println("<title>Access Denied - InteropHub</title>");
                out.println(
                        "<link rel=\"stylesheet\" href=\"" + request.getContextPath() + "/css/main.css\" /></head>");
                out.println("<body><main class=\"container\"><h1>Access Denied</h1>");
                out.println("<p>You must be an InteropHub admin to access this page.</p>");
                out.println("<p><a href=\"" + request.getContextPath() + "/welcome\">Return to Welcome</a></p>");
                out.println("</main></body></html>");
            }
            return Optional.empty();
        }
        return authenticatedUser;
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