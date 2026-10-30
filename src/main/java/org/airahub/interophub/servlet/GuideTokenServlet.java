package org.airahub.interophub.servlet;

import java.io.IOException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.service.GuideTokenService;

/**
 * {@code POST /api/guide/token} exchanges a topic-reference login code for a guide token.
 * {@code DELETE /api/guide/token} revokes the calling guide token. The bearer filter exempts POST.
 */
public class GuideTokenServlet extends HttpServlet {
    private final GuideTokenService guideTokenService;

    public GuideTokenServlet() {
        this.guideTokenService = new GuideTokenService();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        guideTokenService.exchange(request, response);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        guideTokenService.revokeCallingToken(request, response);
    }
}
