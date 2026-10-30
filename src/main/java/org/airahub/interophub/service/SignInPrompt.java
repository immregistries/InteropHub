package org.airahub.interophub.service;

/**
 * {@code prompt=login} ends the InteropHub session and continues as if there were none.
 * It does not clear the servlet session that holds the external sign-in request.
 */
public final class SignInPrompt {
    public enum Next {
        ISSUE_EXTERNAL_CODE,
        REDIRECT_INTERNAL,
        REDIRECT_WELCOME,
        SHOW_FORM
    }

    private SignInPrompt() {
    }

    public static boolean isLoginPrompt(String prompt) {
        return prompt != null && "login".equals(prompt.trim());
    }

    public static Next decide(boolean promptLogin, boolean authenticated, boolean hasExternal, boolean hasInternal) {
        if (promptLogin || !authenticated) {
            return Next.SHOW_FORM;
        }
        if (hasExternal) {
            return Next.ISSUE_EXTERNAL_CODE;
        }
        if (hasInternal) {
            return Next.REDIRECT_INTERNAL;
        }
        return Next.REDIRECT_WELCOME;
    }
}
