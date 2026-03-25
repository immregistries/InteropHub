package org.airahub.interophub.servlet;

import java.io.IOException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.service.AuthFlowService;

public class LogoutServlet extends HttpServlet {
    private final AuthFlowService authFlowService;

    public LogoutServlet() {
        this.authFlowService = new AuthFlowService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doPost(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        authFlowService.logout(request);
        response.addCookie(authFlowService.buildClearedSessionCookie(request));
        response.sendRedirect(request.getContextPath() + "/home");
    }
}
