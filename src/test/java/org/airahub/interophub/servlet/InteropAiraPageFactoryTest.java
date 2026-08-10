package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.User;
import org.immregistries.aira.web.AiraPage;
import org.junit.jupiter.api.Test;

class InteropAiraPageFactoryTest {

    @Test
    void authenticatedUserShowsAccountLabelAndLink() {
        HttpServletRequest request = requestWithContextPath("/hub");
        User user = new User();
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        user.setEmail("ada@example.org");

        String html = render(InteropAiraPageFactory
                .base(request, "Header Test", Optional.of(user), false)
                .build());

        assertTrue(html.contains("Ada Lovelace"));
        assertTrue(html.contains(">Account<"));
        assertTrue(html.contains("/account"));
        assertFalse(html.contains(">Sign in<"));
    }

    @Test
    void anonymousUserShowsSignInAndNoFakeDisplayName() {
        HttpServletRequest request = requestWithContextPath("/hub");

        String html = render(InteropAiraPageFactory
                .base(request, "Header Test", Optional.empty(), false)
                .build());

        assertTrue(html.contains(">Sign in<"));
        assertFalse(html.contains(">Account<"));
        assertFalse(html.contains("Signed in user"));
    }

    @Test
    void globalSearchConfigRenderedInHeader() {
        HttpServletRequest request = requestWithContextPath("/hub");

        String html = render(InteropAiraPageFactory
                .base(request, "Header Test", Optional.empty(), false)
                .build());

        assertTrue(html.contains("Search InteropHub"));
        assertTrue(html.contains("name=\"q\""));
        assertTrue(html.contains("Search topics or meetings"));
        assertTrue(html.contains("action=\"/hub/search\""));
    }

    @Test
    void localEnvironmentBadgeOnlyRenderedWhenEnabled() {
        HttpServletRequest request = requestWithContextPath("/hub");

        String localHtml = render(InteropAiraPageFactory
                .base(request, "Header Test", Optional.empty(), true)
                .build());
        String nonLocalHtml = render(InteropAiraPageFactory
                .base(request, "Header Test", Optional.empty(), false)
                .build());

        assertTrue(localHtml.contains("aira-environment-badge"));
        assertTrue(localHtml.contains("Local"));
        assertFalse(nonLocalHtml.contains("aira-environment-badge"));
    }

    @Test
    void accountNameFallsBackToEmailThenGenericLabel() {
        HttpServletRequest request = requestWithContextPath("/hub");

        User emailOnlyUser = new User();
        emailOnlyUser.setEmail("user@example.org");
        String emailHtml = render(InteropAiraPageFactory
                .base(request, "Header Test", Optional.of(emailOnlyUser), false)
                .build());
        assertTrue(emailHtml.contains("user@example.org"));

        User unnamedUser = new User();
        String fallbackHtml = render(InteropAiraPageFactory
                .base(request, "Header Test", Optional.of(unnamedUser), false)
                .build());
        assertTrue(fallbackHtml.contains("Signed in user"));
    }

    @Test
    void topicSpacePickerContextRendersTitleAndSpaceLinks() {
        HttpServletRequest request = requestWithContextPath("/hub");
        List<EsTopicSpace> spaces = List.of(
                topicSpace("emerging-standards", "Emerging Standards"),
                topicSpace("building-bridges", "Building Bridges"));

        String html = render(InteropAiraPageFactory
                .base(request, "Header Test", Optional.empty(), false)
                .context(InteropAiraPageFactory.topicSpacePickerContext(spaces))
                .build());

        assertTrue(html.contains("Interoperability Hub"));
        assertTrue(html.contains("Emerging Standards"));
        assertTrue(html.contains("/spaces/emerging-standards/topics"));
        assertTrue(html.contains("Building Bridges"));
        assertTrue(html.contains("/spaces/building-bridges/topics"));
    }

    @Test
    void topicSpacePickerContextCapsAtFiveSpaces() {
        HttpServletRequest request = requestWithContextPath("/hub");
        List<EsTopicSpace> spaces = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            spaces.add(topicSpace("space-" + i, "Space " + i));
        }

        String html = render(InteropAiraPageFactory
                .base(request, "Header Test", Optional.empty(), false)
                .context(InteropAiraPageFactory.topicSpacePickerContext(spaces))
                .build());

        assertTrue(html.contains("Space 1"));
        assertTrue(html.contains("Space 5"));
        assertFalse(html.contains("Space 6"));
    }

    private EsTopicSpace topicSpace(String code, String name) {
        EsTopicSpace space = new EsTopicSpace();
        space.setSpaceCode(code);
        space.setSpaceName(name);
        return space;
    }

    private String render(AiraPage page) {
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        page.writeStart(out);
        page.writeEnd(out);
        out.flush();
        return writer.toString();
    }

    private HttpServletRequest requestWithContextPath(String contextPath) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class[] { HttpServletRequest.class },
                (proxy, method, args) -> {
                    if ("getContextPath".equals(method.getName())) {
                        return contextPath;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType.equals(boolean.class)) {
                        return false;
                    }
                    if (returnType.equals(int.class)) {
                        return 0;
                    }
                    if (returnType.equals(long.class)) {
                        return 0L;
                    }
                    return null;
                });
    }
}
