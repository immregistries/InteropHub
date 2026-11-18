package org.airahub.interophub.servlet;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.GuideApiErrors;
import org.airahub.interophub.service.GuideHelp;
import org.airahub.interophub.service.GuideRequestAuth;
import org.airahub.interophub.service.GuideTokenInfoService;
import org.json.JSONObject;

/**
 * {@code GET /api/token}. Behind {@link GuideBearerTokenFilter}; any valid guide or personal token,
 * with no particular scope, because it only describes the caller.
 */
public class GuideTokenInfoServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(GuideTokenInfoServlet.class.getName());

    private final GuideTokenInfoService service;

    public GuideTokenInfoServlet() {
        this(null);
    }

    GuideTokenInfoServlet(GuideTokenInfoService service) {
        this.service = service;
    }

    @Override
    public void init() {
        GuideHelp.useHubSettings();
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "GET");
            GuideApiErrors.invalidRequest(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    "Only GET is supported on /api/token.");
            return;
        }
        User user = GuideRequestAuth.requireUser(request, response);
        if (user == null) {
            return;
        }
        try {
            Object tokenId = request.getAttribute(GuideRequestAuth.TOKEN_ID);
            Object kind = request.getAttribute(GuideRequestAuth.TOKEN_KIND);
            JSONObject body = service().describe(user, tokenId instanceof Long id ? id : null,
                    kind instanceof String text ? text : null, GuideRequestAuth.scopes(request), LocalDateTime.now());
            response.setStatus(HttpServletResponse.SC_OK);
            response.setHeader("Cache-Control", "no-store");
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(body.toString());
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Token description failed", ex);
            GuideApiErrors.serverError(response);
        }
    }

    private GuideTokenInfoService service() {
        return service != null ? service : new GuideTokenInfoService();
    }
}
