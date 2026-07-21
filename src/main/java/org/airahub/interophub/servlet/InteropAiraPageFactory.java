package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServletRequest;
import org.immregistries.aira.web.AiraDefaults;
import org.immregistries.aira.web.AiraLogo;
import org.immregistries.aira.web.AiraPage;

final class InteropAiraPageFactory {
    private static final String APPLICATION_NAME = "InteropHub";

    private InteropAiraPageFactory() {
    }

    static AiraPage.Builder base(HttpServletRequest request, String documentTitle) {
        return AiraPage.builder()
                .applicationName(APPLICATION_NAME)
                .applicationVersion(InteropVersionResolver.getApplicationVersion())
                .documentTitle(documentTitle)
                .contextPath(request.getContextPath())
                .identityHref("/home")
                .logo(new AiraLogo(AiraDefaults.DEFAULT_LOGO_PATH, AiraDefaults.DEFAULT_LOGO_ALT_TEXT));
    }
}