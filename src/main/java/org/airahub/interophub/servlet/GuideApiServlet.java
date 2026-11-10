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
import org.airahub.interophub.service.GuideHostingService;
import org.airahub.interophub.service.GuideHtmlRules;
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
    private static final Set<String> REGISTRATION_FIELDS = Set.of("title", "repositoryUrl", "urls", "isActive", "hosting");

    private final GuideCommentService comments;
    private final GuideRegistrationService registration;
    private final PublicUrlService publicUrls;
    private final GuideHostingService hosting;

    public GuideApiServlet() {
        this(new GuideCommentService(), new GuideRegistrationService(), new PublicUrlService(), new GuideHostingService());
    }

    public GuideApiServlet(GuideCommentService comments, GuideRegistrationService registration, PublicUrlService publicUrls) {
        this(comments, registration, publicUrls, null);
    }

    public GuideApiServlet(GuideCommentService comments, GuideRegistrationService registration,
            PublicUrlService publicUrls, GuideHostingService hosting) {
        this.comments = comments;
        this.registration = registration;
        this.publicUrls = publicUrls;
        this.hosting = hosting;
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

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response, () -> delete(request, response));
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
            write(response, HttpServletResponse.SC_OK, comments.comment(user(request), positiveId(parts[4], "That comment was not found.")));
            return;
        }
        if (parts.length == 5 && "review-flags".equals(parts[4])) {
            if (!GuideRequestAuth.requireScope(request, response, GuideScopes.COMMENTS)) {
                return;
            }
            write(response, HttpServletResponse.SC_OK,
                    comments.reviewFlags(user(request), parts[3], request.getParameter("includeCleared")));
            return;
        }
        if (parts.length == 5 && "versions".equals(parts[4])) {
            if (!GuideRequestAuth.requireScope(request, response, GuideScopes.COMMENTS, GuideScopes.TOPICS_READ)) {
                return;
            }
            write(response, HttpServletResponse.SC_OK, hosting.list(user(request), parts[3]));
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
            String hosting = null;
            if (body.has("hosting")) {
                if (body.isNull("hosting")) {
                    throw GuideApiException.invalidField("hosting", "Hosting must be EXTERNAL or HUB.");
                }
                hosting = text(body, "hosting");
            }
            JSONObject result = registration.create(user(request), tokenId(request), "API", dryRun,
                    text(body, "topicCode"), text(body, "documentKey"), text(body, "title"),
                    text(body, "repositoryUrl"), urlInputs(body, true), hosting);
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
            write(response, HttpServletResponse.SC_OK, comments.withdraw(user(request), tokenId(request),
                    positiveId(parts[4], "That comment was not found.")));
            return;
        }
        if (parts.length == 5 && "review-flags".equals(parts[4])) {
            if (!GuideRequestAuth.requireScope(request, response, GuideScopes.COMMENTS)) {
                return;
            }
            GuideCommentService.FlagResult flagged = comments.flag(user(request), parts[3], jsonBody(request));
            write(response, flagged.status(), flagged.flag());
            return;
        }
        if (parts.length == 6 && "versions".equals(parts[4])) {
            if (!GuideRequestAuth.requireScope(request, response, GuideScopes.TOPICS_WRITE)) {
                return;
            }
            byte[] html = request.getInputStream().readNBytes((int) GuideHtmlRules.MAX_BYTES + 1);
            GuideHostingService.UploadResult uploaded = hosting.upload(user(request), tokenId(request), "API",
                    parts[3], parts[5], html, request.getContentType());
            write(response, uploaded.status(), uploaded.version());
            return;
        }
        GuideApiErrors.write(response, GuideApiException.notFound("That guide was not found."));
    }

    private void delete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] parts = parts(request);
        if (parts.length == 5 && "comments".equals(parts[3])) {
            if (!GuideRequestAuth.requireScope(request, response, GuideScopes.COMMENTS)) {
                return;
            }
            comments.delete(user(request), tokenId(request), positiveId(parts[4], "That comment was not found."));
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        if (parts.length == 6 && "review-flags".equals(parts[4])) {
            if (!GuideRequestAuth.requireScope(request, response, GuideScopes.COMMENTS)) {
                return;
            }
            comments.clearFlag(user(request), parts[3], positiveId(parts[5], "That flag was not found."));
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
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
                    body.has("isActive"), active(body),
                    body.has("hosting"), body.has("hosting") ? text(body, "hosting") : null);
            write(response, HttpServletResponse.SC_OK, result);
            return;
        }
        if (parts.length == 5 && "comments".equals(parts[3])) {
            if (!GuideRequestAuth.requireScope(request, response, GuideScopes.COMMENTS)) {
                return;
            }
            write(response, HttpServletResponse.SC_OK,
                    comments.patch(user(request), tokenId(request), positiveId(parts[4], "That comment was not found."),
                            jsonBody(request)));
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

    private static long positiveId(String raw, String missing) {
        try {
            long id = Long.parseLong(raw);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException ex) {
            throw GuideApiException.notFound(missing);
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
