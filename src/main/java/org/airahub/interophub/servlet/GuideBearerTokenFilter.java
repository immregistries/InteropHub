package org.airahub.interophub.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.service.BearerDecision;
import org.airahub.interophub.service.BearerTokenRules;
import org.airahub.interophub.service.GuideAllowlistOrigins;
import org.airahub.interophub.service.GuideApiErrors;
import org.airahub.interophub.service.GuideBearerAuthenticator;
import org.airahub.interophub.service.GuideCors;
import org.airahub.interophub.service.GuideRequestAuth;
import org.airahub.interophub.service.GuideTokenLookup;

/**
 * Authentication for {@code /api/guide/*}. Scope checks stay in each servlet.
 * {@code OPTIONS} and {@code POST /api/guide/token} do not require a bearer token.
 */
public class GuideBearerTokenFilter implements Filter {
    private static final String ORIGIN_MESSAGE = "This origin is not allowed to call the guide API.";

    private final GuideTokenLookup tokenLookup;
    private final Supplier<Set<String>> allowedOrigins;

    public GuideBearerTokenFilter() {
        this(new GuideBearerAuthenticator(), new GuideAllowlistOrigins());
    }

    public GuideBearerTokenFilter(GuideTokenLookup tokenLookup, Supplier<Set<String>> allowedOrigins) {
        this.tokenLookup = tokenLookup;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // No setup. Origins are read per request so a new allowlist row is visible immediately.
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        if (!(servletRequest instanceof HttpServletRequest request)
                || !(servletResponse instanceof HttpServletResponse response)) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        String origin = request.getHeader("Origin");
        GuideCors.Permission permission = GuideCors.permission(origin, allowedOrigins.get());
        boolean options = "OPTIONS".equalsIgnoreCase(request.getMethod());
        if (permission == GuideCors.Permission.REFUSED) {
            if (options) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            } else {
                GuideApiErrors.forbidden(response, ORIGIN_MESSAGE);
            }
            return;
        }
        if (permission == GuideCors.Permission.ALLOWED) {
            GuideCors.applyAllowed(response, origin);
        }
        if (options) {
            if (permission == GuideCors.Permission.ALLOWED) {
                GuideCors.applyPreflight(response);
            }
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        if (isGuideTokenPost(request)) {
            chain.doFilter(request, response);
            return;
        }

        List<String> headers = new ArrayList<>();
        if (request.getHeaders("Authorization") != null) {
            headers.addAll(Collections.list(request.getHeaders("Authorization")));
        }
        String rawToken = BearerTokenRules.singleBearerToken(headers);
        BearerDecision decision = rawToken == null ? BearerDecision.reject() : tokenLookup.authenticate(rawToken);
        if (!decision.accepted()) {
            GuideApiErrors.invalidToken(response);
            return;
        }
        request.setAttribute(GuideRequestAuth.USER, decision.getUser());
        request.setAttribute(GuideRequestAuth.TOKEN_ID, decision.getTokenId());
        request.setAttribute(GuideRequestAuth.TOKEN_KIND, decision.getKind());
        request.setAttribute(GuideRequestAuth.SCOPES, decision.getScopes());
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }

    static boolean isGuideTokenPost(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            String uri = request.getRequestURI();
            String context = request.getContextPath() == null ? "" : request.getContextPath();
            if (uri != null && context.length() > 0 && uri.startsWith(context)) {
                path = uri.substring(context.length());
            } else {
                path = uri;
            }
        }
        if (path != null && path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return "/api/guide/token".equals(path);
    }
}
