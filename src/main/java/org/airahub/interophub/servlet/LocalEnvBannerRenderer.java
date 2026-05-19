package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import org.airahub.interophub.service.PublicUrlService;

final class LocalEnvBannerRenderer {

    private LocalEnvBannerRenderer() {
    }

    static void renderIfLocalhost(PrintWriter out) {
        if (new PublicUrlService().isLocalhostMode()) {
            out.println("  <div class=\"env-ribbon\">LOCAL</div>");
        }
    }
}
