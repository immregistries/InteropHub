package org.airahub.interophub.servlet;

import java.io.PrintWriter;

public final class PageFooterRenderer {
    private static final String VERSION = InteropVersionResolver.getApplicationVersion();

    private PageFooterRenderer() {
    }

    static void render(PrintWriter out) {
        LocalEnvBannerRenderer.renderIfLocalhost(out);
        out.println("  <footer class=\"legal-footer\">");
        out.println(
                "    <p><strong>American Immunization Registry Association (AIRA)</strong> supports collaboration, standards, and shared solutions that strengthen immunization information systems and improve the use of vaccination data to protect public health. <a href=\"https://www.immregistries.org/\">https://www.immregistries.org/</a></p>");
        out.println("    <p>InteropHub version " + VERSION
                + " &ndash; &copy; Copyright 2026, American Immunization Registry Association. All rights reserved. <a href=\"https://aira.memberclicks.net/assets/docs/Organizational_Docs/AIRA%20Privacy%20Policy%20-%20Final%202024_.pdf\">Privacy Policy</a> <a href=\"https://repository.immregistries.org/files/AIRA-Terms_of_Use_2024.pdf\">Terms of Use</a></p>");
        out.println("  </footer>");
    }
}