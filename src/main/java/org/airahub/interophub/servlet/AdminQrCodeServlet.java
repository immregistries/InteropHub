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

        if (target == null) {
            renderError(response, contextPath, "A target path is required.", back);
            return;
        }

        try {
            String normalizedTarget = publicUrlService.normalizeInternalPath(target);
            String resolvedUrl = publicUrlService.resolveExternalUrl(normalizedTarget);
            String qrCodeDataUrl = buildQrCodeDataUrl(resolvedUrl);
            renderPage(response, contextPath, normalizedTarget, resolvedUrl, qrCodeDataUrl, label, back);
        } catch (IllegalArgumentException ex) {
            renderError(response, contextPath, ex.getMessage(), back);
        }
    }

    private void renderPage(HttpServletResponse response, String contextPath, String targetPath, String resolvedUrl,
            String qrCodeDataUrl, String label, String back) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        String effectiveLabel = label == null ? "QR Code" : label;
        String backHref = resolveBackHref(contextPath, back);

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "QR Code - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>QR Code</h2>");
                panelOut.println("        <p><strong>Link:</strong> " + escapeHtml(effectiveLabel) + "</p>");
                panelOut.println("        <p><strong>Target Path:</strong> " + escapeHtml(targetPath) + "</p>");
                panelOut.println(
                        "        <p><strong>Resolved URL:</strong> <a href=\"" + escapeHtml(resolvedUrl) + "\">"
                                + escapeHtml(resolvedUrl) + "</a></p>");
                panelOut.println("        <p><img src=\"" + qrCodeDataUrl + "\" alt=\"QR code for "
                        + escapeHtml(effectiveLabel) + "\" style=\"max-width:360px;width:100%;height:auto\" /></p>");
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