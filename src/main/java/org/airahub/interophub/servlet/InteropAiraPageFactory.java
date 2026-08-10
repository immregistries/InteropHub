package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.PublicUrlService;
import org.airahub.interophub.service.TopicSpaceAccessService;
import org.immregistries.aira.web.AiraAccountConfig;
import org.immregistries.aira.web.AiraContextConfig;
import org.immregistries.aira.web.AiraDefaults;
import org.immregistries.aira.web.AiraEnvironmentConfig;
import org.immregistries.aira.web.AiraLogo;
import org.immregistries.aira.web.AiraNavigationItem;
import org.immregistries.aira.web.AiraPage;
import org.immregistries.aira.web.AiraSearchConfig;

final class InteropAiraPageFactory {
    private static final String APPLICATION_NAME = "InteropHub";
    private static final String HOME_HREF = "/home";
    private static final String ACCOUNT_HREF = "/account";
    private static final String ADMIN_HREF = "/admin";
    private static final String SEARCH_ACTION = "/search";
    private static final String SEARCH_PARAMETER = "q";
    private static final String SEARCH_LABEL = "Search InteropHub";
    private static final String SEARCH_PLACEHOLDER = "Search topics or meetings";
    private static final String HEADER_SEARCH_SCRIPT_PATH = "/js/header-search.js";
    private static final String LOCAL_ENV_LABEL = "Local";
    private static final String LOCAL_ENV_DESCRIPTION = "Running in localhost mode";
    private static final String SIGNED_IN_USER_FALLBACK = "Signed in user";
    private static final String WELCOME_CONTEXT_TITLE = "Interoperability Hub";
    private static final int MAX_WELCOME_SPACE_ITEMS = 5;

    private static final AuthFlowService AUTH_FLOW_SERVICE = new AuthFlowService();
    private static final PublicUrlService PUBLIC_URL_SERVICE = new PublicUrlService();
    private static final EsTopicSpaceDao TOPIC_SPACE_DAO = new EsTopicSpaceDao();
    private static final TopicSpaceAccessService TOPIC_SPACE_ACCESS_SERVICE = new TopicSpaceAccessService();

    private InteropAiraPageFactory() {
    }

    static AiraPage.Builder base(HttpServletRequest request, String documentTitle) {
        Optional<User> authenticatedUser = AUTH_FLOW_SERVICE.findAuthenticatedUser(request);
        return base(request, documentTitle, authenticatedUser, PUBLIC_URL_SERVICE.isLocalhostMode());
    }

    static AiraPage.Builder base(HttpServletRequest request, String documentTitle, Optional<User> authenticatedUser,
            boolean localhostMode) {
        AiraPage.Builder builder = AiraPage.builder()
                .applicationName(APPLICATION_NAME)
                .applicationVersion(InteropVersionResolver.getApplicationVersion())
                .documentTitle(documentTitle)
                .contextPath(request.getContextPath())
                .identityHref(HOME_HREF)
                .logo(new AiraLogo(AiraDefaults.DEFAULT_LOGO_PATH, AiraDefaults.DEFAULT_LOGO_ALT_TEXT))
                .account(buildAccountConfig(authenticatedUser == null ? Optional.empty() : authenticatedUser))
                .search(new AiraSearchConfig(SEARCH_ACTION, SEARCH_PARAMETER, SEARCH_LABEL, SEARCH_PLACEHOLDER));

        if (localhostMode) {
            builder.environment(new AiraEnvironmentConfig(LOCAL_ENV_LABEL, LOCAL_ENV_DESCRIPTION));
        }

        return builder;
    }

    /**
     * The {@code <script>} tag that wires up the header search-as-you-type
     * combobox behavior. {@link AiraPage} has no hook for injecting a script
     * tag into every page, so each servlet that renders a full page must print
     * this into its own body content before calling {@code page.writeEnd(out)}.
     */
    static String headerSearchScriptTag(String contextPath) {
        String path = contextPath == null ? "" : contextPath;
        return "    <script src=\"" + path + HEADER_SEARCH_SCRIPT_PATH + "\" defer></script>";
    }

    static AiraContextConfig topicsMeetingsContext(String contextLabel, String spaceCode, boolean topicsActive,
            boolean meetingsActive) {
        String label = trimToNull(contextLabel);
        if (label == null) {
            label = "InteropHub";
        }
        String meetingsHref = "/es/meetings";
        String normalizedSpaceCode = trimToNull(spaceCode);
        if (normalizedSpaceCode != null) {
            meetingsHref = "/es/meetings?space="
                    + URLEncoder.encode(normalizedSpaceCode, StandardCharsets.UTF_8).replace("+", "%20");
        }
        return new AiraContextConfig(label, List.of(
                new AiraNavigationItem("Topics", "/es/topics", topicsActive),
                new AiraNavigationItem("Meetings", meetingsHref, meetingsActive)));
    }

    /**
     * Context navigation listing public topic spaces, for pre-authentication pages
     * (sign-in, register, magic-link confirmation) so a visitor can browse public
     * spaces before signing in. Mirrors what {@code WelcomeServlet} shows an
     * anonymous visitor.
     */
    static AiraContextConfig publicTopicSpacePickerContext() {
        List<EsTopicSpace> publicSpaces = TOPIC_SPACE_ACCESS_SERVICE.filterVisibleSpaces(null,
                TOPIC_SPACE_DAO.findAllActiveOrdered());
        return topicSpacePickerContext(publicSpaces);
    }

    static AiraContextConfig topicSpacePickerContext(List<EsTopicSpace> orderedTopicSpaces) {
        List<AiraNavigationItem> items = new ArrayList<>();
        for (EsTopicSpace topicSpace : orderedTopicSpaces) {
            if (items.size() >= MAX_WELCOME_SPACE_ITEMS) {
                break;
            }
            String spaceCode = trimToNull(topicSpace.getSpaceCode());
            if (spaceCode == null) {
                continue;
            }
            String spaceName = trimToNull(topicSpace.getSpaceName());
            items.add(new AiraNavigationItem(
                    spaceName == null ? "Topic Space" : spaceName,
                    "/spaces/" + URLEncoder.encode(spaceCode, StandardCharsets.UTF_8).replace("+", "%20")
                            + "/topics",
                    false));
        }
        return new AiraContextConfig(WELCOME_CONTEXT_TITLE, items);
    }

    private static AiraAccountConfig buildAccountConfig(Optional<User> authenticatedUser) {
        if (authenticatedUser.isPresent()) {
            User user = authenticatedUser.get();
            if (AUTH_FLOW_SERVICE.isAdminUser(user)) {
                return new AiraAccountConfig(resolveUserDisplayName(user), "Admin", ADMIN_HREF);
            }
            return new AiraAccountConfig(resolveUserDisplayName(user), "Account", ACCOUNT_HREF);
        }
        return new AiraAccountConfig("", "Sign in", HOME_HREF);
    }

    /**
     * Dark context-nav row for the 4 top-level admin areas, shown on every
     * admin page (mirrors {@link #topicsMeetingsContext}). Also includes a
     * plain "Account" link back to the personal account page, since admins no
     * longer see that in the global header once it shows "Admin" instead.
     */
    static AiraContextConfig adminContext(AdminSection activeSection) {
        List<AiraNavigationItem> items = new ArrayList<>();
        for (AdminSection section : AdminSection.values()) {
            items.add(new AiraNavigationItem(section.label, section.homeHref, section == activeSection));
        }
        items.add(new AiraNavigationItem("Account", ACCOUNT_HREF, false));
        return new AiraContextConfig("Admin", items);
    }

    private static String resolveUserDisplayName(User user) {
        if (user == null) {
            return SIGNED_IN_USER_FALLBACK;
        }

        String firstName = trimToNull(user.getFirstName());
        String lastName = trimToNull(user.getLastName());
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }

        String email = trimToNull(user.getEmail());
        if (email != null) {
            return email;
        }

        return SIGNED_IN_USER_FALLBACK;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
