package org.airahub.interophub.servlet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.GuideApiErrors;
import org.airahub.interophub.service.GuideApiException;
import org.airahub.interophub.service.GuideCommentService;
import org.airahub.interophub.service.GuideRegistrationService;
import org.airahub.interophub.service.GuideRegistrationService.UrlInput;
import org.airahub.interophub.service.GuideRequestAuth;
import org.airahub.interophub.service.GuideScopes;
import org.airahub.interophub.service.PublicUrlService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Guide registration and comments. {@code /api/guide/token} stays on {@link GuideTokenServlet}.
 */
public class GuideApiServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(GuideApiServlet.class.getName());
    private static final Set<String> REGISTRATION_FIELDS = Set.of("title", "repositoryUrl", "urls", "isActive");

    private final GuideCommentService comments;
    private final GuideRegistrationService registration;
    private final PublicUrlService publicUrls;

    public GuideApiServlet() {
        this(new GuideCommentService(), new GuideRegistrationService(), new PublicUrlService());
    }

    public GuideApiServlet(GuideCommentService comments, GuideRegistrationService registration, PublicUrlService publicUrls) {
        this.comments = comments;
        this.registration = registration;
        this.publicUrls = publicUrls;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(request.getMethod())) {
            try {
                patch(request, response);
            } catch (GuideApiException ex) {
                GuideApiErrors.write(response, ex);
            } catch (RuntimeException ex) {
                LOGGER.log(Level.SEVERE, "Guide API failed", ex);
                GuideApiErrors.serverError(response);
            }
            return;
        }
        super.service(request, response);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response, () -> get(request, response));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response, () -> post(request, response));
    }

    private void handle(HttpServletRequest request, HttpServletResponse response, Action action) throws IOException {
        try {
            action.run();
        } catch (GuideApiException ex) {
            GuideApiErrors.write(response, ex);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Guide API failed", ex);
            GuideApiErrors.serverError(response);
        }
    }

    private void get(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] parts = parts(request);
        if (parts.length == 4) {
            if (!GuideRequestAuth.requireScope(request, response, GuideScopes.COMMENTS, GuideScopes.TOPICS_READ)) {
                return;
            }
            write(response, HttpServletResponse.SC_OK, comments.document(user(request), parts[3]));
            return;
        }
        if (parts.length == 5 && "comments".equals(parts[4])) {
            if (!GuideRequestAuth.requireScope(request, response, GuideScopes.COMMENTS)) {
                return;
            }
            write(response, HttpServletResponse.SC_OK, comments.list(user(request), parts[3],
                    request.getParameter("state"), request.getParameter("anchor"), request.getParameter("since")));
            return;
        }
        if (parts.length == 5 && "comments".equals(parts[3])) {
            if (!GuideRequestAuth.requireScope(request, response, GuideScopes.COMMENTS)) {
                return;
            }
            write(response, HttpServletResponse.SC_OK, comments.comment(user(request), commentId(parts[4])));
            return;
        }
        GuideApiErrors.write(response, GuideApiException.notFound("That guide was not found."));
    }

    private void post(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] parts = parts(request);
        if (parts.length == 3 && "guides".equals(parts[2])) {
            if (!GuideRequestAuth.requireScope(request, response, GuideScopes.TOPICS_WRITE)) {
                return;
            }
            JSONObject body = jsonBody(request);
            boolean dryRun = dryRun(request);
            JSONObject result = registration.create(user(request), tokenId(request), "API", dryRun,
                    text(body, "topicCode"), text(body, "documentKey"), text(body, "title"),
                    text(body, "repositoryUrl"), urlInputs(body, true));
            write(response, dryRun ? HttpServletResponse.SC_OK : HttpServletResponse.SC_CREATED, result);
            return;
        }
        if (parts.length == 5 && "comments".equals(parts[4])) {
            if (!GuideRequestAuth.requireScope(request, response, GuideScopes.COMMENTS)) {
                return;
            }
            GuideCommentService.Posted posted = comments.post(user(request), tokenId(request), parts[3], jsonBody(request));
            response.setHeader("Location", publicUrls.resolveExternalUrl("/api/guide/comments/" + posted.id()));
            write(response, HttpServletResponse.SC_CREATED, posted.thread());
            return;
        }
        if (parts.length == 6 && "comments".equals(parts[3]) && "withdraw".equals(parts[5])) {
            if (!GuideRequestAuth.requireScope(request, response, GuideScopes.COMMENTS)) {
                return;
            }
            write(response, HttpServletResponse.SC_OK, comments.withdraw(user(request), tokenId(request), commentId(parts[4])));
            return;
        }
        GuideApiErrors.write(response, GuideApiException.notFound("That guide was not found."));
    }

    private void patch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] parts = parts(request);
        if (parts.length == 4) {
            if (!GuideRequestAuth.requireScope(request, response, GuideScopes.TOPICS_WRITE)) {
                return;
            }
            JSONObject body = jsonBody(request);
            for (String key : body.keySet()) {
                if (!REGISTRATION_FIELDS.contains(key)) {
                    throw GuideApiException.invalidField(key, "That field is not accepted on this request.");
                }
            }
            boolean dryRun = dryRun(request);
            JSONObject result = registration.update(user(request), tokenId(request), "API", dryRun, parts[3],
                    body.has("title"), text(body, "title"),
                    body.has("repositoryUrl"), body.isNull("repositoryUrl") ? null : text(body, "repositoryUrl"),
                    body.has("urls"), body.has("urls") ? urlInputs(body, false) : List.of(),
                    body.has("isActive"), active(body));
            write(response, HttpServletResponse.SC_OK, result);
            return;
        }
        if (parts.length == 5 && "comments".equals(parts[3])) {
            if (!GuideRequestAuth.requireScope(request, response, GuideScopes.COMMENTS)) {
                return;
            }
            write(response, HttpServletResponse.SC_OK,
                    comments.patchText(user(request), tokenId(request), commentId(parts[4]), jsonBody(request)));
            return;
        }
        GuideApiErrors.write(response, GuideApiException.notFound("That guide was not found."));
    }

    private static Boolean active(JSONObject body) {
        if (!body.has("isActive") || body.isNull("isActive")) {
            return null;
        }
        Object value = body.get("isActive");
        if (!(value instanceof Boolean flag)) {
            throw GuideApiException.invalidField("isActive", "isActive must be true or false.");
        }
        return flag;
    }

    private static List<UrlInput> urlInputs(JSONObject body, boolean allowMissing) {
        if (!body.has("urls") || body.isNull("urls")) {
            if (allowMissing) {
                return List.of();
            }
            throw GuideApiException.invalidField("urls", "URLs must be a list.");
        }
        Object raw = body.get("urls");
        if (!(raw instanceof JSONArray array)) {
            throw GuideApiException.invalidField("urls", "URLs must be a list.");
        }
        List<UrlInput> urls = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object item = array.get(i);
            if (!(item instanceof JSONObject object)) {
                throw GuideApiException.invalidField("urls", "Each URL needs a url and a label.");
            }
            urls.add(new UrlInput(optionalText(object, "url"), optionalText(object, "label")));
        }
        return urls;
    }

    private static String optionalText(JSONObject body, String field) {
        if (!body.has(field) || body.isNull(field)) {
            return null;
        }
        Object value = body.get(field);
        if (!(value instanceof String text)) {
            throw GuideApiException.invalidField(field, "That field must be text.");
        }
        return text;
    }

    private static String text(JSONObject body, String field) {
        if (!body.has(field) || body.isNull(field)) {
            return null;
        }
        Object value = body.get(field);
        if (!(value instanceof String text)) {
            throw GuideApiException.invalidField(field, "That field must be text.");
        }
        return text;
    }

    private static JSONObject jsonBody(HttpServletRequest request) throws IOException {
        byte[] bytes = request.getInputStream().readNBytes(100_001);
        if (bytes.length > 100_000) {
            throw GuideApiException.invalidRequest("The request body is too large.");
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            throw GuideApiException.invalidRequest("A JSON body is required.");
        }
        try {
            return new JSONObject(text);
        } catch (JSONException ex) {
            throw GuideApiException.invalidRequest("The request body is not valid JSON.");
        }
    }

    private static boolean dryRun(HttpServletRequest request) {
        String value = request.getParameter("dryRun");
        if (value == null || value.isBlank() || "false".equals(value)) {
            return false;
        }
        if ("true".equals(value)) {
            return true;
        }
        throw GuideApiException.invalidRequest("dryRun must be true or false.");
    }

    private static long commentId(String raw) {
        try {
            long id = Long.parseLong(raw);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException ex) {
            throw GuideApiException.notFound("That comment was not found.");
        }
    }

    private static User user(HttpServletRequest request) {
        return (User) request.getAttribute(GuideRequestAuth.USER);
    }

    private static Long tokenId(HttpServletRequest request) {
        Object value = request.getAttribute(GuideRequestAuth.TOKEN_ID);
        return value instanceof Long id ? id : null;
    }

    static String[] parts(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath() == null ? "" : request.getContextPath();
        if (uri != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        if (uri == null) {
            return new String[0];
        }
        if (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri.split("/");
    }

    private static void write(HttpServletResponse response, int status, JSONObject json) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(json.toString());
    }

    @FunctionalInterface
    private interface Action {
        void run() throws IOException;
    }
}
